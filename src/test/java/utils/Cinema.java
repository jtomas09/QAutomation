package utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the cinema under test for a test method.
 * Read by PdfReportExtension.beforeEach() BEFORE @BeforeEach runs, so the cinema
 * name is captured in the report even when setup steps fail before
 * CinemasHelper.ensureCinemaSelectedFromAlimentos() is called.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cinema {
    String value();
}
