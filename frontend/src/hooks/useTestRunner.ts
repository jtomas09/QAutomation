import { useState, useCallback, useRef } from 'react'
import type { RunState, LogLevel, LogEntry } from '../types'
import { postRun, streamExecution, stopExecution, ApiError } from '../api'
import { executionTrackingService } from '../services/ExecutionTrackingService'

/** Mirrors JobExecutor.extractTestName — parses "Class > method() PASSED" → "method" */
function extractTestNameFromLog(line: string): string {
  const idx = line.indexOf(' > ')
  if (idx < 0) return line.trim().slice(0, 80)
  let after = line.substring(idx + 3).trim()
  for (const suffix of [' PASSED', ' FAILED', ' SKIPPED']) {
    if (after.toUpperCase().endsWith(suffix)) { after = after.slice(0, -suffix.length).trim(); break }
  }
  if (after.endsWith('()')) after = after.slice(0, -2)
  return after || 'unknown'
}

// Tope del buffer de logs en memoria. Sin este límite, `logs` crece sin fin durante
// ejecuciones largas (cada línea de Gradle/Appium llega por SSE y se acumula para
// siempre) — el spread `[...prev.logs, entry]` copia el arreglo completo en cada
// evento, así que el costo por línea crece linealmente con el historial ya
// acumulado (O(n) por evento, O(n²) total). En una suite de varias horas esto
// alcanza cientos de miles de entradas y provoca el crash del renderer de
// Chromium (Error Code 5) por agotamiento de memoria. 2000 líneas son más que
// suficientes para inspección en vivo y para "Extraer Log" / "Log Técnico"
// (que ya solo muestran ventanas recientes en pantalla); las líneas más
// antiguas se descartan igual que en un buffer de scrollback de terminal.
const MAX_LOG_ENTRIES = 2000

const initState: RunState = {
  status:        'idle',
  passed:        0,
  failed:        0,
  skipped:       0,
  total:         0,
  totalExpected: 0,
  lastRun:       null,
  logs:          [],
  activeSuite:   null,
  executionId:   null,
}

