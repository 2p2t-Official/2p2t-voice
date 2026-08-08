package org.twoptwot.voice.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.audio.VoiceController;
import org.twoptwot.voice.net.SignalingClient;

public final class PeerRowWidget extends AbstractWidget {

    private final SignalingClient.PeerInfo peer;
    private final VoiceController controller;
    private final Runnable onChanged;
    private float anim;

    public PeerRowWidget(int x, int y, int w, int h, SignalingClient.PeerInfo peer,
                         VoiceController controller, Runnable onChanged) {
        super(x, y, w, h, Component.literal(peer.name == null ? "?" : peer.name));
        this.peer = peer;
        this.controller = controller;
        this.onChanged = onChanged;
    }

    public void tickAnim(float t) {
        this.anim = t;
    }

    public SignalingClient.PeerInfo peer() {
        return peer;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int localX = (int) event.x() - getX();
        int blockW = 52;
        int volW = 44;
        if (localX >= width - blockW) {
            peer.blocked = !peer.blocked;
            TwoptwotVoiceClient.get().webRtc().applyPeerVolume(peer.uuid, peer.effectiveVolume(controller));
            onChanged.run();
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            return;
        }
        if (localX >= width - blockW - volW) {
            
            float lv = peer.localVolume;
            if (lv >= 1.4f) {
                peer.localVolume = 1f;
            } else if (lv >= 0.9f) {
                peer.localVolume = 0.5f;
            } else if (lv >= 0.25f) {
                peer.localVolume = 0f;
            } else {
                peer.localVolume = 1.5f;
            }
            TwoptwotVoiceClient.get().webRtc().applyPeerVolume(peer.uuid, peer.effectiveVolume(controller));
            onChanged.run();
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = isHoveredOrFocused();
        boolean speaking = peer.speaking && !peer.blocked;
        VoiceUi.row(g, getX(), getY(), width, height, hot, speaking);
        if (speaking) {
            int glow = VoiceUi.pulseAlpha(anim) | (VoiceUi.SPEAK & 0x00FFFFFF);
            g.fill(getX() + 3, getY(), getX() + 4, getY() + height, glow);
        }

        var font = net.minecraft.client.Minecraft.getInstance().font;
        String name = peer.name == null || peer.name.isBlank() ? "Unknown" : peer.name;
        int nameColor = peer.blocked ? VoiceUi.TEXT_FAINT : (speaking ? VoiceUi.SPEAK : VoiceUi.TEXT);
        g.drawString(font, name, getX() + 10, getY() + 5, nameColor, false);

        StringBuilder flags = new StringBuilder();
        if (peer.selfMuted || peer.muted) {
            flags.append("MUTE  ");
        }
        if (peer.serverMuted) {
            flags.append("SMUTE  ");
        }
        if (peer.deafened) {
            flags.append("DEAF  ");
        }
        if (peer.blocked) {
            flags.append("BLOCKED  ");
        }
        if (peer.channel != null && !peer.channel.equals(controller.getChannel())) {
            flags.append(VoiceUi.channelTitle(peer.channel));
        }
        if (!flags.isEmpty()) {
            g.drawString(font, flags.toString().trim(), getX() + 10, getY() + 16, VoiceUi.TEXT_FAINT, false);
        }

        int blockW = 52;
        int volW = 44;
        int volX = getX() + width - blockW - volW;
        int blockX = getX() + width - blockW;

        int volPct = Math.round(peer.localVolume * 100f);
        String volLabel = volPct + "%";
        g.fill(volX + 2, getY() + 4, volX + volW - 2, getY() + height - 4, VoiceUi.BG_CHIP);
        g.drawCenteredString(font, volLabel, volX + volW / 2, getY() + (height - 8) / 2, VoiceUi.TEXT_DIM);

        boolean blockHot = hot && mouseX >= blockX;
        g.fill(blockX + 2, getY() + 4, getX() + width - 2, getY() + height - 4,
                peer.blocked ? 0xE0281820 : (blockHot ? VoiceUi.BG_ROW_HOT : VoiceUi.BG_CHIP));
        g.drawCenteredString(font, peer.blocked ? "Unblock" : "Block",
                blockX + blockW / 2, getY() + (height - 8) / 2,
                peer.blocked ? VoiceUi.ACCENT : VoiceUi.DANGER);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
