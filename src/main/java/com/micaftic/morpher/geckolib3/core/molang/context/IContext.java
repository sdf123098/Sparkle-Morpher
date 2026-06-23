package com.micaftic.morpher.geckolib3.core.molang.context;

import com.micaftic.morpher.audio.AudioPlayerManager;
import com.micaftic.morpher.geckolib3.core.controller.AnimationControllerContext;
import com.micaftic.morpher.audio.PlaybackFlags;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.molang.storage.*;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.model.provider.data.EntityModelData;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface IContext<TEntity> {
    TEntity entity();

    AnimatableEntity<?> geoInstance();

    Minecraft mc();

    ClientLevel level();

    AnimationEvent<?> animationEvent();

    EntityModelData data();

    @Nullable
    AnimationControllerContext animationControllerContext();

    @Nullable
    PlaybackFlags getPlaybackFlags();

    RandomSource random();

    <TChild> IContext<TChild> createChild(TChild tchild);

    ITempVariableStorage tempStorage();

    IScopedVariableStorage scopedStorage();

    @Nullable
    IControllerVariableStorage controllerStorage();

    IForeignVariableStorage foreignStorage();

    @Nullable
    IValue resolveExpression(String str);

    Object callFunction(ExecutionContext<?> context, IValue value, List<?> list);

    Object callFunctionWithArgs(ExecutionContext<?> context, IValue value, Function.ArgumentCollection arguments);

    List<?> getAnimationLayers();

    boolean isDebugMode();

    boolean isClientSide();

    void logWarning(String str, Object... objArr);

    void logWarningComponent(Component component);

    AudioPlayerManager getAudioPlayerManager(boolean global);
}