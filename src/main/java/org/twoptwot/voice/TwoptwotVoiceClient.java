package org.twoptwot.voice;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import org.twoptwot.voice.audio.VoiceController;
import org.twoptwot.voice.net.PluginBridge;
import org.twoptwot.voice.net.SignalingClient;
import org.twoptwot.voice.ui.VoiceHud;
import org.twoptwot.voice.ui.VoiceKeybinds;
import org.twoptwot.voice.ui.menu.MenuScreens;
import org.twoptwot.voice.update.ModUpdater;
import org.twoptwot.voice.webrtc.WebRtcEngine;

public final class TwoptwotVoiceClient implements ClientModInitializer {

    public static final String MOD_ID = "twoptwotvoice";

    private static TwoptwotVoiceClient instance;

    private VoiceConfig config;
    private VoiceController controller;
    private SignalingClient signaling;
    private WebRtcEngine webRtc;
    private PluginBridge pluginBridge;
    private VoiceHud hud;
    private boolean activeOnServer;

    @Override
    public void onInitializeClient() {
        instance = this;
        ModUpdater.cleanupStaleJarsOnStartup();
        config = VoiceConfig.load();
        controller = new VoiceController(config);
        signaling = new SignalingClient(controller);
        webRtc = new WebRtcEngine(controller, signaling);
        signaling.setWebRtc(webRtc);
        pluginBridge = new PluginBridge(signaling, controller);
        hud = new VoiceHud(controller, signaling);

        VoiceKeybinds.register(controller, signaling);
        pluginBridge.register();
        MenuScreens.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            client.execute(() -> {
                if (!ServerGate.isAllowed()) {
                    activeOnServer = false;
                    silentShutdown();
                    return;
                }
                activeOnServer = true;
                controller.onJoinWorld();
                pluginBridge.requestSession();
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            client.execute(() -> {
                activeOnServer = false;
                silentShutdown();
                controller.onLeaveWorld();
            });
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            VoiceKeybinds.tick(client, controller, signaling);
            if (!activeOnServer || !ServerGate.isAllowed()) {
                return;
            }
            controller.tick(client);
        });

        HudRenderCallback.EVENT.register((graphics, delta) -> {
            if (!activeOnServer || !ServerGate.isAllowed()) {
                return;
            }
            hud.render(graphics, delta);
        });

        if (config.autoUpdate) {
            ModUpdater.checkAsync(false);
        }
    }

    private void silentShutdown() {
        activeOnServer = false;
        try {
            signaling.disconnect("left_server");
        } catch (Throwable ignored) {
        }
        try {
            webRtc.shutdown();
        } catch (Throwable ignored) {
        }
    }

    public boolean isActiveOnServer() {
        return activeOnServer && ServerGate.isAllowed();
    }

    public static TwoptwotVoiceClient get() {
        return instance;
    }

    public VoiceConfig config() {
        return config;
    }

    public VoiceController controller() {
        return controller;
    }

    public SignalingClient signaling() {
        return signaling;
    }

    public WebRtcEngine webRtc() {
        return webRtc;
    }

    public PluginBridge pluginBridge() {
        return pluginBridge;
    }

    public VoiceHud hud() {
        return hud;
    }
}
