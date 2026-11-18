package com.micaftic.morpher.client.animation.particle;

import com.micaftic.morpher.client.render.ModelBone;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.List;

public final class AnimationParticleLocator {
    private AnimationParticleLocator() {}

    public static Vector3f resolve(List<ModelBone> bones, float[] parameters, String name, Matrix4f root) {
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
            ModelBone bone = bones.get(index);
            int offset = index * 12;
            if (offset + 11 >= parameters.length) return null;
            transform.translate(-parameters[offset + 3] / 16f, parameters[offset + 4] / 16f,
                    parameters[offset + 5] / 16f);
            transform.translate(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
            if (index != target) {
                float rx = parameters[offset];
                float ry = parameters[offset + 1];
                float rz = parameters[offset + 2];
                if (rz != 0.0f || ry != 0.0f || rx != 0.0f) {
                    transform.rotate(new Quaternionf().rotateZYX(rz, ry, rx));
                }
                transform.scale(parameters[offset + 6], parameters[offset + 7], parameters[offset + 8]);
                transform.translate(-bone.getPivotX() / 16f, -bone.getPivotY() / 16f, -bone.getPivotZ() / 16f);
            }
        }
        return transform.transformPosition(new Vector3f());
    }
}
