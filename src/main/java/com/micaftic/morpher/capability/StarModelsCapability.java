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

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

public class StarModelsCapability implements ValueIOSerializable {

    private Set<String> starModels = Sets.newHashSet();

    public static Optional<StarModelsCapability> get(Player player) {
        return Optional.of(player.getData(NeoForgeCapabilityTypes.STAR_MODELS));
    }

    public void addModel(String str) {
        this.starModels.add(str);
    }

    public void copyFrom(StarModelsCapability other) {
        this.starModels = other.starModels;
    }

    public void removeModel(String str) {
        this.starModels.remove(str);
    }

    public boolean containsModel(String str) {
        return this.starModels.contains(str);
    }

    public Set<String> getStarModels() {
        return this.starModels;
    }

    public void setStarModels(Set<String> set) {
        this.starModels = set;
    }

    public void clear() {
        this.starModels.clear();
    }

    @Override
    public void serialize(ValueOutput output) {
        ValueOutput.TypedOutputList<String> list = output.list("models", Codec.STRING);
        for (String starModel : this.starModels) {
            list.add(starModel);
        }
    }

    @Override
    public void deserialize(ValueInput input) {
        this.starModels.clear();
        for (String model : input.listOrEmpty("models", Codec.STRING)) {
            this.starModels.add(model);
        }
    }

    public ListTag serializeNBT() {
        ListTag listTag = new ListTag();
        Iterator<String> it = this.starModels.iterator();
        while (it.hasNext()) {
            listTag.add(StringTag.valueOf(it.next()));
        }
        return listTag;
    }

    public void deserializeNBT(ListTag listTag) {
        this.starModels.clear();
        Iterator<Tag> it = listTag.iterator();
        while (it.hasNext()) {
            it.next().asString().ifPresent(this.starModels::add);
        }
    }
}
