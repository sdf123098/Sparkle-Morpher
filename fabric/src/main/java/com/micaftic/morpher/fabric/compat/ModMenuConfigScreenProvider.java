package com.micaftic.morpher.fabric.compat;

import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuConfigScreenProvider implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parentScreen -> ModernPlayerModelScreen.settings();
    }
}
