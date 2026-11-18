package com.micaftic.morpher.client.animation.molang.struct;

import com.micaftic.morpher.client.render.BoneIds;
import dev.spm.core.scene.MolangValue;

import java.util.Map;

public abstract class Vec3fStruct {

    private static final int NAME_X = BoneIds.id("x");
    private static final int NAME_Y = BoneIds.id("y");
    private static final int NAME_Z = BoneIds.id("z");

    public float getProperty(int id) {
        if (id == NAME_X) {
            return getX();
        }
        if (id == NAME_Y) {
            return getY();
        }
        if (id == NAME_Z) {
            return getZ();
        }
        return 0.0f;
    }

    public MolangValue.StructValue toMolangStruct() {
        return MolangValue.struct(Map.of(
                "x", MolangValue.number(getX()),
                "y", MolangValue.number(getY()),
                "z", MolangValue.number(getZ())
        ));
    }

    public abstract float getX();

    public abstract float getY();

    public abstract float getZ();

    @Override
    public String toString() {
        return String.format("vec3{x=%.2f, y=%.2f, z=%.2f}", getX(), getY(), getZ());
    }
}
