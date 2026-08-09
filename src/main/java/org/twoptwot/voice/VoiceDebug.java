package org.twoptwot.voice;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.twoptwot.voice.update.ModUpdater;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class VoiceDebug {

    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private static volatile Path sessionFile;

    private VoiceDebug() {
    }

    public static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("twoptwotvoice").resolve("debug");
    }

    public static Path sessionFile() {
        Path existing = sessionFile;
        if (existing != null) {
            return existing;
        }
        synchronized (VoiceDebug.class) {
            if (sessionFile == null) {
                try {
                    Files.createDirectories(folder());
                } catch (IOException ignored) {
                }
                sessionFile = folder().resolve("session-" + FILE_TS.format(Instant.now()) + ".log");
            }
            return sessionFile;
        }
    }

    public static void log(String message) {
        String line = TS.format(Instant.now()) + "  " + message + System.lineSeparator();
        try {
            Path file = sessionFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    public static void snapshot(String reason) {
        Minecraft client = Minecraft.getInstance();
        StringBuilder sb = new StringBuilder();
        sb.append(reason);
        sb.append(" | mod=").append(ModUpdater.installedVersion());
        sb.append(" hash=").append(shortHash(ModIntegrity.jarSha256()));
        sb.append(" signed=").append(ModIntegrity.isSigned());
        sb.append(" gate=").append(ServerGate.isAllowed());
        try {
            sb.append(" canSend=").append(
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(
                            org.twoptwot.voice.net.PluginBridge.VoicePluginPayload.TYPE));
        } catch (Throwable t) {
            sb.append(" canSend=?");
        }
        if (client != null) {
            ServerData data = client.getCurrentServer();
            if (data != null) {
                sb.append(" server=").append(data.ip);
            }
            if (client.getConnection() != null && client.getConnection().getConnection() != null) {
                sb.append(" remote=").append(client.getConnection().getConnection().getRemoteAddress());
            }
        }
        log(sb.toString());
    }

    public static void openFolder() {
        try {
            Path dir = folder();
            Files.createDirectories(dir);
            if (sessionFile == null) {
                log("debug folder opened");
            }
            openDir(dir);
        } catch (Exception e) {
            log("openFolder failed: " + e.getMessage());
        }
    }

    private static void openDir(Path dir) throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String abs = dir.toAbsolutePath().toString();
        if (os.contains("win")) {
            new ProcessBuilder("explorer.exe", abs).start();
            return;
        }
        if (os.contains("mac")) {
            new ProcessBuilder("open", abs).start();
            return;
        }
        new ProcessBuilder("xdg-open", abs).start();
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.isBlank()) {
            return "-";
        }
        return hash.length() <= 12 ? hash : hash.substring(0, 12);
    }
}
