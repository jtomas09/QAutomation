package utils;

import org.opentest4j.TestAbortedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Utilidad de reintentos para pasos de prueba intermitentes.
 *
 * Uso básico:
 *   Reintento.intentar(3, () -> page.clickBoton());
 *
 * Con condición de éxito:
 *   Reintento.hasta(3, () -> page.estaVisible(BTN_X));
 *
 * Con pausa entre reintentos:
 *   Reintento.conPausa(3, 500).ejecutar(() -> page.scrollYClick(BTN_X));
 */
public class Reintento {

    private static final Logger log = LoggerFactory.getLogger(Reintento.class);

    private final int maxIntentos;
    private final long pausaMs;
    private final boolean backoffExponencial;

    private Reintento(int maxIntentos, long pausaMs, boolean backoffExponencial) {
        this.maxIntentos = maxIntentos;
        this.pausaMs = pausaMs;
        this.backoffExponencial = backoffExponencial;
    }

    // ─── Builders ────────────────────────────────────────────────────────────

    public static Reintento conPausa(int maxIntentos, long pausaMs) {
        return new Reintento(maxIntentos, pausaMs, false);
    }

    public static Reintento conBackoff(int maxIntentos, long pausaBaseMs) {
        return new Reintento(maxIntentos, pausaBaseMs, true);
    }

    // ─── API estática simplificada ────────────────────────────────────────────

    /**
     * Ejecuta la acción hasta maxIntentos veces; lanza si todos fallan.
     * TestAbortedException (SKIP) se propaga siempre sin reintentos.
     */
    public static void intentar(int maxIntentos, Runnable accion) {
        conPausa(maxIntentos, 300).ejecutar(accion);
    }

    /**
     * Ejecuta la acción hasta que el predicado devuelva true.
     * Devuelve true si tuvo éxito, false si se agotaron los intentos.
     */
    public static boolean hasta(int maxIntentos, BooleanSupplier condicion) {
        return conPausa(maxIntentos, 300).evaluarHasta(condicion);
    }

    /**
     * Ejecuta el supplier y devuelve el resultado; reintenta si lanza excepción.
     */
    public static <T> T obtener(int maxIntentos, Supplier<T> proveedor) {
        return conPausa(maxIntentos, 300).evaluar(proveedor);
    }

    // ─── Instancia ────────────────────────────────────────────────────────────

    public void ejecutar(Runnable accion) {
        Throwable ultimo = null;
        for (int intento = 1; intento <= maxIntentos; intento++) {
            try {
                accion.run();
                return;
            } catch (TestAbortedException e) {
                throw e; // SKIP nunca se reintenta
            } catch (Throwable t) {
                ultimo = t;
                log.warn("[Reintento] Intento {}/{} fallido: {}", intento, maxIntentos, t.getMessage());
                if (intento < maxIntentos) pausar(intento);
            }
        }
        throw new RuntimeException(
            "Acción falló tras " + maxIntentos + " intentos: " + ultimo.getMessage(), ultimo);
    }

    public boolean evaluarHasta(BooleanSupplier condicion) {
        for (int intento = 1; intento <= maxIntentos; intento++) {
            try {
                if (condicion.getAsBoolean()) return true;
            } catch (TestAbortedException e) {
                throw e;
            } catch (Exception e) {
                log.warn("[Reintento] Intento {}/{} con error: {}", intento, maxIntentos, e.getMessage());
            }
            if (intento < maxIntentos) pausar(intento);
        }
        return false;
    }

    public <T> T evaluar(Supplier<T> proveedor) {
        Throwable ultimo = null;
        for (int intento = 1; intento <= maxIntentos; intento++) {
            try {
                return proveedor.get();
            } catch (TestAbortedException e) {
                throw e;
            } catch (Throwable t) {
                ultimo = t;
                log.warn("[Reintento] Intento {}/{} fallido: {}", intento, maxIntentos, t.getMessage());
                if (intento < maxIntentos) pausar(intento);
            }
        }
        throw new RuntimeException(
            "Proveedor falló tras " + maxIntentos + " intentos: " + ultimo.getMessage(), ultimo);
    }

    private void pausar(int intento) {
        long espera = backoffExponencial ? pausaMs * (long) Math.pow(2, intento - 1) : pausaMs;
        try { Thread.sleep(espera); } catch (InterruptedException ignored) {}
    }
}
