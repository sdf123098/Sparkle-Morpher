package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.entity.PlayerPreviewEntity;

import java.util.HashMap;
import java.util.Map;

final class YsmCardAnimator {

    private static final long FADEOUT_MILLIS = 350L;

    private final Map<String, String> defaultAnimByModel = new HashMap<>();
    private final Map<String, Long> hoverSince = new HashMap<>();
    private final Map<String, Long> fadeoutUntil = new HashMap<>();

    private static boolean hasAnim(PlayerPreviewEntity entity, String name) {
        if (entity == null || name == null) {
            return false;
        }
        try {
            return entity.getAnimation(name) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String defaultAnim(String modelId, PlayerPreviewEntity entity) {
        String cached = this.defaultAnimByModel.get(modelId);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String resolved = "";
        try {
            var props = entity.getModelAssembly().getModelData().getModelProperties();
            String declared = props == null ? null : props.getPreviewAnimation();
            if (declared != null && !declared.isEmpty() && hasAnim(entity, declared)) {
                resolved = declared;
            } else if (hasAnim(entity, "idle")) {
                resolved = "idle";
            }
        } catch (Exception ignored) {
        }
        this.defaultAnimByModel.put(modelId, resolved);
        return resolved.isEmpty() ? null : resolved;
    }

    void forget(String modelId) {
        this.defaultAnimByModel.remove(modelId);
        this.hoverSince.remove(modelId);
        this.fadeoutUntil.remove(modelId);
    }

    void update(String modelId, PlayerPreviewEntity entity, boolean hover, long now) {
        if (entity == null) {
            return;
        }
        try {
            String idle = defaultAnim(modelId, entity);
            boolean hasHover = hasAnim(entity, "hover");
            boolean hasFade = hasAnim(entity, "hover_fadeout");
            if (hover) {
                this.hoverSince.put(modelId, now);
                this.fadeoutUntil.remove(modelId);
            } else if (this.hoverSince.containsKey(modelId)) {
                this.hoverSince.remove(modelId);
                if (hasFade) {
                    this.fadeoutUntil.put(modelId, now + FADEOUT_MILLIS);
                }
            }
            Long until = this.fadeoutUntil.get(modelId);
            boolean fading = until != null && now < until;

            entity.getAnimationStateMachine().setCurrentAnimation(idle == null ? "" : idle);
            if (hover) {
                entity.getAnimationStateMachine().setPreviousAnimation(hasHover ? "hover" : "");
            } else if (fading) {
                entity.getAnimationStateMachine().setPreviousAnimation(hasFade ? "hover_fadeout" : "");
            } else {
                if (until != null) {
                    this.fadeoutUntil.remove(modelId);
                }
                entity.getAnimationStateMachine().setPreviousAnimation("");
            }
        } catch (Exception ignored) {
        }
    }
}
