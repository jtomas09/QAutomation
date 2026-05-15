/**
 * api.ts — Capa de abstracción de API.
 *
 * FASE 1 (actual): implementación mock — todo local, sin red.
 * FASE 4: cambiar USE_REAL_API = true y configurar VITE_API_URL en .env.
 */

import type { LogLevel } from './types'

type AddLog    = (level: LogLevel, message: string) => void
type IsAborted = () => boolean

// ─── Toggle FASE 1 ↔ FASE 4 ────────────────────────────────────────────────
const USE_REAL_API = import.meta.env.VITE_API_URL !== undefined
const BASE_URL     = import.meta.env.VITE_API_URL ?? ''

// ─── Interfaz pública ────────────────────────────────────────────────────────

export async function apiRunTest(
  suiteId: string,
  env: string,
  device: string,
  addLog: AddLog,
  isAborted: IsAborted,
): Promise<{ passed: number; failed: number; skipped: number; total: number }> {
  return USE_REAL_API
    ? realRunTest(suiteId, env, device, addLog, isAborted)
    : mockRunTest(suiteId, env, device, addLog, isAborted)
}

export async function apiStop(): Promise<void> {
  if (USE_REAL_API) {
    await fetch(`${BASE_URL}/api/run`, { method: 'DELETE' })
  }
}

// ─── FASE 4: ejecución real via SSE ─────────────────────────────────────────

async function realRunTest(
  suiteId: string,
  env: string,
  device: string,
  addLog: AddLog,
  isAborted: IsAborted,
): Promise<{ passed: number; failed: number; skipped: number; total: number }> {
  return new Promise((resolve) => {
    const url = `${BASE_URL}/api/run?suite=${suiteId}&env=${env}&device=${device}`
    const es = new EventSource(url)

    let passed = 0, failed = 0, skipped = 0, total = 0

    es.addEventListener('log', (e) => {
      if (isAborted()) { es.close(); resolve({ passed, failed, skipped, total }); return }
      const { level, message } = JSON.parse(e.data) as { level: LogLevel; message: string }
      addLog(level, message)
      if (level === 'PASS') { passed++; total++ }
      if (level === 'FAIL') { failed++; total++ }
    })

    es.addEventListener('done', (e) => {
      es.close()
      resolve({ passed, failed, skipped, total })
    })

    es.onerror = () => {
      es.close()
      addLog('ERROR', 'Conexión con el backend perdida')
      resolve({ passed, failed, skipped, total })
    }
  })
}

// ─── FASE 1: mock local ──────────────────────────────────────────────────────

const sleep = (ms: number) => new Promise<void>(r => setTimeout(r, ms))

async function mockRunTest(
  suiteId: string,
  env: string,
  device: string,
  addLog: AddLog,
  isAborted: IsAborted,
): Promise<{ passed: number; failed: number; skipped: number; total: number }> {
  const steps: { delay: number; level: LogLevel; msg: string }[] = [
    { delay: 700,  level: 'INFO', msg: 'Conectando al servidor Appium...' },
    { delay: 1100, level: 'INFO', msg: `Appium: http://127.0.0.1:4723  |  mode=${env}` },
    { delay: 600,  level: 'INFO', msg: `Suite [${suiteId}] — device: ${device}` },
    { delay: 900,  level: 'INFO', msg: 'Instalando UiAutomator2 en el dispositivo...' },
    { delay: 1300, level: 'PASS', msg: '✓ Verificar pantalla principal (1.4s)' },
    { delay: 1000, level: 'PASS', msg: '✓ Login con cuenta registrada (2.1s)' },
    { delay: 1200, level: 'PASS', msg: '✓ Selección de película disponible (1.9s)' },
    { delay: 800,  level: 'WARN', msg: '⚠ Tiempo de respuesta elevado en selector de horarios (3.2s)' },
    { delay: 1100, level: 'PASS', msg: '✓ Selección y validación de asientos (2.3s)' },
    { delay: 700,  level: 'INFO', msg: 'Generando reporte Allure...' },
    { delay: 500,  level: 'INFO', msg: '✅ Suite completada — 4 PASSED · 0 FAILED · 1 SKIPPED' },
  ]

  for (const s of steps) {
    if (isAborted()) break
    await sleep(s.delay)
    addLog(s.level, s.msg)
  }

  return { passed: 4, failed: 0, skipped: 1, total: 5 }
}
