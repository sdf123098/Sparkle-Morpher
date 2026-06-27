package com.micaftic.morpher.core.vector;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.joml.Matrix4f;

public final class JdkVectorModelMath {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_128;
    private static final boolean AVAILABLE = SPECIES.length() >= 4;

    private JdkVectorModelMath() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static void transformQuadPositions(GeoModel.BakedQuad quad, Matrix4f matrix, float[] outX, float[] outY, float[] outZ) {
        float[] x = outX;
        float[] y = outY;
        float[] z = outZ;
        x[0] = quad.x(0);
        x[1] = quad.x(1);
        x[2] = quad.x(2);
        x[3] = quad.x(3);
        y[0] = quad.y(0);
        y[1] = quad.y(1);
        y[2] = quad.y(2);
        y[3] = quad.y(3);
        z[0] = quad.z(0);
        z[1] = quad.z(1);
        z[2] = quad.z(2);
        z[3] = quad.z(3);

        FloatVector vx = FloatVector.fromArray(SPECIES, x, 0);
        FloatVector vy = FloatVector.fromArray(SPECIES, y, 0);
        FloatVector vz = FloatVector.fromArray(SPECIES, z, 0);

        vx.mul(matrix.m00())
                .add(vy.mul(matrix.m10()))
                .add(vz.mul(matrix.m20()))
                .add(matrix.m30())
                .intoArray(outX, 0);
        vx.mul(matrix.m01())
                .add(vy.mul(matrix.m11()))
                .add(vz.mul(matrix.m21()))
                .add(matrix.m31())
                .intoArray(outY, 0);
        vx.mul(matrix.m02())
                .add(vy.mul(matrix.m12()))
                .add(vz.mul(matrix.m22()))
                .add(matrix.m32())
                .intoArray(outZ, 0);
    }
}
