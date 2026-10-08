package com.micaftic.morpher.model;

import java.util.Map;

/** Immutable model evaluation results, independent of a game packet or Cloud transport. */
public record LocalModelFeedback(int entityId, Map<String, Float> stringValues, int flags) {
    public LocalModelFeedback {
        stringValues = Map.copyOf(stringValues);
        if (stringValues.size() > 4096 || stringValues.entrySet().stream().anyMatch(entry ->
                entry.getKey().isEmpty() || entry.getKey().length() > 256
                || entry.getKey().chars().anyMatch(Character::isISOControl)
                || !Float.isFinite(entry.getValue()))) {
            throw new IllegalArgumentException("Invalid local model feedback");
        }
    }
}
