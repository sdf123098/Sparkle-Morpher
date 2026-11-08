package com.micaftic.morpher.client.core;

/** Scalar sampling conventions derived solely from d253 QueryBinding and MolangUtils. */
public final class CoreQuerySampling {
    private CoreQuerySampling() { }
    public static double timeOfDay(long timestamp) { return ((float)(timestamp+6000L)/24000)%1; }
    public static boolean sneaking(boolean onGround,boolean crouchingPose) { return onGround && crouchingPose; }
    public static double bodyYaw(float previous,float current,float partial) {
        float value=(previous+partial*(current-previous))%360;
        if(value>=180) value-=360;
        if(value< -180) value+=360;
        return value;
    }
    public static double groundSpeed(double x,double z) { return 20*(float)Math.sqrt((float)(x*x+z*z)); }
    /** Caller supplies the game's sine-table values, retaining Mth lookup behavior without MC here. */
    public static float capeFlap(float dx,float dy,float dz,float bodySin,float negativeBodyCos,
                                 float walkSin,float bob,boolean crouching) {
        float vertical=clamp(dy*10,-6,32);
        float forward=clamp((dx*bodySin+dz*negativeBodyCos)*100,0,150);
        vertical+=walkSin*32*bob;
        if(crouching) vertical+=25;
        return clamp((6+forward/2+vertical)/108,0,1);
    }
    private static float clamp(float value,float minimum,float maximum) { return Math.max(minimum,Math.min(maximum,value)); }
}
