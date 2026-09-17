package qa.cinepolis.runner;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * TAREA 25 — Apple Signing Probe.
 *
 * La TAREA 24 (auditoría) determinó la causa raíz de "No Accounts" en xcodebuild:
 * {@code AppleDeveloperTeamManager}/{@code XcodeAccountProvider}/{@code validateCachedWda()}
 * solo leen artefactos LOCALES estáticos (vigencia X.509 del certificado en Keychain +
 * el último snapshot que Xcode escribió en {@code com.apple.dt.Xcode.plist}) — "USABLE"
 * nunca pregunta en vivo si la sesión de cuenta Apple ID que
 * {@code xcodebuild -allowProvisioningUpdates} necesita para crear un provisioning
 * profile NUEVO contra el portal de Apple sigue vigente. Esa sesión es una tercera
 * entidad, independiente del certificado y del plist, que expira con el tiempo sin que
 * ninguno de esos dos artefactos cambie.
 *
 * Este probe cierra esa brecha ejecutando una operación REAL de xcodebuild — no una
 * simple lectura de Keychain/plist, que es precisamente lo que ya existe y produce
 * falsos positivos — pero mucho más barata que el {@code xcodebuild test} completo que
 * usa {@link WdaManager}: un {@code xcodebuild build} (no {@code test}) con los MISMOS
 * parámetros de firma/provisioning.
 *
 * ── Por qué {@code build} y no {@code -dry-run} ──────────────────────────────────
 * Se probó primero {@code xcodebuild build ... -dry-run} contra este mismo proyecto:
 * Xcode 26.5 no reconoce {@code -dry-run} como acción/flag válido para {@code build} —
 * imprime la ayuda completa y termina en ~0.7s sin ejecutar nada. Se descartó sin
 * asumir que funcionaría, tal como exige la tarea.
 *
 * ── Por qué {@code build} SÍ valida lo que necesitamos ──────────────────────────
 * Confirmado con ejecución real contra el dispositivo físico (00008110-000129261482601E,
 * Team 872XA9LGH4, mismo estado "No Accounts" diagnosticado en TAREA 24):
 * {@code xcodebuild build} con estos mismos parámetros falla en la fase
 * "GatherProvisioningInputs" — ANTES de compilar una sola línea de código — con
 * exactamente el mismo "No Accounts: Add a new account in Accounts settings." /
 * "No profiles for 'io.qautomation.wda.xctrunner' were found" que produce
 * {@code xcodebuild test}. Tiempo real medido: 5.3s (vs. varios minutos de
 * {@code test}, que además instala y lanza WDA en el dispositivo). La resolución de
 * firma/provisioning ocurre en la MISMA fase temprana para ambas acciones — {@code build}
 * simplemente no continúa hacia instalar/lanzar/servir HTTP como sí hace {@code test}.
 * No se instala nada en el dispositivo, no se lanza la suite, no se modifica
 * {@code project.pbxproj} (a diferencia de {@code WdaManager.syncProjectSigningTeam()},
 * este probe pasa el Team únicamente por línea de comandos — el archivo del proyecto
 * nunca se toca, evitando ese efecto secundario permanente para una simple verificación).
 *
 * Cero cambios a {@link WdaLifecycleOwner} ni {@link WdaManager}: este probe es una
 * pieza aparte, reutiliza {@link WdaManager#findWdaProjectPath()} (ya público) y el
 * constructor ya accesible en el mismo paquete de {@code WdaLifecycleOwner.Result} para
 * expresar "no listo" sin invocar {@code acquire()} — ver el único call site en
 * {@code IosPreflightManager.runPreflight()}.
 *
 * La clasificación de la salida reutiliza el conocimiento YA EXISTENTE y respaldado con
 * evidencia de {@link IOSWdaErrorClassifier} (TAREA 3.2) en vez de duplicar los mismos
 * patrones de texto — con una única diferencia deliberada de prioridad: este probe
 * necesita que "No Accounts" (sesión de cuenta) gane sobre "No profiles for"
 * (provisioning) cuando ambos aparecen juntos, porque la propia TAREA 24 concluyó que
 * la falta de sesión de cuenta es la causa raíz y la ausencia de perfil es un síntoma
 * derivado de no poder crear uno nuevo — exactamente el caso real observado hoy.
 * {@link IOSWdaErrorClassifier#classify(String)} prioriza PROVISIONING sobre SIGNING
 * para su propio uso (no consumido aún por ningún flujo real, según su propio Javadoc)
 * — cambiar ESA prioridad globalmente arriesgaría a otros futuros consumidores fuera
 * del alcance de esta tarea, así que aquí se comprueba "No Accounts" primero y solo se
 * delega al clasificador compartido para el resto de los casos.
 */
final class AppleSigningProbe {

    private AppleSigningProbe() {}

    enum Status {
        READY, ACCOUNT_SESSION_REQUIRED, PROVISIONING_REQUIRED, SIGNING_ERROR, BUILD_FAILURE, TIMEOUT, UNKNOWN,
        // TAREA — distinguen, ANTES de invocar xcodebuild, "Xcode no tiene ninguna cuenta
        // configurada" (requiere intervención humana real) de "sí hay cuenta(s) pero
        // ninguna resolvió a un Team/certificado utilizable" (p. ej. certificado huérfano
        // de un Team anterior — NO requiere volver a iniciar sesión). Ver el branch de
        // teamId en blanco dentro de probe().
        APPLE_ACCOUNT_NOT_AUTHENTICATED, APPLE_TEAM_NOT_AVAILABLE
    }

    record Result(Status status, String reason, long probeDurationMs) {}

    /**
     * Generoso a propósito: en el caso READY el probe SÍ compila WebDriverAgentLib +
     * WebDriverAgentRunner de verdad (no hay forma más barata confirmada — ver Javadoc
     * de clase). En el caso de fallo real medido (ACCOUNT_SESSION_REQUIRED) termina en
     * ~5s; este límite solo protege contra un xcodebuild colgado, nunca se espera
     * alcanzarlo en un caso normal.
     */
    private static final long PROBE_TIMEOUT_MS = 90_000L;

    /**
     * TAREA 26A — marcador estable, ASCII, exclusivo de esta clase (nunca aparece en
     * la salida RAW de xcodebuild) que {@link IOSWdaErrorClassifier} reconoce con
     * máxima prioridad para producir {@link IOSWdaErrorCode#IOS_ACCOUNT_SESSION_REQUIRED}
     * cuando este mensaje se persiste vía {@code WdaLifecycleOwner.markTerminalError()}
     * (ver {@code IosPreflightManager.runPreflight()}) — es el ÚNICO puente entre el
     * {@link Status} ya estructurado de este probe y el resto de {@code TERMINAL_ERRORS},
     * que solo almacena texto. No se afirma que la sesión "expiró" (TAREA 24 dejó ese
     * mecanismo sin demostrar) — solo que no está disponible ahora mismo.
     */
    private static final String ACCOUNT_SESSION_REQUIRED_REASON =
            "[APPLE-SIGNING-PROBE] ACCOUNT_SESSION_REQUIRED — No Accounts: la sesión de Apple ID "
            + "utilizada por Xcode/xcodebuild no está disponible para provisioning automático. "
            + "Requiere reautenticación en Xcode.";

    static Result probe(BackendClient client, String executionId,
                         String udid, String teamId, String wdaBundleId) {
        long t0 = System.currentTimeMillis();

        client.sendTechLog(executionId,
                "[APPLE-SIGNING-PROBE] Starting...\n"
                + "[APPLE-SIGNING-PROBE] Team: " + (isBlank(teamId) ? "no detectado" : teamId) + "\n"
                + "[APPLE-SIGNING-PROBE] Certificate: Apple Development\n"
                + "[APPLE-SIGNING-PROBE] Bundle: " + wdaBundleId);

        if (isBlank(teamId)) {
            // TAREA — reutiliza XcodeAccountProvider, la MISMA fuente local (Xcode.plist)
            // que AppleDeveloperTeamManager ya consulta para resolver teamId — sin red, sin
            // xcodebuild, sin ninguna acción nueva.
            AppleDeveloperAccountProvider.DiscoveryResult accounts =
                    new XcodeAccountProvider().discoverAuthenticatedTeams();
            return report(client, executionId, t0,
                    classifyMissingTeam(accounts, System.currentTimeMillis() - t0));
        }

        String projectPath = WdaManager.findWdaProjectPath();
        if (projectPath == null) {
            return report(client, executionId, t0, new Result(Status.UNKNOWN,
                    "WDA_PROJECT_NOT_FOUND — WebDriverAgent.xcodeproj no encontrado — no se puede ejecutar el probe.",
                    System.currentTimeMillis() - t0));
        }

        List<String> command = buildCommand(projectPath, udid, teamId, wdaBundleId);

        // TAREA — logging diagnóstico permanente ANTES de ejecutar. Ningún valor aquí es
        // secreto: Team/bundle/UDID ya se registran hoy en otros logs del Runner.
        client.sendTechLog(executionId,
                "[APPLE-SIGNING-PROBE] command=" + String.join(" ", command) + "\n"
                + "[APPLE-SIGNING-PROBE] workingDirectory=" + System.getProperty("user.dir") + "\n"
                + "[APPLE-SIGNING-PROBE] project=" + projectPath + "\n"
                + "[APPLE-SIGNING-PROBE] scheme=WebDriverAgentRunner\n"
                + "[APPLE-SIGNING-PROBE] destination=id=" + udid + "\n"
                + "[APPLE-SIGNING-PROBE] team=" + teamId + "\n"
                + "[APPLE-SIGNING-PROBE] signingIdentity=Apple Development\n"
                + "[APPLE-SIGNING-PROBE] updatedWDABundleId=" + wdaBundleId);

        String output;
        int exitCode;
        PhaseTracker phase = new PhaseTracker();
        try {
            Process p = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            client.sendTechLog(executionId,
                    "[APPLE-SIGNING-PROBE] PROCESS_STARTED pid=" + p.pid()
                    + " startTime=" + Instant.now());

            StringBuilder sb = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (var in = p.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                        String newPhase = phase.observe(sb);
                        if (newPhase != null) {
                            client.sendTechLog(executionId,
                                    "[APPLE-SIGNING-PROBE] XCODEBUILD_PHASE: " + newPhase);
                        }
                    }
                } catch (Exception ignored) {
                    // El proceso terminó / stream cerrado — nada que hacer.
                }
            }, "apple-signing-probe-reader");
            reader.setDaemon(true);
            reader.start();

            boolean finished = p.waitFor(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                killTree(p);
                joinQuietly(reader);
                long durationMs = System.currentTimeMillis() - t0;
                output = sb.toString();
                // TAREA — causa raíz real del UNKNOWN ciego anterior: se descartaba toda
                // la salida ya capturada en vez de clasificarla. exitCode=-1 (sentinel,
                // el proceso fue matado, nunca terminó solo) hace estructuralmente
                // imposible reportar READY aquí — classify() exige exitCode==0 para eso.
                Result partial = classify(output, -1, durationMs);
                if (partial.status() != Status.UNKNOWN) {
                    return report(client, executionId, t0, partial);
                }
                String lastPhase = phase.last() == null ? "PROCESS_STARTED_BUT_NO_OUTPUT" : phase.last();
                return report(client, executionId, t0, new Result(Status.TIMEOUT,
                        "WDA_PROBE_TIMEOUT — el probe no terminó dentro de " + PROBE_TIMEOUT_MS
                        + "ms sin ninguna evidencia clasificable en la salida capturada. "
                        + "Última fase observada: " + lastPhase + ".",
                        durationMs));
            }
            joinQuietly(reader);
            exitCode = p.exitValue();
            output = sb.toString();
        } catch (Exception e) {
            return report(client, executionId, t0, new Result(Status.UNKNOWN,
                    "XCODEBUILD_NOT_STARTED — no se pudo ejecutar el probe: " + e.getMessage(),
                    System.currentTimeMillis() - t0));
        }

        Result classified = classify(output, exitCode, System.currentTimeMillis() - t0);
        return report(client, executionId, t0, classified);
    }

    /**
     * Detección de fase basada en marcadores REALES de la salida estándar de
     * xcodebuild (las mismas líneas que xcodebuild imprime en cualquier build,
     * documentadas y observables en cualquier ejecución — no se inventa ningún
     * texto nuevo). Uso exclusivamente diagnóstico: nunca decide READY/UNKNOWN,
     * solo permite saber, ante un timeout, hasta dónde llegó realmente el proceso
     * antes de matarlo — distingue "nunca imprimió nada" de "llegó a compilar/firmar
     * pero se quedó ahí".
     */
    private static final class PhaseTracker {
        private static final String[][] ORDERED_PHASES = {
            {"CreateBuildRequest", "BUILD_REQUEST_CREATED"},
            {"ComputeTargetDependencyGraph", "DEPENDENCY_GRAPH_COMPUTED"},
            {"GatherProvisioningInputs", "PROVISIONING_RESOLUTION"},
            {"=== BUILD TARGET", "BUILD_STARTED"},
            {"CodeSign ", "CODE_SIGNING_STARTED"},
            {"** BUILD FAILED **", "BUILD_FAILED"},
            {"** BUILD SUCCEEDED **", "BUILD_SUCCEEDED"},
        };

        private int lastIndex = -1;

        /** Devuelve el nombre de la nueva fase si avanzó respecto a la última observada, o null. */
        String observe(CharSequence accumulated) {
            String text = accumulated.toString();
            int highest = lastIndex;
            for (int i = 0; i < ORDERED_PHASES.length; i++) {
                if (text.contains(ORDERED_PHASES[i][0])) {
                    highest = Math.max(highest, i);
                }
            }
            if (highest > lastIndex) {
                lastIndex = highest;
                return ORDERED_PHASES[highest][1];
            }
            return null;
        }

        String last() {
            return lastIndex >= 0 ? ORDERED_PHASES[lastIndex][1] : null;
        }
    }

    private static List<String> buildCommand(String projectPath, String udid, String teamId, String wdaBundleId) {
        // Mismos parámetros que WdaManager.startXcodebuildAttempt() usará después para
        // el build real — "build" en vez de "test" (ver Javadoc de clase) y SIN pasar
        // por syncProjectSigningTeam(): el Team se pasa solo por línea de comandos, así
        // que project.pbxproj nunca se modifica por causa de este probe.
        List<String> cmd = new ArrayList<>();
        cmd.add("xcodebuild");
        cmd.add("build");
        cmd.add("-project");     cmd.add(projectPath);
        cmd.add("-scheme");      cmd.add("WebDriverAgentRunner");
        cmd.add("-destination"); cmd.add("id=" + udid);
        cmd.add("DEVELOPMENT_TEAM=" + teamId);
        cmd.add("CODE_SIGN_IDENTITY=Apple Development");
        cmd.add("CODE_SIGN_STYLE=Automatic");
        if (!isBlank(wdaBundleId)) {
            cmd.add("UPDATEDWDABUNDLEID=" + wdaBundleId);
        }
        cmd.add("-allowProvisioningUpdates");
        cmd.add("-allowProvisioningDeviceRegistration");
        return cmd;
    }

    /**
     * Clasificación pura del caso "teamId en blanco" — recibe el {@link
     * AppleDeveloperAccountProvider.DiscoveryResult} ya resuelto (mismo patrón que
     * {@code AppleDeveloperTeamManager.discoverCandidates(..., DiscoveryResult)}: la
     * fuente se resuelve en el llamador, la decisión es pura y testeable sin tocar
     * Xcode.plist real). "available" significa "se pudo LEER el registro de cuentas de
     * Xcode", nunca "hay/no hay cuenta" (ver Javadoc de {@code DiscoveryResult}) — solo
     * cuando {@code available=true} Y el mapa de Teams está vacío hay evidencia real de
     * que Xcode no tiene ninguna cuenta configurada.
     */
    static Result classifyMissingTeam(AppleDeveloperAccountProvider.DiscoveryResult accounts, long probeDurationMs) {
        if (accounts.available() && accounts.teamNamesById().isEmpty()) {
            return new Result(Status.APPLE_ACCOUNT_NOT_AUTHENTICATED,
                    "APPLE_ACCOUNT_NOT_AUTHENTICATED — Xcode no tiene ninguna cuenta de Apple ID "
                    + "configurada. Requiere agregar una cuenta en Xcode → Settings → Accounts "
                    + "(única acción de este diagnóstico que sí requiere intervención humana).",
                    probeDurationMs);
        }
        if (accounts.available()) {
            return new Result(Status.APPLE_TEAM_NOT_AVAILABLE,
                    "APPLE_TEAM_NOT_AVAILABLE — Xcode tiene cuenta(s) autenticada(s) ("
                    + String.join(", ", accounts.teamNamesById().keySet())
                    + "), pero ninguna resolvió a un Team con certificado/perfil utilizable "
                    + "(posible certificado huérfano de un Team anterior) — no requiere volver "
                    + "a iniciar sesión.",
                    probeDurationMs);
        }
        // No se pudo ni siquiera leer el registro de cuentas de Xcode — no hay evidencia
        // suficiente para afirmar que la cuenta está desautenticada, así que no se asume.
        return new Result(Status.APPLE_TEAM_NOT_AVAILABLE,
                "APPLE_TEAM_NOT_AVAILABLE — Team ID no detectado y no se pudo verificar el "
                + "registro de cuentas de Xcode (" + accounts.unavailableReason() + ").",
                probeDurationMs);
    }

    /**
     * Clasificación pura — sin efectos secundarios, no ejecuta nada. Es la única parte
     * de esta clase cubierta por tests unitarios (no requiere Xcode/Apple ID/hardware).
     */
    static Result classify(String output, int exitCode, long probeDurationMs) {
        String text = output == null ? "" : output;

        // TAREA — PRIORIDAD MÁXIMA, causa raíz real encontrada (evidencia RUN-1002): el
        // orden anterior comprobaba patrones de fallo ANTES de comprobar éxito, sin
        // condicionarlos al exit code. Un build que termina con exitCode=0 y el marcador
        // oficial "** BUILD SUCCEEDED **" puede seguir conteniendo, en su log completo,
        // menciones no-fatales a "provisioning profile" (p. ej. un warning informativo,
        // o texto de una fase intermedia que el build superó sin problema) — con el
        // orden anterior, eso bastaba para clasificar INCORRECTAMENTE un build
        // realmente exitoso como PROVISIONING_REQUIRED, nunca llegando a comprobar
        // "build succeeded". Ahora el éxito inequívoco (exitCode==0 + marcador oficial)
        // se comprueba PRIMERO — ninguna clasificación de fallo de abajo puede activarse
        // si el build realmente tuvo éxito.
        if (exitCode == 0 && containsIgnoreCase(text, "build succeeded")) {
            return new Result(Status.READY,
                    "xcodebuild pudo resolver signing/provisioning correctamente.",
                    probeDurationMs);
        }

        // A partir de aquí NO hubo evidencia de éxito real — cada clasificación de fallo
        // exige además exitCode != 0 (nunca solo texto): "la clasificación debe estar
        // basada en evidencia real: exit code + errores de signing/provisioning", nunca
        // en una coincidencia de texto aislada de lo que realmente pasó.

        // "No Accounts" gana sobre "No profiles for" cuando ambos aparecen — ver Javadoc
        // de clase para el porqué (causa raíz vs síntoma derivado, evidencia TAREA 24).
        if (exitCode != 0 && containsIgnoreCase(text, "no accounts:")) {
            return new Result(Status.ACCOUNT_SESSION_REQUIRED, ACCOUNT_SESSION_REQUIRED_REASON, probeDurationMs);
        }

        // TAREA — KEYCHAIN_ACCESS_FAILURE: error REAL y documentado de macOS
        // Security.framework (errSecInteractionNotAllowed = -25308; "User interaction
        // is not allowed" es el texto exacto que SecCopyErrorMessageString devuelve
        // para ese OSStatus). Ocurre cuando codesign/security necesita mostrar el
        // diálogo "Siempre permitir" del Keychain y no hay sesión de UI disponible —
        // el caso típico de un proceso lanzado por un LaunchAgent. No es un problema
        // de certificado ni de cuenta: es acceso al Keychain en sí.
        if (exitCode != 0
                && (containsIgnoreCase(text, "user interaction is not allowed")
                    || containsIgnoreCase(text, "errsecinteractionnotallowed")
                    || text.contains("-25308"))) {
            return new Result(Status.SIGNING_ERROR,
                    "KEYCHAIN_ACCESS_FAILURE — Keychain requiere interacción del usuario "
                    + "('User interaction is not allowed' / errSecInteractionNotAllowed) — "
                    + "inaccesible en un proceso sin sesión de UI (LaunchAgent).",
                    probeDurationMs);
        }

        if (exitCode != 0) {
            IOSWdaErrorCode code = IOSWdaErrorClassifier.classify(text);
            switch (code) {
                case IOS_PROVISIONING_REQUIRED:
                    return new Result(Status.PROVISIONING_REQUIRED,
                            "No existe un provisioning profile válido para el bundle solicitado.",
                            probeDurationMs);
                case IOS_SIGNING_REQUIRED:
                    // Solo se llega aquí si IOSWdaErrorClassifier detectó "no accounts:" por
                    // una vía distinta a la comprobación explícita de arriba — normalizado al
                    // mismo estado para no exponer dos nombres distintos para la misma causa.
                    return new Result(Status.ACCOUNT_SESSION_REQUIRED, ACCOUNT_SESSION_REQUIRED_REASON, probeDurationMs);
                case IOS_DEVELOPER_TRUST_REQUIRED:
                    return new Result(Status.SIGNING_ERROR,
                            "xcodebuild reportó un problema de confianza/certificado de firma "
                            + "(no de cuenta ni de provisioning).",
                            probeDurationMs);
                default:
                    break;
            }
        }

        // TAREA — XCODEBUILD_BUILD_FAILURE: evidencia real e inequívoca ("** BUILD
        // FAILED **") que antes se perdía dentro del UNKNOWN genérico. Se exige
        // exitCode != 0 (igual que READY exige == 0 para "build succeeded") para no
        // clasificar así por una coincidencia de texto sin exit code coherente.
        if (exitCode != 0 && containsIgnoreCase(text, "** build failed **")) {
            return new Result(Status.BUILD_FAILURE,
                    "XCODEBUILD_BUILD_FAILURE — xcodebuild terminó con \"** BUILD FAILED **\" "
                    + "(exitCode=" + exitCode + ") sin ninguna señal de cuenta/provisioning/confianza "
                    + "— revisar el log completo del build para la causa específica.",
                    probeDurationMs);
        }

        // IOS_WDA_BUILD_FAILED / IOS_WDA_STARTUP_FAILED / UNKNOWN del clasificador
        // compartido, o cualquier salida vacía/no reconocida — nunca se oculta como
        // READY (regla explícita de TAREA 25): se trata como bloqueante por precaución.
        return new Result(Status.UNKNOWN,
                "xcodebuild terminó con un resultado no reconocido (exitCode=" + exitCode
                + ") — no se puede clasificar con seguridad; se trata como bloqueante por precaución.",
                probeDurationMs);
    }

    /**
     * NOTA IMPORTANTE (causa raíz investigada de "wdaBuildStarted=false" en timeout):
     * el campo {@code wdaBuildStarted} de este log NUNCA significó "el proceso
     * xcodebuild se lanzó" — significa exclusivamente "el probe terminó y clasificó
     * READY". Antes de esta corrección, un timeout devolvía UNKNOWN directamente sin
     * clasificar la salida ya capturada, así que "wdaBuildStarted=false" no aportaba
     * ninguna evidencia real sobre qué hizo xcodebuild en esos 90s. Ahora, ante un
     * timeout, primero se reclasifica la salida parcial (ver {@link #probe}) y solo
     * se llega aquí con {@link Status#TIMEOUT} cuando esa salida parcial tampoco
     * tenía ninguna señal reconocible — el nombre del campo se conserva sin cambios
     * (evita romper cualquier consumidor externo que ya lo parsee), pero ahora casi
     * nunca es el único dato disponible: los logs XCODEBUILD_PHASE y el `reason`
     * (que ahora incluye la última fase observada) dan la evidencia real que faltaba.
     */
    private static Result report(BackendClient client, String executionId, long t0, Result result) {
        long durationMs = System.currentTimeMillis() - t0;
        client.sendTechLog(executionId,
                "[APPLE-SIGNING-PROBE] Result: " + result.status()
                + (result.status() != Status.READY
                        ? "\n[APPLE-SIGNING-PROBE] xcodebuild reports: " + result.reason() : "")
                + "\n[APPLE-SIGNING-PROBE] probeDurationMs=" + durationMs
                + " wdaBuildStarted=" + (result.status() == Status.READY));
        return result;
    }

    private static boolean containsIgnoreCase(String text, String needle) {
        return text.toLowerCase().contains(needle.toLowerCase());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static void killTree(Process p) {
        try {
            for (ProcessHandle h : p.descendants().toList()) {
                try { h.destroyForcibly(); } catch (Exception ignored) {}
            }
            p.destroyForcibly();
        } catch (Exception ignored) {}
    }

    private static void joinQuietly(Thread t) {
        try { t.join(2_000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
    }
}
