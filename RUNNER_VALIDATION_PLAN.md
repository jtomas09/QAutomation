# Plan de Validación Final — Runner (auto-update vs. ejecución de suites)

Generado: 2026-07-17
Alcance: `runner/src/main/java/qa/cinepolis/runner/{RunnerAgent,JobExecutor,UpdateManager}.java`,
`src/test/java/{base/BaseTest,utils/AllureMailListener,utils/AllureReportSender,utils/PdfReportExtension}.java`

Código congelado desde el cierre de la investigación (commit `e17751e`). Este documento cubre
exclusivamente evidencia de comportamiento observado — no es una revisión de código.

---

## 0. Objetivo y alcance

Esta validación no revisa código — el desarrollo se considera cerrado. El objetivo es producir
evidencia observable (logs, métricas, artefactos generados) de que:

- El síntoma original — **ninguna suite envía correo** — no vuelve a ocurrir bajo ninguna de las
  condiciones que antes lo disparaban.
- El auto-update jamás corta una suite en ejecución, ni siquiera en el borde de la ventana de
  carrera original.
- STOP y RESTART manual se comportan **exactamente igual** que antes de esta investigación.
- Ningún cambio (gate CAS, jerarquía de `catch`, `ShutdownReason` tipado, limpieza en fallo
  parcial de arranque) deja hilos, procesos o el gate en un estado que no se auto-corrija.

### Causa raíz (referencia)

`UpdateManager.checkAndApply()` llamaba `System.exit(0)` sin verificar si había un job activo,
disparando el shutdown hook que forzaba `destroyForcibly()` sobre el proceso Gradle antes de que
JUnit completara `Launcher.execute()` — por eso `SuiteMailer.close()` y
`testPlanExecutionFinished()` nunca se ejecutaban y el correo jamás se enviaba. Evidencia real en
`automationqa-runner.log`: 15 de 18 kills coincidían con heartbeat `BUSY`.

---

## 1. Matriz de pruebas

| # | Escenario | Tipo | Riesgo que cubre | Duración aprox. |
|---|---|---|---|---|
| 1 | Smoke completa (~50 casos) | Base | Regresión funcional general | 45–90 min |
| 2 | Auto-update sin jobs | Base | Camino feliz del update | 5–10 min |
| 3 | Auto-update durante Smoke | **Crítico** | La causa raíz original | 45–90 min |
| 4 | STOP manual durante suite | Regresión | Comportamiento operador sin cambios | 10–15 min |
| 5 | RESTART manual durante suite | Regresión | Comportamiento operador sin cambios | 10–15 min |
| 6 | Error durante descarga de update | Borde | Liberación del gate en fallo | 10 min |
| 7 | VirtualMachineError simulado | **Crítico** | Jerarquía de `catch` fatal | 15 min |
| 8 | Múltiples suites consecutivas | Base | Fugas y estado acumulado | 3–4 h |
| 9 | 48 h continuas | Resistencia | Estabilidad de largo plazo | 48 h |

---

## 2. Escenarios en detalle

### 2.1 · Smoke completa (~50 casos)

**Preparación:** confirmar que no hay una versión de Runner más nueva publicada (para no
interferir con el escenario 3). Lanzar la Smoke normal desde el Dashboard.

**Validar:**
- Ejecución completa: los ~50 casos corren y terminan (sin cortes a mitad — referencia al bug
  histórico de "9/15/19 de 50").
- Video generado por cada test (`build/videos/<Clase>/*.mp4`).
- PDF generado por cada test (`build/reportes-pdf/*.pdf`) y el PDF fusionado de la suite.
- Reporte Allure publicado (URL válida, resultados completos).
- Correo recibido con el reporte adjunto.
- Runner vuelve a `ONLINE` en el heartbeat inmediatamente después (no queda en `BUSY`).

**Evidencia de log esperada (en orden):**
```
[Executor] ▶  RUN-XXXX  |  Suite: smoke  |  Env: QA  |  País: mexico
... 50× [Test] Starting / [Video] Guardado / PdfReportGenerator / [Test] Finished ...
[EMAIL FLOW] Entrando a TestExecutionListener.testPlanExecutionFinished() — esto solo ocurre si Launcher.execute() completó normalmente.
[EMAIL FLOW] Entrando a SuiteMailer.close() — root store cerrando, fin real del run.
[Suite] Sending final suite email. suiteName=... total=50 passed=... failed=...
[EMAIL FLOW] Invocando SMTP...
[EMAIL FLOW] Correo enviado correctamente.
[Runner] Heartbeat: N dispositivo(s) | ONLINE | ...
```

