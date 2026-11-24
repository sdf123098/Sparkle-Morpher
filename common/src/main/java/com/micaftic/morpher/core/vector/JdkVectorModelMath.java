package com.micaftic.morpher.core.vector;

import com.micaftic.morpher.client.render.BuiltModel;
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

    public static void transformQuadPositions(BuiltModel.BakedQuad quad, Matrix4f matrix, float[] outX, float[] outY, float[] outZ) {
        transformQuadPositions(
                quad,
                matrix.m00(), matrix.m10(), matrix.m20(), matrix.m30(),
                matrix.m01(), matrix.m11(), matrix.m21(), matrix.m31(),
                matrix.m02(), matrix.m12(), matrix.m22(), matrix.m32(),
                outX, outY, outZ
        );
    }

    public static void transformQuadPositions(
            BuiltModel.BakedQuad quad,
            float m00, float m10, float m20, float m30,
            float m01, float m11, float m21, float m31,
            float m02, float m12, float m22, float m32,
            float[] outX, float[] outY, float[] outZ
    ) {
        FloatVector vx = FloatVector.fromArray(SPECIES, quad.soaX, 0);
        FloatVector vy = FloatVector.fromArray(SPECIES, quad.soaY, 0);
        FloatVector vz = FloatVector.fromArray(SPECIES, quad.soaZ, 0);

        vx.mul(m00).add(vy.mul(m10)).add(vz.mul(m20)).add(m30).intoArray(outX, 0);
        vx.mul(m01).add(vy.mul(m11)).add(vz.mul(m21)).add(m31).intoArray(outY, 0);
        vx.mul(m02).add(vy.mul(m12)).add(vz.mul(m22)).add(m32).intoArray(outZ, 0);
    }
}
