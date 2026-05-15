package utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public class AllureMailRunner {

    private static final Logger log = LoggerFactory.getLogger(AllureMailRunner.class);

    private static final Path REPORT_DIR = Paths.get("build", "reportes-pdf");
    private static final Path METRICS_PATH = REPORT_DIR.resolve("suite-metrics.properties");
    private static final Path MAIL_LOCK_PATH = REPORT_DIR.resolve("mail-sent.lock");

    public static void main(String[] args) throws Exception {

        Files.createDirectories(REPORT_DIR);

        String allurePublicUrl = System.getProperty("allure.public.url",
                System.getenv().getOrDefault("ALLURE_PUBLIC_URL", "")).trim();

        if (!allurePublicUrl.isBlank()) {
            System.setProperty("allure.public.url", allurePublicUrl);
            log.info("[AllureMailRunner] Allure public URL: {}", allurePublicUrl);
        } else {
            log.warn("[AllureMailRunner] No Allure public URL provided; interactive link will not be included.");
        }

        try (FileChannel channel = FileChannel.open(
                MAIL_LOCK_PATH,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        )) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                log.info("[AllureMailRunner] Skipping: email already sent or another process holds the lock.");
                return;
            }

            try {
                if (!Files.exists(METRICS_PATH)) {
                    log.error("[AllureMailRunner] suite-metrics.properties not found at: {}",
                            METRICS_PATH.toAbsolutePath());
                    return;
                }

                Properties props = new Properties();
                try (var in = Files.newInputStream(METRICS_PATH)) {
                    props.load(in);
                }

                String suiteName = props.getProperty("suiteName", "Ejecución");
                int totalTests = Integer.parseInt(props.getProperty("totalTests", "0"));
                int passedTests = Integer.parseInt(props.getProperty("passedTests", "0"));
                int failedTests = Integer.parseInt(props.getProperty("failedTests", "0"));
                long durationMillis = Long.parseLong(props.getProperty("durationMillis", "0"));
                String executedTests = props.getProperty("executedTests", "");
                String mergedPdfName = props.getProperty("mergedPdfName", "");

                log.info("[AllureMailRunner] Sending final suite email. suiteName={} total={} passed={} failed={}",
                        suiteName, totalTests, passedTests, failedTests);

                AllureReportSender.sendFinalSuiteReport(
                        suiteName,
                        totalTests,
                        passedTests,
                        failedTests,
                        durationMillis,
                        executedTests,
                        mergedPdfName
                );

                channel.write(StandardCharsets.UTF_8.encode("SENT"));
                log.info("[AllureMailRunner] Suite email sent successfully.");

            } finally {
                try { lock.release(); } catch (Exception ignored) {}
            }
        }
    }
}
