package utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(PdfReportGenerator.class);

    private static final Path   REPORT_DIR   = Paths.get("build", "reportes-pdf");
    private static final String LOGO_PATH    = "src/test/resources/Cinepolis.png";
    private static final String REPORT_TITLE = "REPORTE DE PRUEBAS AUTOMATIZADAS";

    public static final String EXECUTOR = "Jairo Tomas Baza";
    public static final String PROJECT  = "Cinepolis";

    // ── Color palette (PDFBox 0-1 float RGB) ──────────────────────────────────
    private static final float[] C_NAVY    = {0.043f, 0.122f, 0.302f}; // #0B1F4D
    private static final float[] C_BLUE    = {0.145f, 0.388f, 0.922f}; // #2563EB
    private static final float[] C_GREEN   = {0.063f, 0.725f, 0.506f}; // #10B981
    private static final float[] C_RED     = {0.937f, 0.267f, 0.267f}; // #EF4444
    private static final float[] C_YELLOW  = {0.961f, 0.620f, 0.043f}; // #F59E0B
    private static final float[] C_WHITE   = {1f, 1f, 1f};
    private static final float[] C_CARD_BG = {0.973f, 0.980f, 0.988f}; // #F8FAFC
    private static final float[] C_BORDER  = {0.886f, 0.910f, 0.941f}; // #E2E8F0
    private static final float[] C_TEXT_D  = {0.118f, 0.161f, 0.231f}; // #1E293B
    private static final float[] C_TEXT_M  = {0.392f, 0.455f, 0.545f}; // #64748B
    private static final float[] C_ROW_ALT = {0.973f, 0.980f, 0.988f}; // #F8FAFC
    private static final float[] C_GRN_BG  = {0.878f, 0.969f, 0.937f}; // #E0F7EF
    private static final float[] C_RED_BG  = {0.996f, 0.902f, 0.902f}; // #FDEAEA
    private static final float[] C_YEL_BG  = {0.996f, 0.965f, 0.878f}; // #FEF6E0
    private static final float[] C_META    = {0.620f, 0.710f, 0.858f}; // blue-gray label
    private static final float[] C_FOOTER  = {0.027f, 0.071f, 0.180f}; // #07122E
    private static final float[] C_HDR_DIV = {0.180f, 0.280f, 0.480f}; // column divider

    // ==========================================================================
    //  PUBLIC API — signatures must not change
    // ==========================================================================

    public static void generate(String testName, String cinema, List<StepResult> steps) {
        if (steps == null || steps.isEmpty()) {
            log.info("[PdfReportGenerator] No steps found for test: {}", testName);
            return;
        }

        try (PDDocument doc = new PDDocument()) {
            Files.createDirectories(REPORT_DIR);

            final float margin    = 30f;
            final float rowH      = 105f;
            final float footerH   = 26f;
            final float minBottom = margin + footerH + 4f;

            // Per-test step stats
            int total   = steps.size();
            int passed  = (int) steps.stream().filter(s -> "OK".equalsIgnoreCase(s.getStatus())).count();
            int skipped = (int) steps.stream().filter(s -> "SKIPPED".equalsIgnoreCase(s.getStatus())).count();
            int failed  = total - passed - skipped;
            boolean hasFailure = failed > 0;

            String overallStatus = hasFailure ? "FALLADO"
                    : (skipped > 0 && passed == 0) ? "OMITIDO" : "PASADO";
            float[] stColor = hasFailure ? C_RED : (skipped > 0 && passed == 0) ? C_YELLOW : C_GREEN;
            float[] stBg    = hasFailure ? C_RED_BG : (skipped > 0 && passed == 0) ? C_YEL_BG : C_GRN_BG;

            // Page 1
            PDPage page1 = new PDPage(PDRectangle.A4);
            doc.addPage(page1);
            final float pageW  = page1.getMediaBox().getWidth();
            final float pageH  = page1.getMediaBox().getHeight();
            final float tableW = pageW - 2f * margin;

            // Column widths
            final float colNum    = 28f;
            final float colStatus = 62f;
            final float colName   = (tableW - colNum - colStatus) * 0.42f;
            final float colEvid   = tableW - colNum - colStatus - colName;

            int pageNum = 1;
            PDPageContentStream cs = new PDPageContentStream(doc, page1);

            fillRect(cs, C_WHITE, 0, 0, pageW, pageH);

            // 1. Premium header banner
            float y = drawPremiumHeader(cs, doc, pageW, pageH);

            // 2. KPI cards
            y -= 12f;
            y = drawKpiRow(cs, margin, y, tableW, 65f, total, passed, failed, skipped);

            // 3. Horizontal bar chart
            y -= 10f;
            y = drawBarChart(cs, margin, y, tableW, total, passed, failed, skipped);

            // 4. Test info card
            y -= 10f;
            y = drawTestInfoCard(cs, margin, y, tableW, testName, cinema,
                    overallStatus, stColor, stBg);

            // 5. Step table header
            y -= 10f;
            y = drawStepTableHeader(cs, margin, y, tableW, colNum, colName, colStatus, colEvid);

            // 6. Step rows — multi-page
            for (int i = 0; i < steps.size(); i++) {
                if (y - rowH < minBottom) {
                    drawFooter(cs, pageW, footerH, pageNum);
                    cs.close();
                    pageNum++;

                    PDPage extra = new PDPage(PDRectangle.A4);
                    doc.addPage(extra);
                    cs = new PDPageContentStream(doc, extra);
                    fillRect(cs, C_WHITE, 0, 0, pageW, pageH);
                    y = pageH - margin;
                    y = drawStepTableHeader(cs, margin, y, tableW, colNum, colName, colStatus, colEvid);
                }

                y = drawStepRow(cs, doc, margin, y, tableW, rowH,
                        colNum, colName, colStatus, colEvid, steps.get(i), i, i % 2 == 1);
            }

            // 7. Error section when test failed and there is room
            if (hasFailure && y - 65f >= minBottom) {
                y -= 10f;
                drawErrorSection(cs, margin, y, tableW, steps);
            }

            // 8. Footer on last page
            drawFooter(cs, pageW, footerH, pageNum);
            cs.close();

            String base = (cinema != null && !cinema.isBlank())
                    ? sanitize(testName) + "_" + sanitize(cinema)
                    : sanitize(testName);
            Path outFile = REPORT_DIR.resolve(base + ".pdf");
            doc.save(outFile.toFile());
            log.info("[PdfReportGenerator] PDF generated ({} pages, {} steps): {}",
                    doc.getNumberOfPages(), total, outFile.toAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void createPdfFromImage(File imageFile, File outPdf) {
        if (imageFile == null || !imageFile.exists()) {
            throw new RuntimeException("No existe la imagen para PDF: " + imageFile);
        }
        try (PDDocument doc = new PDDocument()) {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                throw new RuntimeException(
                        "No se pudo leer la imagen (ImageIO devolvio null): " + imageFile);
            }
            float imgW = img.getWidth();
            float imgH = img.getHeight();
            PDPage page = new PDPage(new PDRectangle(imgW, imgH));
            doc.addPage(page);
            var pdImage = LosslessFactory.createFromImage(doc, img);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, 0, 0, imgW, imgH);
            }
            File parent = outPdf.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            doc.save(outPdf);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error creando PDF desde imagen: " + imageFile + " -> " + outPdf, e);
        }
    }

    // ==========================================================================
    //  SECTION DRAWERS
    // ==========================================================================

    private static float drawPremiumHeader(PDPageContentStream cs, PDDocument doc,
                                            float pageW, float pageH) throws IOException {
        final float bannerH = 110f;
        final float yTop    = pageH;

        // Navy banner + blue accent stripe at bottom
        fillRect(cs, C_NAVY, 0, yTop - bannerH, pageW, bannerH);
        fillRect(cs, C_BLUE, 0, yTop - bannerH, pageW, 3f);

        // Logo (left)
        final float logoMaxW = 90f;
        final float logoMaxH = 38f;
        if (Files.exists(Paths.get(LOGO_PATH))) {
            PDImageXObject logo = PDImageXObject.createFromFile(LOGO_PATH, doc);
            float scale = Math.min(logoMaxW / logo.getWidth(), logoMaxH / logo.getHeight());
            float lw = logo.getWidth()  * scale;
            float lh = logo.getHeight() * scale;
            cs.drawImage(logo, 28f, yTop - bannerH / 2f - lh / 2f, lw, lh);
        }

        // Title — centered
        final float titleFs = 13f;
        float titleW = PDType1Font.HELVETICA_BOLD.getStringWidth(REPORT_TITLE) / 1000f * titleFs;
        float titleY = yTop - bannerH / 2f + 12f;

        cs.beginText();
        setNonStrokingColor(cs, C_WHITE);
        cs.setFont(PDType1Font.HELVETICA_BOLD, titleFs);
        cs.newLineAtOffset((pageW - titleW) / 2f, titleY);
        cs.showText(REPORT_TITLE);
        cs.endText();

        // Subtitle — centered
        String sub = PROJECT + "  |  Automatizacion QA";
        float subFs = 8.5f;
        float subW  = PDType1Font.HELVETICA.getStringWidth(sub) / 1000f * subFs;
        cs.beginText();
        setNonStrokingColor(cs, C_META);
        cs.setFont(PDType1Font.HELVETICA, subFs);
        cs.newLineAtOffset((pageW - subW) / 2f, titleY - 17f);
        cs.showText(sub);
        cs.endText();

        // Right-side metadata (4 rows)
        LocalDateTime now = LocalDateTime.now();
        String fecha   = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        String device  = System.getProperty("deviceName", "N/A");
        String env     = System.getProperty("env", "N/A");
        String country = System.getProperty("country", "N/A");

        String[][] meta = {
            {"Fecha:",       fecha},
            {"Ejecutor:",    EXECUTOR},
            {"Ambiente:",    truncate(env + " | " + country, 28)},
            {"Dispositivo:", truncate(device, 24)},
        };

        float metaX    = pageW - 185f;
        float metaY    = yTop - 20f;
        float metaLine = 19f;

        for (String[] row : meta) {
            cs.beginText();
            setNonStrokingColor(cs, C_META);
            cs.setFont(PDType1Font.HELVETICA, 7f);
            cs.newLineAtOffset(metaX, metaY);
            cs.showText(row[0]);
            cs.endText();

            cs.beginText();
            setNonStrokingColor(cs, C_WHITE);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 7f);
            cs.newLineAtOffset(metaX + 60f, metaY);
            cs.showText(row[1]);
            cs.endText();

            metaY -= metaLine;
        }

        return yTop - bannerH;
    }

    private static float drawKpiRow(PDPageContentStream cs,
                                     float x, float yTop, float rowW, float cardH,
                                     int total, int passed, int failed, int skipped) throws IOException {
        final int   n   = 6;
        final float gap = 6f;
        final float cw  = (rowW - gap * (n - 1)) / n;

        String rate  = total > 0 ? String.format("%.0f%%", passed * 100.0 / total) : "0%";
        String estado = failed > 0 ? "FALLADO"
                      : (skipped > 0 && passed == 0) ? "OMITIDO" : "PASADO";

        float[][] accents = {
            C_BLUE, C_GREEN, C_RED, C_YELLOW,
            C_GREEN,
            failed > 0 ? C_RED : (skipped > 0 && passed == 0 ? C_YELLOW : C_GREEN)
        };
        String[] labels = {"Total Pasos", "Pasados", "Fallados", "Omitidos", "Tasa Exito", "Este Test"};
        String[] values = {
            String.valueOf(total), String.valueOf(passed),
            String.valueOf(failed), String.valueOf(skipped),
            rate, estado
        };

        for (int i = 0; i < n; i++) {
            float cx = x + i * (cw + gap);
            float cy = yTop - cardH;

            fillRect(cs, C_CARD_BG, cx, cy, cw, cardH);
            strokeRect(cs, C_BORDER, cx, cy, cw, cardH);
            fillRect(cs, accents[i], cx, cy, 4f, cardH);

            float valFs  = (i == 5) ? 9.5f : 17f;
            float valOffY = (i == 5) ? cardH - 22f : cardH - 26f;

            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_D);
            cs.setFont(PDType1Font.HELVETICA_BOLD, valFs);
            cs.newLineAtOffset(cx + 12f, cy + valOffY);
            cs.showText(values[i]);
            cs.endText();

            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_M);
            cs.setFont(PDType1Font.HELVETICA, 7f);
            cs.newLineAtOffset(cx + 12f, cy + 9f);
            cs.showText(labels[i]);
            cs.endText();
        }

        return yTop - cardH;
    }

    private static float drawBarChart(PDPageContentStream cs,
                                       float x, float yTop, float w,
                                       int total, int passed, int failed, int skipped)
            throws IOException {
        final float secH  = 75f;
        final float barH  = 11f;
        final float barGap = 17f;

        fillRect(cs, C_CARD_BG, x, yTop - secH, w, secH);
        strokeRect(cs, C_BORDER, x, yTop - secH, w, secH);

        cs.beginText();
        setNonStrokingColor(cs, C_TEXT_D);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 8.5f);
        cs.newLineAtOffset(x + 10f, yTop - 14f);
        cs.showText("Distribucion de Resultados");
        cs.endText();

        final float labelW   = 60f;
        final float barAreaX = x + labelW + 10f;
        final float barAreaW = w - labelW - 75f;

        String[]  bLabels = {"Pasados", "Fallados", "Omitidos"};
        int[]     bValues = {passed, failed, skipped};
        float[][] bColors = {C_GREEN, C_RED, C_YELLOW};

        float barY = yTop - 30f;
        for (int i = 0; i < 3; i++) {
            float pct  = total > 0 ? (float) bValues[i] / total : 0f;
            float fill = barAreaW * pct;

            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_M);
            cs.setFont(PDType1Font.HELVETICA, 7.5f);
            cs.newLineAtOffset(x + 10f, barY - 2f);
            cs.showText(bLabels[i]);
            cs.endText();

            fillRect(cs, C_BORDER, barAreaX, barY - barH, barAreaW, barH);
            if (fill > 0.5f) {
                fillRect(cs, bColors[i], barAreaX, barY - barH, fill, barH);
            }

            String pctTxt = String.format("%d (%.0f%%)", bValues[i], pct * 100);
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_D);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 7f);
            cs.newLineAtOffset(barAreaX + barAreaW + 6f, barY - 2f);
            cs.showText(pctTxt);
            cs.endText();

            barY -= barGap;
        }

        return yTop - secH;
    }

    private static float drawTestInfoCard(PDPageContentStream cs,
                                           float x, float yTop, float w,
                                           String testName, String cinema,
                                           String status, float[] stFg, float[] stBg)
            throws IOException {
        final float hdrH   = 28f;
        final float bodyH  = 22f;
        final float totalH = hdrH + bodyH;

        fillRect(cs, C_NAVY, x, yTop - hdrH, w, hdrH);

        cs.beginText();
        setNonStrokingColor(cs, C_WHITE);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9.5f);
        cs.newLineAtOffset(x + 10f, yTop - 18f);
        cs.showText(truncate(testName, 58));
        cs.endText();

        // Status badge in header
        final float bW = 58f, bH = 14f;
        float bX = x + w - bW - 10f;
        float bY = yTop - hdrH / 2f - bH / 2f;
        fillRect(cs, stBg, bX, bY, bW, bH);
        strokeRect(cs, stFg, bX, bY, bW, bH);

        float stW = PDType1Font.HELVETICA_BOLD.getStringWidth(status) / 1000f * 7.5f;
        cs.beginText();
        setNonStrokingColor(cs, stFg);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 7.5f);
        cs.newLineAtOffset(bX + (bW - stW) / 2f, bY + 3.5f);
        cs.showText(status);
        cs.endText();

        // Card body
        fillRect(cs, C_WHITE, x, yTop - totalH, w, bodyH);
        strokeRect(cs, C_BORDER, x, yTop - totalH, w, totalH);

        if (cinema != null && !cinema.isBlank()) {
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_M);
            cs.setFont(PDType1Font.HELVETICA, 8f);
            cs.newLineAtOffset(x + 10f, yTop - hdrH - 14f);
            cs.showText("Cine: " + cinema);
            cs.endText();
        }

        return yTop - totalH;
    }

    private static float drawStepTableHeader(PDPageContentStream cs,
                                              float x, float yTop, float tableW,
                                              float colNum, float colName,
                                              float colStatus, float colEvid) throws IOException {
        final float hdrH = 22f;

        fillRect(cs, C_NAVY, x, yTop - hdrH, tableW, hdrH);

        float tY = yTop - 14f;

        String[] heads = {"#", "PASO", "RESULTADO", "EVIDENCIA"};
        float[]  hX = {
            x + 8f,
            x + colNum + 6f,
            x + colNum + colName + 6f,
            x + colNum + colName + colStatus + 6f
        };
        for (int i = 0; i < heads.length; i++) {
            cs.beginText();
            setNonStrokingColor(cs, C_WHITE);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 8f);
            cs.newLineAtOffset(hX[i], tY);
            cs.showText(heads[i]);
            cs.endText();
        }

        // Column dividers
        float[] divX = {
            x + colNum,
            x + colNum + colName,
            x + colNum + colName + colStatus
        };
        for (float dx : divX) {
            setStrokingColor(cs, C_HDR_DIV);
            cs.moveTo(dx, yTop);
            cs.lineTo(dx, yTop - hdrH);
            cs.stroke();
        }

        return yTop - hdrH;
    }

    private static float drawStepRow(PDPageContentStream cs, PDDocument doc,
                                      float x, float yTop, float tableW, float rowH,
                                      float colNum, float colName, float colStatus, float colEvid,
                                      StepResult step, int idx, boolean alt) throws IOException {
        float rowBottom = yTop - rowH;

        fillRect(cs, alt ? C_ROW_ALT : C_WHITE, x, rowBottom, tableW, rowH);
        hLine(cs, C_BORDER, x, rowBottom, tableW);

        // Step number badge (blue square)
        final float badgeSz = 16f;
        float bX = x + (colNum - badgeSz) / 2f;
        float bY = yTop - rowH / 2f - badgeSz / 2f;
        fillRect(cs, C_BLUE, bX, bY, badgeSz, badgeSz);

        String numStr = String.valueOf(idx + 1);
        float numW = PDType1Font.HELVETICA_BOLD.getStringWidth(numStr) / 1000f * 7f;
        cs.beginText();
        setNonStrokingColor(cs, C_WHITE);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 7f);
        cs.newLineAtOffset(bX + (badgeSz - numW) / 2f, bY + 4.5f);
        cs.showText(numStr);
        cs.endText();

        // Step name
        cs.beginText();
        setNonStrokingColor(cs, C_TEXT_D);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 8.5f);
        cs.newLineAtOffset(x + colNum + 6f, yTop - 16f);
        cs.showText(truncate(step.getStepName(), 46));
        cs.endText();

        // Status badge
        String raw = step.getStatus();
        String display;
        float[] sFg, sBg;
        if ("OK".equalsIgnoreCase(raw)) {
            display = "PASADO";  sFg = C_GREEN;  sBg = C_GRN_BG;
        } else if ("SKIPPED".equalsIgnoreCase(raw)) {
            display = "OMITIDO"; sFg = C_YELLOW; sBg = C_YEL_BG;
        } else {
            display = "FALLADO"; sFg = C_RED;    sBg = C_RED_BG;
        }

        float sbW = colStatus - 10f;
        float sbH = 14f;
        float sbX = x + colNum + colName + 5f;
        float sbY = yTop - rowH / 2f - sbH / 2f;
        fillRect(cs, sBg, sbX, sbY, sbW, sbH);
        strokeRect(cs, sFg, sbX, sbY, sbW, sbH);

        float dsW = PDType1Font.HELVETICA_BOLD.getStringWidth(display) / 1000f * 7f;
        cs.beginText();
        setNonStrokingColor(cs, sFg);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 7f);
        cs.newLineAtOffset(sbX + (sbW - dsW) / 2f, sbY + 3.5f);
        cs.showText(display);
        cs.endText();

        // Screenshot thumbnail
        float evidX = x + colNum + colName + colStatus;
        String shotPath = step.getScreenshotPath();
        if (shotPath != null && Files.exists(Paths.get(shotPath))) {
            PDImageXObject img = PDImageXObject.createFromFile(shotPath, doc);
            float availW = colEvid - 10f;
            float availH = rowH - 10f;
            float scale  = Math.min(availW / img.getWidth(), availH / img.getHeight());
            float imgW   = img.getWidth()  * scale;
            float imgH   = img.getHeight() * scale;
            float imgX   = evidX + (colEvid - imgW) / 2f;
            float imgY   = yTop - rowH / 2f - imgH / 2f;
            fillRect(cs, C_BORDER, imgX - 1f, imgY - 1f, imgW + 2f, imgH + 2f);
            cs.drawImage(img, imgX, imgY, imgW, imgH);
        } else {
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_M);
            cs.setFont(PDType1Font.HELVETICA, 7f);
            cs.newLineAtOffset(evidX + 8f, yTop - rowH / 2f - 3f);
            cs.showText("Sin evidencia");
            cs.endText();
        }

        return rowBottom;
    }

    private static float drawErrorSection(PDPageContentStream cs,
                                           float x, float yTop, float w,
                                           List<StepResult> steps) throws IOException {
        final float secH = 60f;

        fillRect(cs, C_RED_BG, x, yTop - secH, w, secH);
        fillRect(cs, C_RED,    x, yTop - secH, 4f, secH);
        strokeRect(cs, C_RED,  x, yTop - secH, w, secH);

        cs.beginText();
        setNonStrokingColor(cs, C_RED);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9f);
        cs.newLineAtOffset(x + 14f, yTop - 15f);
        cs.showText("Error Detectado en la Ejecucion");
        cs.endText();

        StepResult firstFailed = steps.stream()
                .filter(s -> "FAIL".equalsIgnoreCase(s.getStatus())
                          || "ERROR".equalsIgnoreCase(s.getStatus()))
                .findFirst().orElse(null);

        if (firstFailed != null) {
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_D);
            cs.setFont(PDType1Font.HELVETICA, 8.5f);
            cs.newLineAtOffset(x + 14f, yTop - 32f);
            cs.showText("Paso fallido: " + truncate(firstFailed.getStepName(), 68));
            cs.endText();
        }

        cs.beginText();
        setNonStrokingColor(cs, C_TEXT_M);
        cs.setFont(PDType1Font.HELVETICA, 7.5f);
        cs.newLineAtOffset(x + 14f, yTop - 48f);
        cs.showText("Revisar la evidencia adjunta del paso fallido para mayor detalle.");
        cs.endText();

        return yTop - secH;
    }

    private static void drawFooter(PDPageContentStream cs, float pageW,
                                    float footerH, int pageNum) throws IOException {
        fillRect(cs, C_FOOTER, 0, 0, pageW, footerH);

        String left  = PROJECT + " | QA Automation | Appium + Java + JUnit 5 + Allure";
        String right = "Pag. " + pageNum;

        cs.beginText();
        setNonStrokingColor(cs, C_META);
        cs.setFont(PDType1Font.HELVETICA, 6.5f);
        cs.newLineAtOffset(10f, 9f);
        cs.showText(left);
        cs.endText();

        float rW = PDType1Font.HELVETICA.getStringWidth(right) / 1000f * 6.5f;
        cs.beginText();
        setNonStrokingColor(cs, C_META);
        cs.setFont(PDType1Font.HELVETICA, 6.5f);
        cs.newLineAtOffset(pageW - rW - 10f, 9f);
        cs.showText(right);
        cs.endText();
    }

    // ==========================================================================
    //  LOW-LEVEL HELPERS
    // ==========================================================================

    private static void fillRect(PDPageContentStream cs, float[] rgb,
                                   float x, float y, float w, float h) throws IOException {
        setNonStrokingColor(cs, rgb);
        cs.addRect(x, y, w, h);
        cs.fill();
    }

    private static void strokeRect(PDPageContentStream cs, float[] rgb,
                                    float x, float y, float w, float h) throws IOException {
        setStrokingColor(cs, rgb);
        cs.addRect(x, y, w, h);
        cs.stroke();
    }

    private static void hLine(PDPageContentStream cs, float[] rgb,
                               float x, float y, float len) throws IOException {
        setStrokingColor(cs, rgb);
        cs.moveTo(x, y);
        cs.lineTo(x + len, y);
        cs.stroke();
    }

    private static void setNonStrokingColor(PDPageContentStream c, float[] rgb) throws IOException {
        c.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
    }

    private static void setStrokingColor(PDPageContentStream c, float[] rgb) throws IOException {
        c.setStrokingColor(rgb[0], rgb[1], rgb[2]);
    }

    private static String sanitize(String t) {
        return t.replaceAll("[^a-zA-Z0-9-_]", "_");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
