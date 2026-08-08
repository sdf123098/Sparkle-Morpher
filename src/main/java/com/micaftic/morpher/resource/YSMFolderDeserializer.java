package com.micaftic.morpher.resource;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import rip.ysm.imagestream.avif.AvifDecoder;
import rip.ysm.imagestream.webp.WebpDecoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

import static com.micaftic.morpher.util.DigestUtil.md5Hex;
import static com.micaftic.morpher.util.DigestUtil.sha256Hex;

public class YSMFolderDeserializer implements AutoCloseable {
    private final Map<String, String> readFilesMd5Map = new TreeMap<>();
    private String finalFolderHash;
    private final Path rootPath;
    private final FileSystem zipFileSystem;
    private final RawYsmModel model;

    private final Map<String, byte[]> inMemoryFiles;

    /** 单个模型资源文件的大小上限（字节）。超限视为垃圾/损坏资源，跳过读取以避免内存暴涨或卡顿。 */
    private static final long MAX_RESOURCE_BYTES = 256L * 1024L * 1024L;

    public YSMFolderDeserializer(Path sourcePath) throws IOException {
        if (!Files.exists(sourcePath)) {
            throw new FileNotFoundException("Model source not found: " + sourcePath);
        }

        if (Files.isDirectory(sourcePath)) {
            this.inMemoryFiles = null;
            this.rootPath = sourcePath;
            this.zipFileSystem = null;
        } else if (sourcePath.toString().endsWith(".zip") || sourcePath.toString().endsWith(".ysm")) {
            URI uri = URI.create("jar:" + sourcePath.toUri());
            java.nio.file.FileSystem openedFs = null;
            Path openedRoot = null;
            try {
                openedFs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                openedRoot = resolveArchiveModelRoot(openedFs.getPath("/"));
            } catch (java.util.zip.ZipException zipException) {
                // 中文 Windows 压缩工具生成的 zip 常用 GBK 编码条目名，jdk.zipfs 按 UTF-8
                // 解码会抛 "invalid CEN header (bad entry name)"。回退用 ZipFile + GBK 读取。
                System.err.println("[SM] Warning: Zip entry names are not UTF-8, retrying with GBK: " + sourcePath);
            }
            if (openedFs != null) {
                this.zipFileSystem = openedFs;
                this.rootPath = openedRoot;
                this.inMemoryFiles = null;
            } else {
                this.zipFileSystem = null;
                this.rootPath = null;
                this.inMemoryFiles = readZipEntries(sourcePath, java.nio.charset.Charset.forName("GBK"));
            }
        } else {
            throw new IllegalArgumentException("Unsupported file type. Expected directory or .zip");
        }

        this.model = new RawYsmModel();
        this.model.formatVersion = 65535;
    }

    /** 仅用于静态探测（parseBedrockGeometry 等不需要读取任何 zip/目录资源）。 */
    private YSMFolderDeserializer() {
        this.rootPath = null;
        this.zipFileSystem = null;
        this.inMemoryFiles = null;
        this.model = new RawYsmModel();
        this.model.formatVersion = 65535;
    }

    private static Path resolveArchiveModelRoot(Path archiveRoot) throws IOException {
        if (isModelFolder(archiveRoot)) {
            return archiveRoot;
        }

        Path detectedRoot = null;
        try (Stream<Path> stream = Files.list(archiveRoot)) {
            Iterator<Path> iterator = stream.iterator();
            while (iterator.hasNext()) {
                Path child = iterator.next();
                if (!Files.isDirectory(child) || !isModelFolder(child)) {
                    continue;
                }
                if (detectedRoot != null) {
                    return archiveRoot;
                }
                detectedRoot = child;
            }
        }
        return detectedRoot != null ? detectedRoot : archiveRoot;
    }

