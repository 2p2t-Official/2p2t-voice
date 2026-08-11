package org.twoptwot.voice.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.twoptwot.voice.ModIntegrity;
import org.twoptwot.voice.ServerGate;
import org.twoptwot.voice.VoiceDebug;
import org.twoptwot.voice.audio.VoiceController;
import org.twoptwot.voice.update.ModUpdater;

import java.nio.charset.StandardCharsets;

public final class PluginBridge {

    public static final Identifier CHANNEL_ID = Identifier.fromNamespaceAndPath("twoptwotvoice", "main");

    private static final int RETRY_INTERVAL_TICKS = 40;
    private static final int GIVE_UP_TICKS = 200;

    private final SignalingClient signaling;
    private final VoiceController controller;
    private boolean awaiting;
    private int waitTicks;
    private boolean sessionRequested;
    private int nextHelloAtTick;

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

    public void onJoin() {
        waitTicks = 0;
        nextHelloAtTick = 0;
        sessionRequested = false;
        awaiting = false;
        VoiceDebug.snapshot("join canSend=" + ClientPlayNetworking.canSend(VoicePluginPayload.TYPE));
        requestSession(false);
    }

    public void onLeave() {
        waitTicks = 0;
        nextHelloAtTick = 0;
        awaiting = false;
        sessionRequested = false;
    }

    public void tick() {
        if (!awaiting || !ServerGate.isAllowed()) {
            return;
        }
        waitTicks++;
        if (waitTicks >= GIVE_UP_TICKS) {
            awaiting = false;
            VoiceDebug.snapshot("hello-timeout canSend=" + ClientPlayNetworking.canSend(VoicePluginPayload.TYPE));
            controller.setStatus("Voice is unavailable");
            return;
        }
        if (waitTicks >= nextHelloAtTick) {
            VoiceDebug.log("hello attempt tick=" + waitTicks
                    + " canSend=" + ClientPlayNetworking.canSend(VoicePluginPayload.TYPE));
            sendHello(waitTicks == 0 ? "auto" : "retry", false);
        }
    }

    public void requestSession() {
        requestSession(true);
    }

    public void requestSession(boolean manual) {
        if (!ServerGate.isAllowed()) {
            VoiceDebug.log("requestSession blocked by ServerGate");
            return;
        }
        if (manual) {
            sessionRequested = false;
            awaiting = false;
            waitTicks = 0;
            nextHelloAtTick = 0;
        }
        sendHello(manual ? "manual" : "auto", true);
    }

    private void sendHello(String reason, boolean resetWait) {
        if (!ServerGate.isAllowed()) {
            return;
        }
        awaiting = true;
        sessionRequested = true;
        if (resetWait) {
            waitTicks = 0;
        }
        nextHelloAtTick = waitTicks + RETRY_INTERVAL_TICKS;
        controller.setStatus("Connecting…");

        if (!ClientPlayNetworking.canSend(VoicePluginPayload.TYPE)) {
            VoiceDebug.log("hello deferred reason=" + reason + " canSend=false");
            return;
        }

        JsonObject hello = new JsonObject();
        hello.addProperty("t", "hello");
        String version = ModUpdater.installedVersion();
        hello.addProperty("v", version);
        hello.addProperty("h", ModIntegrity.jarSha256());
        hello.addProperty("s", ModIntegrity.signature(version));
        String hash = ModIntegrity.jarSha256();
        VoiceDebug.log("hello sent reason=" + reason
                + " canSend=true"
                + " v=" + version
                + " hash=" + (hash.isEmpty() ? "-" : hash.substring(0, Math.min(12, hash.length())))
                + " signed=" + ModIntegrity.isSigned());
        try {
            ClientPlayNetworking.send(new VoicePluginPayload(hello.toString()));
        } catch (Exception e) {
            VoiceDebug.log("hello send failed: " + e.getMessage());
        }
    }

    private void handleIncoming(String json) {
        awaiting = false;
        VoiceDebug.log("s2c len=" + (json == null ? 0 : json.length())
                + " preview=" + preview(json));
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String type = obj.has("t") ? obj.get("t").getAsString() : "";
            if ("error".equals(type)) {
                String err = obj.has("error") ? obj.get("error").getAsString() : "unknown";
                VoiceDebug.log("error=" + err);
                if ("unofficial_mod".equals(err)) {
                    controller.setStatus("This mod build is not allowed. Download the official release.");
                } else {
                    controller.setStatus("Couldn't start voice: " + err);
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
            VoiceDebug.log("session ok uuid=" + uuid + " api=" + apiBase);
            controller.onSessionGranted(uuid, name, apiBase);
            signaling.connect(wsUrl, sessionId);
        } catch (Exception e) {
            VoiceDebug.log("bad session payload: " + e.getMessage());
            controller.setStatus("Couldn't start voice.");
        }
    }

    private static String preview(String json) {
        if (json == null) {
            return "";
        }
        String trimmed = json.replace('\n', ' ').trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120) + "…";
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
