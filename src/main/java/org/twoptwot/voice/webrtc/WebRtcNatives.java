package org.twoptwot.voice.webrtc;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.twoptwot.voice.TwoptwotVoiceClient;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class WebRtcNatives {

    private static final Logger LOG = Logger.getLogger("twoptwotvoice");
    private static final long MIN_NATIVE_BYTES = 1024L;

    private static volatile boolean loaded;
    private static volatile String error;

    private WebRtcNatives() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        try {
            String resource = nativeResourceName();
            Path loadedPath = extractNative(resource);
            System.load(loadedPath.toAbsolutePath().toString());
            markNativeLoaderDone();
            loaded = true;
            error = null;
            LOG.info("Loaded WebRTC native (" + Files.size(loadedPath) + " bytes): " + loadedPath.toAbsolutePath());
        } catch (Throwable t) {
            loaded = false;
            error = flatten(t);
            LOG.log(Level.SEVERE, "Failed to load WebRTC natives: " + error, t);
            throw new IllegalStateException("WebRTC native load failed: " + error, t);
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static String error() {
        return error;
    }

    private static Path extractNative(String resource) throws Exception {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("twoptwotvoice").resolve("natives");
        Files.createDirectories(dir);
        cleanupOldNatives(dir, resource);

        String uniqueName = resource + "." + ProcessHandle.current().pid() + "." + UUID.randomUUID();
        Path dest = dir.resolve(uniqueName);

        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(TwoptwotVoiceClient.MOD_ID);
        if (mod.isPresent()) {
            Optional<Path> embedded = mod.get().findPath(resource);
            if (embedded.isEmpty()) {
                embedded = mod.get().findPath("/" + resource);
            }
            if (embedded.isPresent()) {
                try {
                    Files.copy(embedded.get(), dest, StandardCopyOption.REPLACE_EXISTING);
                    dest.toFile().deleteOnExit();
                    return validate(dest);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Copy from mod container failed, trying classpath/temp", e);
                }
            }
        }

        try (InputStream in = openResource(resource)) {
            if (in != null) {
                try {
                    Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    dest.toFile().deleteOnExit();
                    return validate(dest);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Copy to game dir failed, trying java.io.tmpdir", e);
                }
            }
        }

        Path tmp = Files.createTempFile("twoptwotvoice-", "-" + resource);
        try (InputStream in = openResource(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing native resource: " + resource
                        + " (os=" + System.getProperty("os.name")
                        + " arch=" + System.getProperty("os.arch") + ")");
            }
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        tmp.toFile().deleteOnExit();
        return validate(tmp);
    }

    private static Path validate(Path dest) throws Exception {
        if (!Files.isRegularFile(dest) || Files.size(dest) < MIN_NATIVE_BYTES) {
            throw new IllegalStateException("Native file missing or too small: " + dest
                    + " size=" + (Files.isRegularFile(dest) ? Files.size(dest) : -1));
        }
        return dest;
    }

    private static void cleanupOldNatives(Path dir, String resourcePrefix) {
        try (Stream<Path> stream = Files.list(dir)) {
            long now = System.currentTimeMillis();
            stream.filter(p -> p.getFileName().toString().startsWith(resourcePrefix)).forEach(p -> {
                try {
                    long age = now - Files.getLastModifiedTime(p).toMillis();
                    if (age > 3_600_000L) {
                        Files.deleteIfExists(p);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static InputStream openResource(String resource) {
        ClassLoader[] loaders = {
                WebRtcNatives.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            InputStream in = loader.getResourceAsStream(resource);
            if (in != null) {
                return in;
            }
            in = loader.getResourceAsStream("/" + resource);
            if (in != null) {
                return in;
            }
        }
        return WebRtcNatives.class.getResourceAsStream("/" + resource);
    }

    private static void markNativeLoaderDone() {
        try {
            Class<?> nl = Class.forName("dev.onvoid.webrtc.internal.NativeLoader", true,
                    WebRtcNatives.class.getClassLoader());
            Field field = nl.getDeclaredField("LOADED_LIB_SET");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> set = (Set<String>) field.get(null);
            set.add("webrtc-java");
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Could not mark NativeLoader loaded set", t);
        }
    }

    private static String nativeResourceName() {
        return System.mapLibraryName("webrtc-java-" + osFamily() + "-" + osArch());
    }

    private static String osFamily() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.startsWith("mac os") || name.startsWith("macos") || name.startsWith("darwin")) {
            return "macos";
        }
        if (name.startsWith("linux")) {
            return "linux";
        }
        if (name.startsWith("windows")) {
            return "windows";
        }
        throw new IllegalStateException("Unsupported OS: " + name);
    }

    private static String osArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64", "x86-64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            case "arm", "aarch32" -> "aarch32";
            default -> throw new IllegalStateException("Unsupported arch: " + arch);
        };
    }

    static String flatten(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage());
            if (sb.length() > 280) {
                break;
            }
        }
        return sb.toString();
    }
}
