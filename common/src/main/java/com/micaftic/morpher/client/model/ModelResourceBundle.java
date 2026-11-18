package com.micaftic.morpher.client.model;

import com.micaftic.morpher.audio.AudioTrackData;
import com.micaftic.morpher.client.core.HostMolangScript;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;

import java.util.List;
import java.util.Map;

public class ModelResourceBundle {

    private final Map<String, AudioTrackData> soundEffects;

    private final Object2ReferenceOpenHashMap<String, HostMolangScript> functions;

    private final Object2ReferenceOpenHashMap<String, List<HostMolangScript>> events;

    private final Map<String, Map<String, String>> translations;

    public ModelResourceBundle(Map<String, AudioTrackData> soundEffects, Object2ReferenceOpenHashMap<String, HostMolangScript> functions, Object2ReferenceOpenHashMap<String, List<HostMolangScript>> events, Map<String, Map<String, String>> translations) {
        this.soundEffects = soundEffects;
        this.functions = functions;
        this.events = events;
        this.translations = translations;
    }

    public Map<String, AudioTrackData> getSoundEffects() {
        return this.soundEffects;
    }

    public Object2ReferenceOpenHashMap<String, HostMolangScript> getFunctions() {
        return this.functions;
    }

    public Object2ReferenceOpenHashMap<String, List<HostMolangScript>> getEvents() {
        return this.events;
    }

    public Map<String, Map<String, String>> getMetadata() {
        return this.translations;
    }
}
