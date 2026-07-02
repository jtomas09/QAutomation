/**
 * SuiteService v2 — TestSuite (container) + TestCase (atomic with steps).
 *
 * Hierarchy:
 *   TestSuite (e.g. "E2E Compra")
 *     └── TestCase[]  (e.g. "Login", "Comprar Boletos")
 *           └── SuiteStep[] + generatedCode + pageObjects
 *
 * Storage key: "qa_suites_v2"  (migrates from "qa_custom_suites" on first load)
 *
 * Events dispatched on window:
 *   qa:suite:created | qa:suite:updated | qa:suite:deleted
 *   qa:case:created  | qa:case:updated  | qa:case:deleted
 */

// ── SuiteStep — unchanged, imported by RecordStudio ───────────────────────────

export interface SuiteStep {
  id:        string
  n:         number
  type:      string
  timeStr:   string
  inputVal?: string
  dir?:      string
  el?: {
    platform?:             string
    className?:            string
    varName?:              string
    semanticName?:         string
    locatorStrategy?:      string
    locatorValue?:         string
    resourceId?:           string
    accessId?:             string
    text?:                 string
    elType?:               string
    bounds?:               string
    accessibilityLabel?:   string
    pageObjectAnnotation?: string
    enabled?:              boolean
    clickable?:            boolean
    visible?:              boolean
  } | null
}

// ── Domain types ──────────────────────────────────────────────────────────────

export interface TestCase {
  id:            string
  suiteId:       string
  name:          string
  description:   string
  steps:         SuiteStep[]
  stepCount:     number
  generatedCode: string
  generatedXML:  string
  pageObjects:   string
  lang:          string
  platform:      string
  device:        string
  udid:          string
  appPackage:    string
  country:       string
  createdAt:     string
  updatedAt:     string
  status:        'active' | 'draft'
}

export interface TestSuite {
  id:          string
  name:        string
  description: string
  platform:    'android' | 'ios' | ''
  appName:     string
  appPackage:  string
  country:     string
  device:      string
  udid:        string
  lang:        string
  createdAt:   string
  updatedAt:   string
  icon:        string
  accent:      string
  status:      'active' | 'draft'
  testCases:   TestCase[]
}

// ── Legacy type (used only for one-time migration) ────────────────────────────

interface _LegacySuite {
  id: string; name: string; description: string; mode: 'caso' | 'suite'
  platform: string; device: string; udid: string; appName: string; appPackage: string
  country: string; steps: SuiteStep[]; stepCount: number; lang: string
  generatedCode: string; generatedXML: string; pageObjects: string
  savedAt: string; updatedAt?: string; parentSuiteId?: string; memberCaseIds?: string[]
  icon: string; accent: string; status: 'active' | 'pending' | 'draft'
}

// ── Display constants ─────────────────────────────────────────────────────────

const SUITE_ICONS   = ['🎬', '🎭', '🎪', '🎨', '🎯', '🎮', '🎲', '🎰', '🎳', '🎻']
const SUITE_ACCENTS = ['#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#3b82f6', '#14b8a6', '#f43f5e']

const APP_ICON_RULES: Array<{ patterns: string[]; icon: string }> = [
  { patterns: ['cinepolis', 'cinépolis', 'cine'],       icon: '🎬' },
  { patterns: ['netflix'],                               icon: '🎞️' },
  { patterns: ['youtube', 'ytmusic'],                    icon: '▶️' },
  { patterns: ['spotify'],                               icon: '🎵' },
  { patterns: ['tiktok'],                                icon: '🎶' },
  { patterns: ['amazon', 'shopping'],                    icon: '🛒' },
  { patterns: ['rappi', 'ubereats', 'uber_eats', 'doordash', 'delivery'], icon: '🍔' },
  { patterns: ['instagram'],                             icon: '📸' },
  { patterns: ['facebook', '.fb.'],                      icon: '📘' },
  { patterns: ['twitter', 'x.com', '.x.android'],       icon: '🐦' },
  { patterns: ['whatsapp'],                              icon: '💬' },
  { patterns: ['telegram'],                              icon: '✈️' },
  { patterns: ['discord'],                               icon: '🎮' },
  { patterns: ['slack'],                                 icon: '🧩' },
  { patterns: ['zoom'],                                  icon: '📹' },
  { patterns: ['uber', 'lyft', 'didi', 'cabify'],       icon: '🚗' },
  { patterns: ['airbnb'],                                icon: '🏠' },
  { patterns: ['linkedin'],                              icon: '💼' },
  { patterns: ['gmail', 'inbox'],                        icon: '📧' },
  { patterns: ['maps', 'waze', 'navigation'],            icon: '🗺️' },
  { patterns: ['chrome', 'firefox', 'safari', 'browser', 'webview'], icon: '🌐' },
  { patterns: ['camera', 'photo', 'gallery'],            icon: '📷' },
  { patterns: ['wallet', 'pay', 'bank', 'finance', 'bbva', 'santander', 'banamex'], icon: '💳' },
  { patterns: ['health', 'fitness', 'workout', 'strava', 'peloton'], icon: '💪' },
  { patterns: ['weather', 'clima'],                      icon: '🌤️' },
  { patterns: ['news', 'noticias'],                      icon: '📰' },
  { patterns: ['game', 'games'],                         icon: '🕹️' },
  { patterns: ['music', 'audio', 'podcast'],             icon: '🎵' },
]

