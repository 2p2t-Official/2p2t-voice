package org.twoptwot.voice.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class VoiceButton extends AbstractWidget {

    private final Consumer<VoiceButton> onPress;
    private Style style;
    private boolean selected;

    public enum Style {
        PRIMARY,
        GHOST,
        DANGER,
        QUIET
    }

    public VoiceButton(int x, int y, int w, int h, Component message, Style style, Consumer<VoiceButton> onPress) {
        super(x, y, w, h, message);
        this.style = style;
        this.onPress = onPress;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (active) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            onPress.accept(this);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = isHoveredOrFocused();
        int bg;
        int fg = VoiceUi.TEXT;
        int border = VoiceUi.BORDER_SOFT;
        switch (style) {
            case PRIMARY -> {
                bg = selected || hot ? VoiceUi.GOLD : VoiceUi.ACCENT;
                fg = VoiceUi.TEXT;
                border = hot ? VoiceUi.ACCENT_BRIGHT : VoiceUi.ACCENT_DIM;
            }
            case DANGER -> {
                bg = hot ? 0xF03A1820 : 0xE0281218;
                fg = 0xFFF0D4D8;
                border = 0xFF5A2430;
            }
            case QUIET -> {
                bg = hot ? VoiceUi.BG_ROW_HOT : VoiceUi.BG_CHIP;
                fg = VoiceUi.TEXT_DIM;
            }
            default -> {
                bg = selected ? 0xE01A2838 : (hot ? VoiceUi.BG_ROW_HOT : VoiceUi.BG_CHIP);
                if (selected) {
                    border = VoiceUi.GOLD;
                    fg = VoiceUi.GOLD;
                }
            }
        }
        g.fill(getX(), getY(), getX() + width, getY() + height, bg);
        g.fill(getX(), getY(), getX() + width, getY() + 1, border);
        g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
        g.fill(getX(), getY(), getX() + 1, getY() + height, border);
        g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);
        if (selected && style == Style.GHOST) {
            g.fill(getX(), getY(), getX() + 3, getY() + height, VoiceUi.GOLD);
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        int tw = font.width(getMessage());
        int tx = getX() + (width - tw) / 2;
        int ty = getY() + (height - 8) / 2;
        g.drawString(font, getMessage(), tx, ty, fg, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
