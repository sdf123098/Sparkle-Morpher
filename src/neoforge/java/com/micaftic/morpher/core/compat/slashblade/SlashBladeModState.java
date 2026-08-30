package com.micaftic.morpher.core.compat.slashblade;

import net.neoforged.fml.ModList;

/**
 * Loader-side gate for the SlashBlade integration. Deliberately free of any
 * SlashBlade class reference so it is safe to classload unconditionally; the
 * heavy bridge is only touched once {@link #LOADED} is true.
 */
public final class SlashBladeModState {

    public static final boolean LOADED = ModList.get().isLoaded("slashblade");

    private SlashBladeModState() {
    }
}
