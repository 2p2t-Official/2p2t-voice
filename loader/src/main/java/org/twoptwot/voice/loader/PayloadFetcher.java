package org.twoptwot.voice.loader;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public final class PayloadFetcher {

    private static final String API_LATEST =
            "https://api.github.com/repos/" + LoaderState.GITHUB_REPO + "/releases/latest";

    private PayloadFetcher() {
    }

    public static Path ensurePayload() throws Exception {
        String mc = minecraftVersion();
        Path dir = LoaderState.payloadDir();
        Files.createDirectories(dir);
        Path cached = dir.resolve("twoptwotvoice-+" + sanitize(mc) + ".jar");
        Path meta = dir.resolve("twoptwotvoice-+" + sanitize(mc) + ".meta");
        Path staged = dir.resolve(cached.getFileName().toString() + ".next");

        if (Files.isRegularFile(staged) && isValidPayloadJar(staged)) {
            try {
                Files.move(staged, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                try {
                    Files.move(staged, cached, StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception replaceFail) {
                    LoaderState.LOG.info("Using staged payload {}", staged.getFileName());
                    return staged;
                }
            }
            LoaderState.LOG.info("Promoted staged payload to {}", cached.getFileName());
        }

        ReleaseAsset remote = null;
        try {
            remote = fetchLatestAsset(mc);
        } catch (Exception e) {
            LoaderState.LOG.warn("Could not check GitHub for voice payload: {}", e.toString());
        }

        if (Files.isRegularFile(cached) && isValidPayloadJar(cached)) {
            if (remote == null) {
                LoaderState.LOG.info("Using cached payload {}", cached.getFileName());
                return cached;
            }
            String localTag = readMetaTag(meta);
            if (!isNewer(remote.tag(), localTag) && remote.tag().equals(localTag)) {
                LoaderState.LOG.info("Cached payload is current ({})", localTag);
                return cached;
            }
            if (!isNewer(remote.tag(), localTag) && !localTag.isBlank()) {
                return cached;
            }
        } else if (remote == null) {
            throw new IllegalStateException("No cached voice jar for Minecraft " + mc + " and download failed");
        }

        LoaderState.LOG.info("Downloading {} ...", remote.assetName());
        Path tmp = dir.resolve(cached.getFileName().toString() + ".download");
        download(remote.url(), tmp);
        if (!isValidPayloadJar(tmp)) {
            Files.deleteIfExists(tmp);
            throw new IllegalStateException("Downloaded file is not a valid voice payload jar");
        }
        try {
            Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
            Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(meta, remote.tag() + "\n" + remote.assetName() + "\n", StandardCharsets.UTF_8);
        LoaderState.LOG.info("Payload ready: {} ({})", cached.getFileName(), remote.tag());
        return cached;
    }

    public static String readPayloadVersion(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            ZipEntry entry = jf.getEntry("fabric.mod.json");
            if (entry == null) {
                return "unknown";
            }
            try (InputStream in = jf.getInputStream(entry)) {
                JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (root.has("version") && root.get("version").isJsonPrimitive()) {
                    return root.get("version").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    private static ReleaseAsset fetchLatestAsset(String mc) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(12))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(API_LATEST))
                .timeout(Duration.ofSeconds(25))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "twoptwotvoice-loader")
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("GitHub HTTP " + res.statusCode());
        }
        JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
        String tag = root.has("tag_name") ? root.get("tag_name").getAsString() : "";
        String cleanTag = tag.startsWith("v") ? tag.substring(1) : tag;
        JsonArray assets = root.has("assets") ? root.getAsJsonArray("assets") : new JsonArray();

        String assetName = "";
        String assetUrl = "";
        for (JsonElement el : assets) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject a = el.getAsJsonObject();
            String name = a.has("name") ? a.get("name").getAsString() : "";
            String dl = a.has("browser_download_url") ? a.get("browser_download_url").getAsString() : "";
            if (!isVoicePayloadAsset(name) || !matchesMinecraftExact(name, mc)) {
                continue;
            }
            assetName = name;
            assetUrl = dl;
            break;
        }
        if (assetUrl.isBlank()) {
            throw new IllegalStateException("No voice jar for Minecraft " + mc + " in latest release");
        }
        return new ReleaseAsset(cleanTag, assetName, assetUrl);
    }

    private static void download(String url, Path target) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(4))
                .header("User-Agent", "twoptwotvoice-loader")
                .GET()
                .build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("Download HTTP " + res.statusCode());
        }
        try (InputStream in = res.body()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isValidPayloadJar(Path jar) {
        try (JarFile jf = new JarFile(jar.toFile())) {
            ZipEntry entry = jf.getEntry("fabric.mod.json");
            if (entry == null) {
                return false;
            }
            try (InputStream in = jf.getInputStream(entry)) {
                JsonObject root = JsonParser.parseReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                String id = root.has("id") ? root.get("id").getAsString() : "";
                return LoaderState.VOICE_MOD_ID.equals(id);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static String readMetaTag(Path meta) {
        try {
            if (!Files.isRegularFile(meta)) {
                return "";
            }
            List<String> lines = Files.readAllLines(meta, StandardCharsets.UTF_8);
            return lines.isEmpty() ? "" : lines.get(0).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    static boolean isVoicePayloadAsset(String assetName) {
        String n = assetName == null ? "" : assetName.toLowerCase(Locale.ROOT);
        if (!n.endsWith(".jar")) {
            return false;
        }
        if (n.contains("loader")) {
            return false;
        }
        return n.startsWith("twoptwotvoice") || n.startsWith("2p2tvoice");
    }

    static boolean matchesMinecraftExact(String assetName, String mcVersion) {
        String n = assetName.toLowerCase(Locale.ROOT);
        for (String mc : mcVersionCandidates(mcVersion)) {
            if (n.endsWith("+" + mc + ".jar")
                    || n.contains("+" + mc + "-")
                    || n.endsWith("-" + mc + ".jar")
                    || n.endsWith("_" + mc + ".jar")) {
                return true;
            }
        }
        return false;
    }

    static List<String> mcVersionCandidates(String mcVersion) {
        String mc = mcVersion == null ? "" : mcVersion.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        if (!mc.isEmpty()) {
            out.add(mc);
        }
        if (mc.endsWith(".0") && mc.chars().filter(ch -> ch == '.').count() >= 2) {
            String trimmed = mc.substring(0, mc.length() - 2);
            if (!trimmed.isEmpty() && !out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    static boolean isNewer(String remote, String local) {
        if (local == null || local.isBlank() || "unknown".equalsIgnoreCase(local)) {
            return true;
        }
        int[] ra = parseSemver(stripBuildMeta(remote));
        int[] la = parseSemver(stripBuildMeta(local));
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
        if (plus >= 0) {
            s = s.substring(0, plus);
        }
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1);
        }
        return s;
    }

    private static int[] parseSemver(String v) {
        String[] parts = v.split("[^0-9]+");
        int[] out = new int[3];
        int filled = 0;
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (filled >= 3) {
                break;
            }
            try {
                out[filled++] = Integer.parseInt(p);
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static String sanitize(String mc) {
        return mc.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private record ReleaseAsset(String tag, String assetName, String url) {
    }
}
