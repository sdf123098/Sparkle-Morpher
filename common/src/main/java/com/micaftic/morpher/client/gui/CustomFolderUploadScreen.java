package com.micaftic.morpher.client.gui;

import net.minecraft.client.gui.screens.Screen;
import java.util.List;

/** Compatibility entry point for the unified Cloud upload workspace. */
public class CustomFolderUploadScreen extends CloudUploadWorkspaceScreen {
    public CustomFolderUploadScreen(Screen parent) {
        super(parent, List.of());
    }
}