package com.micaftic.morpher.resource;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.client.ClientModelInfo;
import com.micaftic.morpher.client.model.MainModelData;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.core.compat.oculus.ShadersTextureType;
import com.micaftic.morpher.resource.bbmodel.BBModelFile;
import com.micaftic.morpher.resource.bbmodel.BBModelParser;
import com.micaftic.morpher.resource.bbmodel.BBToRawConverter;
import com.micaftic.morpher.resource.bundle.ClientModelBundleAssembler;
import com.micaftic.morpher.resource.bundle.TextureDecoder;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2.7 §10.5 ImportPipelineGoldenTest（§4.3 Characterization Before Refactor）。
 *
 * <p>golden / 特征化测试：用<b>合成样本</b>（无第三方版权资产）走完整导入管线
 * （YSM folder 解析 → 装配 {@link ClientModelInfo}，以及 bbmodel 解析路径），把关键产出
 * 固定为 golden 断言，为后续拆分提供回归基线。</p>
 *
 * <p>断言只覆盖确实稳定、有意义的量：骨骼/立方体/面数、贴图数与纹理尺寸、动画名集合、
 * 控制器数、子实体数、附加资源数、元数据、命中策略标记等。不断言时间/内存/字节哈希。</p>
 */
class ImportPipelineGoldenTest {

    /** discovery 模式：只打印实测 golden 值，不做断言。定稿后置 false。 */
    private static final boolean DISCOVER = false;

    /** 合成贴图尺寸（不用任何第三方素材）。 */
    private static final int TEX_W = 16;
    private static final int TEX_H = 16;

    private static final String YSM_JSON = """
            {
              "metadata": {
                "name": "Golden Sample",
                "tips": "synthetic golden fixture",
                "license": { "type": "CC0", "desc": "synthetic" },
                "authors": [
                  { "name": "Tester", "role": "author", "comment": "synthetic",
                    "avatar": "textures/avatar.png",
                    "contact": { "homepage": "https://example.invalid" } }
                ],
                "link": { "source": "https://example.invalid" }
              },
              "properties": {
                "width_scale": 0.7,
                "height_scale": 0.7,
                "default_texture": "tex",
                "preview_animation": "idle",
                "free": true,
                "all_cutout": false,
                "gui_background": "textures/gui_bg.png",
                "extra_animation": { "dance": "" }
              },
              "files": {
                "player": {
                  "model": { "main": "models/main.geo.json", "arm": "models/arm.geo.json" },
                  "texture": {
                    "uv": "textures/tex.png",
                    "normal": "textures/tex_normal.png",
                    "specular": "textures/tex_specular.png"
                  },
                  "animation": {
                    "main": "animations/main.animation.json",
                    "arm": "animations/arm.animation.json"
                  },
                  "animation_controllers": [ "animations/controller.json" ]
                },
                "projectiles": [
                  {
                    "identifier": "golden_projectile",
                    "match": [ "golden:projectile" ],
                    "model": "models/projectile.geo.json",
                    "texture": "textures/tex.png",
                    "animation": "animations/projectile.animation.json"
                  }
                ]
              }
            }
            """;

