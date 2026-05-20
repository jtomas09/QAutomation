import { useState, useEffect } from 'react'
import { getStatus } from '../api'

/**
 * Polls GET /api/status every 10 s.
 * Returns true if the backend reports the runner pinged within the last 15 s.
 */
export function useRunnerStatus(): boolean {
  const [online, setOnline] = useState(false)

  useEffect(() => {
    let mounted = true

    const check = async () => {
      try {
        const { runnerOnline } = await getStatus()
        if (mounted) setOnline(runnerOnline)
      } catch {
        if (mounted) setOnline(false)
      }
    }

    check()
    const id = setInterval(check, 10_000)
    return () => { mounted = false; clearInterval(id) }
  }, [])

  return online
}
