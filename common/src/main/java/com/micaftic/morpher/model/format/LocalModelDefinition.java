package com.micaftic.morpher.model.format;

import com.micaftic.morpher.util.FileTypeUtil;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

public class LocalModelDefinition {
    // 妯″瀷鐨勭洰閷勫悕绋?
    private final String modelId;
    private final ServerAnimationInfo serverAnimationInfo;
    private final Set<Identifier> entityTypes = new HashSet<>();
    private final Set<Identifier> excludedEntityTypes = new HashSet<>();
    private final ModelMetadata info;
    private final boolean isCustomSkinModel; // 鍙兘淇?
    private final boolean isAuth; // 鍦╝uth璩囨枡澶句笖is_free鐐篺alse

    // 鎷嬪皠鐗?渚嬪绠?涓夊弶鎴?涔嬮鐨?鏉愯唱鍦╰extures minecraft:arrow ....
    private Object[] projectiles;
    // 鍧愰◣ 渚嬪 鑸?绀﹁粖 棣?minecraft:horse ....
    private Object[] vehicles;

    public LocalModelDefinition(String modelId, ServerAnimationInfo serverAnimationInfo, Object[] projectiles, Object[] vehicles, ModelMetadata info, boolean encrypted, boolean isAuth) {
        this.modelId = modelId;
        this.serverAnimationInfo = serverAnimationInfo;
        this.projectiles = projectiles;
        this.vehicles = vehicles;
        this.info = info;
        this.isCustomSkinModel = encrypted;
        this.isAuth = isAuth;
    }

    public String getModelId() {
        return this.modelId;
    }

    public ServerAnimationInfo getModelInfo() {
        return this.serverAnimationInfo;
    }

    public Set<Identifier> getEntityTypes() {
        for (Object obj : this.projectiles) {
            this.entityTypes.addAll(FileTypeUtil.resolveEntityTypes((String[]) obj));
            this.projectiles = null;
        }
        return this.entityTypes;
    }

    public Set<Identifier> getExcludedEntityTypes() {
        for (Object obj : this.vehicles) {
            this.excludedEntityTypes.addAll(FileTypeUtil.resolveEntityTypes((String[]) obj));
            this.vehicles = null;
        }
        return this.excludedEntityTypes;
    }

    public ModelMetadata getLoadedModelData() {
        return this.info;
    }

    public boolean isCustomSkinModel() {
        return this.isCustomSkinModel;
    }

    public boolean isAuth() {
        return this.isAuth;
    }
}