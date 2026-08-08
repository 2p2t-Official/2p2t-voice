package org.twoptwot.voice.webrtc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.PeerConnectionFactory;
import dev.onvoid.webrtc.PeerConnectionObserver;
import dev.onvoid.webrtc.RTCAnswerOptions;
import dev.onvoid.webrtc.RTCConfiguration;
import dev.onvoid.webrtc.RTCIceCandidate;
import dev.onvoid.webrtc.RTCIceServer;
import dev.onvoid.webrtc.RTCIceTransportPolicy;
import dev.onvoid.webrtc.RTCOfferOptions;
import dev.onvoid.webrtc.RTCPeerConnection;
import dev.onvoid.webrtc.RTCPeerConnectionState;
import dev.onvoid.webrtc.RTCRtpTransceiver;
import dev.onvoid.webrtc.RTCRtpTransceiverDirection;
import dev.onvoid.webrtc.RTCSdpType;
import dev.onvoid.webrtc.RTCSessionDescription;
import dev.onvoid.webrtc.RTCStats;
import dev.onvoid.webrtc.RTCStatsCollectorCallback;
import dev.onvoid.webrtc.RTCStatsReport;
import dev.onvoid.webrtc.RTCStatsType;
import dev.onvoid.webrtc.SetSessionDescriptionObserver;
import dev.onvoid.webrtc.media.MediaStreamTrack;
import dev.onvoid.webrtc.media.audio.AudioTrack;
import dev.onvoid.webrtc.media.audio.HeadlessAudioDeviceModule;
import org.twoptwot.voice.audio.VoiceController;
import org.twoptwot.voice.net.SignalingClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WebRtcEngine {

    private static final Logger LOG = Logger.getLogger("twoptwotvoice");

    private final VoiceController controller;
    private final SignalingClient signaling;
    private final Map<String, PeerConnection> peers = new ConcurrentHashMap<>();

    private volatile PeerConnectionFactory factory;
    private volatile HeadlessAudioDeviceModule adm;
    private volatile JavaSoundAudio javaSound;
    private volatile AudioTrack localTrack;
    private volatile RTCConfiguration rtcConfig = new RTCConfiguration();
    private volatile boolean available;
    private volatile String initError = "not_started";
    private volatile boolean startAttempted;
    private volatile String pathSummary = "path:?";
    private volatile long lastStatsMs;

    public WebRtcEngine(VoiceController controller, SignalingClient signaling) {
        this.controller = controller;
        this.signaling = signaling;
        List<RTCIceServer> defaults = new ArrayList<>();
        RTCIceServer stun = new RTCIceServer();
        stun.urls.add("stun:stun.l.google.com:19302");
        defaults.add(stun);
        rtcConfig.iceServers = defaults;
        
        rtcConfig.iceTransportPolicy = RTCIceTransportPolicy.ALL;
    }

    public synchronized boolean ensureStarted() {
        if (available && factory != null && javaSound != null) {
            return true;
        }
        if (startAttempted && !available) {
            return false;
        }
        startAttempted = true;
        tryInitFactory();
        return available;
    }

    private void tryInitFactory() {
        try {
            WebRtcNatives.ensureLoaded();

            
            
            
            HeadlessAudioDeviceModule module = new HeadlessAudioDeviceModule();
            module.initRecording();
            module.startRecording();
            module.initPlayout();
            module.startPlayout();
            adm = module;

            factory = new PeerConnectionFactory(module);
            javaSound = new JavaSoundAudio();
            javaSound.setMicGain(controller.config().micVolume);
            localTrack = factory.createAudioTrack("microphone", javaSound.source());
            localTrack.setEnabled(false);
            javaSound.start();

            available = true;
            initError = null;
            LOG.info("WebRTC ready (JavaSound I/O + headless ADM)");
            syncLocalMic();
        } catch (Throwable t) {
            available = false;
            initError = WebRtcNatives.flatten(t);
            LOG.log(Level.SEVERE, "WebRTC init failed: " + initError, t);
            disposeAudio();
        }
    }

    public void setIceServers(JsonArray arr) {
        if (arr == null || arr.isEmpty()) {
            return;
        }
        List<RTCIceServer> servers = new ArrayList<>();
        for (JsonElement el : arr) {
            try {
                if (el == null || !el.isJsonObject()) {
                    continue;
                }
                JsonObject o = el.getAsJsonObject();
                String username = o.has("username") && o.get("username").isJsonPrimitive()
                        ? o.get("username").getAsString() : null;
                String credential = o.has("credential") && o.get("credential").isJsonPrimitive()
                        ? o.get("credential").getAsString() : null;
                List<String> urls = new ArrayList<>();
                if (o.has("urls") && !o.get("urls").isJsonNull()) {
                    if (o.get("urls").isJsonArray()) {
                        for (JsonElement u : o.getAsJsonArray("urls")) {
                            if (u != null && u.isJsonPrimitive()) {
                                urls.add(u.getAsString());
                            }
                        }
                    } else if (o.get("urls").isJsonPrimitive()) {
                        urls.add(o.get("urls").getAsString());
                    }
                }
                
                for (String url : urls) {
                    if (url == null || url.isBlank()) {
                        continue;
                    }
                    RTCIceServer server = new RTCIceServer();
                    server.urls.add(url);
                    if (username != null) {
                        server.username = username;
                    }
                    if (credential != null) {
                        server.password = credential;
                    }
                    servers.add(server);
                }
            } catch (Exception ignored) {
            }
        }
        if (servers.isEmpty()) {
            return;
        }
        rtcConfig.iceServers = servers;
        
        if (!peers.isEmpty()) {
            List<String[]> snapshot = new ArrayList<>();
            for (Map.Entry<String, PeerConnection> e : peers.entrySet()) {
                snapshot.add(new String[]{e.getKey(), e.getValue().name});
            }
            clearPeers();
            for (String[] pair : snapshot) {
                ensurePeer(pair[0], pair[1]);
            }
        }
    }

    public void syncLocalMic() {
        if (localTrack == null || javaSound == null) {
            return;
        }
        boolean transmit = controller.isTransmitting();
        boolean openMic = !"ptt".equalsIgnoreCase(controller.getMode());
        try {
            localTrack.setEnabled(transmit);
        } catch (Throwable ignored) {
        }
        javaSound.setCaptureEnabled(transmit);
        javaSound.setMicGain(controller.config().micVolume);
        javaSound.setVadThreshold(controller.config().normalizedVadThreshold());
        
        javaSound.setVadGate(transmit && openMic);
        if (controller.isDeafened()) {
            for (String id : peers.keySet()) {
                javaSound.setPeerVolume(id, 0f);
            }
        }
    }

    public boolean isLocalSpeaking() {
        if (javaSound == null) {
            return false;
        }
        if ("ptt".equalsIgnoreCase(controller.getMode())) {
            return controller.isTransmitting();
        }
        return controller.isTransmitting() && javaSound.isVadSpeaking();
    }

    public float localMicLevel() {
        return javaSound == null ? 0f : javaSound.lastMicLevel();
    }

    public void ensurePeer(String uuid, String name) {
        if (!ensureStarted()) {
            return;
        }
        if (uuid == null || uuid.isBlank() || uuid.equalsIgnoreCase(controller.getUuid())) {
            return;
        }
        
        if (isSelfEchoPeer(uuid, name)) {
            removePeer(uuid);
            return;
        }
        PeerConnection existing = peers.get(uuid);
        if (existing != null) {
            if (existing.needsRestart()) {
                removePeer(uuid);
            } else {
                return;
            }
        }
        peers.computeIfAbsent(uuid, id -> {
            PeerConnection pc = new PeerConnection(id, name == null ? id : name);
            pc.open();
            if (shouldOffer(id)) {
                pc.createAndSendOffer();
            }
            return pc;
        });
    }

    private boolean isSelfEchoPeer(String uuid, String name) {
        String mine = controller.getName();
        if (mine != null && !mine.isBlank() && name != null && mine.equalsIgnoreCase(name.trim())) {
            return true;
        }
        String myUuid = controller.getUuid();
        if (myUuid != null && uuid != null) {
            String a = myUuid.toLowerCase(Locale.ROOT);
            String b = uuid.toLowerCase(Locale.ROOT);
            if (a.equals(b)) {
                return true;
            }
            
        }
        return false;
    }

    public void refreshOffers() {
        if (!ensureStarted()) {
            return;
        }
        for (Map.Entry<String, PeerConnection> e : peers.entrySet()) {
            if (shouldOffer(e.getKey())) {
                e.getValue().maybeOffer();
            }
        }
    }

    public void removePeer(String uuid) {
        PeerConnection pc = peers.remove(uuid);
        if (pc != null) {
            pc.close();
        }
        if (javaSound != null) {
            javaSound.removePeer(uuid);
        }
    }

    public void clearPeers() {
        for (PeerConnection pc : peers.values()) {
            pc.close();
        }
        peers.clear();
        pathSummary = "path:none";
        if (javaSound != null) {
            javaSound.clearPeers();
        }
    }

    public synchronized void shutdown() {
        if (!available && !startAttempted && factory == null && javaSound == null && adm == null && peers.isEmpty()) {
            return;
        }
        try {
            clearPeers();
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "clearPeers during shutdown failed", t);
        }
        try {
            disposeAudio();
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "disposeAudio during shutdown failed", t);
        }
        available = false;
        startAttempted = false;
        initError = "not_started";
    }

    public void applyPeerVolume(String uuid, float volume) {
        PeerConnection pc = peers.get(uuid);
        if (pc != null) {
            pc.setOutputVolume(volume);
        }
        if (javaSound != null) {
            javaSound.setPeerVolume(uuid, controller.isDeafened() ? 0f : volume);
        }
    }

    public void onRemoteSignal(String from, String name, JsonObject data) {
        if (data == null || !ensureStarted()) {
            if (!available) {
                LOG.warning("Ignoring signal — WebRTC unavailable (" + initError + ")");
            }
            return;
        }
        if (isSelfEchoPeer(from, name)) {
            return;
        }
        ensurePeer(from, name);
        PeerConnection pc = peers.get(from);
        if (pc == null) {
            return;
        }
        if (data.has("description") && data.get("description").isJsonObject()) {
            JsonObject desc = data.getAsJsonObject("description");
            if (!desc.has("type") || desc.get("type").isJsonNull()
                    || !desc.has("sdp") || desc.get("sdp").isJsonNull()) {
                return;
            }
            String type = desc.get("type").getAsString();
            String sdp = desc.get("sdp").getAsString();
            if (sdp.isBlank()) {
                return;
            }
            RTCSdpType sdpType = "offer".equals(type) ? RTCSdpType.OFFER : RTCSdpType.ANSWER;
            LOG.info("Remote " + type + " from " + from);
            pc.setRemoteDescription(new RTCSessionDescription(sdpType, sdp), type);
        } else if (data.has("candidate") && data.get("candidate").isJsonObject()) {
            JsonObject c = data.getAsJsonObject("candidate");
            String cand = c.has("candidate") && c.get("candidate").isJsonPrimitive()
                    ? c.get("candidate").getAsString() : null;
            if (cand == null || cand.isBlank()) {
                return;
            }
            String mid = c.has("sdpMid") && c.get("sdpMid").isJsonPrimitive()
                    ? c.get("sdpMid").getAsString() : null;
            int index = 0;
            if (c.has("sdpMLineIndex") && c.get("sdpMLineIndex").isJsonPrimitive()) {
                try {
                    index = c.get("sdpMLineIndex").getAsInt();
                } catch (Exception ignored) {
                    index = 0;
                }
            }
            pc.addIceCandidate(new RTCIceCandidate(mid, index, cand));
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String initError() {
        return initError;
    }

    public int connectedPeerCount() {
        int n = 0;
        for (PeerConnection pc : peers.values()) {
            if (pc.isConnected()) {
                n++;
            }
        }
        return n;
    }

    
    public String pathSummary() {
        maybeRefreshPathStats();
        return pathSummary;
    }

    private void maybeRefreshPathStats() {
        long now = System.currentTimeMillis();
        if (now - lastStatsMs < 2000L) {
            return;
        }
        lastStatsMs = now;
        if (peers.isEmpty()) {
            pathSummary = "path:none";
            return;
        }
        for (PeerConnection peer : peers.values()) {
            peer.requestPathStats();
        }
    }

    private void updatePathSummaryFromPeer(String peerPath) {
        if (peerPath == null || peerPath.isBlank()) {
            return;
        }
        String normalized = peerPath.toLowerCase(Locale.ROOT);
        if (normalized.contains("relay")) {
            pathSummary = "path:relay";
            return;
        }
        
        if (pathSummary.contains("relay")) {
            return;
        }
        if (normalized.contains("srflx")) {
            pathSummary = "path:srflx";
        } else if (normalized.contains("prflx")) {
            pathSummary = "path:prflx";
        } else if (normalized.contains("host")) {
            if (!pathSummary.contains("srflx") && !pathSummary.contains("prflx")) {
                pathSummary = "path:host";
            }
        } else {
            pathSummary = "path:" + normalized;
        }
    }

    private boolean shouldOffer(String peerUuid) {
        String mine = controller.getUuid();
        if (mine == null || mine.isBlank()) {
            return false;
        }
        return mine.compareToIgnoreCase(peerUuid) < 0;
    }

    private void disposeAudio() {
        
        JavaSoundAudio sound = javaSound;
        javaSound = null;
        try {
            if (sound != null) {
                sound.stop();
            }
        } catch (Throwable ignored) {
        }
        
        try {
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (localTrack != null) {
                localTrack.setEnabled(false);
                localTrack.dispose();
            }
        } catch (Throwable ignored) {
        }
        localTrack = null;
        HeadlessAudioDeviceModule module = adm;
        adm = null;
        try {
            if (module != null) {
                try {
                    module.stopRecording();
                } catch (Throwable ignored) {
                }
                try {
                    module.stopPlayout();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (factory != null) {
                factory.dispose();
            }
        } catch (Throwable ignored) {
        }
        factory = null;
        try {
            if (module != null) {
                module.dispose();
            }
        } catch (Throwable ignored) {
        }
    }

    private final class PeerConnection {
        private final String uuid;
        private final String name;
        private RTCPeerConnection pc;
        private float outputVolume = 1f;
        private volatile boolean remoteDescriptionSet;
        private volatile boolean offered;
        private int iceFailRetries;
        private final List<RTCIceCandidate> pendingIce = new CopyOnWriteArrayList<>();

        private PeerConnection(String uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }

        private void open() {
            pc = factory.createPeerConnection(rtcConfig, new PeerObserver(this));
            if (localTrack != null) {
                try {
                    pc.addTrack(localTrack, List.of("2p2t"));
                } catch (Exception e) {
                    LOG.warning("addTrack failed for " + uuid + ": " + e.getMessage());
                }
            }
            syncLocalMic();
        }

        private void applyConfig() {
            if (pc != null) {
                try {
                    pc.setConfiguration(rtcConfig);
                } catch (Exception ignored) {
                }
            }
        }

        private void maybeOffer() {
            if (!offered) {
                createAndSendOffer();
            }
        }

        private void createAndSendOffer() {
            if (pc == null || offered) {
                return;
            }
            offered = true;
            LOG.info("Creating offer for " + uuid);
            pc.createOffer(new RTCOfferOptions(), new CreateSessionDescriptionObserver() {
                @Override
                public void onSuccess(RTCSessionDescription description) {
                    pc.setLocalDescription(description, new SetSessionDescriptionObserver() {
                        @Override
                        public void onSuccess() {
                            sendDescription(description);
                        }

                        @Override
                        public void onFailure(String error) {
                            offered = false;
                            LOG.warning("setLocalDescription offer failed: " + error);
                        }
                    });
                }

                @Override
                public void onFailure(String error) {
                    offered = false;
                    LOG.warning("createOffer failed: " + error);
                }
            });
        }

        private void setRemoteDescription(RTCSessionDescription description, String type) {
            if (pc == null) {
                return;
            }
            pc.setRemoteDescription(description, new SetSessionDescriptionObserver() {
                @Override
                public void onSuccess() {
                    remoteDescriptionSet = true;
                    flushPendingIce();
                    if ("offer".equals(type)) {
                        LOG.info("Creating answer for " + uuid);
                        pc.createAnswer(new RTCAnswerOptions(), new CreateSessionDescriptionObserver() {
                            @Override
                            public void onSuccess(RTCSessionDescription answer) {
                                pc.setLocalDescription(answer, new SetSessionDescriptionObserver() {
                                    @Override
                                    public void onSuccess() {
                                        sendDescription(answer);
                                    }

                                    @Override
                                    public void onFailure(String error) {
                                        LOG.warning("setLocalDescription answer failed: " + error);
                                    }
                                });
                            }

                            @Override
                            public void onFailure(String error) {
                                LOG.warning("createAnswer failed: " + error);
                            }
                        });
                    }
                }

                @Override
                public void onFailure(String error) {
                    LOG.warning("setRemoteDescription failed: " + error);
                }
            });
        }

        private void addIceCandidate(RTCIceCandidate candidate) {
            if (pc == null) {
                return;
            }
            if (!remoteDescriptionSet) {
                pendingIce.add(candidate);
                return;
            }
            try {
                pc.addIceCandidate(candidate);
            } catch (Exception e) {
                LOG.warning("addIceCandidate failed: " + e.getMessage());
            }
        }

        private void flushPendingIce() {
            for (RTCIceCandidate candidate : pendingIce) {
                try {
                    pc.addIceCandidate(candidate);
                } catch (Exception e) {
                    LOG.warning("flush ICE failed: " + e.getMessage());
                }
            }
            pendingIce.clear();
        }

        private void sendDescription(RTCSessionDescription description) {
            JsonObject data = new JsonObject();
            JsonObject desc = new JsonObject();
            desc.addProperty("type", description.sdpType == RTCSdpType.OFFER ? "offer" : "answer");
            desc.addProperty("sdp", description.sdp);
            data.add("description", desc);
            LOG.info("Sending " + desc.get("type").getAsString() + " to " + uuid);
            signaling.sendSignal(uuid, data);
        }

        private void sendIce(RTCIceCandidate candidate) {
            JsonObject data = new JsonObject();
            JsonObject c = new JsonObject();
            c.addProperty("candidate", candidate.sdp);
            if (candidate.sdpMid != null) {
                c.addProperty("sdpMid", candidate.sdpMid);
            }
            c.addProperty("sdpMLineIndex", candidate.sdpMLineIndex);
            data.add("candidate", c);
            signaling.sendSignal(uuid, data);
        }

        private void setOutputVolume(float volume) {
            this.outputVolume = Math.max(0f, Math.min(2f, volume));
            if (javaSound != null) {
                javaSound.setPeerVolume(uuid, controller.isDeafened() ? 0f : this.outputVolume);
            }
        }

        private void attachRemote(MediaStreamTrack track) {
            if (!(track instanceof AudioTrack audio) || javaSound == null) {
                return;
            }
            
            if (isLocalMicTrack(audio)) {
                LOG.warning("Refusing to play local mic track as remote for " + name);
                return;
            }
            javaSound.attachRemote(uuid, audio, controller.isDeafened() ? 0f : outputVolume);
            LOG.info("Remote audio track from " + name + " vol=" + outputVolume);
        }

        private boolean isLocalMicTrack(AudioTrack audio) {
            if (audio == null) {
                return true;
            }
            
            return localTrack != null && (audio == localTrack || audio.equals(localTrack));
        }

        private boolean isConnected() {
            if (pc == null) {
                return false;
            }
            try {
                return pc.getConnectionState() == RTCPeerConnectionState.CONNECTED;
            } catch (Exception e) {
                return false;
            }
        }

        private boolean needsRestart() {
            if (pc == null) {
                return true;
            }
            try {
                RTCPeerConnectionState state = pc.getConnectionState();
                return state == RTCPeerConnectionState.FAILED
                        || state == RTCPeerConnectionState.CLOSED;
            } catch (Exception e) {
                return true;
            }
        }

        private void requestPathStats() {
            if (pc == null || !isConnected()) {
                return;
            }
            try {
                pc.getStats(new RTCStatsCollectorCallback() {
                    @Override
                    public void onStatsDelivered(RTCStatsReport report) {
                        String path = extractSelectedPath(report);
                        if (path != null) {
                            updatePathSummaryFromPeer(path);
                            LOG.info("ICE path for " + uuid + ": " + path);
                        }
                    }
                });
            } catch (Throwable t) {
                LOG.log(Level.FINE, "getStats failed for " + uuid, t);
            }
        }

        private static String extractSelectedPath(RTCStatsReport report) {
            if (report == null || report.getStats() == null) {
                return null;
            }
            Map<String, RTCStats> all = report.getStats();
            String localCandId = null;
            for (RTCStats stats : all.values()) {
                if (stats.getType() != RTCStatsType.CANDIDATE_PAIR) {
                    continue;
                }
                Map<String, Object> attrs = stats.getAttributes();
                if (attrs == null) {
                    continue;
                }
                Object nominated = attrs.get("nominated");
                Object state = attrs.get("state");
                boolean ok = Boolean.TRUE.equals(nominated)
                        || "succeeded".equals(String.valueOf(state))
                        || "in-progress".equals(String.valueOf(state));
                if (!ok && nominated == null && state == null) {
                    
                    Object selected = attrs.get("selected");
                    ok = Boolean.TRUE.equals(selected);
                }
                if (!ok) {
                    continue;
                }
                Object localId = attrs.get("localCandidateId");
                if (localId != null) {
                    localCandId = String.valueOf(localId);
                    break;
                }
            }
            if (localCandId == null) {
                return null;
            }
            RTCStats local = all.get(localCandId);
            if (local == null) {
                for (RTCStats stats : all.values()) {
                    if (stats.getType() == RTCStatsType.LOCAL_CANDIDATE && localCandId.equals(stats.getId())) {
                        local = stats;
                        break;
                    }
                }
            }
            if (local == null || local.getAttributes() == null) {
                return null;
            }
            Object type = local.getAttributes().get("candidateType");
            if (type == null) {
                type = local.getAttributes().get("type");
            }
            if (type == null) {
                return null;
            }
            String value = String.valueOf(type).toLowerCase(Locale.ROOT);
            if (value.contains("relay")) {
                return "relay";
            }
            if (value.contains("srflx")) {
                return "srflx";
            }
            if (value.contains("prflx")) {
                return "prflx";
            }
            if (value.contains("host")) {
                return "host";
            }
            return value;
        }

        private void close() {
            pendingIce.clear();
            if (javaSound != null) {
                javaSound.removePeer(uuid);
            }
            if (pc != null) {
                try {
                    pc.close();
                } catch (Exception ignored) {
                }
                pc = null;
            }
        }
    }

    private final class PeerObserver implements PeerConnectionObserver {
        private final PeerConnection owner;

        private PeerObserver(PeerConnection owner) {
            this.owner = owner;
        }

        @Override
        public void onIceCandidate(RTCIceCandidate candidate) {
            owner.sendIce(candidate);
        }

        @Override
        public void onConnectionChange(RTCPeerConnectionState state) {
            LOG.info("PC state " + owner.uuid + " -> " + state);
            if (state == RTCPeerConnectionState.CONNECTED) {
                owner.iceFailRetries = 0;
                controller.setStatus("Voice linked (" + connectedPeerCount() + " peers)");
            } else if (state == RTCPeerConnectionState.FAILED) {
                controller.setStatus("Voice ICE failed — retrying " + owner.name);
                String id = owner.uuid;
                String name = owner.name;
                int retries = owner.iceFailRetries;
                removePeer(id);
                if (retries < 3) {
                    peers.computeIfAbsent(id, ignored -> {
                        PeerConnection pc = new PeerConnection(id, name);
                        pc.iceFailRetries = retries + 1;
                        pc.open();
                        if (shouldOffer(id)) {
                            pc.createAndSendOffer();
                        }
                        return pc;
                    });
                }
            }
        }

        @Override
        public void onTrack(RTCRtpTransceiver transceiver) {
            if (transceiver == null || transceiver.stopped()) {
                return;
            }
            try {
                RTCRtpTransceiverDirection direction = transceiver.getCurrentDirection();
                if (direction == null) {
                    direction = transceiver.getDirection();
                }
                if (direction == RTCRtpTransceiverDirection.SEND_ONLY
                        || direction == RTCRtpTransceiverDirection.INACTIVE
                        || direction == RTCRtpTransceiverDirection.STOPPED) {
                    return;
                }
            } catch (Throwable ignored) {
            }
            if (transceiver.getReceiver() == null) {
                return;
            }
            MediaStreamTrack track = transceiver.getReceiver().getTrack();
            if (track == null) {
                return;
            }
            try {
                if (transceiver.getSender() != null) {
                    MediaStreamTrack senderTrack = transceiver.getSender().getTrack();
                    if (senderTrack != null && (senderTrack == track || senderTrack.equals(track))) {
                        LOG.warning("Ignoring onTrack that points at sender/local track");
                        return;
                    }
                }
            } catch (Throwable ignored) {
            }
            owner.attachRemote(track);
        }
    }
}
