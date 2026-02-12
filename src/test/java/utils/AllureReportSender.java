package utils;

import static utils.PdfReportGenerator.EXECUTOR;

import jakarta.mail.*;
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
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

public class AllureReportSender {

    // ======================================================
    //                CONFIG MAIL
    // ======================================================
    private static final String SMTP_HOST = System.getProperty("mail.smtp.host", "email-smtp.us-east-1.amazonaws.com");
    private static final String SMTP_PORT = System.getProperty("mail.smtp.port", "587");
    private static final String SMTP_USER = System.getProperty("mail.smtp.user", "");
    private static final String SMTP_PASS = System.getProperty("mail.smtp.pass", "");
    private static final String FROM_ADDRESS = System.getProperty("mail.from", "automation_android@ia.com.mx");

    // ✅ URL pública del reporte Allure (Cloudflare Pages / GitHub Pages / etc.)
    // Pásala en CI con: -Dallure.public.url="https://<proyecto>.pages.dev"
    private static final String ALLURE_PUBLIC_URL =
            System.getProperty("allure.public.url", System.getenv().getOrDefault("ALLURE_PUBLIC_URL", "")).trim();

    private static final String[] TO_ADDRESSES = System.getProperty("mail.to", "").split(",");
    private static final String TO_ADDRESS = Stream.of(TO_ADDRESSES)
            .map(String::trim)
            .filter(s -> s != null && !s.trim().isEmpty())
            .reduce((a, b) -> a.trim() + "," + b.trim())
            .orElse("");

    // Envío por test (solo si activas)
    private static final boolean SEND_PER_TEST = Boolean.parseBoolean(System.getProperty("SEND_PER_TEST", "false"));

    // Ruta local de Chrome
    private static final String CHROME_PATH = System.getenv().getOrDefault(
            "CHROME_PATH",
            "C:\\\\Program Files\\\\Google\\\\Chrome\\\\Application\\\\chrome.exe"
    );

    // ======================================================
    // ✅ FINAL DE SUITE (1 SOLO CORREO)
    //    - Adjunta SOLO 1 PDF: Allure_Overview_Behaviors.pdf
    // ======================================================
    public static void sendFinalSuiteReport(
            String suiteName,
            int totalTests,
            int passedTests,
            int failedTests,
            long durationMillis,
            String executedTests,
            String mergedPdfName
    ) throws Exception {

        System.out.println("[SES] (FINAL) Enviando reporte FINAL de suite (una sola vez).");

        Path allurePdf = generateAllureOverviewPdf();

        sendInternalAllureOnly(
                allurePdf,
                suiteName,
                totalTests,
                passedTests,
                failedTests,
                durationMillis,
                executedTests
        );
    }

    // ======================================================
    // ❌ Envío por test (solo si tú lo activas con -DSEND_PER_TEST=true)
    // ======================================================
    public static void sendEvidenceAndAllure(
            String suiteName,
            int totalTests,
            int passedTests,
            int failedTests,
            long durationMillis,
            String executedTests
    ) throws Exception {

        if (!SEND_PER_TEST) {
            System.out.println("[SES] (SKIP) SEND_PER_TEST=false -> NO se envía por test.");
            return;
        }

        System.out.println("[SES] (PER-TEST) SEND_PER_TEST=true -> enviando por test (solo Allure PDF).");

        Path allurePdf = generateAllureOverviewPdf();

        sendInternalAllureOnly(
                allurePdf,
                suiteName,
                totalTests,
                passedTests,
                failedTests,
                durationMillis,
                executedTests
        );
    }

