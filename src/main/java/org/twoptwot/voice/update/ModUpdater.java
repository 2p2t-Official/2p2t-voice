package org.twoptwot.voice.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.twoptwot.voice.TwoptwotVoiceClient;
import org.twoptwot.voice.VoiceConfig;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModUpdater {

    public static final String GITHUB_REPO = "WaffleStealz/2p2t-voice";
    private static final String API_LATEST =
            "https://api.github.com/repos/" + GITHUB_REPO + "/releases/latest";
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    private static final AtomicBoolean checking = new AtomicBoolean(false);
    private static final AtomicBoolean updating = new AtomicBoolean(false);

    private static volatile String statusLine = "Idle";
    private static volatile String latestTag = "";
    private static volatile String latestAssetName = "";
    private static volatile String latestAssetUrl = "";
    private static volatile boolean updateAvailable = false;

    private ModUpdater() {
    }

    public static String statusLine() {
        return statusLine;
    }

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public static String latestTag() {
        return latestTag;
    }

    public static String formatTimestamp(long epochMs) {
        if (epochMs <= 0L) {
            return "never";
        }
        return DISPLAY_FMT.format(Instant.ofEpochMilli(epochMs));
    }

    public static String installedVersion() {
        return FabricLoader.getInstance()
                .getModContainer(TwoptwotVoiceClient.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    public static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    public static void checkAsync(boolean manual) {
        if (!checking.compareAndSet(false, true)) {
            statusLine = "Check already running…";
            return;
        }
        statusLine = "Checking GitHub for updates…";
        VoiceConfig config = TwoptwotVoiceClient.get().config();
        config.lastUpdateCheckMs = System.currentTimeMillis();
        config.save();

        CompletableFuture.runAsync(() -> {
            try {
                CheckResult result = checkLatest();
                latestTag = result.tag();
                latestAssetName = result.assetName();
                latestAssetUrl = result.assetUrl();
                updateAvailable = result.updateAvailable();
                config.latestKnownVersion = result.tag();
                config.save();
                if (result.updateAvailable()) {
                    statusLine = "Update available: " + result.tag();
                    if (!manual && config.autoUpdate) {
                        applyUpdateAsync(false);
                    }
                } else {
                    statusLine = "Up to date (" + installedVersion() + ")";
                }
            } catch (Exception e) {
                statusLine = "Update check failed: " + shortMsg(e);
                updateAvailable = false;
            } finally {
                checking.set(false);
            }
        });
    }

    public static void applyUpdateAsync(boolean manual) {
        if (!updating.compareAndSet(false, true)) {
            statusLine = "Update already running…";
            return;
        }
        if (latestAssetUrl == null || latestAssetUrl.isBlank()) {
            updating.set(false);
            checkAsync(manual);
            return;
        }
        statusLine = manual ? "Downloading update (manual)…" : "Downloading update (auto)…";
        final boolean wasManual = manual;
        final String url = latestAssetUrl;
        final String asset = latestAssetName;
        final String tag = latestTag;
        CompletableFuture.runAsync(() -> {
            try {
                Path installed = installJar(url, asset);
                scheduleOldJarRemoval();
                VoiceConfig config = TwoptwotVoiceClient.get().config();
                config.lastUpdateMs = System.currentTimeMillis();
                config.lastUpdateType = wasManual ? "manual" : "auto";
                config.lastUpdateVersion = tag;
                config.save();
                statusLine = "Installed " + tag + " → restart Minecraft to load it.";
                updateAvailable = false;
            } catch (Exception e) {
                statusLine = "Update failed: " + shortMsg(e);
            } finally {
                updating.set(false);
            }
        });
    }

    private static CheckResult checkLatest() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(API_LATEST))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "twoptwotvoice-updater")
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) {
            return new CheckResult(installedVersion(), "", "", false);
        }
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("GitHub HTTP " + res.statusCode());
        }
        JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
        String tag = root.has("tag_name") ? root.get("tag_name").getAsString() : "";
        String cleanTag = tag.startsWith("v") ? tag.substring(1) : tag;
        String mc = minecraftVersion();
        JsonArray assets = root.has("assets") ? root.getAsJsonArray("assets") : new JsonArray();
        String assetName = "";
        String assetUrl = "";
        for (JsonElement el : assets) {
            if (!el.isJsonObject()) continue;
            JsonObject a = el.getAsJsonObject();
            String name = a.has("name") ? a.get("name").getAsString() : "";
            String dl = a.has("browser_download_url") ? a.get("browser_download_url").getAsString() : "";
            if (name.toLowerCase(Locale.ROOT).endsWith(".jar") && matchesMinecraft(name, mc)) {
                assetName = name;
                assetUrl = dl;
                break;
            }
        }
        if (assetUrl.isBlank()) {
            throw new IllegalStateException("No jar for Minecraft " + mc + " in latest release");
        }
        boolean newer = isNewer(cleanTag, installedVersion());
        return new CheckResult(cleanTag, assetName, assetUrl, newer);
    }

    static boolean matchesMinecraft(String assetName, String mcVersion) {
        String n = assetName.toLowerCase(Locale.ROOT);
        String mc = mcVersion.toLowerCase(Locale.ROOT);
        return n.contains("+" + mc + ".")
                || n.contains("+" + mc + "-")
                || n.endsWith("+" + mc + ".jar")
                || n.contains("-" + mc + ".jar")
                || n.contains("_" + mc + ".jar");
    }

    static boolean isNewer(String remote, String local) {
        String r = stripBuildMeta(remote);
        String l = stripBuildMeta(local);
        int[] ra = parseSemver(r);
        int[] la = parseSemver(l);
        for (int i = 0; i < 3; i++) {
            if (ra[i] != la[i]) {
                return ra[i] > la[i];
            }
        }
        return false;
    }

    private static String stripBuildMeta(String v) {
        String s = v == null ? "0" : v.trim();
        int plus = s.indexOf('+');
        if (plus >= 0) s = s.substring(0, plus);
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        return s;
    }

    private static int[] parseSemver(String v) {
        String[] parts = v.split("[^0-9]+");
        int[] out = new int[3];
        int filled = 0;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (filled >= 3) break;
            try {
                out[filled++] = Integer.parseInt(p);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static Path installJar(String url, String assetName) throws Exception {
        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        Files.createDirectories(mods);
        Path target = mods.resolve(assetName);
        Path tmp = mods.resolve(assetName + ".download");
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(3))
                .header("User-Agent", "twoptwotvoice-updater")
                .GET()
                .build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("Download HTTP " + res.statusCode());
        }
        try (InputStream in = res.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    private static void scheduleOldJarRemoval() {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(TwoptwotVoiceClient.MOD_ID);
        if (mod.isEmpty()) {
            return;
        }
        for (Path path : mod.get().getOrigin().getPaths()) {
            if (path == null || !Files.isRegularFile(path)) continue;
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".jar")) continue;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                }
            }, "twoptwotvoice-jar-cleanup"));
        }
    }

    private static String shortMsg(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isBlank()) {
            return t.getClass().getSimpleName();
        }
        return m.length() > 80 ? m.substring(0, 80) + "…" : m;
    }

    private record CheckResult(String tag, String assetName, String assetUrl, boolean updateAvailable) {
    }
}
