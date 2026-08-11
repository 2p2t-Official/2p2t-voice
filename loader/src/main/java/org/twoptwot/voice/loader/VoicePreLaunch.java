package org.twoptwot.voice.loader;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Path;

public final class VoicePreLaunch implements PreLaunchEntrypoint {

    @Override
    public void onPreLaunch() {
        if (LoaderState.voiceModAlreadyPresent()) {
            LoaderState.markSkippedDirectJar();
            LoaderState.LOG.info("Full 2p2t Voice jar already loaded — loader standing down");
            return;
        }
        try {
            Path jar = PayloadFetcher.ensurePayload();
            KnotInjector.addJar(jar);
            String version = PayloadFetcher.readPayloadVersion(jar);
            LoaderState.markInjected(jar, version);
            LoaderState.LOG.info("2p2t Voice {} injected for this launch", version);
        } catch (Throwable t) {
            String msg = t.getMessage() == null ? t.toString() : t.getMessage();
            LoaderState.setError(msg);
            showFailureDialog(msg);
        }
    }

    private static void showFailureDialog(String detail) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            SwingUtilities.invokeAndWait(() -> JOptionPane.showMessageDialog(
                    null,
                    "2p2t Voice could not load.\n\n" + detail + "\n\nThe game will continue without voice.",
                    "2p2t Voice Loader",
                    JOptionPane.WARNING_MESSAGE));
        } catch (Throwable ignored) {
        }
    }
}
