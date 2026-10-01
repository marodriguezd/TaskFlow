package io.github.marodriguezd.taskflow.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javafx.application.Platform;
import javafx.scene.media.AudioClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service managing audio completion notifications. Automatically provisions default completion
 * sound to user data directory and handles customizable user sounds with graceful fallbacks.
 */
public class SoundService {

    private static final Logger log = LoggerFactory.getLogger(SoundService.class);
    private final PlatformService platformService;
    private AudioClip cachedAudioClip;
    private Path lastLoadedSoundPath;

    public SoundService(PlatformService platformService) {
        this.platformService = platformService;
        ensureDefaultSoundExists();
    }

    /** Ensures default bell.mp3 is copied to user directory if not already customized. */
    public void ensureDefaultSoundExists() {
        Path userSound = platformService.getUserSoundPath();
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
    public void playCompletionSound() {
        Path soundPath = resolveSoundPath();

        try {
            if (Platform.isFxApplicationThread()) {
                playAudioInternal(soundPath);
            } else {
                Platform.runLater(() -> playAudioInternal(soundPath));
            }
        } catch (Exception e) {
            log.warn(
                    "Could not play completion sound (fallback beep will be used): {}",
                    e.getMessage());
            triggerBeepFallback();
        }
    }

    private void playAudioInternal(Path soundPath) {
        try {
            if (soundPath != null && Files.exists(soundPath)) {
                if (cachedAudioClip == null || !soundPath.equals(lastLoadedSoundPath)) {
                    cachedAudioClip = new AudioClip(soundPath.toUri().toString());
                    lastLoadedSoundPath = soundPath;
                }
                cachedAudioClip.setVolume(1.0);
                cachedAudioClip.play();
                log.debug("Played completion sound from {}", soundPath);
                return;
            }
        } catch (Throwable t) {
            log.warn("JavaFX media playback failed: {}", t.getMessage());
        }

        triggerBeepFallback();
    }

    private Path resolveSoundPath() {
        Path userSound = platformService.getUserSoundPath();
        if (Files.exists(userSound)) {
            return userSound;
        }
        return null;
    }

    private void triggerBeepFallback() {
        try {
            java.awt.Toolkit.getDefaultToolkit().beep();
        } catch (Throwable ignored) {
            // In headless CI or minimal linux, beep might not be available
        }
    }
}
