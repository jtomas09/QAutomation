import { useState, useCallback, useRef } from 'react'
import type { RunState, LogLevel, LogEntry } from '../types'
import { apiRunTest } from '../api'

const initState: RunState = {
  status: 'idle',
  passed: 0, failed: 0, skipped: 0, total: 0,
  lastRun: null,
  logs: [],
  activeSuite: null,
}

export function useTestRunner() {
  const [state, setState] = useState<RunState>(initState)
  const abortRef = useRef(false)

  const addLog = useCallback((level: LogLevel, message: string) => {
    const entry: LogEntry = {
      id: Math.random().toString(36).slice(2),
      time: new Date().toLocaleTimeString('es-MX'),
      level,
      message,
    }
    setState(prev => ({ ...prev, logs: [...prev.logs, entry] }))
  }, [])

  const runTest = useCallback(async (
    suiteId: string,
    env: string,
    device: string,
  ) => {
    abortRef.current = false
    setState(prev => ({
      ...prev,
      status: 'running',
      passed: 0, failed: 0, skipped: 0, total: 0,
      activeSuite: suiteId,
    }))
    addLog('INFO', `▶ Ejecutando suite: ${suiteId}  |  Env: ${env}  |  Device: ${device}`)

    const result = await apiRunTest(suiteId, env, device, addLog, () => abortRef.current)

    setState(prev => ({
      ...prev,
      status: 'finished',
      ...result,
      lastRun: new Date().toLocaleString('es-MX'),
      activeSuite: null,
    }))
  }, [addLog])

  const stopTest = useCallback(() => {
    abortRef.current = true
    addLog('WARN', '⛔ Ejecución abortada por el usuario')
    setState(prev => ({ ...prev, status: 'idle', activeSuite: null }))
  }, [addLog])

  const clearLog = useCallback(() => {
    setState(prev => ({ ...prev, logs: [] }))
  }, [])

  return { state, runTest, stopTest, clearLog }
}
