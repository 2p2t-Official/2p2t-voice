package org.twoptwot.voice.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.VoiceConfig;

public final class HudMoveScreen extends Screen {

    private final Screen parent;
    private final VoiceConfig config = TwoptwotVoiceClient.get().controller().config();
    private final VoiceHud hud;

    private enum DragTarget { NONE, MAIN, SPEAK }

    private DragTarget dragging = DragTarget.NONE;
    private double grabDx;
    private double grabDy;

    public HudMoveScreen(Screen parent) {
        super(Component.literal("Move Voice HUD"));
        this.parent = parent;
        this.hud = TwoptwotVoiceClient.get().hud();
    }

    @Override
    protected void init() {
        addRenderableWidget(new VoiceButton(
                width / 2 - 110, height - 28, 100, 20,
                Component.literal("Reset"),
                VoiceButton.Style.QUIET,
                b -> {
                    config.hudX = 4;
                    config.hudY = 4;
                    config.speakingHudX = -1;
                    config.speakingHudY = -1;
                    config.save();
                }));
        addRenderableWidget(new VoiceButton(
                width / 2 + 10, height - 28, 100, 20,
                Component.literal("Done"),
                VoiceButton.Style.PRIMARY,
                b -> {
                    config.save();
                    minecraft.setScreen(parent);
                }));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        VoiceUi.dimWorld(graphics, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.drawCenteredString(font, "Drag the HUD panels — Done to save",
                width / 2, 12, VoiceUi.TEXT);
        graphics.drawCenteredString(font, "Status chip  ·  Speaking list",
                width / 2, 24, VoiceUi.TEXT_DIM);

        if (hud != null) {
            hud.renderForEditor(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && hud != null) {
            
            if (hud.hitSpeak(event.x(), event.y())) {
                dragging = DragTarget.SPEAK;
                grabDx = event.x() - hud.speakX();
                grabDy = event.y() - hud.speakY();
                return true;
            }
            if (hud.hitMain(event.x(), event.y())) {
                dragging = DragTarget.MAIN;
                grabDx = event.x() - hud.mainX();
                grabDy = event.y() - hud.mainY();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != DragTarget.NONE && event.button() == 0 && hud != null) {
            int nx = (int) Math.round(event.x() - grabDx);
            int ny = (int) Math.round(event.y() - grabDy);
            if (dragging == DragTarget.MAIN) {
                nx = clamp(nx, 0, Math.max(0, width - hud.mainW()));
                ny = clamp(ny, 0, Math.max(0, height - hud.mainH()));
                config.hudX = nx;
                config.hudY = ny;
            } else {
                nx = clamp(nx, 0, Math.max(0, width - hud.speakW()));
                ny = clamp(ny, 0, Math.max(0, height - hud.speakH()));
                config.speakingHudX = nx;
                config.speakingHudY = ny;
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != DragTarget.NONE) {
            dragging = DragTarget.NONE;
            config.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        config.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
