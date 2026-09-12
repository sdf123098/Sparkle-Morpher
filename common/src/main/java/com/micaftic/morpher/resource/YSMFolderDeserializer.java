package com.micaftic.morpher.resource;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

import static com.micaftic.morpher.util.DigestUtil.md5Hex;
import static com.micaftic.morpher.util.DigestUtil.sha256Hex;

public class YSMFolderDeserializer implements AutoCloseable {
    private final Map<String, String> readFilesMd5Map = new TreeMap<>();
    private String finalFolderHash;
    private final ModelResourceContainer container;
    private final RawYsmModel model;

    public YSMFolderDeserializer(Path sourcePath) throws IOException {
        if (!Files.exists(sourcePath)) {
            throw new FileNotFoundException("Model source not found: " + sourcePath);
        }
        if (Files.isDirectory(sourcePath)) {
            // R4.2：folder 源统一走 ModelResourceContainer（懒加载：构造只收集条目清单，读取时实时读文件）
            this.container = ModelResourceContainer.folder(sourcePath);
        } else if (sourcePath.toString().endsWith(".zip") || sourcePath.toString().endsWith(".ysm")) {
            // R4.2：zip/ysm 统一走 ModelResourceContainer（ZipFile + GBK 回退 + 限额 warn-skip
            // + 模型根探测剥离）。不再使用 zipfs——测试环境与生产路径行为一致。
            this.container = ModelResourceContainer.zip(sourcePath);
        } else {
            throw new IllegalArgumentException("Unsupported file type. Expected directory or .zip");
        }

        this.model = new RawYsmModel();
        this.model.formatVersion = 65535;
    }

    /** 仅用于静态探测（parseBedrockGeometry 等不需要读取任何 zip/目录资源）。 */
    private YSMFolderDeserializer() {
        this.container = null;
        this.model = new RawYsmModel();
        this.model.formatVersion = 65535;
    }

    public YSMFolderDeserializer(Map<String, byte[]> memoryFiles) {
        // R4.2：memory 源统一走 container（大小写归一冲突检测在 container.memory 内）
        this.container = ModelResourceContainer.memory(memoryFiles);
        this.model = new RawYsmModel();
        this.model.formatVersion = 65535;
    }

    private byte[] readResource(String relativePath) {
        if (container == null) return null; // 静态探测构造（parseBedrockGeometry 等不读资源）
        if (relativePath == null || relativePath.isEmpty()) return null;
        relativePath = YsmJsonSupport.cleanJsonString(relativePath);
        if (relativePath.isEmpty()) return null;
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        try {
            // R4.2：sandbox（词法逃逸拒绝）+ case-insensitive 回退 + 限额统一由 container 负责
            byte[] data = container.read(relativePath);
            if (data != null) {
                String key = normalizeResourceKey(relativePath);
                if (!readFilesMd5Map.containsKey(key)) {
                    readFilesMd5Map.put(key, md5Hex(data));
                }
            }
            return data;
        } catch (Exception e) {
            System.err.println("[SM] Warning: Failed to read resource: " + relativePath);
        }
        return null;
    }

    private static String normalizeResourceKey(String path) {
        return path.replace('\\', '/').replaceAll("^/+|/+$", "").replaceAll("/+", "/").toLowerCase(Locale.ROOT);
    }

    public RawYsmModel deserialize() {
        byte[] ysmJsonBytes = readResource("ysm.json");
        if (ysmJsonBytes != null) {  // https://ysm.cfpa.team/wiki/struct/#%E6%96%87%E4%BB%B6%E7%9B%AE%E5%BD%95%E7%BB%93%E6%9E%84
            try {
                String jsonStr = new String(ysmJsonBytes, StandardCharsets.UTF_8);
                JsonObject ysmJson = JsonParser.parseString(jsonStr).getAsJsonObject();
                parseYsmJson(ysmJson);
            } catch (Exception e) {
                System.err.println("[SM] Warning: Failed to parse ysm.json, falling back to legacy model scan. " + e.getMessage());
                parseLegacyFormat();
            }
        } else parseLegacyFormat();

        parseGlobalResources();

        this.finalFolderHash = calculateFinalFolderHash();
        model.properties.sha256 = finalFolderHash;

        model.footer.version = 65535;
        return model;
    }

    @Override
    public void close() {
        if (container != null) container.close();
    }

    private void parseYsmJson(JsonObject ysmJson) {
        if (ysmJson.has("metadata")) parseMetadata(ysmJson.getAsJsonObject("metadata"));
        if (ysmJson.has("properties")) parseProperties(ysmJson.getAsJsonObject("properties"));
        if (ysmJson.has("files")) {
            JsonObject files = ysmJson.getAsJsonObject("files");
            if (files.has("player")) parseMainEntity(files.getAsJsonObject("player"));
            if (files.has("vehicles")) parseSubEntities(files.get("vehicles"), model.vehicles, "vehicle");
            if (files.has("projectiles")) parseSubEntities(files.get("projectiles"), model.projectiles, "projectile");
        }
    }

