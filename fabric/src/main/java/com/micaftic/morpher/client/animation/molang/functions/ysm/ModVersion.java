package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.Function;
import dev.architectury.platform.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModVersion implements Function {
    @Override
    @Nullable
    public Object evaluate(@NotNull ExecutionContext<?> context, @NotNull Function.ArgumentCollection arguments) {
        String modid = arguments.getAsString(context, 0);
        if (modid == null) {
            return null;
        }
        if (!Platform.isModLoaded(modid)) {
            return null;
        }
        return Platform.getMod(modid).getVersion();
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}