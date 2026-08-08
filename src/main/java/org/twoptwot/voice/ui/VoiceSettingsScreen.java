package org.twoptwot.voice.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.VoiceConfig;
import org.twoptwot.voice.audio.VoiceController;
import org.twoptwot.voice.update.ModUpdater;
import org.twoptwot.voice.webrtc.AudioDevices;

import java.util.List;

public final class VoiceSettingsScreen extends Screen {

    private final Screen parent;
    private final VoiceController controller = TwoptwotVoiceClient.get().controller();
    private final VoiceConfig config = controller.config();
    private List<AudioDevices.Device> inputs = AudioDevices.listInputs();
    private List<AudioDevices.Device> outputs = AudioDevices.listOutputs();

    private int panelX;
    private int panelY;
    private int panelW = 320;
    private int panelH = 520;

    public VoiceSettingsScreen(Screen parent) {
        super(Component.literal("Voice Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        inputs = AudioDevices.listInputs();
        outputs = AudioDevices.listOutputs();
        panelW = Math.min(340, width - 40);
        panelH = Math.min(520, height - 20);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int cx = panelX + 20;
        int cw = panelW - 40;
        int y = panelY + 36;

        addRenderableWidget(new VoiceButton(
                cx, y, cw, 20,
                Component.literal("Mic: " + ("ptt".equalsIgnoreCase(config.mode) ? "Push to Talk" : "Open Mic")),
                VoiceButton.Style.GHOST,
                b -> {
                    controller.setMode("ptt".equalsIgnoreCase(config.mode) ? "vad" : "ptt");
                    TwoptwotVoiceClient.get().webRtc().syncLocalMic();
                    b.setMessage(Component.literal(
                            "Mic: " + ("ptt".equalsIgnoreCase(config.mode) ? "Push to Talk" : "Open Mic")));
                }));
        y += 26;

        addRenderableWidget(new VoiceButton(
                cx, y, cw, 20,
                Component.literal("Input: " + AudioDevices.labelFor(inputs, config.inputDeviceId)),
                VoiceButton.Style.GHOST,
                b -> {
                    config.inputDeviceId = AudioDevices.cycleId(inputs, config.inputDeviceId);
                    config.save();
                    TwoptwotVoiceClient.get().webRtc().applyDeviceSettings();
                    b.setMessage(Component.literal("Input: " + AudioDevices.labelFor(inputs, config.inputDeviceId)));
                }));
        y += 24;

        addRenderableWidget(new VoiceButton(
                cx, y, cw, 20,
                Component.literal("Output: " + AudioDevices.labelFor(outputs, config.outputDeviceId)),
                VoiceButton.Style.GHOST,
                b -> {
                    config.outputDeviceId = AudioDevices.cycleId(outputs, config.outputDeviceId);
                    config.save();
                    TwoptwotVoiceClient.get().webRtc().applyDeviceSettings();
                    b.setMessage(Component.literal("Output: " + AudioDevices.labelFor(outputs, config.outputDeviceId)));
                }));
        y += 24;

        addRenderableWidget(new VoiceButton(
                cx, y, cw, 20,
                Component.literal("Noise Suppression: " + (config.noiseSuppression ? "ON" : "OFF")),
                config.noiseSuppression ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST,
                b -> {
                    config.noiseSuppression = !config.noiseSuppression;
                    config.save();
                    TwoptwotVoiceClient.get().webRtc().applyDeviceSettings();
                    b.setMessage(Component.literal("Noise Suppression: " + (config.noiseSuppression ? "ON" : "OFF")));
                    b.setStyle(config.noiseSuppression ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST);
                }));
        y += 28;

        addRenderableWidget(new VoiceSlider(cx, y, cw, 28, "Proximity",
                config.proximityRange, 4, 48, false,
                v -> controller.setProximityRange((int) Math.round(v))));
        y += 32;

        addRenderableWidget(new VoiceSlider(cx, y, cw, 28, "Master Volume",
                config.masterVolume, 0, 2, true,
                v -> controller.setMasterVolume((float) v)));
        y += 32;

        addRenderableWidget(new VoiceSlider(cx, y, cw, 28, "Mic Volume",
                config.micVolume, 0, 2, true,
                v -> {
                    config.micVolume = (float) v;
                    config.save();
                    TwoptwotVoiceClient.get().webRtc().syncLocalMic();
                }));
        y += 32;

        addRenderableWidget(new VoiceSlider(cx, y, cw, 28, "Mic Sensitivity",
                config.sensitivity01(), 0, 1, true,
                v -> {
                    config.setSensitivity01((float) v);
                    TwoptwotVoiceClient.get().webRtc().syncLocalMic();
                }));
        y += 32;

        addRenderableWidget(new VoiceButton(
                cx, y, cw / 2 - 4, 20,
                Component.literal("Tones: " + (config.pttTones ? "ON" : "OFF")),
                config.pttTones ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST,
                b -> {
                    config.pttTones = !config.pttTones;
                    config.save();
                    b.setMessage(Component.literal("Tones: " + (config.pttTones ? "ON" : "OFF")));
                    b.setStyle(config.pttTones ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST);
                }));
        addRenderableWidget(new VoiceButton(
                cx + cw / 2 + 4, y, cw / 2 - 4, 20,
                Component.literal("HUD: " + (config.hudEnabled ? "ON" : "OFF")),
                config.hudEnabled ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST,
                b -> {
                    config.hudEnabled = !config.hudEnabled;
                    config.save();
                    b.setMessage(Component.literal("HUD: " + (config.hudEnabled ? "ON" : "OFF")));
                    b.setStyle(config.hudEnabled ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST);
                }));
        y += 24;

        addRenderableWidget(new VoiceButton(
                cx, y, cw / 2 - 4, 20,
                Component.literal("Speaking: " + (config.isHudSpeaking() ? "ON" : "OFF")),
                config.isHudSpeaking() ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST,
                b -> {
                    config.hudSpeaking = !config.isHudSpeaking();
                    config.save();
                    b.setMessage(Component.literal("Speaking: " + (config.isHudSpeaking() ? "ON" : "OFF")));
                    b.setStyle(config.isHudSpeaking() ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST);
                }));
        addRenderableWidget(new VoiceButton(
                cx + cw / 2 + 4, y, cw / 2 - 4, 20,
                Component.literal("Debug: " + (config.hudDebug ? "ON" : "OFF")),
                config.hudDebug ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST,
                b -> {
                    config.hudDebug = !config.hudDebug;
                    config.save();
                    b.setMessage(Component.literal("Debug: " + (config.hudDebug ? "ON" : "OFF")));
                    b.setStyle(config.hudDebug ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST);
                }));
        y += 24;

        addRenderableWidget(new VoiceButton(
                cx, y, cw, 20,
                Component.literal("Move HUD…"),
                VoiceButton.Style.PRIMARY,
                b -> minecraft.setScreen(new HudMoveScreen(this))));
        y += 24;

        addRenderableWidget(new VoiceButton(
                cx, y, cw, 20,
                Component.literal("Auto-update: " + (config.autoUpdate ? "ON" : "OFF")),
                config.autoUpdate ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST,
                b -> {
                    config.autoUpdate = !config.autoUpdate;
                    config.save();
                    b.setMessage(Component.literal("Auto-update: " + (config.autoUpdate ? "ON" : "OFF")));
                    b.setStyle(config.autoUpdate ? VoiceButton.Style.PRIMARY : VoiceButton.Style.GHOST);
                }));
        y += 22;

        addRenderableWidget(new VoiceButton(
                cx, y, cw / 2 - 4, 20,
                Component.literal("Check updates"),
                VoiceButton.Style.GHOST,
                b -> ModUpdater.checkAsync(true)));
        addRenderableWidget(new VoiceButton(
                cx + cw / 2 + 4, y, cw / 2 - 4, 20,
                Component.literal("Update now"),
                VoiceButton.Style.PRIMARY,
                b -> {
                    if (ModUpdater.isUpdateAvailable()
                            || (ModUpdater.latestTag() != null && !ModUpdater.latestTag().isBlank())) {
                        ModUpdater.applyUpdateAsync(true);
                    } else {
                        ModUpdater.checkAsync(true);
                    }
                }));
        y += 26;

        addRenderableWidget(new VoiceButton(
                cx, y, cw / 2 - 4, 22,
                Component.literal("Back"),
                VoiceButton.Style.GHOST,
                b -> minecraft.setScreen(parent)));
        addRenderableWidget(new VoiceButton(
                cx + cw / 2 + 4, y, cw / 2 - 4, 22,
                Component.literal("Done"),
                VoiceButton.Style.PRIMARY,
                b -> minecraft.setScreen(parent)));

        addRenderableWidget(new VoiceButton(
                panelX + panelW - 22, panelY + 4, 16, 16,
                Component.literal("X"),
                VoiceButton.Style.QUIET,
                b -> minecraft.setScreen(parent)));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VoiceUi.dimWorld(graphics, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VoiceUi.panel(graphics, panelX, panelY, panelW, panelH);
        VoiceUi.accentBar(graphics, panelX + 1, panelY + 1, panelW - 2);
        graphics.drawString(font, "Voice Settings", panelX + 14, panelY + 10, VoiceUi.TEXT, false);

        int foot = panelY + panelH - 40;
        graphics.drawString(font, "Installed: " + ModUpdater.installedVersion()
                        + "  ·  MC " + ModUpdater.minecraftVersion(),
                panelX + 14, foot, VoiceUi.TEXT_FAINT, false);
        graphics.drawString(font, ModUpdater.statusLine(),
                panelX + 14, foot + 12, VoiceUi.TEXT_DIM, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
