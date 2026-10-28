package com.micaftic.morpher.client.animation.particle;

import com.micaftic.morpher.geckolib3.geo.render.built.GeoBone;
import com.micaftic.morpher.geckolib3.util.RenderUtils;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.List;

public final class AnimationParticleLocator {
    private AnimationParticleLocator() {}

    public static Vector3f resolve(List<GeoBone> bones, float[] parameters, String name, Matrix4f root) {
        int target = -1;
        for (int i = 0; i < bones.size(); i++) {
            if (bones.get(i).getName().equals(name)) { target = i; break; }
        }
        if (target < 0) return null;
        var chain = new ArrayDeque<Integer>();
        int index = target;
        while (index >= 0) {
            if (index >= bones.size() || chain.size() >= bones.size()) return null;
            chain.push(index);
            index = bones.get(index).parentIdx;
        }
        var transform = new Matrix4f(root);
        while (!chain.isEmpty()) {
            index = chain.pop();
            GeoBone bone = bones.get(index);
            int offset = index * 12;
            if (offset + 11 >= parameters.length) return null;
            if (index == target) {
                transform.translate(-parameters[offset + 3] / 16f, parameters[offset + 4] / 16f,
                        parameters[offset + 5] / 16f);
                transform.translate(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
            } else {
                RenderUtils.prepMatrixForBone(bone, transform, parameters, offset);
            }
        }
        return transform.transformPosition(new Vector3f());
    }
}