    private void parseMetadata(JsonObject metaObj) {
        model.metadata.name = YsmJsonSupport.getStr(metaObj, "name", "");
        model.metadata.tips = YsmJsonSupport.getStr(metaObj, "tips", "");
        if (metaObj.has("license") && metaObj.get("license").isJsonObject()) {
            JsonObject licObj = metaObj.getAsJsonObject("license");
            model.metadata.licenseType = YsmJsonSupport.getStr(licObj, "type", "");
            model.metadata.licenseDescription = YsmJsonSupport.getStr(licObj, "desc", "");
        }

        if (metaObj.has("authors") && metaObj.get("authors").isJsonArray()) {
            for (JsonElement elem : metaObj.getAsJsonArray("authors")) {
                if (!elem.isJsonObject()) continue;
                JsonObject authorObj = elem.getAsJsonObject();
                RawYsmModel.RawMetadata.Author author = new RawYsmModel.RawMetadata.Author();
                author.name = YsmJsonSupport.getStr(authorObj, "name", "");
                author.role = YsmJsonSupport.getStr(authorObj, "role", "");
                author.comment = YsmJsonSupport.getStr(authorObj, "comment", "");

                if (authorObj.has("contact") && authorObj.get("contact").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> cEntry : authorObj.getAsJsonObject("contact").entrySet()) {
                        author.contacts.put(cEntry.getKey(), YsmJsonSupport.getJsonString(cEntry.getValue()));
                    }
                }

                if (authorObj.has("avatar")) {
                    String avatarPath = YsmJsonSupport.getStr(authorObj, "avatar", "");
                    if (!avatarPath.isEmpty()) {
                        byte[] avatarData = readResource(avatarPath);
                        if (avatarData != null) {
                            ImageMeta meta = YsmTextureParsing.parseImageMeta(avatarData, avatarPath);
                            RawYsmModel.RawImage img = new RawYsmModel.RawImage();
                            img.width = meta.width();
                            img.height = meta.height();
                            img.format = meta.format();
                            img.name = author.name;
                            img.data = avatarData;
                            img.unknownFlag = 1;

                            author.avatar = avatarPath;
                            author.avatarImage = img;
                        }
                    }
                }
                model.metadata.authors.add(author);
            }
        }

