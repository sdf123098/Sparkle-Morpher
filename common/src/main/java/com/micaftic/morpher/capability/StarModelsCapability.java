package com.micaftic.morpher.capability;

import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.fabric.StarModelsCapabilityImpl;
import com.micaftic.morpher.core.architectury.platform.Platform;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

public class StarModelsCapability {

    private Set<String> starModels = Sets.newHashSet();

    private static StarModelsCapability cache;
    private static LocalStarModelsCapability localInstance;

    public static Optional<StarModelsCapability> get(Player player) {
        Optional<StarModelsCapability> cap = StarModelsCapabilityImpl.get(player);
        if(StarModelsCapabilityImpl.isClientSide()) {
            if(player instanceof LocalPlayer) {
                if(cap.isEmpty()) return Optional.empty();

                StarModelsCapability temp = cap.get();
                if(cache != temp) {
                    cache = temp;
                    localInstance = new LocalStarModelsCapability();
                }

                return Optional.of(localInstance);
            }
        }
        return cap;
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

    public static class LocalStarModelsCapability extends StarModelsCapability {
        private static final Path LOCAL_FILE = Platform.getConfigFolder()
                .resolve(YesSteveModel.MOD_ID)
                .resolve("local_starred_models.json");

        private boolean disableLocal = false;

        public LocalStarModelsCapability() {
            updateFromFile();
        }

        public void updateFromFile() {
            super.starModels.clear();
            try {
                JsonParser.parseString(FileUtils.readFileToString(LOCAL_FILE.toFile(), "UTF-8")).getAsJsonArray().forEach(je -> super.starModels.add(je.getAsString()));
            } catch (Throwable _) {}
        }

        public void save() {
            if(disableLocal) return;
            JsonArray ja = new JsonArray();
            super.starModels.forEach(ja::add);
            try {
                FileUtils.writeStringToFile(LOCAL_FILE.toFile(), ja.toString(), StandardCharsets.UTF_8);
            } catch (IOException _) {}
        }

        @Override
        public void addModel(String str) {
            super.addModel(str);
            save();
        }

        @Override
        public void removeModel(String str) {
            super.removeModel(str);
            save();
        }

        @Override
        public void clear() {
            super.clear();
            save();
        }

        @Override
        public void deserializeNBT(ListTag listTag) {
            disableLocal = true; // 从服务器获取到的收藏模型, 不要写到本地
            super.deserializeNBT(listTag);
        }

        @Override
        public void setStarModels(Set<String> set) {
            disableLocal = true; // 从服务器获取到的收藏模型, 不要写到本地
            super.setStarModels(set);
        }

    }

}
