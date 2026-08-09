package org.twoptwot.voice.audio;

import net.minecraft.client.Minecraft;
import org.twoptwot.voice.VoiceConfig;
import org.twoptwot.voice.TwoptwotVoiceClient;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class VoiceController {

    private final VoiceConfig config;

    private String uuid = "";
    private String name = "";
    private String apiBase = "https://voice.2p2t.org";
    private String status = "Voice idle";
    private String channel = "global";
    private boolean connected;
    private boolean muted;
    private boolean deafened;
    private boolean pttHeld;
    private boolean speaking;
    private boolean serverMuted;
    private boolean admin;
    private final Set<String> permissions = new HashSet<>();
    private int peerCount;
    private long lastMicSendMs;

    public VoiceController(VoiceConfig config) {
        this.config = config;
    }

    public void onJoinWorld() {
        status = "Joined world — requesting voice...";
    }

    public void onLeaveWorld() {
        connected = false;
        status = "Left server";
        speaking = false;
        pttHeld = false;
    }

    public void onSessionGranted(String uuid, String name, String apiBase) {
        this.uuid = uuid == null ? "" : uuid;
        this.name = name == null ? "" : name;
        if (apiBase != null && !apiBase.isBlank()) {
            this.apiBase = apiBase;
        }
    }

    public void tick(Minecraft client) {
        if (client.player == null) {
            return;
        }

        boolean liveSpeaking = TwoptwotVoiceClient.get().webRtc().isLocalSpeaking();
        if (liveSpeaking != speaking) {
            speaking = liveSpeaking;
            TwoptwotVoiceClient.get().signaling().sendMic();
        }

        if ("spawn".equals(channel) && !isWithinSpawnRange(client)) {
            setChannel("lobby", true);
            setStatus(SPAWN_TOO_FAR_LEFT);
        }
        long now = System.currentTimeMillis();
        if (connected && now - lastMicSendMs > 250L) {
            lastMicSendMs = now;
            TwoptwotVoiceClient.get().webRtc().syncLocalMic();
            TwoptwotVoiceClient.get().signaling().sendMic();
        }
    }

    public static final double SPAWN_CHANNEL_RADIUS = 1000.0;
    public static final String SPAWN_TOO_FAR_JOIN =
            "You must be within 1000 blocks of 0, 0 to join Spawn.";
    public static final String SPAWN_TOO_FAR_LEFT =
            "Left Spawn — you must stay within 1000 blocks of 0, 0.";

    public static boolean isWithinSpawnRange(Minecraft client) {
        if (client == null || client.player == null) {
            return false;
        }
        double x = client.player.getX();
        double z = client.player.getZ();
        return Math.hypot(x, z) <= SPAWN_CHANNEL_RADIUS;
    }

    public boolean trySetChannel(String next, boolean notifyServer) {
        if ("spawn".equals(next) && !isWithinSpawnRange(Minecraft.getInstance())) {
            setStatus(SPAWN_TOO_FAR_JOIN);
            return false;
        }
        if ("staff".equals(next) && !canAccessStaffChannel()) {
            setStatus("Staff channel is for staff only.");
            return false;
        }
        if ("lobby".equals(next) && !admin) {
            setStatus("Lobby is staff-only. Use Leave Channel to wait there.");
            return false;
        }
        setChannel(next, notifyServer);
        setStatus("Joined " + org.twoptwot.voice.ui.VoiceUi.channelTitle(next) + ".");
        return true;
    }

    public void leaveChannel(boolean notifyServer) {
        setChannel("lobby", notifyServer);
        setStatus("Left channel — waiting in lobby.");
    }

    public boolean canAccessStaffChannel() {
        return permissions.contains("staff_channel");
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public void setPermissions(Iterable<String> next) {
        permissions.clear();
        if (next == null) {
            return;
        }
        for (String permission : next) {
            if (permission != null && !permission.isBlank()) {
                permissions.add(permission);
            }
        }
    }

    public Set<String> getPermissions() {
        return Collections.unmodifiableSet(permissions);
    }

    public void setChannel(String channel, boolean notifyServer) {
        this.channel = channel == null || channel.isBlank() ? "global" : channel;
        if (notifyServer) {

            TwoptwotVoiceClient.get().webRtc().clearPeers();
            TwoptwotVoiceClient.get().signaling().sendSettings();
        }
    }

    public boolean isTransmitting() {
        if (muted || serverMuted || deafened) {
            return false;
        }

        if ("ptt".equalsIgnoreCase(config.mode)) {
            return pttHeld;
        }
        return true;
    }

    public void toggleMute() {
        muted = !muted;
        if (muted) {
            speaking = false;
        }
        TwoptwotVoiceClient.get().webRtc().syncLocalMic();
        TwoptwotVoiceClient.get().signaling().sendMic();
        config.save();
    }

    public void toggleDeafen() {
        deafened = !deafened;
        if (deafened) {
            muted = true;
            speaking = false;
        }
        TwoptwotVoiceClient.get().webRtc().syncLocalMic();
        TwoptwotVoiceClient.get().signaling().sendMic();
    }

    public void setPttHeld(boolean held) {
        this.pttHeld = held;
        TwoptwotVoiceClient.get().webRtc().syncLocalMic();
        TwoptwotVoiceClient.get().signaling().sendMic();
    }

    public void setSpeaking(boolean speaking) {
        this.speaking = speaking;
    }

    public void setProximityRange(int range) {
        config.proximityRange = Math.max(4, Math.min(48, range));
        config.save();
        TwoptwotVoiceClient.get().signaling().sendSettings();
    }

    public int getProximityRange() {
        return config.proximityRange;
    }

    public float getMasterVolume() {
        return config.masterVolume;
    }

    public void setMasterVolume(float v) {
        config.masterVolume = Math.max(0f, Math.min(2f, v));
        config.save();
    }

    public String getMode() {
        return config.mode;
    }

    public void setMode(String mode) {
        config.mode = mode;
        config.save();
    }

    public String getChannel() {
        return channel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean isDeafened() {
        return deafened;
    }

    public boolean isSpeaking() {
        return speaking;
    }

    public boolean isServerMuted() {
        return serverMuted;
    }

    public void setServerMuted(boolean serverMuted) {
        this.serverMuted = serverMuted;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApiBase() {
        return apiBase;
    }

    public int getPeerCount() {
        return peerCount;
    }

    public void setPeerCount(int peerCount) {
        this.peerCount = peerCount;
    }

    public VoiceConfig config() {
        return config;
    }
}
