package utils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import utils.BaseTestStatusRegistry;

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

public class PdfReportGenerator {

    private static final Path REPORT_DIR = Paths.get("build", "reportes-pdf");

    // Ruta del logo
    private static final String LOGO_PATH = "src/test/resources/Cinepolis.png";

    // Datos de encabezado
    private static final String REPORT_TITLE = "Reporte de Prueba Automatizada Alimentos";
    public static final String EXECUTOR = "Jairo Tomás Baza";
    public static final String PROJECT = "Cinépolis Alimentos";

    // Colores claros
    private static final float[] COLOR_BG_LIGHT = {1f, 1f, 1f};
    private static final float[] COLOR_HEADER_BLUE = {0.0f, 0.23f, 0.44f};
    private static final float[] COLOR_BORDER = {0.75f, 0.75f, 0.75f};
    private static final float[] COLOR_TEXT_BLACK = {0f, 0f, 0f};
    private static final float[] COLOR_TEXT_WHITE = {1f, 1f, 1f};
    private static final float[] COLOR_OK = {0.0f, 0.6f, 0.0f};
    private static final float[] COLOR_FAIL = {0.8f, 0.1f, 0.1f};

    public static void generate(String testName, List<StepResult> steps) {
        if (steps == null || steps.isEmpty()) {
            System.out.println("[PDF] No hay pasos para el test: " + testName);
            return;
        }

        try (PDDocument doc = new PDDocument()) {
            Files.createDirectories(REPORT_DIR);

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDRectangle mediaBox = page.getMediaBox();
            float pageWidth = mediaBox.getWidth();
            float pageHeight = mediaBox.getHeight();

            float margin = 36;
            float yStart = pageHeight - margin;

            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {

                // Fondo claro
                setNonStrokingColor(content, COLOR_BG_LIGHT);
                content.addRect(0, 0, pageWidth, pageHeight);
                content.fill();

                float yAfterHeader = drawHeader(content, doc, pageWidth, yStart);

// ======= RESUMEN DE TESTS =======
                float yAfterSummary = drawTestSummaryBox(content, margin, yAfterHeader - 10);

// ======= Nombre del test =======
                content.beginText();
                setNonStrokingColor(content, COLOR_TEXT_BLACK);
                content.setFont(PDType1Font.HELVETICA_BOLD, 13);
                content.newLineAtOffset(margin, yAfterSummary - 18);
                content.showText("Test: " + testName);
                content.endText();

                float y = yAfterSummary - 40;


                // ========= TABLA DE PASOS =========
                float tableWidth = pageWidth - 2 * margin;
                float colPasoWidth = tableWidth * 0.25f;
                float colResultadoWidth = tableWidth * 0.12f;
                float colEvidenciaWidth = tableWidth - colPasoWidth - colResultadoWidth;

                float rowHeightMin = 140;
                float headerHeight = 26;
                float padding = 6;

                // Header tabla
                drawTableHeaderRow(content, margin, y, tableWidth, headerHeight,
                        colPasoWidth, colResultadoWidth, colEvidenciaWidth);

                y -= headerHeight;

                // Filas
                for (StepResult step : steps) {
                    float rowHeight = rowHeightMin;

                    if (y - rowHeight < margin + 80) { // dejar espacio para conclusiones
                        break;
                    }

                    // Fondo fila
                    setNonStrokingColor(content, COLOR_BG_LIGHT);
                    content.addRect(margin, y - rowHeight, tableWidth, rowHeight);
                    content.fill();

                    // Bordes
                    setStrokingColor(content, COLOR_BORDER);
                    content.addRect(margin, y - rowHeight, tableWidth, rowHeight);
                    content.stroke();

                    float xPaso = margin;
                    float xResultado = margin + colPasoWidth;
                    float xEvidencia = margin + colPasoWidth + colResultadoWidth;

                    // Paso
                    content.beginText();
                    setNonStrokingColor(content, COLOR_TEXT_BLACK);
                    content.setFont(PDType1Font.HELVETICA, 11);
                    content.newLineAtOffset(xPaso + padding, y - 18);
                    content.showText(step.getStepName());
                    content.endText();

                    // Resultado (OK / ERROR)
                    content.beginText();
                    if (step.getStatus().equalsIgnoreCase("OK")) {
                        setNonStrokingColor(content, COLOR_OK);
                    } else {
                        setNonStrokingColor(content, COLOR_FAIL);
                    }
                    content.setFont(PDType1Font.HELVETICA_BOLD, 12);
                    content.newLineAtOffset(xResultado + padding, y - 18);
                    content.showText(step.getStatus());
                    content.endText();

                    // Evidencia (imagen)
                    if (step.getScreenshotPath() != null &&
                            Files.exists(Paths.get(step.getScreenshotPath()))) {

                        PDImageXObject image = PDImageXObject.createFromFile(step.getScreenshotPath(), doc);

                        float availableW = colEvidenciaWidth - 2 * padding;
                        float availableH = rowHeight - 2 * padding;

                        float scale = Math.min(availableW / image.getWidth(), availableH / image.getHeight());

                        float imgW = image.getWidth() * scale;
                        float imgH = image.getHeight() * scale;

                        float imgX = xEvidencia + (availableW - imgW) / 2;
                        float imgY = y - padding - imgH;

                        content.drawImage(image, imgX, imgY, imgW, imgH);
                    }

                    y -= rowHeight;
                }

                // ========= CONCLUSIONES DE LA EJECUCIÓN =========
                drawConclusions(content, margin, y - 20, steps);
            }

            Path pdfFile = REPORT_DIR.resolve(sanitize(testName) + ".pdf");
            doc.save(pdfFile.toFile());
            System.out.println("[PDF] Generado en modo CLARO → " + pdfFile.toAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= ENCABEZADO (logo + título + metadatos) =================

    private static float drawHeader(PDPageContentStream content,
                                    PDDocument doc,
                                    float pageWidth,
                                    float yTop) throws IOException {

        float leftMargin = 36;

        // Logo
        float logoWidth = 80;
        float logoHeight = 30;
        float logoY = yTop - logoHeight;

        if (Files.exists(Paths.get(LOGO_PATH))) {
            PDImageXObject logo = PDImageXObject.createFromFile(LOGO_PATH, doc);
            float scale = Math.min(logoWidth / logo.getWidth(), logoHeight / logo.getHeight());
            float lw = logo.getWidth() * scale;
            float lh = logo.getHeight() * scale;
            content.drawImage(logo, leftMargin, logoY, lw, lh);
        }

        // Título general
        String title = REPORT_TITLE;
        content.setFont(PDType1Font.HELVETICA_BOLD, 18);
        float titleWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(title) / 1000 * 18;
        float titleX = (pageWidth - titleWidth) / 2;
        float titleY = yTop - 10;

        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_BLACK);
        content.newLineAtOffset(titleX, titleY);
        content.showText(title);
        content.endText();

        // Metadatos
        LocalDateTime now = LocalDateTime.now();
        String fecha = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

        float metaY = titleY - 22;
        content.setFont(PDType1Font.HELVETICA, 11);

        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_BLACK);
        content.newLineAtOffset(leftMargin + 20, metaY);
        content.showText("Generado el: " + fecha);
        content.endText();

        metaY -= 14;
        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_BLACK);
        content.newLineAtOffset(leftMargin + 20, metaY);
        content.showText("Ejecutor: " + EXECUTOR);
        content.endText();

        metaY -= 14;
        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_BLACK);
        content.newLineAtOffset(leftMargin + 20, metaY);
        content.showText("Proyecto: " + PROJECT);
        content.endText();

