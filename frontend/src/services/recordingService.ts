/**
 * recordingService — direct Runner recording engine access (port 8082).
 *
 * Mirrors the same host/port pattern as mirrorService: frontend talks directly
 * to the Runner's embedded HTTP server, bypassing the cloud backend.
 *
 * Endpoints (all on the Runner):
 *   POST /api/recording/start            → start session
 *   POST /api/recording/stop/{sessionId} → stop session
 *   POST /api/recording/action/{sessionId} → execute action, get step back
 *   GET  /api/recording/events/{sessionId} → SSE stream for physical taps
 */

import { runnerBaseUrl } from './mirrorService'

// Se resuelve por llamada (no como constante de módulo) para que siempre refleje
// el protocolo de la página actual — ver runnerBaseUrl() en mirrorService.ts.
function base(): string { return runnerBaseUrl() }

// ── Types ─────────────────────────────────────────────────────────────────────

export interface RecordingSession {
  sessionId:    string
  deviceWidth:  number
  deviceHeight: number
}

export type RecordingAction =
  | { action: 'tap';         x: number;  y: number }
  | { action: 'double_tap';  x: number;  y: number }
  | { action: 'long_press';  x: number;  y: number }
  | { action: 'swipe';       x1: number; y1: number; x2: number; y2: number }
  | { action: 'input';       text: string }
  | { action: 'key';         key: string }

// ── API ───────────────────────────────────────────────────────────────────────

export async function startRecording(udid: string): Promise<RecordingSession> {
  const res = await fetch(`${base()}/api/recording/start`, {
    method:  'POST',
    headers: { 'Content-Type': 'application/json' },
    body:    JSON.stringify({ udid }),
  })
  if (!res.ok) throw new Error(`startRecording failed: ${res.status}`)
  return res.json() as Promise<RecordingSession>
}

export async function stopRecording(sessionId: string): Promise<void> {
  try {
    await fetch(`${base()}/api/recording/stop/${encodeURIComponent(sessionId)}`, {
      method: 'POST',
    })
  } catch {
    // ignore — session may have already been cleaned up
  }
}

/**
 * Execute an action on the device and return the resulting recorded step.
 * Returns null when the session is gone or the action fails.
 */
export async function sendAction(
  sessionId: string,
  action: RecordingAction,
): Promise<unknown | null> {
  try {
    const res = await fetch(
      `${base()}/api/recording/action/${encodeURIComponent(sessionId)}`,
      {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify(action),
      },
    )
    if (!res.ok) return null
    return res.json()
  } catch {
    return null
  }
}

/**
 * Subscribe to physical-device events (getevent-detected taps/long-presses).
 * Returns an unsubscribe function — call it to close the EventSource.
 *
 * The onStep callback fires for every step pushed by the getevent listener.
 * The initial "connected" handshake event is filtered out.
 */
export function subscribePhysicalEvents(
  sessionId: string,
  onStep:  (step: unknown) => void,
  onError?: (err: Event) => void,
): () => void {
  const es = new EventSource(
    `${base()}/api/recording/events/${encodeURIComponent(sessionId)}`,
  )
  es.onmessage = (e) => {
    try {
      const data = JSON.parse(e.data as string)
      // Filter the initial handshake event
      if ((data as { type?: string }).type === 'connected') return
      onStep(data)
    } catch {
      // skip malformed events
    }
  }
  if (onError) es.onerror = onError
  return () => es.close()
}
