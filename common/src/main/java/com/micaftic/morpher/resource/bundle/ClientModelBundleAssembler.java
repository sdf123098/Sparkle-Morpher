package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.AudioTrackData;
import com.micaftic.morpher.client.ClientModelInfo;
import com.micaftic.morpher.client.model.MainModelData;
import com.micaftic.morpher.client.model.SpecialHandLocatorProfile;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.core.compat.oculus.ShadersTextureType;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.file.AnimationControllerFile;
import com.micaftic.morpher.geckolib3.file.AnimationFile;
import com.micaftic.morpher.geckolib3.file.ModelExtraResourcesFile;
import com.micaftic.morpher.geckolib3.file.ProjectileModelFiles;
import com.micaftic.morpher.geckolib3.file.VehicleModelFiles;
import com.micaftic.morpher.geckolib3.resource.GeckoLibCache;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.resource.models.GeometryDescription;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.2.7 §24.1：从 {@code YSMClientMapper} 外提的模型装配编排职责（行为等价，纯搬运）。
 *
 * 编排职责（模型装配策略）在这里，格式处理/几何/动画/音频/贴图/元数据各自委托给
 * {@link TextureDecoder} / {@link TextureAlphaAnalyzer} / {@link GeometryBaker} /
 * {@link AnimationMapper} / {@link AudioResourceMapper} / {@link GuiConfigMapper}。
 * 旧入口 {@code YSMClientMapper.buildParsedBundle} 保留为 delegate（Facade First）。
 */
public final class ClientModelBundleAssembler {

    private ClientModelBundleAssembler() {
    }

    public static ClientModelInfo buildParsedBundle(RawYsmModel raw, String modelId) {
        Map<String, OuterFileTexture> mainTextures = new LinkedHashMap<>();
        int textureCount = Math.max(1, raw.mainEntity.textures.size());

        List<BufferedImage> imagesList = new ArrayList<>();

        for (RawYsmModel.RawTexture rt : raw.mainEntity.textures.values()) {
            if (rt.data == null || rt.data.length == 0) {
                throw new IllegalArgumentException("Missing texture source data: " + rt.name + " (" + rt.hash + ")");
            }
            BufferedImage img = TextureDecoder.decodeToImage(rt.data, rt.imageFormat, rt.width, rt.height);
            imagesList.add(img);

            byte[] processedData = TextureDecoder.toPng(rt.data, rt.imageFormat, rt.width, rt.height);
            OuterFileTexture tex = new OuterFileTexture(processedData, modelId);

            Map<ShadersTextureType, OuterFileTexture> suffixTextures = new LinkedHashMap<>();
            for (RawYsmModel.RawTexture.SubTexture sub : rt.subTextures) {
                if (sub.data == null) continue;
                byte[] processedSubData = TextureDecoder.toPng(sub.data, sub.imageFormat, sub.width, sub.height);
                if (sub.specularType == 1) {
                    suffixTextures.put(ShadersTextureType.NORMAL, new OuterFileTexture(processedSubData, modelId));
                } else if (sub.specularType == 2) {
                    suffixTextures.put(ShadersTextureType.SPECULAR, new OuterFileTexture(processedSubData, modelId));
                }
            }
            tex.setSuffixTextures(suffixTextures);
            String textureName = uniqueTextureName(mainTextures, rt.name, rt.hash);
            if (!textureName.equals(rt.name)) {
                YesSteveModel.LOGGER.warn("[SM] Duplicate texture name '{}' in model '{}'; using '{}'", rt.name, modelId, textureName);
            }
            mainTextures.put(textureName, tex);
        }
        Map<String, OuterFileTexture> avatarTextures = new LinkedHashMap<>();
        for (RawYsmModel.RawMetadata.Author author : raw.metadata.authors) {
            if (author.avatarImage == null) continue;
            byte[] processedAvatarData = TextureDecoder.toPng(author.avatarImage.data, author.avatarImage.format, author.avatarImage.width, author.avatarImage.height);
            OuterFileTexture tex = new OuterFileTexture(processedAvatarData, modelId);
            avatarTextures.put(author.avatarImage.name, tex);
        }
        OrderedStringMap<String, OuterFileTexture> textureMap = GeometryBaker.buildTextureMap(mainTextures);

        GeometryDescription context = GeometryBaker.buildContext(raw.mainEntity.mainModel);

        BufferedImage[] imagesArray = imagesList.toArray(new BufferedImage[0]);
        TextureAlphaAnalyzer mainScanner = raw.mainEntity.mainModel != null ?
                new TextureAlphaAnalyzer(imagesArray, textureCount) : null;
        TextureAlphaAnalyzer armScanner = raw.mainEntity.armModel != null ?
                new TextureAlphaAnalyzer(imagesArray, textureCount) : null;

        Map<String, String> mainParentMap = GeometryBaker.buildParentMap(raw.mainEntity.mainModel);
        SpecialHandLocatorProfile specialHandLocatorProfile = detectSpecialHandLocatorProfile(raw, mainParentMap);

        GeoModel mainMesh = GeometryBaker.buildMesh(raw.mainEntity.mainModel, context, textureCount, mainScanner, raw.properties.allCutout, specialHandLocatorProfile);
        GeoModel armMesh = raw.mainEntity.armModel != null ? GeometryBaker.buildMesh(raw.mainEntity.armModel, context, textureCount, armScanner, raw.properties.allCutout) : mainMesh;

        GeoModel[] meshes = new GeoModel[]{mainMesh, armMesh};

        Map<String, AnimationFile> animations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> entry : raw.mainEntity.animationFiles.entrySet()) {
            animations.put(entry.getKey(), new AnimationFile(AnimationMapper.buildAnimations(entry.getValue(), raw.properties.mergeMultilineExpr)));
        }

