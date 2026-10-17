package io.github.marodriguezd.taskflow.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service managing audio completion notifications. Automatically provisions default completion
 * sound to user data directory and handles customizable user sounds with graceful fallbacks.
 */
public class SoundService {

    private static final Logger log = LoggerFactory.getLogger(SoundService.class);
    private final Path userSoundPath;
    private final Consumer<Path> playbackDispatcher;
    private AudioClip cachedAudioClip;
    private Path lastLoadedSoundPath;
    private Path fallbackSoundPath;
    private Path probedSoundPath;
    private long probedSoundModified = Long.MIN_VALUE;
    private boolean probedSoundPlayable = true;
    private volatile boolean soundEnabled = true;

    public SoundService(PlatformService platformService) {
        this(platformService, null, true);
    }

    SoundService(
            PlatformService platformService,
            Consumer<Path> playbackDispatcher,
            boolean provisionDefaultSound) {
        this(
                Objects.requireNonNull(platformService).getUserSoundPath(),
                playbackDispatcher,
                provisionDefaultSound);
    }

    /** Test seam: a sound path that never touches the real user data directory. */
    SoundService(
            Path userSoundPath, Consumer<Path> playbackDispatcher, boolean provisionDefaultSound) {
        this.userSoundPath = Objects.requireNonNull(userSoundPath);
        this.playbackDispatcher =
                playbackDispatcher == null ? this::dispatchPlayback : playbackDispatcher;
        if (provisionDefaultSound) {
            ensureDefaultSoundExists();
        }
    }

    /** Ensures default bell.mp3 is copied to user directory if not already customized. */
    public void ensureDefaultSoundExists() {
        Path userSound = userSoundPath;
        if (Files.exists(userSound)) {
            return;
        }

        try (InputStream in = getClass().getResourceAsStream("/assets/bell.mp3")) {
            if (in != null) {
                Files.createDirectories(userSound.getParent());
                Files.copy(in, userSound, StandardCopyOption.REPLACE_EXISTING);
                log.info("Provisioned default bell sound to {}", userSound);
            } else {
                log.warn("Default bell.mp3 resource not found in classpath.");
            }
        } catch (Exception e) {
            log.warn("Failed to copy default bell.mp3 to {}: {}", userSound, e.getMessage());
        }
    }

