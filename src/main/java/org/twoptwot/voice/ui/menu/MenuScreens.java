package org.twoptwot.voice.ui.menu;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;

public final class MenuScreens {

    private MenuScreens() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.screen == null) {
                return;
            }
            if (client.screen instanceof TwopTitleScreen
                    || client.screen instanceof TwopMultiplayerScreen
                    || client.screen instanceof TwopJoinMultiplayerScreen) {
                return;
            }
            if (client.screen instanceof TitleScreen) {
                client.setScreen(new TwopTitleScreen());
                return;
            }
            if (client.screen instanceof SafetyScreen) {
                client.setScreen(new TwopMultiplayerScreen(new TwopTitleScreen()));
                return;
            }
            if (client.screen.getClass() == JoinMultiplayerScreen.class) {
                client.setScreen(new TwopJoinMultiplayerScreen(new TwopMultiplayerScreen(new TwopTitleScreen())));
            }
        });
    }
}
