package config;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.android.AndroidStartScreenRecordingOptions;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.ios.IOSStartScreenRecordingOptions;
import io.qameta.allure.Allure;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;

/**
 * JUnit 5 extension that records the device screen for every test method (Android + iOS).
 *
 * Enable by setting  video.enabled=true  in appium.properties (or -Dvideo.enabled=true).
 * Videos are saved to  build/videos/{ClassName}/{TestDisplayName}.mp4
 * and attached to the Allure report automatically.
 *
 * Auto-registered via META-INF/services/org.junit.jupiter.api.extension.Extension
 * (requires junit.jupiter.extensions.autodetection.enabled=true in junit-platform.properties).
 */
public class VideoRecordingExtension implements BeforeEachCallback, AfterEachCallback {

    private static final Logger log = LoggerFactory.getLogger(VideoRecordingExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        if (!isEnabled()) return;
        try {
            AppiumDriver driver = DriverFactory.getDriver();
            if (DriverFactory.isIOS()) {
                ((IOSDriver) driver).startRecordingScreen(
                    new IOSStartScreenRecordingOptions()
                        .withVideoQuality(IOSStartScreenRecordingOptions.VideoQuality.MEDIUM)
                        .withTimeLimit(Duration.ofMinutes(15))
                );
            } else {
                ((AndroidDriver) driver).startRecordingScreen(
                    new AndroidStartScreenRecordingOptions()
                        .withBitRate(2_000_000)
                        .withTimeLimit(Duration.ofMinutes(15))
                );
            }
            log.info("[Video] Grabacion iniciada: {}", context.getDisplayName());
        } catch (Exception e) {
            log.warn("[Video] No se pudo iniciar grabacion: {}", e.getMessage());
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (!isEnabled()) return;
        try {
            AppiumDriver driver = DriverFactory.getDriver();
            String base64;
            if (DriverFactory.isIOS()) {
                base64 = ((IOSDriver) driver).stopRecordingScreen();
            } else {
                base64 = ((AndroidDriver) driver).stopRecordingScreen();
            }
            if (base64 == null || base64.isBlank()) return;

            byte[] videoBytes = Base64.getDecoder().decode(base64);

            String className = context.getTestClass()
                .map(Class::getSimpleName)
                .orElse("unknown");
            String testName = context.getDisplayName()
                .replaceAll("[^a-zA-Z0-9_\\-]", "_");

            Path dir  = Paths.get("build", "videos", className);
            Files.createDirectories(dir);
            Path file = dir.resolve(testName + ".mp4");
            Files.write(file, videoBytes);
            log.info("[Video] Guardado ({} KB): {}", videoBytes.length / 1024, file.toAbsolutePath());

            Allure.addAttachment(
                "Video — " + context.getDisplayName(),
                "video/mp4",
                new ByteArrayInputStream(videoBytes),
                ".mp4"
            );

        } catch (Exception e) {
            log.warn("[Video] No se pudo guardar grabacion: {}", e.getMessage());
        }
    }

    private static boolean isEnabled() {
        return "true".equalsIgnoreCase(DriverFactory.prop("video.enabled", "false"));
    }
}
