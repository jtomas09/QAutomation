/**
 * SuiteService — localStorage-backed CRUD for recorded test suites.
 *
 * The storage layer is intentionally isolated behind a thin interface so it
 * can be replaced by a real API without modifying any UI code.  Every mutating
 * operation dispatches a CustomEvent on window so other panels can react
 * immediately (Dashboard, Suites list, Execute) without a page reload.
 *
 * Events dispatched:
 *   qa:suite:created  → { detail: Suite }
 *   qa:suite:updated  → { detail: Suite }
 *   qa:suite:deleted  → { detail: { id: string } }
 */

// ── Domain types ──────────────────────────────────────────────────────────────

export interface SuiteStep {
  id:        string
  n:         number
  type:      string
  timeStr:   string
  inputVal?: string
  dir?:      string
  el?: {
    platform?:           string
    className?:          string
    varName?:            string
    locatorStrategy?:    string
    locatorValue?:       string
    resourceId?:         string
    accessId?:           string
    text?:               string
    elType?:             string
    bounds?:             string
    accessibilityLabel?: string
    pageObjectAnnotation?: string
    enabled?:            boolean
    clickable?:          boolean
    visible?:            boolean
  } | null
}

export interface Suite {
  // Identity
  id:          string
  name:        string
  description: string
  mode:        'caso' | 'suite'

  // Platform & app
  platform:    string   // "android" | "ios" | ""
  device:      string   // device name
  udid:        string
  appName:     string
  appPackage:  string   // Android package or iOS bundle id
  country:     string

  // Content
  steps:          SuiteStep[]
  stepCount:      number
  lang:           string
  generatedCode:  string
  generatedXML:   string
  pageObjects:    string

  // Timestamps
  savedAt:     string   // ISO
  updatedAt:   string   // ISO

  // Display helpers
  icon:   string
  accent: string
  status: 'active' | 'pending' | 'draft'
}

// ── Private storage key ───────────────────────────────────────────────────────

const SUITES_KEY   = 'qa_custom_suites'
const SESSIONS_KEY = 'qa_record_sessions'

const SUITE_ICONS   = ['🎬', '🎭', '🎪', '🎨', '🎯', '🎮', '🎲', '🎰', '🎳', '🎻']
const SUITE_ACCENTS = ['#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#3b82f6', '#14b8a6', '#f43f5e']

// ── Service ───────────────────────────────────────────────────────────────────

class SuiteServiceImpl {

  // ── Read ──────────────────────────────────────────────────────────────────

  getAllSuites(): Suite[] {
    try {
      return JSON.parse(localStorage.getItem(SUITES_KEY) ?? '[]') as Suite[]
    } catch {
      return []
    }
  }

  getSuite(id: string): Suite | null {
    return this.getAllSuites().find(s => s.id === id) ?? null
  }

  // ── Create ────────────────────────────────────────────────────────────────

  createSuite(suite: Suite): Suite {
    const suites = this.getAllSuites()
    // Assign display helpers if missing
    const idx   = suites.length
    suite.icon   = suite.icon   || SUITE_ICONS[idx % SUITE_ICONS.length]
    suite.accent = suite.accent || SUITE_ACCENTS[idx % SUITE_ACCENTS.length]
    suite.status = suite.status || 'active'
    suites.push(suite)
    this._persist(suites)
    this._saveSession(suite)
    this._dispatch('qa:suite:created', suite)
    return suite
  }

  // ── Update ────────────────────────────────────────────────────────────────

  updateSuite(id: string, updates: Partial<Suite>): Suite | null {
    const suites = this.getAllSuites()
    const idx    = suites.findIndex(s => s.id === id)
    if (idx < 0) return null
    const updated: Suite = { ...suites[idx], ...updates, updatedAt: new Date().toISOString() }
    suites[idx] = updated
    this._persist(suites)
    this._dispatch('qa:suite:updated', updated)
    return updated
  }

  // ── Delete ────────────────────────────────────────────────────────────────

  deleteSuite(id: string): boolean {
    const suites   = this.getAllSuites()
    const filtered = suites.filter(s => s.id !== id)
    if (filtered.length === suites.length) return false
    this._persist(filtered)
    this._dispatch('qa:suite:deleted', { id })
    return true
  }

  // ── Builder helpers ───────────────────────────────────────────────────────

  /** Builds a Suite from RecordStudio data and calls createSuite(). */
  saveFromRecording(params: {
    name:          string
    description:   string
    country:       string
    mode:          'caso' | 'suite'
    platform:      string
    device:        string
    udid:          string
    appName:       string
    appPackage:    string
    steps:         SuiteStep[]
    lang:          string
    generatedCode: string
    generatedXML:  string
    pageObjects:   string
  }): Suite {
    const id    = `suite_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
    const now   = new Date().toISOString()
    const suite: Suite = {
      id,
      name:          params.name,
      description:   params.description || `Suite con ${params.steps.length} paso${params.steps.length !== 1 ? 's' : ''}`,
      mode:          params.mode,
      platform:      params.platform,
      device:        params.device,
      udid:          params.udid,
      appName:       params.appName,
      appPackage:    params.appPackage,
      country:       params.country,
      steps:         params.steps,
      stepCount:     params.steps.length,
      lang:          params.lang,
      generatedCode: params.generatedCode,
      generatedXML:  params.generatedXML,
      pageObjects:   params.pageObjects,
      savedAt:       now,
      updatedAt:     now,
      icon:          '',   // assigned by createSuite()
      accent:        '',
      status:        'active',
    }
    return this.createSuite(suite)
  }

  // ── Private helpers ───────────────────────────────────────────────────────

  private _persist(suites: Suite[]): void {
    localStorage.setItem(SUITES_KEY, JSON.stringify(suites))
  }

  /** Also writes a minimal record to the legacy sessions key so existing history panels work. */
  private _saveSession(suite: Suite): void {
    try {
      const sessions = JSON.parse(localStorage.getItem(SESSIONS_KEY) ?? '[]') as unknown[]
      sessions.push({
        id:          suite.id,
        name:        suite.name,
        description: suite.description,
        mode:        suite.mode,
        country:     suite.country,
        platform:    suite.platform,
        device:      suite.device,
        appPackage:  suite.appPackage,
        savedAt:     suite.savedAt,
        stepCount:   suite.stepCount,
        lang:        suite.lang,
        code:        suite.generatedCode,
        xml:         suite.generatedXML,
      })
      localStorage.setItem(SESSIONS_KEY, JSON.stringify(sessions))
    } catch { /* non-critical */ }
  }

  private _dispatch(event: string, detail: unknown): void {
    try {
      window.dispatchEvent(new CustomEvent(event, { detail, bubbles: false }))
    } catch { /* non-critical */ }
  }
}

export const suiteService = new SuiteServiceImpl()