    // ======================================================
    //            ENVÍO DE CORREO (SOLO ALLURE 1 PDF)
    // ======================================================
    private static void sendInternalAllureOnly(
            Path allurePdf,
            String suiteName,
            int totalTests,
            int passedTests,
            int failedTests,
            long durationMillis,
            String executedTests
    ) throws Exception {

        if (allurePdf == null || !Files.exists(allurePdf)) {
            System.err.println("[SES] ERROR: No existe el PDF de Allure: " + allurePdf);
            return;
        }

        if (SMTP_PASS == null || SMTP_PASS.isBlank()) {
            System.err.println("[SES] ERROR: Falta SMTP_PASS (contraseña de SES). No se enviará el correo.");
            return;
        }

        if (TO_ADDRESS.isBlank()) {
            System.err.println("[SES] ERROR: No hay destinatarios (CFG.mail.to vacío o solo vacíos).");
            return;
        }

        System.out.println("[SES] Preparando envío SMTP a: " + TO_ADDRESS);
        System.out.println("[SES] SMTP_HOST = " + SMTP_HOST + ", SMTP_PORT = " + SMTP_PORT);
        System.out.println("[SES] SMTP_USER = " + SMTP_USER);
        System.out.println("[SES] Adjuntando SOLO Allure PDF: " + allurePdf.toAbsolutePath());

        Properties props = new Properties();
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.ssl.trust", SMTP_HOST);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USER.trim(), SMTP_PASS.trim());
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(FROM_ADDRESS));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO_ADDRESS));

        // ======================================================
        // ✅ SOLUCIÓN A: Resumen basado en Allure (summary.json)
        //    Evita discrepancias vs Gradle/JUnit/Allure UI
        // ======================================================
        try {
            AllureSummaryReader.Stats stats = AllureSummaryReader.readAuto();

            totalTests = stats.total;
            passedTests = stats.passed;

            // Para negocio: fallados = failed + broken (Allure separa ambos)
            failedTests = stats.failed + stats.broken;

            // Duración real del reporte Allure (ms). Si no viene, conserva la que te pasaron.
            if (stats.durationMs > 0) {
                durationMillis = stats.durationMs;
            }

            System.out.println("[AllureReportSender] Resumen(Allure): total=" + totalTests +
                    ", passed=" + passedTests +
                    ", failed=" + failedTests +
                    ", durationMs=" + durationMillis);
        } catch (Exception e) {
            System.out.println("[AllureReportSender] WARNING: No se pudo leer summary.json, usando contadores recibidos. " + e.getMessage());
        }

        String projectName = pickProjectName(suiteName);
        String subject = buildSubject(projectName, failedTests);
        message.setSubject(subject, "UTF-8");

        String executedHtml = "";
        if (executedTests != null && !executedTests.trim().isEmpty()) {

            // ✅ Elimina duplicados conservando el orden
            List<String> uniqueTests = Arrays.stream(executedTests.split("\\s*\\|\\s*"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .toList();

            StringBuilder sb = new StringBuilder();
            sb.append("<p><b>Tests ejecutados:</b><br>");
            for (String t : uniqueTests) {
                sb.append("• ").append(escapeHtml(t)).append("<br>");
            }
            sb.append("</p>");

            executedHtml = sb.toString();
        }

        String html =
                "<html><body style='font-family:Arial;background:#222;color:#eee;font-size:13px'>" +
                        "<h2 style='color:#66aaff'>Reporte de pruebas automatizadas</h2>" +
                        "<p><b>Proyecto:</b> " + escapeHtml(projectName) + "<br>" +
                        "<b>Ejecutor:</b> " + escapeHtml(EXECUTOR) + "</p>" +
                        executedHtml +
                        buildInteractiveAllureBlock(ALLURE_PUBLIC_URL) +
                        "<table border='1' cellpadding='6' cellspacing='0' " +
                        "style='border-collapse:collapse;background:#111;border:1px solid #666'>" +
                        "  <tr style='background:#333'><th colspan='2'>Resumen</th></tr>" +
                        "  <tr><td>Total</td><td>" + totalTests + "</td></tr>" +
                        "  <tr><td>Pasados</td><td style='color:#00cc00'>" + passedTests + "</td></tr>" +
                        "  <tr><td>Fallados</td><td style='color:#ff4444'>" + failedTests + "</td></tr>" +
                        "  <tr><td>Duración</td><td>" + formatDuration(durationMillis) + "</td></tr>" +
                        "</table>" +
                        "</body></html>";

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(html, "text/html; charset=UTF-8");

        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(htmlPart);

        // ✅ SOLO 1 adjunto: Allure PDF
        MimeBodyPart allurePart = new MimeBodyPart();
        allurePart.attachFile(allurePdf.toFile());
        allurePart.setFileName("Reporte_Allure_" + sanitizeFileName(projectName) + ".pdf");
        multipart.addBodyPart(allurePart);

        message.setContent(multipart);

        System.out.println("[SES] Enviando correo vía " + SMTP_HOST + "...");
        try {
            Transport.send(message);
            System.out.println("[SES] ✔ Correo enviado correctamente a: " + TO_ADDRESS);
        } catch (Throwable e) {
            System.err.println("[SES] ERROR enviando correo (Throwable): " + e.getClass().getName() + " -> " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ======================================================
    //      PDF / CHROME / MERGE (Allure)
    // ======================================================
    public static Path generateAllureOverviewPdf() {
        HttpServer server = null;
        try {
            // 1) Genera allureReport (estático)
            try {
                String projectDir = System.getProperty("user.dir");
                System.out.println("[AllureReportSender] Ejecutando gradlew allureReport en: " + projectDir);

                ProcessBuilder pbAllure = new ProcessBuilder("cmd", "/c", "gradlew.bat", "allureReport");
                pbAllure.directory(new java.io.File(projectDir));
                pbAllure.redirectErrorStream(true);

                Process pAllure = pbAllure.start();

                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(pAllure.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        System.out.println("[gradlew allureReport] " + line);
                    }
                }

                pAllure.waitFor(120, TimeUnit.SECONDS);
                if (pAllure.exitValue() != 0) {
                    System.err.println("[AllureReportSender] gradlew allureReport terminó con código != 0");
                } else {
                    System.out.println("[AllureReportSender] gradlew allureReport OK");
                }

            } catch (Exception e) {
                System.err.println("[AllureReportSender] No se pudo ejecutar gradlew: " + e.getMessage());
            }

            Path reportDir = Paths.get("build", "reports", "allure-report", "allureReport");
            Path indexHtml = reportDir.resolve("index.html");

            if (!Files.exists(indexHtml)) {
                System.err.println("[AllureReportSender] No se encontró index.html en: " + indexHtml);
                return null;
            }

            // 2) Servidor local para assets del reporte
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            int port = server.getAddress().getPort();
            Path finalReportDir = reportDir;

            server.createContext("/", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    try {
                        URI uri = exchange.getRequestURI();
                        String path = uri.getPath();

                        if (path == null || path.equals("/") || path.isEmpty()) {
                            path = "index.html";
                        } else if (path.startsWith("/")) {
                            path = path.substring(1);
                        }

                        Path requested = finalReportDir.resolve(path).normalize();
                        if (!requested.startsWith(finalReportDir) ||
                                !Files.exists(requested) ||
                                Files.isDirectory(requested)) {
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
                        try {
                            exchange.sendResponseHeaders(500, -1);
                        } catch (Exception ignored) {
                        }
                        exchange.close();
                    }
                }
            });

            server.start();

            // 3) Chrome headless imprime Overview
            String chromeExe = CHROME_PATH;
            String urlOverview = "http://127.0.0.1:" + port + "/index.html";

            Path overviewPdf = reportDir.resolve("Allure_Overview.pdf");
            Path behaviorsPdf = reportDir.resolve("Allure_Behaviors.pdf");
            Path finalPdf = reportDir.resolve("Allure_Overview_Behaviors.pdf");

            for (Path p : Arrays.asList(overviewPdf, behaviorsPdf, finalPdf)) {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            }

            ProcessBuilder pbOverview = new ProcessBuilder(
                    chromeExe,
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
                while ((line = reader.readLine()) != null) {
                    System.out.println("[Chrome Overview] " + line);
                }
            }

            pOverview.waitFor(60, TimeUnit.SECONDS);

            // 4) Behaviors con Selenium + screenshot -> PDF
            generateBehaviorsPdfWithSelenium(reportDir, port, behaviorsPdf);

            // 5) Merge Overview + Behaviors
            mergePdfs(Arrays.asList(overviewPdf, behaviorsPdf), finalPdf);

            if (Files.exists(finalPdf)) {
                System.out.println("[AllureReportSender] Final PDF generado: " + finalPdf.toAbsolutePath());
                return finalPdf;
            }
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (server != null) server.stop(0);
        }
    }

    private static void generateBehaviorsPdfWithSelenium(Path reportDir, int port, Path behaviorsPdf) {
        Path behaviorsPng = reportDir.resolve("Allure_Behaviors.png");
        try {
            Files.deleteIfExists(behaviorsPng);
        } catch (Exception ignored) {
        }

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
            System.err.println("[AllureReportSender] ERROR generando Behaviors PDF: " + e.getMessage());
        } finally {
            try {
                driver.quit();
            } catch (Exception ignored) {
            }
        }
    }

    private static void mergePdfs(List<Path> pdfs, Path out) throws Exception {
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.setDestinationFileName(out.toAbsolutePath().toString());

        for (Path p : pdfs) {
            if (p != null && Files.exists(p)) {
                merger.addSource(p.toFile());
            }
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

    private static String formatDuration(long millis) {
        if (millis <= 0) return "-";
        long seconds = millis / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = (seconds % 60);
        return h + "h " + m + "m " + s + "s";
    }

    private static String sanitizeFileName(String s) {
        if (s == null) return "Reporte";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }


    private static String buildInteractiveAllureBlock(String url) {
        if (url == null || url.trim().isEmpty()) return "";
        String u = escapeHtml(url.trim());

        return ""
                + "<div style='margin-top:18px;padding:16px;border:1px solid #2b2f36;border-radius:10px;background:#111827'>"
                + "  <div style='font-size:14px;color:#e5e7eb;margin-bottom:10px;'>"
                + "    🔗 <b>Reporte Allure interactivo (navegable):</b>"
                + "  </div>"
                + "  <a href='" + u + "' style='display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;"
                + "     padding:12px 18px;border-radius:8px;font-weight:bold;'>"
                + "    Abrir reporte interactivo"
                + "  </a>"
                + "  <div style='margin-top:10px;font-size:12px;'>"
                + "    <a href='" + u + "' style='color:#a78bfa;text-decoration:underline;'>" + u + "</a>"
                + "  </div>"
                + "</div>";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}