package services;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public final class AudioCallManager {
    @FunctionalInterface
    public interface AudioFrameSender {
        void send(String peerUsername, String callId, byte[] audioBytes);
    }

    private static final AudioFormat AUDIO_FORMAT = new AudioFormat(16_000.0f, 16, 1, true, false);
    private static final int FRAME_BYTES = 640;

    private final AudioFrameSender frameSender;
    private final ExecutorService playbackExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "voice-call-playback");
        thread.setDaemon(true);
        return thread;
    });
    private final Object lifecycleLock = new Object();

    private volatile boolean running;
    private volatile String activePeerUsername = "";
    private volatile String activeCallId = "";
    private TargetDataLine microphoneLine;
    private SourceDataLine speakerLine;
    private Thread captureThread;

    public AudioCallManager(AudioFrameSender frameSender) {
        this.frameSender = frameSender;
    }

    public boolean startCall(String peerUsername, String callId) {
        String normalizedPeer = normalizeUsername(peerUsername);
        String safeCallId = callId == null ? "" : callId.trim();
        if (normalizedPeer.isBlank() || safeCallId.isBlank() || frameSender == null) {
            return false;
        }

        synchronized (lifecycleLock) {
            if (running
                    && normalizedPeer.equals(activePeerUsername)
                    && safeCallId.equals(activeCallId)) {
                return true;
            }

            stopLocked();
            try {
                DataLine.Info microphoneInfo = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
                DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
                microphoneLine = (TargetDataLine) AudioSystem.getLine(microphoneInfo);
                speakerLine = (SourceDataLine) AudioSystem.getLine(speakerInfo);

                microphoneLine.open(AUDIO_FORMAT);
                speakerLine.open(AUDIO_FORMAT);
                microphoneLine.start();
                speakerLine.start();

                activePeerUsername = normalizedPeer;
                activeCallId = safeCallId;
                running = true;

                captureThread = new Thread(this::captureLoop, "voice-call-capture");
                captureThread.setDaemon(true);
                captureThread.start();
                return true;
            } catch (LineUnavailableException ex) {
                stopLocked();
                return false;
            }
        }
    }

    public void handleIncomingAudio(String fromUsername, String callId, byte[] audioBytes) {
        if (!running || audioBytes == null || audioBytes.length == 0) {
            return;
        }

        String normalizedPeer = normalizeUsername(fromUsername);
        String safeCallId = callId == null ? "" : callId.trim();
        if (!normalizedPeer.equals(activePeerUsername) || !safeCallId.equals(activeCallId)) {
            return;
        }

        byte[] frameCopy = Arrays.copyOf(audioBytes, audioBytes.length);
        playbackExecutor.execute(() -> {
            SourceDataLine currentSpeakerLine = speakerLine;
            if (!running || currentSpeakerLine == null) {
                return;
            }

            currentSpeakerLine.write(frameCopy, 0, frameCopy.length);
        });
    }

    public void stop() {
        synchronized (lifecycleLock) {
            stopLocked();
        }
    }

    public void shutdown() {
        stop();
        playbackExecutor.shutdownNow();
    }

    public boolean isRunning() {
        return running;
    }

    private void captureLoop() {
        byte[] buffer = new byte[FRAME_BYTES];
        while (running) {
            TargetDataLine currentMicrophoneLine = microphoneLine;
            if (currentMicrophoneLine == null) {
                break;
            }

            int read = currentMicrophoneLine.read(buffer, 0, buffer.length);
            if (read <= 0) {
                continue;
            }

            frameSender.send(activePeerUsername, activeCallId, Arrays.copyOf(buffer, read));
        }
    }

    private void stopLocked() {
        running = false;
        activePeerUsername = "";
        activeCallId = "";

        Thread currentCaptureThread = captureThread;
        captureThread = null;
        if (currentCaptureThread != null) {
            currentCaptureThread.interrupt();
        }

        if (microphoneLine != null) {
            microphoneLine.stop();
            microphoneLine.close();
            microphoneLine = null;
        }

        if (speakerLine != null) {
            speakerLine.stop();
            speakerLine.close();
            speakerLine = null;
        }
    }

    private static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
