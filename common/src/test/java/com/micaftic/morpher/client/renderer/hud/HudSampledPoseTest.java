package com.micaftic.morpher.client.renderer.hud;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class HudSampledPoseTest {
    @Test
    void heldPoseRestoresHudSampleAfterWorldBowPoseOverwritesLiveArrays() throws Exception {
        var pose = new HudSampledPose();
        Object owner = new Object(), model = new Object();
        float[] live = {.3f, .1f, 1f}, pivots = {2f, 3f};
        assertTrue(pose.capture(owner, model, live, pivots, .2f, -.1f));
        live[0] = 1.2f;
        pivots[0] = 9f;
        assertTrue(pose.restore(owner, model, live, pivots));
        assertArrayEquals(new float[]{.3f, .1f, 1f}, live);
        assertArrayEquals(new float[]{2f, 3f}, pivots);
    }

    @Test
    void capturedAndRestoredArraysCannotMutateSnapshotHistory() throws Exception {
        var pose = new HudSampledPose();
        Object owner = new Object(), model = new Object();
        float[] source = {1f, 2f}, sourcePivot = {3f};
        assertTrue(pose.capture(owner, model, source, sourcePivot, 0f, 0f));
        Arrays.fill(source, 8f);
        Arrays.fill(sourcePivot, 8f);
        float[] destination = new float[2], destinationPivot = new float[1];
        assertTrue(pose.restore(owner, model, destination, destinationPivot));
        destination[0] = 99f;
        destinationPivot[0] = 99f;
        assertTrue(pose.restore(owner, model, destination, destinationPivot));
        assertArrayEquals(new float[]{1f, 2f}, destination);
        assertArrayEquals(new float[]{3f}, destinationPivot);
    }

    @Test
    void ownerModelAndBothArrayShapesMustMatchBeforeWriting() throws Exception {
        var pose = new HudSampledPose();
        Object owner = new Object(), model = new Object();
        assertTrue(pose.capture(owner, model, new float[]{1f, 2f}, new float[]{3f}, 0f, 0f));
        float[] matrix = {7f, 7f}, pivot = {7f};
        assertFalse(pose.restore(new Object(), model, matrix, pivot));
        assertFalse(pose.restore(owner, new Object(), matrix, pivot));
        assertFalse(pose.restore(owner, model, new float[3], pivot));
        assertFalse(pose.restore(owner, model, matrix, new float[2]));
        assertArrayEquals(new float[]{7f, 7f}, matrix);
        assertArrayEquals(new float[]{7f}, pivot);
    }

    @Test
    void headTrackingBaseStaysWithItsSampleWhenLivePoseChanges() throws Exception {
        var pose = new HudSampledPose();
        Object owner = new Object(), model = new Object();
        float[] live = {.2f, -.3f};
        assertTrue(pose.capture(owner, model, live, new float[]{0f}, .2f, -.3f));
        live[0] = .8f;
        live[1] = .9f;
        assertEquals(.2f, pose.headBaseX());
        assertEquals(-.3f, pose.headBaseY());
    }

    @Test
    void invalidationForcesARealSampleInsteadOfUsingStalePose() throws Exception {
        var pose = new HudSampledPose();
        Object owner = new Object(), model = new Object();
        assertTrue(pose.capture(owner, model, new float[]{1f}, new float[]{2f}, 0f, 0f));
        pose.invalidate();
        float[] live = {7f}, pivot = {8f};
        assertFalse(pose.restore(owner, model, live, pivot));
        assertArrayEquals(new float[]{7f}, live);
        assertArrayEquals(new float[]{8f}, pivot);
    }

    @Test
    void newModelShapeReplacesSnapshotAndEmptyPoseCannotBeHeld() throws Exception {
        var pose = new HudSampledPose();
        Object owner = new Object(), model = new Object(), replacement = new Object();
        assertTrue(pose.capture(owner, model, new float[]{1f}, new float[]{2f}, 0f, 0f));
        assertTrue(pose.capture(owner, replacement, new float[]{3f, 4f}, new float[]{5f, 6f}, 0f, 0f));
        assertFalse(pose.restore(owner, model, new float[1], new float[1]));
        float[] live = new float[2], pivot = new float[2];
        assertTrue(pose.restore(owner, replacement, live, pivot));
        assertArrayEquals(new float[]{3f, 4f}, live);
        assertArrayEquals(new float[]{5f, 6f}, pivot);
        assertFalse(pose.capture(owner, replacement, new float[0], new float[0], 0f, 0f));
        assertFalse(pose.restore(owner, replacement, live, pivot));
    }

}
