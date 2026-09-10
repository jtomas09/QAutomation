package pages.ios;

import io.appium.java_client.AppiumDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pages.common.BasePage;

/**
 * Base común de la familia de Page Objects exclusivos de iOS (TAREA 30).
 *
 * Contexto: {@link pages.common.CinemasHelper} y {@link pages.asientos.SelectorPage}
 * son clases COMPARTIDAS por Android e iOS (usan {@code isIOS()} internamente para
 * bifurcar comportamiento). TAREA 29 encontró que esa mezcla concentra la mayoría del
 * costo de rendimiento de iOS en loops/polling dentro de esas mismas clases.
 *
 * Esta familia {@code pages.ios.*} NO reemplaza esas clases todavía — las envuelve
 * (composición, no copia) para dar a iOS un punto de entrada propio, con nombres y
 * responsabilidades específicas de iOS, sin introducir ni un solo {@code if (isIOS())}
 * adicional dentro de las clases Android/compartidas existentes. Ver TAREA 30 para el
 * mapa completo Android → iOS y la estrategia de migración.
 *
 * Extiende {@link BasePage} (igual que las clases compartidas) únicamente para heredar
 * los mismos helpers genéricos ya usados por ambas plataformas (waits, scroll, sleep) —
 * no se duplica ninguno de ellos aquí.
 */
public abstract class IOSBasePage extends BasePage {

    private static final Logger log = LoggerFactory.getLogger(IOSBasePage.class);

    protected IOSBasePage(AppiumDriver driver) {
        super(driver);
        if (!isIOS()) {
            // Guarda de documentación/diagnóstico — NO bloquea ni lanza: esta familia de
            // clases está pensada exclusivamente para sesiones iOS, pero no se le agrega
            // ningún comportamiento condicional adicional a las clases compartidas por
            // esto, así que un uso incorrecto aquí no puede afectar a Android.
            log.warn("[{}] Instanciada sobre una sesión no-iOS — esta familia de clases "
                    + "es exclusiva de iOS (ver TAREA 30).", getClass().getSimpleName());
        }
    }
}
