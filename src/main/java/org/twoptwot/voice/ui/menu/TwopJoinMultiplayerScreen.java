package org.twoptwot.voice.ui.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.ui.VoiceUi;
import org.twoptwot.voice.update.ModUpdater;

public final class TwopJoinMultiplayerScreen extends JoinMultiplayerScreen {

    private float ticks;

    public TwopJoinMultiplayerScreen(Screen parent) {
        super(parent);
    }

    @Override
    protected void init() {
        ServerDirectory.ensureOfficialSaved(minecraft);
        super.init();
        if (getServers() != null && ServerDirectory.ensureOfficialEntry(getServers())) {
            getServers().save();
            if (serverSelectionList != null) {
                serverSelectionList.updateOnlineServers(getServers());
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        ticks += 1.0f;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MenuChrome.drawBackdrop(graphics, width, height, ticks + partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, "2p2t", 8, 8, VoiceUi.GOLD, false);
        graphics.drawString(font, "Official server is pinned at the top", 8, 18, VoiceUi.TEXT_DIM, false);
        if (ModUpdater.isUpdateAvailable()) {
            graphics.drawCenteredString(
                    font,
                    Component.literal("Update " + ModUpdater.latestTag() + " available"),
                    width / 2,
                    height - 14,
                    VoiceUi.WARN);
        }
    }
}