        return metaY;
    }
    public static void createPdfFromImage(File imageFile, File outPdf) {
        if (imageFile == null || !imageFile.exists()) {
            throw new RuntimeException("No existe la imagen para PDF: " + imageFile);
        }

        try (PDDocument doc = new PDDocument()) {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) {
                throw new RuntimeException("No se pudo leer la imagen (ImageIO devolvió null): " + imageFile);
            }

            float imgW = img.getWidth();
            float imgH = img.getHeight();

            // Página del tamaño exacto de la imagen (para que no se recorte)
            PDPage page = new PDPage(new PDRectangle(imgW, imgH));
            doc.addPage(page);

            var pdImage = LosslessFactory.createFromImage(doc, img);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(pdImage, 0, 0, imgW, imgH);
            }

            // Crea carpeta destino si no existe
            File parent = outPdf.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            doc.save(outPdf);
        } catch (Exception e) {
            throw new RuntimeException("Error creando PDF desde imagen: " + imageFile + " -> " + outPdf, e);
        }
    }
    // ================= HEADER DE TABLA =================
    private static float drawTestSummaryBox(PDPageContentStream content, float x, float yTop) throws IOException {

        int total = BaseTestStatusRegistry.getTotal();
        int passed = BaseTestStatusRegistry.getPassed();
        int failed = BaseTestStatusRegistry.getFailed();

        float boxW = 220;
        float headerH = 20;
        float rowH = 18;
        float boxH = headerH + rowH * 3;

        // Borde
        setStrokingColor(content, COLOR_BORDER);
        content.addRect(x, yTop - boxH, boxW, boxH);
        content.stroke();

        // Header
        setNonStrokingColor(content, COLOR_HEADER_BLUE);
        content.addRect(x, yTop - headerH, boxW, headerH);
        content.fill();

        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_WHITE);
        content.setFont(PDType1Font.HELVETICA_BOLD, 11);
        content.newLineAtOffset(x + 8, yTop - 14);
        content.showText("Resumen de Ejecución");
        content.endText();

        float y = yTop - headerH - 12;

        drawSummaryRow(content, x, y, boxW, "Total", String.valueOf(total), COLOR_TEXT_BLACK);
        y -= rowH;

        drawSummaryRow(content, x, y, boxW, "Pasados", String.valueOf(passed), COLOR_OK);
        y -= rowH;

        drawSummaryRow(content, x, y, boxW, "Fallados", String.valueOf(failed), COLOR_FAIL);

        return yTop - boxH;
    }

    private static void drawSummaryRow(PDPageContentStream content,
                                       float x, float yText,
                                       float boxW,
                                       String label, String value,
                                       float[] valueColor) throws IOException {

        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_BLACK);
        content.setFont(PDType1Font.HELVETICA, 11);
        content.newLineAtOffset(x + 8, yText);
        content.showText(label);
        content.endText();

        content.beginText();
        setNonStrokingColor(content, valueColor);
        content.setFont(PDType1Font.HELVETICA_BOLD, 11);

        float valueWidth =
                PDType1Font.HELVETICA_BOLD.getStringWidth(value) / 1000 * 11;

        content.newLineAtOffset(x + boxW - 10 - valueWidth, yText);
        content.showText(value);
        content.endText();
    }

    private static void drawTableHeaderRow(PDPageContentStream content,
                                           float x, float y,
                                           float width, float height,
                                           float colPasoWidth, float colResultadoWidth, float colEvidenciaWidth)
            throws IOException {

        // Fondo azul
        setNonStrokingColor(content, COLOR_HEADER_BLUE);
        content.addRect(x, y - height, width, height);
        content.fill();

        // Bordes
        setStrokingColor(content, COLOR_BORDER);
        content.addRect(x, y - height, width, height);
        content.stroke();

        float textY = y - 17;
        content.setFont(PDType1Font.HELVETICA_BOLD, 11);

        // Paso
        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_WHITE);
        content.newLineAtOffset(x + 8, textY);
        content.showText("Paso");
        content.endText();

        // Resultado
        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_WHITE);
        content.newLineAtOffset(x + colPasoWidth + 8, textY);
        content.showText("Resultado");
        content.endText();

        // Evidencia
        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_WHITE);
        content.newLineAtOffset(x + colPasoWidth + colResultadoWidth + 8, textY);
        content.showText("Evidencia");
        content.endText();
    }

    // ================= CONCLUSIONES =================

    private static void drawConclusions(PDPageContentStream content,
                                        float x, float y,
                                        List<StepResult> steps) throws IOException {

        int total = steps.size();
        int ok = (int) steps.stream()
                .filter(s -> "OK".equalsIgnoreCase(s.getStatus()))
                .count();
        int fail = total - ok;

        content.setFont(PDType1Font.HELVETICA_BOLD, 12);
        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_BLACK);
        content.newLineAtOffset(x, y);
        content.showText("Conclusiones de la ejecución:");
        content.endText();

        float lineY = y - 16;
        content.setFont(PDType1Font.HELVETICA, 11);

        // Total de pasos
        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_BLACK);
        content.newLineAtOffset(x, lineY);
        content.showText("Total de pasos: " + total);
        content.endText();

        // Pasos exitosos
        lineY -= 14;
        content.beginText();
        setNonStrokingColor(content, COLOR_OK);
        content.newLineAtOffset(x, lineY);
        content.showText("Pasos exitosos: " + ok);
        content.endText();

        // Pasos fallidos
        lineY -= 14;
        content.beginText();
        setNonStrokingColor(content, COLOR_FAIL);
        content.newLineAtOffset(x, lineY);
        content.showText("Pasos fallidos: " + fail);
        content.endText();

        // Mensaje final (sin emojis para evitar el error de fuente)
        lineY -= 18;

        content.beginText();
        setNonStrokingColor(content, COLOR_TEXT_BLACK);
        content.newLineAtOffset(x, lineY);
        if (fail > 0) {
            content.showText("Se encontraron errores. Revisar los pasos fallidos.");
        } else {
            content.showText("No se encontraron errores en la ejecución.");
        }
        content.endText();
    }

    // ================= UTILS =================

    private static void setNonStrokingColor(PDPageContentStream c, float[] rgb) throws IOException {
        c.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
    }

    private static void setStrokingColor(PDPageContentStream c, float[] rgb) throws IOException {
        c.setStrokingColor(rgb[0], rgb[1], rgb[2]);
    }

    private static String sanitize(String t) {
        return t.replaceAll("[^a-zA-Z0-9-_]", "_");
    }
}
