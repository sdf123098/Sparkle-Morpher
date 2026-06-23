package com.micaftic.morpher.core.gui;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OptionGroup {
    private final String translationKey;
    private final List<OptionRow<?>> rows = new ArrayList<>();

    public OptionGroup(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Component getTitle() {
        return Component.translatable("gui.sparkle_morpher.config.group." + translationKey);
    }

    public OptionGroup add(OptionRow<?> row) {
        rows.add(row);
        return this;
    }

    public List<OptionRow<?>> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public boolean isDirty() {
        for (OptionRow<?> row : rows) {
            if (row.getOption() != null && row.getOption().isDirty()) return true;
        }
        return false;
    }

    public void apply() {
        for (OptionRow<?> row : rows) {
            if (row.getOption() != null) row.getOption().apply();
        }
    }

    public void undo() {
        for (OptionRow<?> row : rows) {
            if (row.getOption() != null) row.getOption().undo();
            row.refresh();
        }
    }
}
