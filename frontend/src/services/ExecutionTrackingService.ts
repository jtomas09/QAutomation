/**
 * ExecutionTrackingService — front-end execution lifecycle manager.
 *
 * Manages the lifecycle of suite executions started from the UI:
 *   queued → initializing → running → passed | failed | cancelled | error
 *
 * Persists to localStorage (key: qa_exec_tracking), max 50 records.
 * Dispatches window CustomEvents so Dashboard components update in real-time:
 *   qa:exec:created | qa:exec:updated | qa:exec:finished
 */

import { postRun, streamExecution } from '../api'
import type { PhysicalDevice } from '../types'

// ── Types ─────────────────────────────────────────────────────────────────────

export type ExecStatus =
  | 'queued' | 'initializing' | 'running'
  | 'passed' | 'failed' | 'cancelled' | 'error'

export const ACTIVE_STATUSES: ExecStatus[] = ['queued', 'initializing', 'running']
export const DONE_STATUSES:   ExecStatus[] = ['passed', 'failed', 'cancelled', 'error']

export interface CaseRun {
  caseId:      string
  caseName:    string
  status:      ExecStatus | 'pending'
  startedAt?:  string
  finishedAt?: string
  durationMs?: number
  error?:      string
  stepsPassed: number
  stepsFailed: number
  stepsTotal:  number
}

export interface ActivityEntry {
  ts:    string
  level: 'info' | 'ok' | 'error' | 'warn'
  msg:   string
}

export interface ExecutionRecord {
  id:             string
  suiteId:        string
  suiteName:      string
  appName:        string
  appPackage:     string
  platform:       string
  device:         string
  udid:           string
  runner:         string
  environment:    string
  country:        string
  status:         ExecStatus
  createdAt:      string
  startedAt?:     string
  finishedAt?:    string
  durationMs?:    number
  cases:          CaseRun[]
  totalCases:     number
  passedCases:    number
  failedCases:    number
  completedCases: number
  totalSteps:     number
  completedSteps: number
  activity:       ActivityEntry[]
  runnerId?:      string   // executionId returned by the Runner API
}

// ── Storage ───────────────────────────────────────────────────────────────────

const KEY     = 'qa_exec_tracking'
const MAX_REC = 50

