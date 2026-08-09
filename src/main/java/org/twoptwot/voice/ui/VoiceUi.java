package org.twoptwot.voice.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class VoiceUi {

    public static final int BG_VOID = 0xCC0C1016;
    public static final int BG_SHELL = 0xEB12161C;
    public static final int BG_SIDE = 0xF00E1218;
    public static final int BG_PANEL = 0xF5141A22;
    public static final int BG_ROW = 0xE0141A22;
    public static final int BG_ROW_HOT = 0xE01A2838;
    public static final int BG_CHIP = 0xF0141A22;
    public static final int BORDER = 0x1AFFFFFF;
    public static final int BORDER_SOFT = 0xFF1A222C;
    public static final int LINE = 0x19FFFFFF;
    public static final int GOLD = 0xFF6B9FD4;
    public static final int ACCENT = 0xFF3D6A9E;
    public static final int ACCENT_DIM = 0xFF2A4A6E;
    public static final int ACCENT_BRIGHT = 0xFF5088BF;
    public static final int ACCENT_GLOW = 0x336B9FD4;
    public static final int TEXT = 0xFFE8EAED;
    public static final int TEXT_DIM = 0xFF8A939E;
    public static final int TEXT_FAINT = 0xFF6A737E;
    public static final int DANGER = 0xFFC45A6A;
    public static final int WARN = 0xFFFFC857;
    public static final int SPEAK = 0xFF4ADE80;
    public static final int MUTED = 0xFFC45A6A;

    private VoiceUi() {
    }

    public static void dimWorld(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, BG_VOID);
    }

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG_SHELL);
        g.fill(x, y, x + w, y + 1, LINE);
        g.fill(x, y + h - 1, x + w, y + h, BORDER_SOFT);
        g.fill(x, y, x + 1, y + h, LINE);
        g.fill(x + w - 1, y, x + w, y + h, BORDER_SOFT);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, ACCENT_GLOW);
    }

    public static void sidebar(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BG_SIDE);
        g.fill(x + w - 1, y, x + w, y + h, BORDER_SOFT);
    }

    public static void row(GuiGraphics g, int x, int y, int w, int h, boolean hot, boolean speaking) {
        g.fill(x, y, x + w, y + h, hot ? BG_ROW_HOT : BG_ROW);
        if (speaking) {
            g.fill(x, y, x + 3, y + h, SPEAK);
            g.fill(x + 3, y, x + 4, y + h, ACCENT_GLOW);
        } else {
            g.fill(x, y, x + 2, y + h, BORDER_SOFT);
        }
    }

    public static void chip(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, LINE);
        g.fill(x, y + h - 1, x + w, y + h, BORDER_SOFT);
    }

    public static void accentBar(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 2, GOLD);
        g.fill(x, y + 2, x + w, y + 3, ACCENT);
    }

    public static void statusDot(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 6, y + 6, color);
        g.fill(x + 1, y + 1, x + 5, y + 5, color | 0xFF000000);
    }

    public static void label(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public static void label(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public static String channelTitle(String channel) {
        if (channel == null || channel.isBlank()) {
            return "Voice";
        }
        return switch (channel) {
            case "global" -> "Server-Wide";
            case "proximity" -> "Proximity";
            case "spawn" -> "Spawn";
            case "staff" -> "Staff";
            case "lobby" -> "Lobby";
            default -> {
                if (channel.startsWith("group:")) {
                    String id = channel.substring("group:".length());
                    try {
                        var groups = org.twoptwot.voice.TwoptwotVoiceClient.get().signaling().groups();
                        for (var group : groups) {
                            if (id.equals(group.id) && group.name != null && !group.name.isBlank()) {
                                yield group.name;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    yield "Group";
                }
                yield channel;
            }
        };
    }

    public static int pulseAlpha(float t) {
        float wave = 0.55f + 0.45f * Mth.sin(t * 4.2f);
        int a = Mth.clamp((int) (wave * 255f), 80, 255);
        return (a << 24);
    }
}
