package org.twoptwot.voice.ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.VoiceConfig;
import org.twoptwot.voice.audio.VoiceController;
import org.twoptwot.voice.net.SignalingClient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class VoiceHud {

    private final VoiceController controller;
    private final SignalingClient signaling;
    private float pulse;

    private int mainX, mainY, mainW, mainH;
    private int speakX, speakY, speakW, speakH;
    private boolean speakVisible;

    private static final class SpeakerRow {
        final String name;
        final String uuid;

        SpeakerRow(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }

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
        List<SpeakerRow> rows = new ArrayList<>();
        if (TwoptwotVoiceClient.get().webRtc().isLocalSpeaking() && !controller.isDeafened()) {
            String self = controller.getName();
            if (self == null || self.isBlank()) {
                self = profileName(mc.player != null ? mc.player.getGameProfile() : null);
                if (self == null || self.isBlank()) {
                    self = "You";
                }
            }
            String selfUuid = "";
            if (mc.player != null) {
                selfUuid = mc.player.getUUID().toString();
            } else if (controller.getUuid() != null) {
                selfUuid = controller.getUuid();
            }
            rows.add(new SpeakerRow(self, selfUuid));
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
            boolean exists = false;
            for (SpeakerRow row : rows) {
                if (row.name.equalsIgnoreCase(name)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                rows.add(new SpeakerRow(name, peer.uuid));
            }
            if (rows.size() >= 6) {
                break;
            }
        }
        if (rows.isEmpty() && !editor) {
            speakVisible = false;
            return;
        }
        if (rows.isEmpty()) {
            rows.add(new SpeakerRow("(nobody)", ""));
        }

        int rowH = 14;
        int pad = 5;
        int head = 10;
        int maxNameW = 0;
        for (SpeakerRow row : rows) {
            maxNameW = Math.max(maxNameW, mc.font.width(row.name));
        }
        String header = "Speaking";
        speakW = Math.max(mc.font.width(header) + 8, maxNameW + head + 10) + pad * 2;
        speakH = pad + 12 + rows.size() * rowH + 3;

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

        graphics.fill(x, y, x + w, y + h, 0xF012161C);
        graphics.fill(x, y, x + w, y + 1, VoiceUi.SPEAK);
        graphics.fill(x, y + h - 1, x + w, y + h, VoiceUi.BORDER_SOFT);
        graphics.fill(x, y, x + 2, y + h, VoiceUi.SPEAK);
        int pulseBar = (VoiceUi.pulseAlpha(pulse) & 0xFF000000) | (VoiceUi.SPEAK & 0x00FFFFFF);
        graphics.fill(x + 2, y, x + 3, y + h, pulseBar);
        if (editor) {
            graphics.fill(x, y, x + w, y + 1, VoiceUi.GOLD);
            graphics.fill(x, y + h - 1, x + w, y + h, VoiceUi.GOLD);
        }

        graphics.drawString(mc.font, header, x + pad + 4, y + 4, VoiceUi.TEXT_DIM, false);
        int ty = y + 15;
        for (SpeakerRow row : rows) {
            drawHead(graphics, mc, row.uuid, x + pad, ty - 1);
            graphics.drawString(mc.font, row.name, x + pad + head + 4, ty, VoiceUi.TEXT, false);
            ty += rowH;
        }
    }

    private static void drawHead(GuiGraphics graphics, Minecraft mc, String uuidStr, int x, int y) {
        Identifier skin = resolveSkin(mc, uuidStr);
        if (tryPlayerFaceRenderer(graphics, skin, x, y, 8)) {
            return;
        }
        try {
            graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 8.0f, 8.0f, 8, 8, 64, 64);
            graphics.blit(RenderPipelines.GUI_TEXTURED, skin, x, y, 40.0f, 8.0f, 8, 8, 64, 64);
        } catch (Throwable t) {
            graphics.fill(x, y, x + 8, y + 8, VoiceUi.ACCENT);
        }
    }

    private static boolean tryPlayerFaceRenderer(GuiGraphics graphics, Identifier skin, int x, int y, int size) {
        try {
            Class<?> clazz = Class.forName("net.minecraft.client.gui.components.PlayerFaceRenderer");
            try {
                clazz.getMethod("draw", GuiGraphics.class, Identifier.class, int.class, int.class, int.class)
                        .invoke(null, graphics, skin, x, y, size);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
            try {
                clazz.getMethod("draw", GuiGraphics.class, Identifier.class, int.class, int.class, int.class, int.class)
                        .invoke(null, graphics, skin, x, y, size, -1);
                return true;
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Identifier resolveSkin(Minecraft mc, String uuidStr) {
        UUID uuid = parseUuid(uuidStr);
        if (uuid == null && mc.player != null) {
            uuid = mc.player.getUUID();
        }

        if (mc.player != null && uuid != null && uuid.equals(mc.player.getUUID())) {
            Identifier local = textureFromSkinObject(safeGetSkin(mc.player));
            if (local != null && !isDefaultStevePath(local)) {
                return local;
            }
            if (local != null) {
                return local;
            }
        }

        if (uuid != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                Identifier fromInfo = textureFromSkinObject(safeGetSkin(info));
                if (fromInfo != null && !isDefaultStevePath(fromInfo)) {
                    return fromInfo;
                }
                if (fromInfo != null) {
                    return fromInfo;
                }
            }
        }

        if (mc.player != null && (uuid == null || uuid.equals(mc.player.getUUID()))) {
            Identifier local = textureFromSkinObject(safeGetSkin(mc.player));
            if (local != null) {
                return local;
            }
        }

        if (uuid != null) {
            try {
                Object skin = DefaultPlayerSkin.class.getMethod("get", UUID.class).invoke(null, uuid);
                Identifier fromDefault = textureFromSkinObject(skin);
                if (fromDefault != null) {
                    return fromDefault;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Object tex = DefaultPlayerSkin.class.getMethod("getDefaultTexture").invoke(null);
            Identifier rl = asIdentifier(tex);
            if (rl != null) {
                return rl;
            }
        } catch (Throwable ignored) {
        }
        return Identifier.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");
    }

    private static Object safeGetSkin(Object target) {
        if (target == null) {
            return null;
        }
        Object skin = invokeNoArg(target, "getSkin");
        if (skin != null) {
            return skin;
        }
        return invokeNoArg(target, "getSkinLocation");
    }

    private static boolean isDefaultStevePath(Identifier id) {
        if (id == null) {
            return true;
        }
        String path = id.getPath();
        return path != null && (path.contains("/steve") || path.endsWith("steve.png"));
    }

    private static String profileName(Object profile) {
        if (profile == null) {
            return null;
        }
        for (String method : new String[]{"name", "getName"}) {
            try {
                Object n = profile.getClass().getMethod(method).invoke(profile);
                if (n != null) {
                    String s = n.toString();
                    if (!s.isBlank()) {
                        return s;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static UUID parseUuid(String uuidStr) {
        if (uuidStr == null || uuidStr.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Identifier textureFromSkinObject(Object skin) {
        if (skin == null) {
            return null;
        }
        try {
            Object body = skin.getClass().getMethod("body").invoke(skin);
            Object path = body.getClass().getMethod("texturePath").invoke(body);
            Identifier id = asIdentifier(path);
            if (id != null) {
                return id;
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Class<?> iface : skin.getClass().getInterfaces()) {
                // no-op; fall through
            }
            Object body = skin.getClass().getMethod("body").invoke(skin);
            for (Class<?> iface : body.getClass().getInterfaces()) {
                try {
                    Object path = iface.getMethod("texturePath").invoke(body);
                    Identifier id = asIdentifier(path);
                    if (id != null) {
                        return id;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return asIdentifier(invokeNoArg(skin, "texture"));
    }

    private static Object invokeNoArg(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Identifier asIdentifier(Object value) {
        return value instanceof Identifier id ? id : null;
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
