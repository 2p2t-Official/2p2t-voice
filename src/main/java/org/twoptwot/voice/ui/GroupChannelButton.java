package org.twoptwot.voice.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.twoptwot.voice.net.SignalingClient;

import java.util.function.Consumer;

public final class GroupChannelButton extends AbstractWidget {

    private final SignalingClient.GroupInfo group;
    private final Runnable onLeftClick;
    private final Consumer<GroupChannelButton> onRightClick;
    private boolean selected;

    public GroupChannelButton(int x, int y, int w, int h, SignalingClient.GroupInfo group,
                              Runnable onLeftClick, Consumer<GroupChannelButton> onRightClick) {
        super(x, y, w, h, Component.literal(labelFor(group)));
        this.group = group;
        this.onLeftClick = onLeftClick;
        this.onRightClick = onRightClick;
    }

    private static String labelFor(SignalingClient.GroupInfo group) {
        String label = group.name == null || group.name.isBlank() ? "Group" : group.name;
        if (label.length() > 14) {
            label = label.substring(0, 13) + "…";
        }
        if (group.isOwner) {
            label = "★ " + label;
            if (label.length() > 14) {
                label = label.substring(0, 13) + "…";
            }
        }
        return label;
    }

    public SignalingClient.GroupInfo group() {
        return group;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || !visible || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        if (event.button() == 1) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            if (onRightClick != null) {
                onRightClick.accept(this);
            }
            return true;
        }
        if (event.button() == 0) {
            playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
            if (onLeftClick != null) {
                onLeftClick.run();
            }
            return true;
        }
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hot = isHoveredOrFocused();
        int bg = selected ? VoiceUi.GOLD : (hot ? VoiceUi.BG_ROW_HOT : VoiceUi.BG_CHIP);
        int fg = selected ? VoiceUi.TEXT : (group.isOwner ? VoiceUi.GOLD : VoiceUi.TEXT);
        int border = selected ? VoiceUi.ACCENT_BRIGHT : VoiceUi.BORDER_SOFT;
        g.fill(getX(), getY(), getX() + width, getY() + height, bg);
        g.fill(getX(), getY(), getX() + width, getY() + 1, border);
        g.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
        g.fill(getX(), getY(), getX() + 1, getY() + height, border);
        g.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);
        if (selected) {
            g.fill(getX(), getY(), getX() + 3, getY() + height, VoiceUi.GOLD);
        } else if (!group.isPublic) {
            g.fill(getX(), getY(), getX() + 2, getY() + height, VoiceUi.ACCENT_DIM);
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
