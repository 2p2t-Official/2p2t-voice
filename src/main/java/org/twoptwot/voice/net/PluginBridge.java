package org.twoptwot.voice.net;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.C2SPlayChannelEvents;
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

import java.nio.charset.StandardCharsets;

public final class PluginBridge {

    public static final Identifier CHANNEL_ID = Identifier.fromNamespaceAndPath("twoptwotvoice", "main");

    private static final int RETRY_INTERVAL_TICKS = 20;
    private static final int MAX_WAIT_TICKS = 200;

    private final SignalingClient signaling;
    private final VoiceController controller;
    private boolean awaiting;
    private boolean waitingForChannel;
    private int waitTicks;
    private boolean sessionRequested;

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

        C2SPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> {
            if (!channels.contains(CHANNEL_ID)) {
                return;
            }
            client.execute(() -> {
                VoiceDebug.log("channel REGISTER twoptwotvoice:main");
                if (!ServerGate.isAllowed()) {
                    return;
                }
                if (sessionRequested && !waitingForChannel) {
                    return;
                }
                waitingForChannel = false;
                waitTicks = 0;
                sendHello("channel-register");
            });
        });
    }

    public void onJoin() {
        waitTicks = 0;
        VoiceDebug.snapshot("join");
        if (sessionRequested && !waitingForChannel) {
            return;
        }
        requestSession(false);
    }

    public void onLeave() {
        waitingForChannel = false;
        waitTicks = 0;
        awaiting = false;
        sessionRequested = false;
    }

    public void tick() {
        if (!waitingForChannel || !ServerGate.isAllowed()) {
            return;
        }
        waitTicks++;
        if (waitTicks % RETRY_INTERVAL_TICKS != 0) {
            return;
        }
        boolean canSend = ClientPlayNetworking.canSend(VoicePluginPayload.TYPE);
        VoiceDebug.log("retry tick=" + waitTicks + " canSend=" + canSend);
        if (canSend) {
            waitingForChannel = false;
            waitTicks = 0;
            sendHello("retry");
            return;
        }
        if (waitTicks >= MAX_WAIT_TICKS) {
            waitingForChannel = false;
            VoiceDebug.snapshot("channel-timeout");
            controller.setStatus("No voice channel from server — check debug log / update 2p2tCore");
        } else {
            controller.setStatus("Waiting for voice channel… (" + (waitTicks / 20) + "s)");
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
            waitingForChannel = false;
            waitTicks = 0;
        }
        if (!ClientPlayNetworking.canSend(VoicePluginPayload.TYPE)) {
            VoiceDebug.snapshot(manual ? "canSend=false manual" : "canSend=false auto");
            waitingForChannel = true;
            waitTicks = 0;
            if (manual) {
                controller.setStatus("Voice channel not ready — waiting…");
            } else {
                controller.setStatus("Waiting for voice channel…");
            }
            return;
        }
        waitingForChannel = false;
        waitTicks = 0;
        sendHello(manual ? "manual" : "auto");
    }

    private void sendHello(String reason) {
        if (!ServerGate.isAllowed()) {
            return;
        }
        if (!ClientPlayNetworking.canSend(VoicePluginPayload.TYPE)) {
            VoiceDebug.log("sendHello aborted canSend=false reason=" + reason);
            waitingForChannel = true;
            return;
        }
        awaiting = true;
        sessionRequested = true;
        controller.setStatus("Requesting voice session...");
        JsonObject hello = new JsonObject();
        hello.addProperty("t", "hello");
        String version = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("twoptwotvoice")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        hello.addProperty("v", version);
        hello.addProperty("h", ModIntegrity.jarSha256());
        hello.addProperty("s", ModIntegrity.signature(version));
        String hash = ModIntegrity.jarSha256();
        VoiceDebug.log("hello sent reason=" + reason + " v=" + version
                + " hash=" + (hash.isEmpty() ? "-" : hash.substring(0, Math.min(12, hash.length())))
                + " signed=" + ModIntegrity.isSigned());
        ClientPlayNetworking.send(new VoicePluginPayload(hello.toString()));
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
            VoiceDebug.log("session ok uuid=" + uuid + " api=" + apiBase);
            controller.onSessionGranted(uuid, name, apiBase);
            signaling.connect(wsUrl, sessionId);
        } catch (Exception e) {
            VoiceDebug.log("bad session payload: " + e.getMessage());
            controller.setStatus("Bad session payload");
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
