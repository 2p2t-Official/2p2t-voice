package org.twoptwot.voice.ui.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.ui.VoiceButton;
import org.twoptwot.voice.ui.VoiceSettingsScreen;
import org.twoptwot.voice.ui.VoiceUi;
import org.twoptwot.voice.update.ModUpdater;

public final class TwopTitleScreen extends Screen {

    private float ticks;

    public TwopTitleScreen() {
        super(Component.literal("2p2t"));
    }

    @Override
    protected void init() {
        clearWidgets();
        ServerDirectory.ensureOfficialSaved(minecraft);
        if (ModUpdater.statusLine() == null || ModUpdater.statusLine().isBlank()
                || "Idle".equals(ModUpdater.statusLine())) {
            ModUpdater.checkAsync(false);
        }

        int cx = width / 2;
        int logoSize = Math.min(96, Math.max(64, height / 6));
        int top = Math.max(24, height / 10);
        int y = top + logoSize + 36;
        int bw = Math.min(220, width - 40);
        int bh = 22;
        int gap = 6;
        int x = cx - bw / 2;

        addRenderableWidget(new VoiceButton(
                x, y, bw, bh,
                Component.literal("Join 2p2t.org"),
                VoiceButton.Style.PRIMARY,
                b -> ServerDirectory.connectOfficial(this)));
        y += bh + gap + 4;

        addRenderableWidget(new VoiceButton(
                x, y, bw, bh,
                Component.translatable("menu.singleplayer"),
                VoiceButton.Style.GHOST,
                b -> minecraft.setScreen(new SelectWorldScreen(this))));
        y += bh + gap;

        addRenderableWidget(new VoiceButton(
                x, y, bw, bh,
                Component.translatable("menu.multiplayer"),
                VoiceButton.Style.GHOST,
                b -> minecraft.setScreen(new TwopMultiplayerScreen(this))));
        y += bh + gap;

        int half = (bw - gap) / 2;
        addRenderableWidget(new VoiceButton(
                x, y, half, bh,
                Component.translatable("menu.options"),
                VoiceButton.Style.QUIET,
                b -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new VoiceButton(
                x + half + gap, y, half, bh,
                Component.literal("Voice"),
                VoiceButton.Style.QUIET,
                b -> minecraft.setScreen(new VoiceSettingsScreen(this))));
        y += bh + gap + 4;

        addRenderableWidget(new VoiceButton(
                x, y, bw, bh,
                Component.translatable("menu.quit"),
                VoiceButton.Style.DANGER,
                b -> minecraft.stop()));
    }

    @Override
    public void tick() {
        ticks += 1.0f;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MenuChrome.drawBackdrop(graphics, width, height, ticks + partialTick);
        int logoSize = Math.min(96, Math.max(64, height / 6));
        int top = Math.max(24, height / 10);
        MenuChrome.drawLogo(graphics, width / 2, top, logoSize);
        MenuChrome.drawBrandTitle(graphics, font, width / 2, top + logoSize + 8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (ModUpdater.isUpdateAvailable()) {
            graphics.drawCenteredString(
                    font,
                    Component.literal("Update available: " + ModUpdater.latestTag() + " — open Voice settings"),
                    width / 2,
                    height - 28,
                    VoiceUi.WARN);
        }
        MenuChrome.drawFooter(graphics, font, width, height, ModUpdater.installedVersion());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
