/**
 * mirrorService — direct Runner MJPEG stream access.
 *
 * Instead of going through the Railway cloud backend (which adds 200-400 ms per frame),
 * the frontend connects directly to the Runner's embedded HTTP server on port 8082.
 *
 * Flow: Frontend <img src="http://localhost:8082/api/device-mirror/{udid}">
 *         → Runner MJPEG loop (ADB screencap → JPEG → multipart/x-mixed-replace)
 *
 * The browser opens ONE persistent connection and renders each JPEG frame as it
 * arrives — no polling, no blob URLs, no JavaScript timers.
 *
 * Configurable via env vars:
 *   VITE_RUNNER_STREAM_PORT (default 8082)
 *   VITE_RUNNER_STREAM_HOST (default localhost)
 */

export const RUNNER_STREAM_PORT = parseInt(import.meta.env.VITE_RUNNER_STREAM_PORT ?? '8082', 10)
export const RUNNER_STREAM_HOST = import.meta.env.VITE_RUNNER_STREAM_HOST ?? 'localhost'

export interface DeviceMirrorState {
  connected:   boolean
  deviceId:    string
  isStreaming: boolean
  resolution:  string
  fps:         number
}

/** Returns the MJPEG URL for a device. Pass directly to <img src>. */
export function getMirrorStreamUrl(udid: string): string {
  return `http://${RUNNER_STREAM_HOST}:${RUNNER_STREAM_PORT}/api/device-mirror/${encodeURIComponent(udid)}`
}

/** Pings the Runner health endpoint. Returns true when the server is reachable. */
export async function checkRunnerStreamReachable(): Promise<boolean> {
  try {
    const ctrl  = new AbortController()
    const timer = setTimeout(() => ctrl.abort(), 2500)
    const res   = await fetch(
      `http://${RUNNER_STREAM_HOST}:${RUNNER_STREAM_PORT}/health`,
      { signal: ctrl.signal }
    )
    clearTimeout(timer)
    return res.ok
  } catch {
    return false
  }
}

/** Fetches live mirror state for a specific device. */
export async function getDeviceMirrorStatus(udid: string): Promise<DeviceMirrorState | null> {
  try {
    const res = await fetch(
      `http://${RUNNER_STREAM_HOST}:${RUNNER_STREAM_PORT}/api/device/status?udid=${encodeURIComponent(udid)}`
    )
    if (!res.ok) return null
    return (await res.json()) as DeviceMirrorState
  } catch {
    return null
  }
}
