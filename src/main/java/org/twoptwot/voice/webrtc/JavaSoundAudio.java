package org.twoptwot.voice.webrtc;

import dev.onvoid.webrtc.media.audio.AudioTrack;
import dev.onvoid.webrtc.media.audio.AudioTrackSink;
import dev.onvoid.webrtc.media.audio.CustomAudioSource;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JavaSoundAudio {

    private static final Logger LOG = Logger.getLogger("twoptwotvoice");
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNELS = 1;
    private static final int BITS = 16;
    private static final int BYTES_PER_SAMPLE = BITS / 8;
    private static final int FRAME_SAMPLES = SAMPLE_RATE / 100; 
    private static final int FRAME_BYTES = FRAME_SAMPLES * CHANNELS * BYTES_PER_SAMPLE;

    private final CustomAudioSource customSource = new CustomAudioSource();
    private final Object pushLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean captureEnabled = new AtomicBoolean();
    private final AtomicBoolean sourceAlive = new AtomicBoolean(true);
    private final Map<String, PeerPlayback> playbacks = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<PlaybackPacket> playbackQueue = new ArrayBlockingQueue<>(64);

    private Thread captureThread;
    private Thread playbackThread;
    private volatile float micGain = 1f;
    
    private volatile boolean vadGate = false;
    private volatile float vadThreshold = 0.02f;
    private volatile boolean vadSpeaking;
    private volatile float lastMicLevel;
    private float smoothedLevel;
    private float noiseFloor = 0.003f;
    private long speakingUntilMs;

    public CustomAudioSource source() {
        return customSource;
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        sourceAlive.set(true);
        captureThread = new Thread(this::captureLoop, "twoptwotvoice-mic");
        captureThread.setDaemon(true);
        captureThread.start();
        playbackThread = new Thread(this::playbackLoop, "twoptwotvoice-spk");
        playbackThread.setDaemon(true);
        playbackThread.start();
        LOG.info("JavaSound audio bridge started");
    }

    public synchronized void stop() {
        running.set(false);
        captureEnabled.set(false);
        
        synchronized (pushLock) {
            sourceAlive.set(false);
        }
        playbackQueue.clear();
        playbackQueue.offer(PlaybackPacket.POISON);

        joinQuiet(captureThread, 800);
        joinQuiet(playbackThread, 800);
        captureThread = null;
        playbackThread = null;

        for (PeerPlayback playback : playbacks.values()) {
            playback.close();
        }
        playbacks.clear();
        synchronized (pushLock) {
            try {
                customSource.dispose();
            } catch (Throwable ignored) {
            }
        }
        LOG.info("JavaSound audio bridge stopped");
    }

    public void setCaptureEnabled(boolean enabled) {
        captureEnabled.set(enabled);
    }

    public void setMicGain(float gain) {
        this.micGain = Math.max(0f, Math.min(2f, gain));
    }

    public void setVadGate(boolean enabled) {
        this.vadGate = enabled;
        if (!enabled) {
            vadSpeaking = false;
            speakingUntilMs = 0L;
        }
    }

    public void setVadThreshold(float threshold) {
        this.vadThreshold = Math.max(0.002f, Math.min(0.08f, threshold));
    }

    public boolean isVadSpeaking() {
        return vadSpeaking;
    }

    public float lastMicLevel() {
        return lastMicLevel;
    }

    public void attachRemote(String peerId, AudioTrack track, float initialVolume) {
        if (track == null || peerId == null || !running.get()) {
            return;
        }
        PeerPlayback existing = playbacks.remove(peerId);
        if (existing != null) {
            existing.close();
            try {
                track.removeSink(existing);
            } catch (Throwable ignored) {
            }
        }
        PeerPlayback playback = new PeerPlayback(peerId, initialVolume);
        playbacks.put(peerId, playback);
        try {
            track.addSink(playback);
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "addSink failed for " + peerId, t);
            playbacks.remove(peerId, playback);
            playback.close();
        }
    }

    public void setPeerVolume(String peerId, float volume) {
        PeerPlayback playback = playbacks.get(peerId);
        if (playback != null) {
            playback.setVolume(volume);
        }
    }

    public void removePeer(String peerId) {
        PeerPlayback playback = playbacks.remove(peerId);
        if (playback != null) {
            playback.close();
        }
    }

    public void clearPeers() {
        for (PeerPlayback playback : playbacks.values()) {
            playback.close();
        }
        playbacks.clear();
        playbackQueue.clear();
    }

    private void captureLoop() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, BITS, CHANNELS, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine line = null;
        byte[] buffer = new byte[FRAME_BYTES];
        byte[] pushBuf = new byte[FRAME_BYTES];
        long nextPushAt = System.nanoTime();
        try {
            if (!AudioSystem.isLineSupported(info)) {
                LOG.warning("No TargetDataLine for " + format);
                return;
            }
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format, FRAME_BYTES * 8);
            line.start();

            while (running.get()) {
                int read = 0;
                while (read < FRAME_BYTES && running.get()) {
                    int n = line.read(buffer, read, FRAME_BYTES - read);
                    if (n <= 0) {
                        break;
                    }
                    read += n;
                }
                if (!running.get() || !sourceAlive.get()) {
                    break;
                }
                if (read < FRAME_BYTES) {
                    for (int i = read; i < FRAME_BYTES; i++) {
                        buffer[i] = 0;
                    }
                }

                float level = rmsLevel(buffer, FRAME_BYTES);
                lastMicLevel = level;
                boolean speaking = updateVad(level);

                
                
                boolean pushLive = captureEnabled.get() && micGain > 0.001f && (!vadGate || speaking);
                if (!pushLive) {
                    java.util.Arrays.fill(pushBuf, (byte) 0);
                } else if (micGain < 0.999f || micGain > 1.001f) {
                    applyGainInto(buffer, pushBuf, FRAME_BYTES, micGain);
                } else {
                    System.arraycopy(buffer, 0, pushBuf, 0, FRAME_BYTES);
                }

                synchronized (pushLock) {
                    if (sourceAlive.get()) {
                        try {
                            customSource.pushAudio(pushBuf, BITS, SAMPLE_RATE, CHANNELS, FRAME_SAMPLES);
                        } catch (Throwable t) {
                            LOG.log(Level.WARNING, "pushAudio failed: " + t.getMessage(), t);
                            sleep(20);
                        }
                    }
                }

                
                nextPushAt += 10_000_000L;
                long sleepNs = nextPushAt - System.nanoTime();
                if (sleepNs > 1_000_000L) {
                    sleep(sleepNs / 1_000_000L);
                } else if (sleepNs < -20_000_000L) {
                    nextPushAt = System.nanoTime();
                }
            }
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "Mic capture failed: " + e.getMessage(), e);
        } finally {
            if (line != null) {
                try {
                    line.stop();
                    line.flush();
                    line.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void playbackLoop() {
        try {
            while (running.get()) {
                PlaybackPacket packet;
                try {
                    packet = playbackQueue.poll(50, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (packet == null) {
                    continue;
                }
                if (packet == PlaybackPacket.POISON) {
                    break;
                }
                PeerPlayback playback = playbacks.get(packet.peerId);
                if (playback == null || playback.closed) {
                    continue;
                }
                playback.writeOnPlaybackThread(packet.data, packet.sampleRate, packet.channels, packet.bits);
            }
        } catch (Throwable t) {
            LOG.log(Level.WARNING, "Playback loop failed: " + t.getMessage(), t);
        } finally {
            for (PeerPlayback playback : playbacks.values()) {
                playback.close();
            }
        }
    }

    private static void joinQuiet(Thread t, long ms) {
        if (t == null) {
            return;
        }
        try {
            t.join(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    
    private static float rmsLevel(byte[] pcm, int len) {
        if (pcm == null || len < 2) {
            return 0f;
        }
        double sum = 0;
        int samples = 0;
        for (int i = 0; i + 1 < len; i += 2) {
            int sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            double n = sample / 32768.0;
            sum += n * n;
            samples++;
        }
        if (samples == 0) {
            return 0f;
        }
        return (float) Math.sqrt(sum / samples);
    }

    
    private boolean updateVad(float level) {
        smoothedLevel = smoothedLevel > 0f ? smoothedLevel * 0.68f + level * 0.32f : level;
        float base = vadThreshold > 0f ? vadThreshold : 0.02f;
        float adaptive = Math.max(base, Math.max(noiseFloor * 1.65f, 0.0025f));
        float release = Math.max(adaptive * 0.55f, Math.max(noiseFloor * 1.15f, 0.0018f));
        long now = System.currentTimeMillis();
        boolean wasSpeaking = vadSpeaking || now < speakingUntilMs;
        boolean starts = smoothedLevel >= adaptive;
        boolean keeps = wasSpeaking && smoothedLevel >= release;
        boolean speaking = starts || keeps;
        if (speaking) {
            speakingUntilMs = now + 650L;
            vadSpeaking = true;
        } else {
            noiseFloor = clamp(noiseFloor * 0.995f + smoothedLevel * 0.005f, 0.0015f, 0.04f);
            vadSpeaking = now < speakingUntilMs;
        }
        return vadSpeaking;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static byte[] applyGain(byte[] pcm, int len, float gain) {
        byte[] out = new byte[len];
        applyGainInto(pcm, out, len, gain);
        return out;
    }

    private static void applyGainInto(byte[] pcm, byte[] out, int len, float gain) {
        for (int i = 0; i + 1 < len; i += 2) {
            int sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            sample = Math.round(sample * gain);
            if (sample > Short.MAX_VALUE) {
                sample = Short.MAX_VALUE;
            } else if (sample < Short.MIN_VALUE) {
                sample = Short.MIN_VALUE;
            }
            out[i] = (byte) (sample & 0xff);
            out[i + 1] = (byte) ((sample >> 8) & 0xff);
        }
    }

    private final class PeerPlayback implements AudioTrackSink {
        private final String peerId;
        private volatile float volume;
        private volatile boolean closed;
        private SourceDataLine line;
        private int lineRate = -1;
        private int lineChannels = -1;
        private int lineBits = -1;

        private PeerPlayback(String peerId, float volume) {
            this.peerId = peerId;
            this.volume = Math.max(0f, Math.min(2f, volume));
        }

        private void setVolume(float volume) {
            this.volume = Math.max(0f, Math.min(2f, volume));
        }

        @Override
        public void onData(byte[] data, int bitsPerSample, int sampleRate, int channels, int frames) {
            
            if (closed || !running.get() || volume <= 0.01f || data == null || data.length == 0) {
                return;
            }
            byte[] copy = data.clone();
            if (volume < 0.999f || volume > 1.001f) {
                copy = applyGain(copy, copy.length, volume);
            }
            PlaybackPacket packet = new PlaybackPacket(peerId, copy, sampleRate, channels, bitsPerSample);
            if (!playbackQueue.offer(packet)) {
                playbackQueue.poll();
                playbackQueue.offer(packet);
            }
        }

        private synchronized void writeOnPlaybackThread(byte[] data, int sampleRate, int channels, int bits) {
            if (closed) {
                return;
            }
            if (line == null || lineRate != sampleRate || lineChannels != channels || lineBits != bits) {
                closeLine();
                openLine(sampleRate, channels, bits);
            }
            if (line == null) {
                return;
            }
            try {
                int off = 0;
                while (off < data.length && !closed) {
                    int written = line.write(data, off, data.length - off);
                    if (written <= 0) {
                        break;
                    }
                    off += written;
                }
            } catch (Throwable t) {
                LOG.log(Level.FINE, "playback write failed", t);
                closeLine();
            }
        }

        private void openLine(int sampleRate, int channels, int bits) {
            try {
                AudioFormat format = new AudioFormat(sampleRate, bits, channels, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                if (!AudioSystem.isLineSupported(info)) {
                    LOG.warning("No SourceDataLine for " + format);
                    return;
                }
                line = (SourceDataLine) AudioSystem.getLine(info);
                int buffer = Math.max(sampleRate / 20, 1024) * channels * (bits / 8);
                line.open(format, buffer);
                line.start();
                lineRate = sampleRate;
                lineChannels = channels;
                lineBits = bits;
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "Playback open failed for " + peerId + ": " + e.getMessage(), e);
                line = null;
            }
        }

        private void closeLine() {
            if (line != null) {
                try {
                    line.stop();
                    line.flush();
                    line.close();
                } catch (Throwable ignored) {
                }
                line = null;
            }
        }

        private synchronized void close() {
            closed = true;
            closeLine();
        }
    }

    private static final class PlaybackPacket {
        private static final PlaybackPacket POISON = new PlaybackPacket("", new byte[0], 0, 0, 0);

        private final String peerId;
        private final byte[] data;
        private final int sampleRate;
        private final int channels;
        private final int bits;

        private PlaybackPacket(String peerId, byte[] data, int sampleRate, int channels, int bits) {
            this.peerId = peerId;
            this.data = data;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.bits = bits;
        }
    }
}