### 2.2 · Auto-update sin Jobs

**Preparación:** publicar una versión de Runner incrementada en el backend. Runner en `ONLINE`,
sin ningún job en cola. Esperar al próximo ciclo de `scheduleAtFixedRate` (o reiniciar el Runner
para forzar el primer ciclo a los 60 min).

**Validar:**
- Descarga exitosa y SHA256 validado.
- `rotate()` reemplaza el JAR activo, deja backup `.jar.bak`.
- `requestShutdown(0, AUTO_UPDATE)` se invoca — único `System.exit()` del módulo.
- El shutdown hook corre, reporta `ShutdownReason=AUTO_UPDATE`, `exitCode=0`.
- El wrapper externo reinicia el proceso con el JAR nuevo.
- Runner vuelve a `ONLINE`, reportando la versión nueva.

```
[Update] Nueva versión detectada: X.Y.Z (actual: A.B.C)
[Update] Runner libre. Aplicando actualización.
[Update] SHA256 OK.
[Update] Verificación final: sin jobs activos.
[Update] JAR actualizado: .../automationqa-runner.jar
[Update] Reiniciando Runner con la versión X.Y.Z (gate exclusivo confirmado: sin jobs activos desde antes de la descarga).
PROCESS FLOW — SHUTDOWN HOOK EJECUTADO
[PROCESS FLOW] Shutdown reason   : AUTO_UPDATE
[PROCESS FLOW] Exit code         : 0
... reinicio del proceso por el wrapper externo ...
[Runner] ===== RUNNER ACTIVO =====
```

### 2.3 · Auto-update durante una Smoke (crítico — la causa raíz original)

**Preparación:** publicar una versión nueva en el backend. Lanzar la Smoke. Mientras corre, forzar
(o esperar) a que `updateMgr.checkAndApply()` dispare — idealmente a mitad de la suite.

**Validar:**
- El update se difiere — `beginExclusiveUpdate()` falla porque el gate está en `JOB`.
- Cero descarga, cero `rotate()`, cero `System.exit()` mientras el gate esté ocupado.
- El proceso Gradle **no recibe ningún `destroyForcibly()`** — verificar ausencia total de
  `"Shutdown detectado — terminando proceso Gradle"` en toda la ventana de la suite.
- `Launcher.execute()` termina por sí solo (evidencia indirecta: aparecen los logs de
  `testPlanExecutionFinished()`/`SuiteMailer.close()`).
- Correo enviado normalmente, igual que en el escenario 1.
- Al terminar la suite (gate vuelve a libre), el **siguiente** ciclo aplica el update sin
  intervención manual.

```
[Executor] ▶  RUN-XXXX  |  Suite: smoke  |  ...
... suite en curso ...
[Update] Nueva versión detectada: X.Y.Z (actual: A.B.C)
[Update] Runner ocupado. Actualización diferida.   ← el gate hizo su trabajo
... la suite sigue corriendo, SIN interrupción ...
[EMAIL FLOW] Entrando a SuiteMailer.close() — root store cerrando, fin real del run.
[EMAIL FLOW] Correo enviado correctamente.
[Runner] Heartbeat: N dispositivo(s) | ONLINE | ...
... 60 min después, próximo ciclo programado ...
[Update] Runner libre. Aplicando actualización.
[Update] Reiniciando Runner con la versión X.Y.Z ...
```

> **Señal de fallo:** si en algún punto de este escenario aparece `"Shutdown detectado —
> terminando proceso Gradle"` o el correo no llega, la corrección no se sostiene — este es el
> único escenario que reproduce exactamente el bug original.

### 2.4 · STOP manual durante una suite (confirmar cero regresión)

Lanzar una suite y, a mitad de ejecución, enviar STOP desde el Dashboard.

**Validar (comparar contra el comportamiento previo a esta investigación):**
- El proceso Gradle se mata inmediatamente (comportamiento intencional de STOP, sin cambios).
- `stopAllServices(false)` corre completo: job-poll thread interrumpido, `workScheduler` apagado,
  Appium detenido, stream server detenido.
