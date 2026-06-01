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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    /** Returns true when sendMail=true OR mail.enabled=SI (launcher config). Evaluated per call so it picks up runtime changes. */
    private static boolean isMailEnabled() {
        return Boolean.parseBoolean(System.getProperty("sendMail", "false"))
            || "SI".equalsIgnoreCase(System.getProperty("mail.enabled", "NO"));
    }

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
            "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"
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
        sendFinalSuiteReport(suiteName, totalTests, passedTests, failedTests,
                durationMillis, executedTests, mergedPdfName, 0L);
    }

    public static void sendFinalSuiteReport(
            String suiteName,
            int totalTests,
            int passedTests,
            int failedTests,
            long durationMillis,
            String executedTests,
            String mergedPdfName,
            long runStartMs
    ) throws Exception {
        sendFinalSuiteReport(suiteName, totalTests, passedTests, failedTests,
                durationMillis, executedTests, mergedPdfName, runStartMs, java.util.Set.of());
    }

    public static void sendFinalSuiteReport(
            String suiteName,
            int totalTests,
            int passedTests,
            int failedTests,
            long durationMillis,
            String executedTests,
            String mergedPdfName,
            long runStartMs,
            java.util.Set<String> executedClasses
    ) throws Exception {

        log.info("[AllureReportSender] sendFinalSuiteReport — sendMail={} mail.enabled={} MAIL_TO={}",
                System.getProperty("sendMail", "<not set>"),
                System.getProperty("mail.enabled", "<not set>"),
                System.getenv("MAIL_TO") != null ? System.getenv("MAIL_TO") : "<not set>");

        if (!isMailEnabled()) {
            log.info("[AllureReportSender] Email delivery disabled (-DsendMail=false). Skipping.");
            return;
        }

        // Omitir email si es una ejecución de Atmosfera SIN fallos reales.
        // Doble verificación: contador pasado (ya corregido en AllureMailListener) +
        // lectura directa de allure-results por si el contador viniera en 0 por otro caller.
        boolean isAtmosfera =
                (suiteName != null && suiteName.toLowerCase().contains("atmosfera")) ||
                (executedTests != null && executedTests.contains("MenuAtmosfera"));

        if (isAtmosfera) {
            boolean hasFailures = failedTests > 0 || hasMenuAtmosferaFailuresInResults();
            if (!hasFailures) {
                log.info("[AllureReportSender] MenuAtmosfera suite — sin fallos detectados — email omitido.");
                return;
            }
            log.info("[AllureReportSender] MenuAtmosfera suite falló (failedTests={}) — enviando correo.", failedTests);
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
                reportUrl,
                runStartMs,
                executedClasses
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
            String reportUrl,
            long runStartMs,
            java.util.Set<String> executedClasses
    ) throws Exception {

        if (allurePdf == null || !Files.exists(allurePdf)) {
            log.warn("[AllureReportSender] Allure PDF not generated — sending email without PDF attachment.");
            allurePdf = null;
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

        // MAIL_TO env var (set by backend when Settings recipients are saved) takes priority
        String mailToEnv = System.getenv("MAIL_TO");
        if (mailToEnv != null && !mailToEnv.isBlank()) {
            to = mailToEnv.trim();
            log.info("[AllureReportSender] Destinatarios sobreescritos por MAIL_TO env: {}", to);
        }

        // Fallback: launcher config property (mail.recipients set from config dialog)
        if (to.isBlank()) {
            String launcherRecipients = System.getProperty("mail.recipients", "");
            if (!launcherRecipients.isBlank()) {
                to = launcherRecipients.trim();
                log.info("[AllureReportSender] Destinatarios tomados de mail.recipients: {}", to);
            }
        }

        if (smtpPass.isBlank()) {
            log.error("[AllureReportSender] smtp.pass is missing in smtp-config.json; email will not be sent.");
            return false;
        }
        if (to.isBlank()) {
            log.error("[AllureReportSender] No recipients configured (smtp-config.json mail.to ni MAIL_TO env).");
            return false;
        }

        log.info("[AllureReportSender] Sending via SMTP. host={} port={} from={} to={}", smtpHost, smtpPort, from, to);
        log.info("[AllureReportSender] PDF adjunto: {}", allurePdf != null ? allurePdf.toAbsolutePath() : "ninguno");
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

        // Conteos pasados como parámetros (fuente primaria).
        // Cuando vienen de AllureMailListener son los conteos REALES de JUnit TestExecutionResult:
        // total y passed se calculan ahora en el listener, failed siempre fue preciso.
        final int junitTotal  = totalTests;
        final int junitPassed = passedTests;
        final int junitFailed = failedTests;
        log.info("[EMAIL] Diagnóstico — suiteName='{}' total(JUnit)={} passed(JUnit)={} failed(JUnit)={} duration={}ms",
                suiteName, junitTotal, junitPassed, junitFailed, durationMillis);

        if (junitTotal > 0) {
            // Los conteos JUnit son autoritativos. NO llamamos a AllureSummaryReader para evitar
            // que un summary.json de un run anterior sobreescriba los valores correctos del run actual.
            // Esto resuelve: asunto FAILED con test PASSED, "590h" de duración, 17 fallos históricos, etc.
            log.info("[EMAIL] Fuente de datos: JUnit events (total={} > 0). AllureSummaryReader OMITIDO.", junitTotal);
        } else {
            // Fallback: total==0 → llamado desde AllureMailRunner (CI) o desde PdfReportExtension
            // que aún no migró. En ese caso AllureSummaryReader puede ser útil.
            log.info("[EMAIL] total(JUnit)=0 → intentando AllureSummaryReader como fallback...");
            try {
                AllureSummaryReader.Stats stats = AllureSummaryReader.readAuto();
                log.info("[EMAIL] AllureSummaryReader (fallback) — total={} passed={} failed={} broken={} skipped={} durationMs={}",
                        stats.total, stats.passed, stats.failed, stats.broken, stats.skipped, stats.durationMs);
                if (stats.total > 0) {
                    totalTests    = stats.total;
                    passedTests   = stats.passed;
                    failedTests   = stats.failed + stats.broken;
                    if (stats.durationMs > 0) durationMillis = stats.durationMs;
                    log.info("[EMAIL] Fuente de datos: AllureSummaryReader (fallback).");
                } else {
                    log.warn("[EMAIL] AllureSummaryReader también retornó total=0; se usarán valores en 0.");
                }
            } catch (Exception e) {
                log.warn("[EMAIL] AllureSummaryReader no disponible (fallback): {}", e.getMessage());
            }
        }

        // ── Counts: fuente autoritativa = BaseTestStatusRegistry (mismo origen que el PDF) ──
        // NO se sobreescriben con allure-results para evitar contaminación de runs anteriores.
        // allure-results solo se usa para el DETALLE de fallos (nombres, mensajes de error).
        int total   = totalTests;
        int passed  = passedTests;
        int failed  = failedTests;

        // subjectFailed: máximo entre registry y conteos de plataforma JUnit
        int subjectFailed = Math.max(failed, junitFailed);
        int skipped = Math.max(0, total - passed - failed);
        log.info("[EMAIL] Resultado consolidado — total={} passed={} failed={} skipped={} subjectFailed={}",
                total, passed, failed, skipped, subjectFailed);
        String durationPretty = formatDurationPretty(durationMillis);

        // Detalle de fallos: filtrado por clases del run actual Y por timestamp start del JSON
        List<FailureInfo> failureDetails = subjectFailed > 0
                ? readAllureFailures(runStartMs, executedClasses) : List.of();
        String failuresSection = buildFailuresHtml(failureDetails);

        String conclusionText;
        String conclusionColor;
        if (failed > 0) {
            conclusionText = "Se detectaron <b>" + failed + " test(s) con fallos</b> de un total de " + total + ". Se requiere revisión.";
            conclusionColor = "#f85149";
        } else if (skipped > 0) {
            conclusionText = "Todos los tests activos pasaron correctamente. <b>" + skipped + " test(s) omitidos</b>.";
            conclusionColor = "#d29922";
        } else {
            conclusionText = "<b>Todos los " + total + " tests pasaron correctamente.</b>";
            conclusionColor = "#2ea043";
        }
        String conclusionesSection =
                "<div style='background:#111827;border:1px solid #22304a;border-radius:16px;padding:16px;margin-top:16px;'>"
                + "<div style='font-size:15px;font-weight:800;color:#c9d1d9;margin-bottom:8px;'>📋 Conclusiones</div>"
                + "<div style='font-size:13px;color:" + conclusionColor + ";'>" + conclusionText + "</div>"
                + "</div>";

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
        String subject = buildSubject(projectName, subjectFailed);
        log.info("[EMAIL] Asunto generado: '{}'", subject);
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

                        +      failuresSection

                        +      conclusionesSection

                        + "    <div style='margin-top:14px;font-size:12px;color:#8b949e;'>"
                        + "      Se adjuntan: 📊 Reporte Allure (Overview + Behaviors) y 📋 PDFs individuales por test."
                        + "    </div>"

                        + "  </div>"
                        + "</body></html>";

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(htmlPart);

        // Adjunto 1: Allure Overview + Behaviors PDF (optional — skip if generation failed)
        if (allurePdf != null && Files.exists(allurePdf)) {
            MimeBodyPart allurePart = new MimeBodyPart();
            allurePart.attachFile(allurePdf.toFile());
            allurePart.setFileName("Reporte_Allure_" + sanitizeFileName(projectName) + ".pdf");
            multipart.addBodyPart(allurePart);
        } else {
            log.warn("[AllureReportSender] Skipping Allure PDF attachment (not generated).");
        }

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
            // Discover project root — the EXE may run from build/launch4j/, not the project root
            File projectRootFile = new File(System.getProperty("cinepolis.project.root",
                    System.getProperty("user.dir")));
            if (!new File(projectRootFile, "gradlew.bat").exists()) {
                File candidate = projectRootFile;
                while (candidate != null && !new File(candidate, "gradlew.bat").exists()) {
                    candidate = candidate.getParentFile();
                }
                if (candidate != null) projectRootFile = candidate;
            }
            final String projectDir = projectRootFile.getAbsolutePath();
            log.info("[AllureReportSender] Project root resolved to: {}", projectDir);

            // When running as EXE, allure-results land in user.dir (build/launch4j/allure-results/).
            // Copy them to {projectRoot}/build/allure-results/ so gradlew allureReport can find them.
            Path userDirResults = Paths.get(System.getProperty("user.dir"), "allure-results");
            Path projectResults = Paths.get(projectDir, "build", "allure-results");
            if (Files.exists(userDirResults)
                    && !userDirResults.toAbsolutePath().equals(projectResults.toAbsolutePath())) {
                try {
                    Files.createDirectories(projectResults);
                    Files.walk(userDirResults).forEach(src -> {
                        try {
                            Path dst = projectResults.resolve(userDirResults.relativize(src));
                            if (Files.isDirectory(src)) Files.createDirectories(dst);
                            else Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } catch (Exception ignored) {}
                    });
                    log.info("[AllureReportSender] Copied allure-results to: {}", projectResults);
                } catch (Exception e) {
                    log.warn("[AllureReportSender] Could not copy allure-results: {}", e.getMessage());
                }
            }

            try {
                log.info("[AllureReportSender] Running gradlew allureReport --clean in: {}", projectDir);

                ProcessBuilder pbAllure = new ProcessBuilder("cmd", "/c", "gradlew.bat", "allureReport", "--clean", "--no-daemon");
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

            Path reportDir = Paths.get(projectDir, "build", "reports", "allure-report", "allureReport");
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

    // ======================================================
    //            LECTURA DE FALLOS DESDE ALLURE RESULTS
    // ======================================================

    private record FailureInfo(String name, String suite, String cinema, String message,
                               String traceShort, String failingStep, String status) {}

    private static String buildFriendlyDescription(String failingStep, String message) {
        // Priority 1: use the step name to build the message
        if (failingStep != null && !failingStep.isBlank()) {
            String step = failingStep.trim();
            // Strip "failed" suffix if present
            if (step.toLowerCase().endsWith(" failed.")) step = step.substring(0, step.length() - 8).trim();
            if (step.toLowerCase().endsWith(" failed"))  step = step.substring(0, step.length() - 7).trim();
            return "Falló al intentar: " + step;
        }
        // Priority 2: simplify the error message
        if (message != null && !message.isBlank()) {
            String m = message.trim();
            String ml = m.toLowerCase();
            if (ml.contains("timeout") || ml.contains("timed out"))
                return "Tiempo de espera agotado — el elemento no apareció a tiempo";
            if (ml.contains("nosuchelement") || ml.contains("no such element"))
                return "Elemento no encontrado en pantalla";
            if (ml.contains("stale") && ml.contains("element"))
                return "El elemento dejó de estar disponible en pantalla";
            if (ml.contains("assertionerror") || ml.contains("expected") && ml.contains("but"))
                return "Verificación fallida — el resultado no fue el esperado";
            // Generic: strip " failed." suffix and show the action
            if (m.endsWith(" failed.")) return "Falló al intentar: " + m.substring(0, m.length() - 8).trim();
            if (m.endsWith(" failed"))  return "Falló al intentar: " + m.substring(0, m.length() - 7).trim();
            // Fallback to first 120 chars of message
            return m.length() > 120 ? m.substring(0, 120) + "…" : m;
        }
        return "Error durante la ejecución del test";
    }

    private static boolean hasMenuAtmosferaFailuresInResults() {
        Path resultsDir = Paths.get("build", "allure-results");
        if (!Files.exists(resultsDir)) return false;
        ObjectMapper mapper = new ObjectMapper();
        try {
            return Files.walk(resultsDir, 1)
                    .filter(p -> p.getFileName().toString().endsWith("-result.json"))
                    .anyMatch(p -> {
                        try {
                            JsonNode root = mapper.readTree(p.toFile());
                            String status = root.path("status").asText("");
                            if (!status.equals("failed") && !status.equals("broken")) return false;

                            String fullName = root.path("fullName").asText("");
                            if (fullName.contains("MenuAtmosfera")
                                    || fullName.toLowerCase().contains("atmosfera")) return true;

                            JsonNode labels = root.path("labels");
                            if (labels.isArray()) {
                                for (JsonNode lbl : labels) {
                                    String lblName  = lbl.path("name").asText("");
                                    String lblValue = lbl.path("value").asText("");
                                    if (("testClass".equals(lblName) || "suite".equals(lblName)
                                            || "feature".equals(lblName))
                                            && (lblValue.contains("MenuAtmosfera")
                                                || lblValue.toLowerCase().contains("atmosfera"))) {
                                        return true;
                                    }
                                }
                            }
                            return false;
                        } catch (Exception e) {
                            return false;
                        }
                    });
        } catch (Exception e) {
            log.warn("[AllureReportSender] Error revisando allure-results para fallos Atmosfera: {}", e.getMessage());
            return false;
        }
    }

    private static int countAllureResultFailures(long runStartMs) {
        Path resultsDir = Paths.get("build", "allure-results");
        if (!Files.exists(resultsDir)) return 0;
        ObjectMapper mapper = new ObjectMapper();
        try (var stream = Files.list(resultsDir)) {
            return (int) stream
                    .filter(p -> p.getFileName().toString().endsWith("-result.json"))
                    .filter(p -> {
                        try {
                            JsonNode root = mapper.readTree(p.toFile());
                            // Filtrar por el campo "start" del JSON de Allure:
                            // es el timestamp real de inicio del test, escrito por Allure mismo.
                            // Mucho más fiable que lastModified del filesystem (impreciso en Windows).
                            if (runStartMs > 0) {
                                long testStart = root.path("start").asLong(0);
                                if (testStart > 0 && testStart < runStartMs) return false;
                            }
                            String status = root.path("status").asText("");
                            return "failed".equals(status) || "broken".equals(status);
                        } catch (Exception ignored) { return false; }
                    })
                    .count();
        } catch (Exception e) {
            log.warn("[AllureReportSender] No se pudieron contar fallos en allure-results: {}", e.getMessage());
            return 0;
        }
    }

    private static List<FailureInfo> readAllureFailures(long runStartMs,
                                                          java.util.Set<String> executedClasses) {
        List<FailureInfo> failures = new ArrayList<>();
        Path resultsDir = Paths.get("build", "allure-results");
        if (!Files.exists(resultsDir)) return failures;

        ObjectMapper mapper = new ObjectMapper();
        try {
            Files.walk(resultsDir, 1)
                 .filter(p -> p.getFileName().toString().endsWith("-result.json"))
                 .forEach(p -> {
                     try {
                         JsonNode root = mapper.readTree(p.toFile());

                         // Filtro 1: timestamp start del JSON (descarta archivos de runs anteriores)
                         if (runStartMs > 0) {
                             long testStart = root.path("start").asLong(0);
                             if (testStart > 0 && testStart < runStartMs) return;
                         }

                         // Filtro 2: clase del test (descarta tests de otras suites en la misma sesión)
                         if (executedClasses != null && !executedClasses.isEmpty()) {
                             boolean perteneceAlRun = false;
                             JsonNode labels = root.path("labels");
                             if (labels.isArray()) {
                                 for (JsonNode lbl : labels) {
                                     String lblName = lbl.path("name").asText("");
                                     if ("testClass".equals(lblName) || "suite".equals(lblName)) {
                                         String lblValue = lbl.path("value").asText("");
                                         String simpleVal = lblValue.contains(".")
                                                 ? lblValue.substring(lblValue.lastIndexOf('.') + 1)
                                                 : lblValue;
                                         if (executedClasses.contains(simpleVal)
                                                 || executedClasses.contains(lblValue)) {
                                             perteneceAlRun = true;
                                             break;
                                         }
                                     }
                                 }
                             }
                             if (!perteneceAlRun) return; // test de otra suite → ignorar
                         }

                         String status = root.path("status").asText("");
                         if (!status.equals("failed") && !status.equals("broken")) return;

                         String name = root.path("name").asText("Test desconocido");

                         JsonNode details = root.path("statusDetails");
                         String message = details.path("message").asText("").trim();
                         String trace   = details.path("trace").asText("").trim();

                         String traceShort = Arrays.stream(trace.split("\\n"))
                                 .limit(5)
                                 .collect(java.util.stream.Collectors.joining("\n"));

                         String failingStep = "";
                         JsonNode steps = root.path("steps");
                         if (steps.isArray()) {
                             for (JsonNode step : steps) {
                                 if ("failed".equals(step.path("status").asText(""))) {
                                     failingStep = step.path("name").asText("");
                                     break;
                                 }
                             }
                         }

                         String suite = "";
                         String cinema = "";
                         JsonNode labels = root.path("labels");
                         if (labels.isArray()) {
                             for (JsonNode lbl : labels) {
                                 String lblName = lbl.path("name").asText("");
                                 if ("suite".equals(lblName)) suite = lbl.path("value").asText("");
                                 else if ("cinema".equals(lblName)) cinema = lbl.path("value").asText("");
                             }
                         }

                         failures.add(new FailureInfo(name, suite, cinema, message, traceShort, failingStep, status));
                     } catch (Exception ignored) {}
                 });
        } catch (Exception e) {
            log.warn("[AllureReportSender] No se pudieron leer allure-results: {}", e.getMessage());
        }
        return failures;
    }

    private static String buildFailuresHtml(List<FailureInfo> failures) {
        if (failures.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='background:#111827;border:1px solid #3a1a1a;border-radius:16px;padding:18px;margin-top:16px;'>");
        sb.append("<div style='font-size:18px;font-weight:800;color:#f85149;margin-bottom:14px;'>❌ Detalle de Fallos (")
          .append(failures.size()).append(")</div>");

        for (FailureInfo f : failures) {
            boolean broken = "broken".equals(f.status());
            String statusColor = broken ? "#d29922" : "#f85149";
            String statusLabel = broken ? "⚠️ BROKEN" : "❌ FAILED";

            sb.append("<div style='border:1px solid ").append(broken ? "#3a2a00" : "#3a1a1a")
              .append(";border-radius:10px;padding:14px;margin-bottom:12px;background:#0d1117;'>");

            sb.append("<div style='display:flex;align-items:center;gap:8px;margin-bottom:8px;flex-wrap:wrap;'>");
            sb.append("<span style='font-size:12px;font-weight:700;color:").append(statusColor)
              .append(";background:").append(broken ? "rgba(210,153,34,0.1)" : "rgba(248,81,73,0.1)")
              .append(";padding:2px 8px;border-radius:6px;'>").append(statusLabel).append("</span>");
            if (!f.suite().isEmpty()) {
                sb.append("<span style='font-size:11px;color:#8b949e;background:#1a2233;padding:2px 8px;border-radius:6px;'>")
                  .append(escapeHtml(f.suite())).append("</span>");
            }
            if (!f.cinema().isEmpty()) {
                sb.append("<span style='font-size:11px;color:#c9d1d9;background:#1a2233;padding:2px 8px;border-radius:6px;'>🎬 ")
                  .append(escapeHtml(f.cinema())).append("</span>");
            }
            sb.append("</div>");

            sb.append("<div style='font-size:14px;font-weight:700;color:#c9d1d9;margin-bottom:8px;'>")
              .append(escapeHtml(f.name())).append("</div>");

            if (!f.message().isEmpty()) {
                String msg = f.message().length() > 400 ? f.message().substring(0, 400) + "…" : f.message();
                sb.append("<div style='font-size:12px;color:#f85149;background:#1c0a0a;border-left:3px solid #f85149;")
                  .append("border-radius:0 6px 6px 0;padding:8px 10px;margin-bottom:8px;font-family:monospace;word-break:break-all;'>")
                  .append(escapeHtml(msg)).append("</div>");
            }

            String desc = buildFriendlyDescription(f.failingStep(), f.message());
            sb.append("<div style='font-size:12px;color:#8b949e;margin-bottom:4px;'>")
              .append("<span style='color:#c9d1d9;font-style:italic;'>").append(escapeHtml(desc)).append("</span></div>");

            sb.append("</div>");
        }

        sb.append("</div>");
        return sb.toString();
    }
}