export function useTestRunner() {
  const [state, setState]       = useState<RunState>(initState)
  const executionIdRef          = useRef<string | null>(null)
  const executionIdsRef         = useRef<string[]>([])     // all active IDs for multi-device stop
  const closeStreamRef          = useRef<(() => void) | null>(null)

  const addLog = useCallback((level: LogLevel, message: string) => {
    const entry: LogEntry = {
      id:      Math.random().toString(36).slice(2),
      time:    new Date().toLocaleTimeString('es-MX'),
      level,
      message,
    }
    setState(prev => {
      let { passed, failed, skipped, total, totalExpected } = prev
      if (level === 'PASS') { passed++; total++ }
      if (level === 'FAIL') { failed++; total++ }
      if (level === 'SKIP') { skipped++; total++ }

      if (level === 'INFO') {
        const m = message.match(/TOTAL_ESPERADO:(\d+)/)
        if (m) totalExpected = parseInt(m[1], 10)
      }

      // Buffer acotado: conserva las últimas MAX_LOG_ENTRIES líneas (ring buffer).
      // slice() sobre un arreglo ya acotado a MAX_LOG_ENTRIES es O(MAX_LOG_ENTRIES),
      // constante — ya no O(n) creciente con la duración de la ejecución.
      const logs = prev.logs.length >= MAX_LOG_ENTRIES
        ? [...prev.logs.slice(prev.logs.length - MAX_LOG_ENTRIES + 1), entry]
        : [...prev.logs, entry]

      return { ...prev, logs, passed, failed, skipped, total, totalExpected }
    })
  }, [])

  /**
   * Fires one POST /api/run per device (in parallel), then opens one SSE stream
   * per execution. Logs are prefixed with the device name when running multi-device.
   *
   * @param devices      Array of device UDIDs. Pass [] to abort early.
   * @param deviceLabels Optional friendly names parallel to `devices` array.
   */
  const runTest = useCallback(async (
    suiteId:      string,
    env:          string,
    devices:      string[],          // UDIDs — one POST per element
    country:      string  = 'mexico',
    videoEnabled: boolean = false,
    deviceLabels: string[] = [],     // display names for log prefixes
  ) => {
    if (devices.length === 0) {
      addLog('WARN', '⚠️ Sin dispositivos seleccionados')
      return
    }

    setState(prev => ({
      ...prev,
      status:        'running',
      passed:        0,
      failed:        0,
      skipped:       0,
      total:         0,
      totalExpected: 0,
      activeSuite:   suiteId,
      executionId:   null,
    }))

    const multi   = devices.length > 1
    const labelOf = (i: number) => deviceLabels[i] ?? devices[i].slice(0, 8)

    addLog('INFO', `▶ Suite: ${suiteId}  |  Env: ${env}  |  ${devices.length} dispositivo(s)`)

    try {
      // Fire all POSTs simultaneously
      const runs = await Promise.all(
        devices.map(udid => postRun({ suite: suiteId, env, device: udid, country, videoEnabled }))
      )

      const allIds = runs.map(r => r.executionId)
      executionIdsRef.current = allIds
      executionIdRef.current  = allIds[0] ?? null
      setState(prev => ({ ...prev, executionId: allIds[0] ?? null }))

      runs.forEach((r, i) =>
        addLog('INFO', `🆔 ${r.executionId}${multi ? ` → ${labelOf(i)}` : ''}`)
      )

      // Create one tracking record per device in ExecutionTrackingService so LiveExecutionPanel shows them
      const trackingIds = runs.map((r, i) => {
        const rec = executionTrackingService.createExecution({
          suiteId:     suiteId,
          suiteName:   suiteId,
          device:      labelOf(i),
          environment: env,
          country,
          cases:       [],
        })
        executionTrackingService.startExecution(rec.id)
        return rec.id
      })

      // Open one SSE stream per execution
      let finished = 0

      const unsubscribers = runs.map(({ executionId }, i) => {
        const prefix     = multi ? `[${labelOf(i)}] ` : ''
        const trackingId = trackingIds[i]
        return streamExecution(
          executionId,
          (level, message) => {
            addLog(level, `${prefix}${message}`)
            // Bridge SSE results into ExecutionTrackingService for LiveExecutionPanel
            if (level === 'PASS')
              executionTrackingService.onRunnerResult(trackingId, extractTestNameFromLog(message), 'passed', message)
            else if (level === 'FAIL')
              executionTrackingService.onRunnerResult(trackingId, extractTestNameFromLog(message), 'failed', message)
            else if (level === 'SKIP')
              executionTrackingService.onRunnerResult(trackingId, extractTestNameFromLog(message), 'skipped', message)
            else
              executionTrackingService.logActivity(trackingId, message,
                level === 'ERROR' ? 'error' : level === 'WARN' ? 'warn' : 'info')
          },
          (result) => {
            executionTrackingService.finishExecution(trackingId, result.failed > 0 ? 'failed' : 'passed')
            finished++
            if (finished === runs.length) {
              closeStreamRef.current  = null
              executionIdRef.current  = null
              executionIdsRef.current = []
              setState(prev => ({
                ...prev,
                status:      'finished',
                lastRun:     new Date().toLocaleString('es-MX'),
                activeSuite: null,
                executionId: null,
              }))
            }
          },
          (errMsg) => {
            executionTrackingService.finishExecution(trackingId, 'error')
            addLog('ERROR', errMsg)
            finished++
            if (finished === runs.length) {
              closeStreamRef.current  = null
              executionIdRef.current  = null
              executionIdsRef.current = []
              setState(prev => ({ ...prev, status: 'idle', activeSuite: null, executionId: null }))
            }
          },
        )
      })

      // Close ALL streams on stop
      closeStreamRef.current = () => {
        unsubscribers.forEach(u => u())
        trackingIds.forEach(tid => executionTrackingService.cancelExecution(tid))
      }

    } catch (err) {
      const msg = err instanceof ApiError
        ? `Error ${err.status}: ${err.message}`
        : err instanceof Error ? err.message : 'Unknown error'
      addLog('ERROR', `❌ Error al iniciar ejecución: ${msg}`)
      setState(prev => ({ ...prev, status: 'idle', activeSuite: null, executionId: null }))
    }
  }, [addLog])

  const stopTest = useCallback(() => {
    if (closeStreamRef.current) {
      closeStreamRef.current()
      closeStreamRef.current = null
    }
    // Abort all active executions on the backend
    executionIdsRef.current.forEach(id => stopExecution(id).catch(console.warn))
    executionIdsRef.current = []
    executionIdRef.current  = null
    addLog('WARN', '⛔ Ejecución abortada por el usuario')
    setState(prev => ({ ...prev, status: 'idle', activeSuite: null, executionId: null }))
  }, [addLog])

  const clearLog = useCallback(() => {
    setState(prev => ({ ...prev, logs: [] }))
  }, [])

  const attachToExecution = useCallback((executionId: string, suiteName: string) => {
    if (executionIdRef.current) return
    executionIdRef.current  = executionId
    executionIdsRef.current = [executionId]
    setState(prev => ({
      ...prev,
      status:        'running',
      passed:        0, failed: 0, skipped: 0, total: 0, totalExpected: 0,
      activeSuite:   suiteName,
      executionId,
      logs:          [],
    }))
    addLog('INFO', `📡 Ejecución programada detectada: ${executionId} — suite: ${suiteName}`)

    // Create tracking record for LiveExecutionPanel
    const rec = executionTrackingService.createExecution({
      suiteId: suiteName, suiteName, environment: 'QA', cases: [],
    })
    executionTrackingService.startExecution(rec.id)

    const unsubscribe = streamExecution(
      executionId,
      (level, message) => {
        addLog(level as Parameters<typeof addLog>[0], message)
        if (level === 'PASS')
          executionTrackingService.onRunnerResult(rec.id, extractTestNameFromLog(message), 'passed', message)
        else if (level === 'FAIL')
          executionTrackingService.onRunnerResult(rec.id, extractTestNameFromLog(message), 'failed', message)
        else if (level === 'SKIP')
          executionTrackingService.onRunnerResult(rec.id, extractTestNameFromLog(message), 'skipped', message)
        else
          executionTrackingService.logActivity(rec.id, message,
            level === 'ERROR' ? 'error' : level === 'WARN' ? 'warn' : 'info')
      },
      (result) => {
        closeStreamRef.current  = null
        executionIdRef.current  = null
        executionIdsRef.current = []
        executionTrackingService.finishExecution(rec.id, result.failed > 0 ? 'failed' : 'passed')
        setState(prev => ({
          ...prev,
          status:  'finished',
          ...result,
          lastRun: new Date().toLocaleString('es-MX'),
          activeSuite: null,
          executionId: null,
        }))
      },
      (errMsg) => {
        closeStreamRef.current  = null
        executionIdRef.current  = null
        executionIdsRef.current = []
        executionTrackingService.finishExecution(rec.id, 'error')
        addLog('ERROR', errMsg)
        setState(prev => ({ ...prev, status: 'idle', activeSuite: null, executionId: null }))
      },
    )
    closeStreamRef.current = () => {
      unsubscribe()
      executionTrackingService.cancelExecution(rec.id)
    }
  }, [addLog])

  return { state, runTest, stopTest, clearLog, attachToExecution }
}
