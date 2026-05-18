/**
 * api.ts — Cliente HTTP para QAutomation Backend (Spring Boot / Railway)
 *
 * Variable de entorno requerida:
 *   VITE_API_URL=https://qautomation-production.up.railway.app
 *
 * Endpoints:
 *   GET  /health          → "QAutomation Backend Online 🚀"
 *   GET  /api/status      → { running: boolean }
 *   GET  /api/config      → { environments, suites, devices }
 *   GET  /api/run?...     → SSE stream de logs
 *   DELETE /api/run       → aborta ejecución en curso
 */

import type { LogLevel } from './types'

// ─── Base URL ─────────────────────────────────────────────────────────────────

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

if (!API_URL) {
  console.warn('[api] VITE_API_URL no está definida. Las llamadas al backend fallarán.')
}

// ─── Error tipado ─────────────────────────────────────────────────────────────

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

// ─── Helpers internos ─────────────────────────────────────────────────────────

async function httpGet<T>(path: string): Promise<T> {
  const res = await fetch(`${API_URL}${path}`)
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    throw new ApiError(res.status, `GET ${path} → ${res.status} ${res.statusText}${body ? `: ${body}` : ''}`)
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

// ─── Health ──────────────────────────────────────────────────────────────────

/** Verifica que el backend esté activo. Devuelve el mensaje de bienvenida. */
export async function getHealth(): Promise<string> {
  return httpGet<string>('/health')
}

/** Estado actual de ejecución de tests. */
export async function getStatus(): Promise<{ running: boolean }> {
  return httpGet<{ running: boolean }>('/api/status')
}

// ─── Configuración ───────────────────────────────────────────────────────────

export interface BackendConfig {
  environments: string[]
  suites: string[]
  devices: string[]
}

/** Catálogos de entornos, suites y dispositivos desde el backend. */
export async function getConfig(): Promise<BackendConfig> {
  return httpGet<BackendConfig>('/api/config')
}

// ─── Ejecución de tests (SSE) ─────────────────────────────────────────────────

type AddLog    = (level: LogLevel, message: string) => void
type IsAborted = () => boolean

export interface RunResult {
  passed: number
  failed: number
  skipped: number
  total: number
}

/**
 * Ejecuta una suite de tests y transmite los logs en tiempo real via SSE.
 * El backend responde con eventos `log` y finaliza con `done`.
 */
export async function apiRunTest(
  suiteId: string,
  env: string,
  device: string,
  addLog: AddLog,
  isAborted: IsAborted,
): Promise<RunResult> {
  if (!API_URL) {
    addLog('ERROR', '❌ VITE_API_URL no configurada. Agrega la variable de entorno y recarga.')
    return { passed: 0, failed: 0, skipped: 0, total: 0 }
  }

  return new Promise<RunResult>((resolve, reject) => {
    const params = new URLSearchParams({ suite: suiteId, env, device })
    const url    = `${API_URL}/api/run?${params}`

    addLog('INFO', `🔌 Conectando con el backend: ${API_URL}`)

    const es = new EventSource(url)
    let passed = 0, failed = 0, skipped = 0, total = 0
    let settled = false

    function finish(result: RunResult) {
      if (settled) return
      settled = true
      es.close()
      resolve(result)
    }

    es.addEventListener('log', (e: MessageEvent) => {
      if (isAborted()) { finish({ passed, failed, skipped, total }); return }
      try {
        const { level, message } = JSON.parse(e.data) as { level: LogLevel; message: string }
        addLog(level, message)
        if (level === 'PASS') { passed++; total++ }
        if (level === 'FAIL') { failed++; total++ }
        if (level === 'SKIP') { skipped++; total++ }
      } catch {
        addLog('WARN', `Evento inesperado del servidor: ${e.data}`)
      }
    })

    es.addEventListener('done', () => {
      addLog('INFO', `✅ Suite finalizada — ${passed} PASSED · ${failed} FAILED · ${skipped} SKIPPED`)
      finish({ passed, failed, skipped, total })
    })

    es.onerror = () => {
      if (settled) return
      addLog('ERROR', `❌ Conexión SSE perdida con ${API_URL}. Verifica que Railway esté activo.`)
      settled = true
      es.close()
      reject(new ApiError(0, 'SSE connection failed'))
    }
  })
}

/** Aborta la ejecución activa en el backend. */
export async function apiStop(): Promise<void> {
  if (!API_URL) return
  try {
    await httpDelete('/api/run')
  } catch (e) {
    console.warn('[api] apiStop error:', e)
  }
}
