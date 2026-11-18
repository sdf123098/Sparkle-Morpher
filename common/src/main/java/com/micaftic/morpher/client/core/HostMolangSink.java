package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangValue;

import java.util.List;

/**
 * Typed host-side binding sink for {@code ysm.*}, {@code ctrl.*}, and {@code tlm.*} variables and functions,
 * evaluated on {@code spm-core}.
 */
public interface HostMolangSink {

    @FunctionalInterface
    interface VarSampler {
        MolangValue sample(HostActionEvent<?> event);
    }

    @FunctionalInterface
    interface FunctionHandler {
        MolangValue invoke(HostActionEvent<?> event, List<MolangValue> arguments);
    }

    void var(String name, VarSampler sampler);

    void function(String name, FunctionHandler handler);
}
