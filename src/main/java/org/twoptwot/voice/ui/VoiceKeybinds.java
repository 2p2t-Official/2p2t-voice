package org.twoptwot.voice.ui;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.audio.VoiceController;
import org.twoptwot.voice.net.SignalingClient;

public final class VoiceKeybinds {

    public static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("twoptwotvoice", "main"));

    public static KeyMapping openPanel;
    public static KeyMapping pushToTalk;
    public static KeyMapping muteToggle;
    public static KeyMapping deafenToggle;

    private VoiceKeybinds() {
    }

    public static void register(VoiceController controller, SignalingClient signaling) {
        openPanel = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.twoptwotvoice.open",
                GLFW.GLFW_KEY_H,
                CATEGORY
        ));
        pushToTalk = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.twoptwotvoice.ptt",
                GLFW.GLFW_KEY_V,
                CATEGORY
        ));
        muteToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.twoptwotvoice.mute",
                GLFW.GLFW_KEY_M,
                CATEGORY
        ));
        deafenToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.twoptwotvoice.deafen",
                GLFW.GLFW_KEY_N,
                CATEGORY
        ));
    }

    public static void tick(Minecraft client, VoiceController controller, SignalingClient signaling) {
        boolean active = TwoptwotVoiceClient.get().isActiveOnServer();
        while (openPanel.consumeClick()) {
            if (active && client.screen == null) {
                client.setScreen(new VoiceScreen());
            }
        }
        while (muteToggle.consumeClick()) {
            if (active) {
                controller.toggleMute();
            }
        }
        while (deafenToggle.consumeClick()) {
            if (active) {
                controller.toggleDeafen();
            }
        }
        if (!active) {
            return;
        }
        boolean ptt = pushToTalk.isDown();
        if ("ptt".equalsIgnoreCase(controller.getMode())) {
            controller.setPttHeld(ptt);
        }
    }
}
