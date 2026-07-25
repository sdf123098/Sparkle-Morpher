package com.micaftic.morpher.capability;

import dev.architectury.injectables.annotations.ExpectPlatform;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public class VehicleModelCapability {

    @ExpectPlatform
    public static Optional<VehicleModelCapability> get(Entity entity) {
        throw new AssertionError();
    }

    private String ownerModelId = "default";

    private String ownerTexture = "";

    private String rouletteAnimation = "";

    private boolean initialized = false;

    private Object2FloatOpenHashMap<String> molangVars = new Object2FloatOpenHashMap<>();

    public void setModel(String str, Object2FloatOpenHashMap<String> object2FloatOpenHashMap) {
        this.ownerModelId = str;
        this.initialized = true;
        this.molangVars = new Object2FloatOpenHashMap<>(object2FloatOpenHashMap);
    }

    public void setMaidModel(String modelId, String texture, Object2FloatOpenHashMap<String> molangVars) {
        this.ownerModelId = modelId;
        this.ownerTexture = texture == null ? "" : texture;
        this.initialized = true;
        this.molangVars = new Object2FloatOpenHashMap<>(molangVars);
    }

    public void clearMaidModel() {
        this.ownerModelId = "default";
        this.ownerTexture = "";
        this.rouletteAnimation = "";
        this.initialized = false;
        this.molangVars.clear();
    }

    public void setRouletteAnimation(String animation) {
        this.rouletteAnimation = animation == null ? "" : animation;
    }

    public void copyFrom(VehicleModelCapability other) {
        this.ownerModelId = other.ownerModelId;
        this.ownerTexture = other.ownerTexture;
        this.rouletteAnimation = other.rouletteAnimation;
        this.initialized = other.initialized;
        this.molangVars = new Object2FloatOpenHashMap<>(other.molangVars);
    }

    public String getOwnerModelId() {
        return this.ownerModelId;
    }

    public String getOwnerTexture() {
        return this.ownerTexture;
    }

    public String getRouletteAnimation() {
        return this.rouletteAnimation;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public Object2FloatOpenHashMap<String> getMolangVars() {
        return this.molangVars;
    }

    public CompoundTag serializeNBT() {
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("owner_model_id", this.ownerModelId);
        compoundTag.putString("owner_texture", this.ownerTexture);
        compoundTag.putString("roulette_animation", this.rouletteAnimation);
        compoundTag.putBoolean("initialized", this.initialized);
        CompoundTag compoundTag2 = new CompoundTag();
        this.molangVars.object2FloatEntrySet().fastForEach(entry -> {
            compoundTag2.putFloat(entry.getKey(), entry.getFloatValue());
        });
        compoundTag.put("molang_vars_server_bound", compoundTag2);
        return compoundTag;
    }

    public void deserializeNBT(CompoundTag compoundTag) {
        this.ownerModelId = compoundTag.getString("owner_model_id");
        this.ownerTexture = compoundTag.getString("owner_texture");
        this.rouletteAnimation = compoundTag.getString("roulette_animation");
        this.initialized = compoundTag.getBoolean("initialized");
        this.molangVars.clear();
        CompoundTag compound = compoundTag.getCompound("molang_vars_server_bound");
        for (String str : compound.getAllKeys()) {
            this.molangVars.put(str, compound.getFloat(str));
        }
    }
}
