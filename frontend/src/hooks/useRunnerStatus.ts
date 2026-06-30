/**
 * useRunnerStatus — backwards-compatible hook.
 *
 * Delegates to RunnerLifecycleService so all consumers share a single
 * 5-second polling cycle instead of each making individual API calls.
 * Returns true when the runner is actively responding (ONLINE / BUSY / DEGRADED).
 */

import { useState, useEffect } from 'react'
import {
  runnerLifecycleService,
  RunnerStatusEvent,
} from '../services/RunnerLifecycleService'

export function useRunnerStatus(): boolean {
  const [online, setOnline] = useState(() => runnerLifecycleService.isOnline())

  useEffect(() => {
    const handler = (e: Event) => {
      const ce = e as CustomEvent<RunnerStatusEvent>
      setOnline(
        ce.detail.status === 'ONLINE'  ||
        ce.detail.status === 'BUSY'    ||
        ce.detail.status === 'DEGRADED'
      )
    }
    window.addEventListener('qa:runner:status', handler)
    return () => window.removeEventListener('qa:runner:status', handler)
  }, [])

  return online
}
