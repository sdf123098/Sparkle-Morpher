package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.keyframe.BoneAnimation;
import com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.core.util.MathUtil;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.resource.bundle.KipfelCompatibility;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.stb.STBPerlin;
import java.util.HashMap;
import java.util.Map;

/** Single writer owned by AnimationProcessor. Immutable pose maps are published after complete steps. */
public final class KipfelRuntime {
    private record Key(String animation, int bone) { }
    private record Rotation(float x,float y,float z,float w) {
        Rotation(Quaternionf q) { this(q.x,q.y,q.z,q.w); }
        Quaternionf quaternion() { return new Quaternionf(x,y,z,w); }
    }
    private final KipfelSimulation simulation;
    private Map<Key,Rotation> previous = Map.of(), current = Map.of(), recoveryFrom = Map.of();
    private double recoveryTime = Double.NEGATIVE_INFINITY;
    private double renderTime;
    private net.minecraft.world.phys.Vec3 lastPosition;
    private final AnimatableEntity<?> owner;

    public KipfelRuntime(AnimatableEntity<?> owner) {
        this.owner = owner;
        simulation = new KipfelSimulation((seed,x,y)->STBPerlin.stb_perlin_noise3_seed(x,y,0,0,0,0,seed),owner.entity.getUUID().hashCode());
    }

    public void update(AnimationEvent<?> event, AnimationContext<?> context, ExpressionEvaluator<AnimationContext<?>> evaluator) {
        Entity entity = owner.entity;
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo, dy = entity.getY() - entity.yo;
        double ground = 20 * Math.hypot(dx,dz);
        double yawRadians = Math.toRadians(entity.getYRot());
        float forward = ground > 1e-4 ? (float)((-Math.sin(yawRadians)*dx + Math.cos(yawRadians)*dz)/Math.hypot(dx,dz)) : 1;
        float bv = numeric(context.storage.getScoped(StringPool.computeIfAbsent("bv")));
        var input = new KipfelSimulation.Input((float)ground,(float)(20*dy),forward,context.data.headPitch,
                20*Mth.wrapDegrees(entity.getYRot()-entity.yRotO),bv);
        // Use the integer game tick plus fractional tick; do not derive the fixed clock from float seekTime.
        renderTime = Math.max(renderTime, ((double)event.getTickCount() + event.getPartialTick()) / 20.0);
        // Vanilla teleport may update xo/yo/zo at once; compare our own immutable observations.
        net.minecraft.world.phys.Vec3 position = entity.position();
        boolean teleport = lastPosition != null && position.distanceToSqr(lastPosition) > 64;
        lastPosition = position;
        if (teleport) simulation.recoverOnNextAdvance();
        simulation.advance(renderTime,t->input,o->capture(o,context,evaluator));
        if(simulation.output()!=null) publishVariables(simulation.output(),context);
    }

    private void capture(KipfelSimulation.Output output, AnimationContext<?> context, ExpressionEvaluator<AnimationContext<?>> evaluator) {
        publishVariables(output,context);
        Map<Key,Rotation> next = new HashMap<>();
        for(String name:KipfelCompatibility.OUTPUTS) {
            Animation animation=owner.getAnimation(name);
            if(animation==null || !animation.stableKipfelAttachments) continue;
            for(BoneAnimation bone:animation.boneAnimations) {
                if(bone.rotationKeyFrames.isEmpty()) continue;
                Key key=new Key(name,bone.boneId);
                Vector3f value=bone.rotationKeyFrames.get(0).evaluate(evaluator,0);
                Rotation old=current.get(key);
                if(!finite(value)) { if(old!=null)next.put(key,old); continue; }
                Vector3f initial=owner.getBone(bone.boneId)==null ? new Vector3f() : owner.getBone(bone.boneId).getInitialRotation();
                Quaternionf q=new Quaternionf().rotationZYX(value.z+initial.z,value.y+initial.y,value.x+initial.x);
                if(old!=null && !output.recovery()) {
                    Quaternionf from=old.quaternion();
                    double angle=2*Math.acos(Math.min(1,Math.abs(from.dot(q))));
                    double budget=Math.toRadians(KipfelSimulation.MAX_ANGULAR_SPEED)/60;
                    if(angle>budget) from.slerp(q,(float)(budget/angle),q);
                }
                next.put(key,new Rotation(q));
            }
        }
        if(output.recovery() && !current.isEmpty()) { recoveryFrom=current; recoveryTime=renderTime; }
        previous=current.isEmpty() || output.recovery() ? Map.copyOf(next) : current;
        current=Map.copyOf(next);
        if(Boolean.getBoolean("sparkle_morpher.physicsTrace") && (output.recovery() || output.stepId()%60==0))
            YesSteveModel.LOGGER.info("[SM-PHYSICS] entity={} profile=kipfel-stable-v1 step={} t={} dt={} recovery={} target={} hair={} excitation={} delta={}",
                    owner.entity.getId(),output.stepId(),output.time(),output.recovery()?0:SimulationClock.STEP,
                    output.recovery(),output.target(),output.hair(),output.deltaHair(),output.delta());
    }

    public Vector3f sample(String animation,int bone,Vector3f initial) {
        Key key=new Key(animation,bone);
        Rotation now=current.get(key);
        if(now==null)return null;
        Rotation before=previous.getOrDefault(key,now);
        Quaternionf q=before.quaternion().slerp(now.quaternion(),simulation.interpolation());
        Rotation recovery=recoveryFrom.get(key);
        float blend=(float)Math.max(0,Math.min(1,(renderTime-recoveryTime)/.1));
        if(recovery!=null && blend<1) recovery.quaternion().slerp(q,blend,q);
        return MathUtil.getEulerAnglesZYX(q,new Vector3f()).sub(initial);
    }

    private static boolean finite(Vector3f v) { return Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z); }
    private static float numeric(Object value) { return value instanceof Number n && Float.isFinite(n.floatValue()) ? n.floatValue() : 0; }
    private static void set(AnimationContext<?> context,String name,float value) {
        context.storage.setScoped(StringPool.computeIfAbsent(name),value);
    }
    private static void publishVariables(KipfelSimulation.Output o,AnimationContext<?> c) {
        set(c,"hairso",o.hair()); set(c,"hairso_",o.hair()); set(c,"hairyawso",o.yaw());
        set(c,"hairpitchso",o.pitch()); set(c,"deltahair",o.deltaHair()); set(c,"deltahairso",o.delta());
        set(c,"fronthairnoise",o.frontNoise()); set(c,"fronthairrotationamplitude",5);
        set(c,"tailnoiseresponse",o.tailResponse()); set(c,"tailangle",o.tailAngle());
        set(c,"tailbaseamplitude",20); set(c,"tailphi",-20); set(c,"lastframetime",(float)o.time());
    }
}
