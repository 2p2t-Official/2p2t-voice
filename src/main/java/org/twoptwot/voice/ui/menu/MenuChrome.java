package org.twoptwot.voice.ui.menu;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.ui.VoiceUi;

public final class MenuChrome {

    public static final Identifier LOGO =
            Identifier.fromNamespaceAndPath(TwoptwotVoiceClient.MOD_ID, "textures/gui/logo.png");
    public static final Identifier MENU_BG =
            Identifier.fromNamespaceAndPath(TwoptwotVoiceClient.MOD_ID, "textures/gui/menu_bg.png");

    public static final String SITE_HOST = "2p2t.org";
    public static final String VOICE_HOST = "voice.2p2t.org";

    private MenuChrome() {
    }

    public static void drawBackdrop(GuiGraphics graphics, int width, int height, float ticks) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, MENU_BG, 0, 0, 0.0f, 0.0f, width, height, width, height);
        graphics.fill(0, 0, width, height, 0xB0080C14);
        int pulse = VoiceUi.pulseAlpha(ticks * 0.035f);
        int glow = (pulse << 24) | 0x002A4A6E;
        graphics.fill(0, 0, width, height / 3, glow);
        graphics.fill(0, 0, width, 2, VoiceUi.GOLD);
        graphics.fill(0, height - 2, width, height, VoiceUi.ACCENT_DIM);
    }

    public static void drawLogo(GuiGraphics graphics, int centerX, int topY, int size) {
        int x = centerX - size / 2;
        int pad = Mth.clamp(size / 10, 4, 16);
        graphics.fill(x - pad, topY - pad, x + size + pad, topY + size + pad, 0x66080C14);
        graphics.fill(x - pad, topY - pad, x + size + pad, topY - pad + 1, VoiceUi.GOLD);
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, topY, 0.0f, 0.0f, size, size, size, size);
    }

    public static void drawBrandTitle(GuiGraphics graphics, Font font, int centerX, int y) {
        Component title = Component.literal("2p2t");
        graphics.drawCenteredString(font, title, centerX, y, VoiceUi.GOLD);
        graphics.drawCenteredString(font, Component.literal("Voice · Official Client"), centerX, y + 12, VoiceUi.TEXT_DIM);
    }

    public static void drawFooter(GuiGraphics graphics, Font font, int width, int height, String status) {
        String left = SITE_HOST + "  ·  " + VOICE_HOST;
        graphics.drawString(font, left, 8, height - 12, VoiceUi.TEXT_FAINT, false);
        if (status != null && !status.isBlank()) {
            int sw = font.width(status);
            graphics.drawString(font, status, width - sw - 8, height - 12, VoiceUi.TEXT_DIM, false);
        }
    }
}