export function resolveAppIcon(packageName: string, appName = ''): string {
  const hay = `${packageName} ${appName}`.toLowerCase()
  for (const rule of APP_ICON_RULES) {
    if (rule.patterns.some(p => hay.includes(p))) return rule.icon
  }
  return ''
}

// ── Storage helpers ───────────────────────────────────────────────────────────

const V2_KEY       = 'qa_suites_v2'
const LEGACY_KEY   = 'qa_custom_suites'
const SESSIONS_KEY = 'qa_record_sessions'

function uid(): string {
  return `${Date.now()}_${Math.random().toString(36).slice(2, 7)}`
}

// ── One-time migration ────────────────────────────────────────────────────────

function _legacyToCase(s: _LegacySuite, suiteId: string): TestCase {
  return {
    id: `tc_${s.id}`, suiteId,
    name: s.name, description: s.description,
    steps: s.steps ?? [], stepCount: s.stepCount ?? (s.steps?.length ?? 0),
    generatedCode: s.generatedCode ?? '', generatedXML: s.generatedXML ?? '',
    pageObjects: s.pageObjects ?? '', lang: s.lang ?? 'java-testng',
    platform: s.platform ?? '', device: s.device ?? '', udid: s.udid ?? '',
    appPackage: s.appPackage ?? '', country: s.country ?? 'mexico',
    createdAt: s.savedAt, updatedAt: s.updatedAt ?? s.savedAt,
    status: s.status === 'active' ? 'active' : 'draft',
  }
}

function _migrateFromLegacy(old: _LegacySuite[]): TestSuite[] {
  const result: TestSuite[] = []
  const processed = new Set<string>()
  let idx = 0

  // Pass 1: mode='suite' items become TestSuites; linked casos become their TestCases
  for (const s of old) {
    if (s.mode !== 'suite') continue
    const icon = resolveAppIcon(s.appPackage, s.appName) || s.icon || SUITE_ICONS[idx % SUITE_ICONS.length]
    const ts: TestSuite = {
      id: s.id, name: s.name, description: s.description,
      platform: (s.platform as 'android' | 'ios') || '', appName: s.appName ?? '',
      appPackage: s.appPackage ?? '', country: s.country ?? 'mexico',
      device: s.device ?? '', udid: s.udid ?? '', lang: s.lang ?? 'java-testng',
      createdAt: s.savedAt, updatedAt: s.updatedAt ?? s.savedAt,
      icon, accent: s.accent || SUITE_ACCENTS[idx % SUITE_ACCENTS.length],
      status: s.status === 'active' ? 'active' : 'draft', testCases: [],
    }
    for (const cid of (s.memberCaseIds ?? [])) {
      const c = old.find(x => x.id === cid)
      if (c) { ts.testCases.push(_legacyToCase(c, ts.id)); processed.add(cid) }
    }
    result.push(ts)
    processed.add(s.id)
    idx++
  }

  // Pass 2: orphan casos → solo TestSuites each containing one TestCase
  for (const s of old) {
    if (processed.has(s.id)) continue
    const sid  = `ts_${s.id}`
    const icon = resolveAppIcon(s.appPackage, s.appName) || s.icon || SUITE_ICONS[idx % SUITE_ICONS.length]
    result.push({
      id: sid, name: s.name, description: s.description,
      platform: (s.platform as 'android' | 'ios') || '', appName: s.appName ?? '',
      appPackage: s.appPackage ?? '', country: s.country ?? 'mexico',
      device: s.device ?? '', udid: s.udid ?? '', lang: s.lang ?? 'java-testng',
      createdAt: s.savedAt, updatedAt: s.updatedAt ?? s.savedAt,
      icon, accent: s.accent || SUITE_ACCENTS[idx % SUITE_ACCENTS.length],
      status: s.status === 'active' ? 'active' : 'draft',
      testCases: [_legacyToCase(s, sid)],
    })
    idx++
  }
  return result
}

