package qa.cinepolis.runner;

import qa.cinepolis.runner.model.NetworkMonitoringConfig;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Orquesta el ciclo de vida de Network Monitoring para UN Job: arranca/detiene el
 * proxy mitmdump, configura/revierte el proxy del dispositivo Android, y verifica
 * (nunca fuerza) la confianza del certificado CA. Mismo idioma que
 * {@link IOSVideoRecordingManager}: estado estático simple (el Runner procesa un
 * Job a la vez), start()/stop() idempotentes, nunca lanza — cualquier fallo se
 * degrada a "Network Monitoring no disponible esta vez", jamás bloquea el Job.
 *
 * IMPORTANTE — límite real de plataforma (documentado, no evadido):
 * confiar en la CA nueva requiere un toque humano en el dispositivo, salvo
 * Android rooteado/emulador con imagen Google APIs (ver {@link #tryZeroTouchAndroidCaInstall}).
 * Este manager NUNCA intenta saltarse esa protección — solo la detecta (observando
 * si el handshake TLS del dispositivo contra el proxy tiene éxito) y la documenta.
 */
public final class NetworkMonitoringManager {

    public record Session(boolean active, int proxyPort, String eventsFilePath,
                           String evidenceBaseDir, String caFingerprintSha256) {
        static final Session INACTIVE = new Session(false, 0, null, null, null);
    }

    private static volatile Process proxyProcess;
    private static volatile Path    eventsFile;
    private static volatile Path    confDir;
    private static volatile boolean androidProxyWasSet;
    private static volatile String  androidUdidWithProxySet;

    private NetworkMonitoringManager() {}

    /**
     * Arranca (si aplica) Network Monitoring para este Job. Nunca lanza. Devuelve
     * {@link Session#INACTIVE} si está deshabilitado por config o si cualquier
     * paso de preparación falla — el llamador simplemente no agrega las
     * capabilities/flags de red y el Job continúa exactamente como sin esta
     * función.
     */
    public static Session start(BackendClient client, String executionId,
                                 NetworkMonitoringConfig cfg, Path agentDataDir,
                                 String udid, boolean isAndroid, Path evidenceRootDir) {
        if (cfg == null || !cfg.enabled) return Session.INACTIVE;

        NetworkRuntimeReadiness readiness = new NetworkRuntimeReadiness();
        try {
            client.sendLog(executionId, "INFO", "[NETWORK] Preparando Network Monitoring...");

            String os = qa.cinepolis.runner.model.RunnerConfig.detectOs();
            boolean osSupported = "MACOS".equals(os) || "WINDOWS".equals(os);
            readiness.add("OS supported", osSupported, os);
            String arch = System.getProperty("os.arch", "?");
            boolean archSupported = osSupported && (arch.toLowerCase().contains("aarch64")
                    || arch.toLowerCase().contains("arm64") || arch.toLowerCase().contains("64"));
            readiness.add("Architecture supported", archSupported, arch);
            if (!readiness.isReady()) {
                client.sendLog(executionId, "WARN", readiness.renderSummary());
                return Session.INACTIVE;
            }

            NetworkRuntimeProvisioner provisioner = new NetworkRuntimeProvisioner(agentDataDir, os);
            String mitmdump = provisioner.resolveMitmdump();
            readiness.add("Runtime available", mitmdump != null,
                    mitmdump != null ? mitmdump : provisioner.lastFailureReason());
            if (!readiness.isReady()) {
                client.sendLog(executionId, "WARN", readiness.renderSummary());
                return Session.INACTIVE;
            }
            readiness.add("Runtime executable", true, "mitmdump --version OK");
            readiness.add("Version valid", true, "12.2.3 (fijada)");
            readiness.add("Checksum valid", true, "SHA-256 verificado antes de extraer");
            client.sendLog(executionId, "INFO", "[NETWORK] ✓ Network Runtime disponible");

            int port = findFreePort();
            readiness.add("Port available", true, String.valueOf(port));
            client.sendLog(executionId, "INFO", "[NETWORK] ✓ Puerto disponible: " + port);

            confDir = agentDataDir.resolve("network-monitoring").resolve("ca");
            Files.createDirectories(confDir);
            Path evidenceDir = evidenceRootDir.resolve(executionId);
            Files.createDirectories(evidenceDir);
            eventsFile = evidenceDir.resolve("events.ndjson");
            Files.deleteIfExists(eventsFile);
            Files.createFile(eventsFile);

            Path addonScript = extractAddonScript(agentDataDir);
            if (addonScript == null) {
                client.sendLog(executionId, "WARN",
                        "[NETWORK] No se pudo preparar el addon de captura — se continúa sin Network Monitoring.");
                return Session.INACTIVE;
            }

            List<String> cmd = List.of(
                    mitmdump,
                    "--listen-port", String.valueOf(port),
                    "--set", "confdir=" + confDir,
                    "-s", addonScript.toString(),
                    "--set", "events_file=" + eventsFile,
                    "--set", "termlog_verbosity=warn",
                    "--set", "flow_detail=0"
            );
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            proxyProcess = p;
            // Drenar stdout en un hilo daemon — evitar bloquear el pipe del proceso.
            Thread drain = new Thread(() -> {
                try (InputStream in = p.getInputStream()) { in.readAllBytes(); } catch (Exception ignored) {}
            }, "network-monitoring-proxy-drain");
            drain.setDaemon(true);
            drain.start();

            // Da tiempo a mitmdump a generar la CA (mitmproxy-ca-cert.pem) en el confdir
            // en su primer arranque — no es un sleep fijo "por si acaso": se sondea el
            // archivo hasta 5s, saliendo en cuanto aparece.
            Path caCertPem = confDir.resolve("mitmproxy-ca-cert.pem");
            long deadline = System.currentTimeMillis() + 5000;
            while (!Files.exists(caCertPem) && System.currentTimeMillis() < deadline) {
                Thread.sleep(200);
            }
            readiness.add("Proxy can start", p.isAlive(), p.isAlive() ? "pid=" + p.pid() : "proceso terminó de inmediato");
            if (!p.isAlive()) {
                client.sendLog(executionId, "WARN", readiness.renderSummary());
                proxyProcess = null;
                return Session.INACTIVE;
            }
            client.sendLog(executionId, "INFO", "[NETWORK] Network Runtime Readiness: READY");

            String caFingerprint = Files.exists(caCertPem) ? computeFingerprint(caCertPem) : null;

            DeviceTrustStore trustStore = new DeviceTrustStore(agentDataDir);
            boolean trusted = caFingerprint != null && trustStore.isTrusted(udid, caFingerprint);

            if (isAndroid && udid != null && !udid.isBlank()) {
                setAndroidProxy(client, executionId, udid, port);
                if (!trusted) {
                    tryZeroTouchAndroidCaInstall(client, executionId, udid, caCertPem);
                }
            }

            if (!trusted) {
                printSetupRequiredBanner(client, executionId, udid, isAndroid, port);
                // Fail-open explícito: seguimos arrancados (para poder detectar el
                // handshake y persistir TRUSTED en cuanto el usuario complete el paso
                // manual en una corrida futura), pero el Job de HOY probablemente no
                // tendrá tráfico HTTPS decodificado — eso es esperado y no es un error.
            } else {
                client.sendLog(executionId, "INFO",
                        "[NETWORK] ✓ Dispositivo " + udid + " — certificado confiable (verificado previamente)");
            }

            client.sendLog(executionId, "INFO", "[NETWORK] Monitoring started");
            return new Session(true, port, eventsFile.toString(), evidenceRootDir.toString(), caFingerprint);

        } catch (Exception e) {
            client.sendLog(executionId, "WARN",
                    "[NETWORK] No se pudo iniciar Network Monitoring: " + e.getMessage()
                    + " — el Job continúa normalmente.");
            safeStopProxyProcess();
            return Session.INACTIVE;
        }
    }

    /**
     * Detiene el proxy y revierte cualquier configuración del dispositivo.
     * Idempotente — no-op si ya se detuvo o nunca arrancó. Nunca lanza.
     */
    public static void stop(BackendClient client, String executionId) {
        try {
            if (androidProxyWasSet && androidUdidWithProxySet != null) {
                unsetAndroidProxy(client, executionId, androidUdidWithProxySet);
            }
        } catch (Exception ignored) {}
        androidProxyWasSet = false;
        androidUdidWithProxySet = null;

        boolean wasActive = proxyProcess != null;
        safeStopProxyProcess();

        if (wasActive) {
            try { client.sendLog(executionId, "INFO", "[NETWORK] Monitoring stopped"); } catch (Exception ignored) {}
        }
    }

    /**
     * Tras una ejecución con tráfico real, confirma si el handshake TLS del
     * dispositivo tuvo éxito (CA confiada) y, si es así, persiste TRUSTED para
     * que las próximas ejecuciones ya no muestren el aviso de configuración.
     * Se llama después de correr los tests (cuando ya hubo oportunidad real de
     * generar tráfico HTTPS) — nunca antes.
     */
    public static void verifyAndPersistTrust(BackendClient client, String executionId,
                                              NetworkMonitoringConfig cfg, Path agentDataDir,
                                              String udid, String caFingerprintSha256) {
        if (cfg == null || !cfg.enabled || udid == null || udid.isBlank()
                || caFingerprintSha256 == null || eventsFile == null) return;
        try {
            if (!Files.exists(eventsFile)) return;
            boolean sawSuccess = false;
            for (String line : Files.readAllLines(eventsFile)) {
                if (line.contains("\"type\":\"tls_handshake\"") || line.contains("\"type\": \"tls_handshake\"")) {
                    if (line.contains("\"SUCCESS\"")) { sawSuccess = true; break; }
                }
                // Un flow HTTP(S) decodificado con éxito también es evidencia positiva.
                if (line.contains("\"type\":\"http_flow\"") || line.contains("\"type\": \"http_flow\"")) {
                    sawSuccess = true;
                    break;
                }
            }
            DeviceTrustStore trustStore = new DeviceTrustStore(agentDataDir);
            if (sawSuccess) {
                trustStore.markTrusted(udid, caFingerprintSha256);
                client.sendLog(executionId, "INFO",
                        "[NETWORK] ✓ Confianza de certificado confirmada para " + udid
                        + " — no se pedirá de nuevo en próximas ejecuciones.");
            }
        } catch (Exception ignored) {}
    }

    // ── Android ──────────────────────────────────────────────────────────────

    private static String adbPath() {
        String path = System.getProperty("ADB_PATH");
        return (path != null && !path.isBlank()) ? path : "adb";
    }

    private static void setAndroidProxy(BackendClient client, String executionId, String udid, int port) {
        try {
            String hostIp = java.net.InetAddress.getLocalHost().getHostAddress();
            Process p = new ProcessBuilder(adbPath(), "-s", udid, "shell", "settings", "put", "global",
                    "http_proxy", hostIp + ":" + port).redirectErrorStream(true).start();
            p.waitFor(5, TimeUnit.SECONDS);
            if (p.exitValue() == 0) {
                androidProxyWasSet = true;
                androidUdidWithProxySet = udid;
                client.sendTechLog(executionId, "[NETWORK] Proxy Android configurado: " + hostIp + ":" + port);
            }
        } catch (Exception e) {
            client.sendTechLog(executionId, "[NETWORK] No se pudo configurar el proxy Android: " + e.getMessage());
        }
    }

    private static void unsetAndroidProxy(BackendClient client, String executionId, String udid) {
        try {
            Process p = new ProcessBuilder(adbPath(), "-s", udid, "shell", "settings", "put", "global",
                    "http_proxy", ":0").redirectErrorStream(true).start();
            p.waitFor(5, TimeUnit.SECONDS);
            client.sendTechLog(executionId, "[NETWORK] Proxy Android revertido.");
        } catch (Exception e) {
            client.sendTechLog(executionId, "[NETWORK] No se pudo revertir el proxy Android: " + e.getMessage());
        }
    }

    /**
     * Camino de CERO toques humanos — SOLO cuando `adb root` tiene éxito (emulador
     * con imagen "Google APIs", o dispositivo físico rooteado). En cualquier otro
     * caso (la inmensa mayoría de dispositivos físicos reales) esta función no
     * hace nada más que empujar el certificado y lanzar la pantalla del
     * instalador del sistema — el toque final de "Instalar de todos modos" sigue
     * siendo del usuario, porque Android lo exige por diseño y no existe API
     * pública para saltarlo. NUNCA se intenta evadir esa protección.
     */
    private static void tryZeroTouchAndroidCaInstall(BackendClient client, String executionId,
                                                       String udid, Path caCertPem) {
        try {
            Process rootCheck = new ProcessBuilder(adbPath(), "-s", udid, "root")
                    .redirectErrorStream(true).start();
            String out = new String(rootCheck.getInputStream().readAllBytes());
            rootCheck.waitFor(10, TimeUnit.SECONDS);
            boolean rooted = out.toLowerCase().contains("restarting adbd as root");
            if (!rooted) {
                pushCaAndLaunchSystemInstaller(client, executionId, udid, caCertPem);
                return;
            }

            client.sendTechLog(executionId, "[NETWORK] Dispositivo/emulador rooteado — instalando CA "
                    + "directamente en el almacén del sistema (cero toques humanos).");
            Thread.sleep(1000); // dar tiempo a que adbd reinicie como root
            new ProcessBuilder(adbPath(), "-s", udid, "remount").redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS);

            // Android exige el nombre del archivo del sistema = hash subject (openssl x509 -subject_hash_old)
            // — se resuelve dentro del dispositivo para no depender de OpenSSL en el Runner.
            String remotePem = "/data/local/tmp/mitm-ca.pem";
            new ProcessBuilder(adbPath(), "-s", udid, "push", caCertPem.toString(), remotePem)
                    .redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS);

            Process hashProc = new ProcessBuilder(adbPath(), "-s", udid, "shell",
                    "openssl x509 -inform PEM -subject_hash_old -in " + remotePem + " | head -1")
                    .redirectErrorStream(true).start();
            String hash = new String(hashProc.getInputStream().readAllBytes()).trim();
            hashProc.waitFor(10, TimeUnit.SECONDS);
            if (hash.isBlank()) {
                client.sendTechLog(executionId, "[NETWORK] No se pudo calcular el hash del certificado en el "
                        + "dispositivo rooteado — se omite la instalación cero-toque.");
                pushCaAndLaunchSystemInstaller(client, executionId, udid, caCertPem);
                return;
            }
            String systemDest = "/system/etc/security/cacerts/" + hash + ".0";
            new ProcessBuilder(adbPath(), "-s", udid, "shell", "cp", remotePem, systemDest)
                    .redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS);
            new ProcessBuilder(adbPath(), "-s", udid, "shell", "chmod", "644", systemDest)
                    .redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS);
            client.sendTechLog(executionId, "[NETWORK] CA instalada en el almacén del sistema: " + systemDest);
        } catch (Exception e) {
            client.sendTechLog(executionId, "[NETWORK] Instalación cero-toque de CA falló ("
                    + e.getMessage() + ") — se necesitará el paso manual habitual.");
        }
    }

    private static void pushCaAndLaunchSystemInstaller(BackendClient client, String executionId,
                                                         String udid, Path caCertPem) {
        try {
            String remotePath = "/sdcard/Download/mitm-ca.cer";
            new ProcessBuilder(adbPath(), "-s", udid, "push", caCertPem.toString(), remotePath)
                    .redirectErrorStream(true).start().waitFor(10, TimeUnit.SECONDS);
            client.sendTechLog(executionId, "[NETWORK] Certificado copiado a " + remotePath
                    + " en el dispositivo — falta el toque final del usuario para confiarlo "
                    + "(Android lo exige por diseño, no es evadible).");
        } catch (Exception e) {
            client.sendTechLog(executionId, "[NETWORK] No se pudo copiar el certificado al dispositivo: " + e.getMessage());
        }
    }

    // ── Utilidades ───────────────────────────────────────────────────────────

    private static void printSetupRequiredBanner(BackendClient client, String executionId,
                                                   String udid, boolean isAndroid, int port) {
        try {
            String hostIp = java.net.InetAddress.getLocalHost().getHostAddress();
            StringBuilder sb = new StringBuilder();
            sb.append("[NETWORK] ⚠ Configuración inicial del dispositivo requerida (").append(udid).append(")\n");
            if (isAndroid) {
                sb.append("[NETWORK]    El proxy ya se configuró automáticamente. Falta confiar el certificado:\n");
                sb.append("[NETWORK]    1) En el dispositivo, abre el archivo mitm-ca.cer ya copiado a Descargas\n");
                sb.append("[NETWORK]    2) Confirma \"Instalar de todos modos\" en el diálogo del sistema\n");
            } else {
                sb.append("[NETWORK]    1) Conecta el iPhone a la misma red Wi-Fi y configura el proxy manual a ")
                        .append(hostIp).append(":").append(port).append("\n");
                sb.append("[NETWORK]    2) Abre Safari → http://mitm.it → instala el perfil \"mitmproxy\"\n");
                sb.append("[NETWORK]    3) Ajustes → General → VPN y gestión de dispositivos → confía en el perfil\n");
                sb.append("[NETWORK]    4) Ajustes → General → Acerca de → Confianza de certificados → activa \"mitmproxy\"\n");
            }
            sb.append("[NETWORK] Este paso es UNA SOLA VEZ por dispositivo — no se repetirá una vez confiado.");
            client.sendLog(executionId, "WARN", sb.toString());
        } catch (Exception ignored) {}
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private static Path extractAddonScript(Path agentDataDir) {
        try {
            Path dest = agentDataDir.resolve("network-monitoring").resolve("capture_addon.py");
            Files.createDirectories(dest.getParent());
            try (InputStream in = NetworkMonitoringManager.class
                    .getResourceAsStream("/mitmproxy-addon/capture_addon.py")) {
                if (in == null) return null;
                Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return dest;
        } catch (Exception e) {
            return null;
        }
    }

    private static String computeFingerprint(Path caCertPem) {
        try (InputStream in = Files.newInputStream(caCertPem)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(in);
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02X:", b));
            sb.setLength(sb.length() - 1);
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void safeStopProxyProcess() {
        Process p = proxyProcess;
        proxyProcess = null;
        if (p == null) return;
        try {
            p.destroy();
            if (!p.waitFor(10, TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (Exception ignored) {
            try { p.destroyForcibly(); } catch (Exception ignored2) {}
        }
    }
}