function uid(): string {
  return `exec_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
}

function now(): string { return new Date().toISOString() }

// ── Service ───────────────────────────────────────────────────────────────────

class ExecutionTrackingServiceImpl {

  private load(): ExecutionRecord[] {
    try { return JSON.parse(localStorage.getItem(KEY) ?? '[]') as ExecutionRecord[] }
    catch { return [] }
  }

  private persist(records: ExecutionRecord[]): void {
    // Keep newest MAX_REC records
    const pruned = records.slice(-MAX_REC)
    localStorage.setItem(KEY, JSON.stringify(pruned))
  }

  private patch(id: string, updater: (r: ExecutionRecord) => ExecutionRecord): ExecutionRecord | null {
    const records = this.load()
    const idx     = records.findIndex(r => r.id === id)
    if (idx < 0) return null
    records[idx] = updater(records[idx])
    this.persist(records)
    this.dispatch('qa:exec:updated', records[idx])
    return records[idx]
  }

  private dispatch(event: string, detail: unknown): void {
    try { window.dispatchEvent(new CustomEvent(event, { detail, bubbles: false })) }
    catch { /* non-critical */ }
  }

  private addActivity(id: string, msg: string, level: ActivityEntry['level'] = 'info'): void {
    this.patch(id, r => ({
      ...r,
      activity: [...r.activity.slice(-199), { ts: now(), level, msg }],
    }))
  }

  // ── Public API ──────────────────────────────────────────────────────────────

  createExecution(opts: {
    suiteId:     string
    suiteName:   string
    appName?:    string
    appPackage?: string
    platform?:   string
    device?:     string
    udid?:       string
    runner?:     string
    environment: string
    country?:    string
    cases: { caseId: string; caseName: string; stepsTotal: number }[]
  }): ExecutionRecord {
    const rec: ExecutionRecord = {
      id:             uid(),
      suiteId:        opts.suiteId,
      suiteName:      opts.suiteName,
      appName:        opts.appName     ?? '',
      appPackage:     opts.appPackage  ?? '',
      platform:       opts.platform    ?? '',
      device:         opts.device      ?? 'Sin dispositivo',
      udid:           opts.udid        ?? '',
      runner:         opts.runner      ?? window.location.hostname,
      environment:    opts.environment,
      country:        opts.country     ?? 'mexico',
      status:         'queued',
      createdAt:      now(),
      cases:          opts.cases.map(c => ({
        caseId:      c.caseId,
        caseName:    c.caseName,
        status:      'pending' as const,
        stepsPassed: 0,
        stepsFailed: 0,
        stepsTotal:  c.stepsTotal,
      })),
      totalCases:     opts.cases.length,
      passedCases:    0,
      failedCases:    0,
      completedCases: 0,
      totalSteps:     opts.cases.reduce((s, c) => s + c.stepsTotal, 0),
      completedSteps: 0,
      activity:       [{ ts: now(), level: 'info', msg: `Ejecución creada — Suite: ${opts.suiteName}` }],
    }
    const records = this.load()
    records.push(rec)
    this.persist(records)
    this.dispatch('qa:exec:created', rec)
    return rec
  }

  startExecution(id: string): void {
    this.patch(id, r => ({ ...r, status: 'running', startedAt: now() }))
    this.addActivity(id, 'Runner conectado — iniciando ejecución', 'info')
  }

  /** Advance the next pending case to 'running' */
  private startNextCase(id: string): void {
    this.patch(id, r => {
      const idx = r.cases.findIndex(c => c.status === 'pending')
      if (idx < 0) return r
      const updated = [...r.cases]
      updated[idx] = { ...updated[idx], status: 'running', startedAt: now() }
      return { ...r, cases: updated }
    })
  }

  /** Mark the currently running case as passed or failed */
  private finishCurrentCase(id: string, status: 'passed' | 'failed', error?: string): void {
    this.patch(id, r => {
      const idx = r.cases.findIndex(c => c.status === 'running')
      if (idx < 0) return r
      const finAt = now()
      const durMs = r.cases[idx].startedAt
        ? Date.now() - new Date(r.cases[idx].startedAt!).getTime()
        : 0
      const updated  = [...r.cases]
      updated[idx]   = { ...updated[idx], status, finishedAt: finAt, durationMs: durMs, error }
      const passed   = updated.filter(c => c.status === 'passed').length
      const failed   = updated.filter(c => c.status === 'failed').length
      const completed = passed + failed
      return { ...r, cases: updated, passedCases: passed, failedCases: failed, completedCases: completed }
    })
  }

  /** Called by SSE log handler for each PASS log line */
  onRunnerPass(id: string, msg: string): void {
    this.finishCurrentCase(id, 'passed')
    this.startNextCase(id)
    this.addActivity(id, msg, 'ok')
  }

  /** Called by SSE log handler for each FAIL log line */
  onRunnerFail(id: string, msg: string): void {
    this.finishCurrentCase(id, 'failed', msg)
    this.startNextCase(id)
    this.addActivity(id, msg, 'error')
  }

  logActivity(id: string, msg: string, level: ActivityEntry['level'] = 'info'): void {
    this.addActivity(id, msg, level)
  }

  finishExecution(id: string, status: ExecStatus): void {
    this.patch(id, r => {
      const finAt  = now()
      const durMs  = r.startedAt ? Date.now() - new Date(r.startedAt).getTime() : 0
      const cases  = r.cases.map(c => c.status === 'pending' || c.status === 'running'
        ? { ...c, status: status === 'passed' ? 'passed' as const : 'failed' as const }
        : c)
      return {
        ...r, status, finishedAt: finAt, durationMs: durMs, cases,
        passedCases:    cases.filter(c => c.status === 'passed').length,
        failedCases:    cases.filter(c => c.status === 'failed').length,
        completedCases: cases.length,
      }
    })
    this.addActivity(id, `Ejecución finalizada — estado: ${status.toUpperCase()}`,
      status === 'passed' ? 'ok' : status === 'cancelled' ? 'warn' : 'error')
    this.dispatch('qa:exec:finished', { id, status })
  }

  cancelExecution(id: string): void { this.finishExecution(id, 'cancelled') }

  // ── Queries ─────────────────────────────────────────────────────────────────

  getActiveExecutions(): ExecutionRecord[] {
    return this.load().filter(r => (ACTIVE_STATUSES as string[]).includes(r.status))
  }

  getExecution(id: string): ExecutionRecord | null {
    return this.load().find(r => r.id === id) ?? null
  }

  getHistory(limit = 20): ExecutionRecord[] {
    return this.load().slice(-limit).reverse()
  }

  clearHistory(): void {
    this.persist(this.load().filter(r => (ACTIVE_STATUSES as string[]).includes(r.status)))
    this.dispatch('qa:exec:updated', null)
  }

  // ── Trigger a suite execution ────────────────────────────────────────────────

  /**
   * Full execution flow:
   *   1. Create local record (visible in Dashboard immediately)
   *   2. Try to call the Runner API (POST /api/run)
   *   3. If Runner responds: stream SSE events to update case states
   *   4. If Runner unavailable: mark as error
   */
  async runSuite(opts: {
    suiteId:     string
    suiteName:   string
    appName?:    string
    appPackage?: string
    platform?:   string
    device?:     PhysicalDevice | null
    environment: string
    country?:    string
    cases: { caseId: string; caseName: string; stepsTotal: number }[]
    onNavigateToDashboard?: () => void
  }): Promise<ExecutionRecord> {
    const rec = this.createExecution({
      suiteId:     opts.suiteId,
      suiteName:   opts.suiteName,
      appName:     opts.appName,
      appPackage:  opts.appPackage,
      platform:    opts.platform ?? opts.device?.platform ?? '',
      device:      opts.device?.deviceName ?? '',
      udid:        opts.device?.udid ?? '',
      environment: opts.environment,
      country:     opts.country ?? 'mexico',
      cases:       opts.cases,
    })

    // Navigate to Dashboard so user sees the execution immediately
    opts.onNavigateToDashboard?.()

    // Mark as initializing while we try the Runner
    this.patch(rec.id, r => ({ ...r, status: 'initializing' }))
    this.addActivity(rec.id, 'Conectando con Runner…', 'info')

    try {
      const started = await postRun({
        suite:   opts.suiteName,
        env:     opts.environment,
        device:  opts.device?.deviceName ?? '',
        country: opts.country ?? 'mexico',
      })

      // Runner accepted — start streaming
      this.patch(rec.id, r => ({ ...r, status: 'running', startedAt: now(), runnerId: started.executionId }))
      this.addActivity(rec.id, `Runner iniciando — ID: ${started.executionId}`, 'ok')
      this.startNextCase(rec.id)

      const unsub = streamExecution(
        started.executionId,
        (level, msg) => {
          if (level === 'PASS') this.onRunnerPass(rec.id, msg)
          else if (level === 'FAIL') this.onRunnerFail(rec.id, msg)
          else this.addActivity(rec.id, msg, level === 'ERROR' ? 'error' : level === 'WARN' ? 'warn' : 'info')
        },
        (result) => {
          unsub()
          this.finishExecution(rec.id, result.failed > 0 ? 'failed' : 'passed')
        },
        (errMsg) => {
          unsub()
          this.addActivity(rec.id, errMsg, 'error')
          this.finishExecution(rec.id, 'error')
        },
      )
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err)
      this.addActivity(rec.id, `Runner no disponible: ${msg}`, 'error')
      this.finishExecution(rec.id, 'error')
    }

    return rec
  }
}

export const executionTrackingService = new ExecutionTrackingServiceImpl()
