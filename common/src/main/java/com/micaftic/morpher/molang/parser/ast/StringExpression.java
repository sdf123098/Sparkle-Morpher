package com.micaftic.morpher.molang.parser.ast;

import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class StringExpression implements Expression {

    private final String name;

    private final int path;

    private Identifier cachedLocation;

    private EquipmentSlot cachedSlot;

    private boolean slotResolved;

    public StringExpression(@NotNull String str) {
        this.name = Objects.requireNonNull(str, "value");
        this.path = StringPool.computeIfAbsent(str);
    }

    @NotNull
    public String getName() {
        return this.name;
    }

    public int getPath() {
        return this.path;
    }

    @Override
    public <R> R visit(@NotNull ExpressionVisitor<R> expressionVisitor) {
        return expressionVisitor.visitString(this);
    }

    public String toString() {
        return this.name;
    }

    @Nullable
    public Identifier getResourceLocation() {
        return this.cachedLocation;
    }

    public void setResourceLocation(@Nullable Identifier Identifier) {
        this.cachedLocation = Identifier;
    }

    @Nullable
    public EquipmentSlot getCachedSlot() {
        return this.cachedSlot;
    }

    public boolean isSlotResolved() {
        return this.slotResolved;
    }

    public void setCachedSlot(@Nullable EquipmentSlot slot) {
        this.cachedSlot = slot;
        this.slotResolved = true;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof String) {
            return this.name.equals(obj);
        }
        return (obj instanceof StringExpression) && this.path == ((StringExpression) obj).path;
    }

    public int hashCode() {
        return this.name.hashCode();
    }
}