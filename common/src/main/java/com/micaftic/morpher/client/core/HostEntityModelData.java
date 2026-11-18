package com.micaftic.morpher.client.core;

/**
 * Per-frame entity kinematics snapshot computed on the host for head tracking and animation predicates.
 */
public class HostEntityModelData {
    public boolean isSitting;
    public boolean isChild;
    public float netHeadYaw;
    public float headPitch;
    public float rawNetHeadYaw;
    public float rawHeadPitch;
    public float lerpBodyRot;
    public float lerpedAge;
}
