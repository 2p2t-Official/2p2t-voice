package org.twoptwot.voice.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.net.SignalingClient;

public final class GroupInviteScreen extends Screen {

    private final Screen parent;
    private final SignalingClient.GroupInvite invite;

    public GroupInviteScreen(Screen parent, SignalingClient.GroupInvite invite) {
        super(Component.literal("Group Invite"));
        this.parent = parent;
        this.invite = invite;
    }

    @Override
    protected void init() {
        int w = 260;
        int h = 120;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        addRenderableWidget(new VoiceButton(
                x + 16, y + h - 36, 100, 22,
                Component.literal("Decline"),
                VoiceButton.Style.DANGER,
                b -> respond(false)));
        addRenderableWidget(new VoiceButton(
                x + w - 116, y + h - 36, 100, 22,
                Component.literal("Accept"),
                VoiceButton.Style.PRIMARY,
                b -> respond(true)));
    }

    private void respond(boolean accept) {
        TwoptwotVoiceClient.get().signaling().respondInvite(invite.inviteId, accept, () -> {
            if (minecraft != null) {
                minecraft.execute(() -> minecraft.setScreen(parent));
            }
        }, err -> {
            if (minecraft != null) {
                minecraft.execute(() -> {
                    TwoptwotVoiceClient.get().controller().setStatus("Invite failed: " + err);
                    minecraft.setScreen(parent);
                });
            }
        });
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VoiceUi.dimWorld(graphics, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int w = 260;
        int h = 120;
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        VoiceUi.panel(graphics, x, y, w, h);
        VoiceUi.accentBar(graphics, x + 1, y + 1, w - 2);
        graphics.drawString(font, "Group invite", x + 14, y + 12, VoiceUi.TEXT, false);
        graphics.drawString(font, invite.fromName + " invited you to", x + 14, y + 36, VoiceUi.TEXT_DIM, false);
        graphics.drawString(font, invite.groupName, x + 14, y + 50, VoiceUi.GOLD, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