// ── Service ───────────────────────────────────────────────────────────────────

class SuiteServiceImpl {

  private load(): TestSuite[] {
    const v2 = localStorage.getItem(V2_KEY)
    if (v2) {
      try { return JSON.parse(v2) as TestSuite[] }
      catch { return [] }
    }
    const leg = localStorage.getItem(LEGACY_KEY)
    if (leg) {
      try {
        const migrated = _migrateFromLegacy(JSON.parse(leg) as _LegacySuite[])
        this.persist(migrated)
        return migrated
      } catch { return [] }
    }
    return []
  }

  private persist(suites: TestSuite[]): void {
    localStorage.setItem(V2_KEY, JSON.stringify(suites))
  }

  private dispatch(event: string, detail: unknown): void {
    try { window.dispatchEvent(new CustomEvent(event, { detail, bubbles: false })) }
    catch { /* non-critical */ }
  }

  // ── TestSuite CRUD ─────────────────────────────────────────────────────

  getSuites(): TestSuite[] { return this.load() }

  getSuiteById(id: string): TestSuite | null {
    return this.load().find(s => s.id === id) ?? null
  }

  /** Backward-compat alias used by RecordStudio */
  getSuite(id: string): TestSuite | null { return this.getSuiteById(id) }

  createSuite(data: {
    name: string; description?: string; platform?: 'android' | 'ios' | ''
    appName?: string; appPackage?: string; country?: string
    device?: string; udid?: string; lang?: string; icon?: string
  }): TestSuite {
    const suites = this.load()
    const now    = new Date().toISOString()
    const suite: TestSuite = {
      id:          `suite_${uid()}`,
      name:        data.name,
      description: data.description  ?? '',
      platform:    data.platform     ?? '',
      appName:     data.appName      ?? '',
      appPackage:  data.appPackage   ?? '',
      country:     data.country      ?? 'mexico',
      device:      data.device       ?? '',
      udid:        data.udid         ?? '',
      lang:        data.lang         ?? 'java-testng',
      createdAt:   now, updatedAt: now,
      icon:        data.icon
                   || resolveAppIcon(data.appPackage ?? '', data.appName ?? '')
                   || SUITE_ICONS[suites.length % SUITE_ICONS.length],
      accent:      SUITE_ACCENTS[suites.length % SUITE_ACCENTS.length],
      status:      'active',
      testCases:   [],
    }
    suites.push(suite)
    this.persist(suites)
    this.dispatch('qa:suite:created', suite)
    return suite
  }

  updateSuite(id: string, updates: Partial<Omit<TestSuite, 'id'>>): TestSuite | null {
    const suites = this.load()
    const idx    = suites.findIndex(s => s.id === id)
    if (idx < 0) return null
    suites[idx] = { ...suites[idx], ...updates, id, updatedAt: new Date().toISOString() }
    this.persist(suites)
    this.dispatch('qa:suite:updated', suites[idx])
    return suites[idx]
  }

  deleteSuite(id: string): boolean {
    const suites   = this.load()
    const filtered = suites.filter(s => s.id !== id)
    if (filtered.length === suites.length) return false
    this.persist(filtered)
    this.dispatch('qa:suite:deleted', { id })
    return true
  }

  // ── TestCase CRUD ──────────────────────────────────────────────────────