        if (metaObj.has("link") && metaObj.get("link").isJsonObject()) {
            for (Map.Entry<String, JsonElement> linkEntry : metaObj.getAsJsonObject("link").entrySet()) {
                model.metadata.links.put(linkEntry.getKey(), YsmJsonSupport.getJsonString(linkEntry.getValue()));
            }
        }
    }

    private void parseProperties(JsonObject propsObj) {
        model.properties.widthScale = (float) YsmJsonSupport.getDouble(propsObj, "width_scale", 0.7);
        model.properties.heightScale = (float) YsmJsonSupport.getDouble(propsObj, "height_scale", 0.7);
        model.properties.defaultTexture = YsmJsonSupport.getStr(propsObj, "default_texture", "default");
        model.properties.previewAnimation = YsmJsonSupport.getStr(propsObj, "preview_animation", "");
        model.properties.isFree = YsmJsonSupport.getBool(propsObj, "free", false);
        model.properties.renderLayersFirst = YsmJsonSupport.getBool(propsObj, "render_layers_first", false);
        model.properties.allCutout = YsmJsonSupport.getBool(propsObj, "all_cutout", false);
        model.properties.disablePreviewRotation = YsmJsonSupport.getBool(propsObj, "disable_preview_rotation", false);
        model.properties.guiNoLighting = YsmJsonSupport.getBool(propsObj, "gui_no_lighting", false);
        model.properties.mergeMultilineExpr = YsmJsonSupport.getBool(propsObj, "merge_multiline_expr", false);
        model.properties.guiForeground = YsmJsonSupport.getStr(propsObj, "gui_foreground", "");
        model.properties.guiBackground = YsmJsonSupport.getStr(propsObj, "gui_background", "");
        if (propsObj.has("extra_animation") && propsObj.get("extra_animation").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : propsObj.getAsJsonObject("extra_animation").entrySet()) {
                model.properties.extraAnimations.put(entry.getKey(), YsmJsonSupport.getJsonString(entry.getValue()));
            }
        }

        if (propsObj.has("extra_animation_classify") && propsObj.get("extra_animation_classify").isJsonArray()) {
            for (JsonElement elem : propsObj.getAsJsonArray("extra_animation_classify")) {
                if (!elem.isJsonObject()) continue;
                JsonObject clsObj = elem.getAsJsonObject();
                RawYsmModel.ExtraAnimationClassify classify = new RawYsmModel.ExtraAnimationClassify();
                classify.id = YsmJsonSupport.getStr(clsObj, "id", "");
                if (clsObj.has("extra_animation") && clsObj.get("extra_animation").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : clsObj.getAsJsonObject("extra_animation").entrySet()) {
                        classify.extras.put(entry.getKey(), YsmJsonSupport.getJsonString(entry.getValue()));
                    }
                }
                model.properties.extraAnimationClassifies.add(classify);
            }
        }

        if (propsObj.has("extra_animation_buttons") && propsObj.get("extra_animation_buttons").isJsonArray()) {
            for (JsonElement elem : propsObj.getAsJsonArray("extra_animation_buttons")) {
                if (!elem.isJsonObject()) continue;
                JsonObject btnObj = elem.getAsJsonObject();
                RawYsmModel.ExtraAnimationButton btn = new RawYsmModel.ExtraAnimationButton();
                btn.id = YsmJsonSupport.getStr(btnObj, "id", "");
                btn.name = YsmJsonSupport.getStr(btnObj, "name", "");
                btn.description = YsmJsonSupport.getStr(btnObj, "description", "");

                if (btnObj.has("config_forms") && btnObj.get("config_forms").isJsonArray()) {
                    for (JsonElement formElem : btnObj.getAsJsonArray("config_forms")) {
                        if (!formElem.isJsonObject()) continue;
                        JsonObject formObj = formElem.getAsJsonObject();
                        RawYsmModel.ConfigForm form = new RawYsmModel.ConfigForm();
                        form.type = YsmJsonSupport.getStr(formObj, "type", "");
                        form.title = YsmJsonSupport.getStr(formObj, "title", "");
                        form.description = YsmJsonSupport.getStr(formObj, "description", "");
                        form.defaultValue = YsmJsonSupport.getStr(formObj, "value", "");
                        form.step = (float) YsmJsonSupport.getDouble(formObj, "step", 0);
                        form.min = (float) YsmJsonSupport.getDouble(formObj, "min", 0);
                        form.max = (float) YsmJsonSupport.getDouble(formObj, "max", 0);
                        if (formObj.has("labels") && formObj.get("labels").isJsonObject()) {
                            for (Map.Entry<String, JsonElement> lEntry : formObj.getAsJsonObject("labels").entrySet()) {
                                form.labels.put(lEntry.getKey(), YsmJsonSupport.getJsonString(lEntry.getValue()));
                            }
                        }
                        btn.forms.add(form);
                    }
                }
                model.properties.extraAnimationButtons.add(btn);
            }
        }

        loadGuiImage(model.properties.guiBackground, "gui_background");
        loadGuiImage(model.properties.guiForeground, "gui_foreground");
    }

    private void loadGuiImage(String path, String id) {
        if (path == null || path.isEmpty()) return;
        byte[] data = readResource(path);
        if (data == null) data = readResource("background/" + id + ".png");

        if (data != null) {
            ImageMeta meta = YsmTextureParsing.parseImageMeta(data, path);
            RawYsmModel.RawImage img = new RawYsmModel.RawImage();
            img.width = meta.width();
            img.height = meta.height();
            img.format = meta.format();
            img.name = id;
            img.data = data;
            img.unknownFlag = 1;
            model.properties.backgroundImages.add(img);
        }
    }

    private void parseMainEntity(JsonObject playerObj) {
        if (playerObj.has("model") && playerObj.get("model").isJsonObject()) {
            JsonObject modelObj = playerObj.getAsJsonObject("model");
            if (modelObj.has("main")) {
                byte[] geoData = readResource(YsmJsonSupport.getJsonString(modelObj.get("main")));
                if (geoData != null) model.mainEntity.mainModel = YsmGeometryParsing.parseGeometry(geoData, 1, null, this::parseLegacyMetadata);
            }
            if (modelObj.has("arm")) {
                byte[] geoData = readResource(YsmJsonSupport.getJsonString(modelObj.get("arm")));
                if (geoData != null) model.mainEntity.armModel = YsmGeometryParsing.parseGeometry(geoData, 2, null, this::parseLegacyMetadata);
            }
        }

        if (playerObj.has("texture")) {
            JsonElement texElem = playerObj.get("texture");
            Iterable<JsonElement> texArr = texElem.isJsonArray() ? texElem.getAsJsonArray() : Collections.singletonList(texElem);
            for (JsonElement elem : texArr) {
                String texPath = null;
                if (elem.isJsonPrimitive()) {
                    texPath = YsmJsonSupport.getJsonString(elem);
                } else if (elem.isJsonObject() && elem.getAsJsonObject().has("uv")) {
                    texPath = YsmJsonSupport.getJsonString(elem.getAsJsonObject().get("uv"));
                }
                if (texPath == null) continue;

                byte[] texData = readResource(texPath);
                if (texData != null) {
                    ImageMeta meta = YsmTextureParsing.parseImageMeta(texData, texPath);
                    RawYsmModel.RawTexture rt = new RawYsmModel.RawTexture();
                    rt.hash = sha256Hex(texData); // 计算原始数据的 hash
                    rt.width = meta.width();
                    rt.height = meta.height();
                    rt.imageFormat = meta.format();
                    rt.name = YsmJsonSupport.extractFileName(texPath);
                    rt.data = texData;
                    rt.unknownFlag = 1;

                    if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("specular")) {
                            byte[] spData = readResource(YsmJsonSupport.getJsonString(obj.get("specular")));
                            if (spData != null) {
                                ImageMeta spMeta = YsmTextureParsing.parseImageMeta(spData, "specular");
                                RawYsmModel.RawTexture.SubTexture sub = new RawYsmModel.RawTexture.SubTexture();
                                sub.specularType = 2;
                                sub.data = spData;
                                sub.unknownFlag = 1;
                                sub.hash = sha256Hex(spData);
                                sub.width = spMeta.width();
                                sub.height = spMeta.height();
                                sub.imageFormat = spMeta.format();
                                rt.subTextures.add(sub);
                            }
                        }
                        if (obj.has("normal")) {
                            byte[] nrData = readResource(YsmJsonSupport.getJsonString(obj.get("normal")));
                            if (nrData != null) {
                                ImageMeta nrMeta = YsmTextureParsing.parseImageMeta(nrData, "normal");
                                RawYsmModel.RawTexture.SubTexture sub = new RawYsmModel.RawTexture.SubTexture();
                                sub.specularType = 1;
                                sub.data = nrData;
                                sub.unknownFlag = 1;
                                sub.hash = sha256Hex(nrData);
                                sub.width = nrMeta.width();
                                sub.height = nrMeta.height();
                                sub.imageFormat = nrMeta.format();
                                rt.subTextures.add(sub);
                            }
                        }
                    }
                    model.mainEntity.textures.put(rt.name, rt);
                }
            }
        }
        normalizeDefaultTexture();

        if (playerObj.has("animation") && playerObj.get("animation").isJsonObject()) {
            JsonObject animObj = playerObj.getAsJsonObject("animation");
            for (Map.Entry<String, JsonElement> entry : animObj.entrySet()) {
                byte[] animData = readResource(YsmJsonSupport.getJsonString(entry.getValue()));
                if (animData != null) {
                    RawYsmModel.RawAnimationFile raf = parseAnimations(animData);
                    raf.fileHash = sha256Hex(animData);
                    raf.animType = YsmAnimationParsing.getAnimTypeFromKey(entry.getKey());
                    model.mainEntity.animationFiles.put(entry.getKey(), raf);
                }
            }
        }
        if (playerObj.has("animation_controllers") && playerObj.get("animation_controllers").isJsonArray()) {
            for (JsonElement acElem : playerObj.getAsJsonArray("animation_controllers")) {
                String acPath = YsmJsonSupport.getJsonString(acElem);
                byte[] acData = readResource(acPath);
                if (acData != null) {
                    String acHash = sha256Hex(acData);
                    RawYsmModel.RawAnimationControllerFile acFile = new RawYsmModel.RawAnimationControllerFile();
                    acFile.name = YsmJsonSupport.extractFileName(acPath);
                    acFile.hash = acHash;
                    parseAnimationControllers(acData, acFile.controllers);
                    model.mainEntity.animationControllerFiles.add(acFile);
                }
            }
        }
    }

    private void parseSubEntities(JsonElement sectionElem, Map<String, RawYsmModel.RawSubEntity> targetMap, String defaultIdentifier) {
        if (!sectionElem.isJsonArray() && !sectionElem.isJsonObject()) return;
        List<JsonObject> items = new ArrayList<>();

        if (sectionElem.isJsonArray()) {
            for (JsonElement e : sectionElem.getAsJsonArray()) {
                if (e.isJsonObject()) items.add(e.getAsJsonObject());
            }
        } else {
            JsonObject mapObj = sectionElem.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : mapObj.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    JsonObject item = entry.getValue().getAsJsonObject();
                    if (!item.has("match")) item.addProperty("__temp_identifier", entry.getKey());
                    items.add(item);
                }
            }
        }

        int index = 0;
        for (JsonObject item : items) {
            RawYsmModel.RawSubEntity sub = new RawYsmModel.RawSubEntity();
            sub.identifier = item.has("__temp_identifier") ? YsmJsonSupport.getJsonString(item.get("__temp_identifier")) : (defaultIdentifier + "_" + index);

            if (item.has("match")) {
                JsonElement match = item.get("match");
                if (match.isJsonArray()) {
                    JsonArray mArr = match.getAsJsonArray();
                    sub.matchIds = new String[mArr.size()];
                    for (int i = 0; i < mArr.size(); i++) sub.matchIds[i] = YsmJsonSupport.getJsonString(mArr.get(i));
                } else if (match.isJsonPrimitive()) {
                    sub.matchIds = new String[]{YsmJsonSupport.getJsonString(match)};
                }
            }

            if (item.has("model")) {
                byte[] geoData = readResource(YsmJsonSupport.getJsonString(item.get("model")));
                if (geoData != null) sub.model = YsmGeometryParsing.parseGeometry(geoData, 3, null, this::parseLegacyMetadata);
            }

            if (item.has("texture")) {
                String texPath = item.get("texture").isJsonObject() ? YsmJsonSupport.getJsonString(item.getAsJsonObject("texture").get("uv")) : YsmJsonSupport.getJsonString(item.get("texture"));
                byte[] texData = readResource(texPath);
                if (texData != null) {
                    ImageMeta meta = YsmTextureParsing.parseImageMeta(texData, texPath);
                    RawYsmModel.RawTexture rt = new RawYsmModel.RawTexture();

                    rt.hash = sha256Hex(texData);
                    rt.width = meta.width();
                    rt.height = meta.height();
                    rt.imageFormat = meta.format();

                    rt.name = "base_texture_" + index;
                    rt.data = texData;
                    rt.unknownFlag = 1;
                    sub.textures.put(rt.name, rt);
                }
            }

            if (item.has("animation")) {
                byte[] animData = readResource(YsmJsonSupport.getJsonString(item.get("animation")));
                if (animData != null) {
                    RawYsmModel.RawAnimationFile raf = parseAnimations(animData);
                    raf.fileHash = sha256Hex(animData);
                    raf.animType = YsmAnimationParsing.getAnimTypeFromKey("extra");
                    sub.animationFiles.put("sub_anim", raf);
                }
            }

            if (item.has("controller")) {
                String acPath = YsmJsonSupport.getJsonString(item.get("controller"));
                byte[] acData = readResource(acPath);
                if (acData != null) {
                    String acHash = sha256Hex(acData);
                    RawYsmModel.RawAnimationControllerFile acFile = new RawYsmModel.RawAnimationControllerFile();
                    acFile.name = YsmJsonSupport.extractFileName(acPath);
                    acFile.hash = acHash;
                    parseAnimationControllers(acData, acFile.controllers);
                    sub.animationControllerFiles.add(acFile);
                }
            }

            targetMap.put(sub.identifier, sub);
            index++;
        }
    }









    private RawYsmModel.RawAnimationFile parseAnimations(byte[] data) {
        return parseAnimationFile(data);
    }



    private void parseAnimationControllers(byte[] data, Map<String, RawYsmModel.RawAnimationController> targetMap) {
        String json = new String(data, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        if (!root.has("animation_controllers")) return;
        JsonObject acs = root.getAsJsonObject("animation_controllers");

        for (Map.Entry<String, JsonElement> acEntry : acs.entrySet()) {
            if (!acEntry.getValue().isJsonObject()) continue;
            JsonObject acObj = acEntry.getValue().getAsJsonObject();

            RawYsmModel.RawAnimationController ac = new RawYsmModel.RawAnimationController();
            ac.animationName = acEntry.getKey();
            ac.initialState = YsmJsonSupport.getStr(acObj, "initial_state", "default");

            if (acObj.has("states") && acObj.get("states").isJsonObject()) {
                JsonObject statesObj = acObj.getAsJsonObject("states");
                for (Map.Entry<String, JsonElement> sEntry : statesObj.entrySet()) {
                    if (!sEntry.getValue().isJsonObject()) continue;
                    JsonObject sObj = sEntry.getValue().getAsJsonObject();

                    RawYsmModel.RawControllerState state = new RawYsmModel.RawControllerState();
                    state.name = sEntry.getKey();

                    if (sObj.has("animations") && sObj.get("animations").isJsonArray()) {
                        for (JsonElement ae : sObj.getAsJsonArray("animations")) {
                            if (ae.isJsonPrimitive()) {
                                state.animations.put(YsmJsonSupport.getJsonString(ae), "");
                            } else if (ae.isJsonObject()) {
                                for (Map.Entry<String, JsonElement> objEntry : ae.getAsJsonObject().entrySet()) {
                                    state.animations.put(objEntry.getKey(), YsmJsonSupport.getJsonString(objEntry.getValue()));
                                }
                            }
                        }
                    }

                    if (sObj.has("transitions") && sObj.get("transitions").isJsonArray()) {
                        for (JsonElement te : sObj.getAsJsonArray("transitions")) {
                            if (te.isJsonObject()) {
                                for (Map.Entry<String, JsonElement> objEntry : te.getAsJsonObject().entrySet()) {
                                    state.transitions.put(objEntry.getKey(), YsmJsonSupport.getJsonString(objEntry.getValue()));
                                }
                            }
                        }
                    }

                    if (sObj.has("on_entry") && sObj.get("on_entry").isJsonArray()) {
                        for (JsonElement oe : sObj.getAsJsonArray("on_entry")) state.onEntry.add(YsmJsonSupport.getJsonString(oe));
                    }

                    if (sObj.has("on_exit") && sObj.get("on_exit").isJsonArray()) {
                        for (JsonElement oe : sObj.getAsJsonArray("on_exit")) state.onExit.add(YsmJsonSupport.getJsonString(oe));
                    }

                    if (sObj.has("sound_effects") && sObj.get("sound_effects").isJsonArray()) {
                        for (JsonElement se : sObj.getAsJsonArray("sound_effects")) {
                            if (se.isJsonObject()) state.soundEffects.add(YsmJsonSupport.getStr(se.getAsJsonObject(), "effect", ""));
                            else if (se.isJsonPrimitive()) state.soundEffects.add(YsmJsonSupport.getJsonString(se));
                        }
                    }

                    if (sObj.has("blend_transition")) {
                        JsonElement btElem = sObj.get("blend_transition");
                        if (btElem.isJsonPrimitive() && btElem.getAsJsonPrimitive().isNumber()) {
                            state.blendTransitionValue = btElem.getAsFloat();
                        } else if (btElem.isJsonObject()) {
                            for (Map.Entry<String, JsonElement> btEntry : btElem.getAsJsonObject().entrySet()) {
                                state.blendTransitions.put(Float.parseFloat(btEntry.getKey()), btEntry.getValue().getAsFloat());
                            }
                        }
                    }

                    ac.states.add(state);
                }
            }
            targetMap.put(ac.animationName, ac);
        }
    }

    private void parseGlobalResources() {
        // R4.2：统一经 container 枚举条目（folder 懒加载 walk 清单 / zip 剥离根后 / memory 原样）。
        // 原 folder/zipfs 模式用磁盘原 case 匹配小写前缀（sounds/ lang/ functions/），
        // memory 模式用小写 key——此处两案都尝试，向宽容方向统一且不回归。
        for (String name : container.names()) {
            String candidate = name;
            if (!isGlobalResource(candidate)) {
                String lower = candidate.toLowerCase(Locale.ROOT);
                if (isGlobalResource(lower)) {
                    candidate = lower;
                }
            }
            if (!isGlobalResource(candidate)) {
                continue;
            }
            byte[] data = readResource(candidate);
            if (data != null) {
                processGlobalResourceFile(candidate, data);
            }
        }
    }

    /**
     * 全局资源白名单：只有 sounds/lang/functions 目录（及任意位置的 .ogg）里的文件
     * 才会被读取并嵌入模型，模型文件夹里的其它无关文件（说明文档、图片、视频等）直接跳过，
     * 避免把大文件整体读入内存导致卡顿/OOM。与 processGlobalResourceFile 的判定条件保持一致。
     */
    private static boolean isGlobalResource(String relativePath) {
        if (relativePath == null) return false;
        return relativePath.startsWith("sounds/") || relativePath.endsWith(".ogg")
                || relativePath.startsWith("lang/") || relativePath.startsWith("functions/");
    }

    private void processGlobalResourceFile(String relativePath, byte[] data) {
        if (relativePath.startsWith("sounds/") || relativePath.endsWith(".ogg")) {
            String soundName = YsmJsonSupport.extractFileName(relativePath);
            String hash = sha256Hex(data);
            model.soundFiles.put(soundName, new RawYsmModel.RawDataFile(hash, data));
        }
        else if (relativePath.startsWith("lang/") && relativePath.endsWith(".json")) {
            String locale = relativePath.substring("lang/".length(), relativePath.length() - 5);
            try {
                String hash = sha256Hex(data);
                String langJsonStr = new String(data, StandardCharsets.UTF_8);
                JsonObject langJson = JsonParser.parseString(langJsonStr).getAsJsonObject();
                Map<String, String> langMap = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> langEntry : langJson.entrySet()) {
                    if (langEntry.getValue().isJsonPrimitive()) {
                        langMap.put(langEntry.getKey(), YsmJsonSupport.getJsonString(langEntry.getValue()));
                    }
                }
                model.languageFiles.put(locale, new RawYsmModel.RawLanguageFile(hash, langMap));
            } catch (Exception ignored) {}
        }
        else if (relativePath.startsWith("functions/") && relativePath.endsWith(".molang")) {
            String fnName = YsmJsonSupport.extractFileName(relativePath);
            String hash = sha256Hex(data);
            model.functionFiles.put(fnName, new RawYsmModel.RawDataFile(hash, data));
        }
    }

    private void normalizeDefaultTexture() {
        if (model.mainEntity.textures.isEmpty()) return;
        String defaultTexture = model.properties.defaultTexture;
        if (defaultTexture == null || defaultTexture.isBlank() || !model.mainEntity.textures.containsKey(defaultTexture)) {
            model.properties.defaultTexture = model.mainEntity.textures.keySet().iterator().next();
        }
    }
















    private String calculateFinalFolderHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            for (Map.Entry<String, String> entry : readFilesMd5Map.entrySet()) {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update(entry.getValue().getBytes(StandardCharsets.UTF_8));
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder(32);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public String getFolderHash() {
        return finalFolderHash;
    }

    private void parseLegacyFormat() {
        byte[] mainData = readResource("main.json");
        byte[] armData = readResource("arm.json");

        if (mainData == null) {
            throw new RuntimeException("Legacy model missing main.json");
        } else if (armData == null) {
            throw new RuntimeException("Legacy model missing arm.json");
        }

        List<String> pngFiles = new ArrayList<>();
        // R4.2：统一经 container 枚举顶层条目。原 memory 模式 key 已小写、folder 模式为磁盘原 case，
        // 此处统一按大小写不敏感匹配（.png 后缀与 arrow.png 比较），向宽容方向统一且不回归。
        for (String pathKey : container.names()) {
            String lower = pathKey.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".png") && !lower.contains("/")) {
                pngFiles.add(lower);
            }
        }

        boolean hasMainTexture = false;
        for (String texName : pngFiles) {
            if (!texName.equals("arrow.png")) {
                hasMainTexture = true;
                break;
            }
        }

        if (!hasMainTexture) {
            throw new RuntimeException("Legacy model requires at least one texture.");
        }

        byte[] arrowData = readResource("arrow.json");
        if (arrowData != null && !pngFiles.contains("arrow.png")) {
            throw new RuntimeException("arrow.json is present but arrow.png is missing.");
        }

        // 这个可能不存在
        byte[] infoData = readResource("info.json");
        if (infoData != null) {
            try {
                JsonObject infoObj = JsonParser.parseString(new String(infoData, StandardCharsets.UTF_8)).getAsJsonObject();
                parseLegacyMetadata(infoObj, true);
            } catch (Exception e) {
                System.err.println("Failed to parse info.json");
                e.printStackTrace();
            }
        }

        model.mainEntity.mainModel = YsmGeometryParsing.parseGeometry(mainData, 1, null, this::parseLegacyMetadata);
        model.mainEntity.armModel = YsmGeometryParsing.parseGeometry(armData, 2, null, this::parseLegacyMetadata);

        for (String texName : pngFiles) {
            if (texName.equals("arrow.png")) continue;
            byte[] texData = readResource(texName);
            if (texData != null) {
                ImageMeta meta = YsmTextureParsing.parseImageMeta(texData, texName);
                RawYsmModel.RawTexture rt = new RawYsmModel.RawTexture();
                rt.hash = sha256Hex(texData);
                rt.width = meta.width();
                rt.height = meta.height();
                rt.imageFormat = meta.format();
                rt.name = YsmJsonSupport.extractFileName(texName);
                rt.data = texData;
                rt.unknownFlag = 1;
                model.mainEntity.textures.put(rt.name, rt);
            }
        }

        if (!model.mainEntity.textures.isEmpty()) {
            model.properties.defaultTexture = model.mainEntity.textures.keySet().iterator().next();
        }

        String[] animFiles = {"main.animation.json", "arm.animation.json", "extra.animation.json", "tac.animation.json", "carryon.animation.json", "slashblade.animation.json", "tlm.animation.json"};
        for (String fileName : animFiles) {
            byte[] animData = readResource(fileName);
            if (animData != null) {
                RawYsmModel.RawAnimationFile raf = parseAnimations(animData);
                raf.fileHash = sha256Hex(animData);

                String animKey = fileName.substring(0, fileName.length() - ".animation.json".length());
                raf.animType = YsmAnimationParsing.getAnimTypeFromKey(animKey);
                model.mainEntity.animationFiles.put(animKey, raf);
                if("extra".equals(animKey)) {
                    raf.animations.keySet().forEach(animName -> model.properties.extraAnimations.put(animName, animName));
                }
            }
        }

        // 箭矢
        if (arrowData != null) {
            RawYsmModel.RawSubEntity arrowSub = new RawYsmModel.RawSubEntity();
            arrowSub.identifier = "arrow";
            arrowSub.model = YsmGeometryParsing.parseGeometry(arrowData, 3, null, this::parseLegacyMetadata);

            byte[] arrowTexData = readResource("arrow.png");
            if (arrowTexData != null) {
                ImageMeta meta = YsmTextureParsing.parseImageMeta(arrowTexData, "arrow.png");
                RawYsmModel.RawTexture rt = new RawYsmModel.RawTexture();
                rt.hash = sha256Hex(arrowTexData);
                rt.width = meta.width();
                rt.height = meta.height();
                rt.imageFormat = meta.format();
                rt.name = "arrow";
                rt.data = arrowTexData;
                rt.unknownFlag = 1;
                arrowSub.textures.put(rt.name, rt);
            }

            byte[] arrowAnimData = readResource("arrow.animation.json");
            if (arrowAnimData != null) {
                RawYsmModel.RawAnimationFile raf = parseAnimations(arrowAnimData);
                raf.fileHash = sha256Hex(arrowAnimData);
                raf.animType = YsmAnimationParsing.getAnimTypeFromKey("arrow");
                arrowSub.animationFiles.put("sub_anim", raf);
            }

            model.projectiles.put("arrow", arrowSub);
        }
    }

    public static boolean isModelFolder(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        if (Files.isRegularFile(dir.resolve("ysm.json"))) {
            return true;
        }
        return Files.isRegularFile(dir.resolve("main.json")) && Files.isRegularFile(dir.resolve("arm.json"));
    }

    private void parseLegacyMetadata(JsonObject infoObj, boolean overwrite) {
        if (infoObj == null) return;
        if (infoObj.has("name") && (overwrite || model.metadata.name.isEmpty())) {
            model.metadata.name = YsmJsonSupport.getStr(infoObj, "name", "");
        }
        if (infoObj.has("tips") && (overwrite || model.metadata.tips.isEmpty())) {
            model.metadata.tips = YsmJsonSupport.getStr(infoObj, "tips", "");
        }
        if (infoObj.has("license") && (overwrite || model.metadata.licenseDescription.isEmpty())) {
            model.metadata.licenseDescription = YsmJsonSupport.getStr(infoObj, "license", "");
        }
        if (infoObj.has("free")) {
            if (overwrite || !model.properties.isFree) {
                model.properties.isFree = YsmJsonSupport.getBool(infoObj, "free", false);
            }
        }

        if (infoObj.has("authors") && infoObj.get("authors").isJsonArray()) {
            if (overwrite || model.metadata.authors.isEmpty()) {
                model.metadata.authors.clear();
                for (JsonElement e : infoObj.getAsJsonArray("authors")) {
                    RawYsmModel.RawMetadata.Author author = new RawYsmModel.RawMetadata.Author();
                    author.name = YsmJsonSupport.getJsonString(e);
                    model.metadata.authors.add(author);
                }
            }
        }

        if (infoObj.has("extra_animation_names") && infoObj.get("extra_animation_names").isJsonArray()) {
            if (overwrite || model.properties.extraAnimations.isEmpty()) {
                model.properties.extraAnimations.clear();
                JsonArray extras = infoObj.getAsJsonArray("extra_animation_names");
                for (int i = 0; i < extras.size(); i++) {
                    String extraName = YsmJsonSupport.getJsonString(extras.get(i));
                    model.properties.extraAnimations.put("extra" + i, extraName);
                }
            }
        }
    }

    // ---- 1.2.7 §24.2：以下静态入口已迁至专用解析类，此处保留为兼容 facade（删除留到 1.2.8）。----

    /** @deprecated 1.2.7 §24.2：见 {@link YsmTextureParsing#detectFormat(byte[])}。 */
    public static int detectFormat(byte[] data) {
        return YsmTextureParsing.detectFormat(data);
    }

    /** @deprecated 1.2.7 §24.2：见 {@link YsmTextureParsing#parseBedrockTexture(byte[], String)}。 */
    public static RawYsmModel.RawTexture parseBedrockTexture(byte[] imageBytes, String name) {
        return YsmTextureParsing.parseBedrockTexture(imageBytes, name);
    }

    /** @deprecated 1.2.7 §24.2：见 {@link YsmGeometryParsing#parseBedrockGeometry(byte[], String)}。 */
    public static RawYsmModel.RawGeometry parseBedrockGeometry(byte[] data, String identifier) {
        return YsmGeometryParsing.parseBedrockGeometry(data, identifier);
    }

    /** @deprecated 1.2.7 §24.2：见 {@link YsmAnimationParsing#parseAnimationFile(byte[])}。 */
    public static RawYsmModel.RawAnimationFile parseAnimationFile(byte[] data) {
        return YsmAnimationParsing.parseAnimationFile(data);
    }

    /** @deprecated 1.2.7 §24.2：见 {@link YsmAnimationParsing#getAnimTypeFromKey(String)}。 */
    public static int getAnimTypeFromKey(String key) {
        return YsmAnimationParsing.getAnimTypeFromKey(key);
    }

    /** @deprecated 1.2.7 §24.2：见 {@link YsmAnimationParsing#getAnimKeyFromType(int)}。 */
    public static String getAnimKeyFromType(int type) {
        return YsmAnimationParsing.getAnimKeyFromType(type);
    }
}
