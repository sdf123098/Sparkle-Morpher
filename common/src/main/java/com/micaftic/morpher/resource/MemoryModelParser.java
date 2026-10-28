package com.micaftic.morpher.resource;

import com.micaftic.morpher.client.animation.BedrockAnimationMapping;
import com.micaftic.morpher.core.security.YsmCrypt;
import com.micaftic.morpher.legacy.compat.LegacyCompatModelFormat;
import com.micaftic.morpher.resource.bbmodel.BBModelFile;
import com.micaftic.morpher.resource.bbmodel.BBModelParser;
import com.micaftic.morpher.resource.bbmodel.BBToRawConverter;
import com.micaftic.morpher.resource.bbmodel.ZipModelSniffer;
import com.micaftic.morpher.resource.gltf.GltfLoadResult;
import com.micaftic.morpher.resource.gltf.GltfLoader;
import com.micaftic.morpher.resource.pojo.RawYsmModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 从内存字节解析模型（M2）：不写临时文件，按文件名分发 YSM / ZIP / bbmodel /
 * Bedrock geometry / 自包含 glTF。
 *
 * <p>ZIP 展开复用 {@link ModelResourceContainer#zipBytes(byte[])} 的限额与根目录剥离语义；
 * glTF 只接受自包含 GLB 或 data-URI，外部 buffer/image 直接报不支持。</p>
 */
public final class MemoryModelParser {

    /** 单个 zip 条目上限，与既有导入路径一致。 */
    private static final long ZIP_ENTRY_LIMIT_BYTES = 64L * 1024 * 1024;

    public enum Kind {
        YSM,
        ZIP,
        BBMODEL,
        BEDROCK_GEO,
        GLTF,
        UNKNOWN
    }

    private MemoryModelParser() {
    }

    public static Kind detectKind(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ysm")) return Kind.YSM;
        if (lower.endsWith(".zip")) return Kind.ZIP;
        if (lower.endsWith(".bbmodel")) return Kind.BBMODEL;
        if (lower.endsWith(".geo.json") || lower.endsWith("geometry.json")) return Kind.BEDROCK_GEO;
        if (lower.endsWith(".gltf") || lower.endsWith(".glb")) return Kind.GLTF;
        return Kind.UNKNOWN;
    }

    public static boolean isGltf(String fileName) {
        return detectKind(fileName) == Kind.GLTF;
    }

    /** 解析 legacy（RawYsmModel）格式；glTF 必须走 {@link #parseGltf(byte[], String)}。 */
    public static RawYsmModel parseLegacy(byte[] data, String fileName) throws Exception {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Model source is empty: " + fileName);
        }
        return switch (detectKind(fileName)) {
            case YSM -> parseYsm(data, fileName);
            case ZIP -> parseZip(data);
            case BBMODEL -> parseBbModel(data, fileName);
            case BEDROCK_GEO -> parseBedrockGeo(data, fileName);
            case GLTF -> throw new IllegalArgumentException(
                    "glTF must be parsed through the glTF pipeline: " + fileName);
            case UNKNOWN -> throw new IllegalArgumentException("Unsupported model import type: " + fileName);
        };
    }

    /**
     * 解析自包含 glTF/GLB。{@code baseDirectory == null} 让外部 buffer/image
     * 明确失败（"cannot be resolved without a base directory"），不会偷偷读盘。
     */
    public static GltfLoadResult parseGltf(byte[] data, String fileName) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("glTF source is empty: " + fileName);
        }
        return GltfLoader.loadWithManifest(data, null, fileName);
    }

    private static RawYsmModel parseYsm(byte[] data, String source) throws Exception {
        int ysmCryptoVersion = LegacyCompatModelFormat.detectCryptoVersion(data);
        if (ysmCryptoVersion == 1 || ysmCryptoVersion == 2) {
            try (YSMFolderDeserializer deserializer =
                         new YSMFolderDeserializer(LegacyCompatModelFormat.read(data))) {
                return deserializer.deserialize();
            }
        }
        try {
            byte[] decrypted = YsmCrypt.decryptYsmFile(data);
            try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decrypted)) {
                RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                deserializer.parseYSMFooter(rawModel);
                return rawModel;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid model: " + source, e);
        }
    }

    private static RawYsmModel parseZip(byte[] data) throws Exception {
        ZipModelSniffer sniff = ZipModelSniffer.sniff(data, ZIP_ENTRY_LIMIT_BYTES);
        return switch (sniff.kind) {
            case FIGURA_AVATAR, PLAIN_BBMODEL -> parseSniffedBbModel(sniff, data);
            case BEDROCK_PACK -> parseBedrockPack(sniff);
            // YSM_FOLDER / UNKNOWN：交给内存 YSM 容器（坏 zip 在容器构造时报错）
            default -> {
                try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(data)) {
                    yield deserializer.deserialize();
                }
            }
        };
    }

    private static RawYsmModel parseSniffedBbModel(ZipModelSniffer sniff, byte[] archiveBytes) {
        String json = new String(sniff.bbmodelBytes, StandardCharsets.UTF_8);
        BBModelFile bbmodel = BBModelParser.parse(json);
        RawYsmModel rawModel = BBToRawConverter.convert(bbmodel, sniff.sideTextures);
        rawModel.properties.sha256 = BBToRawConverter.importCacheSha256(archiveBytes);
        return rawModel;
    }

    private static RawYsmModel parseBbModel(byte[] data, String source) {
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            BBModelFile bbmodel = BBModelParser.parse(json);
            RawYsmModel rawModel = BBToRawConverter.convert(bbmodel);
            rawModel.properties.sha256 = BBToRawConverter.importCacheSha256(data);
            return rawModel;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid bbmodel file: " + source, e);
        }
    }

    private static RawYsmModel parseBedrockGeo(byte[] data, String fileName) {
        String identifier = bedrockIdentifierFromFileName(fileName);
        RawYsmModel.RawGeometry geometry = com.micaftic.morpher.resource.YsmGeometryParsing.parseBedrockGeometry(data, identifier);
        if (geometry == null || geometry.bones.isEmpty()) {
            throw new IllegalArgumentException("Invalid Bedrock geometry: " + fileName);
        }
        RawYsmModel raw = assembleBedrockModel(geometry, null, null);
        normalizeBedrockCase(raw);
        raw.properties.sha256 = BBToRawConverter.importCacheSha256(data);
        return raw;
    }

    private static RawYsmModel parseBedrockPack(ZipModelSniffer sniff) {
        String identifier = bedrockIdentifierFromFileName(sniff.bedrockGeoPath);
        RawYsmModel.RawGeometry geometry =
                com.micaftic.morpher.resource.YsmGeometryParsing.parseBedrockGeometry(sniff.bedrockGeoBytes, identifier);
        if (geometry == null || geometry.bones.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid Bedrock pack (no usable geometry): " + sniff.bedrockGeoPath);
        }

        Map<String, RawYsmModel.RawTexture> textures = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : sniff.sideTextures.entrySet()) {
            RawYsmModel.RawTexture texture =
                    com.micaftic.morpher.resource.YsmTextureParsing.parseBedrockTexture(entry.getValue(), entry.getKey());
            if (texture.data != null) {
                textures.put(entry.getKey(), texture);
            }
        }

        Map<String, RawYsmModel.RawAnimationFile> animationFiles = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, byte[]> entry : sniff.bedrockAnimations.entrySet()) {
            try {
                RawYsmModel.RawAnimationFile animationFile =
                        com.micaftic.morpher.resource.YsmAnimationParsing.parseAnimationFile(entry.getValue());
                if (!animationFile.animations.isEmpty()) {
                    animationFiles.put("bedrock-anim-" + (index++), animationFile);
                }
            } catch (Exception e) {
                System.err.println("[SM] Warning: Failed to parse Bedrock animation "
                        + entry.getKey() + ": " + e);
            }
        }

        animationFiles = BedrockAnimationMapping.remapToActions(animationFiles);
        RawYsmModel raw = assembleBedrockModel(geometry, textures, animationFiles);
        normalizeBedrockCase(raw);
        raw.properties.sha256 = BBToRawConverter.importCacheSha256(sniff.bedrockGeoBytes);
        return raw;
    }

    /** 组装 Bedrock 直读模型：几何 + 可选纹理 + 可选动画，属性对齐 bbmodel 导入（scale=1）。 */
    private static RawYsmModel assembleBedrockModel(RawYsmModel.RawGeometry geometry,
                                                    Map<String, RawYsmModel.RawTexture> textures,
                                                    Map<String, RawYsmModel.RawAnimationFile> animationFiles) {
        RawYsmModel raw = new RawYsmModel();
        raw.modelId = (geometry.identifier == null || geometry.identifier.isEmpty())
                ? "bedrock" : geometry.identifier;
        raw.formatVersion = 65535;
        raw.metadata = new RawYsmModel.RawMetadata();
        raw.properties = new RawYsmModel.RawProperties();
        raw.properties.widthScale = 1.0f;
        raw.properties.heightScale = 1.0f;
        raw.properties.defaultTexture = "default";

        geometry.modelType = 1;
        RawYsmModel.RawMainEntity mainEntity = new RawYsmModel.RawMainEntity();
        mainEntity.mainModel = geometry;
        if (textures != null) {
            mainEntity.textures.putAll(textures);
            if (!textures.isEmpty()) {
                raw.properties.defaultTexture = textures.keySet().iterator().next();
            }
        }
        if (animationFiles != null) {
            mainEntity.animationFiles.putAll(animationFiles);
        }
        raw.mainEntity = mainEntity;
        raw.footer = new RawYsmModel.RawFooter();
        return raw;
    }

    /**
     * Bedrock 导入边界大小写归一：骨名与动画骨名统一小写。
     * 基岩版动画用小写（leftarm）、几何用驼峰（leftArm），
     * 这里在入口处把两者归一为小写以便动画绑定；
     * 仅在 Bedrock 入口生效，不影响 YSM/内置模型路径。
     */
    private static void normalizeBedrockCase(RawYsmModel raw) {
        if (raw == null || raw.mainEntity == null) {
            return;
        }
        Map<String, String> rename = new LinkedHashMap<>();
        RawYsmModel.RawGeometry geometry = raw.mainEntity.mainModel;
        if (geometry != null && geometry.bones != null) {
            for (RawYsmModel.RawBone bone : geometry.bones) {
                if (bone.name == null || bone.name.isEmpty()) continue;
                String lower = bone.name.toLowerCase(Locale.ROOT);
                if (!lower.equals(bone.name)) {
                    rename.put(bone.name, lower);
                }
            }
            for (RawYsmModel.RawBone bone : geometry.bones) {
                if (bone.name != null) {
                    bone.name = rename.getOrDefault(bone.name, bone.name);
                }
                if (bone.parentName != null) {
                    bone.parentName = rename.getOrDefault(bone.parentName, bone.parentName);
                }
            }
        }
        for (RawYsmModel.RawAnimationFile animationFile : raw.mainEntity.animationFiles.values()) {
            if (animationFile == null || animationFile.animations == null) continue;
            for (RawYsmModel.RawAnimation animation : animationFile.animations.values()) {
                if (animation == null || animation.boneAnimations == null) continue;
                for (RawYsmModel.RawBoneAnimation boneAnimation : animation.boneAnimations) {
                    if (boneAnimation != null && boneAnimation.boneName != null) {
                        boneAnimation.boneName = boneAnimation.boneName.toLowerCase(Locale.ROOT);
                    }
                }
            }
        }
    }

    /** 从文件名推导 Bedrock 几何 identifier：去掉 .geo.json / geometry.json 后缀后的文件名。 */
    private static String bedrockIdentifierFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        String lower = fileName.toLowerCase(Locale.ROOT);
        String base;
        if (lower.endsWith(".geo.json")) {
            base = fileName.substring(0, fileName.length() - ".geo.json".length());
        } else if (lower.endsWith("geometry.json")) {
            base = fileName.substring(0, fileName.length() - "geometry.json".length());
        } else {
            base = fileName;
        }
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.trim();
        return base.isEmpty() ? null : base;
    }
}