    /** Plays the completion chime. Safe to call from any thread or headless environments. */
    public void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
    }

    public void playCompletionSound() {
        if (!soundEnabled) {
            log.debug("Completion sound disabled; skipping playback");
            return;
        }
        Path soundPath = resolveSoundPath();

        try {
            playbackDispatcher.accept(soundPath);
        } catch (IllegalStateException e) {
            // JavaFX may be unavailable in test/headless use; silently skip the cosmetic alert.
            log.debug("JavaFX is unavailable; completion audio was skipped: {}", e.getMessage());
        } catch (Exception e) {
            log.warn(
                    "Could not play completion sound (fallback beep will be used): {}",
                    e.getMessage());
            triggerBeepFallback();
        }
    }

    private void dispatchPlayback(Path soundPath) {
        if (Platform.isFxApplicationThread()) {
            playAudioInternal(soundPath);
        } else {
            Platform.runLater(
                    () -> {
                        if (soundEnabled) {
                            playAudioInternal(soundPath);
                        }
                    });
        }
    }

    private void playAudioInternal(Path soundPath) {
        try {
            Path playable = playableSound(soundPath);
            if (playable != null && Files.exists(playable)) {
                if (cachedAudioClip == null || !playable.equals(lastLoadedSoundPath)) {
                    cachedAudioClip = new AudioClip(playable.toUri().toString());
                    lastLoadedSoundPath = playable;
                }
                cachedAudioClip.setVolume(1.0);
                cachedAudioClip.play();
                log.debug("Played completion sound from {}", playable);
                return;
            }
        } catch (Throwable t) {
            // Covers everything throwable on this call stack. Undecodable compressed files are
            // caught earlier by the synchronous probe in playableSound(); a failure that still
            // happens asynchronously on JavaFX's internal clip thread (for example no audio
            // device) cannot be caught here, so it only reaches stderr and the beep fallback.
            log.warn("JavaFX media playback failed: {}", t.getMessage());
        }

        triggerBeepFallback();
    }

    private Path resolveSoundPath() {
        Path userSound = userSoundPath;
        if (Files.exists(userSound)) {
            return userSound;
        }
        return null;
    }

    /**
     * Returns a path the JavaFX media backend can actually decode.
     *
     * <p>Compressed formats are decoded through GStreamer, whose plugin set depends on the host. On
     * distributions newer than the bundled libav/GStreamer support, opening an MP3 fails with
     * {@code ERROR_MEDIA_AUDIO_FORMAT_UNSUPPORTED}; the failure is reported on a JavaFX clip
     * thread, so it cannot be handled at playback time. The short bundled WAV copy is decoded by
     * the pure-Java platform instead, so a completion still rings on those systems. A custom sound
     * that does decode is always used unchanged.
     */
    private Path playableSound(Path soundPath) {
        if (soundPath == null || isPlayable(soundPath)) {
            return soundPath;
        }
        Path fallback = ensureFallbackSound();
        if (fallback == null) {
            log.warn("No decodable fallback sound is available; trying {} anyway", soundPath);
            return soundPath;
        }
        log.warn(
                "JavaFX media cannot decode {}; using the bundled WAV fallback instead", soundPath);
        return fallback;
    }

    private boolean isPlayable(Path soundPath) {
        String name = soundPath.toString().toLowerCase(Locale.ROOT);
        if (!Files.exists(soundPath) || name.endsWith(".wav")) {
            return true;
        }
        if (name.endsWith(".mp3") && !looksLikeMpegAudio(soundPath)) {
            log.warn("{} does not contain a recognizable MPEG audio frame", soundPath);
            return false;
        }
        long modified;
        try {
            modified = Files.getLastModifiedTime(soundPath).toMillis();
        } catch (Exception e) {
            modified = Long.MIN_VALUE;
        }
        if (soundPath.equals(probedSoundPath) && modified == probedSoundModified) {
            return probedSoundPlayable;
        }

        boolean playable = true;
        try {
            // MediaPlayer construction is synchronous: an unsupported codec throws here instead
            // of surfacing asynchronously from the clip scheduler during playback.
            MediaPlayer probe = new MediaPlayer(new Media(soundPath.toUri().toString()));
            probe.dispose();
        } catch (RuntimeException | LinkageError e) {
            playable = false;
            log.warn("The JavaFX media backend cannot decode {}: {}", soundPath, e.getMessage());
        }
        probedSoundPath = soundPath;
        probedSoundModified = modified;
        probedSoundPlayable = playable;
        return playable;
    }

    /**
     * Cheap structural check for MPEG audio (including ID3v2-tagged files).
     *
     * <p>Creating a {@code MediaPlayer} can succeed for bytes that are not audio at all, with the
     * failure only surfacing later on JavaFX's clip thread. Scanning for an MPEG frame sync word
     * makes corrupt or unrelated files fall back deterministically instead of going silent.
     */
    private static boolean looksLikeMpegAudio(Path soundPath) {
        final int probeSize = 8192;
        byte[] head;
        try (InputStream in = Files.newInputStream(soundPath)) {
            head = in.readNBytes(probeSize);
        } catch (Exception e) {
            return true; // let the player try; an unreadable file is handled by the beep fallback
        }
        return containsMpegFrame(head);
    }

    static boolean containsMpegFrame(byte[] data) {
        int offset = 0;
        if (data.length >= 10 && data[0] == 'I' && data[1] == 'D' && data[2] == '3') {
            int size =
                    ((data[6] & 0x7f) << 21)
                            | ((data[7] & 0x7f) << 14)
                            | ((data[8] & 0x7f) << 7)
                            | (data[9] & 0x7f);
            offset = 10 + size;
        }
        if (offset >= data.length) {
            // The tag extends past the probe window: the frame may still be valid.
            return true;
        }
        for (int index = offset; index + 1 < data.length; index++) {
            if ((data[index] & 0xFF) == 0xFF && (data[index + 1] & 0xE0) == 0xE0) {
                return true;
            }
        }
        return false;
    }

    /** Materialises the bundled WAV fallback once, outside the user data directory. */
    private Path ensureFallbackSound() {
        if (fallbackSoundPath != null && Files.exists(fallbackSoundPath)) {
            return fallbackSoundPath;
        }
        try (InputStream in = getClass().getResourceAsStream("/assets/bell.wav")) {
            if (in == null) {
                log.warn("Bundled /assets/bell.wav fallback is missing");
                return null;
            }
            Path temp = Files.createTempFile("taskflow-bell-", ".wav");
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            temp.toFile().deleteOnExit();
            fallbackSoundPath = temp;
            return temp;
        } catch (Exception e) {
            log.warn("Could not prepare the bundled WAV fallback: {}", e.getMessage());
            return null;
        }
    }

    private void triggerBeepFallback() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Throwable ignored) {
            // In headless CI or minimal Linux, beep might not be available
        }
    }
}
