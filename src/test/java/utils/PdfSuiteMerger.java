package utils;

import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.stream.Stream;

public class PdfSuiteMerger {

    private static final Logger log = LoggerFactory.getLogger(PdfSuiteMerger.class);

    /**
     * Merges all per-test PDF evidence files ({@code Reporte_*.pdf}) found in {@code reportsDir}
     * into a single combined file named {@code outputFileName}.
     *
     * @param reportsDir     directory that contains the per-test PDFs
     * @param outputFileName name of the merged output file (placed in the same directory)
     */
    public static void mergeReports(String reportsDir, String outputFileName) {
        try {
            Path dir = Paths.get(reportsDir);
            if (!Files.exists(dir)) {
                log.info("[PdfSuiteMerger] Report directory does not exist: {}", dir.toAbsolutePath());
                return;
            }

            PDFMergerUtility merger = new PDFMergerUtility();
            Path destino = dir.resolve(outputFileName);
            merger.setDestinationFileName(destino.toString());

            int added = 0;

            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    String name = p.getFileName().toString();

                    if (!name.startsWith("Reporte_")) continue;
                    if (!name.toLowerCase().endsWith(".pdf")) continue;
                    if (name.equals(outputFileName)) continue;
                    if (name.startsWith("Allure_")) continue;

                    try {
                        log.debug("[PdfSuiteMerger] Adding: {}", name);
                        merger.addSource(p.toFile());
                        added++;
                    } catch (Exception e) {
                        log.warn("[PdfSuiteMerger] Could not add PDF {}: {}", p, e.getMessage());
                    }
                }
            }

            if (added == 0) {
                log.info("[PdfSuiteMerger] No Reporte_*.pdf files found to merge in: {}", dir.toAbsolutePath());
                return;
            }

            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly());
            log.info("[PdfSuiteMerger] Merged PDF written to: {}", destino.toAbsolutePath());

        } catch (Exception e) {
            log.error("[PdfSuiteMerger] Failed to merge PDFs: {}", e.getMessage(), e);
        }
    }
}
