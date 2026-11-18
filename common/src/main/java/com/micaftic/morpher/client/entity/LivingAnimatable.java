package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.animation.BedrockAnimationMapping;
import com.micaftic.morpher.client.animation.condition.ConditionManager;
import com.micaftic.morpher.client.animation.molang.MolangEventDispatcher;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.core.HostEntityModelData;
import com.micaftic.morpher.client.core.HostMolangScript;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.client.model.ProjectileModelBundle;
import com.micaftic.morpher.client.model.VehicleModelBundle;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.render.ModelBoneView;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.core.bridge.FiguraRuntimeBridge;
import com.micaftic.morpher.resource.gltf.GltfAnimationController;
import com.micaftic.morpher.util.data.OrderedStringMap;
import dev.spm.core.scene.PoseAnimationLibrary;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.booleans.BooleanList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

public abstract class LivingAnimatable<T extends LivingEntity> extends SpmEntity<T> {

    public String currentTextureName;

    private int textureIndex;

    private final Vector2f armorBoneOffset;

    private boolean needsInit;

    private HostMolangScript playerUpdateIValue;

    private final BooleanList updateExpressionArgs;

    private boolean forceDisabled;

    private boolean extraRenderFlag;

    private GltfAnimationController gltfAnimationController;
    private @Nullable FiguraRuntimeBridge figuraBridge;

    public LivingAnimatable(T t, boolean isActive) {
        super(t, isActive);
        this.armorBoneOffset = new Vector2f();
        this.needsInit = false;
        this.playerUpdateIValue = null;
        this.updateExpressionArgs = new BooleanArrayList(1);
        this.forceDisabled = false;
        this.extraRenderFlag = false;
        this.gltfAnimationController = null;
        this.updateExpressionArgs.size(1);
    }

    @Override
    public void applyHeadTracking(HostActionEvent<? extends HostAnimatable<T>> event, boolean wasAnimEvaluated) {
        ModelScenePose model = getCurrentModel();
        if (model != null && !model.headBones().isEmpty()) {
            ModelBoneView bone = model.headBones().get(model.headBones().size() - 1);
            if (wasAnimEvaluated) {
                this.armorBoneOffset.set(bone.getRotationX(), bone.getRotationY());
            }
            applyHeadTracking(event, this.armorBoneOffset.x, this.armorBoneOffset.y);
        }
    }

    @Override
    public void applyHudHeadTracking(HostActionEvent<? extends HostAnimatable<T>> event, float baseRotationX, float baseRotationY) {
        applyHeadTracking(event, baseRotationX, baseRotationY);
    }

    private void applyHeadTracking(HostActionEvent<?> event, float baseRotationX, float baseRotationY) {
        ModelScenePose model = getCurrentModel();
        if (model != null && !model.headBones().isEmpty()) {
            ModelBoneView bone = model.headBones().get(model.headBones().size() - 1);
            HostEntityModelData data = event.getModelData();
            bone.setRotationX(baseRotationX + ((float) Math.toRadians(data.headPitch)));
            bone.setRotationY(baseRotationY + ((float) Math.toRadians(data.netHeadYaw)));
        }
    }

    public float getHeadBaseRotationX() {
        return this.armorBoneOffset.x;
    }

    public float getHeadBaseRotationY() {
        return this.armorBoneOffset.y;
    }

    @Override
    public void resetHeadTracking(boolean wasAnimEvaluated) {
        ModelScenePose model = getCurrentModel();
        if (model != null && !model.headBones().isEmpty()) {
            ModelBoneView bone = model.headBones().get(model.headBones().size() - 1);
            bone.setRotationX(this.armorBoneOffset.x);
            bone.setRotationY(this.armorBoneOffset.y);
        }
    }

    @Override
    public LivingEntityFrameState<T> createPositionTracker(T t) {
        return new LivingEntityFrameState<>(t);
    }

    @Override
    public LivingEntityFrameState<T> getPositionTracker() {
        return (LivingEntityFrameState<T>) super.getPositionTracker();
    }

    public void setCurrentTexture(String str) {
        this.currentTextureName = str;
        updateCurrentTexture();
    }

    public void initModelWithTexture(String str, String str2) {
        markModelInitialized();
        this.currentTextureName = str2;
        setModelId(str);
        updateCurrentTexture();
    }

    public void setForceDisabled(boolean forceDisabled) {
        this.forceDisabled = forceDisabled;
    }

    public boolean isForceDisabled() {
        return this.forceDisabled;
    }

    public boolean isModelActive() {
        return isModelInitialized() && !this.forceDisabled;
    }

