package org.twoptwot.voice;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.twoptwot.voice.ui.menu.MenuChrome;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class ClasspathAssets {

    private static final Logger LOG = LoggerFactory.getLogger("2p2t-voice-assets");
    private static boolean scheduled;
    private static boolean registered;
    private static boolean langInjected;

    private ClasspathAssets() {
    }

    public static void register() {
        if (scheduled || registered) {
            return;
        }
        scheduled = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (registered) {
                return;
            }
            if (client == null || client.getTextureManager() == null) {
                return;
            }
            tryRegister(client);
        });
    }

    private static void tryRegister(Minecraft mc) {
        boolean logoOk = ensureTexture(mc, MenuChrome.LOGO, "assets/twoptwotvoice/textures/gui/logo.png");
        boolean bgOk = ensureTexture(mc, MenuChrome.MENU_BG, "assets/twoptwotvoice/textures/gui/menu_bg.png");
        if (!langInjected) {
            injectLang();
            langInjected = true;
        }
        if (logoOk && bgOk) {
            registered = true;
        }
    }

    private static boolean ensureTexture(Minecraft mc, Identifier id, String classpath) {
        if (resourcePresent(mc, id)) {
            return true;
        }
        return registerTexture(mc, id, classpath);
    }

    private static boolean resourcePresent(Minecraft mc, Identifier id) {
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(id);
            return res != null && res.isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean registerTexture(Minecraft mc, Identifier id, String classpath) {
        try (InputStream in = open(classpath)) {
            if (in == null) {
                LOG.warn("Missing classpath texture {}", classpath);
                return false;
            }
            NativeImage image = NativeImage.read(in);
            AbstractTexture texture = createDynamicTexture(id, image);
            mc.getTextureManager().register(id, texture);
            return true;
        } catch (Exception e) {
            Throwable cause = e;
            if (e instanceof InvocationTargetException) {
                Throwable nested = e.getCause();
                if (nested != null) {
                    cause = nested;
                }
            }
            LOG.warn("Failed to register texture {}: {}", id, cause.toString());
            return false;
        }
    }

    private static AbstractTexture createDynamicTexture(Identifier id, NativeImage image) throws Exception {
        try {
            Constructor<DynamicTexture> ctor = DynamicTexture.class.getConstructor(Supplier.class, NativeImage.class);
            return ctor.newInstance((Supplier<String>) id::toString, image);
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Constructor<DynamicTexture> ctor = DynamicTexture.class.getConstructor(NativeImage.class);
            return ctor.newInstance(image);
        } catch (NoSuchMethodException ignored) {
        }
        Constructor<DynamicTexture> ctor = DynamicTexture.class.getConstructor(String.class, NativeImage.class);
        return ctor.newInstance(id.toString(), image);
    }

    private static void injectLang() {
        Map<String, String> extras = new HashMap<>();
        try (InputStream in = open("assets/twoptwotvoice/lang/en_us.json")) {
            if (in == null) {
                return;
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (e.getValue() != null && e.getValue().isJsonPrimitive()) {
                    extras.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to load classpath lang: {}", e.toString());
            return;
        }
        if (extras.isEmpty()) {
            return;
        }
        Language current = Language.getInstance();
        boolean need = false;
        for (String key : extras.keySet()) {
            if (!current.has(key)) {
                need = true;
                break;
            }
        }
        if (!need) {
            return;
        }
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                String v = extras.get(key);
                if (v != null) {
                    return v;
                }
                return current.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return extras.containsKey(key) || current.has(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return current.isDefaultRightToLeft();
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return current.getVisualOrder(text);
            }
        });
    }

    private static InputStream open(String classpath) {
        ClassLoader cl = ClasspathAssets.class.getClassLoader();
        InputStream in = cl.getResourceAsStream(classpath);
        if (in != null) {
            return in;
        }
        return cl.getResourceAsStream("/" + classpath);
    }
}
