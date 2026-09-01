package com.micaftic.morpher.client.animation;

/**
 * 1.2.4（§14.1）：玩家基础动作的帧级快照。
 *
 * <p>由 {@link ControllerActionResolver#snapshot} 从 entity 一次性采集，
 * 再经 {@link ControllerActionResolver#resolveState} 映射为唯一的
 * {@link PlayerActionState}。快照本身是纯数据，不含任何 gameplay / network /
 * molang 副作用，任何渲染 pass 多次求值都只读它。
 */
public record PlayerActionSnapshot(
        boolean deadOrDying,
        boolean riptide,
        boolean sleeping,
        boolean swimming,
        boolean swimmingPose,
        boolean onClimbable,
        boolean flying,
        boolean elytraFlying,
        boolean inWater,
        boolean onGround,
        boolean crouching,
        boolean sprinting,
        boolean attacked,
        boolean moving,
        float verticalSpeed,
        boolean riding
) {

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 仅用于测试与显式构造；运行时请使用 {@link ControllerActionResolver#snapshot}。
     */
    public static PlayerActionSnapshot of(
            boolean deadOrDying,
            boolean riptide,
            boolean sleeping,
            boolean swimming,
            boolean swimmingPose,
            boolean onClimbable,
            boolean flying,
            boolean elytraFlying,
            boolean inWater,
            boolean onGround,
            boolean crouching,
            boolean sprinting,
            boolean attacked,
            boolean moving,
            float verticalSpeed,
            boolean riding
    ) {
        return new PlayerActionSnapshot(deadOrDying, riptide, sleeping, swimming, swimmingPose, onClimbable,
                flying, elytraFlying, inWater, onGround, crouching, sprinting, attacked, moving, verticalSpeed, riding);
    }

    public static final class Builder {

        private boolean deadOrDying;
        private boolean riptide;
        private boolean sleeping;
        private boolean swimming;
        private boolean swimmingPose;
        private boolean onClimbable;
        private boolean flying;
        private boolean elytraFlying;
        private boolean inWater;
        private boolean onGround;
        private boolean crouching;
        private boolean sprinting;
        private boolean attacked;
        private boolean moving;
        private float verticalSpeed;
        private boolean riding;

        private Builder() {
        }

        public Builder deadOrDying(boolean deadOrDying) {
            this.deadOrDying = deadOrDying;
            return this;
        }

        public Builder riptide(boolean riptide) {
            this.riptide = riptide;
            return this;
        }

        public Builder sleeping(boolean sleeping) {
            this.sleeping = sleeping;
            return this;
        }

        public Builder swimming(boolean swimming) {
            this.swimming = swimming;
            return this;
        }

        public Builder swimmingPose(boolean swimmingPose) {
            this.swimmingPose = swimmingPose;
            return this;
        }

        public Builder onClimbable(boolean onClimbable) {
            this.onClimbable = onClimbable;
            return this;
        }

        public Builder flying(boolean flying) {
            this.flying = flying;
            return this;
        }

        public Builder elytraFlying(boolean elytraFlying) {
            this.elytraFlying = elytraFlying;
            return this;
        }

        public Builder inWater(boolean inWater) {
            this.inWater = inWater;
            return this;
        }

        public Builder onGround(boolean onGround) {
            this.onGround = onGround;
            return this;
        }

        public Builder crouching(boolean crouching) {
            this.crouching = crouching;
            return this;
        }

        public Builder sprinting(boolean sprinting) {
            this.sprinting = sprinting;
            return this;
        }

        public Builder attacked(boolean attacked) {
            this.attacked = attacked;
            return this;
        }

        public Builder moving(boolean moving) {
            this.moving = moving;
            return this;
        }

        public Builder verticalSpeed(float verticalSpeed) {
            this.verticalSpeed = verticalSpeed;
            return this;
        }

        public Builder riding(boolean riding) {
            this.riding = riding;
            return this;
        }

        public PlayerActionSnapshot build() {
            return new PlayerActionSnapshot(deadOrDying, riptide, sleeping, swimming, swimmingPose, onClimbable,
                    flying, elytraFlying, inWater, onGround, crouching, sprinting, attacked, moving, verticalSpeed, riding);
        }
    }
}