    @Override
    public void onModelLoaded(ModelAssembly context) {
        super.onModelLoaded(context);
        closeFiguraBridge();
        if (context.isGltf()) {
            this.playerUpdateIValue = null;
            this.gltfAnimationController = new GltfAnimationController(context.getGltfModel());
            updateCurrentTexture();
            return;
        }
        this.gltfAnimationController = null;
        if (context.getModelData() != null && context.getModelData().getFiguraArchiveBytes() != null) {
            this.figuraBridge = FiguraRuntimeBridge.create(
                    context.getModelData().getFiguraArchiveBytes(),
                    context.getModelData().getFiguraSourceName(),
                    getEntity().getName().getString());
        }
        updateCurrentTexture();
        List<HostMolangScript> values = context.getExpressionCache().getEvents().get(MolangEventDispatcher.PLAYER_UPDATE);
        if (values != null) {
            this.playerUpdateIValue = MolangEventDispatcher.createUpdateExpression(values, this.updateExpressionArgs);
        } else {
            this.playerUpdateIValue = null;
        }
    }

    @Override
    public void setCurrentModel(ModelScenePose model) {
        super.setCurrentModel(model);
        if (model != null && !model.headBones().isEmpty()) {
            ModelBoneView bone = model.headBones().get(model.headBones().size() - 1);
            this.armorBoneOffset.set(bone.getRotationX(), bone.getRotationY());
        }
    }

    @Override
    public void resetModel() {
        super.resetModel();
        closeFiguraBridge();
        this.currentTextureName = null;
        this.textureIndex = 0;
        this.forceDisabled = false;
        this.gltfAnimationController = null;
    }

    @Override
    public void tickModel() {
        super.tickModel();
        if (this.figuraBridge != null && !this.forceDisabled) {
            Minecraft mc = Minecraft.getInstance();
            this.figuraBridge.tick(getEntity(), mc != null && getEntity() == mc.player);
        }
    }

    public void applyFiguraFrame(float partialTick, @Nullable ModelScenePose model) {
        if (this.figuraBridge != null && !this.forceDisabled) {
            Minecraft mc = Minecraft.getInstance();
            this.figuraBridge.renderAndApply(getEntity(), mc != null && getEntity() == mc.player, partialTick, model);
        }
    }

    private void closeFiguraBridge() {
        if (this.figuraBridge != null) {
            this.figuraBridge.close();
            this.figuraBridge = null;
        }
    }

    @Override
    public void reset() {
        super.reset();
        this.armorBoneOffset.set(0.0f);
        this.extraRenderFlag = false;
        this.needsInit = true;
    }

    @Override
    public void setupAnim(float seekTime, boolean isFirstPerson) {
        super.setupAnim(seekTime, isFirstPerson);
        if (this.needsInit) {
            this.needsInit = false;
            List<HostMolangScript> values = getAnimationExpressions(MolangEventDispatcher.PLAYER_INIT);
            if (values != null) {
                executeExpression(MolangEventDispatcher.createInitExpression(values), true, true, null);
            }
        }
        if (this.playerUpdateIValue != null) {
            this.updateExpressionArgs.set(0, isFirstPerson);
            executeExpression(this.playerUpdateIValue, true, true, null);
        }
    }

    public ConditionManager getModelConfig() {
        return getModelAssembly().getAnimationBundle().getConditionManager();
    }

    @Nullable
    public GltfAnimationController getGltfAnimationController() {
        if (this.gltfAnimationController == null) {
            ModelAssembly assembly = getModelAssembly();
            if (assembly != null && assembly.isGltf() && assembly.getGltfModel() != null) {
                this.gltfAnimationController = new GltfAnimationController(assembly.getGltfModel());
            }
        }
        return this.gltfAnimationController;
    }

    private void updateCurrentTexture() {
        if (isModelReady()) {
            if (getModelAssembly().isGltf()) {
                return;
            }
            OrderedStringMap<String, ? extends AbstractTexture> map = getModelAssembly().getAnimationBundle().getTextures();
            if (map.isEmpty()) {
                return;
            }
            AbstractTexture abstractTexture = map.get(this.currentTextureName);
            if (abstractTexture != null) {
                ((LivingAnimatable<?>.TexturedModelWrapper) getRenderShape()).setTexture(abstractTexture);
                this.textureIndex = map.getValuesList().indexOf(abstractTexture);
            } else {
                this.currentTextureName = map.getKeyAt(0);
                ((LivingAnimatable<?>.TexturedModelWrapper) getRenderShape()).setTexture(map.getValueAt(0));
                this.textureIndex = 0;
            }
        }
    }

    @Override
    public BuiltModel getAnimationProcessor() {
        return getModelAssembly().getAnimationBundle().getMainModel();
    }

    @Override
    @Nullable
    public ClipSpec getAnimation(String str) {
        ClipSpec animation = getModelAssembly().getAnimationBundle().getMainAnimations().get(str);
        if (animation == null) {
            animation = BedrockAnimationMapping.findByAction(getModelAssembly().getAnimationBundle().getMainAnimations(), str);
        }
        return animation;
    }

