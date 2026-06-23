package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.geckolib3.core.molang.binding.ContextBinding;
import com.micaftic.morpher.util.data.LazySupplier;

public class TLMBinding extends ContextBinding {

    public static final LazySupplier<TLMBinding> INSTANCE = new LazySupplier<>(TLMBinding::new);

    public TLMBinding() {
        TouhouLittleMaidCompat.registerMaidAnimStates(this);
    }
}