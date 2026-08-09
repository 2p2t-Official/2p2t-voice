package org.twoptwot.voice.ui.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.ui.VoiceButton;
import org.twoptwot.voice.ui.VoiceUi;
import org.twoptwot.voice.update.ModUpdater;

import java.util.ArrayList;
import java.util.List;

public final class TwopJoinMultiplayerScreen extends JoinMultiplayerScreen {

    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 62;

    private float ticks;
    private int pinCooldown;

    public TwopJoinMultiplayerScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void init() {
        ServerDirectory.ensureOfficialSaved(minecraft);
        super.init();
        pinOfficial(true);
        hideVanillaTitle();
        int joinW = 120;
        addRenderableWidget(new VoiceButton(
                width - joinW - 12,
                Math.max(6, (HEADER_H - 20) / 2),
                joinW,
                20,
                Component.literal("Join 2p2t.org"),
                VoiceButton.Style.PRIMARY,
                b -> ServerDirectory.connectOfficial(this)));
    }

    @Override
    public void tick() {
        super.tick();
        ticks += 1.0f;
        if (pinCooldown > 0) {
            pinCooldown--;
        } else {
            pinOfficial(false);
            pinCooldown = 20;
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MenuChrome.drawServerListChrome(graphics, font, width, height, HEADER_H, FOOTER_H, ticks + partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (ModUpdater.isUpdateAvailable()) {
            graphics.drawCenteredString(
                    font,
                    Component.literal("Update " + ModUpdater.latestTag() + " available"),
                    width / 2,
                    height - 12,
                    VoiceUi.WARN);
        }
    }

    private void pinOfficial(boolean forceRefresh) {
        if (getServers() == null) {
            return;
        }
        boolean dirty = ServerDirectory.ensureOfficialEntry(getServers());
        if ((dirty || forceRefresh) && serverSelectionList != null) {
            if (dirty) {
                getServers().save();
            }
            serverSelectionList.updateOnlineServers(getServers());
        }
    }

    private void hideVanillaTitle() {
        List<AbstractWidget> remove = new ArrayList<>();
        for (var child : children()) {
            if (child instanceof StringWidget widget) {
                remove.add(widget);
            }
        }
        for (AbstractWidget widget : remove) {
            removeWidget(widget);
        }
    }
}
