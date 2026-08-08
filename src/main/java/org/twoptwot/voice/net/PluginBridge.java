package org.twoptwot.voice.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.twoptwot.voice.ServerGate;
import org.twoptwot.voice.audio.VoiceController;

import java.nio.charset.StandardCharsets;

public final class PluginBridge {

    public static final Identifier CHANNEL_ID = Identifier.fromNamespaceAndPath("twoptwotvoice", "main");

    private final SignalingClient signaling;
    private final VoiceController controller;
    private boolean awaiting;

    public PluginBridge(SignalingClient signaling, VoiceController controller) {
        this.signaling = signaling;
        this.controller = controller;
    }

    public void register() {
        PayloadTypeRegistry.playS2C().register(VoicePluginPayload.TYPE, VoicePluginPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VoicePluginPayload.TYPE, VoicePluginPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(VoicePluginPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (!ServerGate.isAllowed()) {
                    return;
                }
                handleIncoming(payload.json());
            });
        });
    }

    public void requestSession() {
        if (!ServerGate.isAllowed()) {
            return;
        }
        if (!ClientPlayNetworking.canSend(VoicePluginPayload.TYPE)) {
            controller.setStatus("Waiting for server (install/update 2p2tCore)...");
            return;
        }
        awaiting = true;
        controller.setStatus("Requesting voice session...");
        JsonObject hello = new JsonObject();
        hello.addProperty("t", "hello");
        String version = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("twoptwotvoice")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        hello.addProperty("v", version);
        hello.addProperty("h", org.twoptwot.voice.ModIntegrity.jarSha256());
        hello.addProperty("s", org.twoptwot.voice.ModIntegrity.signature(version));
        ClientPlayNetworking.send(new VoicePluginPayload(hello.toString()));
    }

    private void handleIncoming(String json) {
        awaiting = false;
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String type = obj.has("t") ? obj.get("t").getAsString() : "";
            if ("error".equals(type)) {
                String err = obj.has("error") ? obj.get("error").getAsString() : "unknown";
                if ("unofficial_mod".equals(err)) {
                    controller.setStatus("Unofficial/modified voice mod — use a release jar from GitHub");
                } else {
                    controller.setStatus("Voice error: " + err);
                }
                return;
            }
            if (!"session".equals(type)) {
                return;
            }
            String sessionId = obj.get("sessionId").getAsString();
            String apiBase = obj.has("apiBase") ? obj.get("apiBase").getAsString() : "https://voice.2p2t.org";
            String wsUrl = obj.has("wsUrl")
                    ? obj.get("wsUrl").getAsString()
                    : apiBase.replace("https://", "wss://").replace("http://", "ws://") + "/ws";
            String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
            String name = obj.has("name") ? obj.get("name").getAsString() : "";
            controller.onSessionGranted(uuid, name, apiBase);
            signaling.connect(wsUrl, sessionId);
        } catch (Exception e) {
            controller.setStatus("Bad session payload");
        }
    }

    public record VoicePluginPayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<VoicePluginPayload> TYPE =
                new CustomPacketPayload.Type<>(CHANNEL_ID);

        public static final StreamCodec<FriendlyByteBuf, VoicePluginPayload> CODEC =
                StreamCodec.of(
                        (buf, value) -> buf.writeBytes(value.json.getBytes(StandardCharsets.UTF_8)),
                        buf -> {
                            byte[] data = new byte[buf.readableBytes()];
                            buf.readBytes(data);
                            return new VoicePluginPayload(new String(data, StandardCharsets.UTF_8));
                        }
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
