/**
 * Device Stream Service — Phase 10 (Live Preview Engine)
 *
 * Architecture contract:
 *   Frontend → Backend /api/devices/{udid}/preview → Runner /api/device-stream/{udid} → ADB
 *
 * This service is the single point of control for device previews.
 * Components (Record Studio, Device Farm, Dashboard, Runner Manager) subscribe
 * to a UDID and receive state/URL updates via callbacks — they never build URLs.
 *
 * Design for future evolution:
 *   The only file that changes when migrating from PNG → MJPEG → H264 is this one.
 *   PhoneFrame, DevicePreview, and every consumer stay untouched.
 *
 * Features:
 *   - Configurable polling interval (default 1000 ms, not hardcoded in components)
 *   - Shared object-URL cache: multiple subscribers on the same UDID share one fetch
 *   - Request deduplication: never more than one in-flight request per UDID
 *   - Automatic cleanup: object URLs are revoked when last subscriber leaves
 *   - State machine per UDID with 8 states
 */

import { API_URL } from '../api'

// ── State machine ─────────────────────────────────────────────────────────────

export type StreamState =
  | 'idle'               // no subscribers yet
  | 'connecting'         // first subscriber attached, first fetch not started
  | 'loading'            // fetching first frame (no cached frame to show)
  | 'available'          // frame received and displayed
  | 'updating'           // polling tick while a valid frame is already shown
  | 'error'              // network error, no cached frame
  | 'runner_offline'     // backend returned 503 (runner not reachable)
  | 'device_disconnected' // backend returned 404 (device gone)

export type StreamCallback = (state: StreamState, url: string | null) => void

// ── Internal entry per UDID ───────────────────────────────────────────────────

interface StreamEntry {
  state:      StreamState
  objectUrl:  string | null   // revocable blob URL from the last successful fetch
  pending:    boolean         // true while a fetch is in flight
  controller: AbortController | null
  timer:      ReturnType<typeof setInterval> | null
  subs:       Set<StreamCallback>
}

// ── Service singleton ─────────────────────────────────────────────────────────

class DeviceStreamService {
  private static _inst: DeviceStreamService | null = null

  private readonly entries = new Map<string, StreamEntry>()
  private pollingMs: number

  private constructor(pollingMs = 1000) {
    this.pollingMs = pollingMs
  }

  static getInstance(): DeviceStreamService {
    if (!DeviceStreamService._inst) {
      DeviceStreamService._inst = new DeviceStreamService(1000)
    }
    return DeviceStreamService._inst
  }

  // ── Configuration ────────────────────────────────────────────────────────

  setPollingInterval(ms: number): void {
    if (ms < 200) ms = 200  // floor: 200 ms
    this.pollingMs = ms
    // Restart active pollers
    for (const [udid, entry] of this.entries) {
      if (entry.subs.size > 0 && entry.timer !== null) {
        this._stopTimer(udid)
        this._startTimer(udid)
      }
    }
  }

  getPollingInterval(): number { return this.pollingMs }

  // ── Public API ───────────────────────────────────────────────────────────

  /**
   * Subscribe to live frames for a device.
   * Returns an unsubscribe function — call it in useEffect cleanup.
   *
   * Usage:
   *   const unsub = deviceStreamService.subscribe(udid, (state, url) => { ... })
   *   // later:
   *   unsub()
   */
  subscribe(udid: string, cb: StreamCallback): () => void {
    if (!this.entries.has(udid)) {
      this.entries.set(udid, {
        state: 'idle', objectUrl: null, pending: false,
        controller: null, timer: null, subs: new Set(),
      })
    }
    const entry = this.entries.get(udid)!
    entry.subs.add(cb)

    if (entry.subs.size === 1) {
      // First subscriber — transition to connecting and start polling
      entry.state = 'connecting'
      this._notify(udid)
      void this._fetchFrame(udid)           // immediate first frame
      this._startTimer(udid)
    } else {
      // Late subscriber — emit current state immediately
      cb(entry.state, entry.objectUrl)
    }

    return () => this._unsubscribe(udid, cb)
  }

  // ── Internal ─────────────────────────────────────────────────────────────

  private _unsubscribe(udid: string, cb: StreamCallback): void {
    const entry = this.entries.get(udid)
    if (!entry) return
    entry.subs.delete(cb)
    if (entry.subs.size === 0) {
      this._stopPolling(udid)
      if (entry.objectUrl) { URL.revokeObjectURL(entry.objectUrl); entry.objectUrl = null }
      this.entries.delete(udid)
    }
  }

  private _startTimer(udid: string): void {
    const entry = this.entries.get(udid)
    if (!entry || entry.timer !== null) return
    entry.timer = setInterval(() => { void this._fetchFrame(udid) }, this.pollingMs)
  }

  private _stopTimer(udid: string): void {
    const entry = this.entries.get(udid)
    if (!entry) return
    if (entry.timer !== null) { clearInterval(entry.timer); entry.timer = null }
  }

  private _stopPolling(udid: string): void {
    this._stopTimer(udid)
    const entry = this.entries.get(udid)
    if (!entry) return
    entry.controller?.abort()
    entry.controller = null
    entry.pending    = false
  }

  private async _fetchFrame(udid: string): Promise<void> {
    const entry = this.entries.get(udid)
    if (!entry || entry.subs.size === 0) return
    if (entry.pending) return   // dedup: skip if already in-flight

    entry.pending = true

    // Cancel previous request (should already be done, but be safe)
    entry.controller?.abort()
    const ctrl = new AbortController()
    entry.controller = ctrl

    // State transition: loading (no frame) or updating (has frame)
    const nextState: StreamState = entry.objectUrl ? 'updating' : 'loading'
    if (entry.state !== nextState) {
      entry.state = nextState
      this._notify(udid)
    }

    try {
      const res = await fetch(
        `${API_URL}/api/devices/${encodeURIComponent(udid)}/preview`,
        { signal: ctrl.signal, cache: 'no-store' },
      )

      if (!res.ok) {
        const errState: StreamState = res.status === 404 ? 'device_disconnected' : 'runner_offline'
        if (entry.objectUrl) { URL.revokeObjectURL(entry.objectUrl); entry.objectUrl = null }
        entry.state = errState
        this._notify(udid)
        return
      }

      const blob    = await res.blob()
      const nextUrl = URL.createObjectURL(blob)
      if (entry.objectUrl && entry.objectUrl !== nextUrl) URL.revokeObjectURL(entry.objectUrl)
      entry.objectUrl = nextUrl
      entry.state     = 'available'
      this._notify(udid)

    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') return
      // Network error — keep last frame if we have one
      if (!entry.objectUrl) { entry.state = 'error'; this._notify(udid) }
      else { entry.state = 'available' }
    } finally {
      const e = this.entries.get(udid)
      if (e) { e.pending = false; e.controller = null }
    }
  }

  private _notify(udid: string): void {
    const entry = this.entries.get(udid)
    if (!entry) return
    for (const cb of entry.subs) cb(entry.state, entry.objectUrl)
  }
}

// ── Export singleton ──────────────────────────────────────────────────────────
export const deviceStreamService = DeviceStreamService.getInstance()
