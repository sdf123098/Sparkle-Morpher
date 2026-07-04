package com.micaftic.morpher.client.model;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.file.AnimationFile;
import com.micaftic.morpher.resource.YSMClientMapper;
import com.micaftic.morpher.resource.YSMFolderDeserializer;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import it.unimi.dsi.fastutil.objects.Object2ReferenceLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * bbmodel / figura 导入模型专用的内建动作动画预设。
 *
 * <p>这套预设与 YSM 原生的 {@link BuiltinDefaultActionPreset} <b>完全隔离</b>：
 * 独立的资源目录、独立的清单、独立的缓存。动画针对 vanilla 玩家骨骼
 * （{@code Head/Body/LeftArm/RightArm/LeftLeg/RightLeg} 等）作者化，
 * 不复用、不依赖 YSM 的骨骼层级与动作内容。</p>
 *
 * <p>缺失的资源文件会被安全跳过（记 warn 后继续），因此在动作内容尚未
 * 完整补齐前，本类也能正常加载已存在的部分。</p>
 */
public final class BuiltinBbmodelActionPreset {

    private static final String BASE_PATH = "/assets/sparkle_morpher/builtin/bbmodel/animations/";

    private static final Map<String, String> PLAYER_ANIMATION_FILES = createPlayerAnimationFiles();

    private static volatile Map<String, AnimationFile> playerAnimations;

    private BuiltinBbmodelActionPreset() {
    }

    public static Map<String, AnimationFile> playerAnimations() {
        Map<String, AnimationFile> cached = playerAnimations;
        if (cached == null) {
            synchronized (BuiltinBbmodelActionPreset.class) {
                cached = playerAnimations;
                if (cached == null) {
                    cached = loadPlayerAnimations();
                    playerAnimations = cached;
                }
            }
        }
        return cached;
    }

    public static Object2ReferenceMap<String, Animation> mainAnimations() {
        Object2ReferenceLinkedOpenHashMap<String, Animation> animations = new Object2ReferenceLinkedOpenHashMap<>();
        for (Map.Entry<String, AnimationFile> entry : playerAnimations().entrySet()) {
            if (!"fp_arm".equals(entry.getKey())) {
                animations.putAll(entry.getValue().getAnimations());
            }
        }
        return Object2ReferenceMaps.unmodifiable(animations);
    }

    public static Object2ReferenceMap<String, Animation> armAnimations() {
        AnimationFile armFile = playerAnimations().get("fp_arm");
        if (armFile == null) {
            return Object2ReferenceMaps.emptyMap();
        }
        return Object2ReferenceMaps.unmodifiable(new Object2ReferenceLinkedOpenHashMap<>(armFile.getAnimations()));
    }

    private static Map<String, AnimationFile> loadPlayerAnimations() {
        LinkedHashMap<String, AnimationFile> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : PLAYER_ANIMATION_FILES.entrySet()) {
            byte[] data = readResource(BASE_PATH + entry.getValue());
            if (data == null) {
                YesSteveModel.LOGGER.warn("[SM] Missing builtin bbmodel action preset file {}", entry.getValue());
                continue;
            }
            try {
                RawYsmModel.RawAnimationFile rawFile = YSMFolderDeserializer.parseAnimationFile(data);
                Map<String, Animation> animations = YSMClientMapper.buildAnimations(rawFile, false);
                animations.values().forEach(animation -> {
                    if (animation.sourceKey == null) {
                        animation.sourceKey = entry.getKey();
                    }
                });
                result.put(entry.getKey(), new AnimationFile(animations));
            } catch (Exception e) {
                YesSteveModel.LOGGER.warn("[SM] Failed to load builtin bbmodel action preset file {}", entry.getValue(), e);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> createPlayerAnimationFiles() {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        // 基础游戏动作（状态驱动：fly/elytra/swim/sneak/jump/...）
        files.put("main", "main.animation.json");
        // 通用轮盘表情（extra0/extra1/...）
        files.put("extra", "extra.animation.json");
        return Collections.unmodifiableMap(files);
    }

    private static byte[] readResource(String path) {
        try (InputStream in = BuiltinBbmodelActionPreset.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to read builtin bbmodel action preset resource {}", path, e);
            return null;
        }
    }
}