    /** 主几何：2 根骨骼（root / head），root 1 个 cube（object UV），head 1 个 cube（array UV）。 */
    private static final String MAIN_GEO_JSON = """
            {
              "format_version": "1.12.0",
              "minecraft:geometry": [
                {
                  "description": {
                    "identifier": "geometry.golden_main",
                    "texture_width": 64,
                    "texture_height": 64,
                    "visible_bounds_width": 3,
                    "visible_bounds_height": 4,
                    "visible_bounds_offset": [0, 1.5, 0]
                  },
                  "bones": [
                    {
                      "name": "root",
                      "pivot": [0, 0, 0],
                      "cubes": [
                        {
                          "origin": [-2, 0, -2],
                          "size": [4, 8, 4],
                          "uv": {
                            "north": { "uv": [0, 0], "uv_size": [4, 8] },
                            "south": { "uv": [8, 0], "uv_size": [4, 8] },
                            "east":  { "uv": [4, 0], "uv_size": [4, 8] },
                            "west":  { "uv": [12, 0], "uv_size": [4, 8] },
                            "up":    { "uv": [4, 8], "uv_size": [4, 4] },
                            "down":  { "uv": [8, 8], "uv_size": [4, 4] }
                          }
                        }
                      ]
                    },
                    {
                      "name": "head",
                      "parent": "root",
                      "pivot": [0, 8, 0],
                      "cubes": [
                        { "origin": [-4, 8, -4], "size": [8, 8, 8], "uv": [0, 0] }
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    private static final String ARM_GEO_JSON = """
            {
              "format_version": "1.12.0",
              "minecraft:geometry": [
                {
                  "description": {
                    "identifier": "geometry.golden_arm",
                    "texture_width": 64,
                    "texture_height": 64
                  },
                  "bones": [
                    {
                      "name": "arm",
                      "pivot": [-5, 22, 0],
                      "cubes": [
                        { "origin": [-8, 12, -2], "size": [4, 12, 4], "uv": [16, 16] }
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    private static final String PROJECTILE_GEO_JSON = """
            {
              "format_version": "1.12.0",
              "minecraft:geometry": [
                {
                  "description": {
                    "identifier": "geometry.golden_projectile",
                    "texture_width": 16,
                    "texture_height": 16
                  },
                  "bones": [
                    {
                      "name": "body",
                      "pivot": [0, 0, 0],
                      "cubes": [
                        { "origin": [-1, -1, -1], "size": [2, 2, 2], "uv": [0, 0] }
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    private static final String MAIN_ANIM_JSON = """
            {
              "format_version": "1.8.0",
              "animations": {
                "idle": {
                  "loop": true,
                  "animation_length": 2.0,
                  "bones": { "root": { "rotation": { "0.0": [0, 0, 0], "1.0": [0, 45, 0] } } }
                },
                "wave": {
                  "loop": "hold_on_last_frame",
                  "animation_length": 1.0,
                  "bones": { "head": { "position": { "0.0": [0, 0, 0] } } }
                }
              }
            }
            """;

    private static final String ARM_ANIM_JSON = """
            {
              "format_version": "1.8.0",
              "animations": {
                "arm_swing": {
                  "loop": true,
                  "animation_length": 0.5,
                  "bones": { "arm": { "rotation": { "0.0": [0, 0, 0] } } }
                }
              }
            }
            """;

    private static final String PROJECTILE_ANIM_JSON = """
            {
              "format_version": "1.8.0",
              "animations": {
                "fly": {
                  "loop": true,
                  "animation_length": 0.25,
                  "bones": { "body": { "rotation": { "0.0": [0, 0, 0] } } }
                }
              }
            }
            """;

    private static final String CONTROLLER_JSON = """
            {
              "format_version": "1.8.0",
              "animation_controllers": {
                "controller.golden": {
                  "initial_state": "idle",
                  "states": {
                    "idle": {
                      "animations": [ "idle" ],
                      "transitions": [ { "wave": "query.is_moving" } ]
                    },
                    "wave": { "animations": [ "wave" ] }
                  }
                }
              }
            }
            """;

    /** bbmodel 合成样本：1 骨骼 + 1 cube（6 面）+ 1 声明动画 + 1 控制器；贴图走 sideTextures。 */
    private static final String BBMODEL_JSON = """
            {
              "meta": { "format_version": "4.5", "model_format": "free" },
              "resolution": { "width": 32, "height": 32 },
              "name": "Golden Bb",
              "model_identifier": "golden:bb",
              "elements": [
                {
                  "uuid": "bb-cube",
                  "type": "cube",
                  "name": "bb_body",
                  "from": [0, 0, 0],
                  "to": [4, 8, 4],
                  "origin": [0, 12, 0],
                  "faces": {
                    "north": { "uv": [0, 0, 4, 8], "texture": 0 },
                    "south": { "uv": [4, 0, 8, 8], "texture": 0 },
                    "east":  { "uv": [8, 0, 12, 8], "texture": 0 },
                    "west":  { "uv": [12, 0, 16, 8], "texture": 0 },
                    "up":    { "uv": [4, 8, 8, 12], "texture": 0 },
                    "down":  { "uv": [8, 8, 12, 12], "texture": 0 }
                  }
                }
              ],
              "outliner": [
                { "name": "bb_bone", "uuid": "bb-group", "origin": [0, 12, 0], "children": [ "bb-cube" ] }
              ],
              "textures": [
                { "uuid": "bb-tex-1", "name": "skin", "width": 32, "height": 32 }
              ],
              "animations": [
                {
                  "uuid": "bb-anim-1", "name": "wave", "loop": "loop", "length": 1.0,
                  "animators": {
                    "bb-group": {
                      "name": "bb_bone", "type": "bone",
                      "keyframes": [
                        { "channel": "rotation", "time": 0, "interpolation": "linear",
                          "data_points": [ { "x": "0", "y": "0", "z": "0" } ] }
                      ]
                    }
                  }
                }
              ]
            }
            """;

    @TempDir
    Path tempDir;

    // ---------------------------------------------------------------- helpers

    /** 生成合成 PNG（无第三方素材）。pattern 固定 → 内容确定性。 */
    private static byte[] pngOf(int width, int height, int argb) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                img.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static int[] pngSize(byte[] data) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        return img == null ? null : new int[]{img.getWidth(), img.getHeight()};
    }

    private Path writeYsmFolder() throws Exception {
        Path root = tempDir.resolve("golden_model");
        write(root, "ysm.json", YSM_JSON);
        write(root, "models/main.geo.json", MAIN_GEO_JSON);
        write(root, "models/arm.geo.json", ARM_GEO_JSON);
        write(root, "models/projectile.geo.json", PROJECTILE_GEO_JSON);
        write(root, "animations/main.animation.json", MAIN_ANIM_JSON);
        write(root, "animations/arm.animation.json", ARM_ANIM_JSON);
        write(root, "animations/projectile.animation.json", PROJECTILE_ANIM_JSON);
        write(root, "animations/controller.json", CONTROLLER_JSON);
        Files.createDirectories(root.resolve("textures"));
        Files.write(root.resolve("textures/tex.png"), pngOf(TEX_W, TEX_H, 0xFF3366CC));
        Files.write(root.resolve("textures/tex_normal.png"), pngOf(TEX_W, TEX_H, 0xFF8080FF));
        Files.write(root.resolve("textures/tex_specular.png"), pngOf(TEX_W, TEX_H, 0xFF101010));
        Files.write(root.resolve("textures/gui_bg.png"), pngOf(TEX_W, TEX_H, 0xFF202020));
        Files.write(root.resolve("textures/avatar.png"), pngOf(8, 8, 0xFFAA55AA));
        write(root, "sounds/golden.ogg", "SYNTHETIC-OGG-BYTES");
        write(root, "lang/en_us.json", "{\"gui.golden\":\"Golden\"}");
        return root;
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path target = root.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- golden

    @Test
    void ysmFolderPipeline_matchesGolden() throws Exception {
        Path root = writeYsmFolder();

        RawYsmModel raw;
        String folderHashA;
        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(root)) {
            raw = deserializer.deserialize();
            folderHashA = deserializer.getFolderHash();
        }
        String folderHashB;
        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(root)) {
            deserializer.deserialize();
            folderHashB = deserializer.getFolderHash();
        }

        ClientModelInfo info = ClientModelBundleAssembler.buildParsedBundle(raw, "golden_model");
        assertNotNull(info, "装配结果不得为 null");

        RawYsmModel.RawGeometry mainGeo = raw.mainEntity.mainModel;
        int rawBones = mainGeo.bones.size();
        int rawCubes = 0;
        int rawFaces = 0;
        for (RawYsmModel.RawBone bone : mainGeo.bones) {
            rawCubes += bone.cubes.size();
            for (RawYsmModel.RawCube cube : bone.cubes) {
                rawFaces += cube.faces.size();
            }
        }
        RawYsmModel.RawTexture rawTex = raw.mainEntity.textures.values().iterator().next();

        MainModelData data = info.getMainModelData();
        GeoModel mainMesh = data.getModels().get(0);
        GeoModel armMesh = data.getModels().get(1);

        int bakedCubes = 0;
        int bakedQuads = 0;
        for (GeoModel.BakedBone bakedBone : mainMesh.bakedBones) {
            bakedCubes += bakedBone.cubes.size();
            for (GeoModel.BakedCube bakedCube : bakedBone.cubes) {
                bakedQuads += bakedCube.quads.size();
            }
        }

        List<String> animFileKeys = new ArrayList<>(data.getAnimations().keySet());
        TreeSet<String> mainAnimNames = new TreeSet<>(data.getAnimations().get("main").getAnimations().keySet());
        List<String> armAnimNames = new ArrayList<>(data.getAnimations().get("arm").getAnimations().keySet());
        Map<String, OuterFileTexture> textureMap = new HashMap<>();
        for (Map.Entry<String, OuterFileTexture> e : data.getTextureMap().entrySet()) {
            textureMap.put(e.getKey(), e.getValue());
        }
        OuterFileTexture mainTexture = data.getTextureMap().get("tex");
        TreeSet<String> suffixKinds = new TreeSet<>();
        if (mainTexture != null) {
            for (ShadersTextureType type : mainTexture.getSuffixTextures().keySet()) {
                suffixKinds.add(type.name());
            }
        }

        print("ysm.rawBones", rawBones);
        print("ysm.rawCubes", rawCubes);
        print("ysm.rawFaces", rawFaces);
        print("ysm.rawTexName", rawTex.name);
        print("ysm.rawTexFormat", rawTex.imageFormat);
        print("ysm.rawTexW", rawTex.width);
        print("ysm.rawTexH", rawTex.height);
        print("ysm.rawSubTextures", rawTex.subTextures.size());
        print("ysm.rawTexCount", raw.mainEntity.textures.size());
        print("ysm.animFiles", new TreeSet<>(raw.mainEntity.animationFiles.keySet()));
        print("ysm.mainAnimType", raw.mainEntity.animationFiles.get("main").animType);
        print("ysm.controllerFiles", raw.mainEntity.animationControllerFiles.size());
        print("ysm.projectiles", raw.projectiles.size());
        print("ysm.vehicles", raw.vehicles.size());
        print("ysm.sounds", raw.soundFiles.size());
        print("ysm.langs", raw.languageFiles.size());
        print("ysm.textureWidth", mainGeo.textureWidth);
        print("ysm.textureHeight", mainGeo.textureHeight);
        print("ysm.visibleBoundsWidth", mainGeo.visibleBoundsWidth);
        print("ysm.visibleBoundsHeight", mainGeo.visibleBoundsHeight);
        print("ysm.identifier", mainGeo.identifier);
        print("ysm.authorCount", raw.metadata.authors.size());
        print("ysm.authorAvatar", raw.metadata.authors.get(0).avatar);
        print("ysm.folderHashLen", folderHashA.length());
        print("ysm.folderHashDeterministic", folderHashA.equals(folderHashB));

        print("info.models", data.getModels().size());
        print("info.mainMeshBones", mainMesh.bakedBones.size());
        print("info.armMeshBones", armMesh.bakedBones.size());
        print("info.mainMeshCubes", bakedCubes);
        print("info.mainMeshQuads", bakedQuads);
        print("info.textureMapKeys", new TreeSet<>(textureMap.keySet()));
        print("info.textureMapSize", data.getTextureMap().size());
        print("info.mainTextureIsPng", mainTexture != null && TextureDecoder.isPng(mainTexture.getResourceData()));
        print("info.mainTextureSize", mainTexture == null ? null : pngSize(mainTexture.getResourceData()));
        print("info.suffixKinds", suffixKinds);
        print("info.animFileKeys", new TreeSet<>(animFileKeys));
        print("info.mainAnimNames", mainAnimNames);
        print("info.armAnimNames", armAnimNames);
        print("info.controllerFiles", data.getAnimationControllers().size());
        print("info.profile", data.getSpecialHandLocatorProfile().name());
        print("info.statBones", info.getInfo().getMainModelInfo().bones());
        print("info.statCubes", info.getInfo().getMainModelInfo().cubes());
        print("info.statFaces", info.getInfo().getMainModelInfo().faces());
        print("info.metaName", info.getInfo().getExtraInfo().getName());
        print("info.metaAuthor", info.getInfo().getExtraInfo().getAuthors().get(0).getName());
        print("info.formatVersion", info.getInfo().getFormatVersion());
        print("info.defaultTexture", info.getInfo().getModelProperties().getDefaultTexture());
        print("info.extraAnim", new TreeSet<>(info.getInfo().getModelProperties().getExtraAnimation().keySet()));
        print("info.widthScale", info.getInfo().getModelProperties().getWidthScale());
        print("info.heightScale", info.getInfo().getModelProperties().getHeightScale());
        print("info.isFree", info.getInfo().getModelProperties().isFree());
        print("info.avatarTextures", info.getAvatarTextures().size());
        print("info.guiTextures", new TreeSet<>(info.getGuiTextures().keySet()));
        print("info.projectileFiles", info.getExtraItemModels().length);
        print("info.vehicleFiles", info.getVehicleModelFiles().length);
        print("info.meshPropsTextureWidth", mainMesh.getProperties().getTextureWidth());

        if (DISCOVER) {
            return;
        }
        assertEquals(2, rawBones, "raw 主几何骨骼数");
        assertEquals(2, rawCubes, "raw 主几何立方体数");
        assertEquals(12, rawFaces, "raw 主几何面数");
        assertEquals("tex", rawTex.name, "贴图名（去扩展名）");
        assertEquals(2, rawTex.imageFormat, "PNG 嗅探格式 = 2");
        assertEquals(TEX_W, rawTex.width, "贴图宽");
        assertEquals(TEX_H, rawTex.height, "贴图高");
        assertEquals(2, rawTex.subTextures.size(), "法线 + 高光两个子贴图");
        assertEquals(1, raw.mainEntity.textures.size(), "主实体贴图数");
        assertEquals(List.of("arm", "main"), new ArrayList<>(new TreeSet<>(raw.mainEntity.animationFiles.keySet())), "动画文件键");
        assertEquals(1, raw.mainEntity.animationFiles.get("main").animType, "main 动画类型 = 1");
        assertEquals(1, raw.mainEntity.animationControllerFiles.size(), "控制器文件数");
        assertEquals(1, raw.projectiles.size(), "投射物子实体数");
        assertEquals(0, raw.vehicles.size(), "无载具子实体");
        assertEquals(1, raw.soundFiles.size(), "音频资源数");
        assertEquals(1, raw.languageFiles.size(), "语言文件数");
        assertEquals(64.0f, mainGeo.textureWidth, "raw 几何 texture_width");
        assertEquals(64.0f, mainGeo.textureHeight, "raw 几何 texture_height");
        assertEquals(3.0f, mainGeo.visibleBoundsWidth, "visible_bounds_width");
        assertEquals(4.0f, mainGeo.visibleBoundsHeight, "visible_bounds_height");
        assertEquals("geometry.golden_main", mainGeo.identifier, "几何 identifier");
        assertEquals(1, raw.metadata.authors.size(), "作者数");
        assertTrue(folderHashA.equals(folderHashB) && !folderHashA.isEmpty(), "同目录两次解析 folder hash 必须一致");
        assertEquals(32, folderHashA.length(), "folder hash 为 MD5 hex（32 字符）");

        assertEquals(2, data.getModels().size(), "主 + 手臂两条 mesh");
        assertEquals(2, mainMesh.bakedBones.size(), "主 mesh 骨骼数");
        assertEquals(1, armMesh.bakedBones.size(), "手臂 mesh 骨骼数");
        assertEquals(2, bakedCubes, "主 mesh 烘焙立方体数");
        assertEquals(12, bakedQuads, "主 mesh 烘焙四边形数（不透明贴图无面被剔除）");
        assertEquals(1, data.getTextureMap().size(), "纹理映射表大小");
        assertTrue(mainTexture != null && TextureDecoder.isPng(mainTexture.getResourceData()), "主纹理必须是 PNG 字节");
        assertNotNull(mainTexture);
        assertArrayEquals(new int[]{TEX_W, TEX_H}, pngSize(mainTexture.getResourceData()), "主纹理尺寸");
        assertEquals(new TreeSet<>(List.of(ShadersTextureType.NORMAL.name(), ShadersTextureType.SPECULAR.name())), suffixKinds, "NORMAL + SPECULAR 子纹理");
        assertEquals(2, data.getAnimations().size(), "两个动画文件");
        assertEquals(List.of("idle", "wave"), new ArrayList<>(mainAnimNames), "main 动画名集合");
        assertEquals(List.of("arm_swing"), armAnimNames, "arm 动画名集合");
        assertEquals(1, data.getAnimationControllers().size(), "控制器文件数");
        assertEquals(2, info.getInfo().getMainModelInfo().bones(), "元数据骨骼数");
        assertEquals(2, info.getInfo().getMainModelInfo().cubes(), "元数据立方体数");
        assertEquals(12, info.getInfo().getMainModelInfo().faces(), "元数据面数");
        assertEquals("Golden Sample", info.getInfo().getExtraInfo().getName(), "模型名");
        assertEquals("Tester", info.getInfo().getExtraInfo().getAuthors().get(0).getName(), "作者名");
        assertEquals(65535, info.getInfo().getFormatVersion(), "folder 源 formatVersion = 65535");
        assertEquals("tex", info.getInfo().getModelProperties().getDefaultTexture(), "默认贴图");
        assertEquals(0.7f, info.getInfo().getModelProperties().getWidthScale(), "width_scale");
        assertEquals(0.7f, info.getInfo().getModelProperties().getHeightScale(), "height_scale");
        assertTrue(info.getInfo().getModelProperties().isFree(), "free 标记");
        assertEquals(Set.of("dance"), info.getInfo().getModelProperties().getExtraAnimation().keySet(), "extra_animation 集合");
        assertEquals(1, info.getAvatarTextures().size(), "作者头像贴图数");
        assertEquals(Set.of("gui_background"), info.getGuiTextures().keySet(), "GUI 背景贴图");
        assertEquals(1, info.getExtraItemModels().length, "投射物模型文件数");
        assertEquals(0, info.getVehicleModelFiles().length, "无载具模型文件");
        assertEquals(64.0, mainMesh.getProperties().getTextureWidth(), "mesh 上下文 texture_width");
    }

    @Test
    void bbmodelPipeline_matchesGolden() throws Exception {
        BBModelFile bb = BBModelParser.parse(BBMODEL_JSON);
        byte[] skin = pngOf(32, 32, 0xFF88CC44);
        RawYsmModel raw = BBToRawConverter.convert(bb, Map.of("skin", skin));
        // 生产路径（ServerModelManager.parseBbModelImport / importToModelData）在装配前必须补上
        // import cache sha256——ServerModelInfo 由它派生 hashId。此处按同一顺序复现，保证走的是
        // 完整导入管线而非半截。
        raw.properties.sha256 = BBToRawConverter.importCacheSha256(BBMODEL_JSON.getBytes(StandardCharsets.UTF_8));
        ClientModelInfo info = ClientModelBundleAssembler.buildParsedBundle(raw, "golden_bb");

        RawYsmModel.RawGeometry geo = raw.mainEntity.mainModel;
        int cubes = 0;
        int faces = 0;
        for (RawYsmModel.RawBone bone : geo.bones) {
            cubes += bone.cubes.size();
            for (RawYsmModel.RawCube cube : bone.cubes) {
                faces += cube.faces.size();
            }
        }
        MainModelData data = info.getMainModelData();
        TreeSet<String> animNames = new TreeSet<>();
        for (var file : data.getAnimations().values()) {
            animNames.addAll(file.getAnimations().keySet());
        }
        List<String> boneNames = new ArrayList<>();
        for (RawYsmModel.RawBone bone : geo.bones) {
            boneNames.add(bone.name);
        }
        OuterFileTexture tex = data.getTextureMap().values().stream().findFirst().orElse(null);

        print("bb.bones", geo.bones.size());
        print("bb.cubes", cubes);
        print("bb.faces", faces);
        print("bb.boneNames", boneNames);
        print("bb.textureWidth", geo.textureWidth);
        print("bb.textureHeight", geo.textureHeight);
        print("bb.textures", raw.mainEntity.textures.size());
        print("bb.footerExtra", raw.footer.extra);
        print("bb.widthScale", raw.properties.widthScale);
        print("bb.animFiles", new TreeSet<>(raw.mainEntity.animationFiles.keySet()));
        print("bb.animNames", animNames);
        print("bb.controllers", raw.mainEntity.animationControllerFiles.size());
        print("bb.models", data.getModels().size());
        print("bb.infoBones", info.getInfo().getMainModelInfo().bones());
        print("bb.infoCubes", info.getInfo().getMainModelInfo().cubes());
        print("bb.infoFaces", info.getInfo().getMainModelInfo().faces());
        print("bb.textureMapKeys", new TreeSet<>(data.getTextureMap().keySet()));
        print("bb.textureIsPng", tex != null && TextureDecoder.isPng(tex.getResourceData()));
        print("bb.textureSize", tex == null ? null : pngSize(tex.getResourceData()));
        print("bb.profile", data.getSpecialHandLocatorProfile().name());

        if (DISCOVER) {
            return;
        }
        assertEquals(1, geo.bones.size(), "bbmodel 骨骼数");
        assertEquals(1, cubes, "bbmodel 立方体数");
        assertEquals(6, faces, "bbmodel 面数");
        assertEquals(List.of("bb_bone"), boneNames, "bbmodel 骨骼名");
        assertEquals(32.0f, geo.textureWidth, "resolution.width");
        assertEquals(32.0f, geo.textureHeight, "resolution.height");
        assertEquals(1, raw.mainEntity.textures.size(), "bbmodel 贴图数");
        assertEquals("sparkle_morpher:bbmodel_import", raw.footer.extra, "bbmodel 导入标记");
        assertEquals(1.0f, raw.properties.widthScale, "bbmodel 导入缩放置 1");
        assertEquals(1.0f, raw.properties.heightScale, "bbmodel 导入缩放置 1");
        assertEquals(Set.of("animation-main"), raw.mainEntity.animationFiles.keySet(), "bbmodel 动画文件键");
        assertEquals(Set.of("wave", "idle", "walk", "run", "attacked", "death", "swim", "climb", "climbing", "sleep"), animNames,
                "自带动画 wave 保留 + 导入兜底动作补齐");
        assertEquals(0, raw.mainEntity.animationControllerFiles.size(), "样本无控制器 → 0");
        assertEquals(2, data.getModels().size(), "主 + 手臂两条 mesh");
        assertEquals(1, info.getInfo().getMainModelInfo().bones(), "元数据骨骼数");
        assertEquals(1, info.getInfo().getMainModelInfo().cubes(), "元数据立方体数");
        assertEquals(6, info.getInfo().getMainModelInfo().faces(), "元数据面数");
        assertEquals(1, data.getTextureMap().size(), "bbmodel 纹理映射表大小");
        assertEquals(Set.of("skin"), data.getTextureMap().keySet(), "bbmodel 纹理键（borne name）");
        assertTrue(tex != null && TextureDecoder.isPng(tex.getResourceData()), "bbmodel 纹理必须是 PNG 字节");
        assertNotNull(tex);
        assertArrayEquals(new int[]{32, 32}, pngSize(tex.getResourceData()), "bbmodel 纹理尺寸");
    }

    private static void print(String key, Object value) {
        System.out.println("[GOLDEN] " + key + "=" + value);
    }
}
