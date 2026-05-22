package utils;

import static utils.PdfReportGenerator.EXECUTOR;

import jakarta.mail.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.mail.internet.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

// HTTP server
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

// Selenium
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

// PDF merge
import org.apache.pdfbox.multipdf.PDFMergerUtility;

// ✅ Tu loader JSON
import utils.config.ConfigLoader;
import utils.config.SmtpConfig;

public class AllureReportSender {

    private static final Logger log = LoggerFactory.getLogger(AllureReportSender.class);

    /** Set -DsendMail=true to enable email delivery after each suite run. */
    private static final boolean IS_MAIL_ENABLED = Boolean.parseBoolean(System.getProperty("sendMail", "false"));

    private static final Path FINAL_MAIL_LOCK = Paths.get("build", "suite-mail.sent.lock");
    private static final Path MAIL_LOCK = Paths.get("build", "suite-mail.sent.lock");

    private static boolean isFinalMailAlreadySent() {
        return Files.exists(FINAL_MAIL_LOCK);
    }

    /**
     * Deletes the mail-sent lock so the next run is allowed to send a new email.
     * Call this at the start of each test plan execution.
     */
    public static void resetMailLock() {
        try {
            if (Files.deleteIfExists(MAIL_LOCK)) {
                log.info("[AllureReportSender] Mail lock cleared: {}", MAIL_LOCK.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("[AllureReportSender] Could not clear mail lock: {}", e.getMessage());
        }
    }

    private static void markFinalMailSent() {
        try {
            Files.createDirectories(FINAL_MAIL_LOCK.getParent());
            if (!Files.exists(FINAL_MAIL_LOCK)) {
                Files.writeString(FINAL_MAIL_LOCK, "sent", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            log.info("[AllureReportSender] Mail lock written: {}", FINAL_MAIL_LOCK.toAbsolutePath());
        } catch (FileAlreadyExistsException e) {
            log.debug("[AllureReportSender] Mail lock already exists: {}", FINAL_MAIL_LOCK.toAbsolutePath());
        } catch (Exception e) {
            log.warn("[AllureReportSender] Could not write mail lock: {}", e.getMessage());
        }
    }

    private static final String CHROME_PATH = System.getenv().getOrDefault(
            "CHROME_PATH",
            "C:\\\\Program Files\\\\Google\\\\Chrome\\\\Application\\\\chrome.exe"
    );

    /**
     * Sends a single final suite report email with the Allure PDF attached.
     * Guarded by a lock file so it fires only once per run regardless of how many callbacks invoke it.
     */
    public static void sendFinalSuiteReport(
            String suiteName,
            int totalTests,
            int passedTests,
            int failedTests,
            long durationMillis,
            String executedTests,
            String mergedPdfName
    ) throws Exception {

        if (!IS_MAIL_ENABLED) {
            log.info("[AllureReportSender] Email delivery disabled (-DsendMail=false). Skipping.");
            return;
        }

        if (isFinalMailAlreadySent()) {
            log.info("[AllureReportSender] Skipping: mail lock already exists at {}",
                    FINAL_MAIL_LOCK.toAbsolutePath());
            return;
        }

        String reportUrl = AllureUrlStore.readUrl();
        log.info("[AllureReportSender] Allure URL (from store): {}", reportUrl);

        if (reportUrl == null || reportUrl.trim().isEmpty()) {
            try {
                log.info("[AllureReportSender] No stored URL found. Publishing to Netlify...");
                reportUrl = AllureAutoPublisher.generateAndPublish();
                log.info("[AllureReportSender] Netlify URL: {}", reportUrl);
            } catch (Exception e) {
                log.warn("[AllureReportSender] Netlify publish failed: {}", e.getMessage());
                reportUrl = "";
            }
        }

        log.info("[AllureReportSender] Sending final suite email...");

        Path allurePdf = generateAllureOverviewPdf();

        boolean sent = sendInternalAllureOnly(
                allurePdf,
                suiteName,
                totalTests,
                passedTests,
                failedTests,
                durationMillis,
                executedTests,
                reportUrl
        );

        if (sent) {
            markFinalMailSent();
        } else {
            log.warn("[AllureReportSender] Email was not sent; mail lock will not be written.");
        }
    }

    // ======================================================
    //            ENVÍO DE CORREO (SOLO ALLURE 1 PDF)
    // ======================================================
    private static boolean sendInternalAllureOnly(
            Path allurePdf,
            String suiteName,
            int totalTests,
            int passedTests,
            int failedTests,
            long durationMillis,
            String executedTests,
            String reportUrl
    ) throws Exception {

        if (allurePdf == null || !Files.exists(allurePdf)) {
            log.error("[AllureReportSender] Allure PDF not found: {}", allurePdf);
            return false;
        }

        SmtpConfig cfg;
        try {
            cfg = ConfigLoader.getSmtpConfig();
        } catch (Exception e) {
            log.error("[AllureReportSender] Failed to read smtp-config.json: {}", e.getMessage());
            return false;
        }

        String smtpHost = safe(cfg.smtp.host, "email-smtp.us-east-1.amazonaws.com");
        String smtpPort = safe(cfg.smtp.port, "587");
        String smtpUser = safe(cfg.smtp.user, "");
        String smtpPass = safe(cfg.smtp.pass, "");
        String from = safe(cfg.mail.from, "automation_android@ia.com.mx");

        String to = "";
        try {
            if (cfg.mail.to != null && !cfg.mail.to.isEmpty()) {
                to = cfg.mail.to.stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .reduce((a, b) -> a + "," + b)
                        .orElse("");
            }
        } catch (Exception ignored) {}

        if (smtpPass.isBlank()) {
            log.error("[AllureReportSender] smtp.pass is missing in smtp-config.json; email will not be sent.");
            return false;
        }
        if (to.isBlank()) {
            log.error("[AllureReportSender] No recipients configured in smtp-config.json (mail.to).");
            return false;
        }

        log.info("[AllureReportSender] Sending via SMTP. host={} port={} from={} to={}", smtpHost, smtpPort, from, to);
        log.info("[AllureReportSender] Attaching PDF: {}", allurePdf.toAbsolutePath());
        log.info("[AllureReportSender] Interactive link: {}", reportUrl);

        Properties props = new Properties();
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.trust", smtpHost);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser.trim(), smtpPass.trim());
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));

        try {
            AllureSummaryReader.Stats stats = AllureSummaryReader.readAuto();
            totalTests = stats.total;
            passedTests = stats.passed;
            failedTests = stats.failed + stats.broken;
            if (stats.durationMs > 0) durationMillis = stats.durationMs;

            log.info("[AllureReportSender] Stats from summary.json: total={} passed={} failed={} durationMs={}",
                    totalTests, passedTests, failedTests, durationMillis);
        } catch (Exception e) {
            log.warn("[AllureReportSender] Could not read summary.json; using fallback values: {}", e.getMessage());
        }

        int total = totalTests;
        int passed = passedTests;
        int failed = failedTests;
        int skipped = Math.max(0, total - passed - failed);
        String durationPretty = formatDurationPretty(durationMillis);

        // ✅ Bloque estético del link (recomendado: "card" + botón)
        String interactiveBlock = "";
        if (reportUrl != null && !reportUrl.isBlank()
                && (reportUrl.startsWith("http://") || reportUrl.startsWith("https://"))) {

            String safeUrl = escapeHtml(reportUrl);

            interactiveBlock =
                    "    <div style='background:#111827;border:1px solid #22304a;border-radius:16px;padding:18px;margin-top:16px;'>"
                            + "      <div style='font-size:14px;font-weight:700;color:#c9d1d9;margin-bottom:10px;'>🔗 Reporte Allure interactivo (navegable):</div>"
                            + "      <a href='" + safeUrl + "' target='_blank' rel='noopener noreferrer' "
                            + "         style='display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;"
                            + "                padding:12px 18px;border-radius:10px;font-weight:700;'>"
                            + "        Abrir reporte interactivo"
                            + "      </a>"
                            + "      <div style='margin-top:10px;font-size:12px;color:#8b949e;word-break:break-all;'>"
                            +        safeUrl
                            + "      </div>"
                            + "    </div>";
        }

        String projectName = pickProjectName(suiteName);
        String subject = buildSubject(projectName, failedTests);
        message.setSubject(subject, "UTF-8");

        // ✅ Lista de tests ejecutados (ya lo tenías)
        String executedHtml = "";
        if (executedTests != null && !executedTests.trim().isEmpty()) {

            Set<String> executedMenus = Arrays.stream(executedTests.split("\\s*\\|\\s*"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(t -> {
                        // Ej: MenuCoffeTree_test01 → MenuCoffeTree
                        int idx = t.indexOf("_");
                        return idx > 0 ? t.substring(0, idx) : t;
                    })
                    .filter(t -> t.startsWith("Menu"))
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

            if (!executedMenus.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("<p style='margin:12px 0 0 0;'><b>Menús ejecutados:</b><br>");
                for (String menu : executedMenus) {
                    sb.append("• ").append(escapeHtml(menu)).append("<br>");
                }
                sb.append("</p>");
                executedHtml = sb.toString();
            }
        }

        String generatedAt = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy, hh:mm:ss a"));

        String executorName = safe(EXECUTOR, "");

        // ✅ HTML (solo estética; lógica intacta)
        String html =
                "<html><body style='margin:0;padding:0;background:#0b0f14;font-family:Arial,Helvetica,sans-serif;color:#e6edf3;'>"
                        + "  <div style='max-width:760px;margin:0 auto;padding:24px;'>"

                        + "    <div style='display:flex;align-items:center;gap:12px;margin-bottom:18px;'>"
                        + "      <div style='font-size:34px;line-height:1;'>🤖</div>"
                        + "      <div>"
                        + "        <div style='font-size:26px;font-weight:800;color:#c9d1d9;'>Reporte de pruebas automatizadas</div>"
                        + "        <div style='font-size:12px;color:#8b949e;margin-top:4px;'>Generado: " + escapeHtml(generatedAt) + "</div>"
                        + "      </div>"
                        + "    </div>"

                        + "    <div style='background:#111827;border:1px solid #22304a;border-radius:16px;padding:18px;margin-bottom:16px;'>"
                        + "      <div style='font-size:14px;color:#c9d1d9;line-height:1.6;'>"
                        + "        <div><b>Proyecto:</b> " + escapeHtml(projectName) + "</div>"
                        + "        <div><b>Ejecutor:</b> " + escapeHtml(executorName) + "</div>"
                        + "      </div>"
                        +        executedHtml
                        + "    </div>"

                        +      interactiveBlock

                        + "    <div style='background:#111827;border:1px solid #22304a;border-radius:16px;padding:18px;margin-top:16px;'>"
                        + "      <div style='font-size:18px;font-weight:800;color:#c9d1d9;margin-bottom:12px;'>📊 Resumen de Ejecución</div>"
                        + "      <table cellpadding='0' cellspacing='0' style='border-collapse:collapse;width:360px;border:1px solid #22304a;border-radius:12px;overflow:hidden;'>"
                        + "        <tr><td style='padding:10px 12px;border-bottom:1px solid #22304a;background:#0b1220;color:#c9d1d9;'>Total de tests</td>"
                        + "            <td style='padding:10px 12px;border-bottom:1px solid #22304a;background:#0b1220;color:#c9d1d9;text-align:center;'><b>" + total + "</b></td></tr>"
                        + "        <tr><td style='padding:10px 12px;border-bottom:1px solid #22304a;background:#0b1220;color:#c9d1d9;'>Tests pasados ✅</td>"
                        + "            <td style='padding:10px 12px;border-bottom:1px solid #22304a;background:#0b1220;color:#2ea043;text-align:center;'><b>" + passed + "</b></td></tr>"
                        + "        <tr><td style='padding:10px 12px;border-bottom:1px solid #22304a;background:#0b1220;color:#c9d1d9;'>Tests skipped ⏭️</td>"
                        + "            <td style='padding:10px 12px;border-bottom:1px solid #22304a;background:#0b1220;color:#d29922;text-align:center;'><b>" + skipped + "</b></td></tr>"
                        + "        <tr><td style='padding:10px 12px;border-bottom:1px solid #22304a;background:#0b1220;color:#c9d1d9;'>Tests fallados ❌</td>"
                        + "            <td style='padding:10px 12px;border-bottom:1px solid #22304a;background:#0b1220;color:#f85149;text-align:center;'><b>" + failed + "</b></td></tr>"
                        + "        <tr><td style='padding:10px 12px;background:#0b1220;color:#c9d1d9;'>Tiempo total</td>"
                        + "            <td style='padding:10px 12px;background:#0b1220;color:#c9d1d9;text-align:center;'><b>" + escapeHtml(durationPretty) + "</b></td></tr>"
                        + "      </table>"
                        + "    </div>"

                        + "    <div style='margin-top:14px;font-size:12px;color:#8b949e;'>"
                        + "      Se adjuntan: 📊 Reporte Allure (Overview + Behaviors) y 📋 PDFs individuales por test."
                        + "    </div>"

                        + "  </div>"
                        + "</body></html>";

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(htmlPart);

        // Adjunto 1: Allure Overview + Behaviors PDF
        MimeBodyPart allurePart = new MimeBodyPart();
        allurePart.attachFile(allurePdf.toFile());
        allurePart.setFileName("Reporte_Allure_" + sanitizeFileName(projectName) + ".pdf");
        multipart.addBodyPart(allurePart);

        // Adjunto 2: PDFs por test (merged de build/reportes-pdf/)
        Path testsPdf = mergeTestPdfs(projectName);
        if (testsPdf != null && Files.exists(testsPdf)) {
            MimeBodyPart testsPart = new MimeBodyPart();
            testsPart.attachFile(testsPdf.toFile());
            testsPart.setFileName("Reporte_Tests_" + sanitizeFileName(projectName) + ".pdf");
            multipart.addBodyPart(testsPart);
            log.info("[AllureReportSender] Per-test PDF attached: {}", testsPdf.getFileName());
        }

        message.setContent(multipart);

        log.info("[AllureReportSender] Sending via {}...", smtpHost);
        try {
            Transport.send(message);
            log.info("[AllureReportSender] Email sent successfully to: {}", to);
            return true;
        } catch (Throwable e) {
            log.error("[AllureReportSender] Email send failed: {} -> {}", e.getClass().getName(), e.getMessage(), e);
            return false;
        }
    }

    private static Path mergeTestPdfs(String projectName) {
        Path reportsDir = Paths.get("build", "reportes-pdf");
        if (!Files.exists(reportsDir)) {
            log.info("[AllureReportSender] Per-test PDF dir not found: {}", reportsDir.toAbsolutePath());
            return null;
        }

        Path merged = reportsDir.resolve("Reporte_Tests_" + sanitizeFileName(projectName) + ".pdf");
        try { Files.deleteIfExists(merged); } catch (Exception ignored) {}

        try {
            java.util.List<Path> pdfs = Files.walk(reportsDir, 1)
                    .filter(p -> p.toString().endsWith(".pdf") && !p.equals(merged))
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());

            if (pdfs.isEmpty()) {
                log.info("[AllureReportSender] No per-test PDFs found in {}", reportsDir.toAbsolutePath());
                return null;
            }

            PDFMergerUtility merger = new PDFMergerUtility();
            merger.setDestinationFileName(merged.toAbsolutePath().toString());
            for (Path p : pdfs) {
                if (Files.exists(p)) merger.addSource(p.toFile());
            }
            merger.mergeDocuments(null);

            log.info("[AllureReportSender] Merged {} per-test PDFs → {}", pdfs.size(), merged.toAbsolutePath());
            return Files.exists(merged) ? merged : null;

        } catch (Exception e) {
            log.warn("[AllureReportSender] Could not merge per-test PDFs: {}", e.getMessage());
            return null;
        }
    }

    public static Path generateAllureOverviewPdf() {
        HttpServer server = null;
        try {
            try {
                String projectDir = System.getProperty("user.dir");
                log.info("[AllureReportSender] Running gradlew allureReport --clean in: {}", projectDir);

                ProcessBuilder pbAllure = new ProcessBuilder("cmd", "/c", "gradlew.bat", "allureReport", "--clean");
                pbAllure.directory(new File(projectDir));
                pbAllure.redirectErrorStream(true);

                Process pAllure = pbAllure.start();
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(pAllure.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) log.debug("[gradlew allureReport] {}", line);
                }

                pAllure.waitFor(180, TimeUnit.SECONDS);
                if (pAllure.exitValue() != 0) {
                    log.warn("[AllureReportSender] gradlew allureReport exited with non-zero code.");
                } else {
                    log.info("[AllureReportSender] gradlew allureReport completed successfully.");
                }
            } catch (Exception e) {
                log.warn("[AllureReportSender] Could not run gradlew allureReport: {}", e.getMessage());
            }

            Path reportDir = Paths.get("build", "reports", "allure-report", "allureReport");
            Path indexHtml = reportDir.resolve("index.html");
            if (!Files.exists(indexHtml)) {
                log.error("[AllureReportSender] index.html not found at: {}", indexHtml);
                return null;
            }

            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            Path finalReportDir = reportDir;

            server.createContext("/", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    try {
                        URI uri = exchange.getRequestURI();
                        String path = uri.getPath();

                        if (path == null || path.equals("/") || path.isEmpty()) path = "index.html";
                        else if (path.startsWith("/")) path = path.substring(1);

                        Path requested = finalReportDir.resolve(path).normalize();
                        if (!requested.startsWith(finalReportDir) || !Files.exists(requested) || Files.isDirectory(requested)) {
                            exchange.sendResponseHeaders(404, -1);
                            exchange.close();
                            return;
                        }

                        String mime = URLConnection.guessContentTypeFromName(requested.toString());
                        if (mime == null) mime = "application/octet-stream";

                        exchange.getResponseHeaders().set("Content-Type", mime);
                        byte[] data = Files.readAllBytes(requested);
                        exchange.sendResponseHeaders(200, data.length);
                        exchange.getResponseBody().write(data);
                        exchange.close();
                    } catch (Exception e) {
                        try { exchange.sendResponseHeaders(500, -1); } catch (Exception ignored) {}
                        exchange.close();
                    }
                }
            });

            server.start();

            String urlOverview = "http://127.0.0.1:" + port + "/index.html";

            Path overviewPdf = reportDir.resolve("Allure_Overview.pdf");
            Path behaviorsPdf = reportDir.resolve("Allure_Behaviors.pdf");
            Path finalPdf = reportDir.resolve("Allure_Overview_Behaviors.pdf");

            for (Path p : Arrays.asList(overviewPdf, behaviorsPdf, finalPdf)) {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            }

            ProcessBuilder pbOverview = new ProcessBuilder(
                    CHROME_PATH,
                    "--headless",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--virtual-time-budget=10000",
                    "--print-to-pdf=" + overviewPdf.toAbsolutePath(),
                    urlOverview
            );
            pbOverview.redirectErrorStream(true);
            Process pOverview = pbOverview.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(pOverview.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) log.debug("[Chrome Overview] {}", line);
            }

            pOverview.waitFor(60, TimeUnit.SECONDS);

            generateBehaviorsPdfWithSelenium(reportDir, port, behaviorsPdf);
            mergePdfs(Arrays.asList(overviewPdf, behaviorsPdf), finalPdf);

            if (Files.exists(finalPdf)) {
                log.info("[AllureReportSender] Final PDF generated: {}", finalPdf.toAbsolutePath());
                return finalPdf;
            }
            return null;

        } catch (Exception e) {
            log.error("[AllureReportSender] Failed to generate Allure overview PDF", e);
            return null;
        } finally {
            if (server != null) server.stop(0);
        }
    }

    private static void generateBehaviorsPdfWithSelenium(Path reportDir, int port, Path behaviorsPdf) {
        Path behaviorsPng = reportDir.resolve("Allure_Behaviors.png");
        try { Files.deleteIfExists(behaviorsPng); } catch (Exception ignored) {}

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--window-size=1920,1080");

        WebDriver driver = new ChromeDriver(options);

        try {
            String urlBehaviors = "http://127.0.0.1:" + port + "/index.html#/behaviors";
            driver.get(urlBehaviors);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("body")));
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".tree, .tree__content, .node__title, .node__name")
            ));

            byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(behaviorsPng, screenshot);

            PdfReportGenerator.createPdfFromImage(behaviorsPng.toFile(), behaviorsPdf.toFile());

        } catch (Exception e) {
            log.error("[AllureReportSender] Failed to generate Behaviors PDF: {}", e.getMessage(), e);
        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
        }
    }

    private static void mergePdfs(List<Path> pdfs, Path out) throws Exception {
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.setDestinationFileName(out.toAbsolutePath().toString());

        for (Path p : pdfs) {
            if (p != null && Files.exists(p)) merger.addSource(p.toFile());
        }
        merger.mergeDocuments(null);
    }

    private static String pickProjectName(String suiteName) {
        if (suiteName == null) return "Reporte";
        return suiteName.trim().isEmpty() ? "Reporte" : suiteName.trim();
    }

    private static String buildSubject(String projectName, int failedTests) {
        String prefix = failedTests > 0 ? "FAILED" : "PASSED";
        return prefix + " - Reporte " + projectName;
    }

    /** Formato estilo screenshot: 02m 46s / 13m 36s / 1h 02m 05s */
    private static String formatDurationPretty(long millis) {
        if (millis <= 0) return "-";
        long seconds = millis / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = (seconds % 60);

        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        return String.format("%02dm %02ds", m, s);
    }

    private static String sanitizeFileName(String s) {
        if (s == null) return "Reporte";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String safe(String v, String def) {
        if (v == null) return def;
        String t = v.trim();
        return t.isEmpty() ? def : t;
    }
}