- Runner queda en `STOPPED` / heartbeat `OFFLINE`.
- Un START posterior reinicia todo con normalidad.

```
[Runner] Comando recibido: STOP
[PROCESS FLOW] Comando STOP recibido MIENTRAS HAY UN JOB ACTIVO — esto va a forzar killActiveProcess()...
[PROCESS FLOW] RunnerAgent.stopAllServices(jvmExit=false) START — ... job activo=true
[PROCESS FLOW] JobExecutor.killActiveProcess() invocado — pid=... proceso Gradle SIGUE VIVO
[Runner] Proceso Gradle terminado.
[Runner] ===== RUNNER DETENIDO — esperando START =====
```

### 2.5 · RESTART manual durante una suite (confirmar cero regresión)

Igual que 2.4, pero con RESTART.

**Validar:**
- Mismo kill inmediato del proceso Gradle que en STOP.
- `stopAllServices(false)` completa antes de que `startAllServices()` vuelva a correr (confirmado
  por el `synchronized` compartido — no debe verse ningún log de arranque intercalado con el de
  apagado).
- Runner termina en `RUNNING`/`ONLINE`, no en `STOPPED`.
- El gate queda libre — un job posterior debe poder reclamar sin bloqueos.

### 2.6 · Error durante la descarga del update

**Preparación:** simular indisponibilidad del endpoint de descarga (cortar red hacia el backend
tras publicar una versión nueva, o apuntar temporalmente `downloadUrl` a un recurso inválido).

**Validar:**
- 3 reintentos de descarga, todos fallan.
- `HOST_STATUS=DEGRADED` se marca, pero el Runner **sigue `ONLINE`** en el heartbeat de negocio.
- El gate se libera (`endExclusiveUpdate()` vía `finally`) — un job enviado inmediatamente después
  se reclama sin demora.
- El job-poll thread sigue vivo — no se interrumpió por este fallo.
- 60 minutos después, el siguiente ciclo reintenta la descarga automáticamente.

```
[Update] Nueva versión detectada: X.Y.Z (actual: A.B.C)
[Update] Runner libre. Aplicando actualización.
[Update] Intento 1/3 fallido: ...
[Update] Intento 2/3 fallido: ...
[Update] Intento 3/3 fallido: ...
[Update] Fallo la descarga/validacion tras 3 intentos. Host marcado DEGRADED.
(gate liberado aquí — sin log explícito, verificar por el efecto: job siguiente se reclama normal)
```

### 2.7 · VirtualMachineError simulado (escalación deliberada, no silenciosa)

**Preparación:** en un build de prueba **local, no productivo**, forzar temporalmente que el
bloque `catch (VirtualMachineError fatal)` del job-poll thread reciba un
`throw new OutOfMemoryError("prueba controlada")` inyectado en un punto seguro del loop (revertir
el JAR de prueba antes de desplegar a producción).

**Validar:**
- `ShutdownReason=FATAL_VM_ERROR` aparece en el log del shutdown hook.
- `requestShutdown(1, ...)` se invoca — `exitCode=1`, no 0.
- El shutdown hook corre completo: limpieza de Appium/WDA/stream server igual que en cualquier
  otro camino de salida.
- El wrapper externo reinicia el proceso.
- Runner vuelve a `ONLINE` tras el reinicio.

```
[Runner] FATAL: VirtualMachineError en job-poll thread — terminando la JVM deliberadamente (no se puede confiar en que el resto del proceso siga íntegro).
PROCESS FLOW — SHUTDOWN HOOK EJECUTADO
[PROCESS FLOW] Shutdown reason   : FATAL_VM_ERROR
[PROCESS FLOW] Exit code         : 1
[Runner] ===== RUNNER ACTIVO =====   ← tras el reinicio del wrapper
```

### 2.8 · Múltiples suites consecutivas

Encolar 4–6 suites distintas (mezcla de Smoke y suites por país/menú) para que corran una tras
otra sin intervención manual.

**Validar:**
- Cada suite envía su propio correo — ni se pierde ninguno, ni se duplica.
- El gate queda en `null` (libre) entre cada job — sin demora creciente en que el siguiente job
  sea reclamado.
