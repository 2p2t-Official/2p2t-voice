package org.twoptwot.voice.ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.VoiceConfig;
import org.twoptwot.voice.audio.VoiceController;
import org.twoptwot.voice.net.SignalingClient;

import java.util.ArrayList;
import java.util.List;

public final class VoiceHud {

    private final VoiceController controller;
    private final SignalingClient signaling;
    private float pulse;

    
    private int mainX, mainY, mainW, mainH;
    private int speakX, speakY, speakW, speakH;
    private boolean speakVisible;

    public VoiceHud(VoiceController controller, SignalingClient signaling) {
        this.controller = controller;
        this.signaling = signaling;
    }

    public int mainX() { return mainX; }
    public int mainY() { return mainY; }
    public int mainW() { return mainW; }
    public int mainH() { return mainH; }
    public int speakX() { return speakX; }
    public int speakY() { return speakY; }
    public int speakW() { return speakW; }
    public int speakH() { return speakH; }
    public boolean speakVisible() { return speakVisible; }

    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) {
            return;
        }
        
        boolean movePreview = mc.screen instanceof HudMoveScreen;
        if (!controller.config().hudEnabled && !movePreview) {
            return;
        }

        pulse += delta.getGameTimeDeltaPartialTick(false) * 0.15f;
        renderMain(graphics, mc, false);
        if (controller.config().isHudSpeaking() || movePreview) {
            renderSpeakingList(graphics, mc, movePreview);
        } else {
            speakVisible = false;
        }
    }

    
    public void renderForEditor(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        renderMain(graphics, mc, true);
        renderSpeakingList(graphics, mc, true);
    }

    private void renderMain(GuiGraphics graphics, Minecraft mc, boolean editor) {
        boolean on = controller.isConnected();
        boolean live = TwoptwotVoiceClient.get().webRtc().isLocalSpeaking();
        boolean muted = controller.isMuted() || controller.isServerMuted();
        boolean deaf = controller.isDeafened();
        boolean debug = controller.config().hudDebug;

        String channel = VoiceUi.channelTitle(controller.getChannel());
        String mic = muted ? "MUTED" : (deaf ? "DEAF" : (live ? "LIVE" : (
                controller.isTransmitting() ? "OPEN" : "IDLE")));

        String line1 = "2p2t  ·  " + channel + "  ·  " + mic;
        String line2 = null;
        if (debug) {
            String rtc = TwoptwotVoiceClient.get().webRtc().isAvailable()
                    ? ("RTC " + TwoptwotVoiceClient.get().webRtc().connectedPeerCount())
                    : "RTC off";
            String path = TwoptwotVoiceClient.get().webRtc().isAvailable()
                    ? TwoptwotVoiceClient.get().webRtc().pathSummary()
                    : "";
            line2 = (on ? "Connected" : "Offline") + "  ·  peers " + controller.getPeerCount() + "  ·  " + rtc;
            if (path != null && !path.isBlank() && !"path:off".equals(path)) {
                line2 += "  ·  " + path.replace("path:", "");
            }
        }

        int pad = 6;
        int textW = mc.font.width(line1);
        if (line2 != null) {
            textW = Math.max(textW, mc.font.width(line2));
        }
        mainW = textW + pad * 2 + 12;
        mainH = line2 == null ? 18 : 28;

        VoiceConfig cfg = controller.config();
        mainX = clamp(cfg.hudX, 0, Math.max(0, mc.getWindow().getGuiScaledWidth() - mainW));
        mainY = clamp(cfg.hudY, 0, Math.max(0, mc.getWindow().getGuiScaledHeight() - mainH));

        int x = mainX;
        int y = mainY;
        int w = mainW;
        int h = mainH;

        graphics.fill(x, y, x + w, y + h, VoiceUi.BG_SHELL);
        graphics.fill(x, y, x + w, y + 1, VoiceUi.BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, VoiceUi.BORDER_SOFT);
        graphics.fill(x, y, x + 2, y + h, on ? (live ? VoiceUi.SPEAK : VoiceUi.ACCENT) : VoiceUi.DANGER);
        if (live) {
            int glow = (VoiceUi.pulseAlpha(pulse) & 0xFF000000) | (VoiceUi.SPEAK & 0x00FFFFFF);
            graphics.fill(x + 2, y, x + 3, y + h, glow);
        }
        if (editor) {
            graphics.fill(x, y, x + w, y + 1, VoiceUi.GOLD);
            graphics.fill(x, y + h - 1, x + w, y + h, VoiceUi.GOLD);
        }

        int dotColor = on ? (live ? VoiceUi.SPEAK : VoiceUi.ACCENT) : VoiceUi.DANGER;
        VoiceUi.statusDot(graphics, x + pad + 2, y + (h - 6) / 2, dotColor);

        graphics.drawString(mc.font, line1, x + pad + 12, y + 5, VoiceUi.TEXT, false);
        if (line2 != null) {
            graphics.drawString(mc.font, line2, x + pad + 12, y + 15, VoiceUi.TEXT_FAINT, false);
        }
    }

    private void renderSpeakingList(GuiGraphics graphics, Minecraft mc, boolean editor) {
        List<String> names = new ArrayList<>();
        if (TwoptwotVoiceClient.get().webRtc().isLocalSpeaking() && !controller.isDeafened()) {
            String self = controller.getName();
            names.add(self == null || self.isBlank() ? "You" : self);
        }
        String myChannel = controller.getChannel();
        for (SignalingClient.PeerInfo peer : signaling.peers().values()) {
            if (peer == null || !peer.speaking || peer.blocked || peer.hidden || peer.deafened) {
                continue;
            }
            if (peer.channel != null && myChannel != null && !peer.channel.equals(myChannel)) {
                continue;
            }
            String name = peer.name == null || peer.name.isBlank() ? "Unknown" : peer.name;
            if (controller.getName() != null && controller.getName().equalsIgnoreCase(name)) {
                continue;
            }
            if (!names.contains(name)) {
                names.add(name);
            }
            if (names.size() >= 6) {
                break;
            }
        }
        if (names.isEmpty() && !editor) {
            speakVisible = false;
            return;
        }
        if (names.isEmpty()) {
            names.add("(nobody)");
        }

        int rowH = 12;
        int pad = 5;
        int maxNameW = 0;
        for (String name : names) {
            maxNameW = Math.max(maxNameW, mc.font.width(name));
        }
        String header = "Speaking";
        speakW = Math.max(mc.font.width(header), maxNameW) + pad * 2 + 10;
        speakH = pad + 10 + names.size() * rowH + 2;

        VoiceConfig cfg = controller.config();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        if (cfg.speakingHudX < 0 || cfg.speakingHudY < 0) {
            speakX = mainX;
            speakY = mainY + mainH + 3;
        } else {
            speakX = cfg.speakingHudX;
            speakY = cfg.speakingHudY;
        }
        speakX = clamp(speakX, 0, Math.max(0, screenW - speakW));
        speakY = clamp(speakY, 0, Math.max(0, screenH - speakH));
        speakVisible = true;

        int x = speakX;
        int y = speakY;
        int w = speakW;
        int h = speakH;

        graphics.fill(x, y, x + w, y + h, VoiceUi.BG_SHELL);
        graphics.fill(x, y, x + w, y + 1, VoiceUi.BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, VoiceUi.BORDER_SOFT);
        graphics.fill(x, y, x + 2, y + h, VoiceUi.SPEAK);
        if (editor) {
            graphics.fill(x, y, x + w, y + 1, VoiceUi.SPEAK);
            graphics.fill(x, y + h - 1, x + w, y + h, VoiceUi.SPEAK);
        }

        graphics.drawString(mc.font, header, x + pad + 8, y + 3, VoiceUi.TEXT_DIM, false);
        int ty = y + 13;
        for (String name : names) {
            VoiceUi.statusDot(graphics, x + pad, ty + 1, VoiceUi.SPEAK);
            graphics.drawString(mc.font, name, x + pad + 8, ty, VoiceUi.TEXT, false);
            ty += rowH;
        }
    }

    public boolean hitMain(double mx, double my) {
        return mx >= mainX && mx < mainX + mainW && my >= mainY && my < mainY + mainH;
    }

    public boolean hitSpeak(double mx, double my) {
        return speakVisible
                && mx >= speakX && mx < speakX + speakW
                && my >= speakY && my < speakY + speakH;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
