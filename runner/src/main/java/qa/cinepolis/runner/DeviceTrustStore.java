package qa.cinepolis.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persiste, por dispositivo (UDID), si ya confía en la CA de Network Monitoring —
 * para no volver a pedir la configuración manual una vez hecha una vez.
 *
 * Archivo: {AGENT_DATA_DIR}/network-monitoring/device-trust.json
 *
 * Formato deliberadamente simple (parser/escritor JSON manual, sin depender de
 * Jackson en el módulo runner — mismo criterio que BackendClient.RunnerConfigResponse,
 * que también parsea JSON "a mano" en este módulo):
 * {
 *   "caFingerprintSha256": "AA:BB:...",
 *   "devices": {
 *     "<udid>": { "status": "TRUSTED", "verifiedAt": "2026-09-11T10:15:00Z" }
 *   }
 * }
 *
 * Nunca se guarda ningún secreto — solo el fingerprint PÚBLICO de la CA (no la
 * clave privada) y un estado lógico. Si el fingerprint activo no coincide con el
 * guardado (CA regenerada/rotada), TODOS los dispositivos vuelven a NOT_CONFIGURED
 * automáticamente la próxima vez que se consulten — ver {@link #isTrusted}.
 */
public final class DeviceTrustStore {

    public enum Status { TRUSTED, NOT_CONFIGURED }

    public record DeviceState(Status status, String verifiedAt) {}

    private final Path storeFile;
    private final Map<String, DeviceState> devices = new ConcurrentHashMap<>();
    private volatile String loadedCaFingerprint = null;

    public DeviceTrustStore(Path agentDataDir) {
        this.storeFile = agentDataDir.resolve("network-monitoring").resolve("device-trust.json");
        load();
    }

    /**
     * True solo si el dispositivo fue marcado TRUSTED anteriormente Y el
     * fingerprint de CA activo coincide con el que estaba vigente en ese
     * momento. Si la CA cambió, esto invalida implícitamente el estado guardado
     * (no borra el archivo — simplemente deja de considerarlo válido) hasta que
     * {@link #markTrusted} lo reconfirme contra la CA nueva.
     */
    public synchronized boolean isTrusted(String udid, String activeCaFingerprintSha256) {
        if (udid == null || udid.isBlank()) return false;
        if (loadedCaFingerprint == null || !loadedCaFingerprint.equalsIgnoreCase(activeCaFingerprintSha256)) {
            return false; // CA rotada (o primer arranque) — nunca se confía en el estado viejo
        }
        DeviceState state = devices.get(udid);
        return state != null && state.status() == Status.TRUSTED;
    }

    public synchronized void markTrusted(String udid, String activeCaFingerprintSha256) {
        if (udid == null || udid.isBlank()) return;
        this.loadedCaFingerprint = activeCaFingerprintSha256;
        devices.put(udid, new DeviceState(Status.TRUSTED, Instant.now().toString()));
        save();
    }

    public synchronized void markNotConfigured(String udid) {
        if (udid == null || udid.isBlank()) return;
        devices.put(udid, new DeviceState(Status.NOT_CONFIGURED, null));
        save();
    }

    // ── Persistencia (parser/escritor JSON manual, sin dependencias nuevas) ────

    private static final Pattern FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");

    private synchronized void load() {
        try {
            if (!Files.exists(storeFile)) return;
            String raw = Files.readString(storeFile, StandardCharsets.UTF_8);

            Matcher fpMatcher = Pattern.compile("\"caFingerprintSha256\"\\s*:\\s*\"([^\"]*)\"").matcher(raw);
            if (fpMatcher.find()) loadedCaFingerprint = fpMatcher.group(1);

            int devicesIdx = raw.indexOf("\"devices\"");
            if (devicesIdx < 0) return;
            // Arranca DESPUÉS de la llave de apertura de "devices": { — así el propio
            // texto "devices" nunca entra en el rango que escanea entryPattern (bug
            // real encontrado por DeviceTrustStoreTest.persistsAcrossInstances: sin
            // este corte, "devices": {...} coincidía como si fuera una entrada de
            // dispositivo, consumiendo la primera entrada real dentro de su match).
            int devicesOpenBrace = raw.indexOf('{', devicesIdx);
            if (devicesOpenBrace < 0) return;
            String devicesBlock = raw.substring(devicesOpenBrace + 1);

            // Cada entrada: "<udid>": { "status": "...", "verifiedAt": "..." }
            Pattern entryPattern = Pattern.compile(
                    "\"([^\"]+)\"\\s*:\\s*\\{([^}]*)}");
            Matcher entries = entryPattern.matcher(devicesBlock);
            while (entries.find()) {
                String udid = entries.group(1);
                String body = entries.group(2);
                String status = null, verifiedAt = null;
                Matcher fields = FIELD.matcher(body);
                while (fields.find()) {
                    if ("status".equals(fields.group(1))) status = fields.group(2);
                    if ("verifiedAt".equals(fields.group(1))) verifiedAt = fields.group(2);
                }
                if (status != null) {
                    try {
                        devices.put(udid, new DeviceState(Status.valueOf(status), verifiedAt));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception e) {
            System.out.println("[NetworkMonitoring][DeviceTrustStore] No se pudo leer " + storeFile
                    + " (" + e.getMessage() + ") — se asume sin dispositivos confiados todavía.");
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(storeFile.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"caFingerprintSha256\": \"").append(esc(loadedCaFingerprint)).append("\",\n");
            sb.append("  \"devices\": {\n");
            int i = 0;
            for (Map.Entry<String, DeviceState> e : new LinkedHashMap<>(devices).entrySet()) {
                sb.append("    \"").append(esc(e.getKey())).append("\": { ");
                sb.append("\"status\": \"").append(e.getValue().status()).append("\"");
                if (e.getValue().verifiedAt() != null) {
                    sb.append(", \"verifiedAt\": \"").append(esc(e.getValue().verifiedAt())).append("\"");
                }
                sb.append(" }");
                i++;
                if (i < devices.size()) sb.append(",");
                sb.append("\n");
            }
            sb.append("  }\n}\n");
            Files.writeString(storeFile, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.out.println("[NetworkMonitoring][DeviceTrustStore] No se pudo escribir " + storeFile
                    + " (" + e.getMessage() + ") — el estado de confianza no persistirá esta vez.");
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
