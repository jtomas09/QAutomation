/**
 * ExecutionTrackingService — front-end execution lifecycle manager.
 *
 * Manages the lifecycle of suite executions started from the UI:
 *   queued → initializing → running → passed | failed | cancelled | error
 *
 * Persists to localStorage (key: qa_exec_tracking), max 50 records.
 * Dispatches window CustomEvents so Dashboard components update in real-time:
 *   qa:exec:created | qa:exec:updated | qa:exec:finished
 */

import { postRun, streamExecution } from '../api'
import type { RecordedCasePayload } from '../api'
import type { PhysicalDevice } from '../types'

// ── Types ─────────────────────────────────────────────────────────────────────

export type ExecStatus =
  | 'queued' | 'initializing' | 'running'
  | 'passed' | 'failed' | 'skipped' | 'cancelled' | 'error'

export const ACTIVE_STATUSES: ExecStatus[] = ['queued', 'initializing', 'running']
export const DONE_STATUSES:   ExecStatus[] = ['passed', 'failed', 'cancelled', 'error']

export interface CaseRun {
  caseId:      string
  caseName:    string
  status:      ExecStatus | 'pending'
  startedAt?:  string
  finishedAt?: string
  durationMs?: number
  error?:      string
  stepsPassed: number
  stepsFailed: number
  stepsTotal:  number
}

export interface ActivityEntry {
  ts:    string
  level: 'info' | 'ok' | 'error' | 'warn'
  msg:   string
}

export interface ExecutionRecord {
  id:             string
  suiteId:        string
  suiteName:      string
  appName:        string
  appPackage:     string
  platform:       string
  device:         string
  udid:           string
  runner:         string
  environment:    string
  country:        string
  status:         ExecStatus
  createdAt:      string
  startedAt?:     string
  finishedAt?:    string
  durationMs?:    number
  cases:          CaseRun[]
  totalCases:     number
  passedCases:    number
  failedCases:    number
  completedCases: number
  totalSteps:     number
  completedSteps: number
  activity:       ActivityEntry[]
  runnerId?:      string   // executionId returned by the Runner API
}

// ── Storage ───────────────────────────────────────────────────────────────────

const KEY     = 'qa_exec_tracking'
const MAX_REC = 50