- `lifecycleState` permanece `RUNNING` todo el tiempo entre suites.
- El heartbeat alterna correctamente `BUSY` ↔ `ONLINE` en cada transición.
- Conteo de hilos y memoria del proceso Runner estable entre la suite 1 y la suite 6.

### 2.9 · 48 horas de ejecución continua (resistencia)

Runner encendido 48 h continuas, con suites reales encoladas a intervalos representativos del uso
real, y al menos **2 actualizaciones de versión** publicadas durante la ventana (una que coincida
con una suite activa, otra con el Runner libre).

**Validar cada 4–6 horas:**
- Memoria del proceso (heap y no-heap) — sin tendencia de crecimiento sostenido.
- Conteo de hilos vivos — estable; sin acumulación de hilos `work-scheduler`/`job-poll` huérfanos
  de arranques fallidos.
- `workScheduler` activo — un solo pool vivo, no varios acumulados de reinicios previos.
- Todas las actualizaciones programadas se aplicaron o difirieron correctamente.
- Heartbeats ininterrumpidos cada 30 s.
- Cada suite ejecutada en la ventana produjo su correo.

---

## 3. Métricas a revisar

| Métrica | Fuente | Objetivo |
|---|---|---|
| Suites ejecutadas | Backend / Dashboard | = suites encoladas |
| Suites exitosas vs. abortadas | Backend | abortadas = 0 (salvo STOP/abort manual) |
| Correos enviados | Log `[EMAIL FLOW] Correo enviado correctamente.` | = suites ejecutadas completas |
| Correos fallidos | Log `[AllureReportSender] Email send failed` | 0 |
| Updates diferidos | Log `[Update] Runner ocupado. Actualización diferida.` | > 0 en escenario 3; se resuelven en el ciclo siguiente |
| Updates aplicados | Log `[Update] Reiniciando Runner con la versión` | = versiones publicadas durante la ventana |
| Shutdowns por motivo | Log `Shutdown reason` (AUTO_UPDATE / FATAL_VM_ERROR / UNKNOWN) | `UNKNOWN` solo ante kill externo esperado (STOP/RESTART) |
| Tiempo promedio de suite | `[Executor] [TIMING] fin Runner — cierre total` | consistente con líneas base previas |
| Tiempo promedio de update | diferencia entre `Nueva versión detectada` y `Reiniciando Runner` | segundos a pocos minutos |
| Jobs pendientes en cola | Backend | no crece de forma sostenida |
| Jobs ejecutados | Backend | = jobs pendientes procesados |
| Heartbeats recibidos | Backend | continuos, cada 30 s, sin huecos |
| Uso de memoria (proceso Runner) | `jcmd` / Activity Monitor / Task Manager | estable, sin tendencia creciente |
| Cantidad de hilos | `jstack` / `Thread.getAllStackTraces()` (ya logueado en el shutdown hook) | estable entre muestras |

---

## 4. Líneas de log exactas a buscar

Todas son literales del código actual — buscar tal cual con `grep` sobre
`automationqa-runner.log` (Runner) y la salida de Gradle (suite).

| Confirma | Línea a buscar |
|---|---|
| Launcher.execute() terminó | `testPlanExecutionFinished() — esto solo ocurre si Launcher.execute() completó normalmente` |
| SuiteMailer.close() ocurrió | `Entrando a SuiteMailer.close() — root store cerrando, fin real del run` |
| testPlanExecutionFinished() ocurrió | `Entrando a TestExecutionListener.testPlanExecutionFinished()` |
| Correo enviado | `[EMAIL FLOW] Correo enviado correctamente.` |
| Update diferido | `[Update] Runner ocupado. Actualización diferida.` |
| Update aplicado | `[Update] Reiniciando Runner con la versión` |
| requestShutdown() invocado | `PROCESS FLOW — SHUTDOWN HOOK EJECUTADO` + línea `Shutdown reason` |
| Shutdown hook corrió | `[PROCESS FLOW] Stack traces de TODOS los hilos vivos en este instante:` |
| stopAllServices() completó | `RunnerAgent.stopAllServices(jvmExit=...) END — Runner considera la(s) ejecución(es) finalizada(s).` |
| Runner ONLINE de nuevo | `[Runner] ===== RUNNER ACTIVO =====` seguido de `Heartbeat: ... \| ONLINE \|` |