    public YSMFolderDeserializer(Map<String, byte[]> memoryFiles) {
        this.inMemoryFiles = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : memoryFiles.entrySet()) {
            String key = normalizeResourceKey(entry.getKey());
            byte[] previous = this.inMemoryFiles.putIfAbsent(key, entry.getValue());
            if (previous != null && previous != entry.getValue()) {
                throw new IllegalArgumentException("Duplicate resource path after case normalization: " + entry.getKey());
            }
        }
        this.rootPath = null;
        this.zipFileSystem = null;
        this.model = new RawYsmModel();
        this.model.formatVersion = 65535;
    }

    private byte[] readResource(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;
        relativePath = cleanJsonString(relativePath);
        if (relativePath.isEmpty()) return null;
        try {
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            String normalizedPath = normalizeResourceKey(relativePath);
            byte[] data = null;

            if (inMemoryFiles == null) {
                Path target = resolveResourcePath(normalizedPath);
                if (Files.exists(target) && Files.isRegularFile(target)) {
                    long size = Files.size(target);
                    if (size > MAX_RESOURCE_BYTES) {
                        System.err.println("[SM] Warning: Skipping oversized model resource (" + size + " bytes): " + relativePath);
                        return null;
                    }
                    data = Files.readAllBytes(target);
                }
            } else {
                data = inMemoryFiles.get(normalizedPath);
            }

            if (data != null && !readFilesMd5Map.containsKey(normalizedPath)) {
                readFilesMd5Map.put(normalizedPath, md5Hex(data));
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

    private Path resolveResourcePath(String normalizedPath) throws IOException {
        Path direct = rootPath.resolve(normalizedPath).normalize();
        if (!direct.startsWith(rootPath.normalize())) {
            return direct;
        }
        if (Files.exists(direct)) {
            return direct;
        }
        Path current = rootPath;
        for (String segment : normalizedPath.split("/")) {
            if (!Files.isDirectory(current)) {
                return direct;
            }
            Path matched = null;
            try (Stream<Path> children = Files.list(current)) {
                matched = children.filter(child -> child.getFileName().toString().equalsIgnoreCase(segment)).findFirst().orElse(null);
            }
            if (matched == null) {
                return direct;
            }
            current = matched;
        }
        return current;
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
    public void close() throws IOException {
        if (this.zipFileSystem != null) {
            this.zipFileSystem.close();
        }
        if (inMemoryFiles != null) inMemoryFiles.clear();
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
        model.metadata.name = getStr(metaObj, "name", "");
        model.metadata.tips = getStr(metaObj, "tips", "");
        if (metaObj.has("license") && metaObj.get("license").isJsonObject()) {
            JsonObject licObj = metaObj.getAsJsonObject("license");
            model.metadata.licenseType = getStr(licObj, "type", "");
            model.metadata.licenseDescription = getStr(licObj, "desc", "");
        }

        if (metaObj.has("authors") && metaObj.get("authors").isJsonArray()) {
            for (JsonElement elem : metaObj.getAsJsonArray("authors")) {
                if (!elem.isJsonObject()) continue;
                JsonObject authorObj = elem.getAsJsonObject();
                RawYsmModel.RawMetadata.Author author = new RawYsmModel.RawMetadata.Author();
                author.name = getStr(authorObj, "name", "");
                author.role = getStr(authorObj, "role", "");
                author.comment = getStr(authorObj, "comment", "");

                if (authorObj.has("contact") && authorObj.get("contact").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> cEntry : authorObj.getAsJsonObject("contact").entrySet()) {
                        author.contacts.put(cEntry.getKey(), getJsonString(cEntry.getValue()));
                    }
                }

                if (authorObj.has("avatar")) {
                    String avatarPath = getStr(authorObj, "avatar", "");
                    if (!avatarPath.isEmpty()) {
                        byte[] avatarData = readResource(avatarPath);
                        if (avatarData != null) {
                            ImageMeta meta = parseImageMeta(avatarData, avatarPath);
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
                model.metadata.links.put(linkEntry.getKey(), getJsonString(linkEntry.getValue()));
            }
        }
    }

    private void parseProperties(JsonObject propsObj) {
        model.properties.widthScale = (float) getDouble(propsObj, "width_scale", 0.7);
        model.properties.heightScale = (float) getDouble(propsObj, "height_scale", 0.7);
        model.properties.defaultTexture = getStr(propsObj, "default_texture", "default");
        model.properties.previewAnimation = getStr(propsObj, "preview_animation", "");
        model.properties.isFree = getBool(propsObj, "free", false);
        model.properties.renderLayersFirst = getBool(propsObj, "render_layers_first", false);
        model.properties.allCutout = getBool(propsObj, "all_cutout", false);
        model.properties.disablePreviewRotation = getBool(propsObj, "disable_preview_rotation", false);
        model.properties.guiNoLighting = getBool(propsObj, "gui_no_lighting", false);
        model.properties.mergeMultilineExpr = getBool(propsObj, "merge_multiline_expr", false);
        model.properties.guiForeground = getStr(propsObj, "gui_foreground", "");
        model.properties.guiBackground = getStr(propsObj, "gui_background", "");
        if (propsObj.has("extra_animation") && propsObj.get("extra_animation").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : propsObj.getAsJsonObject("extra_animation").entrySet()) {
                model.properties.extraAnimations.put(entry.getKey(), getJsonString(entry.getValue()));
            }
        }

        if (propsObj.has("extra_animation_classify") && propsObj.get("extra_animation_classify").isJsonArray()) {
            for (JsonElement elem : propsObj.getAsJsonArray("extra_animation_classify")) {
                if (!elem.isJsonObject()) continue;
                JsonObject clsObj = elem.getAsJsonObject();
                RawYsmModel.ExtraAnimationClassify classify = new RawYsmModel.ExtraAnimationClassify();
                classify.id = getStr(clsObj, "id", "");
                if (clsObj.has("extra_animation") && clsObj.get("extra_animation").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : clsObj.getAsJsonObject("extra_animation").entrySet()) {
                        classify.extras.put(entry.getKey(), getJsonString(entry.getValue()));
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
                btn.id = getStr(btnObj, "id", "");
                btn.name = getStr(btnObj, "name", "");
                btn.description = getStr(btnObj, "description", "");

                if (btnObj.has("config_forms") && btnObj.get("config_forms").isJsonArray()) {
                    for (JsonElement formElem : btnObj.getAsJsonArray("config_forms")) {
                        if (!formElem.isJsonObject()) continue;
                        JsonObject formObj = formElem.getAsJsonObject();
                        RawYsmModel.ConfigForm form = new RawYsmModel.ConfigForm();
                        form.type = getStr(formObj, "type", "");
                        form.title = getStr(formObj, "title", "");
                        form.description = getStr(formObj, "description", "");
                        form.defaultValue = getStr(formObj, "value", "");
                        form.step = (float) getDouble(formObj, "step", 0);
                        form.min = (float) getDouble(formObj, "min", 0);
                        form.max = (float) getDouble(formObj, "max", 0);
                        if (formObj.has("labels") && formObj.get("labels").isJsonObject()) {
                            for (Map.Entry<String, JsonElement> lEntry : formObj.getAsJsonObject("labels").entrySet()) {
                                form.labels.put(lEntry.getKey(), getJsonString(lEntry.getValue()));
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
            ImageMeta meta = parseImageMeta(data, path);
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
                byte[] geoData = readResource(getJsonString(modelObj.get("main")));
                if (geoData != null) model.mainEntity.mainModel = parseGeometry(geoData, 1);
            }
            if (modelObj.has("arm")) {
                byte[] geoData = readResource(getJsonString(modelObj.get("arm")));
                if (geoData != null) model.mainEntity.armModel = parseGeometry(geoData, 2);
            }
        }

        if (playerObj.has("texture")) {
            JsonElement texElem = playerObj.get("texture");
            Iterable<JsonElement> texArr = texElem.isJsonArray() ? texElem.getAsJsonArray() : Collections.singletonList(texElem);
            for (JsonElement elem : texArr) {
                String texPath = null;
                if (elem.isJsonPrimitive()) {
                    texPath = getJsonString(elem);
                } else if (elem.isJsonObject() && elem.getAsJsonObject().has("uv")) {
                    texPath = getJsonString(elem.getAsJsonObject().get("uv"));
                }
                if (texPath == null) continue;

                byte[] texData = readResource(texPath);
                if (texData != null) {
                    ImageMeta meta = parseImageMeta(texData, texPath);
                    RawYsmModel.RawTexture rt = new RawYsmModel.RawTexture();
                    rt.hash = sha256Hex(texData); // 计算原始数据的 hash
                    rt.width = meta.width();
                    rt.height = meta.height();
                    rt.imageFormat = meta.format();
                    rt.name = extractFileName(texPath);
                    rt.data = texData;
                    rt.unknownFlag = 1;

                    if (elem.isJsonObject()) {
                        JsonObject obj = elem.getAsJsonObject();
                        if (obj.has("specular")) {
                            byte[] spData = readResource(getJsonString(obj.get("specular")));
                            if (spData != null) {
                                ImageMeta spMeta = parseImageMeta(spData, "specular");
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
                            byte[] nrData = readResource(getJsonString(obj.get("normal")));
                            if (nrData != null) {
                                ImageMeta nrMeta = parseImageMeta(nrData, "normal");
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
                byte[] animData = readResource(getJsonString(entry.getValue()));
                if (animData != null) {
                    RawYsmModel.RawAnimationFile raf = parseAnimations(animData);
                    raf.fileHash = sha256Hex(animData);
                    raf.animType = getAnimTypeFromKey(entry.getKey());
                    model.mainEntity.animationFiles.put(entry.getKey(), raf);
                }
            }
        }
        if (playerObj.has("animation_controllers") && playerObj.get("animation_controllers").isJsonArray()) {
            for (JsonElement acElem : playerObj.getAsJsonArray("animation_controllers")) {
                String acPath = getJsonString(acElem);
                byte[] acData = readResource(acPath);
                if (acData != null) {
                    String acHash = sha256Hex(acData);
                    RawYsmModel.RawAnimationControllerFile acFile = new RawYsmModel.RawAnimationControllerFile();
                    acFile.name = extractFileName(acPath);
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
            sub.identifier = item.has("__temp_identifier") ? getJsonString(item.get("__temp_identifier")) : (defaultIdentifier + "_" + index);

            if (item.has("match")) {
                JsonElement match = item.get("match");
                if (match.isJsonArray()) {
                    JsonArray mArr = match.getAsJsonArray();
                    sub.matchIds = new String[mArr.size()];
                    for (int i = 0; i < mArr.size(); i++) sub.matchIds[i] = getJsonString(mArr.get(i));
                } else if (match.isJsonPrimitive()) {
                    sub.matchIds = new String[]{getJsonString(match)};
                }
            }

            if (item.has("model")) {
                byte[] geoData = readResource(getJsonString(item.get("model")));
                if (geoData != null) sub.model = parseGeometry(geoData, 3);
            }

            if (item.has("texture")) {
                String texPath = item.get("texture").isJsonObject() ? getJsonString(item.getAsJsonObject("texture").get("uv")) : getJsonString(item.get("texture"));
                byte[] texData = readResource(texPath);
                if (texData != null) {
                    ImageMeta meta = parseImageMeta(texData, texPath);
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
                byte[] animData = readResource(getJsonString(item.get("animation")));
                if (animData != null) {
                    RawYsmModel.RawAnimationFile raf = parseAnimations(animData);
                    raf.fileHash = sha256Hex(animData);
                    raf.animType = getAnimTypeFromKey("extra");
                    sub.animationFiles.put("sub_anim", raf);
                }
            }

            if (item.has("controller")) {
                String acPath = getJsonString(item.get("controller"));
                byte[] acData = readResource(acPath);
                if (acData != null) {
                    String acHash = sha256Hex(acData);
                    RawYsmModel.RawAnimationControllerFile acFile = new RawYsmModel.RawAnimationControllerFile();
                    acFile.name = extractFileName(acPath);
                    acFile.hash = acHash;
                    parseAnimationControllers(acData, acFile.controllers);
                    sub.animationControllerFiles.add(acFile);
                }
            }

            targetMap.put(sub.identifier, sub);
            index++;
        }
    }

    private RawYsmModel.RawGeometry parseGeometry(byte[] data, int modelType) {
        return parseGeometry(data, modelType, null);
    }

    /**
     * 解析 Bedrock {@code minecraft:geometry} JSON。
     *
     * @param identifier 非空时按 {@code description.identifier} 选择几何（支持
     *                   {@code geometry.xxx} 与 {@code xxx} 两种写法，大小写不敏感）；
     *                   为空或找不到时回退到第一个（与旧行为一致，YSM 包不受影响）。
     */
    private RawYsmModel.RawGeometry parseGeometry(byte[] data, int modelType, String identifier) {
        String json = new String(data, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray geometries = root.has("minecraft:geometry") ? root.getAsJsonArray("minecraft:geometry") : null;
        if (geometries == null || geometries.isEmpty()) return new RawYsmModel.RawGeometry();

        JsonObject geoObj = selectGeometry(geometries, identifier);
        RawYsmModel.RawGeometry geo = new RawYsmModel.RawGeometry();
        geo.sha256 = sha256Hex(data);

        geo.modelType = modelType;
        geo.unkFloat1 = 0.7f;
        geo.unkFloat2 = 0.7f;

        if (geoObj.has("description")) {
            JsonObject desc = geoObj.getAsJsonObject("description");
            geo.identifier = getStr(desc, "identifier", "");
            geo.textureWidth = (float) getDouble(desc, "texture_width", 64.0);
            geo.textureHeight = (float) getDouble(desc, "texture_height", 64.0);
            geo.visibleBoundsWidth = (float) getDouble(desc, "visible_bounds_width", 0);
            geo.visibleBoundsHeight = (float) getDouble(desc, "visible_bounds_height", 0);
            if (desc.has("visible_bounds_offset") && desc.get("visible_bounds_offset").isJsonArray()) {
                JsonArray offsetArr = desc.getAsJsonArray("visible_bounds_offset");
                geo.visibleBoundsOffset = new float[offsetArr.size()];
                for (int i = 0; i < offsetArr.size(); i++) geo.visibleBoundsOffset[i] = offsetArr.get(i).getAsFloat();
            } else {
                geo.visibleBoundsOffset = new float[0];
            }

            if (modelType == 1 && desc.has("ysm_extra_info")) { // legacy
                parseLegacyMetadata(desc.getAsJsonObject("ysm_extra_info"), false);
            }
        }

        if (geoObj.has("bones") && geoObj.get("bones").isJsonArray()) {
            for (JsonElement boneElem : geoObj.getAsJsonArray("bones")) {
                if (!boneElem.isJsonObject()) continue;
                JsonObject bObj = boneElem.getAsJsonObject();
                RawYsmModel.RawBone bone = new RawYsmModel.RawBone();
                bone.name = getStr(bObj, "name", "");
                bone.parentName = getStr(bObj, "parent", "");

                if (bObj.has("pivot")) {
                    JsonArray pivot = bObj.getAsJsonArray("pivot");
                    bone.pivot = new float[]{-pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(), pivot.get(2).getAsFloat()};
                }
                if (bObj.has("rotation")) {
                    JsonArray rot = bObj.getAsJsonArray("rotation");
                    bone.rotation = new float[]{(float) -Math.toRadians(rot.get(0).getAsFloat()), (float) -Math.toRadians(rot.get(1).getAsFloat()), (float) Math.toRadians(rot.get(2).getAsFloat())};
                }

                float boneInflate = (float) getDouble(bObj, "inflate", 0.0);
                boolean boneMirror = getBool(bObj, "mirror", false);

                if (bObj.has("cubes") && bObj.get("cubes").isJsonArray()) {
                    for (JsonElement cElem : bObj.getAsJsonArray("cubes")) {
                        if (!cElem.isJsonObject()) continue;
                        JsonObject cObj = cElem.getAsJsonObject();
                        RawYsmModel.RawCube cube = new RawYsmModel.RawCube();

                        float inflate = cObj.has("inflate") ? cObj.get("inflate").getAsFloat() : boneInflate;
                        boolean mirror = cObj.has("mirror") ? cObj.get("mirror").getAsBoolean() : boneMirror;

                        float[] origin = getFloatArray(cObj, "origin", 3);
                        float[] size = getFloatArray(cObj, "size", 3);

                        float cx = -origin[0] - size[0] - inflate;
                        float cy = origin[1] - inflate;
                        float cz = origin[2] - inflate;
                        float cw = size[0] + inflate * 2;
                        float ch = size[1] + inflate * 2;
                        float cd = size[2] + inflate * 2;

                        Matrix4f cubeBakeMat = new Matrix4f();
                        if (cObj.has("rotation") || cObj.has("pivot")) {
                            float[] cpvt = getFloatArray(cObj, "pivot", 3);
                            float[] crot = getFloatArray(cObj, "rotation", 3);
                            cubeBakeMat.translate(-cpvt[0] / 16f, cpvt[1] / 16f, cpvt[2] / 16f);
                            cubeBakeMat.rotateZ((float) Math.toRadians(crot[2]));
                            cubeBakeMat.rotateY((float) -Math.toRadians(crot[1]));
                            cubeBakeMat.rotateX((float) -Math.toRadians(crot[0]));
                            cubeBakeMat.translate(cpvt[0] / 16f, -cpvt[1] / 16f, -cpvt[2] / 16f);
                        }
                        Matrix3f cubeNormalMat = new Matrix3f();
                        cubeBakeMat.normal(cubeNormalMat);

                        if (cObj.has("uv")) {
                            JsonElement uvElem = cObj.get("uv");
                            if (uvElem.isJsonObject()) {
                                JsonObject uvObj = uvElem.getAsJsonObject();
                                bakeFaceToRaw(cube, uvObj, "north", "north", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 0, -1), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, uvObj, "south", "south", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 0, 1), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, uvObj, "east", mirror ? "west" : "east", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(1, 0, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, uvObj, "west", mirror ? "east" : "west", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(-1, 0, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, uvObj, "up", "up", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 1, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, uvObj, "down", "down", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, -1, 0), cubeBakeMat, cubeNormalMat);
                            } else if (uvElem.isJsonArray()) {
                                JsonArray uvArr = uvElem.getAsJsonArray();
                                float uvX = uvArr.get(0).getAsFloat();
                                float uvY = uvArr.get(1).getAsFloat();
                                float dx = (float) Math.floor(size[0]);
                                float dy = (float) Math.floor(size[1]);
                                float dz = (float) Math.floor(size[2]);

                                JsonObject fakeUvObj = new JsonObject();
                                fakeUvObj.add("north", createFaceUVNode(uvX + dz, uvY + dz, dx, dy));
                                fakeUvObj.add("south", createFaceUVNode(uvX + dz + dx + dz, uvY + dz, dx, dy));
                                fakeUvObj.add("east", createFaceUVNode(uvX, uvY + dz, dz, dy));
                                fakeUvObj.add("west", createFaceUVNode(uvX + dz + dx, uvY + dz, dz, dy));
                                fakeUvObj.add("up", createFaceUVNode(uvX + dz, uvY, dx, dz));
                                fakeUvObj.add("down", createFaceUVNode(uvX + dz + dx, uvY + dz, dx, -dz));

                                bakeFaceToRaw(cube, fakeUvObj, "north", "north", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 0, -1), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, fakeUvObj, "south", "south", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 0, 1), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, fakeUvObj, "east", mirror ? "west" : "east", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(1, 0, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, fakeUvObj, "west", mirror ? "east" : "west", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(-1, 0, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, fakeUvObj, "up", "up", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, 1, 0), cubeBakeMat, cubeNormalMat);
                                bakeFaceToRaw(cube, fakeUvObj, "down", "down", mirror, cx, cy, cz, cw, ch, cd, geo.textureWidth, geo.textureHeight, new Vector3f(0, -1, 0), cubeBakeMat, cubeNormalMat);
                            }
                        }
                        bone.cubes.add(cube);
                    }
                }
                geo.bones.add(bone);
            }
        }
        return geo;
    }

    /** 按 identifier 选择几何；identifier 为空或未命中时回退到第一个。 */
    private static JsonObject selectGeometry(JsonArray geometries, String identifier) {
        if (identifier != null && !identifier.isEmpty()) {
            String want = identifier.trim();
            String wantWithPrefix = want.startsWith("geometry.") ? want : "geometry." + want;
            for (JsonElement element : geometries) {
                if (!element.isJsonObject()) continue;
                JsonObject candidate = element.getAsJsonObject();
                if (!candidate.has("description") || !candidate.get("description").isJsonObject()) continue;
                String id = getJsonString(candidate.getAsJsonObject("description").get("identifier"));
                if (id == null || id.isEmpty()) continue;
                if (id.equalsIgnoreCase(want) || id.equalsIgnoreCase(wantWithPrefix)
                        || id.endsWith("." + want)) {
                    return candidate;
                }
            }
        }
        return geometries.get(0).getAsJsonObject();
    }

    /**
     * 静态入口：解析裸 Bedrock geo JSON（供 Bedrock 直读导入用，不依赖任何 zip/目录资源）。
     *
     * @param identifier 按 {@code description.identifier} 选择几何，可为 null（取第一个）。
     */
    public static RawYsmModel.RawGeometry parseBedrockGeometry(byte[] data, String identifier) {
        if (data == null || data.length == 0) {
            return new RawYsmModel.RawGeometry();
        }
        try (YSMFolderDeserializer probe = new YSMFolderDeserializer()) {
            return probe.parseGeometry(data, 1, identifier);
        } catch (Exception e) {
            System.err.println("[SM] Failed to parse Bedrock geometry: " + e);
            return new RawYsmModel.RawGeometry();
        }
    }

    /** 静态入口：把 PNG 等图像字节包装成 RawTexture（供 Bedrock 直读导入用）。 */
    public static RawYsmModel.RawTexture parseBedrockTexture(byte[] imageBytes, String name) {
        RawYsmModel.RawTexture texture = new RawYsmModel.RawTexture();
        if (imageBytes == null || imageBytes.length == 0) {
            return texture;
        }
        try (YSMFolderDeserializer probe = new YSMFolderDeserializer()) {
            ImageMeta meta = probe.parseImageMeta(imageBytes, name);
            texture.hash = sha256Hex(imageBytes);
            texture.width = meta.width();
            texture.height = meta.height();
            texture.imageFormat = meta.format();
            texture.name = name;
            texture.data = imageBytes;
            texture.unknownFlag = 1;
        } catch (Exception e) {
            System.err.println("[SM] Failed to parse Bedrock texture " + name + ": " + e);
        }
        return texture;
    }

    private void bakeFaceToRaw(RawYsmModel.RawCube cube, JsonObject uvObj, String faceType, String uvFaceName, boolean mirror, float x, float y, float z, float w, float h, float d, float tw, float th, Vector3f rawNormal, Matrix4f cubeBakeMat, Matrix3f cubeNormalMat) {
        if (!uvObj.has(uvFaceName)) return;
        JsonObject faceData = uvObj.getAsJsonObject(uvFaceName);
        float[] uv = getFloatArray(faceData, "uv", 2);
        float[] uvSize = getFloatArray(faceData, "uv_size", 2);

        float u0 = uv[0] / tw;
        float v0 = uv[1] / th;
        float u1 = (uv[0] + uvSize[0]) / tw;
        float v1 = (uv[1] + uvSize[1]) / th;

        if (!mirror) {
            float temp = u0; u0 = u1; u1 = temp;
        }

        RawYsmModel.RawFace face = new RawYsmModel.RawFace();
        Vector3f bakedNormal = new Vector3f(rawNormal).mul(cubeNormalMat).normalize();
        face.normal = new float[]{bakedNormal.x, bakedNormal.y, bakedNormal.z};

        float x1 = x / 16f, x2 = (x + w) / 16f;
        float y1 = y / 16f, y2 = (y + h) / 16f;
        float z1 = z / 16f, z2 = (z + d) / 16f;

        Vector3f p1 = new Vector3f(x1, y1, z1);
        Vector3f p2 = new Vector3f(x1, y1, z2);
        Vector3f p3 = new Vector3f(x1, y2, z1);
        Vector3f p4 = new Vector3f(x1, y2, z2);
        Vector3f p5 = new Vector3f(x2, y1, z1);
        Vector3f p6 = new Vector3f(x2, y1, z2);
        Vector3f p7 = new Vector3f(x2, y2, z1);
        Vector3f p8 = new Vector3f(x2, y2, z2);

        Vector3f[] positions = switch (faceType) {
            case "west" -> new Vector3f[]{p4, p3, p1, p2};
            case "east" -> new Vector3f[]{p7, p8, p6, p5};
            case "north" -> new Vector3f[]{p3, p7, p5, p1};
            case "south" -> new Vector3f[]{p8, p4, p2, p6};
            case "up" -> new Vector3f[]{p4, p8, p7, p3};
            case "down" -> new Vector3f[]{p1, p5, p6, p2};
            default -> null;
        };

        Vector4f tempPos = new Vector4f();
        for (int i = 0; i < 4; i++) {
            tempPos.set(positions[i].x(), positions[i].y(), positions[i].z(), 1.0f).mul(cubeBakeMat);
            face.positions[i] = new float[]{tempPos.x(), tempPos.y(), tempPos.z()};
        }

        face.u = new float[]{u0, u1, u1, u0};
        face.v = new float[]{v0, v0, v1, v1};
        cube.faces.add(face);
    }

    private JsonObject createFaceUVNode(float u, float v, float w, float h) {
        JsonObject node = new JsonObject();
        JsonArray uv = new JsonArray(); uv.add(u); uv.add(v);
        JsonArray size = new JsonArray(); size.add(w); size.add(h);
        node.add("uv", uv);
        node.add("uv_size", size);
        return node;
    }

    public static RawYsmModel.RawAnimationFile parseAnimationFile(byte[] data) {
        String json = new String(data, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        RawYsmModel.RawAnimationFile raf = new RawYsmModel.RawAnimationFile();

        if (root.has("animations")) {
            JsonObject anims = root.getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> entry : anims.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject aObj = entry.getValue().getAsJsonObject();
                RawYsmModel.RawAnimation anim = new RawYsmModel.RawAnimation();
                anim.name = entry.getKey();
                anim.length = (float) getDouble(aObj, "animation_length", Float.POSITIVE_INFINITY);

                if (aObj.has("loop")) {
                    String loopStr = getJsonString(aObj.get("loop"));
                    if ("true".equals(loopStr)) anim.loopMode = 1;
                    else if ("hold_on_last_frame".equals(loopStr)) anim.loopMode = 3;
                    else anim.loopMode = 0;
                } else {
                    anim.loopMode = 2;
                }

                if (aObj.has("blend_weight")) {
                    JsonElement bw = aObj.get("blend_weight");
                    if (bw.isJsonPrimitive() && bw.getAsJsonPrimitive().isNumber()) {
                        anim.blendWeight = bw.getAsFloat();
                    } else {
                        anim.blendWeight = getJsonString(bw);
                    }
                }

                if (aObj.has("bones") && aObj.get("bones").isJsonObject()) {
                    JsonObject bonesObj = aObj.getAsJsonObject("bones");
                    for (Map.Entry<String, JsonElement> bEntry : bonesObj.entrySet()) {
                        if (!bEntry.getValue().isJsonObject()) continue;
                        JsonObject bObj = bEntry.getValue().getAsJsonObject();
                        RawYsmModel.RawBoneAnimation ba = new RawYsmModel.RawBoneAnimation();
                        ba.boneName = bEntry.getKey();

                        parseChannelToKeyframes(bObj, "rotation", ba.rotation);
                        parseChannelToKeyframes(bObj, "position", ba.position);
                        parseChannelToKeyframes(bObj, "scale", ba.scale);

                        anim.boneAnimations.add(ba);
                    }
                }

                if (aObj.has("timeline") && aObj.get("timeline").isJsonObject()) {
                    JsonObject tlObj = aObj.getAsJsonObject("timeline");
                    for (Map.Entry<String, JsonElement> tlEntry : tlObj.entrySet()) {
                        RawYsmModel.RawTimelineEvent tle = new RawYsmModel.RawTimelineEvent();
                        tle.timestamp = Float.parseFloat(tlEntry.getKey());
                        JsonElement val = tlEntry.getValue();
                        Iterable<JsonElement> arr = val.isJsonArray() ? val.getAsJsonArray() : Collections.singletonList(val);
                        for (JsonElement e : arr) tle.events.add(getJsonString(e));
                        anim.timelineEvents.add(tle);
                    }
                }

                if (aObj.has("sound_effects") && aObj.get("sound_effects").isJsonObject()) {
                    JsonObject sfxObj = aObj.getAsJsonObject("sound_effects");
                    for (Map.Entry<String, JsonElement> sfxEntry : sfxObj.entrySet()) {
                        RawYsmModel.RawSoundEffect sfx = new RawYsmModel.RawSoundEffect();
                        sfx.timestamp = Float.parseFloat(sfxEntry.getKey());
                        sfx.effectName = getStr(sfxEntry.getValue().getAsJsonObject(), "effect", "");
                        anim.soundEffects.add(sfx);
                    }
                }

                raf.animations.put(anim.name, anim);
            }
        }
        return raf;
    }

    private RawYsmModel.RawAnimationFile parseAnimations(byte[] data) {
        return parseAnimationFile(data);
    }

    private static void parseChannelToKeyframes(JsonObject bObj, String channel, List<RawYsmModel.RawKeyframe> targetList) {
        if (!bObj.has(channel)) return;
        JsonElement cElem = bObj.get(channel);

        if (!cElem.isJsonObject()) {
            RawYsmModel.RawKeyframe kf = new RawYsmModel.RawKeyframe();
            kf.timestamp = 0.0f;
            kf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_LINEAR;
            kf.hasPreData = false;
            kf.postData = jsonElementToMolangArray(cElem);
            targetList.add(kf);
            return;
        }

        JsonObject kfsObj = cElem.getAsJsonObject();
        List<Map.Entry<String, JsonElement>> sorted = new ArrayList<>(kfsObj.entrySet());
        sorted.sort(Comparator.comparingDouble(e -> Double.parseDouble(e.getKey())));

        for (Map.Entry<String, JsonElement> entry : sorted) {
            RawYsmModel.RawKeyframe kf = new RawYsmModel.RawKeyframe();
            kf.timestamp = Float.parseFloat(entry.getKey());
            kf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_LINEAR;

            JsonElement valElem = entry.getValue();
            if (valElem.isJsonObject()) {
                JsonObject obj = valElem.getAsJsonObject();
                if (obj.has("lerp_mode")) {
                    String lm = getJsonString(obj.get("lerp_mode"));
                    if ("catmullrom".equals(lm)) kf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_CATMULLROM;
                    else if ("step".equals(lm)) kf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_STEP;
                } else
                    kf.interpolationMode = RawYsmModel.RawKeyframe.INTERPOLATION_STEP;

                if (obj.has("pre") && obj.has("post")) {
                    kf.hasPreData = true;
                    kf.preData = jsonElementToMolangArray(obj.get("pre"));
                    kf.postData = jsonElementToMolangArray(obj.get("post"));
                } else {
                    kf.hasPreData = false;
                    kf.postData = jsonElementToMolangArray(obj.has("post") ? obj.get("post") : obj.has("pre") ? obj.get("pre") : obj);
                }
            } else {
                kf.hasPreData = false;
                kf.postData = jsonElementToMolangArray(valElem);
            }
            targetList.add(kf);
        }
    }

    private static Object[] jsonElementToMolangArray(JsonElement elem) {
        Object[] arr = new Object[]{0f, 0f, 0f};
        if (elem == null || elem.isJsonNull()) return arr;

        if (elem.isJsonArray()) {
            JsonArray jArr = elem.getAsJsonArray();
            for (int i = 0; i < Math.min(3, jArr.size()); i++) {
                JsonElement e = jArr.get(i);
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) arr[i] = e.getAsFloat();
                else arr[i] = getJsonString(e);
            }
        } else {
            Object val;
            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isNumber()) val = elem.getAsFloat();
            else val = getJsonString(elem);
            arr[0] = val; arr[1] = val; arr[2] = val;
        }
        return arr;
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
            ac.initialState = getStr(acObj, "initial_state", "default");

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
                                state.animations.put(getJsonString(ae), "");
                            } else if (ae.isJsonObject()) {
                                for (Map.Entry<String, JsonElement> objEntry : ae.getAsJsonObject().entrySet()) {
                                    state.animations.put(objEntry.getKey(), getJsonString(objEntry.getValue()));
                                }
                            }
                        }
                    }

                    if (sObj.has("transitions") && sObj.get("transitions").isJsonArray()) {
                        for (JsonElement te : sObj.getAsJsonArray("transitions")) {
                            if (te.isJsonObject()) {
                                for (Map.Entry<String, JsonElement> objEntry : te.getAsJsonObject().entrySet()) {
                                    state.transitions.put(objEntry.getKey(), getJsonString(objEntry.getValue()));
                                }
                            }
                        }
                    }

                    if (sObj.has("on_entry") && sObj.get("on_entry").isJsonArray()) {
                        for (JsonElement oe : sObj.getAsJsonArray("on_entry")) state.onEntry.add(getJsonString(oe));
                    }

                    if (sObj.has("on_exit") && sObj.get("on_exit").isJsonArray()) {
                        for (JsonElement oe : sObj.getAsJsonArray("on_exit")) state.onExit.add(getJsonString(oe));
                    }

                    if (sObj.has("sound_effects") && sObj.get("sound_effects").isJsonArray()) {
                        for (JsonElement se : sObj.getAsJsonArray("sound_effects")) {
                            if (se.isJsonObject()) state.soundEffects.add(getStr(se.getAsJsonObject(), "effect", ""));
                            else if (se.isJsonPrimitive()) state.soundEffects.add(getJsonString(se));
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
        if (inMemoryFiles != null) {
            for (Map.Entry<String, byte[]> entry : inMemoryFiles.entrySet()) {
                if (isGlobalResource(entry.getKey())) {
                    processGlobalResourceFile(entry.getKey(), entry.getValue());
                }
            }
        } else {
            try (Stream<Path> stream = Files.walk(rootPath)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> isGlobalResource(rootPath.relativize(path).toString().replace('\\', '/')))
                        .forEach(path -> {
                            String relativePath = rootPath.relativize(path).toString().replace('\\', '/');
                            byte[] data = readResource(relativePath);
                            if (data != null) {
                                processGlobalResourceFile(relativePath, data);
                            }
                        });
            } catch (IOException e) {
                System.err.println("[SM] Warning: Failed to scan global resources. " + e.getMessage());
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
            String soundName = extractFileName(relativePath);
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
                        langMap.put(langEntry.getKey(), getJsonString(langEntry.getValue()));
                    }
                }
                model.languageFiles.put(locale, new RawYsmModel.RawLanguageFile(hash, langMap));
            } catch (Exception ignored) {}
        }
        else if (relativePath.startsWith("functions/") && relativePath.endsWith(".molang")) {
            String fnName = extractFileName(relativePath);
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


    private record ImageMeta(int width, int height, int format) {}


    private static ImageMeta parseImageMeta(byte[] data, String path) {
        if (data == null || data.length < 8) {
            throw new RuntimeException("Invalid image data. File too small: " + path);
        }

        int format = detectFormat(data);
        if (format == 0) {
            throw new RuntimeException("Unsupported image format for: " + path);
        }

        if (format == 2 && data.length >= 24) {
            int w = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
            int h = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
            return new ImageMeta(w, h, format);
        }

        try {
            BufferedImage img = null;
            switch (format) {
                case 1, 3 -> img = ImageIO.read(new ByteArrayInputStream(data));
                case 4 -> img = new WebpDecoder().read(data);
                case 5 -> img = new AvifDecoder().read(data);
            }
            if (img != null) {
                return new ImageMeta(img.getWidth(), img.getHeight(), format);
            }
            throw new RuntimeException("Failed to decode image dimensions for: " + path);
        } catch (Exception e) {
            throw new RuntimeException("Error processing image: " + path, e);
        }
    }

    public static int detectFormat(byte[] data) {
        if (data.length >= 2 && data[0] == 0x42 && data[1] == 0x4D) return 1; // 'BM'
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) return 2; // PNG
        if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) return 3; // JPEG
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') return 4; // WEBP RIFF...WEBP
        if (data.length >= 12 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') return 5; // AVIF ftyp
        return 0;
    }

    public static int getAnimTypeFromKey(String key) {
        if (key == null) return 0;
        return switch (key) {
            case "main" -> 1;
            case "arm" -> 2;
            case "extra" -> 3;
            case "tac" -> 4;
            case "arrow" -> 5;
            case "carryon" -> 6;
            case "parcool" -> 7;
            case "swem" -> 8;
            case "slashblade" -> 9;
            case "tlm" -> 10;
            case "fp.arm", "fp_arm" -> 11;
            case "immersive_melodies" -> 12;
            case "iss", "irons_spell_books" -> 13;
            default -> parseUnknownAnimType(key);
        };
    }

    private static int parseUnknownAnimType(String key) {
        if (!key.startsWith("unk_")) return 0;
        try {
            int type = Integer.parseInt(key.substring(4));
            return type >= 0 ? type : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static String getAnimKeyFromType(int type) {
        return switch (type) {
            case 1 -> "main";
            case 2 -> "arm";
            case 3 -> "extra";
            case 4 -> "tac";
            case 5 -> "arrow";
            case 6 -> "carryon";
            case 7 -> "parcool";
            case 8 -> "swem";
            case 9 -> "slashblade";
            case 10 -> "tlm";
            case 11 -> "fp_arm";
            case 12 -> "immersive_melodies";
            case 13 -> "irons_spell_books";
            default -> "unk_" + type;
        };
    }

    private static String getStr(JsonObject obj, String key, String def) {
        return obj.has(key) ? getJsonString(obj.get(key)) : def;
    }

    private static boolean getBool(JsonObject obj, String key, boolean def) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        return obj.has(key) ? obj.get(key).getAsDouble() : def;
    }

    private static float[] getFloatArray(JsonObject obj, String key, int size) {
        float[] result = new float[size];
        if (obj.has(key)) {
            JsonArray arr = obj.getAsJsonArray(key);
            for (int i = 0; i < Math.min(arr.size(), size); i++) result[i] = arr.get(i).getAsFloat();
        }
        return result;
    }

    private static String extractFileName(String fullPath) {
        String name = cleanJsonString(fullPath);
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) name = name.substring(lastSlash + 1);
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx >= 0) name = name.substring(0, dotIdx);
        return name;
    }

    private static String getJsonString(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return element.toString();
    }

    private static String cleanJsonString(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.length() >= 2 && cleaned.charAt(0) == '"' && cleaned.charAt(cleaned.length() - 1) == '"') {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
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
        if (inMemoryFiles != null) {
            for (String pathKey : inMemoryFiles.keySet()) {
                if (pathKey.endsWith(".png") && !pathKey.contains("/")) {
                    pngFiles.add(pathKey);
                }
            }
        } else {
            try (Stream<Path> stream = Files.list(rootPath)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String fileName = path.getFileName().toString();
                    if (fileName.endsWith(".png")) {
                        pngFiles.add(fileName);
                    }
                });
            } catch (IOException e) { e.printStackTrace(); }
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

        model.mainEntity.mainModel = parseGeometry(mainData, 1);
        model.mainEntity.armModel = parseGeometry(armData, 2);

        for (String texName : pngFiles) {
            if (texName.equals("arrow.png")) continue;
            byte[] texData = readResource(texName);
            if (texData != null) {
                ImageMeta meta = parseImageMeta(texData, texName);
                RawYsmModel.RawTexture rt = new RawYsmModel.RawTexture();
                rt.hash = sha256Hex(texData);
                rt.width = meta.width();
                rt.height = meta.height();
                rt.imageFormat = meta.format();
                rt.name = extractFileName(texName);
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
                raf.animType = getAnimTypeFromKey(animKey);
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
            arrowSub.model = parseGeometry(arrowData, 3);

            byte[] arrowTexData = readResource("arrow.png");
            if (arrowTexData != null) {
                ImageMeta meta = parseImageMeta(arrowTexData, "arrow.png");
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
                raf.animType = getAnimTypeFromKey("arrow");
                arrowSub.animationFiles.put("sub_anim", raf);
            }

            model.projectiles.put("arrow", arrowSub);
        }
    }

    private static Map<String, byte[]> readZipEntries(Path sourcePath, java.nio.charset.Charset charset) throws IOException {
        Map<String, byte[]> rawEntries = new LinkedHashMap<>();
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(sourcePath.toFile(), charset)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                java.util.zip.ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                try (java.io.InputStream input = zipFile.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes((int) Math.min(MAX_RESOURCE_BYTES + 1, Integer.MAX_VALUE));
                    if (bytes.length > MAX_RESOURCE_BYTES) {
                        System.err.println("[SM] Warning: Skipping oversized zip entry (" + bytes.length + " bytes): " + entry.getName());
                        continue;
                    }
                    rawEntries.putIfAbsent(entry.getName(), bytes);
                }
            }
        }
        // 与 resolveArchiveModelRoot 一致的模型根目录探测（字符串层面）
        String rootPrefix = detectInMemoryModelRoot(rawEntries.keySet());
        if (rootPrefix == null) {
            return rawEntries;
        }
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : rawEntries.entrySet()) {
            String name = entry.getKey();
            String stripped = name;
            if (!rootPrefix.isEmpty() && name.startsWith(rootPrefix)) {
                stripped = name.substring(rootPrefix.length());
            }
            String normalized = normalizeResourceKey(stripped);
            if (!normalized.isEmpty()) {
                entries.putIfAbsent(normalized, entry.getValue());
            }
        }
        return entries;
    }

    private static String detectInMemoryModelRoot(java.util.Set<String> names) {
        java.util.Set<String> normalized = new java.util.HashSet<>();
        for (String name : names) {
            normalized.add(normalizeResourceKey(name));
        }
        if (normalized.contains("ysm.json") || (normalized.contains("main.json") && normalized.contains("arm.json"))) {
            return "";
        }
        String detected = null;
        for (String name : names) {
            int slash = name.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String segment = name.substring(0, slash);
            String folderKey = normalizeResourceKey(segment);
            boolean modelFolder = normalized.contains(folderKey + "/ysm.json")
                    || (normalized.contains(folderKey + "/main.json") && normalized.contains(folderKey + "/arm.json"));
            if (!modelFolder) {
                continue;
            }
            if (detected == null) {
                detected = segment;
            } else if (!normalizeResourceKey(segment).equals(normalizeResourceKey(detected))) {
                // 多个不同的模型目录 → 与 resolveArchiveModelRoot 一致，以整个压缩包为根
                return null;
            }
        }
        return detected;
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
            model.metadata.name = getStr(infoObj, "name", "");
        }
        if (infoObj.has("tips") && (overwrite || model.metadata.tips.isEmpty())) {
            model.metadata.tips = getStr(infoObj, "tips", "");
        }
        if (infoObj.has("license") && (overwrite || model.metadata.licenseDescription.isEmpty())) {
            model.metadata.licenseDescription = getStr(infoObj, "license", "");
        }
        if (infoObj.has("free")) {
            if (overwrite || !model.properties.isFree) {
                model.properties.isFree = getBool(infoObj, "free", false);
            }
        }

        if (infoObj.has("authors") && infoObj.get("authors").isJsonArray()) {
            if (overwrite || model.metadata.authors.isEmpty()) {
                model.metadata.authors.clear();
                for (JsonElement e : infoObj.getAsJsonArray("authors")) {
                    RawYsmModel.RawMetadata.Author author = new RawYsmModel.RawMetadata.Author();
                    author.name = getJsonString(e);
                    model.metadata.authors.add(author);
                }
            }
        }

        if (infoObj.has("extra_animation_names") && infoObj.get("extra_animation_names").isJsonArray()) {
            if (overwrite || model.properties.extraAnimations.isEmpty()) {
                model.properties.extraAnimations.clear();
                JsonArray extras = infoObj.getAsJsonArray("extra_animation_names");
                for (int i = 0; i < extras.size(); i++) {
                    String extraName = getJsonString(extras.get(i));
                    model.properties.extraAnimations.put("extra" + i, extraName);
                }
            }
        }
    }
}
