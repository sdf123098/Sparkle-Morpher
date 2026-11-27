package com.micaftic.morpher.client.model;

import com.micaftic.morpher.client.animation.BedrockAnimationMapping;
import com.micaftic.morpher.client.animation.RidingAnimationPlan;
import com.micaftic.morpher.client.animation.condition.ArmorConditions;
import com.micaftic.morpher.client.animation.condition.ConditionManager;
import com.micaftic.morpher.client.animation.controller.FirstPersonArmAnimationController;
import com.micaftic.morpher.client.animation.controller.UnifiedPlayerActionController;
import com.micaftic.morpher.client.compat.touhoulittlemaid.TouhouLittleMaidClientCompat;
import com.micaftic.morpher.client.core.HostAnimationAssets;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.entity.SpmPlayerEntity;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBone;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.data.OrderedStringMap;
import dev.spm.core.scene.PoseAnimationLibrary;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.function.Consumer;

public class PlayerModelBundle {

    private final BuiltModel mainModel;

    private final BuiltModel armModel;

    private final Object2ReferenceMap<String, ClipSpec> mainAnimations;

    private final Object2ReferenceMap<String, ClipSpec> armAnimations;

    private final PoseAnimationLibrary mainAnimationLibrary;

    private final PoseAnimationLibrary armAnimationLibrary;

    private final ConditionManager conditionManager;

    private final ArmorConditions modelProcessor;

    private final Object2ReferenceMap<String, ControllerSpec> animationEntries;

    private final OrderedStringMap<String, ? extends AbstractTexture> textures;

    private final String defaultTextureName;

    private final AbstractTexture defaultTexture;

    private final ModelSourceFormat sourceFormat;

    private final ModelActionProfile actionProfile;

    private final SemanticSkeleton semanticSkeleton;

    private final HandLocatorProfile handLocatorProfile;

    private final boolean fallFlyingPitchHandledByAnimation;

    private final RidingAnimationPlan ridingAnimationPlan;

    private final Consumer<CustomPlayerEntity> playerControllerInstaller;

    private final Consumer<SpmPlayerEntity> armControllerInstaller;

    private final Object maidControllerInstaller;

    public PlayerModelBundle(BuiltModel mainModel,
                             BuiltModel armModel,
                             Object2ReferenceMap<String, ClipSpec> mainAnimations,
                             Object2ReferenceMap<String, ClipSpec> armAnimations,
                             ConditionManager conditionManager,
                             ArmorConditions modelProcessor,
                             Object2ReferenceMap<String, ControllerSpec> animationEntries,
                             OrderedStringMap<String, ? extends AbstractTexture> textures,
                             String defaultTextureName, AbstractTexture defaultTexture, ModelResourceBundle modelResourceBundle,
                             ModelSourceFormat sourceFormat, ModelActionProfile actionProfile,
                             SemanticSkeleton semanticSkeleton, HandLocatorProfile handLocatorProfile) {
        this.mainModel = mainModel;
        this.armModel = armModel;
        this.mainAnimations = mainAnimations;
        this.armAnimations = armAnimations;
        this.mainAnimationLibrary = mainModel != null
                ? HostAnimationAssets.compileLibrary("ysm:main", mainAnimations,
                HostAnimationAssets.buildSkeleton(mainModel), HostAnimationAssets.buildBindPose(mainModel))
                : null;
        this.armAnimationLibrary = armModel != null
                ? HostAnimationAssets.compileLibrary("ysm:arm", armAnimations,
                HostAnimationAssets.buildSkeleton(armModel), HostAnimationAssets.buildBindPose(armModel))
                : null;
        this.conditionManager = conditionManager;
        this.animationEntries = animationEntries;
        this.modelProcessor = modelProcessor;
        this.textures = textures;
        this.defaultTextureName = defaultTextureName;
        this.defaultTexture = defaultTexture;
        this.sourceFormat = sourceFormat;
        this.actionProfile = actionProfile;
        this.semanticSkeleton = semanticSkeleton;
        this.handLocatorProfile = handLocatorProfile;
        this.fallFlyingPitchHandledByAnimation = detectsFallFlyingRootRotation(mainModel, mainAnimations);
        this.ridingAnimationPlan = RidingAnimationPlan.analyze(action -> {
            ClipSpec animation = mainAnimations.get(action);
            return animation != null ? animation
                    : BedrockAnimationMapping.findByAction(mainAnimations, action);
        });
        this.playerControllerInstaller = UnifiedPlayerActionController.buildControllers(this, modelResourceBundle);
        this.armControllerInstaller = FirstPersonArmAnimationController.buildControllers(this, modelResourceBundle);
        this.maidControllerInstaller = actionProfile == ModelActionProfile.VANILLA_HUMANOID ? null : TouhouLittleMaidClientCompat.buildControllers(this, modelResourceBundle);
    }

