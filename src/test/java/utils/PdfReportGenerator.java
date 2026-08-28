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

    // ── Palette ────────────────────────────────────────────────────────────────
    private static final float[] C_NAVY    = {0.043f, 0.122f, 0.302f}; // #0B1F4D
    private static final float[] C_BLUE    = {0.094f, 0.271f, 0.600f}; // #183E99
    private static final float[] C_GREEN   = {0.063f, 0.725f, 0.506f}; // #10B981
    private static final float[] C_RED     = {0.937f, 0.267f, 0.267f}; // #EF4444
    private static final float[] C_YELLOW  = {0.961f, 0.620f, 0.043f}; // #F59E0B
    private static final float[] C_WHITE   = {1f, 1f, 1f};
    private static final float[] C_CARD_BG = {0.973f, 0.980f, 0.988f}; // #F8FAFC
    private static final float[] C_BORDER  = {0.886f, 0.910f, 0.941f}; // #E2E8F0
    private static final float[] C_TEXT_D  = {0.118f, 0.161f, 0.231f}; // #1E293B
    private static final float[] C_TEXT_M  = {0.392f, 0.455f, 0.545f}; // #64748B
    private static final float[] C_GRN_BG  = {0.878f, 0.969f, 0.937f}; // #E0F7EF
    private static final float[] C_RED_BG  = {0.996f, 0.902f, 0.902f}; // #FDEAEA
    private static final float[] C_YEL_BG  = {0.996f, 0.965f, 0.878f}; // #FEF6E0
    private static final float[] C_BLU_BG  = {0.910f, 0.925f, 0.976f}; // light blue bg
    private static final float[] C_RING_BG = {0.922f, 0.929f, 0.945f}; // donut ring bg
    private static final float[] C_GRN_DARK= {0.024f, 0.471f, 0.337f}; // #067A56
    private static final float[] C_GRAY_IC = {0.388f, 0.439f, 0.541f}; // icon circle gray

    // ==========================================================================
    //  PUBLIC API
    // ==========================================================================

    public static void generate(String testName, String cinema, List<StepResult> steps) {
        if (steps == null || steps.isEmpty()) {
            log.info("[PdfReportGenerator] No steps found for test: {}", testName);
            return;
        }

        try (PDDocument doc = new PDDocument()) {
            Files.createDirectories(REPORT_DIR);

            final float margin  = 30f;
            final float rowH    = 95f;
            final float footerH = 70f;
            final float minBottom = margin + footerH + 4f;

            int total   = steps.size();
            int passed  = (int) steps.stream().filter(s -> "OK".equalsIgnoreCase(s.getStatus())).count();
            int skipped = (int) steps.stream().filter(s -> "SKIPPED".equalsIgnoreCase(s.getStatus())).count();
            int failed  = total - passed - skipped;
            boolean hasFailure = failed > 0;

            boolean isSkippedOverall = skipped > 0 && passed == 0 && !hasFailure;
            String overallStatus = hasFailure ? "FALLADO" : isSkippedOverall ? "OMITIDO" : "PASADO";
            float[] stColor = hasFailure ? C_RED : isSkippedOverall ? C_YELLOW : C_GREEN;
            float[] stBg    = hasFailure ? C_RED_BG : isSkippedOverall ? C_YEL_BG : C_GRN_BG;

            // Motivo real del skip (si existe) — solo se usa cuando el caso completo
            // quedó OMITIDO, nunca cuando un paso SKIP convive con otros pasos OK/FAIL.
            String skipReason = isSkippedOverall ? findSkipReason(steps) : null;

            PDPage page1 = new PDPage(PDRectangle.A4);
            doc.addPage(page1);
            final float pageW  = page1.getMediaBox().getWidth();
            final float pageH  = page1.getMediaBox().getHeight();
            final float tableW = pageW - 2f * margin;

            final float colNum    = 32f;
            final float colStatus = 64f;
            final float colName   = (tableW - colNum - colStatus) * 0.42f;
            final float colEvid   = tableW - colNum - colStatus - colName;

            int pageNum = 1;
            PDPageContentStream cs = new PDPageContentStream(doc, page1);

            fillRect(cs, C_WHITE, 0, 0, pageW, pageH);

            // 1. Header (white bg, logo + title + metadata)
            float y = drawHeader(cs, doc, pageW, pageH);

            // 2. Summary: RESUMEN DE EJECUCION (metric cards + donut)
            y -= 14f;
            y = drawSummarySection(cs, margin, y, tableW, total, passed, failed, skipped);

            // 3. Test info card
            y -= 12f;
            y = drawTestInfoCard(cs, margin, y, tableW, testName, cinema, overallStatus, stColor, stBg);

            // 4. DETALLE DE EJECUCION title
            y -= 10f;
            y = drawSectionTitle(cs, margin, y, tableW, "DETALLE DE EJECUCION");

            // 5. Step table header
            y -= 4f;
            y = drawStepTableHeader(cs, margin, y, tableW, colNum, colName, colStatus, colEvid);

            // 6. Step rows (multi-page)
            for (int i = 0; i < steps.size(); i++) {
                if (y - rowH < minBottom) {
                    drawFooter(cs, pageW, pageH, footerH, pageNum, total, passed);
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

            // 7. Error card if failed and space allows
            if (hasFailure && y - 65f >= minBottom) {
                y -= 10f;
                drawErrorSection(cs, margin, y, tableW, steps);
            } else if (skipReason != null && y - 78f >= minBottom) {
                y -= 10f;
                drawSkippedSection(cs, margin, y, tableW, skipReason);
            }

            // 8. Footer
            drawFooter(cs, pageW, pageH, footerH, pageNum, total, passed);
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

    /** White header: logo | title + metadata | decorative block */
    private static float drawHeader(PDPageContentStream cs, PDDocument doc,
                                     float pageW, float pageH) throws IOException {
        final float hdrH = 88f;
        final float yTop = pageH;

        fillRect(cs, C_WHITE, 0, yTop - hdrH, pageW, hdrH);

        // ── Logo ─────────────────────────────────────────────────────────────
        final float logoMaxW = 80f;
        final float logoMaxH = 32f;
        if (Files.exists(Paths.get(LOGO_PATH))) {
            PDImageXObject logo = PDImageXObject.createFromFile(LOGO_PATH, doc);
            float s  = Math.min(logoMaxW / logo.getWidth(), logoMaxH / logo.getHeight());
            float lw = logo.getWidth() * s;
            float lh = logo.getHeight() * s;
            cs.drawImage(logo, 28f, yTop - 22f - lh, lw, lh);
        }

        // ── Title ─────────────────────────────────────────────────────────────
        float titleFs = 14f;
        float titleW  = PDType1Font.HELVETICA_BOLD.getStringWidth(REPORT_TITLE) / 1000f * titleFs;
        float titleX  = (pageW - titleW) / 2f;
        float titleY  = yTop - 22f;

        cs.beginText();
        setNonStrokingColor(cs, C_NAVY);
        cs.setFont(PDType1Font.HELVETICA_BOLD, titleFs);
        cs.newLineAtOffset(titleX, titleY);
        cs.showText(REPORT_TITLE);
        cs.endText();

        // ── Decorative clipboard block (top-right) ────────────────────────────
        float blkX = pageW - 48f;
        float blkY = yTop - hdrH + 8f;
        float blkW = 36f;
        float blkH = 50f;
        fillRect(cs, C_NAVY, blkX, blkY, blkW, blkH);
        // White horizontal lines inside
        setStrokingColor(cs, C_WHITE);
        cs.setLineWidth(1.2f);
        for (int li = 0; li < 4; li++) {
            float ly = blkY + blkH - 10f - li * 8f;
            cs.moveTo(blkX + 5f, ly);
            cs.lineTo(blkX + blkW - 5f, ly);
            cs.stroke();
        }
        cs.setLineWidth(1f);
        // Green check circle on clipboard
        float ckCx = blkX + blkW - 2f;
        float ckCy = blkY + blkH - 2f;
        fillCircle(cs, C_GREEN, ckCx, ckCy, 8f);
        cs.beginText();
        setNonStrokingColor(cs, C_WHITE);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 8f);
        cs.newLineAtOffset(ckCx - 3.5f, ckCy - 3f);
        cs.showText("v");
        cs.endText();

        // ── Metadata row (3 columns) ──────────────────────────────────────────
        LocalDateTime now  = LocalDateTime.now();
        String fecha  = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        String env    = System.getProperty("env", "N/A");
        String country= System.getProperty("country", "");

        String[][] meta = {
            {"Fecha de Ejecucion", fecha},
            {"Ejecutor",           EXECUTOR},
            {"Proyecto",           PROJECT},
        };

        float metaY   = titleY - 22f;
        float colW    = (pageW - 56f - 60f) / 3f;
        float metaX   = 28f;

        for (String[] m : meta) {
            // small navy circle + dot
            fillCircle(cs, C_NAVY, metaX + 6f, metaY + 5f, 5.5f);
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_M);
            cs.setFont(PDType1Font.HELVETICA, 7f);
            cs.newLineAtOffset(metaX + 15f, metaY + 4f);
            cs.showText(m[0]);
            cs.endText();

            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_D);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 7.5f);
            cs.newLineAtOffset(metaX + 15f, metaY - 7f);
            cs.showText(truncate(m[1], 26));
            cs.endText();

            metaX += colW;
        }

        // Env + country under the third column area
        if (!env.equals("N/A") || !country.isEmpty()) {
            float envX = 28f + 2f * colW + 15f;
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_M);
            cs.setFont(PDType1Font.HELVETICA, 7f);
            cs.newLineAtOffset(envX, metaY - 20f);
            cs.showText("Amb: " + truncate(env + (country.isEmpty() ? "" : " | " + country), 28));
            cs.endText();
        }

        // ── Separator line ────────────────────────────────────────────────────
        setStrokingColor(cs, C_BORDER);
        cs.setLineWidth(0.8f);
        cs.moveTo(28f, yTop - hdrH + 1f);
        cs.lineTo(pageW - 28f, yTop - hdrH + 1f);
        cs.stroke();
        cs.setLineWidth(1f);

        return yTop - hdrH;
    }

    /** RESUMEN DE EJECUCION: section title + 4 metric cards + donut chart */
    private static float drawSummarySection(PDPageContentStream cs,
                                             float x, float yTop, float w,
                                             int total, int passed, int failed, int skipped)
            throws IOException {
        final float titleH   = 22f;
        final float cardsH   = 88f;
        final float totalH   = titleH + 8f + cardsH;

        // Section title bar
        y_drawSectionTitle(cs, x, yTop, w, titleH, "RESUMEN DE EJECUCION");

        float y = yTop - titleH - 8f;

        // Cards area: left ~62%  Donut: right ~35%
        float cardsAreaW = w * 0.62f;
        float donutAreaW = w - cardsAreaW - 10f;
        float donutAreaX = x + cardsAreaW + 10f;

        // 4 metric cards
        float gap  = 8f;
        float cardW = (cardsAreaW - 3f * gap) / 4f;

        float[][] icColors = {C_GRAY_IC, C_GREEN, C_RED, C_YELLOW};
        String[] labels  = {"TOTAL", "PASADAS", "FALLADAS", "OMITIDAS"};
        int[]    counts  = {total, passed, failed, skipped};
        float[]  pcts    = {
            100f,
            total > 0 ? passed  * 100f / total : 0f,
            total > 0 ? failed  * 100f / total : 0f,
            total > 0 ? skipped * 100f / total : 0f,
        };
        float[][] bgColors = {C_BLU_BG, C_GRN_BG, C_RED_BG, C_YEL_BG};
        float[][] fgColors = {C_BLUE,   C_GREEN,   C_RED,    C_YELLOW};

        for (int i = 0; i < 4; i++) {
            float cx = x + i * (cardW + gap);
            float cy = y - cardsH;

            fillRect(cs, C_WHITE, cx, cy, cardW, cardsH);
            strokeRect(cs, C_BORDER, cx, cy, cardW, cardsH);

            // Colored circle icon
            float icR  = 13f;
            float icCx = cx + cardW / 2f;
            float icCy = y - 20f;
            fillCircle(cs, bgColors[i], icCx, icCy, icR);
            // Small inner symbol
            drawCardIcon(cs, icColors[i], icCx, icCy, i);

            // Count
            String countStr = String.valueOf(counts[i]);
            float cntFs = 20f;
            float cntW  = PDType1Font.HELVETICA_BOLD.getStringWidth(countStr) / 1000f * cntFs;
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_D);
            cs.setFont(PDType1Font.HELVETICA_BOLD, cntFs);
            cs.newLineAtOffset(cx + (cardW - cntW) / 2f, y - 51f);
            cs.showText(countStr);
            cs.endText();

            // Label
            float lblW = PDType1Font.HELVETICA_BOLD.getStringWidth(labels[i]) / 1000f * 7f;
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_M);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 7f);
            cs.newLineAtOffset(cx + (cardW - lblW) / 2f, y - 64f);
            cs.showText(labels[i]);
            cs.endText();

            // Percentage
            String pctStr = String.format("%.0f%%", pcts[i]);
            float pctW2 = PDType1Font.HELVETICA_BOLD.getStringWidth(pctStr) / 1000f * 7.5f;
            cs.beginText();
            setNonStrokingColor(cs, fgColors[i]);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 7.5f);
            cs.newLineAtOffset(cx + (cardW - pctW2) / 2f, y - 77f);
            cs.showText(pctStr);
            cs.endText();
        }

        // Donut chart
        float donutCx = donutAreaX + donutAreaW / 2f;
        float donutCy = y - cardsH / 2f;
        float outerR  = 32f;
        float innerR  = 20f;

        drawDonut(cs, donutCx, donutCy, outerR, innerR,
                total, passed, failed, skipped);

        // Donut legend
        float lgX = donutAreaX + 4f;
        float lgY = y - cardsH + 16f;
        String[][] legend = {
            {"Pasadas",  String.valueOf(passed)},
            {"Falladas", String.valueOf(failed)},
            {"Omitidas", String.valueOf(skipped)},
        };
        float[][] lgColors = {C_GREEN, C_RED, C_YELLOW};

        for (int i = 0; i < 3; i++) {
            fillRect(cs, lgColors[i], lgX, lgY - 5f, 8f, 8f);
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_M);
            cs.setFont(PDType1Font.HELVETICA, 7f);
            cs.newLineAtOffset(lgX + 12f, lgY);
            cs.showText(legend[i][0] + " (" + legend[i][1] + ")");
            cs.endText();
            lgY += 16f;
        }

        return yTop - totalH;
    }

    /** Navy full-width card with test name + status badge */
    private static float drawTestInfoCard(PDPageContentStream cs,
                                           float x, float yTop, float w,
                                           String testName, String cinema,
                                           String status, float[] stFg, float[] stBg)
            throws IOException {
        final float hdrH  = 36f;
        final float bodyH = cinema != null && !cinema.isBlank() ? 24f : 0f;
        final float totalH = hdrH + bodyH;

        fillRect(cs, C_NAVY, x, yTop - hdrH, w, hdrH);

        // Circle icon left
        fillCircle(cs, C_BLUE, x + 22f, yTop - hdrH / 2f, 12f);
        cs.beginText();
        setNonStrokingColor(cs, C_WHITE);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9f);
        cs.newLineAtOffset(x + 17f, yTop - hdrH / 2f - 3.5f);
        cs.showText("T");
        cs.endText();

        // Test name
        cs.beginText();
        setNonStrokingColor(cs, C_WHITE);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 10f);
        cs.newLineAtOffset(x + 42f, yTop - hdrH / 2f + 3f);
        cs.showText(truncate(testName, 54));
        cs.endText();

        // Status badge
        final float bW = 62f, bH = 16f;
        float bX = x + w - bW - 10f;
        float bY = yTop - hdrH / 2f - bH / 2f;
        fillRect(cs, stBg, bX, bY, bW, bH);
        strokeRect(cs, stFg, bX, bY, bW, bH);

        float stW = PDType1Font.HELVETICA_BOLD.getStringWidth(status) / 1000f * 8f;
        cs.beginText();
        setNonStrokingColor(cs, stFg);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 8f);
        cs.newLineAtOffset(bX + (bW - stW) / 2f, bY + 4f);
        cs.showText(status);
        cs.endText();

        // Body (cinema)
        if (bodyH > 0) {
            fillRect(cs, C_CARD_BG, x, yTop - totalH, w, bodyH);
            strokeRect(cs, C_BORDER, x, yTop - totalH, w, totalH);
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_M);
            cs.setFont(PDType1Font.HELVETICA, 8f);
            cs.newLineAtOffset(x + 42f, yTop - hdrH - 15f);
            cs.showText("Cine: " + cinema);
            cs.endText();
        }

        return yTop - totalH;
    }

    /** Section title with left navy accent */
    private static float drawSectionTitle(PDPageContentStream cs,
                                           float x, float yTop, float w,
                                           String title) throws IOException {
        final float h = 20f;
        fillRect(cs, C_CARD_BG, x, yTop - h, w, h);
        fillRect(cs, C_NAVY, x, yTop - h, 4f, h);

        cs.beginText();
        setNonStrokingColor(cs, C_NAVY);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9.5f);
        cs.newLineAtOffset(x + 12f, yTop - 13f);
        cs.showText(title);
        cs.endText();

        return yTop - h;
    }

    /** Step table header row */
    private static float drawStepTableHeader(PDPageContentStream cs,
                                              float x, float yTop, float tableW,
                                              float colNum, float colName,
                                              float colStatus, float colEvid) throws IOException {
        final float hdrH = 22f;
        fillRect(cs, C_NAVY, x, yTop - hdrH, tableW, hdrH);

        float tY = yTop - 14f;
        String[] heads = {"#", "PASO", "RESULTADO", "EVIDENCIA"};
        float[] hX = {
            x + 9f,
            x + colNum + 8f,
            x + colNum + colName + 8f,
            x + colNum + colName + colStatus + 8f
        };
        for (int i = 0; i < heads.length; i++) {
            cs.beginText();
            setNonStrokingColor(cs, C_WHITE);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 8f);
            cs.newLineAtOffset(hX[i], tY);
            cs.showText(heads[i]);
            cs.endText();
        }

        float[] divX = {
            x + colNum,
            x + colNum + colName,
            x + colNum + colName + colStatus
        };
        setStrokingColor(cs, new float[]{0.18f, 0.28f, 0.48f});
        cs.setLineWidth(0.5f);
        for (float dx : divX) {
            cs.moveTo(dx, yTop);
            cs.lineTo(dx, yTop - hdrH);
            cs.stroke();
        }
        cs.setLineWidth(1f);

        return yTop - hdrH;
    }

    /** Single step row with circle badge + name + status pill + screenshot */
    private static float drawStepRow(PDPageContentStream cs, PDDocument doc,
                                      float x, float yTop, float tableW, float rowH,
                                      float colNum, float colName, float colStatus, float colEvid,
                                      StepResult step, int idx, boolean alt) throws IOException {
        float rowBottom = yTop - rowH;
        fillRect(cs, alt ? C_CARD_BG : C_WHITE, x, rowBottom, tableW, rowH);
        hLine(cs, C_BORDER, x, rowBottom, tableW);

        // Circle step badge (navy)
        final float badgeR = 10f;
        float bcx = x + colNum / 2f;
        float bcy = yTop - rowH / 2f;
        fillCircle(cs, C_NAVY, bcx, bcy, badgeR);

        String numStr = String.valueOf(idx + 1);
        float numW = PDType1Font.HELVETICA_BOLD.getStringWidth(numStr) / 1000f * 7.5f;
        cs.beginText();
        setNonStrokingColor(cs, C_WHITE);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 7.5f);
        cs.newLineAtOffset(bcx - numW / 2f, bcy - 3f);
        cs.showText(numStr);
        cs.endText();

        // Step name (bold)
        cs.beginText();
        setNonStrokingColor(cs, C_TEXT_D);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9f);
        cs.newLineAtOffset(x + colNum + 8f, yTop - 20f);
        cs.showText(truncate(step.getStepName(), 44));
        cs.endText();

        // Status badge
        String raw = step.getStatus();
        String display;
        float[] sFg, sBg;
        if ("OK".equalsIgnoreCase(raw)) {
            display = "OK";    sFg = C_GREEN;  sBg = C_GRN_BG;
        } else if ("SKIPPED".equalsIgnoreCase(raw)) {
            display = "SKIP";  sFg = C_YELLOW; sBg = C_YEL_BG;
        } else {
            display = "FAIL";  sFg = C_RED;    sBg = C_RED_BG;
        }

        float sbW = colStatus - 12f;
        float sbH = 15f;
        float sbX = x + colNum + colName + 6f;
        float sbY = yTop - rowH / 2f - sbH / 2f;
        fillRect(cs, sBg, sbX, sbY, sbW, sbH);
        strokeRect(cs, sFg, sbX, sbY, sbW, sbH);

        // Status text + check/X mark
        String badge = "OK".equals(display) ? "OK  v" : ("SKIP".equals(display) ? "SKIP -" : "FAIL x");
        float bTxtW = PDType1Font.HELVETICA_BOLD.getStringWidth(badge) / 1000f * 7.5f;
        cs.beginText();
        setNonStrokingColor(cs, sFg);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 7.5f);
        cs.newLineAtOffset(sbX + (sbW - bTxtW) / 2f, sbY + 4f);
        cs.showText(badge);
        cs.endText();

        // Screenshot
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

    /** Red-bordered error card */
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

        steps.stream()
             .filter(s -> "FAIL".equalsIgnoreCase(s.getStatus()) || "ERROR".equalsIgnoreCase(s.getStatus()))
             .findFirst()
             .ifPresent(f -> {
                 try {
                     cs.beginText();
                     setNonStrokingColor(cs, C_TEXT_D);
                     cs.setFont(PDType1Font.HELVETICA, 8.5f);
                     cs.newLineAtOffset(x + 14f, yTop - 32f);
                     cs.showText("Paso fallido: " + truncate(f.getStepName(), 68));
                     cs.endText();
                 } catch (IOException ignored) {}
             });

        cs.beginText();
        setNonStrokingColor(cs, C_TEXT_M);
        cs.setFont(PDType1Font.HELVETICA, 7.5f);
        cs.newLineAtOffset(x + 14f, yTop - 48f);
        cs.showText("Revisar la evidencia adjunta del paso fallido para mayor detalle.");
        cs.endText();

        return yTop - secH;
    }

    /**
     * Busca el motivo real del skip entre los pasos — Assumptions.abort() detiene
     * la ejecucion en el acto, asi que solo puede existir un paso SKIPPED por caso.
     * Retorna null si no hay un motivo real y limpio que mostrar (nunca se inventa
     * texto generico: si no hay razon util, la seccion completa se omite en el PDF).
     */
    private static String findSkipReason(List<StepResult> steps) {
        return steps.stream()
                .filter(s -> "SKIPPED".equalsIgnoreCase(s.getStatus()))
                .map(s -> cleanSkipReason(s.getReason()))
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
    }

    /** Prefijos tecnicos ("SKIPPED:") o textos genericos que no aportan valor real. */
    private static final java.util.Set<String> GENERIC_SKIP_TEXTS = java.util.Set.of(
            "skipped", "no ejecutado", "sin informacion", "sin información");

    private static String cleanSkipReason(String raw) {
        if (raw == null) return null;
        String cleaned = raw.trim();
        if (cleaned.regionMatches(true, 0, "SKIPPED:", 0, 8)) {
            cleaned = cleaned.substring(8).trim();
        } else if (cleaned.regionMatches(true, 0, "SKIPPED", 0, 7)) {
            cleaned = cleaned.substring(7).trim();
        }
        if (cleaned.isBlank() || GENERIC_SKIP_TEXTS.contains(cleaned.toLowerCase())) return null;
        return cleaned;
    }

    /** Card amarilla con el motivo real de un caso OMITIDO — mismo estilo visual que drawErrorSection. */
    private static float drawSkippedSection(PDPageContentStream cs,
                                             float x, float yTop, float w,
                                             String skipReason) throws IOException {
        String full = "Prueba omitida: " + skipReason
                + (skipReason.matches(".*[.!?]$") ? "" : ".");

        String line1 = full;
        String line2 = null;
        final int maxLineLen = 95;
        if (full.length() > maxLineLen) {
            int splitAt = full.lastIndexOf(' ', maxLineLen);
            if (splitAt <= 0) splitAt = maxLineLen;
            line1 = full.substring(0, splitAt).trim();
            line2 = truncate(full.substring(splitAt).trim(), maxLineLen);
        }

        final float secH = (line2 != null) ? 74f : 60f;
        fillRect(cs, C_YEL_BG, x, yTop - secH, w, secH);
        fillRect(cs, C_YELLOW, x, yTop - secH, 4f, secH);
        strokeRect(cs, C_YELLOW, x, yTop - secH, w, secH);

        cs.beginText();
        setNonStrokingColor(cs, C_YELLOW);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9f);
        cs.newLineAtOffset(x + 14f, yTop - 15f);
        cs.showText("PRUEBA OMITIDA");
        cs.endText();

        cs.beginText();
        setNonStrokingColor(cs, C_TEXT_D);
        cs.setFont(PDType1Font.HELVETICA, 8.5f);
        cs.newLineAtOffset(x + 14f, yTop - 32f);
        cs.showText(line1);
        cs.endText();

        if (line2 != null) {
            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_D);
            cs.setFont(PDType1Font.HELVETICA, 8.5f);
            cs.newLineAtOffset(x + 14f, yTop - 46f);
            cs.showText(line2);
            cs.endText();
        }

        return yTop - secH;
    }

    /** Footer: 4 info blocks + green bar at absolute bottom */
    private static void drawFooter(PDPageContentStream cs, float pageW, float pageH,
                                    float footerH, int pageNum,
                                    int total, int passed) throws IOException {
        final float greenBarH = 20f;
        final float infoH     = footerH - greenBarH;
        final float infoY     = greenBarH;

        String device  = System.getProperty("deviceName", "N/A");
        String env     = System.getProperty("env", "N/A");
        String country = System.getProperty("country", "");
        String amb     = country.isEmpty() ? env : env + " | " + country;

        fillRect(cs, C_CARD_BG, 0, infoY, pageW, infoH);

        // Top border line
        setStrokingColor(cs, C_BORDER);
        cs.setLineWidth(0.6f);
        cs.moveTo(0, infoY + infoH);
        cs.lineTo(pageW, infoY + infoH);
        cs.stroke();
        cs.setLineWidth(1f);

        // 4 info blocks
        float colW  = pageW / 4f;
        float[][] icColors = {C_BLUE, C_GREEN, C_NAVY, C_GRAY_IC};
        String[] icLabels  = {"DISPOSITIVO", "AMBIENTE", "TOTAL PASOS", "EJECUCION"};
        String[] icValues  = {
            truncate(device, 22),
            truncate(amb, 22),
            passed + " / " + total + " pasados",
            "Appium + Java + JUnit 5"
        };

        for (int i = 0; i < 4; i++) {
            float bx = i * colW + 12f;
            float by = infoY + infoH / 2f;

            // Vertical divider
            if (i > 0) {
                setStrokingColor(cs, C_BORDER);
                cs.setLineWidth(0.5f);
                cs.moveTo(i * colW, infoY + 6f);
                cs.lineTo(i * colW, infoY + infoH - 6f);
                cs.stroke();
                cs.setLineWidth(1f);
            }

            // Small colored circle
            fillCircle(cs, icColors[i], bx + 6f, by + 2f, 6f);

            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_M);
            cs.setFont(PDType1Font.HELVETICA, 6.5f);
            cs.newLineAtOffset(bx + 16f, by + 5f);
            cs.showText(icLabels[i]);
            cs.endText();

            cs.beginText();
            setNonStrokingColor(cs, C_TEXT_D);
            cs.setFont(PDType1Font.HELVETICA_BOLD, 7.5f);
            cs.newLineAtOffset(bx + 16f, by - 8f);
            cs.showText(icValues[i]);
            cs.endText();
        }

        // Green bar
        fillRect(cs, C_GRN_DARK, 0, 0, pageW, greenBarH);

        String leftTxt  = "Reporte generado automaticamente por Automation QA";
        String rightTxt = "Pagina " + pageNum;

        cs.beginText();
        setNonStrokingColor(cs, C_WHITE);
        cs.setFont(PDType1Font.HELVETICA, 7f);
        cs.newLineAtOffset(10f, 7f);
        cs.showText(leftTxt);
        cs.endText();

        float rW = PDType1Font.HELVETICA.getStringWidth(rightTxt) / 1000f * 7f;
        cs.beginText();
        setNonStrokingColor(cs, C_WHITE);
        cs.setFont(PDType1Font.HELVETICA, 7f);
        cs.newLineAtOffset(pageW - rW - 10f, 7f);
        cs.showText(rightTxt);
        cs.endText();
    }

    // ==========================================================================
    //  PRIVATE helpers for summary section title (avoids forward-ref issue)
    // ==========================================================================

    private static void y_drawSectionTitle(PDPageContentStream cs,
                                            float x, float yTop, float w,
                                            float h, String title) throws IOException {
        fillRect(cs, C_CARD_BG, x, yTop - h, w, h);
        fillRect(cs, C_NAVY, x, yTop - h, 4f, h);
        cs.beginText();
        setNonStrokingColor(cs, C_NAVY);
        cs.setFont(PDType1Font.HELVETICA_BOLD, 9.5f);
        cs.newLineAtOffset(x + 12f, yTop - h + 7f);
        cs.showText(title);
        cs.endText();
    }

    // ==========================================================================
    //  DRAWING PRIMITIVES
    // ==========================================================================

    /** Filled circle via cubic bezier approximation */
    private static void fillCircle(PDPageContentStream cs, float[] rgb,
                                    float cx, float cy, float r) throws IOException {
        final float k = r * 0.5522847498f;
        setNonStrokingColor(cs, rgb);
        cs.moveTo(cx - r, cy);
        cs.curveTo(cx - r, cy + k, cx - k, cy + r, cx,     cy + r);
        cs.curveTo(cx + k, cy + r, cx + r, cy + k, cx + r, cy);
        cs.curveTo(cx + r, cy - k, cx + k, cy - r, cx,     cy - r);
        cs.curveTo(cx - k, cy - r, cx - r, cy - k, cx - r, cy);
        cs.closePath();
        cs.fill();
    }

    /** Pie slice from center, going clockwise in PDF coords */
    private static void fillPieSlice(PDPageContentStream cs, float[] rgb,
                                      float cx, float cy, float r,
                                      double startAngle, double endAngle) throws IOException {
        setNonStrokingColor(cs, rgb);
        cs.moveTo(cx, cy);
        int steps = 40;
        double delta = (endAngle - startAngle) / steps;
        for (int i = 0; i <= steps; i++) {
            double a = startAngle + delta * i;
            cs.lineTo(cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a));
        }
        cs.closePath();
        cs.fill();
    }

    /** Donut chart: colored pie slices + white inner hole + percentage text */
    private static void drawDonut(PDPageContentStream cs,
                                   float cx, float cy, float outerR, float innerR,
                                   int total, int passed, int failed, int skipped)
            throws IOException {
        // Background ring
        fillCircle(cs, C_RING_BG, cx, cy, outerR);

        if (total > 0) {
            double start = Math.PI / 2.0; // top
            int[] vals   = {passed, failed, skipped};
            float[][] cls= {C_GREEN, C_RED, C_YELLOW};
            for (int i = 0; i < 3; i++) {
                if (vals[i] > 0) {
                    double end = start - vals[i] * 2.0 * Math.PI / total;
                    fillPieSlice(cs, cls[i], cx, cy, outerR, start, end);
                    start = end;
                }
            }
        }

        // White hole (donut effect)
        fillCircle(cs, C_WHITE, cx, cy, innerR);

        // Percentage in center
        String pct = total > 0 ? String.format("%.0f%%", passed * 100.0 / total) : "0%";
        float pFs = 10f;
        float pW  = PDType1Font.HELVETICA_BOLD.getStringWidth(pct) / 1000f * pFs;
        cs.beginText();
        setNonStrokingColor(cs, C_TEXT_D);
        cs.setFont(PDType1Font.HELVETICA_BOLD, pFs);
        cs.newLineAtOffset(cx - pW / 2f, cy - 3.5f);
        cs.showText(pct);
        cs.endText();
    }

    /** Minimal icon inside metric card circle */
    private static void drawCardIcon(PDPageContentStream cs, float[] color,
                                      float cx, float cy, int type) throws IOException {
        setStrokingColor(cs, color);
        cs.setLineWidth(1.5f);
        switch (type) {
            case 0: // grid lines (Total)
                cs.moveTo(cx - 5f, cy + 3f); cs.lineTo(cx + 5f, cy + 3f); cs.stroke();
                cs.moveTo(cx - 5f, cy);       cs.lineTo(cx + 5f, cy);       cs.stroke();
                cs.moveTo(cx - 5f, cy - 3f); cs.lineTo(cx + 5f, cy - 3f); cs.stroke();
                break;
            case 1: // checkmark (Pasadas)
                cs.moveTo(cx - 4f, cy);
                cs.lineTo(cx - 1f, cy - 3.5f);
                cs.lineTo(cx + 5f, cy + 4f);
                cs.stroke();
                break;
            case 2: // X (Falladas)
                cs.moveTo(cx - 4f, cy + 4f); cs.lineTo(cx + 4f, cy - 4f); cs.stroke();
                cs.moveTo(cx + 4f, cy + 4f); cs.lineTo(cx - 4f, cy - 4f); cs.stroke();
                break;
            case 3: // minus (Omitidas)
                cs.moveTo(cx - 5f, cy); cs.lineTo(cx + 5f, cy); cs.stroke();
                break;
            default:
                break;
        }
        cs.setLineWidth(1f);
    }

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

    // FIX real (causa raíz de pérdida de acentos/Unicode en nombres de archivo de reportes):
    // usada solo para el nombre del PDF en disco (el contenido dibujado dentro del PDF usa
    // testName sin sanitizar — no se toca aquí). Blacklist de caracteres realmente inválidos
    // en un nombre de archivo, en vez de la whitelist ASCII anterior que reemplazaba
    // cualquier acento/ñ por "_".
    private static final java.util.regex.Pattern FILENAME_UNSAFE_CHARS =
            java.util.regex.Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");

    private static String sanitize(String t) {
        if (t == null) return "";
        return FILENAME_UNSAFE_CHARS.matcher(t).replaceAll("_");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
