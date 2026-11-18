package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangContext;
import dev.spm.core.scene.MolangProgram;
import dev.spm.core.scene.MolangValue;
import org.jetbrains.annotations.Nullable;

/**
 * Host Molang expression wrapper backed exclusively by {@code spm-core}'s {@link MolangProgram}.
 */
public final class HostMolangScript {
    public static final HostMolangScript ZERO = constant(0.0);
    public static final HostMolangScript ONE = constant(1.0);

    private final String source;
    private final @Nullable String failure;
    private final @Nullable MolangProgram program;
    private final boolean constant;
    private final double constantValue;

    private HostMolangScript(String source, @Nullable String failure, @Nullable MolangProgram program, boolean constant, double constantValue) {
        this.source = source == null ? "" : source;
        this.failure = failure;
        this.program = program;
        this.constant = constant;
        this.constantValue = constantValue;
    }

    public static HostMolangScript constant(double value) {
        return new HostMolangScript(Double.toString(value), null, null, true, value);
    }

    public static HostMolangScript compile(String source) {
        if (source == null || source.isBlank()) {
            return ZERO;
        }
        String trimmed = source.trim();
        try {
            double literal = Double.parseDouble(trimmed);
            if (Double.isFinite(literal)) {
                return new HostMolangScript(source, null, null, true, literal);
            }
        } catch (NumberFormatException ignored) {
        }
        try {
            return new HostMolangScript(source, null, MolangProgram.compile(source), false, 0.0);
        } catch (RuntimeException error) {
            return new HostMolangScript(source, error.getMessage() != null ? error.getMessage() : error.toString(), null, false, 0.0);
        }
    }

    public static HostMolangScript failed(String source, Throwable error) {
        String msg = error == null ? "parse error" : error.toString();
        return new HostMolangScript(source, msg, null, false, 0.0);
    }

    public String source() {
        return this.source;
    }

    public @Nullable String failure() {
        return this.failure;
    }

    public boolean parsedSuccessfully() {
        return this.failure == null;
    }

    public @Nullable MolangProgram coreProgram() {
        return this.program;
    }

    public boolean isConstant() {
        return this.constant;
    }

    public double constantValue() {
        return this.constantValue;
    }

    public double evaluate(@Nullable MolangContext context) {
        if (this.constant) {
            return this.constantValue;
        }
        if (this.program == null || context == null) {
            return 0.0;
        }
        try {
            double result = this.program.evaluate(context);
            return Double.isFinite(result) ? result : 0.0;
        } catch (RuntimeException ignored) {
            return 0.0;
        }
    }

    public float evaluateAsFloat(@Nullable MolangContext context) {
        return (float) evaluate(context);
    }

    public boolean evaluateAsBoolean(@Nullable MolangContext context) {
        return evaluate(context) != 0.0;
    }

    public MolangValue evaluateValue(@Nullable MolangContext context) {
        if (this.constant) {
            return MolangValue.number(this.constantValue);
        }
        if (this.program == null || context == null) {
            return MolangValue.missing();
        }
        try {
            return this.program.evaluateValue(context);
        } catch (RuntimeException ignored) {
            return MolangValue.missing();
        }
    }
}
