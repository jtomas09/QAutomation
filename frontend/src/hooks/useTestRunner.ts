import { useState, useCallback, useRef } from 'react'
import type { RunState, LogLevel, LogEntry } from '../types'
import { postRun, streamExecution, stopExecution, ApiError } from '../api'

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

      return { ...prev, logs: [...prev.logs, entry], passed, failed, skipped, total, totalExpected }
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

      // Open one SSE stream per execution
      let finished = 0

      const unsubscribers = runs.map(({ executionId }, i) => {
        const prefix = multi ? `[${labelOf(i)}] ` : ''
        return streamExecution(
          executionId,
          (level, message) => addLog(level, `${prefix}${message}`),
          () => {
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
      closeStreamRef.current = () => unsubscribers.forEach(u => u())

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

    const unsubscribe = streamExecution(
      executionId,
      addLog,
      (result) => {
        closeStreamRef.current  = null
        executionIdRef.current  = null
        executionIdsRef.current = []
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
        addLog('ERROR', errMsg)
        setState(prev => ({ ...prev, status: 'idle', activeSuite: null, executionId: null }))
      },
    )
    closeStreamRef.current = unsubscribe
  }, [addLog])

  return { state, runTest, stopTest, clearLog, attachToExecution }
}
