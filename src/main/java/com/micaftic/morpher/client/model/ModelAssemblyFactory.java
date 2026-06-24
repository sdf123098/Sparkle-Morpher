package com.micaftic.morpher.client.model;

import com.micaftic.morpher.client.ClientModelInfo;
import com.micaftic.morpher.client.animation.condition.ArmorConditions;
import com.micaftic.morpher.client.animation.condition.ConditionManager;
import com.micaftic.morpher.client.gui.metadata.ModelDisplayAssets;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import com.micaftic.morpher.geckolib3.core.event.ParticleEventKeyFrame;
import com.micaftic.morpher.geckolib3.core.keyframe.BoneAnimation;
import com.micaftic.morpher.geckolib3.core.keyframe.event.EventKeyFrame;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.file.AnimationControllerFile;
import com.micaftic.morpher.geckolib3.file.AnimationFile;
import com.micaftic.morpher.geckolib3.file.ProjectileModelFiles;
import com.micaftic.morpher.geckolib3.file.VehicleModelFiles;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.util.FileTypeUtil;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class ModelAssemblyFactory {

    private static final String FIRST_PERSON_ARM_BONE = "fp_arm";
    private static final String BBMODEL_IMPORT_EXTRA = "sparkle_morpher:bbmodel_import";

    private static final Map<String, String> FIRST_PERSON_WEAPON_ARM_BONE_TARGETS = Map.ofEntries(
            Map.entry("LeftArm", "LeftArm"),
            Map.entry("LeftForeArm", "LeftForeArm"),
            Map.entry("LeftHand", "LeftHand"),
            Map.entry("LeftHandLocator", "LeftHandLocator"),
            Map.entry("LeftItem", "LeftHandLocator"),
            Map.entry("RightArm", "RightArm"),
            Map.entry("RightForeArm", "RightForeArm"),
            Map.entry("RightHand", "RightHand"),
            Map.entry("RightHandLocator", "RightHandLocator"),
            Map.entry("RightItem", "RightHandLocator"),
            Map.entry("leftArm", "LeftArm"),
            Map.entry("leftarm", "LeftArm"),
            Map.entry("leftForeArm", "LeftForeArm"),
            Map.entry("leftforearm", "LeftForeArm"),
            Map.entry("leftHand", "LeftHand"),
            Map.entry("lefthand", "LeftHand"),
            Map.entry("leftHandLocator", "LeftHandLocator"),
            Map.entry("lefthandlocator", "LeftHandLocator"),
            Map.entry("leftItem", "LeftHandLocator"),
            Map.entry("leftitem", "LeftHandLocator"),
            Map.entry("rightArm", "RightArm"),
            Map.entry("rightarm", "RightArm"),
            Map.entry("rightForeArm", "RightForeArm"),
            Map.entry("rightforearm", "RightForeArm"),
            Map.entry("rightHand", "RightHand"),
            Map.entry("righthand", "RightHand"),
            Map.entry("rightHandLocator", "RightHandLocator"),
            Map.entry("righthandlocator", "RightHandLocator"),
            Map.entry("rightItem", "RightHandLocator"),
            Map.entry("rightitem", "RightHandLocator")
    );

    private static final String MACE_MAINHAND_HOLD_ID = "hold_mainhand$minecraft:mace";
    private static final String MACE_OFFHAND_HOLD_ID = "hold_offhand$minecraft:mace";
    private static final String MACE_SWING_ID = "swing$minecraft:mace";
    private static final String MACE_MAINHAND_HOLD = "hold_mainhand:mace";
    private static final String MACE_OFFHAND_HOLD = "hold_offhand:mace";
    private static final String MACE_SWING = "swing:mace";
    private static final String MACE_MAINHAND_USE = "use_mainhand:mace";
    private static final String MACE_OFFHAND_USE = "use_offhand:mace";
    private static final String LANCE_MAINHAND_HOLD = "hold_mainhand:lance";
    private static final String LANCE_OFFHAND_HOLD = "hold_offhand:lance";
    private static final String LANCE_SWING = "swing:lance";
    private static final String LANCE_MAINHAND_USE = "use_mainhand:lance";
    private static final String LANCE_OFFHAND_USE = "use_offhand:lance";
    private static final String LANCE_STAND = "lance_stand";
    private static final String LANCE_JAB = "lance_jab";
    private static final String LANCE_LUNGE = "lance_lunge";
    private static final String LANCE_CHARGE = "lance_charge";
    private static final String LANCE_RIDING_IDLE = "lance_riding_idle";
    private static final String LANCE_RIDING_CHARGE = "lance_riding_charge";
    private static final String LANCE_FALL_FLYING_CHARGE = "lance_fall_flying_charge";
    private static final String SPEAR_MAINHAND_HOLD = "hold_mainhand:spear";
    private static final String SPEAR_OFFHAND_HOLD = "hold_offhand:spear";
    private static final String SPEAR_SWING = "swing:spear";
    private static final String SPEAR_MAINHAND_USE = "use_mainhand:spear";
    private static final String SPEAR_OFFHAND_USE = "use_offhand:spear";
    private static final String[] FIRST_PERSON_LANCE_ANIMATIONS = {
            LANCE_MAINHAND_HOLD,
            LANCE_OFFHAND_HOLD,
            LANCE_SWING,
            LANCE_MAINHAND_USE,
            LANCE_OFFHAND_USE,
            LANCE_STAND,
            LANCE_JAB,
            LANCE_LUNGE,
            LANCE_CHARGE,
            LANCE_RIDING_IDLE,
            LANCE_RIDING_CHARGE,
            LANCE_FALL_FLYING_CHARGE
    };

    private static ModelAssembly primaryAssembly;

    public static ModelAssembly buildAssembly(ClientModelInfo clientModelInfo, boolean isPrimary, boolean isAuth) {
        ArrayList<AbstractTexture> textureList = new ArrayList();
        ModelResourceBundle resourceBundle = buildResourceBundle(clientModelInfo);
        ModelAssembly assembly = new ModelAssembly(
                buildPlayerModelBundle(clientModelInfo, resourceBundle, isPrimary, textureList),
                buildProjectileModels(clientModelInfo, resourceBundle, isPrimary, textureList),
                buildVehicleModels(clientModelInfo, resourceBundle, isPrimary, textureList),
                resourceBundle, clientModelInfo.getInfo(),
                buildTextureRegistry(clientModelInfo, isAuth, textureList), textureList
        );
        if (isPrimary) {
            primaryAssembly = assembly;
            primaryAssembly.getAnimationBundle().getMainAnimations().values().forEach(animation -> {
                animation.isFromPrimaryAssembly = true;
            });
        }
        return assembly;
    }

    public static PlayerModelBundle buildPlayerModelBundle(ClientModelInfo clientModelInfo, ModelResourceBundle resourceBundle, boolean isPrimary, List<AbstractTexture> textureList) {
        MainModelData hierarchyData = clientModelInfo.getMainModelData();
        GeoModel mainModel = hierarchyData.getModels().get(0);
        GeoModel armModel = hierarchyData.getModels().get(1);
        Object2ReferenceLinkedOpenHashMap<String, Animation> object2ReferenceOpenHashMap = new Object2ReferenceLinkedOpenHashMap<>();
        Object2ReferenceLinkedOpenHashMap<String, Animation> armAnimations = new Object2ReferenceLinkedOpenHashMap<>();
        for (String str : hierarchyData.getAnimations().keySet()) {
            AnimationFile animationFile = hierarchyData.getAnimations().get(str);
            for (Animation animation : animationFile.getAnimations().values()) {
                if (animation.sourceKey == null) animation.sourceKey = str;
            }
            if (FIRST_PERSON_ARM_BONE.equals(str)) {
                armAnimations.putAll(animationFile.getAnimations());
            } else {
                object2ReferenceOpenHashMap.putAll(animationFile.getAnimations());
            }
        }
        boolean bbModelImport = isBbModelImport(clientModelInfo);
        boolean inheritPrimaryAnimations = !isPrimary && !bbModelImport;
        if (inheritPrimaryAnimations) {
            ObjectIterator<Map.Entry<String, Animation>> it = primaryAssembly.getAnimationBundle().getMainAnimations().entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Animation> entry = it.next();
                object2ReferenceOpenHashMap.computeIfAbsent(entry.getKey(), obj -> {
                    return entry.getValue();
                });
            }
            ObjectIterator<Map.Entry<String, Animation>> it2 = primaryAssembly.getAnimationBundle().getArmAnimations().entrySet().iterator();
            while (it2.hasNext()) {
                Map.Entry<String, Animation> entry2 = it2.next();
                armAnimations.computeIfAbsent(entry2.getKey(), obj2 -> {
                    return entry2.getValue();
                });
            }
        }
        if (!bbModelImport) {
            addWeaponAnimationAliases(object2ReferenceOpenHashMap);
            addWeaponAnimationAliases(armAnimations);
            addFirstPersonWeaponArmAnimations(object2ReferenceOpenHashMap, armAnimations);
        }
        ConditionManager conditionManager = new ConditionManager();
        ObjectSet<String> objectSetKeySet = object2ReferenceOpenHashMap.keySet();
        Objects.requireNonNull(conditionManager);
        objectSetKeySet.forEach(conditionManager::addTest);
        ArmorConditions armorRegistry = new ArmorConditions();
        ObjectSet<String> objectSetKeySet2 = armAnimations.keySet();
        Objects.requireNonNull(armorRegistry);
        objectSetKeySet2.forEach(armorRegistry::addCondition);
        Object2ReferenceOpenHashMap<String, AnimationController> animationControllers = new Object2ReferenceOpenHashMap<>();
        for (AnimationControllerFile animationControllerFile : hierarchyData.getAnimationControllers()) {
            animationControllers.putAll(animationControllerFile.getAnimationControllers());
        }
        for (OuterFileTexture texture : hierarchyData.getTextureMap().values()) {
            textureList.add(texture);
            textureList.addAll(texture.getSuffixTextures().values());
        }
        String defaultTextureName = (StringUtils.isEmpty(clientModelInfo.getInfo().getModelProperties().getDefaultTexture()) || !hierarchyData.getTextureMap().containsKey(clientModelInfo.getInfo().getModelProperties().getDefaultTexture())) ? hierarchyData.getTextureMap().getKeyAt(0) : clientModelInfo.getInfo().getModelProperties().getDefaultTexture();
        return new PlayerModelBundle(
                mainModel,
                armModel,
                object2ReferenceOpenHashMap,
                armAnimations,
                conditionManager,
                armorRegistry,
                animationControllers,
                hierarchyData.getTextureMap(),
                defaultTextureName,
                hierarchyData.getTextureMap().get(defaultTextureName),
                resourceBundle,
                bbModelImport);
    }

    private static boolean isBbModelImport(ClientModelInfo clientModelInfo) {
        return clientModelInfo != null
                && clientModelInfo.getInfo() != null
                && BBMODEL_IMPORT_EXTRA.equals(clientModelInfo.getInfo().getExtra());
    }

    private static Map<Identifier, ProjectileModelBundle> buildProjectileModels(ClientModelInfo clientModelInfo, ModelResourceBundle resourceBundle, boolean isPrimary, List<AbstractTexture> textureList) {
        Object2ReferenceOpenHashMap<Identifier, ProjectileModelBundle> projectileMap = new Object2ReferenceOpenHashMap();
        for (ProjectileModelFiles projectileFiles : clientModelInfo.getExtraItemModels()) {
            GeoModel model = projectileFiles.getModel();
            AnimationFile animationFile = projectileFiles.getAnimations();
            AnimationControllerFile controllerFile = projectileFiles.getAnimationController();
            Object2ReferenceOpenHashMap<String, Animation> animations = new Object2ReferenceOpenHashMap(animationFile != null ? animationFile.getAnimations() : Object2ReferenceMaps.emptyMap());
            Object2ReferenceMap<String, AnimationController> controllers = Object2ReferenceMaps.emptyMap();
            if (controllerFile != null) {
                controllers = new Object2ReferenceOpenHashMap(controllerFile.getAnimationControllers());
            }
            textureList.add(projectileFiles.getTexture());
            textureList.addAll(projectileFiles.getTexture().getSuffixTextures().values());
            ProjectileModelBundle projectileBundle = new ProjectileModelBundle(model, animations, controllers, projectileFiles.getTexture(), resourceBundle);
            Iterator<Identifier> typeIterator = FileTypeUtil.resolveEntityTypes(projectileFiles.getTextureNames()).iterator();
            while (typeIterator.hasNext()) {
                projectileMap.put(typeIterator.next(), projectileBundle);
            }
        }
        return projectileMap;
    }

    private static Map<Identifier, VehicleModelBundle> buildVehicleModels(ClientModelInfo clientModelInfo, ModelResourceBundle resourceBundle, boolean isPrimary, List<AbstractTexture> textureList) {
        Object2ReferenceOpenHashMap<Identifier, VehicleModelBundle> vehicleMap = new Object2ReferenceOpenHashMap<>();
        for (VehicleModelFiles vehicleFiles : clientModelInfo.getVehicleModelFiles()) {
            GeoModel model = vehicleFiles.getModel();
            AnimationFile animationFile = vehicleFiles.getAnimations();
            AnimationControllerFile controllerFile = vehicleFiles.getAnimationController();
            Object2ReferenceOpenHashMap<String, Animation> animations = new Object2ReferenceOpenHashMap<>(animationFile != null ? animationFile.getAnimations() : Object2ReferenceMaps.emptyMap());
            Object2ReferenceMap<String, AnimationController> controllers = Object2ReferenceMaps.emptyMap();
            if (controllerFile != null) {
                controllers = new Object2ReferenceOpenHashMap<>(controllerFile.getAnimationControllers());
            }
            textureList.add(vehicleFiles.getTexture());
            textureList.addAll(vehicleFiles.getTexture().getSuffixTextures().values());
            VehicleModelBundle vehicleBundle = new VehicleModelBundle(model, animations, controllers, vehicleFiles.getTexture(), resourceBundle);
            for (Identifier resourceLocation : FileTypeUtil.resolveEntityTypes(vehicleFiles.getTextureNames())) {
                vehicleMap.put(resourceLocation, vehicleBundle);
            }
        }
        return vehicleMap;
    }

    private static ModelResourceBundle buildResourceBundle(ClientModelInfo clientModelInfo) {
        return new ModelResourceBundle(clientModelInfo.getExtraResources().getAudioTracks(), buildMolangFunctions(clientModelInfo), extractMolangEvents(clientModelInfo), clientModelInfo.getExtraResources().getTranslations());
    }

    private static ModelDisplayAssets buildTextureRegistry(ClientModelInfo clientModelInfo, boolean isAuth, List<AbstractTexture> textureList) {
        Map<String, AbstractTexture> extraTextures = extractExtraTextures(clientModelInfo, textureList);
        Metadata metadata = clientModelInfo.getInfo().getExtraInfo();
        return new ModelDisplayAssets(metadata != null ? metadata.getName() : StringPool.EMPTY, isAuth, clientModelInfo.getAvatarTextures(), extraTextures);
    }

    private static Object2ReferenceOpenHashMap<String, IValue> buildMolangFunctions(ClientModelInfo clientModelInfo) {
        Object2ReferenceOpenHashMap<String, IValue> functions = new Object2ReferenceOpenHashMap<>(clientModelInfo.getExtraResources().getFunctions().size());
        for (Map.Entry<String, IValue> entry : clientModelInfo.getExtraResources().getFunctions().entrySet()) {
            String key = entry.getKey();
            int atIndex = key.indexOf('@');
            if (atIndex != 0) {
                if (atIndex != -1) {
                    key = key.substring(0, atIndex);
                }
                functions.put(key, entry.getValue());
            }
        }
        return functions;
    }

    private static Object2ReferenceOpenHashMap<String, List<IValue>> extractMolangEvents(ClientModelInfo clientModelInfo) {
        Object2ReferenceOpenHashMap<String, List<IValue>> events = new Object2ReferenceOpenHashMap<>();
        for (Map.Entry<String, IValue> entry : clientModelInfo.getExtraResources().getFunctions().entrySet()) {
            int atIndex = entry.getKey().indexOf('@');
            if (atIndex != -1 && atIndex + 1 < entry.getKey().length()) {
                events.computeIfAbsent(entry.getKey().substring(atIndex + 1).toLowerCase(), obj -> {
                    return new ReferenceArrayList();
                }).add(entry.getValue());
            }
        }
        return events;
    }

    public static Map<String, AbstractTexture> extractExtraTextures(ClientModelInfo clientModelInfo, List<AbstractTexture> textureList) {
        Object2ObjectOpenHashMap<String, AbstractTexture> extraTextures = new Object2ObjectOpenHashMap();
        if (clientModelInfo.getInfo().getModelProperties() != null) {
            for (Map.Entry<String, OuterFileTexture> entry : clientModelInfo.getGuiTextures().entrySet()) {
                OuterFileTexture texture = entry.getValue();
                if (texture != null) {
                    textureList.add(texture);
                    extraTextures.put(entry.getKey(), texture);
                }
            }
        }
        return Object2ObjectMaps.unmodifiable(extraTextures);
    }

    private static void addWeaponAnimationAliases(Object2ReferenceMap<String, Animation> animations) {
        aliasAnimation(animations, LANCE_MAINHAND_HOLD, SPEAR_MAINHAND_HOLD);
        aliasAnimation(animations, LANCE_OFFHAND_HOLD, SPEAR_OFFHAND_HOLD);
        aliasAnimation(animations, LANCE_SWING, SPEAR_SWING);
        aliasAnimation(animations, LANCE_MAINHAND_USE, SPEAR_MAINHAND_USE);
        aliasAnimation(animations, LANCE_OFFHAND_USE, SPEAR_OFFHAND_USE);
        aliasAnimation(animations, LANCE_STAND, LANCE_MAINHAND_HOLD);
        aliasAnimation(animations, LANCE_JAB, LANCE_SWING);
        aliasAnimation(animations, LANCE_LUNGE, LANCE_SWING);
        aliasAnimation(animations, LANCE_CHARGE, LANCE_MAINHAND_USE);
        aliasAnimation(animations, LANCE_RIDING_IDLE, LANCE_MAINHAND_HOLD);
        aliasAnimation(animations, LANCE_RIDING_CHARGE, LANCE_MAINHAND_USE);
        aliasAnimation(animations, LANCE_FALL_FLYING_CHARGE, LANCE_MAINHAND_USE);
        aliasAnimation(animations, MACE_MAINHAND_HOLD, MACE_MAINHAND_HOLD_ID);
        aliasAnimation(animations, MACE_OFFHAND_HOLD, MACE_OFFHAND_HOLD_ID);
        aliasAnimation(animations, MACE_SWING, MACE_SWING_ID);
        aliasAnimation(animations, MACE_MAINHAND_USE, MACE_MAINHAND_HOLD);
        aliasAnimation(animations, MACE_OFFHAND_USE, MACE_OFFHAND_HOLD);
    }

    private static void aliasAnimation(Object2ReferenceMap<String, Animation> animations, String alias, String source) {
        Animation animation = animations.get(source);
        if (animation != null) {
            animations.putIfAbsent(alias, animation);
        }
    }

    private static void addFirstPersonWeaponArmAnimations(Object2ReferenceMap<String, Animation> sourceAnimations, Object2ReferenceMap<String, Animation> armAnimations) {
        for (String animationName : FIRST_PERSON_LANCE_ANIMATIONS) {
            addFirstPersonArmAnimation(sourceAnimations, armAnimations, animationName);
        }
    }

    private static void addFirstPersonArmAnimation(Object2ReferenceMap<String, Animation> sourceAnimations, Object2ReferenceMap<String, Animation> armAnimations, String animationName) {
        Animation existing = armAnimations.get(animationName);
        if (existing != null && !existing.isEmpty()) {
            return;
        }
        Animation source = sourceAnimations.get(animationName);
        Animation derived = deriveFirstPersonArmAnimation(animationName, source);
        if (derived != null) {
            armAnimations.put(animationName, derived);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Animation deriveFirstPersonArmAnimation(String animationName, Animation source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        LinkedHashMap<String, BoneAnimation> armBones = new LinkedHashMap<>();
        for (BoneAnimation boneAnimation : source.boneAnimations) {
            String targetBoneName = FIRST_PERSON_WEAPON_ARM_BONE_TARGETS.get(boneAnimation.boneName);
            if (targetBoneName != null) {
                BoneAnimation targetAnimation = targetBoneName.equals(boneAnimation.boneName)
                        ? boneAnimation
                        : new BoneAnimation(targetBoneName, boneAnimation.rotationKeyFrames, boneAnimation.positionKeyFrames, boneAnimation.scaleKeyFrames);
                if (targetBoneName.equals(boneAnimation.boneName)) {
                    armBones.put(targetBoneName, targetAnimation);
                } else {
                    armBones.putIfAbsent(targetBoneName, targetAnimation);
                }
            }
        }
        if (armBones.isEmpty()) {
            return null;
        }
        Animation derived = new Animation(
                animationName,
                source.animationLength,
                source.loop,
                source.unKnowData1,
                source.unKnowData2,
                source.blendWeight,
                source.override,
                armBones.values().toArray(new BoneAnimation[0]),
                new EventKeyFrame[0],
                new ParticleEventKeyFrame[0],
                new EventKeyFrame[0]
        );
        derived.sourceKey = source.sourceKey;
        derived.isFromPrimaryAssembly = source.isFromPrimaryAssembly;
        return derived;
    }
}
