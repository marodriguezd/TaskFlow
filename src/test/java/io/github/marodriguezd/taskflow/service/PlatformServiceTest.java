package io.github.marodriguezd.taskflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformServiceTest {

    @Test
    @DisplayName("Initializes data directory and detects current platform")
    void testPlatformService() {
        PlatformService platformService = new PlatformService();

        assertThat(platformService.getCurrentOs()).isNotNull();
        assertThat(platformService.getDataDirectory()).isNotNull();
        assertThat(platformService.getDatabasePath()).isNotNull();
        assertThat(platformService.getUserSoundPath()).isNotNull();

        if (platformService.isWindows()) {
            assertThat(platformService.getDefaultAlwaysOnTop()).isFalse();
            assertThat(platformService.usesFramelessWindow()).isFalse();
        } else if (platformService.isLinux()) {
            assertThat(platformService.getDefaultAlwaysOnTop()).isTrue();
            assertThat(platformService.usesFramelessWindow()).isTrue();
        }
    }
}
