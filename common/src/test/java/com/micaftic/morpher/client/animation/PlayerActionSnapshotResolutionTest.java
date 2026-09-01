package com.micaftic.morpher.client.animation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 1.2.4（§14.3 验收）：{@link ControllerActionResolver#resolveState(PlayerActionSnapshot)}
 * 状态映射表 —— run/walk、ladder、swim、fly、jump、sneak、ride、idle 全覆盖，
 * 并锁定双轨收敛后的判定语义（ladder 死区、run 需 moving、RIDE 最高优先）。
 */
class PlayerActionSnapshotResolutionTest {

    @Test
    void airborneEmptySnapshotResolvesToJump() {
        // 全 false 快照 = 不在地面也不在水里（空中）→ JUMP（历史语义）
        assertState(PlayerActionState.JUMP, PlayerActionSnapshot.builder().build());
    }

    @Test
    void onGroundQuietSnapshotResolvesToIdle() {
        assertState(PlayerActionState.IDLE, PlayerActionSnapshot.builder().onGround(true).build());
    }

    @Test
    void ridingSuppressesAllOtherActions() {
        assertState(PlayerActionState.RIDE, PlayerActionSnapshot.builder()
                .riding(true).onGround(true).sprinting(true).moving(true).build());
        assertState(PlayerActionState.RIDE, PlayerActionSnapshot.builder()
                .riding(true).deadOrDying(true).build());
    }

    @Test
    void deathIsHighestPriorityState() {
        assertState(PlayerActionState.DEATH, PlayerActionSnapshot.builder().deadOrDying(true).build());
        assertState(PlayerActionState.DEATH, PlayerActionSnapshot.builder()
                .deadOrDying(true).swimming(true).flying(true).build());
    }

    @Test
    void resolvesRiptideSleepSwim() {
        assertState(PlayerActionState.RIPTIDE, PlayerActionSnapshot.builder().riptide(true).build());
        assertState(PlayerActionState.SLEEP, PlayerActionSnapshot.builder().sleeping(true).build());
        assertState(PlayerActionState.SWIM, PlayerActionSnapshot.builder().swimming(true).build());
    }

    @Test
    void resolvesClimbVsClimbing() {
        assertState(PlayerActionState.CLIMB, PlayerActionSnapshot.builder()
                .swimmingPose(true).moving(true).build());
        assertState(PlayerActionState.CLIMBING, PlayerActionSnapshot.builder()
                .swimmingPose(true).build());
        // swimming 状态优先于 swimmingPose
        assertState(PlayerActionState.SWIM, PlayerActionSnapshot.builder()
                .swimming(true).swimmingPose(true).moving(true).build());
    }

    @Test
    void resolvesLadderUpStillnessDownWithDeadZone() {
        assertState(PlayerActionState.LADDER_UP, PlayerActionSnapshot.builder()
                .onClimbable(true).verticalSpeed(0.5f).build());
        assertState(PlayerActionState.LADDER_DOWN, PlayerActionSnapshot.builder()
                .onClimbable(true).verticalSpeed(-0.5f).build());
        assertState(PlayerActionState.LADDER_STILLNESS, PlayerActionSnapshot.builder()
                .onClimbable(true).verticalSpeed(0.0f).build());
        // 收敛修复：±0.01 死区内的微小抖动视为静止
        assertState(PlayerActionState.LADDER_STILLNESS, PlayerActionSnapshot.builder()
                .onClimbable(true).verticalSpeed(0.005f).build());
        assertState(PlayerActionState.LADDER_STILLNESS, PlayerActionSnapshot.builder()
                .onClimbable(true).verticalSpeed(-0.005f).build());
    }

    @Test
    void resolvesFlyVsElytraFly() {
        assertState(PlayerActionState.ELYTRA_FLY, PlayerActionSnapshot.builder().elytraFlying(true).build());
        assertState(PlayerActionState.FLY, PlayerActionSnapshot.builder().flying(true).build());
        // 鞘翅飞行优先于普通飞行
        assertState(PlayerActionState.ELYTRA_FLY, PlayerActionSnapshot.builder()
                .elytraFlying(true).flying(true).build());
    }

    @Test
    void resolvesSwimStandInWaterAirborne() {
        assertState(PlayerActionState.SWIM_STAND, PlayerActionSnapshot.builder()
                .inWater(true).build());
        // 水中且浮空（onGround=false）→ swim_stand
        assertState(PlayerActionState.SWIM_STAND, PlayerActionSnapshot.builder()
                .inWater(true).moving(true).build());
        // 落地水中 → 落到 walk/idle 分支
        assertState(PlayerActionState.WALK, PlayerActionSnapshot.builder()
                .inWater(true).onGround(true).moving(true).build());
    }

    @Test
    void resolvesAttackedBeforeJump() {
        assertState(PlayerActionState.ATTACKED, PlayerActionSnapshot.builder().attacked(true).build());
        // 受击优先于跳跃（与历史注册顺序一致：attacked 在 jump 之前注册）
        assertState(PlayerActionState.ATTACKED, PlayerActionSnapshot.builder()
                .attacked(true).moving(true).build());
    }

    @Test
    void resolvesJumpAirborne() {
        assertState(PlayerActionState.JUMP, PlayerActionSnapshot.builder().moving(true).build());
        assertState(PlayerActionState.JUMP, PlayerActionSnapshot.builder()
                .sprinting(true).moving(true).build());
    }

    @Test
    void resolvesSneakVsSneaking() {
        assertState(PlayerActionState.SNEAK, PlayerActionSnapshot.builder()
                .onGround(true).crouching(true).moving(true).build());
        assertState(PlayerActionState.SNEAKING, PlayerActionSnapshot.builder()
                .onGround(true).crouching(true).build());
    }

    @Test
    void runRequiresMovingEvenWhenSprinting() {
        assertState(PlayerActionState.RUN, PlayerActionSnapshot.builder()
                .onGround(true).sprinting(true).moving(true).build());
        // 收敛修复：原地冲刺不再是 RUN
        assertState(PlayerActionState.IDLE, PlayerActionSnapshot.builder()
                .onGround(true).sprinting(true).build());
    }

    @Test
    void resolvesWalkVsIdle() {
        assertState(PlayerActionState.WALK, PlayerActionSnapshot.builder()
                .onGround(true).moving(true).build());
        assertState(PlayerActionState.IDLE, PlayerActionSnapshot.builder()
                .onGround(true).build());
    }

    @Test
    void ladderLosesToSwimmingLikeLegacyOrder() {
        // 历史注册顺序：climb/climbing（HIGHEST）先于 ladder_*（HIGHEST）
        assertState(PlayerActionState.CLIMB, PlayerActionSnapshot.builder()
                .swimmingPose(true).onClimbable(true).moving(true).build());
    }

    @Test
    void flyWinsOverSwimStand() {
        assertState(PlayerActionState.FLY, PlayerActionSnapshot.builder()
                .flying(true).inWater(true).build());
    }

    private static void assertState(PlayerActionState expected, PlayerActionSnapshot snapshot) {
        assertEquals(expected, ControllerActionResolver.resolveState(snapshot));
    }
}
