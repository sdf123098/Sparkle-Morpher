package com.micaftic.morpher.capability;

import com.google.common.collect.Sets;
import com.micaftic.morpher.neoforge.NeoForgeCapabilityTypes;
import com.mojang.serialization.Codec;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

import java.util.Optional;
import java.util.Set;

public class AuthModelsCapability implements ValueIOSerializable {

    private Set<String> authModels = Sets.newHashSet();

    public static Optional<AuthModelsCapability> get(Player player) {
        return Optional.of(player.getData(NeoForgeCapabilityTypes.AUTH_MODELS));
    }

    public void addModel(String str) {
        this.authModels.add(str);
    }

    public void copyFrom(AuthModelsCapability other) {
        this.authModels = other.authModels;
    }

    public void removeModel(String str) {
        this.authModels.remove(str);
    }

    public boolean containsModel(String str) {
        return this.authModels.contains(str);
    }

    public Set<String> getAuthModels() {
        return this.authModels;
    }

    public void setAuthModels(Set<String> set) {
        this.authModels = set;
    }

    public void clear() {
        this.authModels.clear();
    }

    @Override
    public void serialize(ValueOutput output) {
        ValueOutput.TypedOutputList<String> list = output.list("models", Codec.STRING);
        for (String authModel : this.authModels) {
            list.add(authModel);
        }
    }

    @Override
    public void deserialize(ValueInput input) {
        this.authModels.clear();
        for (String model : input.listOrEmpty("models", Codec.STRING)) {
            this.authModels.add(model);
        }
    }

    public ListTag serializeNBT() {
        ListTag listTag = new ListTag();
        for (String authModel : this.authModels) {
            listTag.add(StringTag.valueOf(authModel));
        }
        return listTag;
    }

    public void deserializeNBT(ListTag listTag) {
        this.authModels.clear();
        for (Tag tag : listTag) {
            tag.asString().ifPresent(this.authModels::add);
        }
    }
}
