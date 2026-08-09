package org.twoptwot.voice.ui.menu;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.VoiceConfig;
import org.twoptwot.voice.ui.VoiceButton;

import java.lang.reflect.Method;
import java.util.List;

public final class MenuBootstrap {

    private MenuBootstrap() {
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
            int bw = 120;
            int bh = 20;
            int x = scaledWidth - bw - 6;
            int y = 6;
            List<AbstractWidget> existing = tryGetButtons(screen);
            if (existing != null) {
                int[] spot = findFreeSpot(existing, scaledWidth, scaledHeight, bw, bh);
                x = spot[0];
                y = spot[1];
            }
            addButton(screen, new VoiceButton(
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

    private static int[] findFreeSpot(List<AbstractWidget> existing, int sw, int sh, int bw, int bh) {
        int[][] candidates = {
                {sw - bw - 6, 6},
                {6, 6},
                {sw - bw - 6, 28},
                {6, 28},
                {sw / 2 - bw / 2, 6}
        };
        for (int[] c : candidates) {
            if (!overlapsAny(existing, c[0], c[1], bw, bh)) {
                return c;
            }
        }
        return new int[]{sw - bw - 6, 6};
    }

    private static boolean overlapsAny(List<AbstractWidget> existing, int x, int y, int w, int h) {
        int x2 = x + w;
        int y2 = y + h;
        for (AbstractWidget widget : existing) {
            if (widget == null) {
                continue;
            }
            int wx = widget.getX();
            int wy = widget.getY();
            int wx2 = wx + widget.getWidth();
            int wy2 = wy + widget.getHeight();
            if (x < wx2 && x2 > wx && y < wy2 && y2 > wy) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<AbstractWidget> tryGetButtons(Screen screen) {
        try {
            Class<?> screens = Class.forName("net.fabricmc.fabric.api.client.screen.v1.Screens");
            Method getButtons = screens.getMethod("getButtons", Screen.class);
            return (List<AbstractWidget>) getButtons.invoke(null, screen);
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void addButton(Screen screen, AbstractWidget button) {
        List<AbstractWidget> buttons = tryGetButtons(screen);
        if (buttons != null) {
            buttons.add(button);
            return;
        }
        try {
            Method add = Screen.class.getDeclaredMethod("addRenderableWidget",
                    net.minecraft.client.gui.components.events.GuiEventListener.class);
            add.setAccessible(true);
            add.invoke(screen, button);
        } catch (Throwable ignored) {
        }
    }
}
