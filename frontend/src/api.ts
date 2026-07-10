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

import type { LogLevel, ExecutionSummary, Runner, RunnerDevice, PhysicalDevice, DeviceAppConfig, VideoSuiteSummary, VideoQueryResult } from './types'

// ─── Base URL ─────────────────────────────────────────────────────────────────

export const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

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

  es.addEventListener('status', (e: MessageEvent) => {
    try {
      const { status } = JSON.parse(e.data) as { status: string }
      if (status === 'FINALIZING') addLog('INFO', '⏳ Post-procesamiento activo — limpiando dispositivo, generando reporte…')
      if (status === 'ABORTING')   addLog('WARN', '⛔ Abortando ejecución…')
    } catch { /* ignore malformed events */ }
  })

  es.addEventListener('done', () => {
    const icon = failed > 0 ? '❌' : '✅'
    addLog('INFO', `${icon} Suite finalizada — ${passed} PASSED · ${failed} FAILED · ${skipped} SKIPPED`)
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

/**
 * GET /api/hosts/{hostId}/diagnostics
 * Returns a flat host status snapshot (CAMBIO 6 — Host Diagnostics).
 */
export interface HostDiagnostics {
  hostId:          string
  status:          string   // ONLINE | DEGRADED | OFFLINE | BUSY | UNKNOWN
  jreInstalled:    boolean
  nodeInstalled:   boolean
  appiumInstalled: boolean
  adbInstalled:    boolean
  xcodeInstalled:  boolean
  iosReady:        boolean
  devicesDetected: number
  lastHeartbeat:   string | null
}

export async function getHostDiagnostics(hostId: string): Promise<HostDiagnostics> {
  return httpGet<HostDiagnostics>(`/api/hosts/${encodeURIComponent(hostId)}/diagnostics`)
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

// ─── Execution device config ──────────────────────────────────────────────────

/** GET /api/settings/execution-devices → string[] of UDIDs */
export async function getExecutionDeviceConfig(): Promise<string[]> {
  const data = await httpGet<{ devices: string[] } | string[]>('/api/settings/execution-devices')
  return Array.isArray(data) ? data : (data as { devices: string[] }).devices ?? []
}

/** POST /api/settings/execution-devices { devices: string[] } */
export async function saveExecutionDeviceConfig(udids: string[]): Promise<void> {
  await httpPost<unknown>('/api/settings/execution-devices', { devices: udids })
}

// ─── Project path settings ────────────────────────────────────────────────────

export interface ProjectPathValidation {
  gradlew:        boolean
  buildGradle:    boolean
  settingsGradle: boolean
  valid:          boolean
  checkedPath:    string
  checkedAt:      string
}

export interface ProjectPathConfig {
  path:       string
  validation: ProjectPathValidation | null
}

/** GET /api/settings/project-path */
export async function getProjectPath(): Promise<ProjectPathConfig> {
  return httpGet<ProjectPathConfig>('/api/settings/project-path')
}

/** POST /api/settings/project-path { path: string } */
export async function saveProjectPath(path: string): Promise<void> {
  await httpPost<unknown>('/api/settings/project-path', { path })
}

// ─── Device App Config ────────────────────────────────────────────────────────

/** GET /api/device-app-configs/{udid} — app config for a specific device (null if not set) */
export async function getDeviceAppConfig(udid: string): Promise<DeviceAppConfig | null> {
  try {
    return await httpGet<DeviceAppConfig>(`/api/device-app-configs/${encodeURIComponent(udid)}`)
  } catch (e) {
    if (e instanceof ApiError && e.status === 204) return null
    throw e
  }
}

/** POST /api/device-app-configs/{udid} — save app config for a device */
export async function saveDeviceAppConfig(udid: string, config: DeviceAppConfig): Promise<DeviceAppConfig> {
  return httpPost<DeviceAppConfig>(`/api/device-app-configs/${encodeURIComponent(udid)}`, config)
}

/** GET /api/device-app-configs — all configs keyed by udid */
export async function getAllDeviceAppConfigs(): Promise<Record<string, DeviceAppConfig>> {
  return httpGet<Record<string, DeviceAppConfig>>('/api/device-app-configs')
}

/** DELETE /api/device-app-configs/{udid} — remove app config for a device */
export async function deleteDeviceAppConfig(udid: string): Promise<void> {
  await httpDelete(`/api/device-app-configs/${encodeURIComponent(udid)}`)
}

// ─── Runner centralized config (single source of truth) ──────────────────────

export interface RunnerCentralConfig {
  repositoryUrl: string
  branch:        string
  projectName:   string
  configured:    boolean
}

/** GET /api/runner/config — fetched by Runner at startup and before each job */
export async function getRunnerConfig(): Promise<RunnerCentralConfig> {
  return httpGet<RunnerCentralConfig>('/api/runner/config')
}

/** POST /api/runner/config — admin override, propagates to all Runners instantly */
export async function saveRunnerConfig(
  repositoryUrl: string,
  branch: string,
  projectName: string,
): Promise<void> {
  await httpPost<unknown>('/api/runner/config', { repositoryUrl, branch, projectName })
}

// ─── Videos ───────────────────────────────────────────────────────────────────

/** GET /api/videos/suites — resumen agrupado por suite, más reciente primero */
export async function getVideoSuites(): Promise<VideoSuiteSummary[]> {
  return httpGet<VideoSuiteSummary[]>('/api/videos/suites')
}

export interface VideoQueryParams {
  suite:    string
  q?:       string
  status?:  string
  device?:  string
  env?:     string
  page?:    number
  pageSize?: number
}

/** GET /api/videos?suite=...&q=...&status=...&device=...&env=...&page=...&pageSize=... */
export async function getVideos(params: VideoQueryParams): Promise<VideoQueryResult> {
  const qs = new URLSearchParams()
  qs.set('suite', params.suite)
  if (params.q)        qs.set('q', params.q)
  if (params.status)   qs.set('status', params.status)
  if (params.device)   qs.set('device', params.device)
  if (params.env)      qs.set('env', params.env)
  qs.set('page', String(params.page ?? 0))
  qs.set('pageSize', String(params.pageSize ?? 24))
  return httpGet<VideoQueryResult>(`/api/videos?${qs.toString()}`)
}

/** DELETE /api/videos/{id} — remove a single video */
export async function deleteVideo(id: string): Promise<void> {
  await httpDelete(`/api/videos/${encodeURIComponent(id)}`)
}

/** DELETE /api/videos/suite/{suiteName} — remove all videos in a suite */
export async function deleteVideoSuite(suiteName: string): Promise<void> {
  await httpDelete(`/api/videos/suite/${encodeURIComponent(suiteName)}`)
}

/** Absolute URL to stream/download a video file. */
export function getVideoFileUrl(id: string, download = false): string {
  return `${API_URL}/api/videos/${encodeURIComponent(id)}/file?download=${download}`
}
