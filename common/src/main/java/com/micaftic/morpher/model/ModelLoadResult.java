package com.micaftic.morpher.model;

import com.micaftic.morpher.model.format.ServerModelData;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

public class ModelLoadResult {

    private final boolean success;

    @Nullable
    private final Component errorMessage;

    private final Map<String, ServerModelData> modelDefinitions;

    private final Set<String> authModelIds;

    private final Map<String, ServerPackData> packs;

    public ModelLoadResult(boolean success, @Nullable Object errorMessage, Map<String, ServerModelData> map, String[] strArr) {
        this(success, errorMessage, map, strArr, null);
    }

    public ModelLoadResult(boolean success, @Nullable Object errorMessage, Map<String, ServerModelData> map,
                           String[] strArr, Map<String, ServerPackData> packs) {
        this.success = success;
        this.errorMessage = (Component) errorMessage;
        this.modelDefinitions = map == null ? Object2ReferenceMaps.emptyMap() : ImmutableMap.copyOf(map);
        this.authModelIds = strArr == null ? ObjectSets.emptySet() : ImmutableSet.copyOf(strArr);
        this.packs = packs == null ? Object2ReferenceMaps.emptyMap() : ImmutableMap.copyOf(packs);
    }

    public boolean isSuccess() {
        return this.success;
    }

    @Nullable
    public Component getErrorMessage() {
        return this.errorMessage;
    }

    public Map<String, ServerModelData> getModelDefinitions() {
        return this.modelDefinitions;
    }

    public Set<String> getAuthModelIds() {
        return this.authModelIds;
    }

    public Map<String, ServerPackData> getPacks() {
        return this.packs;
    }
}