> **Señal de regresión — no debería aparecer nunca:**
> `[EMAIL FLOW] JVM shutdown hook: SuiteMailer.close() NUNCA fue invocado aunque el run sí inició`
> — si esta línea aparece en cualquier escenario, la causa raíz volvió a manifestarse.

---

## 5. Criterios de aceptación

- [ ] 100% de las suites lanzadas en los escenarios 1, 3 y 8 terminan completas (sin cortes a mitad).
- [ ] 100% de esas suites envían correo — cero apariciones de la señal de regresión de §4.
- [ ] Cero `destroyForcibly()` sobre un proceso Gradle vivo fuera de STOP/RESTART/abort explícitos (escenario 3).
- [ ] Cero updates aplicados mientras el gate reporta un job activo.
- [ ] El gate vuelve a `null` tras cada ciclo — ningún job posterior espera más de un intervalo de poll normal.
- [ ] STOP y RESTART producen la misma secuencia de log que antes de esta investigación.
- [ ] Memoria y conteo de hilos estables al final de las 48 h (escenario 9) frente a la muestra inicial.
- [ ] Todo `Shutdown reason` registrado corresponde a la causa real (`AUTO_UPDATE`, `FATAL_VM_ERROR` solo en la prueba inyectada del escenario 7, `UNKNOWN` solo ante STOP/RESTART/kill externo).

---

## 6. Checklist QA

- [ ] Escenarios 1–8 ejecutados y documentados con logs adjuntos.
- [ ] Escenario 7 ejecutado solo en build de prueba, JAR productivo intacto.
- [ ] Capturas/adjuntos de correo real recibido guardados como evidencia.
- [ ] Comparación línea a línea de logs de STOP/RESTART contra una corrida de referencia previa a esta investigación.
- [ ] Reporte de discrepancias (si las hay) antes de firmar el paso a producción.

## 7. Checklist producción

- [ ] Escenario 9 (48 h) completado sin hallazgos críticos.
- [ ] Versión validada es la misma que se va a publicar como "nueva versión" real.
- [ ] Plan de rollback confirmado: `automationqa-runner.jar.bak` disponible y restaurable manualmente.
- [ ] Alertas configuradas para la señal de regresión de §4 en el agregador de logs, si existe.
- [ ] Ventana de despliegue acordada con el equipo (evitar publicar una versión nueva a mitad de una Smoke de producción real).

---

## 8. Riesgos residuales

- **Ventana de visibilidad del update:** mientras el gate está en `UPDATE` (descarga en curso), el
  heartbeat sigue reportando `ONLINE`, no un estado distinto — no afecta el comportamiento, solo
  la precisión de lo que ve el dashboard durante esa ventana breve.
- **`RESTARTING` como valor de enum sin usar:** inofensivo, documentado en la auditoría de la
  máquina de estados — no se tocó porque no causa comportamiento incorrecto.
- **Doble RESTART consecutivo:** técnicamente posible si el operador dispara el comando dos veces
  muy seguido; ambos se serializan de forma segura por el `synchronized` compartido, pero el
  Runner ejecutaría dos ciclos completos de apagado/arranque en vez de uno — desperdicio, no
  incorrección.
- **Dependencia del wrapper externo:** toda la recuperación tras `requestShutdown()` depende de
  que el script de arranque (`run-runner.sh`/`.bat`) realmente reinicie el proceso — esta
  validación no cubre ese script en sí, se asume ya probado.

---

## 9. Qué monitorear la primera semana en producción

- Cada update real publicado: confirmar si se aplicó de inmediato o se difirió, y que en este
  último caso se aplicó en el ciclo siguiente sin intervención.
- Tasa de correos enviados vs. suites ejecutadas — debe mantenerse en 100% día a día.
- Cualquier aparición de `ShutdownReason=UNKNOWN` fuera de un STOP/RESTART deliberado — indicaría
  un kill externo no atribuido que vale la pena investigar (SIGTERM de infraestructura, OOM del
  sistema operativo, etc.).
- Memoria y conteo de hilos del proceso Runner, muestreados diariamente, comparados contra la
  línea base del escenario 9.
- Cualquier suite que termine sin la línea de correo enviado — tratar como incidente inmediato.
