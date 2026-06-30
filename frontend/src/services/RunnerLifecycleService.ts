/**
 * RunnerLifecycleService — Global singleton that tracks Runner state and
 * exposes lifecycle controls (start / stop / restart).
 *
 * Polls GET /api/runners every 5 s. When the backend is unreachable for
 * more than 15 s the status transitions to OFFLINE automatically.
 *
 * Events dispatched on window:
 *   qa:runner:status  →  { detail: RunnerStatusEvent }
 */

import {
  getRunners,
  startRunner as apiStart,
  stopRunner  as apiStop,
  restartRunner as apiRestart,
} from '../api'
import type { Runner, RunnerStatus } from '../types'

// ── Public types ─────────────────────────────────────────────────────────────

export type RunnerLifecycleStatus =
  | RunnerStatus   // ONLINE | OFFLINE | BUSY | STARTING | STOPPING | DEGRADED
  | 'UNKNOWN'      // initial value before first poll

export interface RunnerStatusEvent {
  status:  RunnerLifecycleStatus
  runner:  Runner | null
  runners: Runner[]
}

// ── Service ───────────────────────────────────────────────────────────────────

class RunnerLifecycleServiceImpl {
  private _status:        RunnerLifecycleStatus = 'UNKNOWN'
  private _runner:        Runner | null          = null
  private _runners:       Runner[]               = []
  private _initialized    = false
  private _lastSuccess    = 0   // epoch ms of last successful API response
  private _intervalId:    ReturnType<typeof setInterval> | null = null

  /** Start background polling. Called once on module load. */
  init() {
    if (this._intervalId !== null) return
    this._poll()                                          // immediate first poll
    this._intervalId = setInterval(() => this._poll(), 5_000)
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  get status():      RunnerLifecycleStatus { return this._status }
  get runner():      Runner | null          { return this._runner }
  get runners():     Runner[]               { return this._runners }
  get initialized(): boolean                { return this._initialized }

  /**
   * True when the runner is actively serving work.
   * (ONLINE, BUSY, or DEGRADED — anything that means "it's up")
   */
  isOnline(): boolean {
    return this._status === 'ONLINE'
        || this._status === 'BUSY'
        || this._status === 'DEGRADED'
  }

  /** True only when ONLINE — safe to start executions. */
  isReady(): boolean {
    return this._status === 'ONLINE'
  }

  // ── Commands ───────────────────────────────────────────────────────────────

  async startRunner(runnerId?: string): Promise<void> {
    await apiStart(runnerId)
  }

  async stopRunner(runnerId?: string): Promise<void> {
    await apiStop(runnerId)
  }

  async restartRunner(runnerId?: string): Promise<void> {
    await apiRestart(runnerId)
  }

  // ── Private ────────────────────────────────────────────────────────────────

  private async _poll() {
    try {
      const runners: Runner[] = await getRunners()
      this._lastSuccess       = Date.now()
      this._initialized       = true
      this._runners           = runners

      const primary           = runners.length > 0 ? runners[0] : null
      this._runner            = primary

      const newStatus: RunnerLifecycleStatus = primary
        ? primary.status
        : 'OFFLINE'

      this._setStatus(newStatus)
    } catch {
      // Backend unreachable: transition to OFFLINE after 15 s of silence
      if (this._initialized && Date.now() - this._lastSuccess > 15_000) {
        this._setStatus('OFFLINE')
      }
    }
  }

  private _setStatus(next: RunnerLifecycleStatus) {
    if (next === this._status) return
    this._status = next
    this._dispatch()
  }

  private _dispatch() {
    const event: RunnerStatusEvent = {
      status:  this._status,
      runner:  this._runner,
      runners: this._runners,
    }
    window.dispatchEvent(new CustomEvent<RunnerStatusEvent>('qa:runner:status', { detail: event }))
  }
}

export const runnerLifecycleService = new RunnerLifecycleServiceImpl()

// Auto-start polling when the module is first imported
runnerLifecycleService.init()
