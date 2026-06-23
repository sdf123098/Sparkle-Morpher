package com.micaftic.morpher.resource.bbmodel;

import com.micaftic.morpher.resource.pojo.RawYsmModel;

/**
 * BBModel 解析器自检。
 *
 * <p>这份 JSON 严格按 Blockbench 5 真实导出 schema 构造：
 * elements 平铺，outliner 嵌套，无顶层 groups[]，loop 用字符串，states 用数组。</p>
 *
 * <p>跑法：在 IDE 里直接 main，或 {@code java BBModelTest}。</p>
 */
public class BBModelTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        runBasicCubeTest();
        runNestedBonesTest();
        runUvNormalizationTest();
        runMolangDataPointTest();
        runOrphanElementTest();
        runImportedPlayerDefaultsTest();
        runStatesAsArrayTest();
        runMeshTriangulationTest();
        runZipSnifferTest();

        System.out.println();
        System.out.println("================");
        System.out.println("  Passed: " + passed);
        System.out.println("  Failed: " + failed);
        System.out.println("================");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ============================================================

    private static void runBasicCubeTest() {
        String json = """
        {
          "meta": { "format_version": "4.5", "model_format": "free", "box_uv": false },
          "resolution": { "width": 16, "height": 16 },
          "name": "Test Model",
          "model_identifier": "test:model",
          "elements": [
            {
              "uuid": "cube-1",
              "type": "cube",
              "name": "head",
              "from": [0, 0, 0],
              "to": [8, 8, 8],
              "rotation": [0, 0, 0],
              "origin": [0, 24, 0],
              "faces": {
                "north": {"uv": [0, 0, 8, 8], "texture": 0},
                "south": {"uv": [0, 0, 8, 8], "texture": 0},
                "east":  {"uv": [0, 0, 8, 8], "texture": 0},
                "west":  {"uv": [0, 0, 8, 8], "texture": 0},
                "up":    {"uv": [0, 0, 8, 8], "texture": 0},
                "down":  {"uv": [0, 0, 8, 8], "texture": 0}
              }
            }
          ],
          "outliner": [
            {
              "name": "head_bone",
              "uuid": "group-1",
              "origin": [0, 24, 0],
              "rotation": [0, 0, 0],
              "children": ["cube-1"]
            }
          ],
          "textures": [],
          "animations": []
        }
        """;
        BBModelFile model = BBModelParser.parse(json);
        check("basic: name", "Test Model".equals(model.name));
        check("basic: 1 element", model.elements.size() == 1);
        check("basic: 1 outliner root", model.outliner.size() == 1);
        check("basic: outliner is group", model.outliner.get(0).isGroup());
        check("basic: outliner has 1 child", model.outliner.get(0).children.size() == 1);
        check("basic: outliner child is element ref", model.outliner.get(0).children.get(0).isElementRef());

        RawYsmModel raw = BBToRawConverter.convert(model);
        check("convert: mainModel non-null", raw.mainEntity.mainModel != null);
        check("convert: 1 bone", raw.mainEntity.mainModel.bones.size() == 1);
        check("convert: bone name = head_bone", "head_bone".equals(raw.mainEntity.mainModel.bones.get(0).name));
        check("convert: bone pivot Y=24", Math.abs(raw.mainEntity.mainModel.bones.get(0).pivot[1] - 24f) < 1e-4);
        check("convert: bone has 1 cube", raw.mainEntity.mainModel.bones.get(0).cubes.size() == 1);
        check("convert: cube has 6 faces", raw.mainEntity.mainModel.bones.get(0).cubes.get(0).faces.size() == 6);
    }

    private static void runNestedBonesTest() {
        String json = """
        {
          "resolution": { "width": 32, "height": 32 },
          "elements": [
            { "uuid": "arm-cube", "type": "cube", "from": [0,0,0], "to": [2,8,2], "origin": [0,20,0] }
          ],
          "outliner": [
            {
              "name": "body",
              "uuid": "g-body",
              "origin": [0, 12, 0],
              "children": [
                {
                  "name": "shoulder",
                  "uuid": "g-shoulder",
                  "origin": [4, 22, 0],
                  "children": ["arm-cube"]
                }
              ]
            }
          ]
        }
        """;
        BBModelFile model = BBModelParser.parse(json);
        RawYsmModel raw = BBToRawConverter.convert(model);
        check("nested: 2 bones", raw.mainEntity.mainModel.bones.size() == 2);
        RawYsmModel.RawBone body = raw.mainEntity.mainModel.bones.get(0);
        RawYsmModel.RawBone shoulder = raw.mainEntity.mainModel.bones.get(1);
        check("nested: body name", "body".equals(body.name));
        check("nested: body has no parent", "".equals(body.parentName));
        check("nested: shoulder name", "shoulder".equals(shoulder.name));
        check("nested: shoulder parent = body", "body".equals(shoulder.parentName));
        check("nested: cube on shoulder", shoulder.cubes.size() == 1);
        check("nested: body has no cube", body.cubes.isEmpty());
    }

    private static void runUvNormalizationTest() {
        // resolution 32x16，face uv = [0, 0, 16, 16] → 应归一化为 [0, 0, 0.5, 1.0]
        String json = """
        {
          "resolution": { "width": 32, "height": 16 },
          "elements": [
            {
              "uuid": "c", "type": "cube",
              "from": [0,0,0], "to": [4,4,4], "origin": [0,0,0],
              "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": 0},
                "south": {"uv": [0, 0, 16, 16], "texture": 0},
                "east":  {"uv": [0, 0, 16, 16], "texture": 0},
                "west":  {"uv": [0, 0, 16, 16], "texture": 0},
                "up":    {"uv": [0, 0, 16, 16], "texture": 0},
                "down":  {"uv": [0, 0, 16, 16], "texture": 0}
              }
            }
          ],
          "outliner": [
            { "name": "root", "uuid": "g", "children": ["c"] }
          ]
        }
        """;
        BBModelFile model = BBModelParser.parse(json);
        RawYsmModel raw = BBToRawConverter.convert(model);
        RawYsmModel.RawFace north = raw.mainEntity.mainModel.bones.get(0).cubes.get(0).faces.get(0);
        check("uv: u[0] = 0", Math.abs(north.u[0]) < 1e-4);
        check("uv: u[1] = 0.5 (16/32)", Math.abs(north.u[1] - 0.5f) < 1e-4);
        check("uv: v[2] = 1.0 (16/16)", Math.abs(north.v[2] - 1.0f) < 1e-4);
    }

    private static void runMolangDataPointTest() {
        String json = """
        {
          "resolution": {"width": 16, "height": 16},
          "elements": [],
          "outliner": [{"name":"g", "uuid":"u-g", "children":[]}],
          "animations": [
            {
              "uuid": "a1", "name": "anim.test",
              "loop": "loop", "length": 1.0,
              "animators": {
                "u-g": {
                  "name": "g", "type": "bone",
                  "keyframes": [
                    { "channel": "rotation", "time": 0, "interpolation": "linear",
                      "data_points": [{"x": "0", "y": "math.sin(query.anim_time * 360)", "z": "0"}] },
                    { "channel": "position", "time": 0.5, "interpolation": "catmullrom",
                      "data_points": [{"x": "1.5", "y": "2", "z": "3"}] }
                  ]
                }
              }
            }
          ]
        }
        """;
        BBModelFile model = BBModelParser.parse(json);
        RawYsmModel raw = BBToRawConverter.convert(model);
        check("anim: 1 anim file", raw.mainEntity.animationFiles.size() == 1);
        RawYsmModel.RawAnimationFile af = raw.mainEntity.animationFiles.values().iterator().next();
        check("anim: 1 animation", af.animations.size() == 1);
        RawYsmModel.RawAnimation a = af.animations.get("anim.test");
        check("anim: loopMode = 1 (loop)", a != null && a.loopMode == 1);
        check("anim: length = 1.0", a != null && Math.abs(a.length - 1.0f) < 1e-4);
        check("anim: 1 bone anim", a != null && a.boneAnimations.size() == 1);
        RawYsmModel.RawBoneAnimation ba = a.boneAnimations.get(0);
        check("anim: 1 rotation kf", ba.rotation.size() == 1);
        check("anim: 1 position kf", ba.position.size() == 1);
        RawYsmModel.RawKeyframe rotKf = ba.rotation.get(0);
        // x="0" 折叠为 Float 0；y= Molang 表达式保留为 String
        check("anim: rot.postData[0] is Float", rotKf.postData[0] instanceof Float);
        check("anim: rot.postData[1] is Molang String", rotKf.postData[1] instanceof String);
        // position 关键帧的纯数字应全部折叠为 Float
        RawYsmModel.RawKeyframe posKf = ba.position.get(0);
        check("anim: pos.postData all Float", posKf.postData[0] instanceof Float
                && posKf.postData[1] instanceof Float
                && posKf.postData[2] instanceof Float);
        check("anim: pos interpolation = catmullrom (1)", posKf.interpolationMode == 1);
    }

    private static void runOrphanElementTest() {
        // elements 里有一个 cube，但没有任何 outliner group 引用它
        // → 应该塞进自动创建的 "default" bone
        String json = """
        {
          "resolution": {"width": 16, "height": 16},
          "elements": [
            { "uuid": "lone", "type": "cube", "from":[0,0,0], "to":[1,1,1] }
          ],
          "outliner": []
        }
        """;
        BBModelFile model = BBModelParser.parse(json);
        RawYsmModel raw = BBToRawConverter.convert(model);
        check("orphan: 1 default bone", raw.mainEntity.mainModel.bones.size() == 1);
        check("orphan: bone name = default", "default".equals(raw.mainEntity.mainModel.bones.get(0).name));
        check("orphan: bone has the cube", raw.mainEntity.mainModel.bones.get(0).cubes.size() == 1);
    }

    private static void runImportedPlayerDefaultsTest() {
        String json = """
        {
          "resolution": {"width": 16, "height": 16},
          "elements": [
            { "uuid": "lh-cube", "type": "cube", "from": [3,12,-1], "to": [5,20,1] },
            { "uuid": "rh-cube", "type": "cube", "from": [-5,12,-1], "to": [-3,20,1] }
          ],
          "outliner": [
            { "name": "LeftHand", "uuid": "left-hand", "children": ["lh-cube"] },
            { "name": "RightHand", "uuid": "right-hand", "children": ["rh-cube"] }
          ]
        }
        """;
        RawYsmModel raw = BBToRawConverter.convert(BBModelParser.parse(json));
        check("defaults: imported scale = 1", Math.abs(raw.properties.widthScale - 1.0f) < 1e-4
                && Math.abs(raw.properties.heightScale - 1.0f) < 1e-4);
        check("defaults: import marker set", "sparkle_morpher:bbmodel_import".equals(raw.footer.extra));
        check("defaults: left hand locator generated",
                findBone(raw, "LeftHandLocator") != null
                        && "LeftHand".equals(findBone(raw, "LeftHandLocator").parentName));
        check("defaults: right hand locator generated",
                findBone(raw, "RightHandLocator") != null
                        && "RightHand".equals(findBone(raw, "RightHandLocator").parentName));
    }

    private static void runStatesAsArrayTest() {
        String json = """
        {
          "resolution": {"width":16,"height":16},
          "elements": [], "outliner": [],
          "animation_controllers": [
            {
              "uuid": "ctrl-1", "name": "controller.test",
              "initial_state": "idle",
              "states": [
                {
                  "name": "idle",
                  "animations": ["anim.idle"],
                  "transitions": [{"walking": "query.is_moving"}]
                },
                {
                  "name": "walking",
                  "animations": ["anim.walk"],
                  "transitions": [{"idle": "!query.is_moving"}]
                }
              ]
            }
          ]
        }
        """;
        BBModelFile model = BBModelParser.parse(json);
        check("ctrl: 1 controller", model.animation_controllers.size() == 1);
        BBAnimationController c = model.animation_controllers.get(0);
        check("ctrl: 2 states in order", c.stateOrder.size() == 2
                && "idle".equals(c.stateOrder.get(0))
                && "walking".equals(c.stateOrder.get(1)));
        check("ctrl: idle has transition to walking", c.states.get("idle") != null
                && c.states.get("idle").transitions.size() == 1
                && "walking".equals(c.states.get("idle").transitions.get(0).target));

        RawYsmModel raw = BBToRawConverter.convert(model);
        check("ctrl: 1 controller file", raw.mainEntity.animationControllerFiles.size() == 1);
        RawYsmModel.RawAnimationController rc = raw.mainEntity.animationControllerFiles
                .get(0).controllers.get("controller.test");
        check("ctrl: initial = idle", rc != null && "idle".equals(rc.initialState));
        check("ctrl: 2 raw states", rc != null && rc.states.size() == 2);
        check("ctrl: first raw state = idle", rc != null && "idle".equals(rc.states.get(0).name));
    }

    private static void runMeshTriangulationTest() {
        // 一个三角形 mesh：3 个顶点，1 个 face vertices=[a,b,c]
        String json = """
        {
          "resolution": { "width": 16, "height": 16 },
          "elements": [
            {
              "uuid": "m1", "type": "mesh", "name": "tri",
              "origin": [0,0,0], "rotation": [0,0,0],
              "vertices": {
                "a": { "position": [0, 0, 0] },
                "b": { "position": [4, 0, 0] },
                "c": { "position": [0, 4, 0] }
              },
              "faces": {
                "f1": {
                  "vertices": ["a","b","c"],
                  "uv": { "a":[0,0], "b":[16,0], "c":[0,16] },
                  "texture": 0
                }
              }
            },
            {
              "uuid": "m2", "type": "mesh", "name": "quad",
              "origin": [0,0,0], "rotation": [0,0,0],
              "vertices": {
                "p1": { "position": [0,0,0] },
                "p2": { "position": [2,0,0] },
                "p3": { "position": [2,2,0] },
                "p4": { "position": [0,2,0] }
              },
              "faces": {
                "f1": {
                  "vertices": ["p1","p2","p3","p4"],
                  "uv": { "p1":[0,0], "p2":[8,0], "p3":[8,8], "p4":[0,8] },
                  "texture": 0
                }
              }
            }
          ],
          "outliner": [
            { "name": "tri_bone", "uuid": "g1", "children": ["m1"] },
            { "name": "quad_bone", "uuid": "g2", "children": ["m2"] }
          ]
        }
        """;
        BBModelFile model = BBModelParser.parse(json);
        check("mesh: 2 elements parsed as mesh", model.elements.size() == 2
                && "mesh".equals(model.elements.get(0).type));
        check("mesh: tri has 3 vertices", model.elements.get(0).vertices.size() == 3);
        check("mesh: tri face vertices are string keys",
                model.elements.get(0).faces.values().iterator().next().vertices.length == 3);

        RawYsmModel raw = BBToRawConverter.convert(model);
        check("mesh: 2 bones", raw.mainEntity.mainModel.bones.size() == 2);

        // 三角形 → 1 个单面 RawCube
        RawYsmModel.RawBone triBone = raw.mainEntity.mainModel.bones.get(0);
        check("mesh: tri produces 1 cube", triBone.cubes.size() == 1);
        check("mesh: tri cube has 1 face", triBone.cubes.get(0).faces.size() == 1);
        RawYsmModel.RawFace triFace = triBone.cubes.get(0).faces.get(0);
        check("mesh: tri face 4 positions (4th = 3rd degenerate)",
                triFace.positions.length == 4
                && triFace.positions[2][0] == triFace.positions[3][0]
                && triFace.positions[2][1] == triFace.positions[3][1]);
        // UV 已归一化：16/16 = 1.0
        check("mesh: tri UV normalized", Math.abs(triFace.u[1] - 1.0f) < 1e-4);
        // 三角形位于 XY 平面，BlockBench/bbmodel 顶点已与 YSM 渲染器约定一致（X+ 向右），
        // 不做 X 翻转 → winding 顺序保持 → 叉积法线指向 +Z。
        check("mesh: tri normal +Z", triFace.normal[2] > 0.99f);

        // 四边形 → 1 个单面 RawCube
        RawYsmModel.RawBone quadBone = raw.mainEntity.mainModel.bones.get(1);
        check("mesh: quad produces 1 cube", quadBone.cubes.size() == 1);
        RawYsmModel.RawFace quadFace = quadBone.cubes.get(0).faces.get(0);
        check("mesh: quad 4 distinct positions",
                quadFace.positions[3][0] != quadFace.positions[2][0]);
    }

    private static void runZipSnifferTest() {
        // 构造一个内存中的 Figura zip：含 avatar.json + xxx.bbmodel + 一张 PNG
        byte[] avatarJson = "{\"name\":\"TestAvatar\",\"authors\":[\"steve\"]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bbmodel = "{\"meta\":{\"format_version\":\"5.0\"},\"resolution\":{\"width\":16,\"height\":16},\"elements\":[],\"outliner\":[]}"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // 最小合法 PNG（1x1 透明）
        byte[] png = new byte[]{
                (byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 'I','H','D','R',
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte)0xC4, (byte)0x89,
                0x00, 0x00, 0x00, 0x0D, 'I','D','A','T',
                0x78, (byte)0x9C, 0x62, 0x00, 0x01, 0x00, 0x00, 0x05, 0x00, 0x01,
                0x0D, 0x0A, 0x2D, (byte)0xB4,
                0x00, 0x00, 0x00, 0x00, 'I','E','N','D', (byte)0xAE, 0x42, 0x60, (byte)0x82
        };

        byte[] zip = makeZip(new String[]{
                "TestAvatar/avatar.json",
                "TestAvatar/model.bbmodel",
                "TestAvatar/skin.png"
        }, new byte[][]{ avatarJson, bbmodel, png });

        ZipModelSniffer sniff = ZipModelSniffer.sniff(zip, 0);
        check("zip: detected as FIGURA_AVATAR", sniff.kind == ZipModelSniffer.Kind.FIGURA_AVATAR);
        check("zip: extracted bbmodel", sniff.bbmodelBytes != null && sniff.bbmodelBytes.length == bbmodel.length);
        check("zip: extracted avatar.json", sniff.avatarJsonBytes != null && sniff.avatarJsonBytes.length == avatarJson.length);
        check("zip: side textures has skin.png", sniff.sideTextures.containsKey("skin.png"));
        check("zip: avatar name = TestAvatar",
                "TestAvatar".equals(ZipModelSniffer.parseAvatarName(sniff.avatarJsonBytes)));

        // 纯 bbmodel zip（无 avatar.json）
        byte[] plainZip = makeZip(new String[]{ "model.bbmodel" }, new byte[][]{ bbmodel });
        ZipModelSniffer plainSniff = ZipModelSniffer.sniff(plainZip, 0);
        check("zip: plain bbmodel zip → PLAIN_BBMODEL",
                plainSniff.kind == ZipModelSniffer.Kind.PLAIN_BBMODEL);

        // YSM zip（有 ysm.json）
        byte[] ysmZip = makeZip(new String[]{ "ysm.json" }, new byte[][]{ "{}".getBytes() });
        ZipModelSniffer ysmSniff = ZipModelSniffer.sniff(ysmZip, 0);
        check("zip: ysm zip → YSM_FOLDER", ysmSniff.kind == ZipModelSniffer.Kind.YSM_FOLDER);

        // 损坏字节
        ZipModelSniffer badSniff = ZipModelSniffer.sniff(new byte[]{1,2,3,4}, 0);
        check("zip: bad bytes → UNKNOWN", badSniff.kind == ZipModelSniffer.Kind.UNKNOWN);
    }

    private static byte[] makeZip(String[] paths, byte[][] data) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(baos)) {
            for (int i = 0; i < paths.length; i++) {
                zout.putNextEntry(new java.util.zip.ZipEntry(paths[i]));
                zout.write(data[i]);
                zout.closeEntry();
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }

    // ============================================================

    private static RawYsmModel.RawBone findBone(RawYsmModel raw, String name) {
        for (RawYsmModel.RawBone bone : raw.mainEntity.mainModel.bones) {
            if (name.equals(bone.name)) {
                return bone;
            }
        }
        return null;
    }

    private static void check(String label, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  ✅ " + label);
        } else {
            failed++;
            System.out.println("  ❌ " + label);
        }
    }
}
