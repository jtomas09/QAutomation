/**
 * useMirrorStream — drop-in replacement for useDeviceStream.
 *
 * Returns the same { url, state, lastUpdated } shape but `url` is a direct
 * MJPEG endpoint on the Runner instead of a polling blob URL through the
 * Railway cloud backend.
 *
 * The browser keeps the HTTP connection open and renders each JPEG frame as it
 * arrives — equivalent to ~20 FPS without any polling or JavaScript timers.
 */

import { useState, useEffect, useRef } from 'react'
import { getMirrorStreamUrl, checkRunnerStreamReachable } from '../services/mirrorService'
import type { StreamState } from '../services/deviceStream'
import type { DeviceStreamData } from './useDeviceStream'

export type MirrorStreamData = DeviceStreamData

const IDLE: MirrorStreamData        = { url: null, state: 'idle', lastUpdated: 0 }
const RECHECK_INTERVAL_MS = 4_000   // how often to ping Runner when already connected
const RETRY_INTERVAL_MS   = 3_000   // how often to retry when Runner is offline

export function useMirrorStream(udid: string | null | undefined): MirrorStreamData {
  const [reachable, setReachable] = useState(false)
  const [state, setState]         = useState<StreamState>('idle')
  const [lastUpdated, setLastUpdated] = useState(0)
  const timerRef     = useRef<ReturnType<typeof setInterval> | null>(null)
  const prevReachRef = useRef(false)

  useEffect(() => {
    if (!udid) {
      setReachable(false)
      setState('idle')
      setLastUpdated(0)
      prevReachRef.current = false
      if (timerRef.current) clearInterval(timerRef.current)
      return
    }

    setState('connecting')

    const check = async () => {
      const ok = await checkRunnerStreamReachable()
      if (ok !== prevReachRef.current) {
        prevReachRef.current = ok
        setLastUpdated(Date.now())
      }
      setReachable(ok)
      setState(ok ? 'available' : 'runner_offline')
    }

    check()

    timerRef.current = setInterval(check, reachable ? RECHECK_INTERVAL_MS : RETRY_INTERVAL_MS)

    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [udid])

  if (!udid) return IDLE

  return {
    url:         reachable ? getMirrorStreamUrl(udid) : null,
    state,
    lastUpdated,
  }
}
