package com.micaftic.morpher.client.animation.molang;

import com.micaftic.morpher.client.core.HostMolangScript;
import dev.spm.core.scene.MolangContext;
import dev.spm.core.scene.MolangValue;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.text.DecimalFormat;
import java.util.function.BiConsumer;

public class MolangWatchRegistry {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.#####");

    private final ReferenceArrayList<WatchEntry> entries = new ReferenceArrayList<>();

    public enum EvaluationPhase {
        PRE_ANIMATION,
        POST_ANIMATION
    }

    public void addWatch(EvaluationPhase phase, String label, HostMolangScript value) {
        this.entries.add(new WatchEntry(label, value, phase));
    }

    public void removeWatch(String label) {
        this.entries.removeIf(entry -> entry.label.equals(label));
    }

    public void clearAll() {
        this.entries.clear();
    }

    public void evaluatePreAnimation(MolangContext context) {
        for (int i = 0; i < this.entries.size(); i++) {
            WatchEntry entry = this.entries.get(i);
            if (entry.phase == EvaluationPhase.PRE_ANIMATION) {
                entry.evaluate(context);
            }
        }
    }

    public void evaluatePostAnimation(MolangContext context) {
        for (int i = 0; i < this.entries.size(); i++) {
            WatchEntry entry = this.entries.get(i);
            if (entry.phase == EvaluationPhase.POST_ANIMATION) {
                entry.evaluate(context);
            }
        }
    }

    public void forEachEntry(BiConsumer<String, String> biConsumer) {
        for (WatchEntry entry : this.entries) {
            biConsumer.accept(entry.label, entry.resultValue);
        }
    }

    private static final class WatchEntry {
        private final String label;
        private final HostMolangScript value;
        private final EvaluationPhase phase;
        private volatile String resultValue;

        private WatchEntry(String label, HostMolangScript value, EvaluationPhase phase) {
            this.label = label;
            this.value = value;
            this.phase = phase;
        }

        private void evaluate(MolangContext context) {
            try {
                MolangValue result = this.value.evaluateValue(context);
                if (result == null || result == MolangValue.missing()) {
                    this.resultValue = "null";
                } else if (result instanceof MolangValue.BooleanValue boolValue) {
                    this.resultValue = Boolean.toString(boolValue.value());
                } else if (result instanceof MolangValue.TextValue textValue) {
                    this.resultValue = textValue.value();
                } else if (result instanceof MolangValue.NumberValue numValue) {
                    this.resultValue = MolangWatchRegistry.DECIMAL_FORMAT.format(numValue.value());
                } else {
                    this.resultValue = result.toString();
                }
            } catch (Throwable th) {
                this.resultValue = "Error: " + th.getMessage();
            }
        }
    }
}