        List<AnimationControllerFile> controllersList = new ArrayList<>();
        if (raw.mainEntity.animationControllerFiles != null) {
            for (RawYsmModel.RawAnimationControllerFile file : raw.mainEntity.animationControllerFiles) {
                Map<String, AnimationController> controllerMap = AnimationMapper.buildControllers(file.controllers, raw.properties.mergeMultilineExpr);
                if (!controllerMap.isEmpty()) {
                    controllersList.add(new AnimationControllerFile(controllerMap));
                }
            }
        }

        MainModelData mainModelData = new MainModelData(meshes, animations, controllersList.toArray(new AnimationControllerFile[0]), textureMap, specialHandLocatorProfile);

        ServerModelInfo modelInfo = GuiConfigMapper.buildModelInfo(raw);
        ModelExtraResourcesFile extraResources = buildExtraResources(raw);
        ProjectileModelFiles[] extraItemModels = buildExtraItemModels(raw, context, raw.properties.mergeMultilineExpr);
        VehicleModelFiles[] extraEntityModels = buildExtraEntityModels(raw, context, raw.properties.mergeMultilineExpr);
        Map<String, OuterFileTexture> extraTextures = buildExtraTextures(raw);

        return new ClientModelInfo(mainModelData, extraItemModels, extraEntityModels, extraResources, modelInfo, avatarTextures, extraTextures);
    }

    // ---- 导入策略：特殊手部 locator 推断 ----

    public static SpecialHandLocatorProfile detectSpecialHandLocatorProfile(RawYsmModel raw, Map<String, String> parentMap) {
        if (raw == null || raw.mainEntity == null || parentMap == null || parentMap.isEmpty()) {
            return SpecialHandLocatorProfile.NONE;
        }
        if (!GeometryBaker.hasNormalizedExactBone(parentMap, "LeftHandLocator") || !GeometryBaker.hasNormalizedExactBone(parentMap, "RightHandLocator")) {
            return SpecialHandLocatorProfile.NONE;
        }
        if (GeometryBaker.hasNormalizedSwordBone(parentMap, "LeftSword") || GeometryBaker.hasNormalizedSwordBone(parentMap, "RightSword")) {
            return SpecialHandLocatorProfile.NONE;
        }
        for (int i = 2; i <= 8; i++) {
            if (GeometryBaker.hasNormalizedExactBone(parentMap, "LeftHandLocator" + i) || GeometryBaker.hasNormalizedExactBone(parentMap, "RightHandLocator" + i)) {
                return SpecialHandLocatorProfile.NONE;
            }
        }

        boolean handLocatorZero = false;
        for (RawYsmModel.RawAnimationFile animationFile : raw.mainEntity.animationFiles.values()) {
            ScaleZeroScanResult result = scanHandLocatorScaleZero(animationFile);
            if (result.unsafe()) {
                return SpecialHandLocatorProfile.NONE;
            }
            if (result.hasScaleZero()) {
                handLocatorZero = true;
            }
        }
        if (!handLocatorZero) {
            return SpecialHandLocatorProfile.NONE;
        }
        return SpecialHandLocatorProfile.HAND_LOCATOR_HIDDEN_BY_CARRYON;
    }

    private static ScaleZeroScanResult scanHandLocatorScaleZero(RawYsmModel.RawAnimationFile animationFile) {
        if (animationFile == null || animationFile.animations == null) {
            return ScaleZeroScanResult.NONE;
        }
        boolean found = false;
        for (RawYsmModel.RawAnimation animation : animationFile.animations.values()) {
            if (animation == null || animation.boneAnimations == null) {
                continue;
            }
            for (RawYsmModel.RawBoneAnimation boneAnimation : animation.boneAnimations) {
                if (boneAnimation == null || !isPrimaryHandLocatorName(boneAnimation.boneName) || boneAnimation.scale == null || boneAnimation.scale.isEmpty()) {
                    continue;
                }
                for (RawYsmModel.RawKeyframe keyframe : boneAnimation.scale) {
                    KeyframeScaleResult result = isZeroScaleKeyframe(keyframe);
                    if (result == KeyframeScaleResult.UNSAFE) {
                        return ScaleZeroScanResult.UNSAFE;
                    }
                    if (result == KeyframeScaleResult.ZERO) {
                        found = true;
                    }
                }
            }
        }
        return found ? ScaleZeroScanResult.FOUND : ScaleZeroScanResult.NONE;
    }

    private static KeyframeScaleResult isZeroScaleKeyframe(RawYsmModel.RawKeyframe keyframe) {
        if (keyframe == null) {
            return KeyframeScaleResult.NONE;
        }
        KeyframeScaleResult post = isZeroScaleData(keyframe.postData);
        if (post == KeyframeScaleResult.UNSAFE) {
            return KeyframeScaleResult.UNSAFE;
        }
        if (!keyframe.hasPreData) {
            return post;
        }
        KeyframeScaleResult pre = isZeroScaleData(keyframe.preData);
        if (pre == KeyframeScaleResult.UNSAFE) {
            return KeyframeScaleResult.UNSAFE;
        }
        return pre == KeyframeScaleResult.ZERO || post == KeyframeScaleResult.ZERO ? KeyframeScaleResult.ZERO : KeyframeScaleResult.NONE;
    }

    private static KeyframeScaleResult isZeroScaleData(Object[] data) {
        if (data == null || data.length < 3) {
            return KeyframeScaleResult.NONE;
        }
        for (int i = 0; i < 3; i++) {
            if (!isSafeZeroValue(data[i])) {
                return data[i] instanceof String ? KeyframeScaleResult.UNSAFE : KeyframeScaleResult.NONE;
            }
        }
        return KeyframeScaleResult.ZERO;
    }

    private static boolean isSafeZeroValue(Object value) {
        if (value instanceof Float) {
            return (Float) value == 0.0f;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() == 0.0d;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return false;
            }
            try {
                return Double.parseDouble(text) == 0.0d;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean isPrimaryHandLocatorName(String boneName) {
        String normalized = GeometryBaker.normalizeBoneName(boneName);
        return "lefthandlocator".equals(normalized) || "righthandlocator".equals(normalized);
    }

    private enum ScaleZeroScanResult {
        NONE(false, false),
        FOUND(true, false),
        UNSAFE(false, true);

        private final boolean hasScaleZero;
        private final boolean unsafe;

        ScaleZeroScanResult(boolean hasScaleZero, boolean unsafe) {
            this.hasScaleZero = hasScaleZero;
            this.unsafe = unsafe;
        }

        private boolean hasScaleZero() {
            return this.hasScaleZero;
        }

        private boolean unsafe() {
            return this.unsafe;
        }
    }

    private enum KeyframeScaleResult {
        NONE,
        ZERO,
        UNSAFE
    }

    // ---- 附加资源 / 子实体 ----

    private static ModelExtraResourcesFile buildExtraResources(RawYsmModel raw) {
        Map<String, AudioTrackData> sounds = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawDataFile> entry : raw.soundFiles.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue().data;
            AudioTrackData track = AudioResourceMapper.parseAudioTrackData(data);
            if (track != null) sounds.put(name, track);
        }

        Map<String, IValue> functions = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawDataFile> entry : raw.functionFiles.entrySet()) {
            String name = entry.getKey();
            byte[] data = entry.getValue().data;
            String molangScript = new String(data, StandardCharsets.UTF_8);
            try {
                functions.put(name, GeckoLibCache.getMolangParser().parseExpression(molangScript, true));
            } catch (Exception e) {
            }
        }

        Map<String, Map<String, String>> translations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawLanguageFile> entry : raw.languageFiles.entrySet()) {
            translations.put(entry.getKey(), entry.getValue().data);
        }

        return new ModelExtraResourcesFile(sounds, functions, translations);
    }

    private static ProjectileModelFiles[] buildExtraItemModels(RawYsmModel raw, GeometryDescription context, boolean mergeMultilineExpr) {
        List<ProjectileModelFiles> list = new ArrayList<>();
        for (Map.Entry<String, RawYsmModel.RawSubEntity> entry : raw.projectiles.entrySet()) {
            RawYsmModel.RawSubEntity sub = entry.getValue();
            ProjectileModelFiles holder = buildSubEntityHolder(sub, context, 1, mergeMultilineExpr);
            list.add(holder);
        }
        return list.toArray(new ProjectileModelFiles[0]);
    }

    private static VehicleModelFiles[] buildExtraEntityModels(RawYsmModel raw, GeometryDescription context, boolean mergeMultilineExpr) {
        List<VehicleModelFiles> list = new ArrayList<>();
        for (Map.Entry<String, RawYsmModel.RawSubEntity> entry : raw.vehicles.entrySet()) {
            RawYsmModel.RawSubEntity sub = entry.getValue();
            VehicleModelFiles wrapper = buildSubEntityWrapper(sub, context, 1, mergeMultilineExpr);
            list.add(wrapper);
        }
        return list.toArray(new VehicleModelFiles[0]);
    }

    private static ProjectileModelFiles buildSubEntityHolder(RawYsmModel.RawSubEntity sub, GeometryDescription context, int textureCount, boolean mergeMultilineExpr) {
        OuterFileTexture texture = null;
        TextureAlphaAnalyzer subScanner = null;

        if (!sub.textures.isEmpty()) {
            List<BufferedImage> imgList = new ArrayList<>();
            for (RawYsmModel.RawTexture rt : sub.textures.values()) {
                BufferedImage img = TextureDecoder.decodeToImage(rt.data, rt.imageFormat, rt.width, rt.height);
                imgList.add(img);
                byte[] processedData = TextureDecoder.toPng(rt.data, rt.imageFormat, rt.width, rt.height);
                if (texture == null) {
                    texture = new OuterFileTexture(processedData, sub.identifier);
                }
            }
            if (sub.model != null) {
                subScanner = new TextureAlphaAnalyzer(imgList.toArray(new BufferedImage[0]), textureCount);
            }
        }

        GeoModel mesh = GeometryBaker.buildMesh(sub.model, context, textureCount, subScanner, true);

        Map<String, Animation> allAnimations = new LinkedHashMap<>();
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> entry : sub.animationFiles.entrySet()) {
            Map<String, Animation> fileAnims = AnimationMapper.buildAnimations(entry.getValue(), mergeMultilineExpr);
            allAnimations.putAll(fileAnims);
        }
        AnimationFile combinedAnim = new AnimationFile(allAnimations);

        Map<String, AnimationController> controllerMap = new LinkedHashMap<>();
        if (sub.animationControllerFiles != null) {
            for (RawYsmModel.RawAnimationControllerFile file : sub.animationControllerFiles) {
                if (file.controllers != null && !file.controllers.isEmpty()) {
                    controllerMap.putAll(AnimationMapper.buildControllers(file.controllers, mergeMultilineExpr));
                }
            }
        }
        AnimationControllerFile controllers = new AnimationControllerFile(controllerMap);

        String[] matchIds = sub.matchIds != null ? sub.matchIds : new String[]{sub.identifier};
        return new ProjectileModelFiles(matchIds, mesh, combinedAnim, controllers, texture);
    }

    private static VehicleModelFiles buildSubEntityWrapper(RawYsmModel.RawSubEntity sub, GeometryDescription context, int textureCount, boolean mergeMultilineExpr) {
        OuterFileTexture texture = null;
        TextureAlphaAnalyzer subScanner = null;

        if (!sub.textures.isEmpty()) {
            List<BufferedImage> imgList = new ArrayList<>();
            for (RawYsmModel.RawTexture rt : sub.textures.values()) {
                BufferedImage img = TextureDecoder.decodeToImage(rt.data, rt.imageFormat, rt.width, rt.height);
                imgList.add(img);
                byte[] processedData = TextureDecoder.toPng(rt.data, rt.imageFormat, rt.width, rt.height);
                if (texture == null) {
                    texture = new OuterFileTexture(processedData, sub.identifier);
                }
            }
            if (sub.model != null) {
                subScanner = new TextureAlphaAnalyzer(imgList.toArray(new BufferedImage[0]), textureCount);
            }
        }

        GeoModel mesh = GeometryBaker.buildMesh(sub.model, context, textureCount, subScanner, true);

        Map<String, Animation> allAnimations = new LinkedHashMap<>();
        for (RawYsmModel.RawAnimationFile animFile : sub.animationFiles.values()) {
            Map<String, Animation> fileAnims = AnimationMapper.buildAnimations(animFile, mergeMultilineExpr);
            allAnimations.putAll(fileAnims);
        }
        AnimationFile combinedAnim = new AnimationFile(allAnimations);

        Map<String, AnimationController> controllerMap = new LinkedHashMap<>();
        if (sub.animationControllerFiles != null) {
            for (RawYsmModel.RawAnimationControllerFile file : sub.animationControllerFiles) {
                if (file.controllers != null && !file.controllers.isEmpty()) {
                    controllerMap.putAll(AnimationMapper.buildControllers(file.controllers, mergeMultilineExpr));
                }
            }
        }
        AnimationControllerFile controllers = new AnimationControllerFile(controllerMap);

        String[] matchIds = sub.matchIds != null ? sub.matchIds : new String[]{sub.identifier};
        return new VehicleModelFiles(matchIds, mesh, combinedAnim, controllers, texture);
    }

    private static Map<String, OuterFileTexture> buildExtraTextures(RawYsmModel raw) {
        Map<String, OuterFileTexture> result = new LinkedHashMap<>();
        for (RawYsmModel.RawImage img : raw.properties.backgroundImages) {
            if (img.name != null && !img.name.isEmpty()) {
                byte[] processedData = TextureDecoder.toPng(img.data, img.format, img.width, img.height);
                result.put(img.name, new OuterFileTexture(processedData, img.name));
            }
        }
        return result;
    }

    private static String uniqueTextureName(Map<String, OuterFileTexture> textures, String requestedName, String stableId) {
        String base = requestedName == null || requestedName.isBlank() ? "texture" : requestedName;
        if (!textures.containsKey(base)) return base;
        String suffix = stableId == null || stableId.isBlank() ? "duplicate" : stableId.replaceAll("[^a-zA-Z0-9._-]", "_");
        String candidate = base + "@" + suffix;
        int index = 2;
        while (textures.containsKey(candidate)) candidate = base + "@" + suffix + "-" + index++;
        return candidate;
    }
}
