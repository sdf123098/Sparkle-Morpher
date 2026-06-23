package com.micaftic.morpher.capability;

import com.google.common.collect.Sets;
import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

public class AuthModelsCapability {

    private Set<String> authModels = Sets.newHashSet();

    @ExpectPlatform
    public static Optional<AuthModelsCapability> get(Player player) {
        throw new AssertionError();
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
            this.authModels.add(tag.getAsString());
        }
    }
}