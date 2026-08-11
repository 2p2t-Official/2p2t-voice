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
            Class<?> clazz = Class.forName(
                    "org.twoptwot.voice.TwoptwotVoiceClient",
                    true,
                    VoiceLoaderClient.class.getClassLoader());
            Object instance = clazz.getDeclaredConstructor().newInstance();
            clazz.getMethod("onInitializeClient").invoke(instance);
            LoaderState.LOG.info("Started TwoptwotVoiceClient from injected payload");
        } catch (Throwable t) {
            LoaderState.setError("Failed to start voice client: " + t);
            throw new RuntimeException("Failed to start injected 2p2t Voice client", t);
        }
    }
}
