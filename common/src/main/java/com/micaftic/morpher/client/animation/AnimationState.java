package com.micaftic.morpher.client.animation;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostAnimatable;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BiPredicate;

public class AnimationState<TE extends LivingEntity, AE extends HostAnimatable<TE>> {

    private final String animationName;

    private final CodedLoopMode loopType;

    private final int priority;

    private final BiPredicate<TE, HostActionEvent<AE>> predicate;

    public AnimationState(String animationName, CodedLoopMode loopType, int priority, BiPredicate<TE, HostActionEvent<AE>> predicate) {
        this.animationName = animationName;
        this.loopType = loopType;
        this.priority = Mth.clamp(priority, Priority.HIGHEST, Priority.LOWEST);
        this.predicate = predicate;
    }

    public BiPredicate<TE, HostActionEvent<AE>> getPredicate() {
        return this.predicate;
    }

    public String getAnimationName() {
        return this.animationName;
    }

    public CodedLoopMode getLoopType() {
        return this.loopType;
    }

    public int getPriority() {
        return this.priority;
    }
}