    @Nullable
    public ControllerSpec getAnimationEntries(String str) {
        return getModelAssembly().getAnimationBundle().getAnimationEntries().get(str);
    }

    @Override
    public @Nullable ControllerSpec getControllerSpec(String name) {
        return getAnimationEntries(name);
    }

    @Override
    protected @Nullable PoseAnimationLibrary resolveAnimationLibrary() {
        ModelAssembly assembly = getModelAssembly();
        if (assembly == null || assembly.getAnimationBundle() == null) {
            return null;
        }
        return assembly.getAnimationBundle().getMainAnimationLibrary();
    }

    public String getCurrentTextureName() {
        if (isModelReady() && this.currentTextureName != null) {
            return this.currentTextureName;
        }
        ModelAssembly modelAssembly = getModelAssembly();
        if (modelAssembly != null && modelAssembly.getAnimationBundle() != null && !modelAssembly.getAnimationBundle().getTextures().isEmpty()) {
            return modelAssembly.getAnimationBundle().getTextures().getKeyAt(0);
        }
        return "";
    }

    @Override
    @NotNull
    public Identifier getTextureLocation() {
        if (isModelReady()) {
            IResourceLocatable texture = ((LivingAnimatable<?>.TexturedModelWrapper) getRenderShape()).currentTexture;
            Identifier location = texture == null ? null : texture.getResourceLocationOrNull();
            if (location != null) {
                return location;
            }
        }
        return ClientModelManager.getDefaultTexture();
    }

    @Override
    public int getTextureIndex() {
        if (isModelReady()) {
            return this.textureIndex;
        }
        return 0;
    }

    @Override
    public float getWidthScale() {
        return getModelAssembly().getModelData().getModelProperties().getWidthScale();
    }

    @Override
    public float getHeightScale() {
        return getModelAssembly().getModelData().getModelProperties().getHeightScale();
    }

    public boolean isRenderLayersFirst() {
        return getModelAssembly().getModelData().getModelProperties().isRenderLayersFirst();
    }

    public boolean isExtraRenderFlag() {
        return this.extraRenderFlag;
    }

    public void setExtraRenderFlag(boolean extraRenderFlag) {
        this.extraRenderFlag = extraRenderFlag;
    }

    public class TexturedModelWrapper extends ModelWrapper {

        public IResourceLocatable currentTexture;

        private final List<IResourceLocatable> allTextures;

        private final int textureResolution;

        public TexturedModelWrapper(ModelAssembly modelAssembly, boolean isActive, boolean collectAllTextures, boolean registerImmediately, int textureResolution) {
            super(modelAssembly, isActive);
            this.textureResolution = textureResolution;
            if (modelAssembly.isGltf()) {
                AbstractTexture firstTexture = modelAssembly.getTextures().isEmpty() ? null : modelAssembly.getTextures().get(0);
                this.currentTexture = firstTexture == null ? null
                        : UploadManager.getOrCreateLocatableWithSize(firstTexture, registerImmediately, textureResolution);
                this.allTextures = null;
                return;
            }
            PlayerModelBundle animationBundle = modelAssembly.getAnimationBundle();
            if (animationBundle == null) {
                this.currentTexture = null;
                this.allTextures = null;
                return;
            }
            AbstractTexture abstractTexture = animationBundle.getTextures().get(LivingAnimatable.this.currentTextureName);
            this.currentTexture = UploadManager.getOrCreateLocatableWithSize(abstractTexture != null ? abstractTexture : animationBundle.getDefaultTexture(), registerImmediately, textureResolution);
            if (collectAllTextures) {
                this.allTextures = new ArrayList<>();
                for (AbstractTexture texture : animationBundle.getTextures().values()) {
                    this.allTextures.add(UploadManager.getOrCreateLocatable(texture, false));
                }
                if (modelAssembly.getProjectileModels() != null) {
                    for (ProjectileModelBundle projectileModelBundle : modelAssembly.getProjectileModels().values()) {
                        this.allTextures.add(UploadManager.getOrCreateLocatable(projectileModelBundle.getTexture(), false));
                    }
                }
                if (modelAssembly.getVehicleModels() != null) {
                    for (VehicleModelBundle vehicleModelBundle : modelAssembly.getVehicleModels().values()) {
                        this.allTextures.add(UploadManager.getOrCreateLocatable(vehicleModelBundle.getTexture(), false));
                    }
                }
                return;
            }
            this.allTextures = null;
        }

        private void setTexture(AbstractTexture abstractTexture) {
            this.currentTexture = UploadManager.getOrCreateLocatableWithSize(abstractTexture, true, this.textureResolution);
        }

        @Override
        public boolean isValid() {
            return this.currentTexture != null && this.currentTexture.getResourceLocationOrNull() != null;
        }
    }
}
