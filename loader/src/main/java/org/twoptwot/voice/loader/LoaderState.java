package org.twoptwot.voice.loader;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class LoaderState {

    public static final String MOD_ID = "twoptwotvoice-loader";
    public static final String VOICE_MOD_ID = "twoptwotvoice";
    public static final String GITHUB_REPO = "2p2t-Official/2p2t-voice";
    public static final Logger LOG = LoggerFactory.getLogger("2p2t-voice-loader");

    private static volatile boolean injected;
    private static volatile boolean skippedDirectJar;
    private static volatile Path payloadJar;
    private static volatile String payloadVersion = "unknown";
    private static volatile String lastError;

    private LoaderState() {
    }

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("twoptwotvoice");
    }

    public static Path payloadDir() {
        return configDir().resolve("payload");
    }

    public static Path stampFile() {
        return payloadDir().resolve("payload.stamp");
    }

    public static boolean voiceModAlreadyPresent() {
        return FabricLoader.getInstance().isModLoaded(VOICE_MOD_ID);
    }

    public static void markSkippedDirectJar() {
        skippedDirectJar = true;
    }

    public static boolean wasSkippedDirectJar() {
        return skippedDirectJar;
    }

    public static void markInjected(Path jar, String version) {
        injected = true;
        payloadJar = jar;
        payloadVersion = version == null || version.isBlank() ? "unknown" : version;
        lastError = null;
        try {
            Files.createDirectories(payloadDir());
            Files.writeString(stampFile(), payloadVersion + "\n" + jar.toAbsolutePath().normalize());
        } catch (Exception e) {
            LOG.warn("Could not write payload stamp: {}", e.toString());
        }
        System.setProperty("twoptwotvoice.loader.mode", "true");
        System.setProperty("twoptwotvoice.payload.path", jar.toAbsolutePath().normalize().toString());
        System.setProperty("twoptwotvoice.payload.version", payloadVersion);
    }

    public static boolean isInjected() {
        return injected;
    }

    public static Path payloadJar() {
        return payloadJar;
    }

    public static String payloadVersion() {
        return payloadVersion;
    }

    public static void setError(String message) {
        lastError = message;
        LOG.error("[2p2t Voice Loader] {}", message);
    }

    public static String lastError() {
        return lastError;
    }
}
