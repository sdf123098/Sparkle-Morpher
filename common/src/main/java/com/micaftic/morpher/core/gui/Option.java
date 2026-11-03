package com.micaftic.morpher.core.gui;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Option<T> {
    private final String translationKey;
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private T pending;
    private boolean dirty;

    public Option(String translationKey, Supplier<T> getter, Consumer<T> setter) {
        this.translationKey = translationKey;
        this.getter = getter;
        this.setter = setter;
        this.pending = getter.get();
    }

    public static Option<Boolean> ofBoolean(String key, ModConfigSpec.BooleanValue cfg) {
        return new Option<>(key, cfg::get, value -> {
            cfg.set(value);
            cfg.save();
        });
    }

    public static Option<Double> ofDouble(String key, ModConfigSpec.DoubleValue cfg) {
        return new Option<>(key, cfg::get, value -> {
            cfg.set(value);
            cfg.save();
        });
    }

    public static <E extends Enum<E>> Option<E> ofEnum(String key, ModConfigSpec.EnumValue<E> cfg) {
        return new Option<>(key, cfg::get, value -> {
            cfg.set(value);
            cfg.save();
        });
    }

    public Component getLabel() {
        return Component.translatable("gui.sparkle_morpher.config." + translationKey);
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Component getDescription() {
        String descKey = "gui.sparkle_morpher.config." + translationKey + ".desc";
        return Component.translatable(descKey);
    }

    public T get() {
        return pending;
    }

    public void setPending(T value) {
        this.pending = value;
        this.dirty = !Objects.equals(value, getter.get());
    }

    public boolean isDirty() {
        return dirty;
    }

    public void apply() {
        if (dirty) {
            setter.accept(pending);
            dirty = false;
        }
    }

    public void undo() {
        this.pending = getter.get();
        this.dirty = false;
    }
}
