package com.micaftic.morpher.core.gui.molang;

import net.minecraft.network.chat.Component;
import com.micaftic.morpher.core.gui.Option;

import java.util.function.Consumer;
import java.util.function.Supplier;

final class LiveOption<T> extends Option<T> {
    private final Supplier<T> liveGetter;
    private final String titleText;
    private final String descText;

    LiveOption(String titleText, String descText, Supplier<T> getter, Consumer<T> setter) {
        super("", getter, setter);
        this.liveGetter = getter;
        this.titleText = titleText;
        this.descText = descText == null ? "" : descText;
    }

    @Override
    public T get() {
        return liveGetter.get();
    }

    @Override
    public Component getLabel() {
        return Component.literal(titleText);
    }

    @Override
    public Component getDescription() {
        return Component.literal(descText);
    }

    @Override
    public void setPending(T value) {
        super.setPending(value);
        apply();
    }
}
