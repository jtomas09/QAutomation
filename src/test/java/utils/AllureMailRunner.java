package utils;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

public class AllureMailRunner {

    private static final Path REPORT_DIR = Paths.get("build", "reportes-pdf");
    private static final Path METRICS_PATH = REPORT_DIR.resolve("suite-metrics.properties");
    private static final Path MAIL_LOCK_PATH = REPORT_DIR.resolve("mail-sent.lock");

    public static void main(String[] args) throws Exception {

        Files.createDirectories(REPORT_DIR);

        // ✅ 1) Leer URL pública de Allure (Cloudflare Pages) y guardarla como System Property
        String allurePublicUrl = System.getProperty("allure.public.url",
                System.getenv().getOrDefault("ALLURE_PUBLIC_URL", "")).trim();

        if (!allurePublicUrl.isBlank()) {
            System.setProperty("allure.public.url", allurePublicUrl);
            System.out.println("[INFO] Allure public URL: " + allurePublicUrl);
        } else {
            System.out.println("[WARN] No Allure public URL provided (interactive link will not be added)");
        }

        // ✅ Evitar duplicados
        try (FileChannel channel = FileChannel.open(
                MAIL_LOCK_PATH,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        )) {
            FileLock lock = channel.tryLock();
            if (lock == null) {
                System.out.println("[AllureMailRunner] (SKIP) Ya existe un envío en curso o ya se envió (lock activo).");
                return;
            }

            try {
                if (!Files.exists(METRICS_PATH)) {
                    System.out.println("[AllureMailRunner] ERROR: No existe suite-metrics.properties en: " + METRICS_PATH.toAbsolutePath());
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

                System.out.println("[AllureMailRunner] Enviando correo FINAL (una sola vez).");
                System.out.println("  suiteName=" + suiteName);
                System.out.println("  Total=" + totalTests + ", Passed=" + passedTests + ", Failed=" + failedTests);

                // ✅ Método correcto (ya existe)
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
                System.out.println("[AllureMailRunner] ✔ Correo enviado. (lock escrito)");

            } finally {
                try { lock.release(); } catch (Exception ignored) {}
            }
        }
    }
}
