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

import { useState, useEffect, useRef, useCallback } from 'react'
import { getMirrorStreamUrl, checkRunnerStreamReachable, getDeviceMirrorStatus } from '../services/mirrorService'
import type { MirrorPhase } from '../services/mirrorService'
import type { StreamState } from '../services/deviceStream'
import type { DeviceStreamData } from './useDeviceStream'

export type { MirrorPhase }

export type MirrorStreamData = DeviceStreamData & {
  /**
   * Fuerza una re-verificación inmediata del Runner en vez de esperar al
   * siguiente tick del polling interno (que puede llegar tarde en una pestaña
   * recién reactivada, por el throttling de timers en segundo plano). Lo usa
   * el watchdog de recuperación automática de DeviceMirrorPanel.
   */
  reconnect: () => void
  /**
   * Fase real del ciclo de vida de WDA para este UDID (null mientras no se ha
   * resuelto ninguna consulta todavía). Desacopla "el Runner responde" de "WDA
   * realmente produce frames" — ver IOSMirrorStateTracker en el Runner.
   */
  mirrorPhase: MirrorPhase | null
  /** Motivo real del fallo cuando mirrorPhase === 'ERROR'. */
  mirrorReason: string | null
}

const NOOP = () => {}
const IDLE: MirrorStreamData        = {
  url: null, state: 'idle', lastUpdated: 0, reconnect: NOOP, mirrorPhase: null, mirrorReason: null,
}
const RECHECK_INTERVAL_MS = 4_000   // how often to ping Runner when already connected
const RETRY_INTERVAL_MS   = 3_000   // how often to retry when Runner is offline

export function useMirrorStream(udid: string | null | undefined): MirrorStreamData {
  const [reachable, setReachable] = useState(false)
  const [state, setState]         = useState<StreamState>('idle')
  const [lastUpdated, setLastUpdated] = useState(0)
  const [mirrorPhase, setMirrorPhase]   = useState<MirrorPhase | null>(null)
  const [mirrorReason, setMirrorReason] = useState<string | null>(null)
  const timerRef     = useRef<ReturnType<typeof setInterval> | null>(null)
  const prevReachRef = useRef(false)
  const reachableRef = useRef(false)
  const udidRef       = useRef(udid)

  useEffect(() => { udidRef.current = udid }, [udid])

  const check = useCallback(async () => {
    const currentUdid = udidRef.current
    const [ok, mirrorState] = await Promise.all([
      checkRunnerStreamReachable(),
      currentUdid ? getDeviceMirrorStatus(currentUdid) : Promise.resolve(null),
    ])
    if (ok !== prevReachRef.current) {
      prevReachRef.current = ok
      setLastUpdated(Date.now())
      if (ok) console.log('[Mirror] Stream connected', { udid: currentUdid })
    }
    reachableRef.current = ok
    setReachable(ok)
    setState(ok ? 'available' : 'runner_offline')
    setMirrorPhase(mirrorState?.mirrorPhase ?? null)
    setMirrorReason(mirrorState?.reason ?? null)
  }, [])

  useEffect(() => {
    if (!udid) {
      setReachable(false)
      setState('idle')
      setLastUpdated(0)
      setMirrorPhase(null)
      setMirrorReason(null)
      prevReachRef.current = false
      reachableRef.current = false
      if (timerRef.current) clearInterval(timerRef.current)
      return
    }

    console.log('[Mirror] Starting stream for RUN device:', udid)
    setState('connecting')
    void check()

    timerRef.current = setInterval(() => { void check() }, reachableRef.current ? RECHECK_INTERVAL_MS : RETRY_INTERVAL_MS)

    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [udid, check])

  const reconnect = useCallback(() => {
    if (!udidRef.current) return
    if (timerRef.current) { clearInterval(timerRef.current); timerRef.current = null }
    void check().then(() => {
      if (!udidRef.current) return
      timerRef.current = setInterval(() => { void check() }, reachableRef.current ? RECHECK_INTERVAL_MS : RETRY_INTERVAL_MS)
    })
  }, [check])

  if (!udid) return IDLE

  return {
    url:         reachable ? getMirrorStreamUrl(udid) : null,
    state,
    lastUpdated,
    reconnect,
    mirrorPhase,
    mirrorReason,
  }
}
