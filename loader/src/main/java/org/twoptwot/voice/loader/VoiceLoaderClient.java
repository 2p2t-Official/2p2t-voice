package org.twoptwot.voice.loader;

import net.fabricmc.api.ClientModInitializer;

public final class VoiceLoaderClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        if (LoaderState.wasSkippedDirectJar()) {
            return;
        }
        if (!LoaderState.isInjected()) {
            LoaderState.LOG.error("Payload was not injected — voice client will not start");
            return;
        }
        try {
            ClassLoader cl = VoiceLoaderClient.class.getClassLoader();
            try {
                Class.forName("org.java_websocket.client.WebSocketClient", false, cl);
            } catch (ClassNotFoundException missingWs) {
                throw new IllegalStateException(
                        "Java-WebSocket missing after payload inject (nested jar not loaded)", missingWs);
            }
            Class<?> clazz = Class.forName("org.twoptwot.voice.TwoptwotVoiceClient", true, cl);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            clazz.getMethod("onInitializeClient").invoke(instance);
            LoaderState.LOG.info("Started TwoptwotVoiceClient from injected payload");
        } catch (Throwable t) {
            LoaderState.setError("Failed to start voice client: " + t);
            throw new RuntimeException("Failed to start injected 2p2t Voice client", t);
        }
    }
}
