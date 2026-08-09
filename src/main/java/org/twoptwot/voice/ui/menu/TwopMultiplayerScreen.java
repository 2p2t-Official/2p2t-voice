package org.twoptwot.voice.ui.menu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.ui.VoiceButton;
import org.twoptwot.voice.ui.VoiceSettingsScreen;
import org.twoptwot.voice.ui.VoiceUi;
import org.twoptwot.voice.update.ModUpdater;

public final class TwopMultiplayerScreen extends Screen {

    private final Screen parent;
    private float ticks;
    private ServerData directData;

    public TwopMultiplayerScreen(Screen parent) {
        super(Component.literal("2p2t Multiplayer"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        ServerDirectory.ensureOfficialSaved(minecraft);

        int cx = width / 2;
        int logoSize = Math.min(80, Math.max(56, height / 7));
        int top = Math.max(18, height / 12);
        int y = top + logoSize + 34;
        int bw = Math.min(240, width - 40);
        int bh = 22;
        int gap = 6;
        int x = cx - bw / 2;

        addRenderableWidget(new VoiceButton(
                x, y, bw, bh,
                Component.literal("Join 2p2t.org"),
                VoiceButton.Style.PRIMARY,
                b -> ServerDirectory.connectOfficial(this)));
        y += bh + gap + 2;

        addRenderableWidget(new VoiceButton(
                x, y, bw, bh,
                Component.literal("Server List"),
                VoiceButton.Style.GHOST,
                b -> minecraft.setScreen(new TwopJoinMultiplayerScreen(this))));
        y += bh + gap;

        addRenderableWidget(new VoiceButton(
                x, y, bw, bh,
                Component.translatable("selectServer.direct"),
                VoiceButton.Style.GHOST,
                b -> {
                    directData = new ServerData(ServerDirectory.OFFICIAL_NAME, "", ServerData.Type.OTHER);
                    minecraft.setScreen(new DirectJoinServerScreen(this, accepted -> {
                        if (accepted && directData != null && directData.ip != null && !directData.ip.isBlank()) {
                            minecraft.options.lastMpIp = directData.ip;
                            minecraft.options.save();
                            ServerDirectory.connect(this, directData.name, directData.ip);
                        } else {
                            minecraft.setScreen(this);
                        }
                    }, directData));
                }));
        y += bh + gap;

        int half = (bw - gap) / 2;
        addRenderableWidget(new VoiceButton(
                x, y, half, bh,
                Component.literal("Voice"),
                VoiceButton.Style.QUIET,
                b -> minecraft.setScreen(new VoiceSettingsScreen(this))));
        addRenderableWidget(new VoiceButton(
                x + half + gap, y, half, bh,
                Component.translatable("gui.back"),
                VoiceButton.Style.QUIET,
                b -> minecraft.setScreen(parent instanceof TwopTitleScreen ? parent : new TwopTitleScreen())));
    }

    @Override
    public void tick() {
        ticks += 1.0f;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        MenuChrome.drawBackdrop(graphics, width, height, ticks + partialTick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int logoSize = Math.min(80, Math.max(56, height / 7));
        int top = Math.max(18, height / 12);
        MenuChrome.drawLogo(graphics, width / 2, top, logoSize);
        graphics.drawCenteredString(font, title, width / 2, top + logoSize + 8, VoiceUi.GOLD);
        graphics.drawCenteredString(
                font,
                Component.literal("Use Join 2p2t.org for voice. Other servers ignore this mod."),
                width / 2,
                top + logoSize + 20,
                VoiceUi.TEXT_DIM);

        for (var child : children()) {
            if (child instanceof net.minecraft.client.gui.components.Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        if (ModUpdater.isUpdateAvailable()) {
            graphics.drawCenteredString(
                    font,
                    Component.literal("Voice mod update available: " + ModUpdater.latestTag()),
                    width / 2,
                    height - 28,
                    VoiceUi.WARN);
        }
        MenuChrome.drawFooter(graphics, font, width, height, ModUpdater.statusLine());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent instanceof TwopTitleScreen ? parent : new TwopTitleScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
