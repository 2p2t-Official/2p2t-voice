package org.twoptwot.voice.ui.menu;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.VoiceConfig;
import org.twoptwot.voice.ui.VoiceButton;

public final class MenuScreens {

    private MenuScreens() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.screen == null) {
                return;
            }
            if (!brandedMenusEnabled()) {
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

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof OptionsScreen)) {
                return;
            }
            VoiceConfig config = TwoptwotVoiceClient.get().config();
            int bw = 160;
            int bh = 20;
            int x = scaledWidth / 2 - bw / 2;
            int y = scaledHeight - 28;
            Screens.getButtons(screen).add(new VoiceButton(
                    x, y, bw, bh,
                    brandingLabel(config.brandedMenus),
                    config.brandedMenus ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST,
                    b -> {
                        config.brandedMenus = !config.brandedMenus;
                        config.save();
                        b.setMessage(brandingLabel(config.brandedMenus));
                        b.setStyle(config.brandedMenus ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST);
                    }));
        });
    }

    public static boolean brandedMenusEnabled() {
        try {
            return TwoptwotVoiceClient.get().config().brandedMenus;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Component brandingLabel(boolean on) {
        return Component.literal(on ? "2p2t Menus: ON" : "2p2t Menus: OFF");
    }
}
