package org.twoptwot.voice;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ModIntegrity {

    private static final String RESOURCE = "/voice-integrity.properties";
    private static volatile String cachedHash;
    private static volatile String cachedSecret;

    private ModIntegrity() {
    }

    public static String jarSha256() {
        String cached = cachedHash;
        if (cached != null) {
            return cached;
        }
        synchronized (ModIntegrity.class) {
            if (cachedHash != null) {
                return cachedHash;
            }
            cachedHash = computeJarSha256();
            return cachedHash;
        }
    }

    public static String signature(String version) {
        String secret = integritySecret();
        if (secret == null || secret.isBlank()) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String payload = (version == null ? "" : version) + "|" + jarSha256();
            byte[] out = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isSigned() {
        String secret = integritySecret();
        return secret != null && !secret.isBlank();
    }

    private static String integritySecret() {
        String cached = cachedSecret;
        if (cached != null) {
            return cached;
        }
        synchronized (ModIntegrity.class) {
            if (cachedSecret != null) {
                return cachedSecret;
            }
            String fromEnv = System.getenv("VOICE_INTEGRITY_SECRET");
            if (fromEnv != null && !fromEnv.isBlank()) {
                cachedSecret = fromEnv.trim();
                return cachedSecret;
            }
            try (InputStream in = ModIntegrity.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    Properties props = new Properties();
                    props.load(in);
                    String secret = props.getProperty("secret", "");
                    cachedSecret = secret == null ? "" : secret.trim();
                    return cachedSecret;
                }
            } catch (Exception ignored) {
            }
            cachedSecret = "";
            return cachedSecret;
        }
    }

    private static String computeJarSha256() {
        try {
            URI uri = ModIntegrity.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(uri);
            if (!Files.isRegularFile(path)) {
                return "";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (JarFile jar = new JarFile(path.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName();
                    if ("voice-integrity.properties".equals(name) || name.startsWith("META-INF/")) {
                        continue;
                    }
                    digest.update(name.getBytes(StandardCharsets.UTF_8));
                    try (InputStream in = jar.getInputStream(entry)) {
                        in.transferTo(new java.io.OutputStream() {
                            @Override
                            public void write(int b) {
                                digest.update((byte) b);
                            }

                            @Override
                            public void write(byte[] b, int off, int len) {
                                digest.update(b, off, len);
                            }
                        });
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            return "";
        }
    }
}
