package com.micaftic.morpher.capability;

import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import com.micaftic.morpher.neoforge.NeoForgeCapabilityTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

import java.util.Optional;

public class VehicleModelCapability implements ValueIOSerializable {
    private static final String VALUE_IO_NBT_KEY = "nbt";

    public static Optional<VehicleModelCapability> get(Entity entity) {
        return Optional.of(entity.getData(NeoForgeCapabilityTypes.VEHICLE_MODEL));
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
    public String getOwnerTexture() { return this.ownerTexture; }
    public String getRouletteAnimation() { return this.rouletteAnimation; }

    public boolean isInitialized() {
        return this.initialized;
    }

    public Object2FloatOpenHashMap<String> getMolangVars() {
        return this.molangVars;
    }

    @Override
    public void serialize(ValueOutput output) {
        output.putString(VALUE_IO_NBT_KEY, serializeNBT().toString());
    }

    @Override
    public void deserialize(ValueInput input) {
        String data = input.getStringOr(VALUE_IO_NBT_KEY, "");
        if (!data.isEmpty()) {
            try {
                deserializeNBT(TagParser.parseCompoundFully(data));
            } catch (Exception ignored) {
                clearMaidModel();
            }
        }
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
        this.ownerModelId = compoundTag.getString("owner_model_id").orElse("");
        this.ownerTexture = compoundTag.getString("owner_texture").orElse("");
        this.rouletteAnimation = compoundTag.getString("roulette_animation").orElse("");
        this.initialized = compoundTag.getBoolean("initialized").orElse(false);
        this.molangVars.clear();
        CompoundTag compound = compoundTag.getCompound("molang_vars_server_bound").orElse(null);
        if (compound != null) {
            for (String str : compound.keySet()) {
                this.molangVars.put(str, compound.getFloat(str).orElse(0f));
            }
        }
    }
}
