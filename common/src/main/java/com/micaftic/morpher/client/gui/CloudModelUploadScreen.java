package com.micaftic.morpher.client.gui;

import net.minecraft.client.gui.screens.Screen;
import java.util.Collection;

/** Compatibility entry point for the unified Cloud upload workspace. */
public final class CloudModelUploadScreen extends CloudUploadWorkspaceScreen {
    public CloudModelUploadScreen(Screen parent, Collection<String> modelIds) {
        super(parent, modelIds);
    }
}