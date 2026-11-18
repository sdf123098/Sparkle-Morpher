package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.geckolib3.geo.render.built.GeoBone;
import com.micaftic.morpher.resource.models.GeometryDescription;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies SpmCoreBoneMatrixWriter matches expected hierarchical bone transforms, visibility, glow, and locators. */
final class SpmCoreBoneMatrixWriterTest {

    private static BuiltModel.BakedBone bone(String name, int parentIdx, float px, float py, float pz, boolean glow) {
        BuiltModel.BakedBone bone = new BuiltModel.BakedBone();
        bone.name = name;
        bone.parentIdx = parentIdx;
        bone.pivotX = px;
        bone.pivotY = py;
        bone.pivotZ = pz;
        bone.glow = glow;
        return bone;
    }

    private static BuiltModel twoBoneModel(boolean glowChild) {
        BuiltModel.BakedBone root = bone("root", -1, 0.0f, 16.0f, 0.0f, false);
        BuiltModel.BakedBone child = bone("child", 0, 4.0f, 8.0f, 0.0f, glowChild);
        BuiltModel.BakedBone hidden = bone("hidden_child", 0, 0.0f, 0.0f, 0.0f, false);
        BuiltModel model = new BuiltModel(
                new GeoBone[0],
                new String[37][0],
                new boolean[3],
                new GeometryDescription("geometry.test", 64, 64, 1, 1, new double[]{0, 0, 0}),
                new boolean[1]
        );
        model.bakedBones = List.of(root, child, hidden);
        model.bakedBoneOrder = new int[]{0, 1, 2};
        return model;
    }

    @Test
    void writes144ByteGpuRecordsAndLocatorStatesFromSpmCore() {
        BuiltModel model = twoBoneModel(true);
        float[] params = new float[3 * 12];
        // root: default scale 1, translate Y=+8 units (+0.5 blocks), rotateZ = PI/2
        params[2] = (float) (Math.PI / 2.0);
        params[4] = 8.0f;
        params[6] = 1.0f;
        params[7] = 1.0f;
        params[8] = 1.0f;
        params[11] = 1.0f; // locator flag for root

        // child: scale 1, glow=true, locator=true
        params[12 + 6] = 1.0f;
        params[12 + 7] = 1.0f;
        params[12 + 8] = 1.0f;
        params[12 + 11] = 1.0f;

        // hidden_child: ownHidden=1 (index 9)
        params[24 + 6] = 1.0f;
        params[24 + 7] = 1.0f;
        params[24 + 8] = 1.0f;
        params[24 + 9] = 1.0f;
        params[24 + 11] = 1.0f;

        float[] stateBuffer = new float[3 * 4];
        ByteBuffer outFloat = ByteBuffer.allocateDirect(3 * 144).order(ByteOrder.LITTLE_ENDIAN);
        Matrix4f rootPose = new Matrix4f().translate(1.0f, 2.0f, 3.0f);
        Matrix3f rootNormal = new Matrix3f().identity();

        assertTrue(SpmCoreBoneMatrixWriter.writeEvaluated(model, rootPose, rootNormal, params, stateBuffer, 0x00F0, outFloat, true));
        assertEquals(3 * 144, outFloat.remaining());

        // Reference math for root + child local matrices
        Matrix4f expectedRoot = new Matrix4f()
                .translate(0.0f, (16.0f + 8.0f) * 0.0625f, 0.0f)
                .rotateZ((float) (Math.PI / 2.0))
                .translate(0.0f, -1.0f, 0.0f);
        Matrix4f expectedChild = new Matrix4f(expectedRoot)
                .translate(4.0f * 0.0625f, 8.0f * 0.0625f, 0.0f)
                .translate(-4.0f / 16.0f, -8.0f / 16.0f, 0.0f);
        Matrix4f expectedGlobalRoot = new Matrix4f(rootPose).mul(expectedRoot);
        Matrix4f expectedGlobalChild = new Matrix4f(rootPose).mul(expectedChild);

        float[] readRoot = new float[16];
        for (int i = 0; i < 16; i++) {
            readRoot[i] = outFloat.getFloat(i * 4);
        }
        float[] expRootArr = new float[16];
        expectedGlobalRoot.get(expRootArr);
        for (int i = 0; i < 16; i++) {
            assertEquals(expRootArr[i], readRoot[i], 1e-4f, "root matrix element " + i);
        }
        // root tail (float)
        assertEquals((float) 0x00F0, outFloat.getFloat(128), 1e-4f);
        assertEquals(0.0f, outFloat.getFloat(132), 1e-4f);

        // child matrix & glow light + hidden=0
        float[] readChild = new float[16];
        for (int i = 0; i < 16; i++) {
            readChild[i] = outFloat.getFloat(144 + i * 4);
        }
        float[] expChildArr = new float[16];
        expectedGlobalChild.get(expChildArr);
        for (int i = 0; i < 16; i++) {
            assertEquals(expChildArr[i], readChild[i], 1e-4f, "child matrix element " + i);
        }
        assertEquals((float) SpmCoreBoneMatrixWriter.FULL_BRIGHT_LIGHT, outFloat.getFloat(144 + 128), 1e-4f);
        assertEquals(0.0f, outFloat.getFloat(144 + 132), 1e-4f);

        // hidden_child hidden=1.0
        assertEquals(1.0f, outFloat.getFloat(2 * 144 + 132), 1e-4f);

        // locator for root = [0, 24, 0], hidden_child stayed 0
        assertEquals(0.0f, stateBuffer[0], 1e-3f);
        assertEquals(24.0f, stateBuffer[1], 1e-3f);
        assertEquals(0.0f, stateBuffer[2], 1e-3f);
        assertEquals(0.0f, stateBuffer[8], 1e-3f);

        // Verify int-tail (OpenGL GpuRenderPath layout)
        ByteBuffer outInt = ByteBuffer.allocateDirect(3 * 144).order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(SpmCoreBoneMatrixWriter.writeEvaluated(model, rootPose, rootNormal, params, null, 0x00F0, outInt, false));
        assertEquals(0x00F0, outInt.getInt(128));
        assertEquals(0, outInt.getInt(132));
        assertEquals(SpmCoreBoneMatrixWriter.FULL_BRIGHT_LIGHT, outInt.getInt(144 + 128));
        assertEquals(1, outInt.getInt(2 * 144 + 132));

        Matrix4f[] localMats = new Matrix4f[3];
        boolean[] visible = new boolean[3];
        float[] fallbackState = new float[3 * 4];
        assertTrue(SpmCoreBoneMatrixWriter.evaluateBoneLocalTransforms(model, params, fallbackState, localMats, visible));
        assertTrue(visible[0]);
        assertTrue(visible[1]);
        assertFalse(visible[2]);
        float[] localChildArr = new float[16];
        float[] expLocalChildArr = new float[16];
        localMats[1].get(localChildArr);
        expectedChild.get(expLocalChildArr);
        for (int i = 0; i < 16; i++) {
            assertEquals(expLocalChildArr[i], localChildArr[i], 1e-4f, "local child matrix element " + i);
        }
        assertEquals(24.0f, fallbackState[1], 1e-3f);
    }
}