  addCase(suiteId: string, data: {
    name: string; description?: string; steps: SuiteStep[]
    generatedCode?: string; generatedXML?: string; pageObjects?: string
    lang?: string; platform?: string; device?: string; udid?: string
    appPackage?: string; country?: string
  }): TestCase | null {
    const suites = this.load()
    const si     = suites.findIndex(s => s.id === suiteId)
    if (si < 0) return null
    const now = new Date().toISOString()
    const tc: TestCase = {
      id:            `tc_${uid()}`,
      suiteId,
      name:          data.name,
      description:   data.description  ?? '',
      steps:         data.steps,
      stepCount:     data.steps.length,
      generatedCode: data.generatedCode ?? '',
      generatedXML:  data.generatedXML  ?? '',
      pageObjects:   data.pageObjects   ?? '',
      lang:          data.lang          ?? suites[si].lang,
      platform:      data.platform      ?? suites[si].platform,
      device:        data.device        ?? suites[si].device,
      udid:          data.udid          ?? suites[si].udid,
      appPackage:    data.appPackage    ?? suites[si].appPackage,
      country:       data.country       ?? suites[si].country,
      createdAt: now, updatedAt: now, status: 'active',
    }
    suites[si].testCases.push(tc)
    suites[si].updatedAt = now
    this.persist(suites)
    this.dispatch('qa:case:created', tc)
    this.dispatch('qa:suite:updated', suites[si])

    // Keep legacy sessions key alive for history panels
    try {
      const sessions = JSON.parse(localStorage.getItem(SESSIONS_KEY) ?? '[]') as object[]
      sessions.push({
        id: tc.id, name: tc.name, description: tc.description,
        platform: tc.platform, device: tc.device, appPackage: tc.appPackage,
        savedAt: tc.createdAt, stepCount: tc.stepCount, lang: tc.lang,
        code: tc.generatedCode,
      })
      localStorage.setItem(SESSIONS_KEY, JSON.stringify(sessions))
    } catch { /* non-critical */ }

    return tc
  }

  updateCase(suiteId: string, caseId: string, updates: Partial<Omit<TestCase, 'id' | 'suiteId'>>): TestCase | null {
    const suites = this.load()
    const si     = suites.findIndex(s => s.id === suiteId)
    if (si < 0) return null
    const ci = suites[si].testCases.findIndex(c => c.id === caseId)
    if (ci < 0) return null
    const now = new Date().toISOString()
    suites[si].testCases[ci] = {
      ...suites[si].testCases[ci], ...updates, id: caseId, suiteId, updatedAt: now,
      stepCount: updates.steps ? updates.steps.length : suites[si].testCases[ci].stepCount,
    }
    suites[si].updatedAt = now
    this.persist(suites)
    this.dispatch('qa:case:updated', suites[si].testCases[ci])
    this.dispatch('qa:suite:updated', suites[si])
    return suites[si].testCases[ci]
  }

  deleteCase(suiteId: string, caseId: string): boolean {
    const suites = this.load()
    const si     = suites.findIndex(s => s.id === suiteId)
    if (si < 0) return false
    const before = suites[si].testCases.length
    suites[si].testCases = suites[si].testCases.filter(c => c.id !== caseId)
    if (suites[si].testCases.length === before) return false
    suites[si].updatedAt = new Date().toISOString()
    this.persist(suites)
    this.dispatch('qa:case:deleted', { suiteId, caseId })
    this.dispatch('qa:suite:updated', suites[si])
    return true
  }

  reorderCases(suiteId: string, newOrder: string[]): void {
    const suites = this.load()
    const si     = suites.findIndex(s => s.id === suiteId)
    if (si < 0) return
    const map = new Map(suites[si].testCases.map(c => [c.id, c]))
    suites[si].testCases = newOrder.flatMap(id => { const c = map.get(id); return c ? [c] : [] })
    suites[si].updatedAt = new Date().toISOString()
    this.persist(suites)
    this.dispatch('qa:suite:updated', suites[si])
  }

  // ── Metrics ────────────────────────────────────────────────────────────

  getMetrics(): { suites: number; cases: number; steps: number } {
    const suites = this.load()
    let cases = 0, steps = 0
    for (const s of suites) {
      cases += s.testCases.length
      for (const c of s.testCases) steps += c.stepCount
    }
    return { suites: suites.length, cases, steps }
  }
}

export const suiteService = new SuiteServiceImpl()
