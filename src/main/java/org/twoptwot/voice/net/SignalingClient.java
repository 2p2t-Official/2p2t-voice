package org.twoptwot.voice.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.twoptwot.voice.audio.VoiceController;
import org.twoptwot.voice.webrtc.WebRtcEngine;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SignalingClient {

    private static final Logger LOG = Logger.getLogger("twoptwotvoice");

    private final VoiceController controller;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "2p2t-voice-ws");
        t.setDaemon(true);
        return t;
    });

    private volatile WebRtcEngine webRtc;
    private volatile WsClient ws;
    private volatile String sessionId;
    private volatile String wsUrl;

    public String sessionId() {
        return sessionId;
    }

    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();
    private final List<GroupInfo> groups = new CopyOnWriteArrayList<>();
    private final List<GroupInvite> pendingInvites = new CopyOnWriteArrayList<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public SignalingClient(VoiceController controller) {
        this.controller = controller;
    }

    public void setWebRtc(WebRtcEngine webRtc) {
        this.webRtc = webRtc;
    }

    public void connect(String wsUrl, String sessionId) {
        disconnect("reconnect");
        this.wsUrl = wsUrl;
        this.sessionId = sessionId;
        String full = wsUrl.contains("?") ? wsUrl + "&sessionId=" + sessionId : wsUrl + "?sessionId=" + sessionId;
        controller.setStatus("Connecting voice...");
        io.execute(() -> {
            try {
                WsClient client = new WsClient(URI.create(full));
                this.ws = client;
                client.connectBlocking();
            } catch (Exception e) {
                controller.setStatus("Voice WS failed: " + e.getMessage());
            }
        });
    }

    public void disconnect(String reason) {
        WsClient client = this.ws;
        this.ws = null;
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        peers.clear();
        groups.clear();
        if (webRtc != null) {
            webRtc.clearPeers();
        }
        controller.setConnected(false);
        if (reason != null && !"reconnect".equals(reason) && !"left_server".equals(reason)) {
            controller.setStatus("Voice disconnected (" + reason + ")");
        }
    }

    public void sendReady() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "ready");
        msg.addProperty("micGranted", true);
        msg.addProperty("micActive", controller.isTransmitting());
        msg.addProperty("muted", controller.isMuted());
        msg.addProperty("deafened", controller.isDeafened());
        msg.addProperty("speaking", controller.isSpeaking());
        send(msg);
        sendSettings();
        sendMic();
    }

    public void sendSettings() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "settings");
        msg.addProperty("channel", controller.getChannel());
        msg.addProperty("proximityRange", controller.getProximityRange());
        send(msg);
    }

    public void sendMic() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "mic");
        msg.addProperty("active", controller.isTransmitting());
        msg.addProperty("muted", controller.isMuted());
        msg.addProperty("deafened", controller.isDeafened());
        msg.addProperty("speaking", controller.isSpeaking());
        send(msg);
    }

    public void sendSignal(String toUuid, JsonObject data) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "signal");
        msg.addProperty("to", toUuid);
        msg.add("data", data);
        send(msg);
    }

    public void inviteToGroup(String groupId, String targetUuid, Runnable onSuccess, Consumer<String> onError) {
        io.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("sessionId", sessionId);
                body.addProperty("groupId", groupId);
                body.addProperty("targetUuid", targetUuid);
                JsonObject res = postJson(controller.getApiBase() + "/api/groups/invite", body);
                if (res == null || res.has("error")) {
                    if (onError != null) {
                        onError.accept(res != null && res.has("error") ? res.get("error").getAsString() : "invite_failed");
                    }
                    return;
                }
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(e.getMessage());
                }
            }
        });
    }

    public void respondInvite(String inviteId, boolean accept, Runnable onSuccess, Consumer<String> onError) {
        io.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("sessionId", sessionId);
                body.addProperty("inviteId", inviteId);
                body.addProperty("accept", accept);
                JsonObject res = postJson(controller.getApiBase() + "/api/groups/invite-respond", body);
                if (res == null || (res.has("error") && !res.get("error").isJsonNull())) {
                    if (onError != null) {
                        onError.accept(res != null && res.has("error") ? res.get("error").getAsString() : "invite_respond_failed");
                    }
                    return;
                }
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(e.getMessage());
                }
            }
        });
    }

    public void adminPost(String path, JsonObject body, Consumer<JsonObject> onSuccess, Consumer<String> onError) {
        final JsonObject payload = body == null ? new JsonObject() : body;
        io.execute(() -> {
            try {
                payload.addProperty("sessionId", sessionId);
                JsonObject res = postJson(controller.getApiBase() + path, payload);
                if (res == null) {
                    if (onError != null) {
                        onError.accept("request_failed");
                    }
                    return;
                }
                if (res.has("error") && !res.get("error").isJsonNull()) {
                    if (onError != null) {
                        onError.accept(res.get("error").getAsString());
                    }
                    return;
                }
                if (onSuccess != null) {
                    onSuccess.accept(res);
                }
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(e.getMessage());
                }
            }
        });
    }

    public void send(JsonObject msg) {
        WsClient client = this.ws;
        if (client == null || !client.isOpen()) {
            return;
        }
        client.send(msg.toString());
    }

    public Map<String, PeerInfo> peers() {
        return peers;
    }

    public List<GroupInfo> groups() {
        return groups;
    }

    public List<GroupInvite> pendingInvites() {
        return pendingInvites;
    }

    public GroupInvite pollInvite() {
        if (pendingInvites.isEmpty()) {
            return null;
        }
        return pendingInvites.remove(0);
    }

    public boolean isConnected() {
        WsClient client = this.ws;
        return client != null && client.isOpen();
    }

    public void refreshGroups() {
        String sid = sessionId;
        if (sid == null || sid.isBlank()) {
            return;
        }
        String apiBase = controller.getApiBase();
        io.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("sessionId", sid);
                JsonObject res = postJson(apiBase + "/api/groups/list", body);
                if (res != null) {
                    applyGroups(optArray(res, "groups"));
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Failed to refresh voice groups", e);
            }
        });
    }

    public void joinGroup(String groupId, Runnable onSuccess, Consumer<String> onError) {
        String sid = sessionId;
        if (sid == null || sid.isBlank()) {
            if (onError != null) {
                onError.accept("Not connected");
            }
            return;
        }
        String apiBase = controller.getApiBase();
        io.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("sessionId", sid);
                body.addProperty("groupId", groupId);
                JsonObject res = postJson(apiBase + "/api/groups/join", body);
                if (res == null || !res.has("group")) {
                    if (onError != null) {
                        onError.accept("Join failed");
                    }
                    return;
                }
                GroupInfo updated = GroupInfo.fromJson(res.getAsJsonObject("group"));
                if (updated != null) {
                    upsertGroup(updated);
                }
                refreshGroups();
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(e.getMessage() == null ? "Join failed" : e.getMessage());
                }
            }
        });
    }

    public void leaveGroup(String groupId, Runnable onSuccess, Consumer<String> onError) {
        groupApi("leave", groupId, null, null, onSuccess, onError);
    }

    public void deleteGroup(String groupId, Runnable onSuccess, Consumer<String> onError) {
        groupApi("delete", groupId, null, null, onSuccess, onError);
    }

    public void updateGroup(String groupId, String name, List<String> allowedNames,
                            Runnable onSuccess, Consumer<String> onError) {
        groupApi("update", groupId, name, allowedNames, onSuccess, onError);
    }

    private void groupApi(String action, String groupId, String name, List<String> allowedNames,
                          Runnable onSuccess, Consumer<String> onError) {
        String sid = sessionId;
        if (sid == null || sid.isBlank()) {
            if (onError != null) {
                onError.accept("Not connected");
            }
            return;
        }
        String apiBase = controller.getApiBase();
        io.execute(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("sessionId", sid);
                body.addProperty("groupId", groupId);
                if (name != null) {
                    body.addProperty("name", name);
                }
                if (allowedNames != null) {
                    JsonArray arr = new JsonArray();
                    for (String n : allowedNames) {
                        if (n != null && !n.isBlank()) {
                            arr.add(n.trim());
                        }
                    }
                    body.add("allowedNames", arr);
                }
                JsonObject res = postJson(apiBase + "/api/groups/" + action, body);
                if ("update".equals(action) && res != null && res.has("group")) {
                    GroupInfo updated = GroupInfo.fromJson(res.getAsJsonObject("group"));
                    if (updated != null) {
                        upsertGroup(updated);
                    }
                }
                if ("delete".equals(action) || "leave".equals(action)) {
                    if (groupId != null) {
                        groups.removeIf(g -> groupId.equals(g.id));
                    }
                }
                refreshGroups();
                if (onSuccess != null) {
                    onSuccess.run();
                }
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(e.getMessage() == null ? action + " failed" : e.getMessage());
                }
            }
        });
    }

    public void upsertGroup(GroupInfo info) {
        if (info == null || info.id == null || info.id.isBlank()) {
            return;
        }
        groups.removeIf(g -> info.id.equals(g.id));
        groups.add(info);
        groups.sort((a, b) -> {
            int pub = Boolean.compare(b.isPublic, a.isPublic);
            if (pub != 0) {
                return pub;
            }
            String an = a.name == null ? "" : a.name;
            String bn = b.name == null ? "" : b.name;
            return an.compareToIgnoreCase(bn);
        });
    }

    private JsonObject postJson(String url, JsonObject body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + res.statusCode() + ": " + res.body());
        }
        JsonElement parsed = JsonParser.parseString(res.body());
        if (parsed == null || !parsed.isJsonObject()) {
            return null;
        }
        return parsed.getAsJsonObject();
    }

    private void applyGroups(JsonArray arr) {
        List<GroupInfo> next = new ArrayList<>();
        if (arr != null) {
            for (JsonElement el : arr) {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                GroupInfo info = GroupInfo.fromJson(el.getAsJsonObject());
                if (info != null) {
                    next.add(info);
                }
            }
        }
        groups.clear();
        groups.addAll(next);
    }

    private void handleMessage(String raw) {
        JsonObject msg;
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            if (parsed == null || !parsed.isJsonObject()) {
                return;
            }
            msg = parsed.getAsJsonObject();
        } catch (Exception e) {
            return;
        }

        try {
            String type = optString(msg, "type", "");
            switch (type) {
                case "session" -> {
                    String uuid = optString(msg, "uuid", null);
                    if (uuid != null) {
                        controller.setUuid(uuid);
                    }
                    String name = optString(msg, "name", null);
                    if (name != null) {
                        controller.setName(name);
                    }
                    JsonArray ice = optArray(msg, "iceServers");
                    if (webRtc != null) {

                        if (ice != null) {
                            webRtc.setIceServers(ice);
                        }
                        webRtc.ensureStarted();
                    }
                    if (msg.has("voiceMuted") && !msg.get("voiceMuted").isJsonNull()) {
                        controller.setServerMuted(msg.get("voiceMuted").getAsBoolean());
                    }
                    if (msg.has("isAdmin") && !msg.get("isAdmin").isJsonNull()) {
                        controller.setAdmin(msg.get("isAdmin").getAsBoolean());
                    }
                    JsonArray perms = optArray(msg, "permissions");
                    if (perms != null) {
                        java.util.ArrayList<String> list = new java.util.ArrayList<>();
                        for (int i = 0; i < perms.size(); i++) {
                            if (perms.get(i).isJsonPrimitive()) {
                                list.add(perms.get(i).getAsString());
                            }
                        }
                        controller.setPermissions(list);
                    }
                    controller.setConnected(true);
                    if (webRtc != null && !webRtc.isAvailable()) {
                        controller.setStatus("Voice OK (no audio yet): " + webRtc.initError());
                    } else {
                        controller.setStatus("Voice connected");
                    }
                    sendReady();
                    refreshGroups();
                    if (webRtc != null) {
                        webRtc.refreshOffers();
                    }
                }
                case "peers" -> applyPeers(optArray(msg, "peers"));
                case "groups" -> applyGroups(optArray(msg, "groups"));
                case "signal" -> {
                    if (webRtc != null) {
                        String from = optString(msg, "from", null);
                        if (from == null || from.isBlank()) {
                            return;
                        }
                        webRtc.onRemoteSignal(
                                from,
                                optString(msg, "name", ""),
                                optObject(msg, "data")
                        );
                    }
                }
                case "peer-left" -> {
                    String uuid = optString(msg, "uuid", null);
                    if (uuid == null) {
                        return;
                    }
                    peers.remove(uuid);
                    if (webRtc != null) {
                        webRtc.removePeer(uuid);
                    }
                }
                case "proximity" -> applyProximity(msg);
                case "voice-muted", "self-mute-assigned" -> {
                    boolean muted = msg.has("muted") && !msg.get("muted").isJsonNull()
                            && msg.get("muted").getAsBoolean();
                    controller.setServerMuted(muted);
                }
                case "channel-assigned" -> {
                    String channel = optString(msg, "channel", null);
                    String reason = optString(msg, "reason", null);
                    if (channel != null) {
                        controller.setChannel(channel, false);
                        if ("spawn_too_far".equals(reason)) {
                            String detail = optString(msg, "message", null);
                            controller.setStatus(detail != null && !detail.isBlank()
                                    ? detail
                                    : org.twoptwot.voice.audio.VoiceController.SPAWN_TOO_FAR_LEFT);
                        } else if ("channel_forbidden".equals(reason)) {
                            String detail = optString(msg, "message", null);
                            controller.setStatus(detail != null && !detail.isBlank()
                                    ? detail
                                    : "You do not have permission for that channel.");
                        } else {
                            controller.setStatus("Joined "
                                    + org.twoptwot.voice.ui.VoiceUi.channelTitle(channel) + ".");
                        }
                    }
                }
                case "error" -> {
                    String err = optString(msg, "error", "?");
                    if ("spawn_too_far".equals(err)) {
                        String detail = optString(msg, "message", null);
                        controller.setStatus(detail != null && !detail.isBlank()
                                ? detail
                                : org.twoptwot.voice.audio.VoiceController.SPAWN_TOO_FAR_JOIN);
                    } else if ("channel_forbidden".equals(err)) {
                        String detail = optString(msg, "message", null);
                        controller.setStatus(detail != null && !detail.isBlank()
                                ? detail
                                : "You do not have permission for that channel.");
                    } else {
                        controller.setStatus("Voice error: " + err);
                    }
                }
                case "voice-blocked" -> {
                    controller.setStatus("Voice blocked: " + optString(msg, "reason", "not_in_game"));
                    disconnect("voice_blocked");
                }
                case "group-invite" -> {
                    GroupInvite invite = GroupInvite.fromJson(msg);
                    if (invite != null) {
                        pendingInvites.removeIf(i -> invite.inviteId.equals(i.inviteId));
                        pendingInvites.add(invite);
                        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                        if (mc != null) {
                            mc.execute(() -> {
                                if (mc.screen == null || !(mc.screen instanceof org.twoptwot.voice.ui.GroupInviteScreen)) {
                                    mc.setScreen(new org.twoptwot.voice.ui.GroupInviteScreen(mc.screen, invite));
                                }
                            });
                        }
                    }
                }
                case "group-invite-result" -> {
                    String status = optString(msg, "status", "");
                    String groupName = optString(msg, "groupName", "group");
                    String who = optString(msg, "name", "Player");
                    if ("accepted".equals(status)) {
                        controller.setStatus(who + " joined " + groupName);
                        refreshGroups();
                    } else if ("declined".equals(status)) {
                        controller.setStatus(who + " declined invite to " + groupName);
                    }
                }
                case "server-deafen-assigned" -> {
                    boolean deaf = msg.has("deafened") && !msg.get("deafened").isJsonNull()
                            && msg.get("deafened").getAsBoolean();
                    if (deaf && !controller.isDeafened()) {
                        controller.toggleDeafen();
                    } else if (!deaf && controller.isDeafened()) {
                        controller.toggleDeafen();
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {

            LOG.log(Level.WARNING, "Failed handling voice message: " + e.getMessage(), e);
            controller.setStatus("Voice msg error: " + e.getClass().getSimpleName());
        }
    }

    private void applyPeers(JsonArray arr) {
        if (arr == null) {
            return;
        }
        List<String> keep = new ArrayList<>();
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject p = el.getAsJsonObject();
            String uuid = optString(p, "uuid", null);
            if (uuid == null || uuid.isBlank()) {
                continue;
            }
            keep.add(uuid);
            PeerInfo info = peers.computeIfAbsent(uuid, PeerInfo::new);
            String name = optString(p, "name", null);
            if (name != null) {
                info.name = name;
            }
            info.muted = optBoolean(p, "muted", false);
            info.selfMuted = optBoolean(p, "selfMuted", false);
            info.serverMuted = optBoolean(p, "serverMuted", false);
            info.deafened = optBoolean(p, "deafened", false);
            info.speaking = optBoolean(p, "speaking", false);
            info.rosterOnly = optBoolean(p, "rosterOnly", false);
            info.hidden = optBoolean(p, "hidden", false);
            String channel = optString(p, "channel", null);
            if (channel != null) {
                info.channel = channel;
            }

            boolean samePerson = controller.getName() != null
                    && info.name != null
                    && controller.getName().equalsIgnoreCase(info.name);
            boolean shouldConnect = !info.rosterOnly
                    && !samePerson
                    && (info.hidden || controller.getChannel().equals(info.channel));
            if (webRtc != null) {
                if (shouldConnect) {
                    if (isFullVolumeChannel(controller.getChannel())) {
                        info.volume = 1f;
                    }
                    webRtc.ensurePeer(uuid, info.name);
                    webRtc.applyPeerVolume(uuid, info.effectiveVolume(controller));
                } else {
                    webRtc.removePeer(uuid);
                }
            }
        }
        peers.keySet().removeIf(id -> {
            if (!keep.contains(id)) {
                if (webRtc != null) {
                    webRtc.removePeer(id);
                }
                return true;
            }
            return false;
        });
        controller.setPeerCount((int) peers.values().stream().filter(p -> !p.rosterOnly).count());
    }

    private void applyProximity(JsonObject msg) {
        JsonArray players = optArray(msg, "players");
        if (players == null) {
            return;
        }
        boolean fullVolume = isFullVolumeChannel(controller.getChannel());
        for (JsonElement el : players) {
            if (el == null || !el.isJsonObject()) {
                continue;
            }
            JsonObject p = el.getAsJsonObject();
            String uuid = optString(p, "uuid", null);
            if (uuid == null) {
                continue;
            }
            PeerInfo info = peers.computeIfAbsent(uuid, PeerInfo::new);
            float volume = optFloat(p, "volume", 0f);

            if (fullVolume) {
                volume = 1f;
            }
            info.volume = volume;
            info.pan = optFloat(p, "pan", 0f);
            info.distance = optFloat(p, "distance", -1f);
            String channel = optString(p, "channel", null);
            if (channel != null) {
                info.channel = channel;
            }
            if (webRtc != null) {
                webRtc.applyPeerVolume(uuid, info.effectiveVolume(controller));
            }
        }
    }

    private static boolean isFullVolumeChannel(String channel) {
        return "global".equals(channel)
                || "spawn".equals(channel)
                || (channel != null && channel.startsWith("group:"));
    }

    private static String optString(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key)) {
            return fallback;
        }
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return el.getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean optBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj == null || !obj.has(key)) {
            return fallback;
        }
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return el.getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float optFloat(JsonObject obj, String key, float fallback) {
        if (obj == null || !obj.has(key)) {
            return fallback;
        }
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return el.getAsFloat();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static JsonArray optArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return null;
        }
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonArray()) {
            return null;
        }
        return el.getAsJsonArray();
    }

    private static JsonObject optObject(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)) {
            return null;
        }
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonObject()) {
            return null;
        }
        return el.getAsJsonObject();
    }

    private final class WsClient extends WebSocketClient {
        private WsClient(URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            controller.setStatus("Voice socket open");
        }

        @Override
        public void onMessage(String message) {
            handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            controller.setConnected(false);
            controller.setStatus("Voice closed (" + code + ")");
        }

        @Override
        public void onError(Exception ex) {
            String detail = ex == null ? "unknown" : String.valueOf(ex.getMessage());
            if (detail == null || detail.isBlank() || "null".equals(detail) || "JsonNull".equals(detail)) {
                detail = ex == null ? "unknown" : ex.getClass().getSimpleName();
            }
            LOG.log(Level.WARNING, "Voice WebSocket error", ex);
            controller.setStatus("Voice WS error: " + detail);
        }
    }

    public static final class GroupInvite {
        public String inviteId = "";
        public String groupId = "";
        public String groupName = "";
        public String fromUuid = "";
        public String fromName = "";

        public static GroupInvite fromJson(JsonObject obj) {
            if (obj == null) {
                return null;
            }
            String id = optString(obj, "inviteId", null);
            String groupId = optString(obj, "groupId", null);
            if (id == null || groupId == null) {
                return null;
            }
            GroupInvite invite = new GroupInvite();
            invite.inviteId = id;
            invite.groupId = groupId;
            invite.groupName = optString(obj, "groupName", groupId);
            invite.fromUuid = optString(obj, "fromUuid", "");
            invite.fromName = optString(obj, "fromName", "Player");
            return invite;
        }
    }

    public static final class PeerInfo {
        public final String uuid;
        public String name = "";
        public boolean muted;
        public boolean selfMuted;
        public boolean serverMuted;
        public boolean deafened;
        public boolean speaking;
        public String channel = "global";
        public float volume = 0f;
        public float pan = 0f;
        public float distance = -1f;
        public float localVolume = 1f;
        public boolean blocked;
        public boolean rosterOnly;
        public boolean hidden;

        public PeerInfo(String uuid) {
            this.uuid = uuid;
        }

        public float effectiveVolume(VoiceController controller) {
            if (blocked || controller.isDeafened()) {
                return 0f;
            }
            return Math.max(0f, Math.min(2f, volume * localVolume * controller.getMasterVolume()));
        }
    }

    public static final class GroupInfo {
        public String id = "";
        public String name = "";
        public String ownerName = "";
        public boolean isPublic;
        public boolean isOwner;
        public boolean joined;
        public int memberCount;
        public final List<String> allowedNames = new ArrayList<>();

        public String channelId() {
            return "group:" + id;
        }

        public static GroupInfo fromJson(JsonObject obj) {
            if (obj == null) {
                return null;
            }
            String id = optString(obj, "id", null);
            if (id == null || id.isBlank()) {
                return null;
            }
            GroupInfo info = new GroupInfo();
            info.id = id;
            info.name = optString(obj, "name", id);
            info.ownerName = optString(obj, "ownerName", "");
            info.isPublic = optBoolean(obj, "isPublic", true);
            info.isOwner = optBoolean(obj, "isOwner", false);
            info.joined = optBoolean(obj, "joined", false);
            info.memberCount = Math.max(0, (int) optFloat(obj, "memberCount", 0f));
            JsonArray allowed = optArray(obj, "allowedNames");
            if (allowed != null) {
                for (JsonElement el : allowed) {
                    if (el != null && el.isJsonPrimitive()) {
                        try {
                            String n = el.getAsString();
                            if (n != null && !n.isBlank()) {
                                info.allowedNames.add(n.trim());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            return info;
        }
    }
}
