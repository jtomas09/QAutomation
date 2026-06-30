/**
 * useRecordingSession — manages the lifecycle of a Runner recording session.
 *
 * Wraps recordingService to provide React-friendly state and callbacks.
 * The hook is intentionally thin: all device-coord math happens in RecordStudio.
 */

import { useState, useRef, useCallback } from 'react'
import {
  startRecording,
  stopRecording,
  sendAction,
  subscribePhysicalEvents,
} from '../services/recordingService'
import type { RecordingAction } from '../services/recordingService'

export interface UseRecordingSessionResult {
  sessionId:    string | null
  deviceWidth:  number
  deviceHeight: number
  /** Start a session for the given UDID. Throws on Runner unreachable. */
  start: (udid: string) => Promise<void>
  /** Stop the active session (no-op when none active). */
  stop: () => void
  /** Execute an action; returns the step JSON or null on failure. */
  send: (action: RecordingAction) => Promise<unknown | null>
  /**
   * Register a callback for physical-device events pushed via SSE.
   * Re-registering replaces the previous callback — safe to call from effects.
   */
  onPhysicalStep: (cb: (step: unknown) => void) => void
}

export function useRecordingSession(): UseRecordingSessionResult {
  const [sessionId,    setSessionId]    = useState<string | null>(null)
  const [deviceWidth,  setDeviceWidth]  = useState(1080)
  const [deviceHeight, setDeviceHeight] = useState(1920)

  const unsubRef   = useRef<(() => void) | null>(null)
  const physCbRef  = useRef<((step: unknown) => void) | null>(null)

  const start = useCallback(async (udid: string) => {
    const session = await startRecording(udid)
    setSessionId(session.sessionId)
    setDeviceWidth(session.deviceWidth)
    setDeviceHeight(session.deviceHeight)
    // Subscribe to physical events; route them through the latest callback
    unsubRef.current = subscribePhysicalEvents(
      session.sessionId,
      (step) => physCbRef.current?.(step),
      () => { /* SSE error — session may have ended, ignore */ },
    )
  }, [])

  const stop = useCallback(() => {
    if (sessionId) stopRecording(sessionId)
    unsubRef.current?.()
    unsubRef.current = null
    setSessionId(null)
  }, [sessionId])

  const send = useCallback(
    (action: RecordingAction) => {
      if (!sessionId) return Promise.resolve(null)
      return sendAction(sessionId, action)
    },
    [sessionId],
  )

  const onPhysicalStep = useCallback((cb: (step: unknown) => void) => {
    physCbRef.current = cb
  }, [])

  return { sessionId, deviceWidth, deviceHeight, start, stop, send, onPhysicalStep }
}
