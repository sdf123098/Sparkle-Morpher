package com.micaftic.morpher.resource.bbmodel;

import com.google.gson.*;

import java.util.*;

/**
 * Blockbench {@code .bbmodel} 文件解析器。
 *
 * <p>把 JSON 字符串解析为 {@link BBModelFile} 对象，严格按 Blockbench 真实导出 schema：</p>
 *
 * <ul>
 *   <li>顶层只有 {@code outliner[]}，<b>没有</b>独立的 {@code groups[]} 数组；
 *       bone 层级嵌套在 outliner 树里。</li>
 *   <li>{@code elements[]} 平铺所有 cube/mesh/locator/null_object，
 *       outliner 通过 element UUID 字符串引用它们。</li>
 *   <li>{@code animation_controllers[].states} 是<b>数组</b>而非对象，顺序有意义。</li>
 *   <li>{@code cube.faces} 的子项是 {@link BBElement.BBFace}（含 {@code uv: [x1,y1,x2,y2]}）；
 *       {@code mesh.faces} 的子项是 {@link BBElement.BBMeshFace}。</li>
 * </ul>
 */
public class BBModelParser {
    private BBModelParser() {}

    /** 解析 bbmodel JSON 字符串。 */
    public static BBModelFile parse(String json) throws JsonSyntaxException {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        BBModelFile model = new BBModelFile();

        // ---------- 顶层标量 / 元数据 ----------
        if (root.has("meta") && root.get("meta").isJsonObject()) {
            JsonObject metaObj = root.getAsJsonObject("meta");
            if (metaObj.has("format_version")) model.meta.format_version = metaObj.get("format_version").getAsString();
            if (metaObj.has("model_format")) model.meta.model_format = metaObj.get("model_format").getAsString();
            if (metaObj.has("box_uv")) model.meta.box_uv = metaObj.get("box_uv").getAsBoolean();
            if (metaObj.has("backup")) model.meta.backup = metaObj.get("backup").getAsBoolean();
            if (metaObj.has("creation_time")) model.meta.creation_time = metaObj.get("creation_time").getAsLong();
        }

        if (root.has("resolution") && root.get("resolution").isJsonObject()) {
            JsonObject resObj = root.getAsJsonObject("resolution");
            if (resObj.has("width")) model.resolution.width = resObj.get("width").getAsInt();
            if (resObj.has("height")) model.resolution.height = resObj.get("height").getAsInt();
        }

        if (root.has("name")) model.name = root.get("name").getAsString();
        if (root.has("model_identifier")) model.model_identifier = root.get("model_identifier").getAsString();
        if (root.has("geometry_name")) model.geometry_name = root.get("geometry_name").getAsString();
        if (root.has("box_uv")) model.box_uv = root.get("box_uv").getAsBoolean();
        if (root.has("parent_model_id")) model.parent_model_id = root.get("parent_model_id").getAsString();

        // ---------- elements[]: 所有叶子元素（cube/mesh/locator/null_object） ----------
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("elements")) {
                if (el.isJsonObject()) {
                    model.elements.add(parseElement(el.getAsJsonObject()));
                }
            }
        }

        // ---------- outliner[]: 嵌套 group 树，bone 层级在这里 ----------
        // 注意：真实 bbmodel 没有顶层 groups[] 字段，groups 全在 outliner 里内联。
        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            for (JsonElement node : root.getAsJsonArray("outliner")) {
                BBOutlinerNode parsed = parseOutlinerNode(node);
                if (parsed != null) {
                    model.outliner.add(parsed);
                }
            }
        }

        // ---------- groups[]: 可选 "瘦" outliner 的元数据补充 ----------
        // 当 outliner 节点是 {uuid, isOpen, children} 时，bone 名字/origin/rotation 来自这里。
        if (root.has("groups") && root.get("groups").isJsonArray()) {
            for (JsonElement g : root.getAsJsonArray("groups")) {
                if (g.isJsonObject()) {
                    model.groups.add(parseTopLevelGroup(g.getAsJsonObject()));
                }
            }
        }

        // ---------- textures[] ----------
        if (root.has("textures") && root.get("textures").isJsonArray()) {
            for (JsonElement tex : root.getAsJsonArray("textures")) {
                if (tex.isJsonObject()) {
                    model.textures.add(parseTexture(tex.getAsJsonObject()));
                }
            }
        }

        // ---------- animations[] ----------
        if (root.has("animations") && root.get("animations").isJsonArray()) {
            for (JsonElement anim : root.getAsJsonArray("animations")) {
                if (anim.isJsonObject()) {
                    model.animations.add(parseAnimation(anim.getAsJsonObject()));
                }
            }
        }

        // ---------- animation_controllers[] ----------
        if (root.has("animation_controllers") && root.get("animation_controllers").isJsonArray()) {
            for (JsonElement ctrl : root.getAsJsonArray("animation_controllers")) {
                if (ctrl.isJsonObject()) {
                    model.animation_controllers.add(parseAnimationController(ctrl.getAsJsonObject()));
                }
            }
        }

        return model;
    }

    // ============================================================
    // Element 解析
    // ============================================================

    private static BBElement parseElement(JsonObject obj) {
        BBElement element = new BBElement();

        if (obj.has("uuid")) element.uuid = obj.get("uuid").getAsString();
        if (obj.has("type")) element.type = obj.get("type").getAsString();
        if (obj.has("name")) element.name = obj.get("name").getAsString();
        if (obj.has("visibility")) element.visibility = obj.get("visibility").getAsBoolean();
        if (obj.has("locked")) element.locked = obj.get("locked").getAsBoolean();

        // Cube 公共属性
        if (obj.has("from")) element.from = parseFloatArray(obj.getAsJsonArray("from"));
        if (obj.has("to")) element.to = parseFloatArray(obj.getAsJsonArray("to"));
        if (obj.has("rotation")) element.rotation = parseFloatArray(obj.getAsJsonArray("rotation"));
        if (obj.has("origin")) element.origin = parseFloatArray(obj.getAsJsonArray("origin"));
        if (obj.has("mirror_uv")) element.mirror_uv = obj.get("mirror_uv").getAsBoolean();
        if (obj.has("shade")) element.shade = obj.get("shade").getAsBoolean();
        if (obj.has("inflate")) element.inflate = obj.get("inflate").getAsFloat();
        if (obj.has("rescale")) {
            JsonElement rescale = obj.get("rescale");
            if (rescale.isJsonPrimitive()) {
                if (rescale.getAsJsonPrimitive().isBoolean()) {
                    element.rescale = rescale.getAsBoolean() ? 1 : 0;
                } else {
                    element.rescale = rescale.getAsInt();
                }
            }
        }

        // Mesh 顶点
        if ("mesh".equals(element.type) && obj.has("vertices") && obj.get("vertices").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("vertices").entrySet()) {
                JsonElement v = entry.getValue();
                if (v.isJsonArray()) {
                    // mesh 顶点也可以是直接的 [x,y,z]
                    BBElement.BBMeshVertex vertex = new BBElement.BBMeshVertex();
                    vertex.position = parseFloatArray(v.getAsJsonArray());
                    element.vertices.put(entry.getKey(), vertex);
                } else if (v.isJsonObject()) {
                    element.vertices.put(entry.getKey(), parseMeshVertex(v.getAsJsonObject()));
                }
            }
        }

        // 面：cube 和 mesh 的 schema 不同
        if (obj.has("faces") && obj.get("faces").isJsonObject()) {
            JsonObject facesObj = obj.getAsJsonObject("faces");
            if ("mesh".equals(element.type)) {
                for (Map.Entry<String, JsonElement> entry : facesObj.entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        element.faces.put(entry.getKey(), parseMeshFace(entry.getValue().getAsJsonObject()));
                    }
                }
            } else {
                for (Map.Entry<String, JsonElement> entry : facesObj.entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        element.cube_faces.put(entry.getKey(), parseCubeFace(entry.getValue().getAsJsonObject()));
                    }
                }
            }
        }

        // Locator 属性
        if (obj.has("position")) element.position = parseFloatArray(obj.getAsJsonArray("position"));
        if (obj.has("render_order")) {
            JsonElement ro = obj.get("render_order");
            if (ro.isJsonPrimitive() && ro.getAsJsonPrimitive().isBoolean()) {
                element.render_order = ro.getAsBoolean();
            }
        }

        return element;
    }

    private static BBElement.BBMeshVertex parseMeshVertex(JsonObject obj) {
        BBElement.BBMeshVertex vertex = new BBElement.BBMeshVertex();
        if (obj.has("position")) vertex.position = parseFloatArray(obj.getAsJsonArray("position"));
        if (obj.has("visible") && obj.get("visible").isJsonArray()) {
            JsonArray visibleArray = obj.getAsJsonArray("visible");
            for (int i = 0; i < Math.min(3, visibleArray.size()); i++) {
                vertex.visible[i] = visibleArray.get(i).getAsBoolean();
            }
        }
        return vertex;
    }

    private static BBElement.BBMeshFace parseMeshFace(JsonObject obj) {
        BBElement.BBMeshFace face = new BBElement.BBMeshFace();
        if (obj.has("vertices") && obj.get("vertices").isJsonArray()) {
            JsonArray verticesArray = obj.getAsJsonArray("vertices");
            face.vertices = new String[verticesArray.size()];
            for (int i = 0; i < verticesArray.size(); i++) {
                face.vertices[i] = verticesArray.get(i).getAsString();
            }
        }
        if (obj.has("uv") && obj.get("uv").isJsonObject()) {
            // mesh face 的 uv 是 { vertexKey: [u, v] } 对象，而不是数组
            JsonObject uvObj = obj.getAsJsonObject("uv");
            face.uv = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : uvObj.entrySet()) {
                if (entry.getValue().isJsonArray()) {
                    JsonArray uvPoint = entry.getValue().getAsJsonArray();
                    if (uvPoint.size() >= 2) {
                        face.uv.put(entry.getKey(),
                                new float[]{uvPoint.get(0).getAsFloat(), uvPoint.get(1).getAsFloat()});
                    }
                }
            }
        }
        if (obj.has("texture")) {
            JsonElement t = obj.get("texture");
            if (t.isJsonPrimitive()) {
                face.texture = t.getAsString();
            }
        }
        return face;
    }

    private static BBElement.BBFace parseCubeFace(JsonObject obj) {
        BBElement.BBFace face = new BBElement.BBFace();
        if (obj.has("uv") && obj.get("uv").isJsonArray()) {
            face.uv = parseFloatArray(obj.getAsJsonArray("uv"));
        }
        if (obj.has("texture")) {
            JsonElement t = obj.get("texture");
            if (t.isJsonPrimitive()) {
                // texture 可能是 int（索引）或 string；统一存为字符串
                face.texture = t.getAsString();
            }
        }
        if (obj.has("rotation")) face.rotation = obj.get("rotation").getAsInt();
        if (obj.has("enabled")) face.enabled = obj.get("enabled").getAsBoolean();
        if (obj.has("cullface")) face.cullface = obj.get("cullface").getAsString();
        if (obj.has("tint")) face.tint = obj.get("tint").getAsInt();
        return face;
    }

    // ============================================================
    // Outliner 解析（递归）
    // ============================================================

    private static BBOutlinerNode parseOutlinerNode(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            // 叶子：element UUID 字符串
            return BBOutlinerNode.forElement(element.getAsString());
        }
        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject obj = element.getAsJsonObject();
        BBOutlinerNode node = new BBOutlinerNode();
        node.elementRef = false;

        if (obj.has("uuid")) node.uuid = obj.get("uuid").getAsString();
        if (obj.has("name")) node.name = obj.get("name").getAsString();
        if (obj.has("isOpen")) node.isOpen = obj.get("isOpen").getAsBoolean();
        if (obj.has("visibility")) node.visibility = obj.get("visibility").getAsBoolean();
        if (obj.has("locked")) node.locked = obj.get("locked").getAsBoolean();
        if (obj.has("autouv")) {
            JsonElement au = obj.get("autouv");
            if (au.isJsonPrimitive()) {
                if (au.getAsJsonPrimitive().isBoolean()) {
                    node.autouv = au.getAsBoolean();
                } else if (au.getAsJsonPrimitive().isNumber()) {
                    node.autouv = au.getAsInt() != 0;
                }
            }
        }
        if (obj.has("export")) node.export = obj.get("export").getAsBoolean();
        if (obj.has("mirror_uv")) node.mirror_uv = obj.get("mirror_uv").getAsBoolean();
        if (obj.has("origin") && obj.get("origin").isJsonArray()) {
            node.origin = parseFloatArray(obj.getAsJsonArray("origin"));
        }
        if (obj.has("rotation") && obj.get("rotation").isJsonArray()) {
            node.rotation = parseFloatArray(obj.getAsJsonArray("rotation"));
        }

        if (obj.has("children") && obj.get("children").isJsonArray()) {
            for (JsonElement child : obj.getAsJsonArray("children")) {
                BBOutlinerNode parsed = parseOutlinerNode(child);
                if (parsed != null) {
                    node.children.add(parsed);
                }
            }
        }

        return node;
    }

    // ============================================================
    // Top-level groups[] (Figura "瘦" outliner 元数据补充)
    // ============================================================

    private static BBGroup parseTopLevelGroup(JsonObject obj) {
        BBGroup g = new BBGroup();
        if (obj.has("uuid")) g.uuid = obj.get("uuid").getAsString();
        if (obj.has("name")) g.name = obj.get("name").getAsString();
        if (obj.has("visibility")) g.visibility = obj.get("visibility").getAsBoolean();
        if (obj.has("locked")) g.locked = obj.get("locked").getAsBoolean();
        if (obj.has("autouv")) {
            JsonElement au = obj.get("autouv");
            if (au.isJsonPrimitive()) {
                if (au.getAsJsonPrimitive().isBoolean()) g.autouv = au.getAsBoolean();
                else if (au.getAsJsonPrimitive().isNumber()) g.autouv = au.getAsInt() != 0;
            }
        }
        if (obj.has("export")) g.export = obj.get("export").getAsBoolean();
        if (obj.has("mirror_uv")) g.mirror_uv = obj.get("mirror_uv").getAsBoolean();
        if (obj.has("isOpen")) g.isOpen = obj.get("isOpen").getAsBoolean();
        if (obj.has("origin") && obj.get("origin").isJsonArray()) {
            g.origin = parseFloatArray(obj.getAsJsonArray("origin"));
        }
        if (obj.has("rotation") && obj.get("rotation").isJsonArray()) {
            g.rotation = parseFloatArray(obj.getAsJsonArray("rotation"));
        }
        if (obj.has("color") && obj.get("color").isJsonArray()) {
            g.color = parseFloatArray(obj.getAsJsonArray("color"));
        }
        if (obj.has("parent")) g.parent = obj.get("parent").getAsString();
        if (obj.has("render_order") && obj.get("render_order").isJsonPrimitive()
                && obj.getAsJsonPrimitive("render_order").isNumber()) {
            g.render_order = obj.get("render_order").getAsInt();
        }
        if (obj.has("children") && obj.get("children").isJsonArray()) {
            for (JsonElement child : obj.getAsJsonArray("children")) {
                if (child.isJsonPrimitive()) {
                    g.children.add(child.getAsString());
                }
            }
        }
        return g;
    }

    // ============================================================
    // Texture 解析
    // ============================================================

    private static BBTexture parseTexture(JsonObject obj) {
        BBTexture texture = new BBTexture();
        if (obj.has("uuid")) texture.uuid = obj.get("uuid").getAsString();
        if (obj.has("name")) texture.name = obj.get("name").getAsString();
        if (obj.has("source")) texture.source = obj.get("source").getAsString();
        if (obj.has("path")) texture.path = obj.get("path").getAsString();
        if (obj.has("relative_path")) texture.relative_path = obj.get("relative_path").getAsString();
        if (obj.has("frames") && obj.get("frames").isJsonArray()) {
            JsonArray framesArray = obj.getAsJsonArray("frames");
            texture.frames = new int[framesArray.size()];
            for (int i = 0; i < framesArray.size(); i++) {
                texture.frames[i] = framesArray.get(i).getAsInt();
            }
        }
        if (obj.has("frame_time")) texture.frame_time = obj.get("frame_time").getAsInt();
        if (obj.has("visible")) texture.visible = obj.get("visible").getAsBoolean();
        if (obj.has("internal")) texture.internal = obj.get("internal").getAsBoolean();
        if (obj.has("id")) {
            JsonElement idEl = obj.get("id");
            if (idEl.isJsonPrimitive()) {
                if (idEl.getAsJsonPrimitive().isNumber()) {
                    texture.id = idEl.getAsInt();
                } else {
                    try { texture.id = Integer.parseInt(idEl.getAsString()); } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (obj.has("uv_width")) texture.uv_width = obj.get("uv_width").getAsInt();
        if (obj.has("uv_height")) texture.uv_height = obj.get("uv_height").getAsInt();
        if (obj.has("width")) texture.width = obj.get("width").getAsInt();
        if (obj.has("height")) texture.height = obj.get("height").getAsInt();
        return texture;
    }

    // ============================================================
    // Animation 解析
    // ============================================================

    private static BBAnimation parseAnimation(JsonObject obj) {
        BBAnimation animation = new BBAnimation();
        if (obj.has("uuid")) animation.uuid = obj.get("uuid").getAsString();
        if (obj.has("name")) animation.name = obj.get("name").getAsString();
        if (obj.has("loop")) {
            JsonElement loopEl = obj.get("loop");
            if (loopEl.isJsonPrimitive()) {
                if (loopEl.getAsJsonPrimitive().isBoolean()) {
                    animation.loop = loopEl.getAsBoolean();
                } else if (loopEl.getAsJsonPrimitive().isString()) {
                    // Blockbench 写成 "loop" / "once" / "hold"
                    animation.loop = "loop".equalsIgnoreCase(loopEl.getAsString());
                    animation.loopMode = loopEl.getAsString();
                }
            }
        }
        if (obj.has("length")) animation.length = obj.get("length").getAsFloat();
        if (obj.has("selected")) animation.selected = obj.get("selected").getAsBoolean();
        if (obj.has("snapping")) animation.snapping = obj.get("snapping").getAsFloat();
        if (obj.has("path")) animation.path = obj.get("path").getAsString();
        if (obj.has("anim_time_update")) {
            JsonElement el = obj.get("anim_time_update");
            // 这两个字段在真实文件里通常是 Molang 字符串，仅在 schema 是 bool 时取 bool
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
                animation.anim_time_update = el.getAsBoolean();
            }
        }
        if (obj.has("blend_weight")) {
            JsonElement el = obj.get("blend_weight");
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
                animation.blend_weight = el.getAsBoolean();
            }
        }
        if (obj.has("override")) animation.override = obj.get("override").getAsBoolean();

        // animators: { groupUuid: { name, type, keyframes[] } }
        if (obj.has("animators") && obj.get("animators").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("animators").entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    animation.animators.put(entry.getKey(), parseAnimator(entry.getValue().getAsJsonObject()));
                }
            }
        }

        return animation;
    }

    private static BBAnimation.BBAnimator parseAnimator(JsonObject obj) {
        BBAnimation.BBAnimator animator = new BBAnimation.BBAnimator();
        if (obj.has("name")) animator.name = obj.get("name").getAsString();
        if (obj.has("type")) animator.type = obj.get("type").getAsString();
        if (obj.has("keyframes") && obj.get("keyframes").isJsonArray()) {
            for (JsonElement kf : obj.getAsJsonArray("keyframes")) {
                if (kf.isJsonObject()) {
                    animator.keyframes.add(parseKeyframe(kf.getAsJsonObject()));
                }
            }
        }
        return animator;
    }

    private static BBAnimation.BBKeyframe parseKeyframe(JsonObject obj) {
        BBAnimation.BBKeyframe keyframe = new BBAnimation.BBKeyframe();
        if (obj.has("channel")) keyframe.channel = obj.get("channel").getAsString();
        if (obj.has("time")) keyframe.time = obj.get("time").getAsFloat();
        if (obj.has("interpolation")) keyframe.interpolation = obj.get("interpolation").getAsString();
        if (obj.has("bezier_linked")) keyframe.bezier_linked = obj.get("bezier_linked").getAsBoolean();
        if (obj.has("bezier_left_value") && obj.get("bezier_left_value").isJsonArray())
            keyframe.bezier_left_value = parseFloatArray(obj.getAsJsonArray("bezier_left_value"));
        if (obj.has("bezier_right_value") && obj.get("bezier_right_value").isJsonArray())
            keyframe.bezier_right_value = parseFloatArray(obj.getAsJsonArray("bezier_right_value"));
        if (obj.has("bezier_left_time") && obj.get("bezier_left_time").isJsonArray())
            keyframe.bezier_left_time = parseFloatArray(obj.getAsJsonArray("bezier_left_time"));
        if (obj.has("bezier_right_time") && obj.get("bezier_right_time").isJsonArray())
            keyframe.bezier_right_time = parseFloatArray(obj.getAsJsonArray("bezier_right_time"));
        if (obj.has("uuid")) keyframe.uuid = obj.get("uuid").getAsString();

        if (obj.has("data_points") && obj.get("data_points").isJsonArray()) {
            for (JsonElement dp : obj.getAsJsonArray("data_points")) {
                if (dp.isJsonObject()) {
                    keyframe.data_points.add(parseDataPoint(dp.getAsJsonObject()));
                }
            }
        }

        return keyframe;
    }

    private static BBAnimation.BBDataPoint parseDataPoint(JsonObject obj) {
        BBAnimation.BBDataPoint dataPoint = new BBAnimation.BBDataPoint();
        // Blockbench 把数值字段都存成字符串（支持 Molang 表达式），但偶尔也存数字 —— 都接住
        dataPoint.x = readMolangScalar(obj, "x");
        dataPoint.y = readMolangScalar(obj, "y");
        dataPoint.z = readMolangScalar(obj, "z");
        dataPoint.w = readMolangScalar(obj, "w");
        return dataPoint;
    }

    private static String readMolangScalar(JsonObject obj, String key) {
        if (!obj.has(key)) return "";
        JsonElement v = obj.get(key);
        if (v.isJsonNull()) return "";
        if (v.isJsonPrimitive()) {
            return v.getAsString();
        }
        return v.toString();
    }

    // ============================================================
    // AnimationController 解析
    // ============================================================

    private static BBAnimationController parseAnimationController(JsonObject obj) {
        BBAnimationController controller = new BBAnimationController();
        if (obj.has("uuid")) controller.uuid = obj.get("uuid").getAsString();
        if (obj.has("name")) controller.name = obj.get("name").getAsString();
        if (obj.has("selected")) controller.selected = obj.get("selected").getAsBoolean();
        if (obj.has("path")) controller.path = obj.get("path").getAsString();
        if (obj.has("initial_state")) controller.initial_state = obj.get("initial_state").getAsString();

        // Blockbench 的 states 既可能是数组（最新格式，顺序有意义）
        // 也可能在旧 plugin 里写成 { stateName: {...} } 对象 —— 两者都接住。
        if (obj.has("states")) {
            JsonElement statesEl = obj.get("states");
            if (statesEl.isJsonArray()) {
                for (JsonElement s : statesEl.getAsJsonArray()) {
                    if (s.isJsonObject()) {
                        BBAnimationController.BBControllerState state = parseControllerState(s.getAsJsonObject());
                        controller.states.put(state.name, state);
                        controller.stateOrder.add(state.name);
                    }
                }
            } else if (statesEl.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : statesEl.getAsJsonObject().entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        BBAnimationController.BBControllerState state = parseControllerState(entry.getValue().getAsJsonObject());
                        if (state.name == null || state.name.isEmpty()) {
                            state.name = entry.getKey();
                        }
                        controller.states.put(state.name, state);
                        controller.stateOrder.add(state.name);
                    }
                }
            }
        }

        return controller;
    }

    private static BBAnimationController.BBControllerState parseControllerState(JsonObject obj) {
        BBAnimationController.BBControllerState state = new BBAnimationController.BBControllerState();
        if (obj.has("name")) state.name = obj.get("name").getAsString();
        if (obj.has("uuid")) state.uuid = obj.get("uuid").getAsString();

        if (obj.has("animations") && obj.get("animations").isJsonArray()) {
            // Blockbench 的 animations 可能是 [{ animUuid: blendValue }] 也可能是 ["uuid", ...]
            for (JsonElement a : obj.getAsJsonArray("animations")) {
                if (a.isJsonPrimitive()) {
                    state.animations.add(a.getAsString());
                } else if (a.isJsonObject()) {
                    // 取首个键作为引用
                    for (Map.Entry<String, JsonElement> ent : a.getAsJsonObject().entrySet()) {
                        state.animations.add(ent.getKey());
                        break;
                    }
                }
            }
        }

        if (obj.has("transitions") && obj.get("transitions").isJsonArray()) {
            for (JsonElement t : obj.getAsJsonArray("transitions")) {
                if (t.isJsonObject()) {
                    BBAnimationController.BBControllerTransition tr = new BBAnimationController.BBControllerTransition();
                    JsonObject tObj = t.getAsJsonObject();
                    // 真实格式: { "targetStateName": "condition expression" }，单键对象
                    for (Map.Entry<String, JsonElement> ent : tObj.entrySet()) {
                        if ("target".equals(ent.getKey()) && tObj.has("condition")) {
                            tr.target = ent.getValue().getAsString();
                            tr.condition = tObj.get("condition").getAsString();
                            break;
                        }
                        tr.target = ent.getKey();
                        if (ent.getValue().isJsonPrimitive()) {
                            tr.condition = ent.getValue().getAsString();
                        }
                        break;
                    }
                    state.transitions.add(tr);
                }
            }
        }

        if (obj.has("on_entry")) state.on_entry.addAll(scalarOrArray(obj.get("on_entry")));
        if (obj.has("on_exit")) state.on_exit.addAll(scalarOrArray(obj.get("on_exit")));

        return state;
    }

    private static List<String> scalarOrArray(JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el == null || el.isJsonNull()) return out;
        if (el.isJsonPrimitive()) {
            // Molang 字符串里可能带分号分隔多条语句
            String s = el.getAsString();
            if (!s.isEmpty()) {
                for (String line : s.split(";")) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) out.add(trimmed);
                }
            }
        } else if (el.isJsonArray()) {
            for (JsonElement x : el.getAsJsonArray()) {
                if (x.isJsonPrimitive()) out.add(x.getAsString());
            }
        }
        return out;
    }

    // ============================================================
    // 工具
    // ============================================================

    private static float[] parseFloatArray(JsonArray array) {
        float[] result = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            JsonElement el = array.get(i);
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
                result[i] = el.getAsFloat();
            } else if (el.isJsonPrimitive()) {
                try { result[i] = Float.parseFloat(el.getAsString()); } catch (NumberFormatException ignored) {}
            }
        }
        return result;
    }
}