function uid(): string {
  return `exec_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
}

function now(): string { return new Date().toISOString() }

// ── Service ───────────────────────────────────────────────────────────────────

class ExecutionTrackingServiceImpl {

  // ── Cache en memoria + persistencia diferida ─────────────────────────────
  //
  // CAUSA RAÍZ (hallada tras auditar por qué el Dashboard sigue degradándose
  // incluso con el buffer de logs acotado): addActivity() se invoca desde
  // useTestRunner por CADA línea SSE que no es PASS/FAIL/SKIP — es decir, por
  // la inmensa mayoría del volumen de logs del Runner (INFO/DEBUG/WARN, muy
  // verboso). Cada llamada pasaba por patch(): load() hacía un
  // JSON.parse(localStorage) completo y persist() un JSON.stringify +
  // localStorage.setItem completo, de los ≤50 registros con ≤200 actividades
  // cada uno (los mensajes de diagnóstico del Runner pueden ser largos, p.
  // ej. volcados de elementos visibles). En una ejecución de varias horas con
  // miles de líneas por hora, esto son miles de serializaciones/escrituras
  // SÍNCRONAS en el hilo principal — no es una fuga de memoria de datos (el
  // tamaño ya estaba acotado), es saturación sostenida del hilo principal por
  // I/O síncrono repetido en cada evento, agravada por el CustomEvent
  // 'qa:exec:updated' que dispara un re-render completo de LiveExecutionPanel
  // en cada una de esas llamadas.
  //
  // Fix: los registros viven en memoria (this.records) durante toda la
  // sesión — load()/patch() ya no tocan localStorage en cada llamada. La
  // escritura a localStorage se difiere (debounce de 1s) sin cambiar qué se
  // persiste ni cuándo se lee — solo CUÁNTAS VECES se serializa. flush() se
  // ejecuta también al cerrar/ocultar la pestaña para no perder la última
  // ventana de actividad.
  private records: ExecutionRecord[] | null = null
  private persistTimer: ReturnType<typeof setTimeout> | null = null

  private load(): ExecutionRecord[] {
    if (this.records === null) {
      try { this.records = JSON.parse(localStorage.getItem(KEY) ?? '[]') as ExecutionRecord[] }
      catch { this.records = [] }
      this.reconcileStaleExecutions()
    }
    return this.records;
  }

  /**
   * Reconciliación de arranque — se ejecuta una sola vez, en la primera lectura
   * de localStorage de esta sesión de pestaña.
   *
   * CAUSA RAÍZ: un ExecutionRecord solo avanza de estado (queued → running →
   * passed/failed/...) mientras la suscripción SSE dentro de la promesa de
   * runSuite() sigue viva — y esa suscripción vive en el heap de JS de la
   * pestaña, no se persiste. Si la pestaña se recarga o se cierra antes de que
   * el SSE llegue a finishExecution(), el ExecutionRecord queda escrito en
   * localStorage congelado en un status de ACTIVE_STATUSES para siempre — sin
   * ningún proceso vivo que lo vaya a completar. RunTestsPanel/getActiveExecutions()
   * lo seguían tratando como "la ejecución en curso" indefinidamente.
   *
   * Este es el único punto donde se puede distinguir con certeza "activo de
   * verdad" vs. "huérfano de una sesión anterior": si estamos leyendo
   * localStorage por primera vez en esta sesión, ningún run de ESTA sesión ha
   * podido crear un registro todavía — cualquier "activo" ya presente aquí es,
   * por construcción, de una sesión anterior. No requiere umbral de tiempo ni
   * heurística: es una garantía estructural, no una suposición.
   */
  private reconcileStaleExecutions(): void {
    if (this.records === null) return
    let changed = false
    this.records = this.records.map(r => {
      if (!(ACTIVE_STATUSES as string[]).includes(r.status)) return r
      changed = true
      return {
        ...r,
        status:     'error' as const,
        finishedAt: r.finishedAt ?? now(),
        activity:   [...r.activity.slice(-199), {
          ts: now(), level: 'warn' as const,
          msg: 'Ejecución marcada como finalizada — quedó activa de una sesión anterior '
             + '(la pestaña se cerró o recargó antes de recibir el resultado final del Runner).',
        }],
      }
    })
    if (changed) this.persist(this.records)
  }

  private persist(records: ExecutionRecord[]): void {
    // Mantiene el mismo contrato (máximo MAX_REC registros) — solo cambia
    // cuándo se escribe físicamente a localStorage, no qué se guarda.
    this.records = records.slice(-MAX_REC)
    if (this.persistTimer) return
    this.persistTimer = setTimeout(() => {
      this.persistTimer = null
      this.flush()
    }, 1000)
  }

  /** Escribe el estado actual a localStorage de inmediato (usado por el debounce y por beforeunload/pagehide). */
  private flush(): void {
    if (this.records === null) return
    try { localStorage.setItem(KEY, JSON.stringify(this.records)) } catch { /* cuota excedida, etc. — no crítico */ }
  }

  /** Fuerza el flush inmediato de cualquier escritura diferida pendiente (p. ej. antes de cerrar la pestaña). */
  flushPending(): void {
    if (this.persistTimer) { clearTimeout(this.persistTimer); this.persistTimer = null }
    this.flush()
  }

  private patch(id: string, updater: (r: ExecutionRecord) => ExecutionRecord): ExecutionRecord | null {
    const records = this.load()
    const idx     = records.findIndex(r => r.id === id)
    if (idx < 0) return null
    records[idx] = updater(records[idx])
    this.persist(records)
    this.dispatch('qa:exec:updated', records[idx])
    return records[idx]
  }

  // 'qa:exec:updated' puede dispararse por cada línea de log (potencialmente
  // decenas por segundo) — LiveExecutionPanel ignora el detail y simplemente
  // vuelve a leer el estado completo en cada evento, así que coalescer varios
  // disparos consecutivos en uno solo (máx. ~4/s) no pierde ninguna
  // actualización: el próximo evento (o 'qa:exec:finished' al terminar)
  // siempre refleja el estado más reciente. 'created'/'finished' son poco
  // frecuentes y se despachan sin throttle.
  private updatedDispatchPending = false

  private dispatch(event: string, detail: unknown): void {
    if (event !== 'qa:exec:updated') {
      try { window.dispatchEvent(new CustomEvent(event, { detail, bubbles: false })) }
      catch { /* non-critical */ }
      return
    }
    if (this.updatedDispatchPending) return
    this.updatedDispatchPending = true
    try { window.dispatchEvent(new CustomEvent(event, { detail, bubbles: false })) }
    catch { /* non-critical */ }
    setTimeout(() => { this.updatedDispatchPending = false }, 250)
  }

  private addActivity(id: string, msg: string, level: ActivityEntry['level'] = 'info'): void {
    this.patch(id, r => ({
      ...r,
      activity: [...r.activity.slice(-199), { ts: now(), level, msg }],
    }))
  }

  // ── Public API ──────────────────────────────────────────────────────────────

  createExecution(opts: {
    suiteId:     string
    suiteName:   string
    appName?:    string
    appPackage?: string
    platform?:   string
    device?:     string
    udid?:       string
    runner?:     string
    environment: string
    country?:    string
    cases: { caseId: string; caseName: string; stepsTotal: number }[]
  }): ExecutionRecord {
    const rec: ExecutionRecord = {
      id:             uid(),
      suiteId:        opts.suiteId,
      suiteName:      opts.suiteName,
      appName:        opts.appName     ?? '',
      appPackage:     opts.appPackage  ?? '',
      platform:       opts.platform    ?? '',
      device:         opts.device      ?? 'Sin dispositivo',
      udid:           opts.udid        ?? '',
      runner:         opts.runner      ?? window.location.hostname,
      environment:    opts.environment,
      country:        opts.country     ?? 'mexico',
      status:         'queued',
      createdAt:      now(),
      cases:          opts.cases.map(c => ({
        caseId:      c.caseId,
        caseName:    c.caseName,
        status:      'pending' as const,
        stepsPassed: 0,
        stepsFailed: 0,
        stepsTotal:  c.stepsTotal,
      })),
      totalCases:     opts.cases.length,
      passedCases:    0,
      failedCases:    0,
      completedCases: 0,
      totalSteps:     opts.cases.reduce((s, c) => s + c.stepsTotal, 0),
      completedSteps: 0,
      activity:       [{ ts: now(), level: 'info', msg: `Ejecución creada — Suite: ${opts.suiteName}` }],
    }
    const records = this.load()
    records.push(rec)
    this.persist(records)
    this.dispatch('qa:exec:created', rec)
    return rec
  }

  startExecution(id: string): void {
    this.patch(id, r => ({ ...r, status: 'running', startedAt: now() }))
    this.addActivity(id, 'Runner conectado — iniciando ejecución', 'info')
  }

  /** Advance the next pending case to 'running' */
  private startNextCase(id: string): void {
    this.patch(id, r => {
      const idx = r.cases.findIndex(c => c.status === 'pending')
      if (idx < 0) return r
      const updated = [...r.cases]
      updated[idx] = { ...updated[idx], status: 'running', startedAt: now() }
      return { ...r, cases: updated }
    })
  }

  /** Mark the currently running case as passed or failed */
  private finishCurrentCase(id: string, status: 'passed' | 'failed', error?: string): void {
    this.patch(id, r => {
      const idx = r.cases.findIndex(c => c.status === 'running')
      if (idx < 0) return r
      const finAt = now()
      const durMs = r.cases[idx].startedAt
        ? Date.now() - new Date(r.cases[idx].startedAt!).getTime()
        : 0
      const updated  = [...r.cases]
      updated[idx]   = { ...updated[idx], status, finishedAt: finAt, durationMs: durMs, error }
      const passed   = updated.filter(c => c.status === 'passed').length
      const failed   = updated.filter(c => c.status === 'failed').length
      const completed = passed + failed
      return { ...r, cases: updated, passedCases: passed, failedCases: failed, completedCases: completed }
    })
  }

  /** Called by SSE log handler for each PASS log line */
  onRunnerPass(id: string, msg: string): void {
    this.finishCurrentCase(id, 'passed')
    this.startNextCase(id)
    this.addActivity(id, msg, 'ok')
  }

  /** Called by SSE log handler for each FAIL log line */
  onRunnerFail(id: string, msg: string): void {
    this.finishCurrentCase(id, 'failed', msg)
    this.startNextCase(id)
    this.addActivity(id, msg, 'error')
  }

  /**
   * Dynamic case tracking — called when test results arrive via SSE and cases are
   * not known upfront. Creates a new case entry each time a PASS/FAIL/SKIP line arrives.
   * Used by useTestRunner which doesn't have the case list before execution starts.
   */
  onRunnerResult(id: string, caseName: string, status: 'passed' | 'failed' | 'skipped', msg: string): void {
    const level: ActivityEntry['level'] = status === 'passed' ? 'ok' : status === 'failed' ? 'error' : 'warn'
    this.patch(id, r => {
      const entry: CaseRun = {
        caseId:      caseName,
        caseName,
        status,
        finishedAt:  now(),
        stepsPassed: status === 'passed' ? 1 : 0,
        stepsFailed: status === 'failed' ? 1 : 0,
        stepsTotal:  1,
      }
      // Deduplicate by caseName (retry scenario)
      const existing = r.cases.findIndex(c => c.caseName === caseName)
      const cases = existing >= 0
        ? r.cases.map((c, i) => i === existing ? entry : c)
        : [...r.cases, entry]
      const passed    = cases.filter(c => c.status === 'passed').length
      const failed    = cases.filter(c => c.status === 'failed').length
      const skipped   = cases.filter(c => c.status === 'skipped' as string).length
      return {
        ...r,
        cases,
        totalCases:     cases.length,
        passedCases:    passed,
        failedCases:    failed,
        completedCases: passed + failed + skipped,
      }
    })
    this.addActivity(id, msg, level)
  }

  logActivity(id: string, msg: string, level: ActivityEntry['level'] = 'info'): void {
    this.addActivity(id, msg, level)
  }

  finishExecution(id: string, status: ExecStatus): void {
    this.patch(id, r => {
      const finAt  = now()
      const durMs  = r.startedAt ? Date.now() - new Date(r.startedAt).getTime() : 0
      const cases  = r.cases.map(c => c.status === 'pending' || c.status === 'running'
        ? { ...c, status: status === 'passed' ? 'passed' as const : 'failed' as const }
        : c)
      return {
        ...r, status, finishedAt: finAt, durationMs: durMs, cases,
        passedCases:    cases.filter(c => c.status === 'passed').length,
        failedCases:    cases.filter(c => c.status === 'failed').length,
        completedCases: cases.length,
      }
    })
    this.addActivity(id, `Ejecución finalizada — estado: ${status.toUpperCase()}`,
      status === 'passed' ? 'ok' : status === 'cancelled' ? 'warn' : 'error')
    this.dispatch('qa:exec:finished', { id, status })
  }

  cancelExecution(id: string): void { this.finishExecution(id, 'cancelled') }

  // ── Queries ─────────────────────────────────────────────────────────────────

  getActiveExecutions(): ExecutionRecord[] {
    return this.load().filter(r => (ACTIVE_STATUSES as string[]).includes(r.status))
  }

  getExecution(id: string): ExecutionRecord | null {
    return this.load().find(r => r.id === id) ?? null
  }

  getHistory(limit = 20): ExecutionRecord[] {
    return this.load().slice(-limit).reverse()
  }

  clearHistory(): void {
    this.persist(this.load().filter(r => (ACTIVE_STATUSES as string[]).includes(r.status)))
    this.dispatch('qa:exec:updated', null)
  }

  // ── Trigger a suite execution ────────────────────────────────────────────────

  /**
   * Full execution flow:
   *   1. Create local record (visible in Dashboard immediately)
   *   2. Try to call the Runner API (POST /api/run)
   *   3. If Runner responds: stream SSE events to update case states
   *   4. If Runner unavailable: mark as error
   */
  async runSuite(opts: {
    suiteId:     string
    suiteName:   string
    appName?:    string
    appPackage?: string
    platform?:   string
    device?:     PhysicalDevice | null
    environment: string
    country?:    string
    cases: { caseId: string; caseName: string; stepsTotal: number }[]
    /**
     * Suite grabada en Record Studio — un RecordedCasePayload por TestCase de
     * la suite (mismo generador que el caso individual, ver
     * SuitesPage.handleExecuteConfirm). Ausente para las suites reales
     * preexistentes (Smoke/Full Suite/Regresión/etc.) — ese camino sigue
     * enviando solo `suite: suiteName` como siempre.
     */
    recordedCases?: RecordedCasePayload[]
    onNavigateToDashboard?: () => void
  }): Promise<ExecutionRecord> {
    const rec = this.createExecution({
      suiteId:     opts.suiteId,
      suiteName:   opts.suiteName,
      appName:     opts.appName,
      appPackage:  opts.appPackage,
      platform:    opts.platform ?? opts.device?.platform ?? '',
      device:      opts.device?.deviceName ?? '',
      udid:        opts.device?.udid ?? '',
      environment: opts.environment,
      country:     opts.country ?? 'mexico',
      cases:       opts.cases,
    })

    // Navigate to Dashboard so user sees the execution immediately
    opts.onNavigateToDashboard?.()

    // Mark as initializing while we try the Runner
    this.patch(rec.id, r => ({ ...r, status: 'initializing' }))
    this.addActivity(rec.id, 'Conectando con Runner…', 'info')

    try {
      console.log('[SuiteExecution] RUN payload device:',
        { name: opts.device?.deviceName ?? '', platform: opts.device?.platform ?? '', udid: opts.device?.udid ?? '' })
      const started = await postRun({
        suite:   opts.suiteName,
        env:     opts.environment,
        // UDID, no nombre visible — mismo criterio que runReadyRecordedCase()
        // (App.tsx) y que DeviceReadinessService (backend): más específico y
        // sin ambigüedad si dos dispositivos comparten nombre visible.
        device:  opts.device?.udid ?? '',
        // Solo informativos — para que [RunDevice] y los mensajes de rechazo
        // muestren el nombre, no participan en la resolución real del device.
        deviceName:     opts.device?.deviceName,
        devicePlatform: opts.device?.platform,
        country: opts.country ?? 'mexico',
        suiteId:       opts.recordedCases?.length ? opts.suiteId : undefined,
        recordedCases: opts.recordedCases?.length ? opts.recordedCases : undefined,
      })

      // Runner accepted — start streaming
      console.log(`[SuiteExecution] RUN created: ${started.executionId}`)
      console.log(`[SuiteExecution] Target device: name=${opts.device?.deviceName ?? ''} `
        + `platform=${opts.device?.platform ?? ''} udid=${opts.device?.udid ?? ''}`)
      this.patch(rec.id, r => ({ ...r, status: 'running', startedAt: now(), runnerId: started.executionId }))
      this.addActivity(rec.id, `Runner iniciando — ID: ${started.executionId}`, 'ok')
      this.startNextCase(rec.id)

      const unsub = streamExecution(
        started.executionId,
        (level, msg) => {
          if (level === 'PASS') this.onRunnerPass(rec.id, msg)
          else if (level === 'FAIL') this.onRunnerFail(rec.id, msg)
          else this.addActivity(rec.id, msg, level === 'ERROR' ? 'error' : level === 'WARN' ? 'warn' : 'info')
        },
        (result) => {
          unsub()
          // total===0 (ni un PASS/FAIL/SKIP) nunca es un éxito real — significa que el
          // Runner no llegó a reportar ningún resultado (device/Appium/WDA falló antes
          // de que Gradle/JUnit corriera algo). Antes caía en "passed" por default solo
          // porque failed===0, mostrando en Dashboard una falla total de infraestructura
          // como si fuera un COMPLETADO exitoso.
          if (result.total === 0) {
            this.addActivity(rec.id,
              'La ejecución terminó sin reportar ningún resultado — el dispositivo/Appium probablemente no llegó a estar listo.',
              'error')
            this.finishExecution(rec.id, 'error')
          } else {
            this.finishExecution(rec.id, result.failed > 0 ? 'failed' : 'passed')
          }
        },
        (errMsg) => {
          unsub()
          this.addActivity(rec.id, errMsg, 'error')
          this.finishExecution(rec.id, 'error')
        },
      )
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      this.addActivity(rec.id, `Runner no disponible: ${msg}`, 'error')
      this.finishExecution(rec.id, 'error')
    }

    return rec
  }
}

export const executionTrackingService = new ExecutionTrackingServiceImpl()

// La persistencia a localStorage está diferida (debounce de 1s) para no serializar
// en cada línea de log — esto fuerza una escritura final si la pestaña se cierra u
// oculta con un flush pendiente, para no perder la última ventana de actividad.
if (typeof window !== 'undefined') {
  const flushNow = () => executionTrackingService.flushPending()
  window.addEventListener('pagehide', flushNow)
  window.addEventListener('beforeunload', flushNow)
}
