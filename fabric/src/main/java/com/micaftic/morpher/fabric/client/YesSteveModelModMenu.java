package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.client.gui.ExtraPlayerConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class YesSteveModelModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ExtraPlayerConfigScreen::new;
    }
}
