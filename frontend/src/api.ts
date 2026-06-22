/**
 * api.ts — HTTP client for QAutomation Backend (Spring Boot / Railway)
 *
 * Required env var:
 *   VITE_API_URL=https://qautomation-production.up.railway.app
 *
 * Endpoints:
 *   GET    /health                → "OK"
 *   GET    /api/status            → { running: boolean }
 *   GET    /api/config            → { environments, suites, devices }
 *   POST   /api/run               → { executionId, status }
 *   GET    /api/run/{id}/stream   → SSE: log / status / done events
 *   DELETE /api/run/{id}          → abort execution
 *   GET    /api/executions        → ExecutionSummary[]
 *   GET    /api/executions/{id}   → ExecutionSummary (with logs)
 *   GET    /api/jobs/next         → next PENDING job (runner)
 *   POST   /api/logs              → add log line (runner)
 *   POST   /api/results           → finalize execution (runner)
 */

import type { LogLevel, ExecutionSummary, Runner, RunnerDevice, PhysicalDevice } from './types'

// ─── Base URL ─────────────────────────────────────────────────────────────────

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

console.log('[api] API_URL:', API_URL)

if (!API_URL) {
  console.warn('[api] VITE_API_URL not defined. Backend calls will fail.')
}

// ─── Typed error ──────────────────────────────────────────────────────────────

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

// ─── Internal helpers ─────────────────────────────────────────────────────────

async function httpGet<T>(path: string): Promise<T> {
  const res = await fetch(`${API_URL}${path}`)
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new ApiError(res.status, `GET ${path} → ${res.status}${body ? `: ${body}` : ''}`)
  }
  const ct = res.headers.get('content-type') ?? ''
  return ct.includes('application/json')
    ? (res.json() as Promise<T>)
    : (res.text() as unknown as Promise<T>)
}

async function httpPost<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new ApiError(res.status, `POST ${path} → ${res.status}${text ? `: ${text}` : ''}`)
  }
  const ct = res.headers.get('content-type') ?? ''
  return ct.includes('application/json')
    ? (res.json() as Promise<T>)
    : (res.text() as unknown as Promise<T>)
}

async function httpDelete(path: string): Promise<void> {
  const res = await fetch(`${API_URL}${path}`, { method: 'DELETE' })
  if (!res.ok) throw new ApiError(res.status, `DELETE ${path} → ${res.status}`)
}

// ─── Health ───────────────────────────────────────────────────────────────────

export async function getHealth(): Promise<string> {
  return httpGet<string>('/health')
}

export async function getStatus(): Promise<{ running: boolean; runnerOnline: boolean }> {
  return httpGet<{ running: boolean; runnerOnline: boolean }>('/api/status')
}

// ─── Config ───────────────────────────────────────────────────────────────────

export interface BackendConfig {
  environments: string[]
  suites: string[]
  devices: string[]
}

export async function getConfig(): Promise<BackendConfig> {
  return httpGet<BackendConfig>('/api/config')
}

// ─── Run execution ────────────────────────────────────────────────────────────

export interface RunRequest {
  suite:        string
  env:          string
  device:       string
  country:      string
  videoEnabled?: boolean
}

export interface ExecutionStarted {
  executionId: string
  status: string
}

export interface RunResult {
  passed: number
  failed: number
  skipped: number
  total: number
}

type AddLog    = (level: LogLevel, message: string) => void

/**
 * POST /api/run — enqueues a new execution.
 * Returns immediately with {executionId, status: "PENDING"}.
 */
export async function postRun(req: RunRequest): Promise<ExecutionStarted> {
  if (!API_URL) throw new ApiError(0, 'VITE_API_URL not configured')
  return httpPost<ExecutionStarted>('/api/run', req)
}

/**
 * GET /api/run/{id}/stream — subscribes to live SSE logs for an execution.
 * Returns an unsubscribe function; call it to close the EventSource.
 */
export function streamExecution(
  executionId: string,
  addLog:      AddLog,
  onDone:      (result: RunResult) => void,
  onError:     (message: string)   => void,
): () => void {
  const url = `${API_URL}/api/run/${executionId}/stream`
  const es  = new EventSource(url)

  let passed = 0, failed = 0, skipped = 0, total = 0

  es.addEventListener('log', (e: MessageEvent) => {
    try {
      const { level, message } = JSON.parse(e.data) as { level: LogLevel; message: string }
      addLog(level, message)
      if (level === 'PASS') { passed++; total++ }
      if (level === 'FAIL') { failed++; total++ }
      if (level === 'SKIP') { skipped++; total++ }
    } catch {
      addLog('WARN', `Unexpected SSE event: ${e.data}`)
    }
  })

  es.addEventListener('done', () => {
    addLog('INFO', `✅ Suite finalizada — ${passed} PASSED · ${failed} FAILED · ${skipped} SKIPPED`)
    es.close()
    onDone({ passed, failed, skipped, total })
  })

  es.onerror = () => {
    es.close()
    onError(`❌ SSE connection lost with backend. Verify Railway is active.`)
  }

  return () => es.close()
}

