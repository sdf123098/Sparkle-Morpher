package com.micaftic.morpher.client.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class CoreQuerySamplingTest {
 @Test void clockAndSneakUsePinnedConventions() {
  assertEquals(0.25,CoreQuerySampling.timeOfDay(0));
  assertEquals(0,CoreQuerySampling.timeOfDay(18000));
  assertEquals((double)(((float)(123456789012L+6000)/24000)%1),CoreQuerySampling.timeOfDay(123456789012L));
  assertFalse(CoreQuerySampling.sneaking(false,true));
  assertFalse(CoreQuerySampling.sneaking(true,false));
  assertTrue(CoreQuerySampling.sneaking(true,true));
 }
 @Test void bodyYawInterpolatesBeforeWrappingAndGroundSpeedUsesFloatSquareRoot() {
  assertEquals(-180,CoreQuerySampling.bodyYaw(350,10,0.5f));
  assertEquals(-180,CoreQuerySampling.bodyYaw(170,190,0.5f));
  assertEquals((double)(20*(float)Math.sqrt((float)(0.1*0.1+0.2*0.2))),CoreQuerySampling.groundSpeed(0.1,0.2));
 }
 @Test void capeBaselineAndCrouchingOffsetFollowReferenceClamps() {
  assertEquals(6f/108,CoreQuerySampling.capeFlap(0,0,0,0,-1,0,0,false));
  assertEquals(31f/108,CoreQuerySampling.capeFlap(0,0,0,0,-1,0,0,true));
  assertEquals(1,CoreQuerySampling.capeFlap(100,100,-100,1,0,1,1,true));
 }
}
