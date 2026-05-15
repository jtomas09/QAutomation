package pages.alimentos;

import io.appium.java_client.android.AndroidDriver;

/**
 * Página de interacción con la sección de Alimentos.
 *
 * Nombre canónico en español: AlimentosPagina.
 * La implementación vive aquí; SelectorPage queda como alias
 * de compatibilidad hasta que se elimine.
 *
 * Responsabilidad de esta capa:
 *  - Interactuar con elementos de UI (clicks, scrolls, waits).
 *  - NO tomar decisiones de flujo ni conocer el orden de pasos.
 *  - La lógica de "qué hacer y cuándo" pertenece a {@link flujos.AlimentosFlujo}.
 */
public class AlimentosPagina extends SelectorPage {

    public AlimentosPagina(AndroidDriver driver) {
        super(driver);
    }
}
