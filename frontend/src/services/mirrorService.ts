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
 * ── HTTPS (contenido mixto) ──────────────────────────────────────────────────
 * Cuando el Dashboard se sirve por HTTPS (p.ej. Railway), los navegadores
 * modernos auto-suben (o de plano bloquean, sin fallback) cualquier recurso
 * http:// solicitado desde una página https:// — el Mirror pedía
 * http://localhost:8082 y el navegador lo bloqueaba en silencio, sin ningún
 * error visible en el panel (confirmado: el Runner nunca veía una sola conexión
 * real de navegador, solo pruebas locales). runnerBaseUrl() elige el protocolo
 * según cómo se sirve la propia página — HTTPS contra el puerto HTTPS opcional
 * del Runner (ver DeviceStreamServer.startHttpsIfAvailable(), puerto+1, solo
 * activo si existe un certificado local), HTTP contra el puerto de siempre.
 *
 * Configurable via env vars:
 *   VITE_RUNNER_STREAM_PORT       (default 8082)
 *   VITE_RUNNER_STREAM_HTTPS_PORT (default 8083 — puerto HTTP + 1)
 *   VITE_RUNNER_STREAM_HOST       (default localhost)
 */

export const RUNNER_STREAM_PORT       = parseInt(import.meta.env.VITE_RUNNER_STREAM_PORT ?? '8082', 10)
export const RUNNER_STREAM_HTTPS_PORT = parseInt(import.meta.env.VITE_RUNNER_STREAM_HTTPS_PORT ?? String(RUNNER_STREAM_PORT + 1), 10)
export const RUNNER_STREAM_HOST       = import.meta.env.VITE_RUNNER_STREAM_HOST ?? 'localhost'

/**
 * Base URL del Runner — https:// (puerto HTTPS) si la página actual es https:,
 * http:// si no. Exportada para que recordingService.ts (mismo servidor, mismo
 * problema de contenido mixto) la reutilice en vez de duplicar la detección.
 */
export function runnerBaseUrl(): string {
  const isSecurePage = typeof window !== 'undefined' && window.location.protocol === 'https:'
  return isSecurePage
    ? `https://${RUNNER_STREAM_HOST}:${RUNNER_STREAM_HTTPS_PORT}`
    : `http://${RUNNER_STREAM_HOST}:${RUNNER_STREAM_PORT}`
}

/**
 * Fase observable del ciclo de vida de WDA — ver IOSMirrorStateTracker (Runner).
 * ERROR es TERMINAL: WdaLaunchService (Runner) no reintenta automáticamente una
 * vez alcanzado — solo retryMirrorLaunch() (acción explícita del usuario) lo saca
 * de ese estado.
 */
export type MirrorPhase =
  | 'DEVICE_DISCONNECTED'
  | 'DEVICE_DETECTED'
  | 'INITIALIZING_WDA'
  | 'BUILDING_WDA'
  | 'STARTING_WDA'
  | 'VERIFYING_WDA'
  | 'MIRROR_ACTIVE'
  | 'ERROR'

export interface DeviceMirrorState {
  connected:   boolean
  deviceId:    string
  isStreaming: boolean
  resolution:  string
  fps:         number
  /** Para Android siempre es MIRROR_ACTIVE (conectado) o DEVICE_DISCONNECTED — no tiene fases WDA. */
  mirrorPhase: MirrorPhase
  /** Motivo real del fallo cuando mirrorPhase === 'ERROR' (p.ej. "xcodebuild failed with code 65"). */
  reason: string | null
}

/** Returns the MJPEG URL for a device. Pass directly to <img src>. */
export function getMirrorStreamUrl(udid: string): string {
  return `${runnerBaseUrl()}/api/device-mirror/${encodeURIComponent(udid)}`
}

/** Pings the Runner health endpoint. Returns true when the server is reachable. */
export async function checkRunnerStreamReachable(): Promise<boolean> {
  try {
    const ctrl  = new AbortController()
    const timer = setTimeout(() => ctrl.abort(), 2500)
    const res   = await fetch(
      `${runnerBaseUrl()}/health`,
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
      `${runnerBaseUrl()}/api/device/status?udid=${encodeURIComponent(udid)}`
    )
    if (!res.ok) return null
    return (await res.json()) as DeviceMirrorState
  } catch {
    return null
  }
}

/**
 * Saca a un UDID del estado ERROR terminal en WdaLaunchService (Runner) — la
 * ÚNICA forma de reintentar un lanzamiento de WDA que falló. Llamar solo desde
 * una acción explícita del usuario (botón "Reintentar"), nunca desde el
 * watchdog automático de reconexión del stream.
 */
export async function retryMirrorLaunch(udid: string): Promise<boolean> {
  try {
    const res = await fetch(
      `${runnerBaseUrl()}/api/device-mirror/${encodeURIComponent(udid)}/retry`,
      { method: 'POST' }
    )
    return res.ok
  } catch {
    return false
  }
}
