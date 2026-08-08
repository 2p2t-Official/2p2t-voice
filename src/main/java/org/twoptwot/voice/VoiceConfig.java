package org.twoptwot.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class VoiceConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String preferredApiBase = "https://voice.2p2t.org";
    public String mode = "vad";
    public String pttKey = "key.keyboard.v";
    public int proximityRange = 48;
    public float masterVolume = 1.0f;
    public float micVolume = 1.0f;
    
    public float vadThreshold = 0.02f;
    public boolean pttTones = true;

    public static final float VAD_MIN = 0.002f;
    public static final float VAD_MAX = 0.08f;

    public float normalizedVadThreshold() {
        if (!Float.isFinite(vadThreshold)) {
            return 0.02f;
        }
        return Math.max(VAD_MIN, Math.min(VAD_MAX, vadThreshold));
    }

    
    public float sensitivity01() {
        float t = normalizedVadThreshold();
        return 1f - (t - VAD_MIN) / (VAD_MAX - VAD_MIN);
    }

    public void setSensitivity01(float sens) {
        float s = Math.max(0f, Math.min(1f, sens));
        vadThreshold = VAD_MAX - s * (VAD_MAX - VAD_MIN);
        save();
    }
    public boolean hudEnabled = true;
    public boolean hudDebug = false;
    
    public Boolean hudSpeaking = Boolean.TRUE;
    
    public int hudX = 4;
    public int hudY = 4;
    
    public int speakingHudX = -1;
    public int speakingHudY = -1;
    public String inputDeviceId = "";
    public String outputDeviceId = "";
    public boolean noiseSuppression = true;
    public boolean autoUpdate = true;
    public long lastUpdateCheckMs = 0L;
    public long lastUpdateMs = 0L;
    public String lastUpdateType = "";
    public String lastUpdateVersion = "";
    public String latestKnownVersion = "";

    public boolean isHudSpeaking() {
        return hudSpeaking == null || hudSpeaking;
    }

    public static VoiceConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("twoptwotvoice.json");
        if (Files.isRegularFile(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                VoiceConfig cfg = GSON.fromJson(reader, VoiceConfig.class);
                if (cfg != null) {
                    if (cfg.hudSpeaking == null) {
                        cfg.hudSpeaking = Boolean.TRUE;
                    }
                    return cfg;
                }
            } catch (IOException ignored) {
            }
        }
        VoiceConfig fresh = new VoiceConfig();
        fresh.save();
        return fresh;
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("twoptwotvoice.json");
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }
}
