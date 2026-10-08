package com.micaftic.morpher.event;

import com.micaftic.morpher.client.event.AnimationLockEvent;
import com.micaftic.morpher.client.event.ClientPlayerCloneEvent;
import com.micaftic.morpher.client.event.ClientPlayerJoinNotification;
import com.micaftic.morpher.client.event.ClientResourceLifecycleEvent;
import com.micaftic.morpher.client.event.ClientSetupEvent;
import com.micaftic.morpher.client.event.ClientTickEvent;
import com.micaftic.morpher.client.event.PlayerSkinTextureManager;
import com.micaftic.morpher.client.input.AnimationRouletteKey;
import com.micaftic.morpher.client.input.DebugAnimationKey;
import com.micaftic.morpher.client.input.ExtraAnimationKey;
import com.micaftic.morpher.client.input.FakePlayerModelKey;
import com.micaftic.morpher.client.input.TargetActionWheelKey;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.input.PlayerModelToggleKey;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.core.architectury.event.events.common.LifecycleEvent;
import com.micaftic.morpher.core.api.PlatformAPI;

public final class YsmEventBootstrap {

    private YsmEventBootstrap() {
    }

    public static void register() {
        CommonEvent.register();




        if (!PlatformAPI.isServer()) {
            ClientSetupEvent.register();
            ClientResourceLifecycleEvent.register();
            ClientTickEvent.register();
            ClientPlayerJoinNotification.register();
            ClientPlayerCloneEvent.register();
            AnimationLockEvent.register();
            PlayerSkinTextureManager.register();
            RendererManager.register();
            FakePlayerModelKey.register();
            TargetActionWheelKey.register();
            PlayerModelToggleKey.register();
            AnimationRouletteKey.register();
            DebugAnimationKey.register();
            ExtraAnimationKey.register();
            InputStateKey.register();
        }

        LifecycleEvent.fireSetup();
    }
}
