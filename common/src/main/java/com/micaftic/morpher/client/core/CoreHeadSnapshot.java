package com.micaftic.morpher.client.core;

/** Pinned d253 query names deliberately differ from the ysm pitch/yaw field naming. */
public record CoreHeadSnapshot(double netHeadYaw, double headPitch) {
    public double queryHeadXRotation() { return netHeadYaw; }
    public double queryHeadYRotation() { return headPitch; }
}
