package org.twoptwot.voice.webrtc;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;

public final class AudioDevices {

    public record Device(String id, String name, boolean input) {
    }

    private AudioDevices() {
    }

    public static List<Device> listInputs() {
        return list(true);
    }

    public static List<Device> listOutputs() {
        return list(false);
    }

    private static List<Device> list(boolean input) {
        List<Device> out = new ArrayList<>();
        out.add(new Device("", "System default", input));
        AudioFormat format = new AudioFormat(48000, 16, 1, true, false);
        Line.Info want = input
                ? new DataLine.Info(TargetDataLine.class, format)
                : new DataLine.Info(SourceDataLine.class, format);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (!mixer.isLineSupported(want)) {
                    continue;
                }
                String id = info.getName();
                if (id == null || id.isBlank()) {
                    continue;
                }
                String label = info.getName();
                String desc = info.getDescription();
                if (desc != null && !desc.isBlank() && !desc.equalsIgnoreCase(label)) {
                    label = label + " (" + desc + ")";
                }
                if (label.length() > 42) {
                    label = label.substring(0, 41) + "…";
                }
                out.add(new Device(id, label, input));
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    public static TargetDataLine openInput(String deviceId, AudioFormat format, int bufferBytes) throws Exception {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (deviceId != null && !deviceId.isBlank()) {
            Mixer.Info mixerInfo = findMixer(deviceId);
            if (mixerInfo != null) {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                TargetDataLine line = (TargetDataLine) mixer.getLine(info);
                line.open(format, bufferBytes);
                return line;
            }
        }
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format, bufferBytes);
        return line;
    }

    public static SourceDataLine openOutput(String deviceId, AudioFormat format, int bufferBytes) throws Exception {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        if (deviceId != null && !deviceId.isBlank()) {
            Mixer.Info mixerInfo = findMixer(deviceId);
            if (mixerInfo != null) {
                Mixer mixer = AudioSystem.getMixer(mixerInfo);
                SourceDataLine line = (SourceDataLine) mixer.getLine(info);
                line.open(format, bufferBytes);
                return line;
            }
        }
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format, bufferBytes);
        return line;
    }

    private static Mixer.Info findMixer(String deviceId) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (deviceId.equals(info.getName())) {
                return info;
            }
        }
        return null;
    }

    public static String cycleId(List<Device> devices, String current) {
        if (devices == null || devices.isEmpty()) {
            return "";
        }
        int idx = 0;
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).id().equals(current == null ? "" : current)) {
                idx = i;
                break;
            }
        }
        return devices.get((idx + 1) % devices.size()).id();
    }

    public static String labelFor(List<Device> devices, String id) {
        String want = id == null ? "" : id;
        for (Device d : devices) {
            if (d.id().equals(want)) {
                return d.name();
            }
        }
        return want.isBlank() ? "System default" : want;
    }
}