/** DELETE /api/run/{id} — aborts an execution. */
export async function stopExecution(executionId: string): Promise<void> {
  if (!API_URL) return
  try { await httpDelete(`/api/run/${executionId}`) }
  catch (e) { console.warn('[api] stopExecution error:', e) }
}

// ─── Execution history ────────────────────────────────────────────────────────

export async function getExecutions(): Promise<ExecutionSummary[]> {
  return httpGet<ExecutionSummary[]>('/api/executions')
}

export async function getExecution(id: string): Promise<ExecutionSummary> {
  return httpGet<ExecutionSummary>(`/api/executions/${id}`)
}

// ─── Runner Manager ───────────────────────────────────────────────────────────

export interface RunnerStatusSummary {
  total:   number
  online:  number
  busy:    number
  runners: Runner[]
}

/** GET /api/runners — all registered runners */
export async function getRunners(): Promise<Runner[]> {
  return httpGet<Runner[]>('/api/runners')
}

/** GET /api/runners/status — runner summary + list */
export async function getRunnersStatus(): Promise<RunnerStatusSummary> {
  return httpGet<RunnerStatusSummary>('/api/runners/status')
}

/** GET /api/runners/devices — all devices across all runners */
export async function getRunnerDevices(): Promise<RunnerDevice[]> {
  return httpGet<RunnerDevice[]>('/api/runners/devices')
}

/** POST /api/runners/start — start specific runner (omit runnerId for all) */
export async function startRunner(runnerId?: string): Promise<void> {
  await httpPost('/api/runners/start', runnerId ? { runnerId } : {})
}

/** POST /api/runners/stop — stop specific runner (omit runnerId for all) */
export async function stopRunner(runnerId?: string): Promise<void> {
  await httpPost('/api/runners/stop', runnerId ? { runnerId } : {})
}

/** POST /api/runners/restart — restart specific runner (omit runnerId for all) */
export async function restartRunner(runnerId?: string): Promise<void> {
  await httpPost('/api/runners/restart', runnerId ? { runnerId } : {})
}

/**
 * GET /api/runners/{id}/diagnostics
 * Returns full component health for a specific runner (V4/V5 enterprise agent).
 */
export async function getRunnerDiagnostics(runnerId: string): Promise<RunnerDiagnostics> {
  return httpGet<RunnerDiagnostics>(`/api/runners/${encodeURIComponent(runnerId)}/diagnostics`)
}

export interface RunnerDiagnostics {
  runnerId:   string
  status:     string
  lastSeen:   string | null
  hostname:   string | null
  os:         string | null
  version:    string | null
  adb: {
    ok:                  boolean
    path:                string | null
    version:             string | null
    devicesFound:        number
    platformToolsInstalled: boolean
  }
  components: {
    jre:    { installed: boolean; version: string | null }
    node:   { installed: boolean; version: string | null }
    appium: { installed: boolean; version: string | null }
    xcode:  { installed: boolean; version: string | null }
  }
  devices: Array<{ deviceId: string; deviceName: string; platform: string; status: string }>
}

// ─── Device Farm ──────────────────────────────────────────────────────────────

/** GET /api/devices — all registered physical devices */
export async function getDevices(): Promise<PhysicalDevice[]> {
  return httpGet<PhysicalDevice[]>('/api/devices')
}

/** GET /api/devices/available?platform=ANDROID — only AVAILABLE devices, optionally filtered */
export async function getAvailableDevices(platform?: string): Promise<PhysicalDevice[]> {
  const q = platform ? `?platform=${encodeURIComponent(platform)}` : ''
  return httpGet<PhysicalDevice[]>(`/api/devices/available${q}`)
}

/** GET /api/devices/{udid} — single device by UDID */
export async function getDevice(udid: string): Promise<PhysicalDevice> {
  return httpGet<PhysicalDevice>(`/api/devices/${encodeURIComponent(udid)}`)
}

/** PUT /api/devices/status — update device status */
export async function updateDeviceStatus(udid: string, status: string): Promise<void> {
  await httpPost<void>('/api/devices/status', { udid, status })
}

/** DELETE /api/devices/{udid} — remove device from pool */
export async function removeDevice(udid: string): Promise<void> {
  await httpDelete(`/api/devices/${encodeURIComponent(udid)}`)
}
