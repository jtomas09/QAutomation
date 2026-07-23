package qa.cinepolis.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

/**
 * Checks whether an iOS device screen is currently locked using
 * {@code xcrun devicectl}.
 *
 * Called at three points in the execution lifecycle:
 *  1. IosPreflightManager.runPreflight() — initial check before any preflight step.
 *  2. IosPreflightManager.runPreflight() — stability check after all preflight steps.
 *  3. JobExecutor — immediately before launching the Gradle subprocess.
 * (Un 4to punto, IOSPreSessionRevalidator, consulta esto vía HTTP — ver
 * DeviceStreamServer.GET /api/device/unlock-status.)
 *
 * The Framework side tracks elapsed time since the last confirmed unlock
 * (iosState.confirmedUnlockedAtMs) and warns when the gap exceeds 5 seconds.
 *
 * ── Causa raíz confirmada con evidencia real (Xcode 26, devicectl 518.31) ──
 * {@code xcrun devicectl device info details} dejó de incluir los campos que este
 * checker buscaba (screenLocked / isPasscodeLocked / screenViewingRequiresPasscode)
 * en deviceProperties — verificado ejecutando el comando real contra un dispositivo
 * físico: el JSON de "details" en Xcode 26.5 no contiene NINGUNO de esos 3 campos.
 * Apple movió esta información a un subcomando dedicado: "devicectl device info
 * lockState", que expone {@code passcodeRequired} (locked=true ahora mismo) y
 * {@code unlockedSinceBoot} (irrelevante para estado en vivo — es un latch de una
 * sola vez desde el arranque, confirmado empíricamente: se queda en true incluso
 * después de bloquear el dispositivo). Sin este cambio, queryDevicectl() SIEMPRE
 * devolvía null en Xcode 26+ y check() SIEMPRE caía al fallback optimista
 * (unlocked=true) — es decir, ninguno de los 3 gates de unlock del pipeline podía
 * detectar un bloqueo real, sin importar CUÁNDO se consultaran.
 *
 * Se intenta primero "lockState" (autoridad correcta en Xcode 26+); si falla o no
 * responde (Xcode más antiguo, o el subcomando no existe en esa versión), cae al
 * parseo de "details" ya existente — sin remover compatibilidad con instalaciones
 * más viejas de Xcode.
 *
 * Never throws — returns optimistic (unlocked=true) when both queries fail or no
 * lock-state field is found. This prevents a missing field from blocking
 * executions on setups where the device is always unlocked.
 */
public final class DeviceScreenLockChecker {

    private static final ObjectMapper MAPPER      = new ObjectMapper();
    private static final int          TIMEOUT_SEC = 8;

    private DeviceScreenLockChecker() {}

    // ── Result ────────────────────────────────────────────────────────────────

    public static final class LockState {
        /** True when confirmed unlocked — or state is unknown (optimistic). */
        public final boolean unlocked;
        /** How the state was determined: "devicectl", "optimistic", "optimistic-no-udid". */
        public final String  method;
        /** System.currentTimeMillis() when this check ran. */
        public final long    checkedAtMs;

        LockState(boolean unlocked, String method) {
            this.unlocked    = unlocked;
            this.method      = method;
            this.checkedAtMs = System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return (unlocked ? "UNLOCKED ✅" : "LOCKED ❌") + " (via " + method + ")";
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the current lock state for the given physical UDID.
     *
     * Queries {@code xcrun devicectl device info details --device <udid> --json-output -}
     * and parses {@code deviceProperties} for known lock-state field names, which vary
     * across Xcode versions:
     * <ul>
     *   <li>{@code screenLocked}</li>
     *   <li>{@code isPasscodeLocked}</li>
     *   <li>{@code screenViewingRequiresPasscode}</li>
     * </ul>
     *
     * Returns {@code unlocked=true, method="optimistic"} when:
     * <ul>
     *   <li>UDID is blank.</li>
     *   <li>The command times out or fails.</li>
     *   <li>None of the known lock-state fields is present in the JSON.</li>
     * </ul>
     */
    public static LockState check(String udid) {
        if (udid == null || udid.isBlank()) return new LockState(true, "optimistic-no-udid");

        Boolean locked = queryLockState(udid);
        if (locked != null) return new LockState(!locked, "devicectl-lockstate");

        locked = queryDevicectlDetails(udid);
        if (locked != null) return new LockState(!locked, "devicectl-details");

        return new LockState(true, "optimistic");
    }

    // ── Private implementation ────────────────────────────────────────────────

    /**
     * Autoridad correcta en Xcode 26+ — ver Javadoc de clase. {@code passcodeRequired}
     * es el único de los dos campos que refleja el estado EN VIVO (confirmado
     * bloqueando/desbloqueando un dispositivo real y observando el cambio);
     * {@code unlockedSinceBoot} se ignora deliberadamente.
     */
    private static Boolean queryLockState(String udid) {
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "device", "info", "lockState",
                    "--device", udid, "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return null; }

            JsonNode result = MAPPER.readTree(json).path("result");
            JsonNode passcodeRequired = result.path("passcodeRequired");
            if (passcodeRequired.isBoolean()) return passcodeRequired.asBoolean();
        } catch (Exception ignored) {}
        return null;
    }

    /** Parseo original — conservado para compatibilidad con Xcode &lt; 26. */
    private static Boolean queryDevicectlDetails(String udid) {
        try {
            Process p = new ProcessBuilder(
                    "xcrun", "devicectl", "device", "info", "details",
                    "--device", udid, "--json-output", "-")
                    .redirectErrorStream(false).start();
            String json = new String(p.getInputStream().readAllBytes());
            boolean done = p.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!done) { p.destroyForcibly(); return null; }

            JsonNode root = MAPPER.readTree(json);

            // Xcode 15–25: result.device (singular object)
            JsonNode deviceNode = root.path("result").path("device");
            // Xcode 26+: may use result.devices (array) even for single-device details
            if (deviceNode.isMissingNode() || deviceNode.isNull()) {
                JsonNode arr = root.path("result").path("devices");
                if (arr.isArray() && !arr.isEmpty()) deviceNode = arr.get(0);
            }
            if (deviceNode == null || deviceNode.isMissingNode() || deviceNode.isNull()) return null;

            JsonNode devProps = deviceNode.path("deviceProperties");
            if (devProps.isMissingNode() || devProps.isNull()) return null;

            for (String field : new String[]{
                    "screenLocked", "isPasscodeLocked", "screenViewingRequiresPasscode"}) {
                JsonNode n = devProps.path(field);
                if (n.isBoolean()) return n.asBoolean();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
