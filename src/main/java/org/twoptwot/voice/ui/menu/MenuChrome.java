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

    public static void drawServerListChrome(GuiGraphics graphics, Font font, int width, int height, int headerH, int footerH, float ticks) {
        drawBackdrop(graphics, width, height, ticks);
        graphics.fill(0, 0, width, headerH, 0xF00C1018);
        graphics.fill(0, headerH - 1, width, headerH, VoiceUi.GOLD);
        graphics.fill(0, height - footerH, width, height, 0xF00C1018);
        graphics.fill(0, height - footerH, width, height - footerH + 1, VoiceUi.ACCENT_DIM);

        int listTop = headerH + 4;
        int listBottom = height - footerH - 4;
        int listLeft = 12;
        int listRight = width - 12;
        if (listBottom > listTop + 8) {
            VoiceUi.panel(graphics, listLeft, listTop, listRight - listLeft, listBottom - listTop);
        }

        int logo = 22;
        int logoY = Math.max(4, (headerH - logo) / 2);
        drawLogo(graphics, 18 + logo / 2, logoY, logo);
        graphics.drawString(font, "2p2t Server List", 18 + logo + 10, headerH / 2 - 4, VoiceUi.GOLD, false);
        graphics.drawString(font, "Official server pinned at top", 18 + logo + 10, headerH / 2 + 6, VoiceUi.TEXT_DIM, false);
    }
}