    public BuiltModel getMainModel() {
        return this.mainModel;
    }

    public BuiltModel getArmModel() {
        return this.armModel;
    }

    public Object2ReferenceMap<String, ClipSpec> getMainAnimations() {
        return this.mainAnimations;
    }

    public Object2ReferenceMap<String, ClipSpec> getArmAnimations() {
        return this.armAnimations;
    }

    public PoseAnimationLibrary getMainAnimationLibrary() {
        return this.mainAnimationLibrary;
    }

    public PoseAnimationLibrary getArmAnimationLibrary() {
        return this.armAnimationLibrary;
    }

    public ConditionManager getConditionManager() {
        return this.conditionManager;
    }

    public ArmorConditions getModelProcessor() {
        return this.modelProcessor;
    }

    public Object2ReferenceMap<String, ControllerSpec> getAnimationEntries() {
        return this.animationEntries;
    }

    public OrderedStringMap<String, ? extends AbstractTexture> getTextures() {
        return this.textures;
    }

    public String getDefaultTextureName() {
        return this.defaultTextureName;
    }

    public AbstractTexture getDefaultTexture() {
        return this.defaultTexture;
    }

    public boolean isImportedPlayerModel() {
        return this.sourceFormat == ModelSourceFormat.BBMODEL || this.sourceFormat == ModelSourceFormat.FIGURA_BBMODEL;
    }

    public boolean hasVanillaPoseFallback() {
        return this.actionProfile == ModelActionProfile.HYBRID_AUTHORED_WITH_VANILLA_FALLBACK
                || this.actionProfile == ModelActionProfile.VANILLA_HUMANOID;
    }

    public ModelSourceFormat getSourceFormat() {
        return this.sourceFormat;
    }

    public ModelActionProfile getActionProfile() {
        return this.actionProfile;
    }

    public SemanticSkeleton getSemanticSkeleton() {
        return this.semanticSkeleton;
    }

    public HandLocatorProfile getHandLocatorProfile() {
        return this.handLocatorProfile;
    }

    public boolean isFallFlyingPitchHandledByAnimation() {
        return this.fallFlyingPitchHandledByAnimation;
    }

    public RidingAnimationPlan getRidingAnimationPlan() {
        return this.ridingAnimationPlan;
    }

    private static boolean detectsFallFlyingRootRotation(BuiltModel model, Object2ReferenceMap<String, ClipSpec> animations) {
        if (model == null || animations == null) {
            return false;
        }
        ClipSpec animation = animations.get("elytra_fly");
        if (animation == null || !animation.hasKeyframes() || animation.raw().boneAnimations == null) {
            return false;
        }
        for (RawYsmModel.RawBoneAnimation boneAnimation : animation.raw().boneAnimations) {
            if (boneAnimation.rotation == null || boneAnimation.rotation.isEmpty()) {
                continue;
            }
            for (ModelBone bone : model.bones) {
                if (bone.getName().equals(boneAnimation.boneName)
                        && (bone.parentIdx < 0 || bone.parentName == null || bone.parentName.isEmpty())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Consumer<CustomPlayerEntity> getPlayerControllerInstaller() {
        return this.playerControllerInstaller;
    }

    public Consumer<SpmPlayerEntity> getArmControllerInstaller() {
        return this.armControllerInstaller;
    }

    public Object getMaidControllerInstaller() {
        return this.maidControllerInstaller;
    }
}
