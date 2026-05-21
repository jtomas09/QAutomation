import { useState, useCallback, useRef } from 'react'
import type { RunState, LogLevel, LogEntry } from '../types'
import { postRun, streamExecution, stopExecution, ApiError } from '../api'

const initState: RunState = {
  status:      'idle',
  passed:      0,
  failed:      0,
  skipped:     0,
  total:       0,
  lastRun:     null,
  logs:        [],
  activeSuite: null,
  executionId: null,
}

export function useTestRunner() {
  const [state, setState]     = useState<RunState>(initState)
  const executionIdRef        = useRef<string | null>(null)
  const closeStreamRef        = useRef<(() => void) | null>(null)

  const addLog = useCallback((level: LogLevel, message: string) => {
    const entry: LogEntry = {
      id:      Math.random().toString(36).slice(2),
      time:    new Date().toLocaleTimeString('es-MX'),
      level,
      message,
    }
    setState(prev => ({ ...prev, logs: [...prev.logs, entry] }))
  }, [])

  const runTest = useCallback(async (
    suiteId:      string,
    env:          string,
    device:       string,
    country:      string  = 'mexico',
    videoEnabled: boolean = false,
  ) => {
    setState(prev => ({
      ...prev,
      status:      'running',
      passed:      0,
      failed:      0,
      skipped:     0,
      total:       0,
      activeSuite: suiteId,
      executionId: null,
    }))
    addLog('INFO', `▶ Ejecutando suite: ${suiteId}  |  Env: ${env}  |  Device: ${device}`)

    try {
      // Step 1: POST /api/run → get executionId
      const { executionId } = await postRun({ suite: suiteId, env, device, country, videoEnabled })
      executionIdRef.current = executionId
      setState(prev => ({ ...prev, executionId }))
      addLog('INFO', `🆔 ${executionId} — En cola. Esperando runner local...`)

      // Step 2: subscribe to SSE stream for live logs
      const unsubscribe = streamExecution(
        executionId,
        addLog,
        (result) => {
          closeStreamRef.current  = null
          executionIdRef.current  = null
          setState(prev => ({
            ...prev,
            status:      'finished',
            ...result,
            lastRun:     new Date().toLocaleString('es-MX'),
            activeSuite: null,
            executionId: null,
          }))
        },
        (errMsg) => {
          closeStreamRef.current = null
          executionIdRef.current = null
          addLog('ERROR', errMsg)
          setState(prev => ({ ...prev, status: 'idle', activeSuite: null, executionId: null }))
        },
      )
      closeStreamRef.current = unsubscribe

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
    const id = executionIdRef.current
    if (id) {
      stopExecution(id).catch(console.warn)
      executionIdRef.current = null
    }
    addLog('WARN', '⛔ Ejecución abortada por el usuario')
    setState(prev => ({ ...prev, status: 'idle', activeSuite: null, executionId: null }))
  }, [addLog])

  const clearLog = useCallback(() => {
    setState(prev => ({ ...prev, logs: [] }))
  }, [])

  return { state, runTest, stopTest, clearLog }
}
