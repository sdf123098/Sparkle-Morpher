package com.micaftic.morpher.client.renderer.layer;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.util.ItemTagsConstants;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Detects sword geometry authored under YSM's left/right sword locator bones. */
public final class SwordReplacementHelper {
    private static final Map<GeoModel, SwordGeometry> GEOMETRY_CACHE = new WeakHashMap<>();

    private SwordReplacementHelper() {
    }

    public static boolean shouldSuppressVanillaItem(GeoModel model, ItemStack stack, HumanoidArm arm) {
        if (model == null || stack == null || stack.isEmpty() || arm == null || !isSwordItem(stack)) {
            return false;
        }

        SwordGeometry geometry;
        synchronized (GEOMETRY_CACHE) {
            geometry = GEOMETRY_CACHE.computeIfAbsent(model, SwordReplacementHelper::scanGeometry);
        }
        return arm == HumanoidArm.LEFT ? geometry.left() : geometry.right();
    }

    private static boolean isSwordItem(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTagsConstants.SWORDS);
    }

    private static SwordGeometry scanGeometry(GeoModel model) {
        List<GeoModel.BakedBone> bones = model.bakedBones;
        if (bones == null || bones.isEmpty()) {
            return new SwordGeometry(false, false);
        }

        boolean[] leftAnchors = new boolean[bones.size()];
        boolean[] rightAnchors = new boolean[bones.size()];
        for (int i = 0; i < bones.size(); i++) {
            String name = normalizeBoneName(bones.get(i).name);
            leftAnchors[i] = isAnchor(name, "leftsword");
            rightAnchors[i] = isAnchor(name, "rightsword");
        }

        boolean hasLeftGeometry = false;
        boolean hasRightGeometry = false;
        for (int i = 0; i < bones.size() && !(hasLeftGeometry && hasRightGeometry); i++) {
            GeoModel.BakedBone bone = bones.get(i);
            if (bone.cubes == null || bone.cubes.stream().noneMatch(cube ->
                    cube != null && cube.quads != null && !cube.quads.isEmpty())) {
                continue;
            }

            int ancestorIndex = i;
            for (int depth = 0; ancestorIndex >= 0 && depth < bones.size(); depth++) {
                if (ancestorIndex >= bones.size()) {
                    break;
                }
                if (leftAnchors[ancestorIndex]) {
                    hasLeftGeometry = true;
                    break;
                }
                if (rightAnchors[ancestorIndex]) {
                    hasRightGeometry = true;
                    break;
                }
                ancestorIndex = bones.get(ancestorIndex).parentIdx;
            }
        }

        return new SwordGeometry(hasLeftGeometry, hasRightGeometry);
    }

    private static boolean isAnchor(String normalizedName, String anchorName) {
        if (normalizedName.equals(anchorName)) {
            return true;
        }
        if (!normalizedName.startsWith(anchorName)) {
            return false;
        }
        String suffix = normalizedName.substring(anchorName.length());
        return !suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit);
    }

    private static String normalizeBoneName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char character = Character.toLowerCase(name.charAt(i));
            if (Character.isLetterOrDigit(character)) {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    private record SwordGeometry(boolean left, boolean right) {
    }
}
