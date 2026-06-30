/**
 * useRunnerLifecycle — React hook that subscribes to RunnerLifecycleService.
 *
 * Returns real-time runner status without each component needing to poll.
 * Re-renders only when the status actually changes (event-driven).
 */

import { useState, useEffect, useCallback } from 'react'
import {
  runnerLifecycleService,
  RunnerLifecycleStatus,
  RunnerStatusEvent,
} from '../services/RunnerLifecycleService'
import type { Runner } from '../types'

export interface UseRunnerLifecycle {
  /** Current lifecycle status */
  status:      RunnerLifecycleStatus
  /** Primary runner object (first one returned by backend) */
  runner:      Runner | null
  /** All registered runners */
  runners:     Runner[]
  /** True when runner is up and responding (ONLINE | BUSY | DEGRADED) */
  isOnline:    boolean
  /** True only when ONLINE — safe to start executions */
  isReady:     boolean
  /** True after the first successful poll — avoids false "offline" flash */
  initialized: boolean
  /** Request the runner to start */
  startRunner:   (runnerId?: string) => Promise<void>
  /** Request the runner to stop */
  stopRunner:    (runnerId?: string) => Promise<void>
  /** Request a full restart cycle */
  restartRunner: (runnerId?: string) => Promise<void>
}

export function useRunnerLifecycle(): UseRunnerLifecycle {
  const [status,  setStatus]  = useState<RunnerLifecycleStatus>(
    () => runnerLifecycleService.status
  )
  const [runner,  setRunner]  = useState<Runner | null>(
    () => runnerLifecycleService.runner
  )
  const [runners, setRunners] = useState<Runner[]>(
    () => runnerLifecycleService.runners
  )
  const [initialized, setInitialized] = useState(
    () => runnerLifecycleService.initialized
  )

  useEffect(() => {
    const handler = (e: Event) => {
      const ce = e as CustomEvent<RunnerStatusEvent>
      setStatus(ce.detail.status)
      setRunner(ce.detail.runner)
      setRunners(ce.detail.runners)
      setInitialized(true)
    }
    window.addEventListener('qa:runner:status', handler)
    return () => window.removeEventListener('qa:runner:status', handler)
  }, [])

  const startRunner   = useCallback((id?: string) => runnerLifecycleService.startRunner(id),   [])
  const stopRunner    = useCallback((id?: string) => runnerLifecycleService.stopRunner(id),    [])
  const restartRunner = useCallback((id?: string) => runnerLifecycleService.restartRunner(id), [])

  return {
    status,
    runner,
    runners,
    isOnline:    runnerLifecycleService.isOnline(),
    isReady:     runnerLifecycleService.isReady(),
    initialized,
    startRunner,
    stopRunner,
    restartRunner,
  }
}
