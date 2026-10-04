package dev.coreviewer.ui;

import com.terraformersmc.modmenu.api.*;

public final class CoreTraceModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return CoreTraceConfigScreen::create;
    }
}
