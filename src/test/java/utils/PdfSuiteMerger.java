package utils;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;

import java.nio.file.*;
import java.util.stream.Stream;

public class PdfSuiteMerger {

    /**
     * Une SOLO PDFs de evidencia por test (Reporte_*.pdf) del directorio reportsDir
     * en un solo archivo outputFileName.
     *
     * Ej: mergeReports("build/reportes-pdf", "Reporte_Pruebas_MenuVIP.pdf");
     */
    public static void mergeReports(String reportsDir, String outputFileName) {
        try {
            Path dir = Paths.get(reportsDir);
            if (!Files.exists(dir)) {
                System.out.println("[PDF-MERGE] Directorio no existe: " + dir.toAbsolutePath());
                return;
            }

            PDFMergerUtility merger = new PDFMergerUtility();
            Path destino = dir.resolve(outputFileName);
            merger.setDestinationFileName(destino.toString());

            int added = 0;

            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    String name = p.getFileName().toString();

                    // ✅ Solo evidencia por test
                    if (!name.startsWith("Reporte_")) continue;
                    if (!name.toLowerCase().endsWith(".pdf")) continue;

                    // ✅ Evitar auto-incluir el destino
                    if (name.equals(outputFileName)) continue;

                    // ✅ Evitar PDFs de Allure si algún día cayeran aquí
                    if (name.startsWith("Allure_")) continue;

                    try {
                        System.out.println("[PDF-MERGE] Agregando: " + name);
                        merger.addSource(p.toFile());
                        added++;
                    } catch (Exception e) {
                        System.err.println("[PDF-MERGE] No se pudo agregar: " + p + " -> " + e.getMessage());
                    }
                }
            }

            if (added == 0) {
                System.out.println("[PDF-MERGE] No hay PDFs (Reporte_*.pdf) para combinar en: " + dir.toAbsolutePath());
                return;
            }

            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
            System.out.println("[PDF-MERGE] PDF combinado generado en: " + destino.toAbsolutePath());

        } catch (Exception e) {
            System.err.println("[PDF-MERGE] Error al combinar PDFs: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
