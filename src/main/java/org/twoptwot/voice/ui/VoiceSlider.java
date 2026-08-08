package org.twoptwot.voice.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

public final class VoiceSlider extends AbstractSliderButton {

    private final String label;
    private final DoubleConsumer onChange;
    private final double min;
    private final double max;
    private final boolean percent;

    public VoiceSlider(int x, int y, int w, int h, String label, double current, double min, double max,
                       boolean percent, DoubleConsumer onChange) {
        super(x, y, w, Math.max(h, 28), Component.empty(), normalize(current, min, max));
        this.label = label;
        this.min = min;
        this.max = max;
        this.percent = percent;
        this.onChange = onChange;
        updateMessage();
    }

    private static double normalize(double current, double min, double max) {
        if (max <= min) {
            return 0;
        }
        return Math.max(0, Math.min(1, (current - min) / (max - min)));
    }

    public double realValue() {
        return min + value * (max - min);
    }

    @Override
    protected void updateMessage() {
        double v = realValue();
        if (percent) {
            setMessage(Component.literal(label + ": " + Math.round(v * 100.0) + "%"));
        } else {
            setMessage(Component.literal(label + ": " + Math.round(v)));
        }
    }

    @Override
    protected void applyValue() {
        onChange.accept(realValue());
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        g.fill(x, y, x + width, y + height, VoiceUi.BG_CHIP);
        g.fill(x, y, x + width, y + 1, VoiceUi.BORDER);
        g.fill(x, y + height - 1, x + width, y + height, VoiceUi.BORDER_SOFT);

        var font = net.minecraft.client.Minecraft.getInstance().font;
        g.drawString(font, getMessage(), x + 6, y + 3, VoiceUi.TEXT, false);

        int trackLeft = x + 6;
        int trackRight = x + width - 6;
        int trackY = y + height - 10;
        g.fill(trackLeft, trackY, trackRight, trackY + 3, VoiceUi.BORDER_SOFT);
        int fillW = (int) ((trackRight - trackLeft) * value);
        g.fill(trackLeft, trackY, trackLeft + fillW, trackY + 3, VoiceUi.GOLD);

        int handleX = trackLeft + (int) ((trackRight - trackLeft - 6) * value);
        g.fill(handleX, trackY - 3, handleX + 6, trackY + 6, VoiceUi.ACCENT_BRIGHT);
    }
}
