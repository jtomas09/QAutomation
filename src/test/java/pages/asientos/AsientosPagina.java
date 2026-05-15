package pages.asientos;

import io.appium.java_client.android.AndroidDriver;

/**
 * Página de interacción con la pantalla de selección de asientos.
 *
 * Nombre canónico en español: AsientosPagina.
 * La implementación vive aquí; SelectorPage queda como alias
 * de compatibilidad hasta que se elimine.
 *
 * Responsabilidad de esta capa:
 *  - Interactuar con el mapa de asientos, horarios y filtros.
 *  - NO orquestar flujos completos de compra.
 *  - La lógica de "qué hacer y cuándo" pertenece a {@link flujos.AsientosFlujo}.
 */
public class AsientosPagina extends SelectorPage {

    public AsientosPagina(AndroidDriver driver) {
        super(driver);
    }
}
