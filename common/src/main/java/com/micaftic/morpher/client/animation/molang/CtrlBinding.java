package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.client.animation.ControllerActionResolver;
import com.micaftic.morpher.core.compat.immersivemelodies.ImmersiveMelodiesCompat;
import com.micaftic.morpher.core.compat.ironsspellbooks.SpellbooksCompat;
import com.micaftic.morpher.geckolib3.core.controller.controllers.UnifiedPlayerActionController;
import com.micaftic.morpher.client.animation.molang.functions.ctrl.*;
import com.micaftic.morpher.core.compat.sbackpack.SBackpackCompat;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.animation.molang.functions.ctrl.HandRenderFunction;
import com.micaftic.morpher.core.compat.gun.tacz.TacCompat;
import com.micaftic.morpher.core.compat.bettercombat.BetterCombatCompat;
import com.micaftic.morpher.core.compat.carryon.CarryOnCompat;
import com.micaftic.morpher.core.compat.parcool.ParcoolCompat;
import com.micaftic.morpher.core.compat.slashblade.SlashBladeCompat;
import com.micaftic.morpher.core.compat.swem.SWEMCompat;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.enums.AnimationState;
import com.micaftic.morpher.geckolib3.core.molang.binding.ContextBinding;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.util.data.LazySupplier;
import com.micaftic.morpher.core.compat.create.CreateCompat;
import net.minecraft.world.entity.LivingEntity;

public class CtrlBinding extends ContextBinding {

    public static final LazySupplier<CtrlBinding> INSTANCE = new LazySupplier<>(CtrlBinding::new);

    private CtrlBinding() {
        registerState(ControllerActionResolver.DEATH);
        registerState(ControllerActionResolver.RIPTIDE);
        registerState(ControllerActionResolver.SLEEP);
        registerState(ControllerActionResolver.SWIM);
        registerState(ControllerActionResolver.CLIMB);
        registerState(ControllerActionResolver.CLIMBING);
        registerState(ControllerActionResolver.LADDER_UP);
        registerState(ControllerActionResolver.LADDER_STILLNESS);
        registerState(ControllerActionResolver.LADDER_DOWN);
        registerState(ControllerActionResolver.FLY);
        registerState(ControllerActionResolver.ELYTRA_FLY);
        registerState(ControllerActionResolver.SWIM_STAND);
        registerState(ControllerActionResolver.ATTACKED);
        registerState(ControllerActionResolver.JUMP);
        registerState(ControllerActionResolver.SNEAK);
        registerState(ControllerActionResolver.SNEAKING);
        registerState(ControllerActionResolver.RUN);
        registerState(ControllerActionResolver.WALK);
        registerState(ControllerActionResolver.IDLE);

        var("playing_extra_animation", CtrlBinding::isPlayingExtraAnimation);
        function("hold", HandRenderFunction.createAlways());
        function("swing", HandRenderFunction.createWhenSwinging());
        function("use", HandRenderFunction.createWhenUsing());
        function("armor", Armor.create());
        function("ride", Ride.create());
        CarryOnCompat.registerBindings(this);
        TacCompat.registerControllerFunctions(this);
        SWEMCompat.registerControllerFunctions(this);
        ParcoolCompat.registerBindings(this);
        SlashBladeCompat.registerControllerFunctions(this);
        SBackpackCompat.registerControllerFunctions(this);
        CreateCompat.registerCreateFunctions(this);
        BetterCombatCompat.registerBindings(this);
        ImmersiveMelodiesCompat.registerBindings(this);
        SpellbooksCompat.registerBindings(this);
        constValue("state_continue", 2);
        constValue("state_stop", 3);
        constValue("state_pause", 4);
        constValue("state_bypass", 5);
        constValue("loop", 10);
        constValue("play_once", 11);
        constValue("hold_on_last_frame", 12);
        function("set_animation", new SetAnimation());
        function("set_beginning_transition_length", new SetTransitionSpeed());
        function("reset", new Reset());
        function("indicate_reload", new IndicateReload());
    }

    private static boolean isPlayingExtraAnimation(IContext<Object> context) {
        AnimatableEntity<?> animatableEntity = context.geoInstance();
        if (!(animatableEntity instanceof CustomPlayerEntity customPlayerEntity)) {
            return false;
        }
        return customPlayerEntity.isModelSwitching()
                && customPlayerEntity.getAnimationState(UnifiedPlayerActionController.CAP_CONTROLLER_KEY) != AnimationState.IDLE;
    }

    private void registerState(String name) {
        livingEntityVar(name, ctx -> ControllerActionResolver.isState(name, ctx));
    }
}
