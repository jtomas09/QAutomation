import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { useMirrorStream } from '../hooks/useMirrorStream'
import { useRecordingSession } from '../hooks/useRecordingSession'
import type { RecordingAction } from '../services/recordingService'
import type { StreamState } from '../services/deviceStream'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Video, Square, ChevronDown, ChevronRight, ChevronUp,
  Camera, Settings2, Maximize2, RotateCcw, RotateCw, Smartphone,
  Copy, Download, Check, X, MousePointer2, Type,
  MoveHorizontal, ChevronsDown, Keyboard, Clock, Code2,
  FileCode2, Layers3, Plus, Trash2, Play, Circle,
  Hand, Zap, Search, Wifi, Eye, AlertCircle, Link2,
  Pencil, CheckCircle, Package, PlayCircle,
} from 'lucide-react'
import { getDevices, getAllDeviceAppConfigs } from '../api'
import type { PhysicalDevice, DeviceAppConfig } from '../types'
import { resolveDeviceDisplayName } from '../utils/displayNames'
import { RecordStudioHeader } from '../components/record-studio/RecordStudioHeader'
import { useRunnerLifecycle } from '../hooks/useRunnerLifecycle'
import type { UIElement as AccessibilityUIElement } from '../accessibilityTypes'
import { suiteService } from '../services/SuiteService'
import type { SuiteStep } from '../services/SuiteService'

// ─── Local Types ──────────────────────────────────────────────────────────────

type RecState = 'idle' | 'recording'
type StepType = 'tap' | 'double_tap' | 'long_press' | 'input' | 'swipe' | 'scroll' | 'hide_keyboard' | 'assertion' | 'screenshot' | 'back' | 'home'
type StepFilter = 'all' | StepType
type AppScreen = 'home' | 'login'
type Lang = 'java-testng' | 'java-junit' | 'python' | 'javascript' | 'csharp' | 'kotlin'
type ViewTab = 'code' | 'xml' | 'inspector' | 'locators'

/**
 * AppEl is the element type used throughout the code generators.
 * It extends the AccessibilityUIElement shape so that elements from both
 * Android and iOS come in without any transformation.
 *
 * Backward-compat fields (shortId, resourceId, accessId, text, elType, bounds)
 * are always present — new fields are optional so hardcoded HOME_ELS still
 * compile without specifying them.
 */
interface AppEl {
  shortId:    string
  resourceId: string
  accessId:   string
  text:       string
  elType:     'btn' | 'input' | 'text' | 'list' | 'image'
  bounds?:    string
  className?: string
  // New accessibility fields (present on elements from the recording engine)
  platform?:           'android' | 'ios'
  locatorStrategy?:    string
  locatorValue?:       string
  accessibilityLabel?: string
  packageName?:        string
  bundleId?:           string
  enabled?:            boolean
  clickable?:          boolean
  visible?:            boolean
  // ElementResolver + SemanticAnalyzer output
  varName?:               string   // Spanish semantic name, e.g. "btnContinuar"
  semanticName?:          string   // Same as varName (explicit field from SemanticAnalyzer)
  pageObjectAnnotation?:  string   // e.g. "@AndroidFindBy(id = \"...\")\nprivate WebElement btnContinuar;"
}

interface RecStep {
  id:         string
  n:          number
  type:       StepType
  el:         AppEl | null
  inputVal?:  string
  dir?:       'up' | 'down' | 'left' | 'right'
  timeStr:    string
  screenName?: string   // Current screen/activity, e.g. "Login", "Home"
}

interface GenOpts {
  pageObjects:     boolean
  assertions:      boolean
  smartWaits:      boolean
  screenshots:     boolean
  allureLogs:      boolean
  reusableMethods: boolean
}

/**
 * RecordedElement — the single internal representation of a captured element.
 *
 * ALL names (variableName, methodName, paramName) are computed ONCE by
 * buildRecordedElements() and are immutable from that point on.
 * No generator, formatter, or helper ever re-derives a name from raw AppEl
 * fields — they read from this object exclusively.
 */
interface RecordedElement {
  // Identity
  id:          string          // shortId — primary key
  canonicalId: string          // shortId of the first-seen element with same locator
  isDuplicate: boolean         // true → this element reuses another element's declaration

  // Resolved locator (single call to resolveLocator, stored here)
  locator:     LocatorResult | null

  // Raw element reference (needed for annotation generation and assertion logic)
  el:          AppEl

  // ── Pre-computed, immutable names — SINGLE SOURCE OF TRUTH ──
  variableName:  string        // Java field: "btnContinuar", "txtCorreo"
  methodName:    string        // PO method:  "continuar",    "ingresarCorreo"
  paramName:     string        // input only: "correo" (lc stem); "" for non-inputs
  assertKind:    AssertKind    // assertion classification for this element
  readableName:  string        // human-readable label for UI chips and error messages
}

// ─── Cinépolis App Data ───────────────────────────────────────────────────────

const ANDROID_PKG = 'com.cinepolis.go'

const HOME_ELS: Record<string, AppEl> = {
  misCompras: {
    shortId: 'btn_mis_compras',
    resourceId: `${ANDROID_PKG}:id/btn_mis_compras`,
    accessId: 'Mis Compras',
    text: 'Mis compras',
    elType: 'btn',
    className: 'android.widget.Button',
    bounds: '[140,8][252,30]',
  },
  iniciarSesion: {
    shortId: 'btn_iniciar_sesion',
    resourceId: `${ANDROID_PKG}:id/btn_iniciar_sesion`,
    accessId: 'Iniciar Sesión',
    text: 'Iniciar Sesión',
    elType: 'btn',
    className: 'android.widget.Button',
    bounds: '[10,8][138,30]',
  },
  buscar: {
    shortId: 'txt_buscar',
    resourceId: `${ANDROID_PKG}:id/txt_buscar`,
    accessId: 'Buscar',
    text: 'Buscar película...',
    elType: 'input',
    className: 'android.widget.EditText',
    bounds: '[10,44][252,68]',
  },
  tabCartelera: {
    shortId: 'tab_cartelera',
    resourceId: `${ANDROID_PKG}:id/tab_cartelera`,
    accessId: 'En cartelera',
    text: 'En cartelera',
    elType: 'btn',
    className: 'android.widget.TextView',
    bounds: '[0,72][131,98]',
  },
  tabProximos: {
    shortId: 'tab_proximos',
    resourceId: `${ANDROID_PKG}:id/tab_proximos`,
    accessId: 'Próximos estrenos',
    text: 'Próximos estrenos',
    elType: 'btn',
    className: 'android.widget.TextView',
    bounds: '[131,72][262,98]',
  },
  pelicula_duna: {
    shortId: 'rv_pelicula_duna',
    resourceId: `${ANDROID_PKG}:id/rv_pelicula_duna`,
    accessId: 'Duna',
    text: 'Duna: Parte Dos',
    elType: 'list',
    className: 'android.widget.FrameLayout',
    bounds: '[10,104][84,192]',
  },
  pelicula_garfield: {
    shortId: 'rv_pelicula_garfield',
    resourceId: `${ANDROID_PKG}:id/rv_pelicula_garfield`,
    accessId: 'Garfield',
    text: 'Garfield',
    elType: 'list',
    className: 'android.widget.FrameLayout',
    bounds: '[90,104][164,192]',
  },
  navInicio: {
    shortId: 'btn_nav_inicio',
    resourceId: `${ANDROID_PKG}:id/btn_nav_inicio`,
    accessId: 'Inicio',
    text: 'Inicio',
    elType: 'btn',
    className: 'android.widget.LinearLayout',
    bounds: '[0,400][52,452]',
  },
  navMisCompras: {
    shortId: 'btn_nav_mis_compras',
    resourceId: `${ANDROID_PKG}:id/btn_nav_mis_compras`,
    accessId: 'Mis compras',
    text: 'Mis compras',
    elType: 'btn',
    className: 'android.widget.LinearLayout',
    bounds: '[157,400][209,452]',
  },
  navCines: {
    shortId: 'btn_nav_cines',
    resourceId: `${ANDROID_PKG}:id/btn_nav_cines`,
    accessId: 'Cines',
    text: 'Cines',
    elType: 'btn',
    className: 'android.widget.LinearLayout',
    bounds: '[52,400][105,452]',
  },
  navAlimentos: {
    shortId: 'btn_nav_alimentos',
    resourceId: `${ANDROID_PKG}:id/btn_nav_alimentos`,
    accessId: 'Alimentos',
    text: 'Alimentos',
    elType: 'btn',
    className: 'android.widget.LinearLayout',
    bounds: '[105,400][157,452]',
  },
  navMas: {
    shortId: 'btn_nav_mas',
    resourceId: `${ANDROID_PKG}:id/btn_nav_mas`,
    accessId: 'Más',
    text: 'Más',
    elType: 'btn',
    className: 'android.widget.LinearLayout',
    bounds: '[209,400][262,452]',
  },
}

const LOGIN_ELS: Record<string, AppEl> = {
  correo: {
    shortId: 'txt_correo',
    resourceId: `${ANDROID_PKG}:id/txt_correo`,
    accessId: 'Correo',
    text: 'Correo electrónico',
    elType: 'input',
    className: 'android.widget.EditText',
    bounds: '[16,100][246,135]',
  },
  password: {
    shortId: 'txt_password',
    resourceId: `${ANDROID_PKG}:id/txt_password`,
    accessId: 'Contraseña',
    text: 'Contraseña',
    elType: 'input',
    className: 'android.widget.EditText',
    bounds: '[16,147][246,182]',
  },
  entrar: {
    shortId: 'btn_entrar',
    resourceId: `${ANDROID_PKG}:id/btn_entrar`,
    accessId: 'Iniciar sesión',
    text: 'Iniciar Sesión',
    elType: 'btn',
    className: 'android.widget.Button',
    bounds: '[16,194][246,224]',
  },
}

// ─── Inspector helpers ────────────────────────────────────────────────────────

function getElById(shortId: string): AppEl | null {
  const all = { ...HOME_ELS, ...LOGIN_ELS }
  return Object.values(all).find(e => e.shortId === shortId) ?? null
}

function deriveXPath(el: AppEl): string {
  return `//*[@resource-id="${el.resourceId}"]`
}

interface XmlNode {
  tag: string
  attrs: Record<string, string>
  children?: XmlNode[]
  elId?: string
}

function buildXmlTree(screen: AppScreen): XmlNode {
  const els = screen === 'home' ? HOME_ELS : LOGIN_ELS
  const leaves: XmlNode[] = Object.values(els).map(el => ({
    tag: el.className ?? 'android.view.View',
    elId: el.shortId,
    attrs: {
      'resource-id': el.resourceId,
      'content-desc': el.accessId,
      text: el.text,
      bounds: el.bounds ?? '[0,0][0,0]',
      clickable: (el.elType === 'btn' || el.elType === 'input') ? 'true' : 'false',
      enabled: 'true',
      displayed: 'true',
    },
  }))

  const container: XmlNode = {
    tag: 'android.widget.FrameLayout',
    attrs: {
      'resource-id': `${ANDROID_PKG}:id/content`,
      bounds: '[0,0][262,452]',
      clickable: 'false',
      enabled: 'true',
    },
    children: leaves,
  }

  return {
    tag: 'hierarchy',
    attrs: { rotation: '0' },
    children: [container],
  }
}

// ─── Step type helpers ────────────────────────────────────────────────────────

const STEP_COLORS: Record<StepType, string> = {
  tap:           '#818cf8',
  double_tap:    '#a78bfa',
  long_press:    '#c084fc',
  input:         '#34d399',
  swipe:         '#f59e0b',
  scroll:        '#60a5fa',
  hide_keyboard: '#f43f5e',
  assertion:     '#14b8a6',
  screenshot:    '#eab308',
  back:          '#fb923c',
  home:          '#2dd4bf',
}

function stepTypeLabel(type: StepType): string {
  switch (type) {
    case 'tap':           return 'Tap'
    case 'double_tap':    return 'Double Tap'
    case 'long_press':    return 'Long Press'
    case 'input':         return 'Input Text'
    case 'swipe':         return 'Swipe'
    case 'scroll':        return 'Scroll'
    case 'hide_keyboard': return 'Hide Keyboard'
    case 'assertion':     return 'Assertion'
    case 'screenshot':    return 'Screenshot'
    case 'back':          return 'Volver'
    case 'home':          return 'Inicio'
  }
}

function getStepIcon(type: StepType, size = 13): React.ReactNode {
  const c = STEP_COLORS[type]
  switch (type) {
    case 'tap':           return <MousePointer2 size={size} color={c} />
    case 'double_tap':    return <Zap size={size} color={c} />
    case 'long_press':    return <Hand size={size} color={c} />
    case 'input':         return <Type size={size} color={c} />
    case 'swipe':         return <MoveHorizontal size={size} color={c} />
    case 'scroll':        return <ChevronsDown size={size} color={c} />
    case 'hide_keyboard': return <Keyboard size={size} color={c} />
    case 'assertion':     return <CheckCircle size={size} color={c} />
    case 'screenshot':    return <Camera size={size} color={c} />
    case 'back':          return <RotateCcw size={size} color={c} />
    case 'home':          return <Smartphone size={size} color={c} />
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CODE GENERATION ENGINE  (Phases 1-12)
//
//  Modules in declaration order:
//    NamingStrategy    — semantic variable / method names (Phase 2, 6, 7)
//    ElementClassifier — control-type prefix detection (Phase 3)
//    ElementRegistry   — deduplication + unique-name assignment (Phase 4, 5)
//    LocatorResolver   — platform-aware locator priority chain (Phase 1, 10)
//    AssertionGenerator — context-sensitive assertions (Phase 9)
//    NamingEngine      — centralized name-resolution service (Problem 9)
//    ValidationEngine  — pre-generation consistency checks (Problem 10)
//    PlatformStrategy  — @FindBy annotation + per-language selector formatting (Phase 11)
//    PageObjectGenerator — Page Object class construction (inside generateJava)
//    TestGenerator     — test method body construction (inside generateJava)
//    ScrollAnalyzer    — smart scroll-to-element emission (Phase 8)
// ═══════════════════════════════════════════════════════════════════════════════

function cap(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1)
}

function toMethodName(shortId: string): string {
  return shortId.replace(/^(btn|txt|rv|tab|iv|cb)_/, '').split('_').map(cap).join('')
}

/**
 * Extracts the meaningful stem from a camelCase varName for use in method names.
 * "btnContinuar" → "Continuar",  "txtCorreo" → "Correo"
 * Falls back to capitalizing the whole varName when no known prefix is found.
 */
function stemFromVarName(varName: string): string {
  const m = varName.match(/^(container|toolbar|card|nav|scr|cell|lst|img|rdo|chk|cmb|lbl|txt|btn|sw|rv|el)(.+)/i)
  if (m) return m[2].charAt(0).toUpperCase() + m[2].slice(1)
  return varName.charAt(0).toUpperCase() + varName.slice(1)
}

// ── NamingStrategy ── Phase 2, 6, 7 ──────────────────────────────────────────

const ACCENT_MAP: Record<string, string> = {
  'á':'a','à':'a','ä':'a','â':'a','ã':'a',
  'é':'e','è':'e','ë':'e','ê':'e',
  'í':'i','ì':'i','ï':'i','î':'i',
  'ó':'o','ò':'o','ö':'o','ô':'o','õ':'o',
  'ú':'u','ù':'u','ü':'u','û':'u',
  'ñ':'n','ç':'c',
  'Á':'A','À':'A','Ä':'A','Â':'A','Ã':'A',
  'É':'E','È':'E','Ë':'E','Ê':'E',
  'Í':'I','Ì':'I','Ï':'I','Î':'I',
  'Ó':'O','Ò':'O','Ö':'O','Ô':'O','Õ':'O',
  'Ú':'U','Ù':'U','Ü':'U','Û':'U',
  'Ñ':'N','Ç':'C',
}

function removeAccents(s: string): string {
  return s.split('').map(c => ACCENT_MAP[c] ?? c).join('')
}

// Spanish articles, prepositions and conjunctions stripped when building identifiers.
const SPANISH_STOP_WORDS = new Set([
  'de','del','el','la','los','las','un','una','en','a','al','y','o','e','ni',
  'que','con','por','para','sin','sobre','entre','hacia','desde','hasta',
  'se','su','sus','me','mi','mis','te','tu','tus','le','les','nos',
  'si','no','muy','tan','ya','hay',
])

/**
 * Converts arbitrary text to a camelCase identifier fragment with no accents,
 * no symbols, and stop-words removed.
 *   "Métodos de pago"  → "metodosPago"
 *   "Tarjeta de crédito" → "tarjetaCredito"
 *   "Continuar"          → "continuar"
 */
function normalizeToIdentifier(text: string): string {
  const noAcc  = removeAccents(text)
  const words  = noAcc.split(/[^a-zA-Z0-9]+/).filter(w => w.length > 0)
  const useful = words.filter(w => !SPANISH_STOP_WORDS.has(w.toLowerCase()))
  const src    = useful.length > 0 ? useful : words.slice(0, 1)  // keep ≥1 word
  if (src.length === 0) return ''
  return src
    .map((w, i) => i === 0
      ? w.charAt(0).toLowerCase() + w.slice(1).toLowerCase()
      : w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join('')
}

/**
 * Selects the field-name prefix that best describes the element's runtime type.
 * Checks className first (most precise), then falls back to elType.
 * Android and iOS class names are both handled.
 */
// ── ElementClassifier ── Phase 3 ─────────────────────────────────────────────
function prefixForEl(el: AppEl): string {
  const cls = (el.className ?? '').toLowerCase()

  // ── Text input controls ─────────────────────────────────────────────────
  if (cls.includes('edittext')          || cls.includes('textfield')         ||
      cls.includes('securedtextfield')  || cls.includes('textinput')         ||
      cls.includes('xcuielementtypetextfield') ||
      cls.includes('xcuielementtypesecuredtextfield'))
    return 'txt'

  // ── Checkbox ────────────────────────────────────────────────────────────
  if (cls.includes('checkbox') || cls.includes('xcuielementtypecheckbox'))
    return 'chk'

  // ── Radio button ────────────────────────────────────────────────────────
  if (cls.includes('radiobutton') || cls.includes('xcuielementtyperadiobutton'))
    return 'rdo'

  // ── Switch / Toggle ─────────────────────────────────────────────────────
  if ((cls.includes('switch') && !cls.includes('viewswitcher')) ||
      cls.includes('togglebutton') || cls.includes('xcuielementtypeswitch'))
    return 'sw'

  // ── Combo / Spinner / Picker ─────────────────────────────────────────────
  if (cls.includes('spinner')              || cls.includes('combobox')              ||
      cls.includes('autocompletetextview') || cls.includes('picker')                ||
      cls.includes('xcuielementtypepopupbutton') ||
      cls.includes('xcuielementtypecombobox'))
    return 'cmb'

  // ── Image ────────────────────────────────────────────────────────────────
  if (cls.includes('imageview') || cls.includes('imagebutton') ||
      cls.includes('xcuielementtypeimage'))
    return 'img'

  // ── RecyclerView / ListView / Grid / Table / Collection ──────────────────
  if (cls.includes('recyclerview')  || cls.includes('listview')  ||
      cls.includes('gridview')      ||
      cls.includes('xcuielementtypetable') ||
      cls.includes('xcuielementtypecollectionview'))
    return 'lst'

  // ── Scroll view ──────────────────────────────────────────────────────────
  if (cls.includes('scrollview') || cls.includes('nestedscrollview') ||
      cls.includes('horizontalscrollview') ||
      cls.includes('xcuielementtypescrollview'))
    return 'scr'

  // ── Toolbar / AppBar ─────────────────────────────────────────────────────
  if (cls.includes('toolbar')      || cls.includes('appbarlayout') ||
      cls.includes('actionbar')    ||
      cls.includes('xcuielementtypetoolbar'))
    return 'toolbar'

  // ── Navigation ───────────────────────────────────────────────────────────
  if (cls.includes('navigationview')       || cls.includes('bottomnavigationview') ||
      cls.includes('navhostfragment')      || cls.includes('tabbar')               ||
      cls.includes('xcuielementtypenavigationbar') ||
      cls.includes('xcuielementtypetabbar'))
    return 'nav'

  // ── Card ─────────────────────────────────────────────────────────────────
  if (cls.includes('cardview') || cls.includes('materialcardview'))
    return 'card'

  // ── Cell (iOS table/collection cell) ─────────────────────────────────────
  if (cls.includes('cell') || cls.includes('xcuielementtypecell'))
    return 'cell'

  // ── Button ───────────────────────────────────────────────────────────────
  if (cls.includes('button')            || cls.includes('materialbutton')        ||
      cls.includes('floatingactionbutton') ||
      cls.includes('xcuielementtypebutton'))
    return 'btn'

  // ── Static text / Label ──────────────────────────────────────────────────
  if (cls.includes('textview') || cls.includes('statictext') ||
      cls.includes('xcuielementtypestatictext'))
    return 'lbl'

  // ── Container (layout wrappers) ──────────────────────────────────────────
  if (cls.includes('framelayout')      || cls.includes('linearlayout')    ||
      cls.includes('constraintlayout') || cls.includes('relativelayout')  ||
      cls.includes('coordinatorlayout') || cls.includes('viewgroup')      ||
      cls.includes('xcuielementtypegroup') || cls.includes('xcuielementtypewindow'))
    return 'container'

  // ── Fallback to semantic elType ───────────────────────────────────────────
  switch (el.elType) {
    case 'btn':   return 'btn'
    case 'input': return 'txt'
    case 'text':  return 'lbl'
    case 'list':  return 'lst'
    case 'image': return 'img'
    default:      return 'el'
  }
}

// All valid field-name prefixes, longest first to avoid partial-match issues.
const ALL_PREFIXES = 'container|toolbar|card|nav|scr|cell|lst|img|rdo|chk|cmb|lbl|txt|btn|sw|rv|el'

// Recognises content-desc / accessibilityId values that already carry a valid
// field-name prefix, e.g. "btnComprar", "toolbarPrincipal", "txtEmail".
const PREFIXED_CONTENT_RE = new RegExp(`^(${ALL_PREFIXES})([^a-z].*)$`, 'i')

// Generic placeholder names the backend SemanticAnalyzer emits when it cannot
// determine a meaningful identifier: btnElemento, btnElemento12, btn3, el2 …
const GENERIC_NAME_RE = new RegExp(
  `^(?:(?:${ALL_PREFIXES})?[Ee]lemento\\d*|(?:${ALL_PREFIXES})\\d+)$`
)

// Words that describe the UI class/type of an element, not its purpose.
// When the backend sends one of these as varName/semanticName it adds no value —
// we reject it so the naming chain can fall through to content attributes.
const TYPE_DERIVED_NAMES = new Set([
  // Android view classes
  'view','button','imagebutton','scrollview','nestedscrollview','horizontalscrollview',
  'textview','imageview','edittext','checkbox','radiobutton','togglebutton',
  'framelayout','linearlayout','relativelayout','constraintlayout','coordinatorlayout',
  'recyclerview','listview','gridview','expandablelistview','cardview',
  'toolbar','appbar','actionbar','bottomnavigationview','navigationview',
  'fragment','activity','dialog','alertdialog','viewpager','tablayout',
  // iOS view classes
  'uiview','uibutton','uilabel','uitextfield','uitextview','uiimageview',
  'uiscrollview','uitableview','uicollectionview','uiswitch','uislider',
  'uinavigationbar','uitabbar','uisearchbar',
  // Generic low-value identifiers
  'element','widget','component','container','layout','panel','item','cell',
  'row','column','content','wrapper','holder','group','frame',
  'header','footer','body','section',
])

function isGenericVarName(name: string): boolean {
  if (!name || name.length < 3) return true
  if (new RegExp(`^(?:${ALL_PREFIXES})$`, 'i').test(name)) return true
  if (GENERIC_NAME_RE.test(name)) return true
  // Reject names that are purely Android/iOS class names or type descriptors.
  const lower = name.toLowerCase()
  if (TYPE_DERIVED_NAMES.has(lower)) return true
  // Also reject when the stem after stripping the known prefix is type-derived:
  // "btnView" → stem "view" → rejected; "btnContinuar" → stem "continuar" → kept.
  const stemMatch = name.match(new RegExp(`^(?:${ALL_PREFIXES})(.+)$`, 'i'))
  if (stemMatch) return TYPE_DERIVED_NAMES.has(stemMatch[1].toLowerCase())
  return false
}

/**
 * Returns the best field/variable name for an element.
 *
 * Priority:
 *   1. Backend semanticName / varName — when it carries real meaning.
 *   2. accessId / text / accessibilityLabel — normalised and prefixed.
 *   3. shortId-derived name as last resort.
 */
function elVarName(el: AppEl): string {
  const backend = el.semanticName?.trim() || el.varName?.trim()
  if (backend && !isGenericVarName(backend)) return backend

  // Phase 7: input fields use hint/placeholder priority — strip instruction prefix first
  if (el.elType === 'input') {
    const candidates = [
      el.accessId?.trim()           ? stripInputInstruction(el.accessId.trim())           : '',
      el.accessibilityLabel?.trim() ? stripInputInstruction(el.accessibilityLabel.trim()) : '',
      parseInputResourceId(el.resourceId ?? ''),
      (el.text?.trim().length ?? 0) >= 3 ? stripInputInstruction(el.text!.trim()) : '',
    ]
    for (const raw of candidates) {
      if (!raw) continue
      const base = normalizeToIdentifier(removeAccents(raw))
      // Require ≥2 alphabetic chars — rejects "6", "$12.50", "12:30", "05/06".
      if (base && /[a-zA-Z]{2}/.test(base))
        return `txt${base.charAt(0).toUpperCase()}${base.slice(1)}`
    }
  }

  // Try each content source independently — purely numeric values (prices, times,
  // dates, indices) are skipped so the next attribute in the chain is tried instead.
  // Order: accessId → text → accessibilityLabel → resourceId
  const contentSources = [
    el.accessId?.trim()           ?? '',
    el.text?.trim()               ?? '',
    el.accessibilityLabel?.trim() ?? '',
    parseInputResourceId(el.resourceId ?? ''),
  ]
  for (const content of contentSources) {
    if (!content) continue
    const noAcc = removeAccents(content)
    // Honour content that already carries a known prefix (e.g. "btnComprar").
    const m = noAcc.match(PREFIXED_CONTENT_RE)
    if (m) {
      const root = normalizeToIdentifier(m[2])
      if (root) return `${m[1].toLowerCase()}${root.charAt(0).toUpperCase()}${root.slice(1)}`
      continue  // prefix matched but root empty — try next source
    }
    const base = normalizeToIdentifier(noAcc)
    // Require ≥2 alphabetic chars — rejects "6", "$12.50", "12:30", "05/06".
    if (base && /[a-zA-Z]{2}/.test(base)) {
      const prefix = prefixForEl(el)
      return `${prefix}${base.charAt(0).toUpperCase()}${base.slice(1)}`
    }
    // Source is purely numeric or single-char — try next source
  }

  // All content sources are empty or numeric — return bare prefix.
  // buildNameMap will append collision counters (lbl, lbl2, lbl3…) which is
  // far better than lbl6/lbl12 derived from the element's numeric text content.
  return prefixForEl(el)
}

/**
 * Assigns unique names to a group of elements (within one Page Object class).
 * Appends "2", "3", … when two different elements produce the same base name.
 * Returns a map: shortId → uniqueName.
 */
// ── ElementRegistry ── Phase 4, 5 ────────────────────────────────────────────
function buildNameMap(els: Iterable<AppEl>): Map<string, string> {
  const result    = new Map<string, string>()
  const usedNames = new Set<string>()
  for (const el of els) {
    let name = elVarName(el)
    if (usedNames.has(name)) {
      let n = 2
      while (usedNames.has(`${name}${n}`)) n++
      name = `${name}${n}`
    }
    usedNames.add(name)
    result.set(el.shortId, name)
  }
  return result
}

/** Canonical string that uniquely identifies a locator for deduplication purposes. */
function locatorKey(el: AppEl): string {
  if (el.pageObjectAnnotation?.trim()) return el.pageObjectAnnotation.trim()
  const loc = resolveLocator(el)
  return loc ? `${loc.strategy}::${loc.value}` : `__noloc__::${el.shortId}`
}

/**
 * Within a screen's element map, identifies duplicate locators and returns a map
 * shortId → canonical shortId.  Primary elements map to themselves; duplicates
 * map to the first element that shares the same locator.
 */
function buildLocatorAliasMap(els: Map<string, AppEl>): Map<string, string> {
  const firstSeen = new Map<string, string>()  // locatorKey → first shortId
  const aliases   = new Map<string, string>()  // shortId → canonical shortId
  for (const [shortId, el] of els) {
    const key       = locatorKey(el)
    const canonical = firstSeen.get(key)
    if (canonical !== undefined) {
      aliases.set(shortId, canonical)   // duplicate — redirect to first occurrence
    } else {
      firstSeen.set(key, shortId)
      aliases.set(shortId, shortId)     // primary — maps to itself
    }
  }
  return aliases
}

/**
 * buildRecordedElements — ElementRegistry entry point.
 *
 * Collects all elements from the step list, deduplicates by locator,
 * assigns unique names to canonical elements, and pre-computes every
 * name that code generation will ever need.
 *
 * Returns:  screen → (shortId → RecordedElement)
 *
 * Guarantees:
 *   - Every shortId that appears in a step is present in its screen's map.
 *   - Duplicate elements reference the canonical element's variableName.
 *   - nameMap is built from canonical elements ONLY, so name collision
 *     counters are never inflated by duplicates.
 *   - If no locator can be resolved, rec.locator is null and the caller
 *     must skip declaration/method generation for that element.
 */
function buildRecordedElements(steps: RecStep[]): Map<string, Map<string, RecordedElement>> {
  // ── 1. Collect elements per screen ─────────────────────────────────────────
  const screenElSets = new Map<string, Map<string, AppEl>>()
  for (const step of steps) {
    if (!step.el) continue
    const screen = step.screenName?.trim() || 'App'
    if (!screenElSets.has(screen)) screenElSets.set(screen, new Map())
    screenElSets.get(screen)!.set(step.el.shortId, step.el)
  }
  if (screenElSets.size === 0) {
    const allEls = new Map<string, AppEl>()
    for (const step of steps) { if (step.el) allEls.set(step.el.shortId, step.el) }
    if (allEls.size > 0) screenElSets.set('App', allEls)
  }

  const result = new Map<string, Map<string, RecordedElement>>()

  for (const [screen, els] of screenElSets) {
    // ── 2. Dedup: shortId → canonical shortId ────────────────────────────────
    const aliasMap = buildLocatorAliasMap(els)

    // ── 3. Canonical elements in step order (Map preserves insertion order) ──
    const canonicalEls = new Map<string, AppEl>()
    for (const [shortId, el] of els) {
      if (aliasMap.get(shortId) === shortId) canonicalEls.set(shortId, el)
    }
    // Step-order array used for context lookups below.
    const canonicalEntries = [...canonicalEls.entries()]

    // ── 4. Initial name map from content attributes (no context yet) ─────────
    const nameMap = buildNameMap(canonicalEls.values())

    // ── 5. Context enrichment — bare-prefix names get meaning from neighbours ─
    // When an element has no semantic content of its own (name resolved to a bare
    // type prefix like "btn", "lbl") look at the N preceding canonical elements
    // for a text label that reveals the visual context.  A symbol element like "+"
    // next to "Tarjeta de crédito" becomes "btnAgregarTarjetaCredito".
    const usedNames = new Set(nameMap.values())
    const bareRe    = new RegExp(`^(?:${ALL_PREFIXES})\\d*$`)
    for (let i = 0; i < canonicalEntries.length; i++) {
      const [shortId, el] = canonicalEntries[i]
      const currentName   = nameMap.get(shortId)!
      if (!bareRe.test(currentName)) continue  // already has a semantic name

      // Look back up to 5 canonical elements for a real text label.
      let ctxStem: string | null = null
      for (let j = i - 1; j >= 0 && j >= i - 5; j--) {
        // Skip other symbol-only elements — they provide action words, not context labels.
        if (symbolActionStem(canonicalEntries[j][1])) continue
        const s = deriveMethodStem(canonicalEntries[j][1])
        if (s) { ctxStem = s; break }
      }

      const sym = symbolActionStem(el)
      if (!sym && !ctxStem) continue  // no enrichment available

      const prefix   = currentName.replace(/\d+$/, '')  // "btn2" → "btn"
      const combined = sym && ctxStem ? `${sym}${ctxStem}` : sym ?? ctxStem!
      let   newName  = `${prefix}${cap(combined)}`

      // Ensure uniqueness against all other entries.
      usedNames.delete(currentName)
      if (usedNames.has(newName)) {
        let n = 2; while (usedNames.has(`${newName}${n}`)) n++; newName = `${newName}${n}`
      }
      nameMap.set(shortId, newName)
      usedNames.add(newName)
    }

    // ── 6. Build RecordedElement for every element in this screen ────────────
    const screenRegistry = new Map<string, RecordedElement>()
    for (const [shortId, el] of els) {
      const canonicalId = aliasMap.get(shortId) ?? shortId
      const isDuplicate = canonicalId !== shortId

      // variableName: backend annotation name takes priority.
      const annotationVar = el.pageObjectAnnotation?.trim()
        ? extractVarFromAnnotation(el.pageObjectAnnotation)
        : null
      const variableName = annotationVar ?? nameMap.get(canonicalId) ?? `el_${canonicalId}`

      // methodName: built from the enriched stem so it mirrors the variable name.
      const isInput  = el.elType === 'input'
      const rawStem  = deriveMethodStem(el)   // includes symbol detection
      const isSymbol = symbolActionStem(el) !== null

      // Context lookup for the method name (same window as step 5).
      const canonIdx = canonicalEntries.findIndex(([id]) => id === canonicalId)
      let ctxStem: string | null = null
      if ((isSymbol || rawStem === null) && canonIdx >= 0) {
        for (let j = canonIdx - 1; j >= 0 && j >= canonIdx - 5; j--) {
          if (symbolActionStem(canonicalEntries[j][1])) continue
          const s = deriveMethodStem(canonicalEntries[j][1])
          if (s) { ctxStem = s; break }
        }
      }

      // Effective stem priority:
      //  1. symbol + context  → "AgregarTarjetaCredito" ("+" near "Tarjeta de crédito")
      //  2. semantic own text → "Continuar"
      //  3. context only      → element with no own text inherits neighbour label
      //  4. varStem           → last resort, only when it is NOT a type-derived word
      // Effective stem — combines own content, symbol action, and context.
      // Uses nameMap (enriched in step 5) as varStem source, NOT variableName,
      // so annotationVar from the backend doesn't pollute the stem derivation.
      const effectiveStem = (() => {
        if (rawStem && isSymbol && ctxStem) return `${rawStem}${ctxStem}`
        if (rawStem) return rawStem
        if (ctxStem) return ctxStem
        const vs = stemFromVarName(nameMap.get(canonicalId) ?? '')
        if (TYPE_DERIVED_NAMES.has(vs.toLowerCase())) return null
        return vs.length >= 2 && /[a-zA-Z]{2}/.test(vs) ? vs : null
      })()

      // ── NamingEngine: final name resolution ────────────────────────────────
      const methodName  = NamingEngine.resolveMethodName(el, effectiveStem)
      const paramName   = isInput && effectiveStem ? lc(effectiveStem) : ''

      screenRegistry.set(shortId, {
        id:          shortId,
        canonicalId,
        isDuplicate,
        locator:     resolveLocator(el),
        el,
        variableName,
        methodName,
        paramName,
        assertKind:   NamingEngine.resolveAssertionName(el),
        readableName: NamingEngine.resolveReadableName(el),
      })
    }
    result.set(screen, screenRegistry)
  }
  return result
}

/** Lowercase first character. */
function lc(s: string): string {
  return s.length > 0 ? s.charAt(0).toLowerCase() + s.slice(1) : s
}

function esc(v: string): string {
  return v.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/'/g, "\\'")}

// ── Phase 6: Smart method names for Page Object actions ───────────────────────

// Maps common single-character symbols and icon text to semantic action words.
// When an element's text is a bare symbol ("+", "✕", ">") the element has no
// useful text content on its own — but we can infer the intended action and
// then combine it with a nearby contextual label.
const SYMBOL_ACTION_MAP: Record<string, string> = {
  '+': 'Agregar',  '−': 'Quitar',   '-': 'Quitar',
  '×': 'Cerrar',   '✕': 'Cerrar',   '✗': 'Cerrar',  '✖': 'Cerrar',
  '✓': 'Confirmar','✔': 'Confirmar',
  '>': 'Siguiente','→': 'Siguiente', '»': 'Siguiente',
  '<': 'Atras',    '←': 'Atras',    '«': 'Atras',
  '↑': 'Subir',   '↓': 'Bajar',   '⬆': 'Subir',   '⬇': 'Bajar',
  '🔍': 'Buscar', '🔎': 'Buscar',  '⌕': 'Buscar',
  '♡': 'Favorito','♥': 'Favorito', '❤': 'Favorito',
  '🛒': 'Carrito','🛍': 'Carrito',
  '✏': 'Editar',  '✎': 'Editar',   '📝': 'Editar',
  '🗑': 'Eliminar','🗙': 'Eliminar',
  '▶': 'Reproducir','⏸': 'Pausar', '⏹': 'Detener',
  '☰': 'Menu',    '≡': 'Menu',
  '⚙': 'Configurar','⚙️': 'Configurar',
  '↩': 'Volver',  '↪': 'Siguiente',
}

/** Returns the action stem when el.text / el.accessId is a known symbol, null otherwise. */
function symbolActionStem(el: AppEl): string | null {
  const t = el.text?.trim() || el.accessId?.trim() || ''
  return SYMBOL_ACTION_MAP[t] ?? null
}

// Spanish + English action verbs. A stem that starts with one of these is already
// self-describing — no extra verb prefix is added.
const ACTION_VERBS = new Set([
  // Spanish
  'continuar','aceptar','cancelar','iniciar','salir','entrar','ingresar',
  'agregar','eliminar','actualizar','confirmar','guardar','buscar','ver',
  'abrir','cerrar','volver','siguiente','anterior','enviar','editar','borrar',
  'compartir','descargar','comprar','pagar','seleccionar','elegir',
  'registrar','crear','aplicar','filtrar','recargar','refrescar','limpiar',
  'rechazar','omitir','saltar','escanear','capturar','verificar','acceder',
  'cambiar','modificar','mostrar','ocultar','activar','desactivar',
  'retroceder','avanzar','completar','finalizar','terminar','empezar',
  'comenzar','revisar','gestionar','administrar',
  // English (common in mixed-language apps)
  'login','logout','checkout','submit','search','add','remove','delete',
  'confirm','back','next','skip','scan','view','open','close','select',
  'choose','buy','pay','register','save','share','download','refresh',
  'apply','filter','reload','start','finish','accept','go','edit',
])

// Nouns that typically imply a "select one from several" context.
const SELECTION_NOUNS = new Set([
  'metodo','opcion','tipo','categoria','forma','manera','modo','plan',
  'tarifa','servicio','pago','envio','entrega','direccion','promocion',
])

/** Returns true when the stem's first camelCase word is a known action verb. */
function startsWithVerb(stem: string): boolean {
  const first = stem.match(/^([a-z]+)/i)?.[1]?.toLowerCase() ?? ''
  return ACTION_VERBS.has(first)
}

/**
 * Derives the Page Object method name for a non-input element.
 * - Verb stems → used directly:        continuar(), aceptar(), comprar()
 * - "Selection" nouns → seleccionar:   seleccionarMetodoPago()
 * - Other nouns → abrir:               abrirClubCinepolis(), abrirCarrito()
 */
function smartMethodName(stem: string): string {
  if (!stem) return 'interactuar'
  if (startsWithVerb(stem)) return lc(stem)
  const words = stem.split(/(?=[A-Z])/).map(w => w.toLowerCase())
  if (words.some(w => SELECTION_NOUNS.has(w))) return `seleccionar${stem}`
  return `abrir${stem}`
}

// ── Method stem + annotation helpers ────────────────────────────────────────

/**
 * Derives the PascalCase stem for a Page Object method name by trying element
 * content sources in priority order:
 *   accessId → text → accessibilityLabel → resource-id local part
 *
 * Returns null when no source yields at least 2 alphabetic characters,
 * which signals the caller to skip method generation rather than emit
 * garbage names like abrir6(), view(), button().
 */
function deriveMethodStem(el: AppEl): string | null {
  // Single-char / icon text → semantic action word ("+" → "Agregar")
  const sym = symbolActionStem(el)
  if (sym) return sym

  const sources = [
    el.accessId?.trim(),
    el.text?.trim(),
    el.accessibilityLabel?.trim(),
    parseInputResourceId(el.resourceId ?? ''),
  ]
  for (const raw of sources) {
    if (!raw) continue
    const clean = el.elType === 'input' ? stripInputInstruction(raw) : raw
    const norm  = normalizeToIdentifier(removeAccents(clean))
    if (norm.length >= 2 && /[a-zA-Z]{2}/.test(norm)) {
      return norm.charAt(0).toUpperCase() + norm.slice(1)
    }
  }
  return null
}

/**
 * Extracts the Java field name from a backend-provided @FindBy annotation block.
 * "@AndroidFindBy(…)\nprivate WebElement btnContinuar;" → "btnContinuar"
 * Used so that when the backend declares its own name, variableName always
 * matches the actual declared field — preventing click(computedName) mismatches.
 */
function extractVarFromAnnotation(annotation: string): string | null {
  const m = annotation.match(/\bWebElement\s+(\w+)\s*;/)
  return m ? m[1] : null
}

// ── Phase 7: Smart input field naming ────────────────────────────────────────

// Strips leading instructional phrases from hint/placeholder text so only the
// semantic field label remains.
// "Ingresa tu correo electrónico" → "correo electrónico"
// "Enter your email address"      → "email address"
const INPUT_INSTRUCTION_RE = /^(?:ingresa?|escribe?|introduce?|anota|coloca|captura|enter|type|input|write|insert)(?:\s+(?:tu|su|el|la|un|una|your|the|a|an))?[:\s]+/i

function stripInputInstruction(s: string): string {
  return s.replace(INPUT_INSTRUCTION_RE, '').trim()
}

/**
 * Extracts a semantic label from a resource-id, stripping generic input prefixes.
 * "com.cinepolis.go:id/input_correo"  → "correo"
 * "com.app:id/edit_email_field"       → "email field"
 */
function parseInputResourceId(resourceId: string): string {
  if (!resourceId) return ''
  const m = resourceId.match(/:id\/(.+)$/)
  if (!m) return ''
  const local = m[1].replace(/^(?:input|edit|et|edt|field|txt|text|inp|tf)_?/i, '')
  return local.replace(/_/g, ' ').trim()
}

// ── AssertionGenerator ── Phase 9 ────────────────────────────────────────────

type AssertKind = 'checked' | 'selected' | 'enabled' | 'text' | 'exists' | 'visible'

/**
 * Classifies what kind of assertion is most meaningful for a given element.
 * Used by all language generators to avoid generic assertVisible() everywhere.
 */
function elementAssertKind(el: AppEl): AssertKind {
  const cls = (el.className ?? '').toLowerCase()
  if (cls.includes('checkbox') || cls.includes('xcuielementtypecheckbox'))
    return 'checked'
  if (cls.includes('radiobutton') || cls.includes('xcuielementtyperadiobutton'))
    return 'selected'
  if (cls.includes('switch') && !cls.includes('viewswitcher') && !cls.includes('tabswitch'))
    return 'enabled'
  if (cls.includes('imageview') || cls.includes('xcuielementtypeimage'))
    return 'exists'
  if (cls.includes('recyclerview') || cls.includes('listview') ||
      cls.includes('scrollview') || cls.includes('xcuielementtypetable') ||
      cls.includes('xcuielementtypescrollview'))
    return 'exists'
  if (el.elType === 'text' || cls.includes('textview') || cls.includes('xcuielementtypestatictext'))
    return 'text'
  return 'visible'
}

// ── NamingEngine ── Centralized name-resolution service ──────────────────────
//
// All name resolution in Record Studio passes through these four methods.
// No generator, formatter, or helper may compute variable / method / assertion
// names by any other means.  This guarantees that a field declared as
// "btnContinuar" is always referenced as "btnContinuar" — never re-derived.
//
// Pipeline position:
//   RecordedElement → LocatorResolver → ElementClassifier
//     → NamingEngine  ← YOU ARE HERE
//       → ValidationEngine → PageObjectGenerator → TestGenerator

const NamingEngine = {
  /**
   * Java field name for a Page Object element.
   *   1. annotationVar  — backend-declared name overrides everything
   *   2. prefix + stem  — type prefix + semantic content ("btnContinuar")
   *   3. prefix only    — no semantic content ("btn", "lbl" …)
   */
  resolveVariableName(
    el:            AppEl,
    enrichedStem:  string | null,
    annotationVar: string | null,
  ): string {
    if (annotationVar) return annotationVar
    const prefix = prefixForEl(el)
    return enrichedStem ? `${prefix}${cap(enrichedStem)}` : prefix
  },

  /**
   * Page Object method name.
   * Returns '' when there is no semantic content — callers must omit the method.
   */
  resolveMethodName(el: AppEl, effectiveStem: string | null): string {
    if (!effectiveStem) return ''
    return el.elType === 'input'
      ? `ingresar${effectiveStem}`
      : smartMethodName(effectiveStem)
  },

  /** Assertion classification for AssertionGenerator. */
  resolveAssertionName(el: AppEl): AssertKind {
    return elementAssertKind(el)
  },

  /**
   * Human-readable label for UI chips, debug panels, and validation messages.
   * Never returns an empty string — falls back to shortId as last resort.
   */
  resolveReadableName(el: AppEl): string {
    return el.accessId?.trim()
        || el.text?.trim()
        || el.accessibilityLabel?.trim()
        || parseInputResourceId(el.resourceId ?? '')
        || ((el.className ?? '').split(/[./]/).pop()?.replace(/xcuielementtype/i, '') ?? '')
        || el.shortId
  },
}

// ── ValidationEngine ── Pre-generation consistency checks ────────────────────
//
// Called once after buildRecordedElements, before any code is emitted.
// Errors block generation entirely and are shown in the preview panel.
// Warnings are included as comment headers in the generated output.
//
// Rules:
//   E1 – No duplicate variable names per screen
//   E2 – No duplicate locators per screen
//   E3 – Every method references a variable declared in this screen
//   W1 – Elements without a resolved locator (will be silently skipped)
//   W2 – Declared elements not referenced by any recorded step
//   W3 – Generated methods never called in the test body

interface ValidationIssue {
  level:   'error' | 'warning'
  code:    string
  message: string
}

interface ValidationResult {
  valid:   boolean           // false → do not emit code
  issues:  ValidationIssue[]
}

const ValidationEngine = {
  validate(
    registries: Map<string, Map<string, RecordedElement>>,
    steps:      RecStep[],
  ): ValidationResult {
    const issues: ValidationIssue[] = []

    for (const [screen, registry] of registries) {
      const declaredVars = new Map<string, string>()  // varName  → shortId
      const declaredLocs = new Map<string, string>()  // locKey   → shortId

      const referencedIds = new Set(
        steps
          .filter(s => (s.screenName?.trim() || 'App') === screen && s.el)
          .map(s => s.el!.shortId)
      )
      const calledMethods = new Set(
        steps
          .filter(s => (s.screenName?.trim() || 'App') === screen && s.el
                        && s.type !== 'screenshot' && s.type !== 'assertion')
          .map(s => registry.get(s.el!.shortId)?.methodName ?? '')
          .filter(Boolean)
      )

      for (const [, rec] of registry) {
        if (rec.isDuplicate) continue

        // W1 — no locator resolved
        if (!rec.locator) {
          issues.push({ level: 'warning', code: 'W1_NO_LOCATOR',
            message: `"${screen}" › "${rec.readableName}" no tiene locator — se omitirá` })
          continue
        }

        // E1 — duplicate variable name
        const prev = declaredVars.get(rec.variableName)
        if (prev && prev !== rec.id) {
          issues.push({ level: 'error', code: 'E1_DUPLICATE_VAR',
            message: `"${screen}" › nombre duplicado "${rec.variableName}" (${rec.id} y ${prev})` })
        } else {
          declaredVars.set(rec.variableName, rec.id)
        }

        // E2 — duplicate locator
        const locKey = `${rec.locator.strategy}::${rec.locator.value}`
        const dupLoc = declaredLocs.get(locKey)
        if (dupLoc && dupLoc !== rec.id) {
          issues.push({ level: 'error', code: 'E2_DUPLICATE_LOCATOR',
            message: `"${screen}" › locator duplicado [${rec.locator.strategy}] en "${rec.variableName}" y "${dupLoc}"` })
        } else {
          declaredLocs.set(locKey, rec.id)
        }
      }

      // E3 — method references an undeclared variable
      for (const [, rec] of registry) {
        if (!rec.methodName || rec.isDuplicate || !rec.locator) continue
        if (!declaredVars.has(rec.variableName)) {
          issues.push({ level: 'error', code: 'E3_ORPHAN_METHOD',
            message: `"${screen}" › método "${rec.methodName}()" → variable "${rec.variableName}" no declarada` })
        }
      }

      // W2 — element declared but no step references it
      for (const [shortId, rec] of registry) {
        if (rec.isDuplicate || !rec.locator || !rec.methodName) continue
        if (!referencedIds.has(shortId)) {
          issues.push({ level: 'warning', code: 'W2_UNUSED_ELEMENT',
            message: `"${screen}" › "${rec.variableName}" declarado pero sin uso en pasos grabados` })
        }
      }

      // W3 — method generated but never called from a step
      for (const [, rec] of registry) {
        if (!rec.methodName || rec.isDuplicate || !rec.locator) continue
        if (!calledMethods.has(rec.methodName)) {
          issues.push({ level: 'warning', code: 'W3_UNUSED_METHOD',
            message: `"${screen}" › método "${rec.methodName}()" generado pero no llamado desde ningún paso` })
        }
      }
    }

    return { valid: issues.filter(i => i.level === 'error').length === 0, issues }
  },

  /** Formats validation issues as a Java comment block for the code preview. */
  formatAsComments(result: ValidationResult): string {
    const errors   = result.issues.filter(i => i.level === 'error')
    const warnings = result.issues.filter(i => i.level === 'warning')
    const lines: string[] = []

    if (!result.valid) {
      lines.push('// ╔══════════════════════════════════════════════════════════════╗')
      lines.push('// ║  ❌  ValidationEngine — generación bloqueada                 ║')
      lines.push('// ╚══════════════════════════════════════════════════════════════╝')
      lines.push('//')
      lines.push('// Corrija los siguientes errores antes de generar código:')
      errors.forEach(e => lines.push(`//  [${e.code}]  ${e.message}`))
      if (warnings.length > 0) {
        lines.push('//')
        lines.push('// Advertencias adicionales:')
        warnings.forEach(w => lines.push(`//  [${w.code}]  ${w.message}`))
      }
    } else if (warnings.length > 0) {
      lines.push('// ╔══════════════════════════════════════════════════════════════╗')
      lines.push('// ║  ⚠   ValidationEngine — advertencias                        ║')
      lines.push('// ╚══════════════════════════════════════════════════════════════╝')
      warnings.forEach(w => lines.push(`//  [${w.code}]  ${w.message}`))
      lines.push('//')
    }

    return lines.length > 0 ? lines.join('\n') + '\n' : ''
  },
}

/**
 * Generates Java assertion lines for a given element reference.
 * ctx='tap'  → only state-changing assertions (chk/rdo/sw); skips btn/lbl to
 *              avoid asserting an element that may have navigated away.
 * ctx='assertion' → full contextual assertions.
 */
function javaSmartAssert(el: AppEl, ref: string, ctx: 'tap' | 'assertion'): string[] {
  const out: string[] = []
  const prefix = ref.match(/^([a-z]+)/)?.[1] ?? ''
  switch (prefix) {
    case 'chk':
      out.push(`        assertChecked(${ref});`)
      break
    case 'rdo':
      out.push(`        assertSelected(${ref});`)
      break
    case 'sw':
      out.push(`        assertEnabled(${ref});`)
      break
    case 'lbl': {
      const txt = el.text?.trim()
      if (txt) out.push(`        assertText(${ref}, "${txt}");`)
      else if (ctx === 'assertion') out.push(`        assertVisible(${ref});`)
      break
    }
    case 'img': case 'lst': case 'scr': case 'rv':
      out.push(`        assertExists(${ref});`)
      break
    case 'btn':
      if (ctx === 'assertion') {
        out.push(`        assertVisible(${ref});`)
        out.push(`        assertEnabled(${ref});`)
      }
      break
    default:
      if (ctx === 'assertion') out.push(`        assertVisible(${ref});`)
  }
  return out
}

// All locator strategies produced by the backend AccessibilityInspector or this engine.
type LocatorStrategy =
  | 'id'               // Android resource-id
  | 'accessibility_id' // Android content-desc / iOS accessibility identifier
  | 'uiautomator'      // Android UiSelector expression — more reliable than @text XPath
  | 'text_xpath'       // legacy: element text via xpath @text (kept for stored sessions)
  | 'xpath'            // explicit xpath (class-based fallback — last resort only)
  | 'predicate_string' // iOS NSPredicate  e.g. label == "Login"
  | 'class_chain'      // iOS XCUITest class chain

type LocatorResult = { strategy: LocatorStrategy; value: string }

// Resource IDs that carry no semantic signal and should be skipped in favour of
// content-desc, text, or UiSelector.  Matches fully-qualified class paths and
// generic slot names like :id/container, :id/root, :id/item_3, etc.
const GENERIC_RESOURCE_ID_RE = [
  /^android\.(view|widget|support|graphics|app)\./i,
  /^androidx\./i,
  /^com\.android\./i,
  /:id\/(container|wrapper|root|frame|layout|view|group|scroll|recycler|pager|page|content|inner|outer|main|body|header|footer|toolbar|nav|tab|cell|row|item|card|panel|box|surface)(_\w+)?$/i,
  /:id\/\d+$/,
]

function isGenericResourceId(id: string): boolean {
  return !id.trim() || GENERIC_RESOURCE_ID_RE.some(p => p.test(id))
}

// ── LocatorResolver ── Phase 1, 10 ───────────────────────────────────────────
// When a backend XPath carries an attribute that maps to a better strategy
// (resource-id, content-desc, @text, @label), extract it so we never emit XPath
// when a semantic locator is available inside the XPath itself.
function extractFromXPath(xpath: string, isIOS: boolean): LocatorResult | null {
  if (!isIOS) {
    const idM = xpath.match(/@resource-id=['"]([^'"]+)['"]/i)
    if (idM && !isGenericResourceId(idM[1]))
      return { strategy: 'id', value: idM[1] }
    const descM = xpath.match(/@content-desc=['"]([^'"]+)['"]/i)
    if (descM && descM[1].trim())
      return { strategy: 'accessibility_id', value: descM[1] }
    const textM = xpath.match(/@text=['"]([^'"]+)['"]/i)
    if (textM && textM[1].trim())
      return { strategy: 'uiautomator', value: `new UiSelector().text("${textM[1]}")` }
  } else {
    const labelM = xpath.match(/@label=['"]([^'"]+)['"]/i)
    if (labelM && labelM[1].trim())
      return { strategy: 'predicate_string', value: `label == "${labelM[1]}"` }
    const nameM = xpath.match(/@name=['"]([^'"]+)['"]/i)
    if (nameM && nameM[1].trim())
      return { strategy: 'accessibility_id', value: nameM[1] }
    const valueM = xpath.match(/@value=['"]([^'"]+)['"]/i)
    if (valueM && valueM[1].trim())
      return { strategy: 'predicate_string', value: `value == "${valueM[1]}"` }
  }
  return null
}

/**
 * Resolves the best available locator for an element using platform-aware
 * priority chains recommended for professional Appium test suites.
 *
 * Android: accessibilityId → resource-id (non-generic) → content-desc → text (UiSelector) → XPath mining → xpath
 * iOS:     accessibilityId → accessibilityLabel → text (predicate label) → predicate → class chain → XPath mining → xpath
 *
 * XPath is emitted ONLY as absolute last resort — Phase 10 mines attributes
 * out of incoming XPaths before ever emitting a raw XPath locator.
 */
function resolveLocator(el: AppEl | null): LocatorResult | null {
  if (!el) return null
  const isIOS = el.platform === 'ios'

  if (!isIOS) {
    if (el.accessId?.trim())
      return { strategy: 'accessibility_id', value: el.accessId }
    if (el.resourceId?.trim() && !isGenericResourceId(el.resourceId))
      return { strategy: 'id', value: el.resourceId }
    if (el.accessibilityLabel?.trim())
      return { strategy: 'accessibility_id', value: el.accessibilityLabel }
    if (el.text?.trim())
      return { strategy: 'uiautomator', value: `new UiSelector().text("${el.text}")` }
    if (el.locatorStrategy === 'uiautomator' && el.locatorValue?.trim())
      return { strategy: 'uiautomator', value: el.locatorValue }
    if (el.locatorValue?.trim()) {
      const mined = extractFromXPath(el.locatorValue, false)
      return mined ?? { strategy: 'xpath', value: el.locatorValue }
    }
  } else {
    if (el.accessId?.trim())
      return { strategy: 'accessibility_id', value: el.accessId }
    if (el.accessibilityLabel?.trim())
      return { strategy: 'accessibility_id', value: el.accessibilityLabel }
    if (el.text?.trim())
      return { strategy: 'predicate_string', value: `label == "${el.text}"` }
    if (el.locatorStrategy === 'predicate_string' && el.locatorValue?.trim())
      return { strategy: 'predicate_string', value: el.locatorValue }
    if (el.locatorStrategy === 'class_chain' && el.locatorValue?.trim())
      return { strategy: 'class_chain', value: el.locatorValue }
    if (el.locatorValue?.trim()) {
      const mined = extractFromXPath(el.locatorValue, true)
      return mined ?? { strategy: 'xpath', value: el.locatorValue }
    }
  }
  return null
}

// ── PlatformStrategy ── Phase 11 ─────────────────────────────────────────────
// Single engine for both Android and iOS.
// javaAnnotationStr() is the authoritative mapping from LocatorResult →
// @AndroidFindBy / @iOSXCUITFindBy. Per-language selector formatters delegate
// to resolveLocator() so there is no duplicated priority-chain logic anywhere.

function javaByStr(el: AppEl | null): string {
  const loc = resolveLocator(el)
  if (!loc) return `By.id("REPLACE_ME")`
  switch (loc.strategy) {
    case 'id':               return `By.id("${esc(loc.value)}")`
    case 'accessibility_id': return `AppiumBy.accessibilityId("${esc(loc.value)}")`
    case 'uiautomator':      return `AppiumBy.androidUIAutomator("${esc(loc.value)}")`
    case 'predicate_string': return `AppiumBy.iOSNsPredicateString("${esc(loc.value)}")`
    case 'class_chain':      return `AppiumBy.iOSClassChain("${esc(loc.value)}")`
    case 'text_xpath':       return `AppiumBy.androidUIAutomator("new UiSelector().text(\\"${esc(loc.value)}\\")")`
    case 'xpath':            return `By.xpath("${esc(loc.value)}")`
    default:                 return `By.xpath("${esc(loc.value)}")`
  }
}

function pythonByStr(el: AppEl | null): string {
  const loc = resolveLocator(el)
  if (!loc) return `AppiumBy.ID, "REPLACE_ME"`
  switch (loc.strategy) {
    case 'id':               return `AppiumBy.ID, "${esc(loc.value)}"`
    case 'accessibility_id': return `AppiumBy.ACCESSIBILITY_ID, "${esc(loc.value)}"`
    case 'uiautomator':      return `AppiumBy.ANDROID_UIAUTOMATOR, "${esc(loc.value)}"`
    case 'predicate_string': return `AppiumBy.IOS_PREDICATE, "${esc(loc.value)}"`
    case 'class_chain':      return `AppiumBy.IOS_CLASS_CHAIN, "${esc(loc.value)}"`
    case 'text_xpath':       return `AppiumBy.ANDROID_UIAUTOMATOR, "new UiSelector().text(\\"${esc(loc.value)}\\")"`
    case 'xpath':            return `AppiumBy.XPATH, "${esc(loc.value)}"`
    default:                 return `AppiumBy.XPATH, "${esc(loc.value)}"`
  }
}

function jsByStr(el: AppEl | null, isAndroid: boolean): string {
  const loc = resolveLocator(el)
  if (!loc) return `$('~REPLACE_ME')`
  if (isAndroid) {
    switch (loc.strategy) {
      case 'id':               return `$('android=new UiSelector().resourceId("${esc(loc.value)}")')`
      case 'accessibility_id': return `$('~${esc(loc.value)}')`
      case 'uiautomator':      return `$('android=${loc.value}')`
      case 'text_xpath':       return `$('android=new UiSelector().text("${esc(loc.value)}")')`
      case 'xpath':            return `$('${esc(loc.value)}')`
      default:                 return `$('${esc(loc.value)}')`
    }
  } else {
    switch (loc.strategy) {
      case 'id':               return `$('id:${esc(loc.value)}')`
      case 'accessibility_id': return `$('~${esc(loc.value)}')`
      case 'predicate_string': return `$(\`-ios predicate string:${loc.value}\`)`
      case 'class_chain':      return `$(\`-ios class chain:${loc.value}\`)`
      case 'text_xpath':       return `$(\`-ios predicate string:label == "${esc(loc.value)}"\`)`
      case 'xpath':            return `$('${esc(loc.value)}')`
      default:                 return `$('${esc(loc.value)}')`
    }
  }
}

function csByStr(el: AppEl | null): string {
  const loc = resolveLocator(el)
  if (!loc) return `By.Id("REPLACE_ME")`
  switch (loc.strategy) {
    case 'id':               return `By.Id("${esc(loc.value)}")`
    case 'accessibility_id': return `MobileBy.AccessibilityId("${esc(loc.value)}")`
    case 'uiautomator':      return `MobileBy.AndroidUIAutomator("${esc(loc.value)}")`
    case 'predicate_string': return `MobileBy.IosNSPredicate("${esc(loc.value)}")`
    case 'class_chain':      return `MobileBy.IosClassChain("${esc(loc.value)}")`
    case 'text_xpath':       return `MobileBy.AndroidUIAutomator("new UiSelector().text(\\"${esc(loc.value)}\\")")`
    case 'xpath':            return `By.XPath("${esc(loc.value)}")`
    default:                 return `By.XPath("${esc(loc.value)}")`
  }
}

function kotlinByStr(el: AppEl | null): string {
  const loc = resolveLocator(el)
  if (!loc) return `AppiumBy.id("REPLACE_ME")`
  switch (loc.strategy) {
    case 'id':               return `AppiumBy.id("${esc(loc.value)}")`
    case 'accessibility_id': return `AppiumBy.accessibilityId("${esc(loc.value)}")`
    case 'uiautomator':      return `AppiumBy.androidUIAutomator("${esc(loc.value)}")`
    case 'predicate_string': return `AppiumBy.iOSNsPredicateString("${esc(loc.value)}")`
    case 'class_chain':      return `AppiumBy.iOSClassChain("${esc(loc.value)}")`
    case 'text_xpath':       return `AppiumBy.androidUIAutomator("new UiSelector().text(\\"${esc(loc.value)}\\")")`
    case 'xpath':            return `By.xpath("${esc(loc.value)}")`
    default:                 return `By.xpath("${esc(loc.value)}")`
  }
}

/**
 * PlatformStrategy — Phase 11
 * Maps a LocatorResult to the correct Java Page Object @FindBy annotation.
 *
 * Platform resolution order:
 *   1. el.platform (per-element, set by the recording engine)
 *   2. globalIsAndroid (session-level fallback from the platform dropdown)
 *
 * This is the single engine for both Android and iOS — no duplicated priority
 * logic elsewhere.  All annotation emission in PageObjectGenerator calls here.
 */
function javaAnnotationStr(el: AppEl, loc: LocatorResult, globalIsAndroid: boolean): string {
  const isAndroid = el.platform ? el.platform !== 'ios' : globalIsAndroid
  if (isAndroid) {
    switch (loc.strategy) {
      case 'accessibility_id': return `@AndroidFindBy(accessibility = "${esc(loc.value)}")`
      case 'id':               return `@AndroidFindBy(id = "${esc(loc.value)}")`
      case 'uiautomator':      return `@AndroidFindBy(uiAutomator = "${esc(loc.value)}")`
      case 'text_xpath':       return `@AndroidFindBy(uiAutomator = "new UiSelector().text(\\"${esc(loc.value)}\\")")`
      case 'predicate_string':
      case 'class_chain':
      case 'xpath':
      default:                 return `@AndroidFindBy(xpath = "${esc(loc.value)}")`
    }
  } else {
    switch (loc.strategy) {
      case 'accessibility_id': return `@iOSXCUITFindBy(accessibility = "${esc(loc.value)}")`
      case 'predicate_string': return `@iOSXCUITFindBy(iOSNsPredicate = "${esc(loc.value)}")`
      case 'class_chain':      return `@iOSXCUITFindBy(iOSClassChain = "${esc(loc.value)}")`
      case 'id':               return `@iOSXCUITFindBy(accessibility = "${esc(loc.value)}")`
      case 'uiautomator':      return `@iOSXCUITFindBy(iOSNsPredicate = "${esc(loc.value)}")`
      case 'text_xpath':       return `@iOSXCUITFindBy(iOSNsPredicate = "label == \\"${esc(loc.value)}\\"")`
      case 'xpath':
      default:                 return `@iOSXCUITFindBy(xpath = "${esc(loc.value)}")`
    }
  }
}

function generateJava(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
  lang: Lang,
): string {
  const isAndroid = platform.toUpperCase() !== 'IOS'
  const effectiveTestName = testName.trim() || 'myTest'
  const effectiveClassName = className.trim() || 'GeneratedTest'

  const lines: string[] = []

  // Imports
  lines.push('import io.appium.java_client.AppiumBy;')
  lines.push('import io.appium.java_client.AppiumDriver;')
  lines.push('import io.appium.java_client.android.AndroidDriver;')
  lines.push('import io.appium.java_client.ios.IOSDriver;')
  lines.push('import org.openqa.selenium.By;')
  lines.push('import org.openqa.selenium.WebElement;')
  lines.push('import org.openqa.selenium.support.ui.ExpectedConditions;')
  lines.push('import org.openqa.selenium.support.ui.WebDriverWait;')
  if (lang === 'java-testng') {
    lines.push('import org.testng.Assert;')
    lines.push('import org.testng.annotations.Test;')
  } else {
    lines.push('import org.junit.Assert;')
    lines.push('import org.junit.Test;')
  }
  if (opts.pageObjects) {
    lines.push('import io.appium.java_client.pagefactory.AndroidFindBy;')
    lines.push('import io.appium.java_client.pagefactory.iOSXCUITFindBy;')
    lines.push('import io.appium.java_client.pagefactory.AppiumFieldDecorator;')
    lines.push('import org.openqa.selenium.support.PageFactory;')
    lines.push('import qa.cinepolis.framework.BasePage;')
  }
  if (opts.allureLogs) {
    lines.push('import io.qameta.allure.Allure;')
    lines.push('import io.qameta.allure.Description;')
    lines.push('import io.qameta.allure.Feature;')
    lines.push('import io.qameta.allure.Story;')
  }
  lines.push('')

  // ── Pipeline: RecordedElement → NamingEngine → ValidationEngine ─────────────
  const registries = buildRecordedElements(steps)

  // ── ValidationEngine: verify registry before emitting any code ──────────────
  const validation = ValidationEngine.validate(registries, steps)
  if (!validation.valid) {
    return ValidationEngine.formatAsComments(validation)
  }
  const validationHeader = ValidationEngine.formatAsComments(validation)  // warnings only

  // ── PageObjectGenerator ──────────────────────────────────────────────────────
  if (opts.pageObjects) {
    for (const [screen, screenRegistry] of registries) {
      const pageClass = `${screen}Page`
      lines.push(`public class ${pageClass} extends BasePage {`)
      lines.push('')

      // ── Fields ──
      for (const [, rec] of screenRegistry) {
        if (rec.isDuplicate) continue        // locator already declared via canonical
        if (rec.el.pageObjectAnnotation?.trim()) {
          for (const annLine of rec.el.pageObjectAnnotation.split('\n')) {
            lines.push(`    ${annLine}`)
          }
        } else if (rec.locator) {
          lines.push(`    ${javaAnnotationStr(rec.el, rec.locator, isAndroid)}`)
          lines.push(`    private WebElement ${rec.variableName};`)
        } else {
          lines.push(`    // ⚠ No locator — ${rec.variableName} omitted`)
        }
        lines.push('')
      }

      // ── Constructor ──
      lines.push(`    public ${pageClass}(AppiumDriver driver) {`)
      lines.push(`        super(driver);`)
      lines.push(`        PageFactory.initElements(new AppiumFieldDecorator(driver), this);`)
      lines.push(`    }`)
      lines.push('')

      // ── Action methods ──
      for (const [, rec] of screenRegistry) {
        if (rec.isDuplicate) continue
        if (!rec.locator) continue           // no locator → no usable method
        if (!rec.methodName) continue        // no semantic stem → omit; don't emit garbage name
        if (rec.el.elType === 'input') {
          lines.push(`    public void ${rec.methodName}(String ${rec.paramName}) {`)
          lines.push(`        type(${rec.variableName}, ${rec.paramName});`)
        } else {
          lines.push(`    public void ${rec.methodName}() {`)
          lines.push(`        click(${rec.variableName});`)
        }
        lines.push(`    }`)
        lines.push('')
      }

      lines.push(`}`)
      lines.push('')
    }
  }

  // ── TestGenerator ────────────────────────────────────────────────────────────
  lines.push(`public class ${effectiveClassName} extends BaseTest {`)
  lines.push('')
  if (opts.allureLogs) {
    lines.push(`    @Feature("${effectiveClassName}")`)
    lines.push(`    @Story("${effectiveTestName}")`)
    lines.push(`    @Description("Auto-generated by QAutomation Record Studio")`)
  }
  lines.push(`    @Test`)
  lines.push(`    public void ${effectiveTestName}() {`)

  if (opts.pageObjects) {
    const uniqueScreens = [...new Set(
      steps.filter(s => s.el).map(s => s.screenName?.trim() || 'App')
    )]
    for (const screen of uniqueScreens) {
      lines.push(`        ${screen}Page ${lc(screen)}Page = new ${screen}Page(driver);`)
    }
    if (uniqueScreens.length > 0) lines.push('')
  }

  for (const step of steps) {
    lines.push('')
    const label  = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`        // ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)
    if (opts.allureLogs) {
      lines.push(`        Allure.step("${step.n}. ${label}${elText ? ` — ${elText}` : ''}");`)
    }

    // ── Registry lookup — the ONLY source of element names ───────────────────
    const sc  = step.screenName?.trim() || 'App'
    const rec = step.el ? (registries.get(sc)?.get(step.el.shortId) ?? null) : null

    // Validation: if step needs an element but it's not in the registry, skip it.
    // This prevents click(undefined), click(view), click(lbl6) etc.
    if (step.el && !rec) {
      lines.push(`        // ⚠ Element "${step.el.shortId}" not in registry — step skipped`)
      if (opts.screenshots) lines.push(`        captureScreenshot("step_${step.n}");`)
      continue
    }

    // pageVar is valid only when we have a registered rec
    const pageVar = opts.pageObjects && rec ? `${lc(sc)}Page` : null

    // sel is used for direct-driver paths and smartWaits (always safe — derived from locator, not from names)
    const sel   = javaByStr(step.el)
    const hasEl = !!step.el

    if (opts.smartWaits && hasEl) {
      lines.push(`        waitForElement(${sel});`)
    }
    if (!hasEl && ['tap', 'double_tap', 'long_press', 'input'].includes(step.type)) {
      lines.push(`        // ⚠ Element not found — update selector before running`)
    }

    switch (step.type) {
      case 'tap':
        if (pageVar && rec?.methodName) {
          // Page Object path: method was declared → use it
          lines.push(`        ${pageVar}.${rec.methodName}();`)
        } else {
          // Direct path: element has no usable method name → call driver directly
          lines.push(`        click(${sel});`)
        }
        if (opts.assertions && rec) {
          const assertRef = pageVar && rec.methodName ? rec.variableName : sel
          for (const al of javaSmartAssert(rec.el, assertRef, 'tap')) lines.push(al)
        }
        break
      case 'double_tap':
        lines.push(`        doubleTap(${sel});`)
        break
      case 'long_press':
        lines.push(`        longPress(${sel});`)
        break
      case 'input':
        if (pageVar && rec?.methodName) {
          lines.push(`        ${pageVar}.${rec.methodName}("${step.inputVal ?? ''}");`)
        } else {
          lines.push(`        clear(${sel});`)
          lines.push(`        type(${sel}, "${step.inputVal ?? ''}");`)
        }
        if (opts.assertions) {
          lines.push(`        Assert.assertEquals(getValue(${sel}), "${step.inputVal ?? ''}");`)
        }
        break
      case 'swipe':
        lines.push(`        swipe(Direction.${(step.dir ?? 'UP').toUpperCase()});`)
        break
      case 'scroll': {
        if (rec) {
          const scrollRef = pageVar ? rec.variableName : sel
          lines.push(`        scrollUntilVisible(${scrollRef});`)
        } else {
          const d = step.dir ?? 'down'
          lines.push(`        scroll${d.charAt(0).toUpperCase() + d.slice(1)}();`)
        }
        break
      }
      case 'back':
        lines.push(`        driver.navigate().back();`)
        break
      case 'home':
        lines.push(`        driver.pressKey(new KeyEvent(AndroidKey.HOME));`)
        break
      case 'hide_keyboard':
        lines.push(`        driver.hideKeyboard();`)
        break
      case 'assertion':
        if (rec) {
          const assertRef = pageVar ? rec.variableName : sel
          for (const al of javaSmartAssert(rec.el, assertRef, 'assertion')) lines.push(al)
        } else {
          lines.push(`        // ⚠ Assertion without element — add selector`)
        }
        break
      case 'screenshot':
        lines.push(`        captureScreenshot("step_${step.n}");`)
        break
    }

    if (opts.screenshots && step.type !== 'screenshot') {
      lines.push(`        captureScreenshot("step_${step.n}");`)
    }
  }

  lines.push(`    }`)
  lines.push(`}`)

  return validationHeader + lines.join('\n')
}

function getLangFileExt(lang: Lang): string {
  switch (lang) {
    case 'java-testng': case 'java-junit': return 'java'
    case 'python': return 'py'
    case 'javascript': return 'js'
    case 'csharp': return 'cs'
    case 'kotlin': return 'kt'
  }
}

function toPascalCase(s: string): string {
  return s
    .replace(/[^a-zA-Z0-9]/g, ' ')
    .split(' ')
    .filter(Boolean)
    .map(w => w.charAt(0).toUpperCase() + w.slice(1))
    .join('')
}

function toSnakeCase(s: string): string {
  return s
    .replace(/[^a-zA-Z0-9]/g, '_')
    .replace(/_+/g, '_')
    .toLowerCase()
    .replace(/^_|_$/g, '')
}

function generatePython(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
): string {
  const isAndroid = platform.toUpperCase() !== 'IOS'
  const effectiveClass = toPascalCase(className.trim() || 'GeneratedTest')
  const effectiveTest = toSnakeCase(testName.trim() || 'my_test')
  const lines: string[] = []

  lines.push('import pytest')
  lines.push('from appium import webdriver')
  lines.push('from appium.webdriver.common.appiumby import AppiumBy')
  if (opts.smartWaits) {
    lines.push('from selenium.webdriver.support.ui import WebDriverWait')
    lines.push('from selenium.webdriver.support import expected_conditions as EC')
  }
  if (opts.assertions) lines.push('import pytest')
  lines.push('')
  lines.push('')
  lines.push(`class Test${effectiveClass}:`)
  lines.push('')
  lines.push(`    def test_${effectiveTest}(self, driver):`)

  for (const step of steps) {
    lines.push('')
    const label = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`        # ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)

    const sel = pythonByStr(step.el)
    const hasEl = !!step.el

    if (opts.smartWaits && hasEl) {
      lines.push(`        WebDriverWait(driver, 10).until(EC.presence_of_element_located((${sel})))`)
    }
    if (!hasEl && ['tap', 'double_tap', 'long_press', 'input'].includes(step.type)) {
      lines.push(`        # ⚠ Element not found — update selector before running`)
    }

    switch (step.type) {
      case 'tap':
        lines.push(`        driver.find_element(${sel}).click()`)
        break
      case 'double_tap':
        lines.push(`        el = driver.find_element(${sel})`)
        lines.push(`        from appium.webdriver.common.touch_action import TouchAction`)
        lines.push(`        TouchAction(driver).tap(el).tap(el).perform()`)
        break
      case 'long_press':
        lines.push(`        el = driver.find_element(${sel})`)
        lines.push(`        from appium.webdriver.common.touch_action import TouchAction`)
        lines.push(`        TouchAction(driver).long_press(el, duration=1000).perform()`)
        break
      case 'input':
        lines.push(`        el = driver.find_element(${sel})`)
        lines.push(`        el.clear()`)
        lines.push(`        el.send_keys("${step.inputVal ?? ''}")`)
        if (opts.assertions) {
          lines.push(`        assert driver.find_element(${sel}).get_attribute("text") == "${step.inputVal ?? ''}"`)
        }
        break
      case 'swipe':
        lines.push(`        driver.execute_script("mobile: swipe", {"direction": "${step.dir ?? 'up'}"})`)
        break
      case 'scroll':
        if (step.el) {
          lines.push(`        scroll_target = driver.find_element(${sel})`)
          lines.push(`        driver.execute_script("mobile: scrollTo", {"element": scroll_target})`)
        } else {
          lines.push(`        driver.execute_script("mobile: scroll", {"direction": "${step.dir ?? 'down'}"})`)
        }
        break
      case 'back':
        lines.push(`        driver.press_keycode(4)  # KEYCODE_BACK`)
        break
      case 'home':
        lines.push(`        driver.press_keycode(3)  # KEYCODE_HOME`)
        break
      case 'hide_keyboard':
        lines.push(`        driver.hide_keyboard()`)
        break
      case 'assertion': {
        const kind = step.el ? elementAssertKind(step.el) : 'visible'
        const elTxt = step.el?.text?.trim() ?? ''
        switch (kind) {
          case 'checked':
            lines.push(`        assert driver.find_element(${sel}).get_attribute("checked") == "true"`)
            break
          case 'selected':
            lines.push(`        assert driver.find_element(${sel}).get_attribute("selected") == "true"`)
            break
          case 'enabled':
            lines.push(`        assert driver.find_element(${sel}).is_enabled()`)
            break
          case 'text':
            if (elTxt) lines.push(`        assert driver.find_element(${sel}).get_attribute("text") == "${elTxt}"`)
            else       lines.push(`        assert driver.find_element(${sel}).is_displayed()`)
            break
          case 'exists':
            lines.push(`        assert len(driver.find_elements(${sel})) > 0`)
            break
          default:
            lines.push(`        assert driver.find_element(${sel}).is_displayed()`)
        }
        break
      }
      case 'screenshot':
        lines.push(`        driver.save_screenshot(f"screenshot_step_${step.n}.png")`)
        break
    }
    if (opts.screenshots && step.type !== 'screenshot') {
      lines.push(`        driver.save_screenshot(f"step_${step.n}.png")`)
    }
  }

  return lines.join('\n')
}

function generateJavaScript(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
): string {
  const isAndroid = platform.toUpperCase() !== 'IOS'
  const effectiveClass = className.trim() || 'GeneratedTest'
  const effectiveTest = testName.trim() || 'myTest'
  const lines: string[] = []

  lines.push(`const { remote } = require('webdriverio')`)
  lines.push('')
  lines.push(`describe('${effectiveClass}', () => {`)
  lines.push(`  it('${effectiveTest}', async () => {`)

  for (const step of steps) {
    lines.push('')
    const label = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`    // ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)

    const sel = jsByStr(step.el, isAndroid)
    const hasEl = !!step.el

    if (opts.smartWaits && hasEl) {
      lines.push(`    await ${sel}.waitForDisplayed({ timeout: 10000 })`)
    }
    if (!hasEl && ['tap', 'double_tap', 'long_press', 'input'].includes(step.type)) {
      lines.push(`    // ⚠ Element not found — update selector before running`)
    }

    switch (step.type) {
      case 'tap':
        lines.push(`    await ${sel}.click()`)
        break
      case 'double_tap':
        lines.push(`    await ${sel}.doubleClick()`)
        break
      case 'long_press':
        lines.push(`    await browser.touchAction([`)
        lines.push(`      { action: 'longPress', element: await ${sel} },`)
        lines.push(`      { action: 'release' }`)
        lines.push(`    ])`)
        break
      case 'input':
        lines.push(`    await ${sel}.clearValue()`)
        lines.push(`    await ${sel}.setValue('${step.inputVal ?? ''}')`)
        if (opts.assertions) {
          lines.push(`    expect(await ${sel}.getValue()).toBe('${step.inputVal ?? ''}')`)
        }
        break
      case 'swipe':
        lines.push(`    await browser.execute('mobile: swipe', { direction: '${step.dir ?? 'up'}' })`)
        break
      case 'scroll':
        if (step.el) {
          lines.push(`    const scrollTarget = await ${sel}`)
          lines.push(`    await browser.execute('mobile: scrollTo', { element: scrollTarget })`)
        } else {
          lines.push(`    await browser.execute('mobile: scroll', { direction: '${step.dir ?? 'down'}' })`)
        }
        break
      case 'back':
        lines.push(`    await driver.pressKeyCode(4) // KEYCODE_BACK`)
        break
      case 'home':
        lines.push(`    await driver.pressKeyCode(3) // KEYCODE_HOME`)
        break
      case 'hide_keyboard':
        lines.push(`    await driver.hideKeyboard()`)
        break
      case 'assertion': {
        const kind = step.el ? elementAssertKind(step.el) : 'visible'
        const elTxt = step.el?.text?.trim() ?? ''
        switch (kind) {
          case 'checked':
            lines.push(`    expect(await ${sel}).toHaveAttr('checked', 'true')`)
            break
          case 'selected':
            lines.push(`    expect(await ${sel}).toHaveAttr('selected', 'true')`)
            break
          case 'enabled':
            lines.push(`    expect(await ${sel}).toBeEnabled()`)
            break
          case 'text':
            if (elTxt) lines.push(`    expect(await ${sel}).toHaveText('${elTxt}')`)
            else       lines.push(`    expect(await ${sel}).toBeDisplayed()`)
            break
          case 'exists':
            lines.push(`    expect(await ${sel}).toExist()`)
            break
          default:
            lines.push(`    expect(await ${sel}).toBeDisplayed()`)
        }
        break
      }
      case 'screenshot':
        lines.push(`    await browser.saveScreenshot('./step_${step.n}.png')`)
        break
    }
    if (opts.screenshots && step.type !== 'screenshot') {
      lines.push(`    await browser.saveScreenshot('./step_${step.n}.png')`)
    }
  }

  lines.push(`  })`)
  lines.push(`})`)

  return lines.join('\n')
}

function generateCSharp(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
): string {
  const isAndroid = platform.toUpperCase() !== 'IOS'
  const effectiveClass = toPascalCase(className.trim() || 'GeneratedTest')
  const effectiveTest = toPascalCase(testName.trim() || 'MyTest')
  const lines: string[] = []

  lines.push('using NUnit.Framework;')
  lines.push('using OpenQA.Selenium;')
  lines.push('using OpenQA.Selenium.Appium;')
  lines.push('using OpenQA.Selenium.Appium.Android;')
  if (opts.smartWaits) {
    lines.push('using OpenQA.Selenium.Support.UI;')
    lines.push('using SeleniumExtras.WaitHelpers;')
  }
  lines.push('')
  lines.push(`namespace ${effectiveClass}Tests`)
  lines.push('{')
  lines.push('    [TestFixture]')
  lines.push(`    public class ${effectiveClass} : BaseTest`)
  lines.push('    {')
  lines.push('        [Test]')
  lines.push(`        public void ${effectiveTest}()`)
  lines.push('        {')

  for (const step of steps) {
    lines.push('')
    const label = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`            // ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)

    const byStr = csByStr(step.el)
    const hasEl = !!step.el

    if (opts.smartWaits && hasEl) {
      lines.push(`            new WebDriverWait(_driver, TimeSpan.FromSeconds(10))`)
      lines.push(`                .Until(ExpectedConditions.ElementExists(${byStr}));`)
    }
    if (!hasEl && ['tap', 'double_tap', 'long_press', 'input'].includes(step.type)) {
      lines.push(`            // ⚠ Element not found — update selector before running`)
    }

    switch (step.type) {
      case 'tap':
        lines.push(`            _driver.FindElement(${byStr}).Click();`)
        break
      case 'double_tap':
        lines.push(`            var el${step.n} = _driver.FindElement(${byStr});`)
        lines.push(`            new Actions(_driver).DoubleClick(el${step.n}).Perform();`)
        break
      case 'long_press':
        lines.push(`            var el${step.n} = _driver.FindElement(${byStr});`)
        lines.push(`            new Actions(_driver).ClickAndHold(el${step.n}).Pause(TimeSpan.FromSeconds(1)).Release().Perform();`)
        break
      case 'input':
        lines.push(`            var el${step.n} = _driver.FindElement(${byStr});`)
        lines.push(`            el${step.n}.Clear();`)
        lines.push(`            el${step.n}.SendKeys("${step.inputVal ?? ''}");`)
        if (opts.assertions) {
          lines.push(`            Assert.AreEqual("${step.inputVal ?? ''}", _driver.FindElement(${byStr}).GetAttribute("text"));`)
        }
        break
      case 'swipe':
        lines.push(`            _driver.ExecuteScript("mobile: swipe", new Dictionary<string, string> { { "direction", "${step.dir ?? 'up'}" } });`)
        break
      case 'scroll':
        if (step.el) {
          lines.push(`            var scrollTarget${step.n} = _driver.FindElement(${byStr});`)
          lines.push(`            ((IJavaScriptExecutor)_driver).ExecuteScript("mobile: scrollTo", new Dictionary<string, object> { { "element", scrollTarget${step.n} } });`)
        } else {
          lines.push(`            _driver.ExecuteScript("mobile: scroll", new Dictionary<string, string> { { "direction", "${step.dir ?? 'down'}" } });`)
        }
        break
      case 'back':
        lines.push(`            _driver.Navigate().Back();`)
        break
      case 'home':
        lines.push(`            _driver.PressKeyCode(AndroidKeyCode.Home);`)
        break
      case 'hide_keyboard':
        lines.push(`            _driver.HideKeyboard();`)
        break
      case 'assertion': {
        const kind = step.el ? elementAssertKind(step.el) : 'visible'
        const elTxt = step.el?.text?.trim() ?? ''
        switch (kind) {
          case 'checked':
            lines.push(`            Assert.AreEqual("true", _driver.FindElement(${byStr}).GetAttribute("checked"));`)
            break
          case 'selected':
            lines.push(`            Assert.AreEqual("true", _driver.FindElement(${byStr}).GetAttribute("selected"));`)
            break
          case 'enabled':
            lines.push(`            Assert.IsTrue(_driver.FindElement(${byStr}).Enabled);`)
            break
          case 'text':
            if (elTxt) lines.push(`            Assert.AreEqual("${elTxt}", _driver.FindElement(${byStr}).GetAttribute("text"));`)
            else       lines.push(`            Assert.IsTrue(_driver.FindElement(${byStr}).Displayed);`)
            break
          case 'exists':
            lines.push(`            Assert.IsNotEmpty(_driver.FindElements(${byStr}));`)
            break
          default:
            lines.push(`            Assert.IsTrue(_driver.FindElement(${byStr}).Displayed);`)
        }
        break
      }
      case 'screenshot':
        lines.push(`            ((ITakesScreenshot)_driver).GetScreenshot().SaveAsFile($"step_${step.n}.png");`)
        break
    }
    if (opts.screenshots && step.type !== 'screenshot') {
      lines.push(`            ((ITakesScreenshot)_driver).GetScreenshot().SaveAsFile($"step_${step.n}.png");`)
    }
  }

  lines.push(`        }`)
  lines.push(`    }`)
  lines.push(`}`)

  return lines.join('\n')
}

function generateKotlin(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
): string {
  const isAndroid = platform.toUpperCase() !== 'IOS'
  const effectiveClass = toPascalCase(className.trim() || 'GeneratedTest')
  const effectiveTest = testName.trim() || 'myTest'
  const lines: string[] = []

  lines.push('import io.appium.java_client.AppiumBy')
  lines.push('import io.appium.java_client.android.AndroidDriver')
  lines.push('import org.junit.jupiter.api.Test')
  if (opts.smartWaits) {
    lines.push('import org.openqa.selenium.support.ui.WebDriverWait')
    lines.push('import org.openqa.selenium.support.ui.ExpectedConditions')
  }
  if (opts.assertions) lines.push('import org.junit.jupiter.api.Assertions.*')
  lines.push('')
  lines.push(`class ${effectiveClass} : BaseTest() {`)
  lines.push('')
  lines.push('    @Test')
  lines.push(`    fun \`${effectiveTest}\`() {`)

  for (const step of steps) {
    lines.push('')
    const label = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`        // ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)

    const byExpr = kotlinByStr(step.el)
    const hasEl = !!step.el

    if (opts.smartWaits && hasEl) {
      lines.push(`        WebDriverWait(driver, 10).until(ExpectedConditions.presenceOfElementLocated(${byExpr}))`)
    }
    if (!hasEl && ['tap', 'double_tap', 'long_press', 'input'].includes(step.type)) {
      lines.push(`        // ⚠ Element not found — update selector before running`)
    }

    switch (step.type) {
      case 'tap':
        lines.push(`        driver.findElement(${byExpr}).click()`)
        break
      case 'double_tap':
        lines.push(`        val el${step.n} = driver.findElement(${byExpr})`)
        lines.push(`        Actions(driver).doubleClick(el${step.n}).perform()`)
        break
      case 'long_press':
        lines.push(`        val el${step.n} = driver.findElement(${byExpr})`)
        lines.push(`        Actions(driver).clickAndHold(el${step.n}).pause(1000).release().perform()`)
        break
      case 'input':
        lines.push(`        val el${step.n} = driver.findElement(${byExpr})`)
        lines.push(`        el${step.n}.clear()`)
        lines.push(`        el${step.n}.sendKeys("${step.inputVal ?? ''}")`)
        if (opts.assertions) {
          lines.push(`        assertEquals("${step.inputVal ?? ''}", driver.findElement(${byExpr}).getAttribute("text"))`)
        }
        break
      case 'swipe':
        lines.push(`        driver.executeScript("mobile: swipe", mapOf("direction" to "${step.dir ?? 'up'}"))`)
        break
      case 'scroll':
        if (step.el) {
          lines.push(`        val scrollTarget${step.n} = driver.findElement(${byExpr})`)
          lines.push(`        driver.executeScript("mobile: scrollTo", mapOf("element" to scrollTarget${step.n}))`)
        } else {
          lines.push(`        driver.executeScript("mobile: scroll", mapOf("direction" to "${step.dir ?? 'down'}"))`)
        }
        break
      case 'back':
        lines.push(`        driver.navigate().back()`)
        break
      case 'home':
        lines.push(`        (driver as AndroidDriver).pressKey(KeyEvent(AndroidKey.HOME))`)
        break
      case 'hide_keyboard':
        lines.push(`        driver.hideKeyboard()`)
        break
      case 'assertion': {
        const kind = step.el ? elementAssertKind(step.el) : 'visible'
        const elTxt = step.el?.text?.trim() ?? ''
        switch (kind) {
          case 'checked':
            lines.push(`        assertEquals("true", driver.findElement(${byExpr}).getAttribute("checked"))`)
            break
          case 'selected':
            lines.push(`        assertEquals("true", driver.findElement(${byExpr}).getAttribute("selected"))`)
            break
          case 'enabled':
            lines.push(`        assertTrue(driver.findElement(${byExpr}).isEnabled)`)
            break
          case 'text':
            if (elTxt) lines.push(`        assertEquals("${elTxt}", driver.findElement(${byExpr}).getAttribute("text"))`)
            else       lines.push(`        assertTrue(driver.findElement(${byExpr}).isDisplayed)`)
            break
          case 'exists':
            lines.push(`        assertTrue(driver.findElements(${byExpr}).isNotEmpty())`)
            break
          default:
            lines.push(`        assertTrue(driver.findElement(${byExpr}).isDisplayed)`)
        }
        break
      }
      case 'screenshot':
        lines.push(`        (driver as TakesScreenshot).getScreenshotAs(OutputType.FILE).copyTo(File("step_${step.n}.png"))`)
        break
    }
    if (opts.screenshots && step.type !== 'screenshot') {
      lines.push(`        (driver as TakesScreenshot).getScreenshotAs(OutputType.FILE).copyTo(File("step_${step.n}.png"))`)
    }
  }

  lines.push(`    }`)
  lines.push(`}`)

  return lines.join('\n')
}

function generateCode(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
  lang: Lang,
): string {
  switch (lang) {
    case 'java-testng':
    case 'java-junit':
      return generateJava(steps, opts, platform, testName, className, lang)
    case 'python':
      return generatePython(steps, opts, platform, testName, className)
    case 'javascript':
      return generateJavaScript(steps, opts, platform, testName, className)
    case 'csharp':
      return generateCSharp(steps, opts, platform, testName, className)
    case 'kotlin':
      return generateKotlin(steps, opts, platform, testName, className)
  }
}

function generateXML(steps: RecStep[], platform: string): string {
  const lines: string[] = []
  lines.push('<?xml version="1.0" encoding="UTF-8"?>')
  lines.push(`<recording platform="${platform.toUpperCase()}" steps="${steps.length}">`)
  for (const step of steps) {
    lines.push(`  <step type="${step.type}" timestamp="${step.timeStr}" n="${step.n}">`)
    if (step.el) {
      lines.push(`    <element`)
      lines.push(`      resource-id="${step.el.resourceId}"`)
      lines.push(`      content-desc="${step.el.accessId}"`)
      lines.push(`      text="${step.el.text}"`)
      lines.push(`    />`)
    }
    if (step.inputVal) {
      lines.push(`    <value>${step.inputVal}</value>`)
    }
    if (step.dir) {
      lines.push(`    <direction>${step.dir}</direction>`)
    }
    lines.push(`  </step>`)
  }
  lines.push('</recording>')
  return lines.join('\n')
}

// ─── Syntax highlighter ───────────────────────────────────────────────────────

const KW_JAVA = /\b(public|void|class|extends|static|private|new|return|if|else|for|while|this|import|package|final|boolean|int|String|true|false|null)\b/g
const KW_PYTHON = /\b(import|from|def|class|self|return|if|else|elif|for|while|True|False|None|async|await|with|as|assert|not|and|or|in|is|lambda|pass|yield)\b/g
const KW_JS = /\b(const|let|var|function|class|async|await|return|if|else|for|while|new|import|require|export|default|true|false|null|undefined|this|of|in)\b/g
const KW_CS = /\b(using|namespace|public|private|protected|class|void|string|var|new|return|if|else|for|while|foreach|true|false|null|async|await|static|override|virtual|readonly)\b/g
const KW_KT = /\b(import|fun|class|val|var|return|if|else|for|while|when|true|false|null|object|companion|override|private|public|protected|by|is|as|in|this|it)\b/g

function SyntaxLine({ line, lang = 'java-testng' }: { line: string; lang?: Lang }) {
  const trimmed = line.trimStart()

  // Comments — all languages
  if (trimmed.startsWith('//') || trimmed.startsWith('#')) {
    return <span style={{ color: '#6a9955' }}>{line}</span>
  }

  // Decorators/annotations
  if (trimmed.startsWith('@')) {
    return <span style={{ color: '#c586c0' }}>{line}</span>
  }

  // Import lines
  if (
    trimmed.startsWith('import ') ||
    trimmed.startsWith('from ') ||
    trimmed.startsWith('using ') ||
    trimmed.startsWith('require(')
  ) {
    return <span style={{ color: '#4fc1ff' }}>{line}</span>
  }

  const kwPattern =
    lang === 'python' ? KW_PYTHON :
    lang === 'javascript' ? KW_JS :
    lang === 'csharp' ? KW_CS :
    lang === 'kotlin' ? KW_KT :
    KW_JAVA

  // Split by string literals (single or double quoted)
  const parts = line.split(/(\"[^\"]*\"|'[^']*')/g)

  return (
    <span>
      {parts.map((part, i) => {
        if (
          ((part.startsWith('"') && part.endsWith('"')) ||
            (part.startsWith("'") && part.endsWith("'"))) &&
          part.length >= 2
        ) {
          return <span key={i} style={{ color: '#ce9178' }}>{part}</span>
        }
        const kwRe = new RegExp(kwPattern.source, 'g')
        const subparts = part.split(kwRe)
        const kwMatches = part.match(kwRe) ?? []
        if (kwMatches.length === 0) {
          return <span key={i} style={{ color: '#d4d4d4' }}>{part}</span>
        }
        let kwIdx = 0
        return (
          <span key={i}>
            {subparts.map((sp, j) => {
              if (j % 2 === 1) {
                const kw = kwMatches[kwIdx++]
                return <span key={j} style={{ color: '#569cd6' }}>{kw}</span>
              }
              return <span key={j} style={{ color: '#d4d4d4' }}>{sp}</span>
            })}
          </span>
        )
      })}
    </span>
  )
}

// ─── RecordableEl ─────────────────────────────────────────────────────────────

interface RecordableElProps {
  el: AppEl
  recording: boolean
  onRecord: (el: AppEl) => void
  children: React.ReactNode
  style?: React.CSSProperties
  className?: string
  inspectedElId?: string
}

const RecordableEl = React.memo(function RecordableEl({
  el,
  recording,
  onRecord,
  children,
  style,
  className,
  inspectedElId,
}: RecordableElProps) {
  const [hovered, setHovered] = useState(false)
  const isInspected = inspectedElId === el.shortId

  const handleClick = useCallback(
    (e: React.MouseEvent) => {
      if (!recording) return
      e.stopPropagation()
      onRecord(el)
    },
    [recording, onRecord, el],
  )

  return (
    <div
      style={{
        position: 'relative',
        cursor: recording ? 'crosshair' : 'default',
        outline: isInspected
          ? '2px solid #14b8a6'
          : recording && hovered
            ? '2px solid #3b82f6'
            : 'none',
        outlineOffset: '-1px',
        borderRadius: 4,
        boxShadow: isInspected ? '0 0 0 3px rgba(20,184,166,0.18)' : 'none',
        zIndex: isInspected ? 2 : 'auto',
        transition: 'outline 0.15s, box-shadow 0.15s',
        ...style,
      }}
      className={className}
      onMouseEnter={() => recording && setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={handleClick}
    >
      {children}
      {isInspected && (
        <div
          style={{
            position: 'absolute',
            top: -18,
            left: 0,
            background: '#0f766e',
            color: '#fff',
            fontSize: 8,
            padding: '1px 5px',
            borderRadius: 3,
            whiteSpace: 'nowrap',
            zIndex: 100,
            pointerEvents: 'none',
            fontFamily: 'monospace',
          }}
        >
          {el.shortId}
        </div>
      )}
      {recording && hovered && !isInspected && (
        <div
          style={{
            position: 'absolute',
            top: -22,
            left: 0,
            background: '#1e40af',
            color: '#fff',
            fontSize: 9,
            padding: '2px 5px',
            borderRadius: 3,
            whiteSpace: 'nowrap',
            zIndex: 100,
            pointerEvents: 'none',
            fontFamily: 'monospace',
          }}
        >
          {el.shortId}
        </div>
      )}
    </div>
  )
})

// ─── Cinépolis Home Screen ────────────────────────────────────────────────────

interface HomeScreenProps {
  recording: boolean
  onRecord: (el: AppEl) => void
  pkg: string
  onScreenChange: (screen: AppScreen) => void
  inspectedElId?: string
}

const CinepolisHomeScreen = React.memo(function CinepolisHomeScreen({
  recording,
  onRecord,
  onScreenChange,
  inspectedElId,
}: HomeScreenProps) {
  const handleRecord = useCallback(
    (el: AppEl) => {
      onRecord(el)
      if (
        el.shortId === HOME_ELS.navMisCompras.shortId ||
        el.shortId === HOME_ELS.iniciarSesion.shortId
      ) {
        setTimeout(() => onScreenChange('login'), 150)
      }
    },
    [onRecord, onScreenChange],
  )

  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        backgroundColor: '#ffffff',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        fontFamily: 'system-ui, sans-serif',
        fontSize: 13,
      }}
    >
      {/* App Header */}
      <div
        style={{
          backgroundColor: '#003087',
          padding: '10px 12px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <span
          style={{
            color: '#ffffff',
            fontStyle: 'italic',
            fontWeight: 700,
            fontSize: 16,
            letterSpacing: 0.5,
          }}
        >
          cinépolis
        </span>
        <div style={{ display: 'flex', gap: 8 }}>
          <Search size={14} color="#ffffff" />
          <div
            style={{
              width: 14,
              height: 14,
              borderRadius: '50%',
              backgroundColor: 'rgba(255,255,255,0.25)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <div
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                backgroundColor: 'rgba(255,255,255,0.5)',
              }}
            />
          </div>
        </div>
      </div>

      {/* Body */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '10px 10px 0' }}>
        <p style={{ fontSize: 10, color: '#333', marginBottom: 8, fontWeight: 500 }}>
          ¡Bienvenido! ¿Qué vamos a ver hoy?
        </p>

        {/* Search bar */}
        <RecordableEl el={HOME_ELS.buscar} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
          <div
            style={{
              backgroundColor: '#f5f5f5',
              border: '1px solid #e0e0e0',
              borderRadius: 20,
              padding: '5px 10px',
              fontSize: 10,
              color: '#888',
              marginBottom: 10,
              display: 'flex',
              alignItems: 'center',
              gap: 4,
            }}
          >
            <Search size={9} color="#888" />
            <span>Buscar película...</span>
          </div>
        </RecordableEl>

        {/* Tabs */}
        <div style={{ display: 'flex', marginBottom: 10, borderBottom: '1px solid #e0e0e0' }}>
          <RecordableEl el={HOME_ELS.tabCartelera} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
            <div
              style={{
                padding: '4px 8px',
                fontSize: 9,
                fontWeight: 600,
                color: '#003087',
                borderBottom: '2px solid #003087',
                marginBottom: -1,
              }}
            >
              En cartelera
            </div>
          </RecordableEl>
          <RecordableEl el={HOME_ELS.tabProximos} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
            <div
              style={{
                padding: '4px 8px',
                fontSize: 9,
                color: '#888',
              }}
            >
              Próximos estrenos
            </div>
          </RecordableEl>
        </div>

        {/* Movie cards */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
          <RecordableEl el={HOME_ELS.pelicula_duna} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
            <div
              style={{
                width: 64,
                height: 88,
                backgroundColor: '#8B6914',
                borderRadius: 6,
                display: 'flex',
                alignItems: 'flex-end',
                padding: 4,
                cursor: 'pointer',
              }}
            >
              <span style={{ color: '#fff', fontSize: 7, fontWeight: 600, lineHeight: 1.2 }}>
                Duna: Parte Dos
              </span>
            </div>
          </RecordableEl>
          <RecordableEl
            el={HOME_ELS.pelicula_garfield}
            recording={recording}
            onRecord={handleRecord}
            inspectedElId={inspectedElId}
          >
            <div
              style={{
                width: 64,
                height: 88,
                backgroundColor: '#d97706',
                borderRadius: 6,
                display: 'flex',
                alignItems: 'flex-end',
                padding: 4,
                cursor: 'pointer',
              }}
            >
              <span style={{ color: '#fff', fontSize: 7, fontWeight: 600, lineHeight: 1.2 }}>
                Garfield
              </span>
            </div>
          </RecordableEl>
          <div
            style={{
              width: 64,
              height: 88,
              backgroundColor: '#7c3aed',
              borderRadius: 6,
              display: 'flex',
              alignItems: 'flex-end',
              padding: 4,
            }}
          >
            <span style={{ color: '#fff', fontSize: 7, fontWeight: 600, lineHeight: 1.2 }}>
              Intensamente 2
            </span>
          </div>
        </div>
      </div>

      {/* Bottom Nav */}
      <div
        style={{
          display: 'flex',
          borderTop: '1px solid #e0e0e0',
          backgroundColor: '#ffffff',
        }}
      >
        {[
          { el: HOME_ELS.navInicio, label: 'Inicio', active: true },
          { el: HOME_ELS.navCines, label: 'Cines', active: false },
          { el: HOME_ELS.navAlimentos, label: 'Alimentos', active: false },
          { el: HOME_ELS.navMisCompras, label: 'Mis compras', active: false },
          { el: HOME_ELS.navMas, label: 'Más', active: false },
        ].map(({ el, label, active }) => (
          <RecordableEl
            key={el.shortId}
            el={el}
            recording={recording}
            onRecord={handleRecord}
            inspectedElId={inspectedElId}
            style={{ flex: 1 }}
          >
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                padding: '5px 2px',
                gap: 2,
              }}
            >
              <div
                style={{
                  width: 16,
                  height: 16,
                  borderRadius: 3,
                  backgroundColor: active ? '#003087' : '#ccc',
                }}
              />
              <span
                style={{
                  fontSize: 7,
                  color: active ? '#003087' : '#888',
                  textAlign: 'center',
                  lineHeight: 1.2,
                }}
              >
                {label}
              </span>
            </div>
          </RecordableEl>
        ))}
      </div>
    </div>
  )
})

// ─── Cinépolis Login Screen ───────────────────────────────────────────────────

interface LoginScreenProps {
  recording: boolean
  onRecord: (el: AppEl) => void
  onScreenChange: (screen: AppScreen) => void
  inspectedElId?: string
}

const CinepolisLoginScreen = React.memo(function CinepolisLoginScreen({
  recording,
  onRecord,
  onScreenChange,
  inspectedElId,
}: LoginScreenProps) {
  const handleRecord = useCallback(
    (el: AppEl) => {
      onRecord(el)
      if (el.shortId === LOGIN_ELS.entrar.shortId) {
        setTimeout(() => onScreenChange('home'), 150)
      }
    },
    [onRecord, onScreenChange],
  )

  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        backgroundColor: '#ffffff',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: 'system-ui, sans-serif',
      }}
    >
      {/* Header */}
      <div
        style={{
          backgroundColor: '#003087',
          padding: '10px 12px',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <ChevronRight
          size={12}
          color="#fff"
          style={{ transform: 'rotate(180deg)', flexShrink: 0 }}
        />
        <span style={{ color: '#fff', fontWeight: 600, fontSize: 13 }}>Iniciar Sesión</span>
      </div>

      {/* Form */}
      <div style={{ padding: '16px 14px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <p style={{ fontSize: 11, color: '#333', fontWeight: 600, margin: 0 }}>Hola de nuevo</p>
        <p style={{ fontSize: 9, color: '#888', margin: 0 }}>
          Ingresa tus credenciales para continuar
        </p>

        <RecordableEl el={LOGIN_ELS.correo} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
          <div
            style={{
              border: '1px solid #e0e0e0',
              borderRadius: 6,
              padding: '7px 10px',
              fontSize: 9,
              color: '#aaa',
            }}
          >
            Correo electrónico
          </div>
        </RecordableEl>

        <RecordableEl el={LOGIN_ELS.password} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
          <div
            style={{
              border: '1px solid #e0e0e0',
              borderRadius: 6,
              padding: '7px 10px',
              fontSize: 9,
              color: '#aaa',
              letterSpacing: 3,
            }}
          >
            Contraseña
          </div>
        </RecordableEl>

        <RecordableEl el={LOGIN_ELS.entrar} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
          <div
            style={{
              backgroundColor: '#003087',
              borderRadius: 6,
              padding: '8px 10px',
              textAlign: 'center',
              fontSize: 10,
              color: '#fff',
              fontWeight: 600,
            }}
          >
            Iniciar Sesión
          </div>
        </RecordableEl>

        <p style={{ fontSize: 8, color: '#3b82f6', textAlign: 'center', margin: 0 }}>
          ¿Olvidaste tu contraseña?
        </p>
      </div>
    </div>
  )
})

// ─── Phone Frame ──────────────────────────────────────────────────────────────

// ─── Recording Overlay ────────────────────────────────────────────────────────

interface RecordingOverlayProps {
  onInteract: (
    nx: number, ny: number,
    gesture: 'tap' | 'swipe' | 'long_press',
    nx2?: number, ny2?: number,
  ) => void
}

function RecordingOverlay({ onInteract }: RecordingOverlayProps) {
  const dragRef = useRef<{
    startNx:   number
    startNy:   number
    timer:     ReturnType<typeof setTimeout> | null
    fired:     boolean
  } | null>(null)

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        zIndex: 25,
        cursor: 'crosshair',
        userSelect: 'none',
        WebkitUserSelect: 'none',
      } as React.CSSProperties}
      onMouseDown={(e) => {
        e.preventDefault()
        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
        const startNx = Math.max(0, Math.min(1, (e.clientX - rect.left)  / rect.width))
        const startNy = Math.max(0, Math.min(1, (e.clientY - rect.top)   / rect.height))
        const timer = setTimeout(() => {
          if (dragRef.current && !dragRef.current.fired) {
            dragRef.current.fired = true
            onInteract(startNx, startNy, 'long_press')
          }
        }, 600)
        dragRef.current = { startNx, startNy, timer, fired: false }
      }}
      onMouseUp={(e) => {
        const drag = dragRef.current
        if (!drag) return
        if (drag.timer) clearTimeout(drag.timer)
        if (drag.fired) { dragRef.current = null; return }
        dragRef.current = null

        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
        const endNx = Math.max(0, Math.min(1, (e.clientX - rect.left)  / rect.width))
        const endNy = Math.max(0, Math.min(1, (e.clientY - rect.top)   / rect.height))
        const dx = endNx - drag.startNx
        const dy = endNy - drag.startNy
        const dist = Math.sqrt(dx * dx + dy * dy)

        if (dist < 0.03) {
          onInteract(drag.startNx, drag.startNy, 'tap')
        } else {
          onInteract(drag.startNx, drag.startNy, 'swipe', endNx, endNy)
        }
      }}
      onMouseLeave={() => {
        if (dragRef.current?.timer) clearTimeout(dragRef.current.timer)
        dragRef.current = null
      }}
    />
  )
}

// ─── Phone Frame ──────────────────────────────────────────────────────────────

interface PhoneFrameProps {
  recording: boolean
  screen: AppScreen
  onRecord: (el: AppEl) => void
  onScreenChange: (s: AppScreen) => void
  isLandscape?: boolean
  inspectedElId?: string
  previewUrl?: string | null
  previewState?: StreamState
  /** When set and recording with a live preview, renders an interactive overlay. */
  onScreenInteract?: (
    nx: number, ny: number,
    gesture: 'tap' | 'swipe' | 'long_press',
    nx2?: number, ny2?: number,
  ) => void
  /** Notifica cuándo llega un frame real del mirror — solo para medir latencia en el contenedor; no cambia qué se renderiza. */
  onFrameLoad?: () => void
}

// ── Design system unificado con el Dashboard (Fase de rediseño visual) ────────
// Mismo patrón exacto que IconButton en components/dashboard/DeviceMirrorPanel.tsx
// — reutilizado aquí en vez de duplicar estilos ad-hoc para cada botón de la
// toolbar del panel de dispositivo.
function ToolbarIconButton({
  icon: Icon, label, onClick, title, active = false, disabled = false,
}: {
  icon:      React.ElementType
  label?:    string
  onClick:   () => void
  title:     string
  active?:   boolean
  disabled?: boolean
}) {
  const [hover, setHover] = useState(false)
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={title}
      onMouseEnter={() => setHover(true)}
      onMouseLeave={() => setHover(false)}
      className="flex items-center justify-center gap-1.5 rounded-lg transition-colors flex-1"
      style={{
        height: 32,
        background: active ? 'rgba(99,102,241,0.18)' : hover && !disabled ? 'rgba(255,255,255,0.07)' : 'var(--btn-bg)',
        border: `1px solid ${active ? 'rgba(99,102,241,0.4)' : 'var(--btn-border)'}`,
        color: disabled ? '#334155' : active ? '#818cf8' : hover ? '#cbd5e1' : '#94a3b8',
        cursor: disabled ? 'default' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        transition: 'background 0.15s, color 0.15s, border-color 0.15s',
      }}
    >
      <Icon size={13} />
      {label && <span className="text-[10px] font-semibold">{label}</span>}
    </button>
  )
}

/** Mismo vocabulario visual que MIRROR_STATUS_CFG en DeviceMirrorPanel.tsx del Dashboard,
 * derivado de las mismas señales que PhoneFrame ya usa para sus propios estados internos
 * (previewState) — no introduce ningún estado nuevo. */
function computeDeviceStatusVisual(
  device: PhysicalDevice | null,
  state: StreamState | undefined,
): { label: string; color: string; pulse: boolean } {
  if (!device) return { label: 'Sin dispositivo', color: '#64748b', pulse: false }
  switch (state) {
    case 'connecting':          return { label: 'Conectando',           color: '#60a5fa', pulse: true }
    case 'loading':             return { label: 'Cargando',             color: '#60a5fa', pulse: true }
    case 'available':
    case 'updating':            return { label: 'Conectado',            color: '#34d399', pulse: false }
    case 'error':                return { label: 'Error',                color: '#f87171', pulse: false }
    case 'runner_offline':      return { label: 'Runner sin conexión',  color: '#f87171', pulse: false }
    case 'device_disconnected': return { label: 'Desconectado',         color: '#f87171', pulse: false }
    default:                     return { label: 'Sin conexión',         color: '#64748b', pulse: false }
  }
}

const PhoneFrame = React.memo(function PhoneFrame({
  recording,
  screen,
  onRecord,
  onScreenChange,
  isLandscape = false,
  inspectedElId,
  previewUrl,
  previewState,
  onScreenInteract,
  onFrameLoad,
}: PhoneFrameProps) {
  const PHONE_W = 340
  const SCREEN_W = 304
  const SCREEN_H = 524
  const SCALE = 1.0

  return (
    <div
      style={{
        width: PHONE_W,
        background: '#1a1a1a',
        borderRadius: 32,
        padding: '12px 15px',
        boxShadow: '0 0 0 1px rgba(255,255,255,0.08), 0 8px 40px rgba(0,0,0,0.6)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 0,
        position: 'relative',
      }}
    >
      {/* Notch */}
      <div
        style={{
          width: 70,
          height: 8,
          backgroundColor: '#000',
          borderRadius: 10,
          marginBottom: 8,
        }}
      />

      {/* Status bar */}
      <div
        style={{
          width: SCREEN_W,
          backgroundColor: '#003087',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '3px 8px',
          fontSize: 8,
          color: '#ffffff',
          borderTopLeftRadius: 4,
          borderTopRightRadius: 4,
        }}
      >
        <span style={{ fontWeight: 600 }}>12:30</span>
        <div style={{ display: 'flex', gap: 3, alignItems: 'center' }}>
          <Wifi size={7} color="#fff" />
          <div
            style={{
              width: 14,
              height: 7,
              border: '1px solid #fff',
              borderRadius: 2,
              position: 'relative',
            }}
          >
            <div
              style={{
                position: 'absolute',
                left: 1,
                top: 1,
                width: '75%',
                height: 'calc(100% - 2px)',
                backgroundColor: '#fff',
                borderRadius: 1,
              }}
            />
          </div>
        </div>
      </div>

      {/* Screen */}
      <div
        style={{
          width: SCREEN_W,
          height: SCREEN_H,
          overflow: 'hidden',
          backgroundColor: '#000',
          transform: `scale(${SCALE})`,
          transformOrigin: 'top center',
          marginBottom: -(SCREEN_H * (1 - SCALE)),
          position: 'relative',
        }}
      >
        {/* ── Live Preview layer (DeviceStreamProvider) ── */}
        {previewUrl ? (
          <>
            {/* Real device screenshot */}
            <img
              src={previewUrl}
              draggable={false}
              onLoad={onFrameLoad}
              style={{
                position: 'absolute',
                inset: 0,
                width: '100%',
                height: '100%',
                objectFit: 'cover',
                display: 'block',
              }}
              alt="Device screen"
            />
            {/* Subtle "Updating" indicator — top-right dot */}
            {previewState === 'updating' && (
              <div
                style={{
                  position: 'absolute',
                  top: 6,
                  right: 6,
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  backgroundColor: '#6366f1',
                  opacity: 0.85,
                  animation: 'pulse 1s ease-in-out infinite',
                  zIndex: 10,
                }}
              />
            )}
            {/* Interactive recording overlay — captures taps/swipes on the live mirror */}
            {recording && onScreenInteract && (
              <RecordingOverlay onInteract={onScreenInteract} />
            )}
          </>
        ) : (
          /* ── Static mockup fallback (no device / no preview) ── */
          <>
            {(previewState === 'loading' || previewState === 'connecting') && (
              /* Loading state overlay */
              <div
                style={{
                  position: 'absolute',
                  inset: 0,
                  backgroundColor: '#0d1117',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 10,
                  zIndex: 20,
                }}
              >
                <div
                  style={{
                    width: 32,
                    height: 32,
                    borderRadius: '50%',
                    border: '2px solid rgba(99,102,241,0.2)',
                    borderTopColor: '#6366f1',
                    animation: 'spin 0.8s linear infinite',
                  }}
                />
                <span style={{ color: '#475569', fontSize: 10, fontWeight: 600 }}>
                  {previewState === 'connecting' ? 'Conectando...' : 'Cargando pantalla...'}
                </span>
              </div>
            )}
            {(previewState === 'device_disconnected' || previewState === 'runner_offline') && (
              /* Error state */
              <div
                style={{
                  position: 'absolute',
                  inset: 0,
                  backgroundColor: '#0d1117',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 8,
                  zIndex: 20,
                }}
              >
                <div style={{ fontSize: 22, opacity: 0.4 }}>
                  {previewState === 'device_disconnected' ? '📵' : '⚡'}
                </div>
                <span style={{ color: '#475569', fontSize: 10, fontWeight: 600, textAlign: 'center', padding: '0 16px' }}>
                  {previewState === 'device_disconnected'
                    ? 'Dispositivo desconectado'
                    : 'Runner no disponible'}
                </span>
              </div>
            )}
            <AnimatePresence mode="wait">
              {screen === 'home' ? (
                <motion.div
                  key="home"
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: 10 }}
                  transition={{ duration: 0.15 }}
                  style={{ width: '100%', height: '100%' }}
                >
                  <CinepolisHomeScreen
                    recording={recording}
                    onRecord={onRecord}
                    pkg={ANDROID_PKG}
                    onScreenChange={onScreenChange}
                    inspectedElId={inspectedElId}
                  />
                </motion.div>
              ) : (
                <motion.div
                  key="login"
                  initial={{ opacity: 0, x: 10 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0, x: -10 }}
                  transition={{ duration: 0.15 }}
                  style={{ width: '100%', height: '100%' }}
                >
                  <CinepolisLoginScreen
                    recording={recording}
                    onRecord={onRecord}
                    onScreenChange={onScreenChange}
                    inspectedElId={inspectedElId}
                  />
                </motion.div>
              )}
            </AnimatePresence>
          </>
        )}
      </div>

      {/* Home indicator */}
      <div
        style={{
          width: 60,
          height: 4,
          backgroundColor: 'rgba(255,255,255,0.3)',
          borderRadius: 3,
          marginTop: 8,
        }}
      />
    </div>
  )
})

// ─── Manual Action Bar ────────────────────────────────────────────────────────

interface ManualActionBarProps {
  onManualAdd: (
    type: StepType,
    elementId: string,
    inputVal?: string,
    dir?: 'up' | 'down' | 'left' | 'right',
  ) => void
}

interface ManualDialog {
  type: StepType
  elementId: string
  inputVal: string
  dir: 'up' | 'down' | 'left' | 'right'
}

const ManualActionBar = React.memo(function ManualActionBar({ onManualAdd }: ManualActionBarProps) {
  const [dialog, setDialog] = useState<ManualDialog | null>(null)

  const openDialog = (type: StepType) => {
    setDialog({ type, elementId: '', inputVal: '', dir: 'down' })
  }

  const confirm = () => {
    if (!dialog) return
    if (dialog.type === 'scroll' || dialog.type === 'hide_keyboard') {
      onManualAdd(dialog.type, '', undefined, undefined)
    } else if (dialog.type === 'swipe') {
      onManualAdd(dialog.type, dialog.elementId, undefined, dialog.dir)
    } else if (dialog.type === 'input') {
      onManualAdd(dialog.type, dialog.elementId, dialog.inputVal, undefined)
    } else {
      onManualAdd(dialog.type, dialog.elementId, undefined, undefined)
    }
    setDialog(null)
  }

  const actions: Array<{ type: StepType; label: string; icon: React.ReactNode }> = [
    { type: 'tap', label: 'Tap', icon: <MousePointer2 size={11} /> },
    { type: 'input', label: 'Input', icon: <Type size={11} /> },
    { type: 'double_tap', label: 'D.Tap', icon: <Zap size={11} /> },
    { type: 'long_press', label: 'Long', icon: <Hand size={11} /> },
    { type: 'swipe', label: 'Swipe', icon: <MoveHorizontal size={11} /> },
    { type: 'scroll', label: 'Scroll', icon: <ChevronsDown size={11} /> },
    { type: 'hide_keyboard', label: 'KB', icon: <Keyboard size={11} /> },
  ]

  return (
    <>
      <div
        style={{
          display: 'flex',
          gap: 4,
          flexWrap: 'wrap',
          justifyContent: 'center',
        }}
      >
        {actions.map((a) => (
          <button
            key={a.type}
            onClick={() => {
              if (a.type === 'scroll' || a.type === 'hide_keyboard') {
                onManualAdd(a.type, '', undefined, undefined)
              } else {
                openDialog(a.type)
              }
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 3,
              padding: '4px 7px',
              backgroundColor: 'rgba(255,255,255,0.06)',
              border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 5,
              color: '#d4d4d4',
              fontSize: 10,
              cursor: 'pointer',
            }}
          >
            {a.icon}
            {a.label}
          </button>
        ))}
      </div>

      <AnimatePresence>
        {dialog && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            style={{
              position: 'fixed',
              inset: 0,
              backgroundColor: 'rgba(0,0,0,0.6)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 200,
            }}
            onClick={() => setDialog(null)}
          >
            <motion.div
              initial={{ scale: 0.92 }}
              animate={{ scale: 1 }}
              exit={{ scale: 0.92 }}
              onClick={(e) => e.stopPropagation()}
              style={{
                backgroundColor: '#1e2027',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 10,
                padding: 20,
                width: 300,
                display: 'flex',
                flexDirection: 'column',
                gap: 12,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
              >
                <span style={{ color: '#fff', fontWeight: 600, fontSize: 13 }}>
                  Acción: {stepTypeLabel(dialog.type)}
                </span>
                <button
                  onClick={() => setDialog(null)}
                  style={{
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    color: '#888',
                  }}
                >
                  <X size={14} />
                </button>
              </div>

              {dialog.type !== 'scroll' &&
                dialog.type !== 'hide_keyboard' &&
                dialog.type !== 'swipe' && (
                  <div>
                    <label
                      style={{
                        fontSize: 11,
                        color: '#888',
                        display: 'block',
                        marginBottom: 4,
                      }}
                    >
                      ID del elemento
                    </label>
                    <input
                      value={dialog.elementId}
                      onChange={(e) => setDialog({ ...dialog, elementId: e.target.value })}
                      placeholder="btn_iniciar_sesion"
                      style={{
                        width: '100%',
                        backgroundColor: '#141519',
                        border: '1px solid rgba(255,255,255,0.12)',
                        borderRadius: 6,
                        color: '#d4d4d4',
                        padding: '6px 10px',
                        fontSize: 12,
                        fontFamily: 'monospace',
                        boxSizing: 'border-box',
                      }}
                    />
                  </div>
                )}

              {dialog.type === 'swipe' && (
                <div>
                  <label
                    style={{ fontSize: 11, color: '#888', display: 'block', marginBottom: 4 }}
                  >
                    Dirección
                  </label>
                  <div style={{ display: 'flex', gap: 6 }}>
                    {(['up', 'down', 'left', 'right'] as const).map((d) => (
                      <button
                        key={d}
                        onClick={() => setDialog({ ...dialog, dir: d })}
                        style={{
                          flex: 1,
                          padding: '5px 0',
                          borderRadius: 5,
                          fontSize: 10,
                          cursor: 'pointer',
                          backgroundColor: dialog.dir === d ? '#6366f1' : 'rgba(255,255,255,0.06)',
                          border: `1px solid ${
                            dialog.dir === d ? '#818cf8' : 'rgba(255,255,255,0.1)'
                          }`,
                          color: '#fff',
                        }}
                      >
                        {d}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {dialog.type === 'input' && (
                <div>
                  <label
                    style={{ fontSize: 11, color: '#888', display: 'block', marginBottom: 4 }}
                  >
                    Valor a escribir
                  </label>
                  <input
                    value={dialog.inputVal}
                    onChange={(e) => setDialog({ ...dialog, inputVal: e.target.value })}
                    placeholder="usuario@email.com"
                    style={{
                      width: '100%',
                      backgroundColor: '#141519',
                      border: '1px solid rgba(255,255,255,0.12)',
                      borderRadius: 6,
                      color: '#d4d4d4',
                      padding: '6px 10px',
                      fontSize: 12,
                      boxSizing: 'border-box',
                    }}
                  />
                </div>
              )}

              <button
                onClick={confirm}
                style={{
                  backgroundColor: '#6366f1',
                  border: 'none',
                  borderRadius: 6,
                  color: '#fff',
                  padding: '8px 0',
                  fontSize: 12,
                  fontWeight: 600,
                  cursor: 'pointer',
                  marginTop: 4,
                }}
              >
                Agregar paso
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
})

// ─── Step Card ────────────────────────────────────────────────────────────────

const DIR_LABELS: Record<string, string> = {
  up: '↑ Arriba', down: '↓ Abajo', left: '← Izquierda', right: '→ Derecha',
}

interface StepCardProps {
  step: RecStep
  index: number
  total: number
  isSelected: boolean
  onDelete: (id: string) => void
  onDuplicate: (id: string) => void
  onMoveUp: (id: string) => void
  onMoveDown: (id: string) => void
  onEdit: (step: RecStep) => void
  onCardClick: () => void
}

// Strips platform-specific class prefixes so the display is concise.
// "android.widget.Button" → "Button",  "XCUIElementTypeTextField" → "TextField"
function classNameShort(cn: string): string {
  if (!cn) return ''
  if (cn.startsWith('android.widget.')) return cn.slice(15)
  if (cn.startsWith('android.view.'))   return cn.slice(13)
  if (cn.startsWith('XCUIElementType')) return cn.slice(15)
  return cn
}

// Returns the best locator strategy label + value for a step detail panel.
// Priority matches the Android/iOS locator hierarchy (id > accessibility > text > xpath).
function effectiveLocator(el: AppEl): { label: string; value: string } | null {
  if (el.resourceId)         return { label: 'Resource ID',      value: el.resourceId }
  if (el.accessId)           return { label: 'Accessibility ID', value: el.accessId }
  if (el.accessibilityLabel) return { label: 'Accessibility',    value: el.accessibilityLabel }
  if (el.text)               return { label: 'Text',             value: el.text }
  if (el.locatorValue) {
    const LABELS: Record<string, string> = {
      id:               'Resource ID',
      accessibility_id: 'Accessibility ID',
      xpath:            'XPath',
      predicate_string: 'Predicate',
      class_chain:      'Class Chain',
      text:             'Text',
      uiautomator:      'UIAutomator',
    }
    return {
      label: LABELS[el.locatorStrategy ?? ''] ?? (el.locatorStrategy ?? 'Locator'),
      value: el.locatorValue,
    }
  }
  return null
}

function StepCard({ step, index, total, isSelected, onDelete, onDuplicate, onMoveUp, onMoveDown, onEdit, onCardClick }: StepCardProps) {
  const [hovered, setHovered] = useState(false)
  const color = STEP_COLORS[step.type]

  const locator = step.el ? effectiveLocator(step.el) : null

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -16, height: 0, marginBottom: 0, paddingTop: 0 }}
      transition={{ duration: 0.18 }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={onCardClick}
      style={{
        borderRadius: 10,
        border: isSelected
          ? `1px solid ${color}60`
          : `1px solid ${hovered ? color + '30' : 'rgba(255,255,255,0.07)'}`,
        background: isSelected
          ? `linear-gradient(135deg, ${color}12, rgba(255,255,255,0.03))`
          : hovered
            ? `linear-gradient(135deg, ${color}07, rgba(255,255,255,0.02))`
            : 'rgba(255,255,255,0.025)',
        borderLeft: `3px solid ${color}`,
        marginBottom: 8,
        overflow: 'hidden',
        transition: 'border-color 0.15s, background 0.15s',
        cursor: 'pointer',
        boxShadow: isSelected ? `0 0 0 1px ${color}20` : 'none',
      }}
    >
      {/* ── Header row ── */}
      <div style={{ display: 'flex', alignItems: 'center', padding: '10px 12px 8px', gap: 10 }}>
        {/* Icon chip */}
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: 8,
            background: color + '18',
            border: `1px solid ${color}30`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          {getStepIcon(step.type, 14)}
        </div>

        {/* Step number + type label */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span
              style={{
                fontSize: 9,
                color: 'rgba(255,255,255,0.25)',
                background: 'rgba(255,255,255,0.06)',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 4,
                padding: '1px 5px',
                fontWeight: 600,
                flexShrink: 0,
              }}
            >
              #{step.n}
            </span>
            <span style={{ fontSize: 12, color: '#e2e8f0', fontWeight: 700, letterSpacing: 0.3 }}>
              {stepTypeLabel(step.type).toUpperCase()}
            </span>
            {step.screenName && (
              <span style={{
                fontSize: 8,
                color: '#64748b',
                background: 'rgba(99,102,241,0.08)',
                border: '1px solid rgba(99,102,241,0.18)',
                borderRadius: 3,
                padding: '1px 4px',
                fontWeight: 600,
                letterSpacing: 0.2,
                flexShrink: 0,
              }}>
                {step.screenName}
              </span>
            )}
          </div>
          {step.el && (
            <span
              style={{
                fontSize: 10,
                color: '#64748b',
                marginTop: 1,
                display: 'block',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                maxWidth: 200,
              }}
            >
              {step.el.text}
            </span>
          )}
        </div>

        {/* Timestamp */}
        <span style={{ fontSize: 10, color: '#334155', fontFamily: 'monospace', flexShrink: 0 }}>
          {step.timeStr}
        </span>

        {/* Action buttons (appear on hover) */}
        <AnimatePresence>
          {hovered && (
            <motion.div
              initial={{ opacity: 0, x: 6 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 6 }}
              transition={{ duration: 0.1 }}
              style={{ display: 'flex', gap: 3, flexShrink: 0 }}
            >
              {[
                {
                  icon: <ChevronUp size={11} />,
                  title: 'Mover arriba',
                  disabled: index === 0,
                  onClick: () => onMoveUp(step.id),
                  danger: false,
                },
                {
                  icon: <ChevronDown size={11} />,
                  title: 'Mover abajo',
                  disabled: index === total - 1,
                  onClick: () => onMoveDown(step.id),
                  danger: false,
                },
                {
                  icon: <Copy size={11} />,
                  title: 'Duplicar',
                  disabled: false,
                  onClick: () => onDuplicate(step.id),
                  danger: false,
                },
                {
                  icon: <Pencil size={11} />,
                  title: 'Editar',
                  disabled: false,
                  onClick: () => onEdit(step),
                  danger: false,
                },
                {
                  icon: <Trash2 size={11} />,
                  title: 'Eliminar',
                  disabled: false,
                  onClick: () => onDelete(step.id),
                  danger: true,
                },
              ].map((btn, i) => (
                <button
                  key={i}
                  title={btn.title}
                  disabled={btn.disabled}
                  onClick={btn.onClick}
                  style={{
                    width: 24,
                    height: 24,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: btn.danger
                      ? 'rgba(239,68,68,0.08)'
                      : 'rgba(255,255,255,0.05)',
                    border: `1px solid ${btn.danger ? 'rgba(239,68,68,0.2)' : 'rgba(255,255,255,0.1)'}`,
                    borderRadius: 5,
                    cursor: btn.disabled ? 'not-allowed' : 'pointer',
                    color: btn.disabled ? '#1e293b' : btn.danger ? '#ef4444' : '#64748b',
                    transition: 'all 0.1s',
                    opacity: btn.disabled ? 0.4 : 1,
                  }}
                >
                  {btn.icon}
                </button>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* ── Details row ── */}
      <div style={{ padding: '0 12px 10px 54px', display: 'flex', flexDirection: 'column', gap: 4 }}>
        {/* Element + locator (tap/long/double) */}
        {(step.type === 'tap' || step.type === 'double_tap' || step.type === 'long_press') && step.el && (
          <>
            {step.el.className && (
              <DetailRow label="Tipo" value={classNameShort(step.el.className)} mono truncate />
            )}
            <DetailRow label="Variable" value={elVarName(step.el)} mono color="#818cf8" />
            {locator && <DetailRow label={locator.label} value={locator.value} mono truncate />}
          </>
        )}

        {/* Input */}
        {step.type === 'input' && (
          <>
            {step.el?.className && (
              <DetailRow label="Tipo" value={classNameShort(step.el.className)} mono truncate />
            )}
            {step.el && <DetailRow label="Variable" value={elVarName(step.el)} mono color="#818cf8" />}
            {step.inputVal && (
              <DetailRow label="Valor" value={`"${step.inputVal}"`} color="#34d399" />
            )}
            {locator && <DetailRow label={locator.label} value={locator.value} mono truncate />}
          </>
        )}

        {/* Swipe */}
        {step.type === 'swipe' && (
          <DetailRow label="Dirección" value={DIR_LABELS[step.dir ?? 'right'] ?? step.dir ?? '—'} />
        )}

        {/* Scroll */}
        {step.type === 'scroll' && (
          <DetailRow label="Tipo" value="Vertical" />
        )}

        {/* Hide keyboard */}
        {step.type === 'hide_keyboard' && (
          <DetailRow label="Acción" value="Ocultar teclado del sistema" />
        )}

        {/* Assertion/Screenshot */}
        {(step.type === 'assertion' || step.type === 'screenshot') && step.el && (
          <DetailRow label="Elemento" value={step.el.shortId} mono />
        )}
      </div>
    </motion.div>
  )
}

function DetailRow({
  label,
  value,
  mono = false,
  truncate = false,
  color = '#64748b',
}: {
  label: string
  value: string
  mono?: boolean
  truncate?: boolean
  color?: string
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
      <span
        style={{
          fontSize: 9,
          color: '#334155',
          fontWeight: 600,
          textTransform: 'uppercase',
          letterSpacing: 0.4,
          minWidth: 56,
          flexShrink: 0,
        }}
      >
        {label}
      </span>
      <span
        style={{
          fontSize: 10,
          color,
          fontFamily: mono ? 'monospace' : 'inherit',
          overflow: truncate ? 'hidden' : 'visible',
          textOverflow: 'ellipsis',
          whiteSpace: truncate ? 'nowrap' : 'normal',
          maxWidth: truncate ? 280 : 'none',
        }}
      >
        {value}
      </span>
    </div>
  )
}

// ─── Edit Step Modal ───────────────────────────────────────────────────────────

interface EditStepModalProps {
  step: RecStep
  onClose: () => void
  onSave: (id: string, updates: { elementId?: string; inputVal?: string; dir?: 'up' | 'down' | 'left' | 'right' }) => void
}

function EditStepModal({ step, onClose, onSave }: EditStepModalProps) {
  const [elementId, setElementId] = useState(step.el?.shortId ?? '')
  const [inputVal, setInputVal] = useState(step.inputVal ?? '')
  const [dir, setDir] = useState<'up' | 'down' | 'left' | 'right'>(step.dir ?? 'down')

  const handleSave = () => {
    onSave(step.id, {
      elementId: elementId.trim() || undefined,
      inputVal: inputVal.trim() || undefined,
      dir,
    })
    onClose()
  }

  const color = STEP_COLORS[step.type]

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 400,
        backdropFilter: 'blur(4px)',
      }}
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.93, y: 12 }}
        animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.93, y: 12 }}
        transition={{ type: 'spring', stiffness: 300, damping: 28 }}
        onClick={e => e.stopPropagation()}
        style={{
          background: '#111827',
          border: '1px solid rgba(255,255,255,0.1)',
          borderTop: `3px solid ${color}`,
          borderRadius: 12,
          padding: 24,
          width: 400,
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div
              style={{
                width: 28,
                height: 28,
                borderRadius: 7,
                background: color + '18',
                border: `1px solid ${color}30`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {getStepIcon(step.type, 13)}
            </div>
            <div>
              <p style={{ margin: 0, color: '#e2e8f0', fontSize: 13, fontWeight: 700 }}>
                Editar paso #{step.n}
              </p>
              <p style={{ margin: 0, color: '#475569', fontSize: 10 }}>
                {stepTypeLabel(step.type)}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#475569', padding: 4 }}
          >
            <X size={15} />
          </button>
        </div>

        {/* Element ID field */}
        {step.type !== 'scroll' && step.type !== 'hide_keyboard' && step.type !== 'swipe' && (
          <div>
            <label style={{ display: 'block', fontSize: 11, color: '#475569', marginBottom: 5, fontWeight: 600 }}>
              ID DEL ELEMENTO
            </label>
            <input
              value={elementId}
              onChange={e => setElementId(e.target.value)}
              placeholder="btn_iniciar_sesion"
              style={{
                width: '100%',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 7,
                color: '#e2e8f0',
                padding: '8px 11px',
                fontSize: 12,
                fontFamily: 'monospace',
                boxSizing: 'border-box',
                outline: 'none',
              }}
            />
          </div>
        )}

        {/* Input value */}
        {step.type === 'input' && (
          <div>
            <label style={{ display: 'block', fontSize: 11, color: '#475569', marginBottom: 5, fontWeight: 600 }}>
              VALOR A ESCRIBIR
            </label>
            <input
              value={inputVal}
              onChange={e => setInputVal(e.target.value)}
              placeholder="usuario@empresa.com"
              style={{
                width: '100%',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 7,
                color: '#34d399',
                padding: '8px 11px',
                fontSize: 12,
                boxSizing: 'border-box',
                outline: 'none',
              }}
            />
          </div>
        )}

        {/* Direction (swipe) */}
        {step.type === 'swipe' && (
          <div>
            <label style={{ display: 'block', fontSize: 11, color: '#475569', marginBottom: 7, fontWeight: 600 }}>
              DIRECCIÓN
            </label>
            <div style={{ display: 'flex', gap: 6 }}>
              {(['up', 'down', 'left', 'right'] as const).map(d => (
                <button
                  key={d}
                  onClick={() => setDir(d)}
                  style={{
                    flex: 1,
                    padding: '7px 0',
                    borderRadius: 7,
                    fontSize: 11,
                    fontWeight: 600,
                    cursor: 'pointer',
                    background: dir === d ? color + '20' : 'rgba(255,255,255,0.04)',
                    border: `1px solid ${dir === d ? color + '50' : 'rgba(255,255,255,0.08)'}`,
                    color: dir === d ? color : '#475569',
                    transition: 'all 0.12s',
                  }}
                >
                  {DIR_LABELS[d]}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Save button */}
        <button
          onClick={handleSave}
          style={{
            width: '100%',
            padding: '10px 0',
            background: `linear-gradient(135deg, ${color}30, ${color}20)`,
            border: `1px solid ${color}40`,
            borderRadius: 8,
            color,
            fontSize: 12,
            fontWeight: 700,
            cursor: 'pointer',
            marginTop: 4,
            transition: 'all 0.15s',
          }}
        >
          Guardar cambios
        </button>
      </motion.div>
    </motion.div>
  )
}

// ─── Steps Panel ──────────────────────────────────────────────────────────────

interface StepsPanelProps {
  steps: RecStep[]
  recording: boolean
  isDraft: boolean
  savedSuiteName: string | null
  savedCaseName?: string | null
  hasChangesAfterSave: boolean
  selectedStepId: string | null
  onDeleteStep: (id: string) => void
  onDuplicateStep: (id: string) => void
  onMoveStep: (id: string, dir: 'up' | 'down') => void
  onEditStep: (id: string, updates: { elementId?: string; inputVal?: string; dir?: 'up' | 'down' | 'left' | 'right' }) => void
  onSelectStep: (step: RecStep) => void
  onManualAdd: (
    type: StepType,
    elementId: string,
    inputVal?: string,
    dir?: 'up' | 'down' | 'left' | 'right',
  ) => void
}

const FILTER_CHIPS: { label: string; value: StepFilter; color: string }[] = [
  { label: 'Todos', value: 'all', color: '#6366f1' },
  { label: 'Tap', value: 'tap', color: '#818cf8' },
  { label: 'Input', value: 'input', color: '#34d399' },
  { label: 'Swipe', value: 'swipe', color: '#f59e0b' },
  { label: 'Scroll', value: 'scroll', color: '#60a5fa' },
  { label: 'Assertion', value: 'assertion', color: '#14b8a6' },
  { label: 'Screenshot', value: 'screenshot', color: '#eab308' },
]

const StepsPanel = React.memo(function StepsPanel({
  steps,
  recording,
  isDraft,
  savedSuiteName,
  savedCaseName,
  hasChangesAfterSave,
  selectedStepId,
  onDeleteStep,
  onDuplicateStep,
  onMoveStep,
  onEditStep,
  onSelectStep,
  onManualAdd,
}: StepsPanelProps) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const [search, setSearch] = useState('')
  const [activeFilter, setActiveFilter] = useState<StepFilter>('all')
  const [editingStep, setEditingStep] = useState<RecStep | null>(null)

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [steps.length])

  const filteredSteps = useMemo(() => {
    let result = steps
    if (activeFilter !== 'all') {
      result = result.filter(s => s.type === activeFilter)
    }
    if (search.trim()) {
      const q = search.toLowerCase()
      result = result.filter(s =>
        stepTypeLabel(s.type).toLowerCase().includes(q) ||
        s.el?.text?.toLowerCase().includes(q) ||
        s.el?.shortId?.toLowerCase().includes(q) ||
        s.inputVal?.toLowerCase().includes(q)
      )
    }
    return result
  }, [steps, activeFilter, search])

  return (
    <>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          height: '100%',
          backgroundColor: 'rgba(255,255,255,0.03)',
          borderLeft: '1px solid rgba(255,255,255,0.06)',
          borderRight: '1px solid rgba(255,255,255,0.06)',
        }}
      >
        {/* ── Panel header ── */}
        <div
          style={{
            padding: '12px 16px 10px',
            borderBottom: '1px solid rgba(255,255,255,0.06)',
            flexShrink: 0,
          }}
        >
          {/* Title row */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 10,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div
                style={{
                  width: 26,
                  height: 26,
                  borderRadius: 7,
                  background: 'rgba(99,102,241,0.12)',
                  border: '1px solid rgba(99,102,241,0.2)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Layers3 size={13} color="#818cf8" />
              </div>
              <span style={{ color: '#e2e8f0', fontWeight: 700, fontSize: 13 }}>Pasos Grabados</span>
              {steps.length > 0 && (() => {
                if (savedSuiteName && !hasChangesAfterSave) {
                  const badgeText = savedCaseName
                    ? `${savedSuiteName.length > 12 ? savedSuiteName.slice(0, 10) + '…' : savedSuiteName} / ${savedCaseName.length > 12 ? savedCaseName.slice(0, 10) + '…' : savedCaseName}`
                    : (savedSuiteName.length > 18 ? savedSuiteName.slice(0, 16) + '…' : savedSuiteName)
                  const titleText = savedCaseName
                    ? `Suite: "${savedSuiteName}" · Caso: "${savedCaseName}"`
                    : `Guardado en "${savedSuiteName}"`
                  return (
                    <span
                      title={titleText}
                      style={{
                        fontSize: 9, fontWeight: 700,
                        color: '#34d399',
                        background: 'rgba(52,211,153,0.12)',
                        border: '1px solid rgba(52,211,153,0.3)',
                        borderRadius: 4, padding: '1px 5px', letterSpacing: 0.3,
                        display: 'flex', alignItems: 'center', gap: 3,
                      }}
                    >
                      ✓ {badgeText}
                    </span>
                  )
                }
                if (savedSuiteName && hasChangesAfterSave) {
                  return (
                    <span
                      title="Hay cambios sin guardar desde el último guardado"
                      style={{
                        fontSize: 9, fontWeight: 700,
                        color: '#f97316',
                        background: 'rgba(249,115,22,0.12)',
                        border: '1px solid rgba(249,115,22,0.3)',
                        borderRadius: 4, padding: '1px 5px', letterSpacing: 0.3,
                      }}
                    >
                      cambios sin guardar
                    </span>
                  )
                }
                if (isDraft) {
                  return (
                    <span
                      title="Pasos recuperados de sesión anterior — guarda para no perderlos"
                      style={{
                        fontSize: 9, fontWeight: 700,
                        color: '#f59e0b',
                        background: 'rgba(245,158,11,0.12)',
                        border: '1px solid rgba(245,158,11,0.3)',
                        borderRadius: 4, padding: '1px 5px', letterSpacing: 0.3,
                      }}
                    >
                      sin guardar
                    </span>
                  )
                }
                return null
              })()}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              {steps.length > 0 && (
                <span
                  style={{
                    background: 'linear-gradient(135deg, #6366f1, #818cf8)',
                    color: '#fff',
                    fontSize: 10,
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: 20,
                  }}
                >
                  {filteredSteps.length !== steps.length
                    ? `${filteredSteps.length}/${steps.length}`
                    : steps.length}
                </span>
              )}
            </div>
          </div>

          {/* Search bar */}
          <div style={{ position: 'relative', marginBottom: 8 }}>
            <Search
              size={12}
              color="#475569"
              style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}
            />
            <input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Buscar paso..."
              style={{
                width: '100%',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 7,
                padding: '6px 10px 6px 28px',
                fontSize: 11,
                color: '#94a3b8',
                outline: 'none',
                boxSizing: 'border-box',
              }}
            />
          </div>

          {/* Filter chips */}
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
            {FILTER_CHIPS.map(({ label, value, color }) => {
              const isActive = activeFilter === value
              return (
                <button
                  key={value}
                  onClick={() => setActiveFilter(value)}
                  style={{
                    padding: '3px 9px',
                    borderRadius: 20,
                    fontSize: 10,
                    fontWeight: 500,
                    cursor: 'pointer',
                    border: `1px solid ${isActive ? color : 'rgba(255,255,255,0.08)'}`,
                    background: isActive ? `${color}22` : 'rgba(255,255,255,0.03)',
                    color: isActive ? color : '#475569',
                    transition: 'all 0.15s',
                  }}
                >
                  {label}
                </button>
              )
            })}
          </div>
        </div>

        {/* ── Steps list ── */}
        <div
          ref={scrollRef}
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '10px 12px',
          }}
        >
          {steps.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                padding: '32px 24px',
                gap: 14,
                textAlign: 'center',
              }}
            >
              <div
                style={{
                  width: 56,
                  height: 56,
                  borderRadius: 16,
                  background: 'rgba(99,102,241,0.08)',
                  border: '1px solid rgba(99,102,241,0.15)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Layers3 size={24} color="rgba(99,102,241,0.4)" />
              </div>
              <div>
                <p style={{ color: '#475569', fontSize: 13, fontWeight: 600, margin: '0 0 4px' }}>
                  No existen pasos grabados
                </p>
                <p style={{ color: '#334155', fontSize: 11, margin: 0, lineHeight: 1.5 }}>
                  Inicia la grabación e interactúa con el dispositivo para capturar acciones.
                </p>
              </div>
              <div
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: '6px 12px',
                  justifyContent: 'center',
                  marginTop: 4,
                }}
              >
                {[
                  { label: 'Tap', color: '#818cf8' },
                  { label: 'Input', color: '#34d399' },
                  { label: 'Swipe', color: '#f59e0b' },
                  { label: 'Scroll', color: '#60a5fa' },
                ].map(({ label, color }) => (
                  <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                    <div style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: color }} />
                    <span style={{ fontSize: 10, color: '#334155' }}>{label}</span>
                  </div>
                ))}
              </div>
            </div>
          ) : filteredSteps.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '40px 24px',
                gap: 10,
                textAlign: 'center',
              }}
            >
              <Search size={22} color="#334155" />
              <p style={{ color: '#475569', fontSize: 12, fontWeight: 600, margin: 0 }}>
                Sin resultados
              </p>
              <p style={{ color: '#334155', fontSize: 11, margin: 0 }}>
                Prueba con otro término o cambia el filtro.
              </p>
            </div>
          ) : (
            <AnimatePresence initial={false}>
              {filteredSteps.map((step, idx) => (
                <StepCard
                  key={step.id}
                  step={step}
                  index={steps.indexOf(step)}
                  total={steps.length}
                  isSelected={selectedStepId === step.id}
                  onDelete={onDeleteStep}
                  onDuplicate={onDuplicateStep}
                  onMoveUp={id => onMoveStep(id, 'up')}
                  onMoveDown={id => onMoveStep(id, 'down')}
                  onEdit={setEditingStep}
                  onCardClick={() => onSelectStep(step)}
                />
              ))}
            </AnimatePresence>
          )}
        </div>

        {/* Bottom actions when recording */}
        {recording && (
          <div
            style={{
              padding: '8px 10px',
              borderTop: '1px solid rgba(255,255,255,0.06)',
              flexShrink: 0,
            }}
          >
            <p
              style={{
                color: '#555',
                fontSize: 9,
                fontWeight: 600,
                margin: '0 0 5px',
                letterSpacing: 0.5,
              }}
            >
              AGREGAR ACCIÓN
            </p>
            <ManualActionBar onManualAdd={onManualAdd} />
          </div>
        )}
      </div>

      {/* Edit modal — portal-like via AnimatePresence */}
      <AnimatePresence>
        {editingStep && (
          <EditStepModal
            step={editingStep}
            onClose={() => setEditingStep(null)}
            onSave={(id, updates) => {
              onEditStep(id, updates)
              setEditingStep(null)
            }}
          />
        )}
      </AnimatePresence>
    </>
  )
})

const LANG_OPTIONS: { value: Lang; label: string; color: string; ext: string }[] = [
  { value: 'java-testng', label: 'Java · TestNG', color: '#f97316', ext: 'java' },
  { value: 'java-junit',  label: 'Java · JUnit',  color: '#f59e0b', ext: 'java' },
  { value: 'python',      label: 'Python',         color: '#3b82f6', ext: 'py'   },
  { value: 'javascript',  label: 'JavaScript',     color: '#eab308', ext: 'js'   },
  { value: 'csharp',      label: 'C#',             color: '#a855f7', ext: 'cs'   },
  { value: 'kotlin',      label: 'Kotlin',         color: '#818cf8', ext: 'kt'   },
]

// ─── XML Tree View ────────────────────────────────────────────────────────────

interface XmlTreeViewProps {
  node: XmlNode
  expanded: Set<string>
  onToggle: (key: string) => void
  inspectedElId: string | null
  onInspect: (id: string) => void
  depth: number
  parentKey?: string
  nodeIndex?: number
}

function XmlTreeView({ node, expanded, onToggle, inspectedElId, onInspect, depth, parentKey = '', nodeIndex = 0 }: XmlTreeViewProps) {
  const nodeKey = `${parentKey}/${node.tag}[${nodeIndex}]`
  const hasChildren = node.children && node.children.length > 0
  const isExpanded = expanded.has(nodeKey) || depth < 2
  const isInspected = node.elId === inspectedElId
  const indent = depth * 16

  const tagColor = depth === 0 ? '#64748b' : depth === 1 ? '#818cf8' : '#93c5fd'
  const attrNameColor = '#14b8a6'
  const attrValColor = '#34d399'

  const priorityAttrs = ['resource-id', 'text', 'content-desc', 'bounds']
  const shownAttrs = Object.entries(node.attrs).filter(([k]) => priorityAttrs.includes(k) || !node.elId)

  return (
    <div
      style={{
        fontFamily: 'monospace',
        fontSize: 10,
        lineHeight: 1.7,
        userSelect: 'none',
        background: isInspected ? 'rgba(20,184,166,0.06)' : 'transparent',
        borderLeft: isInspected ? '2px solid #14b8a6' : '2px solid transparent',
        transition: 'background 0.15s',
      }}
    >
      {/* Opening tag line */}
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          paddingLeft: indent + 10,
          paddingRight: 10,
          paddingTop: 1,
          paddingBottom: 1,
          cursor: node.elId ? 'pointer' : hasChildren ? 'pointer' : 'default',
          borderRadius: 4,
        }}
        onClick={e => {
          e.stopPropagation()
          if (node.elId) { onInspect(node.elId); return }
          if (hasChildren) onToggle(nodeKey)
        }}
      >
        {hasChildren && (
          <span
            style={{ color: '#475569', marginRight: 4, fontSize: 9, lineHeight: 1.8, flexShrink: 0 }}
            onClick={e => { e.stopPropagation(); onToggle(nodeKey) }}
          >
            {isExpanded ? '▾' : '▸'}
          </span>
        )}
        {!hasChildren && <span style={{ width: 13, flexShrink: 0 }} />}
        <span style={{ flex: 1, flexWrap: 'wrap', display: 'flex', alignItems: 'baseline', gap: 2 }}>
          <span style={{ color: '#475569' }}>&lt;</span>
          <span style={{ color: tagColor, fontWeight: depth < 2 ? 600 : 400 }}>{node.tag}</span>
          {shownAttrs.map(([k, v]) => v && v !== '—' && v !== '' ? (
            <span key={k} style={{ display: 'inline-flex', gap: 1 }}>
              {' '}
              <span style={{ color: attrNameColor }}>{k}</span>
              <span style={{ color: '#475569' }}>="</span>
              <span
                style={{
                  color: attrValColor,
                  maxWidth: 160,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  display: 'inline-block',
                  verticalAlign: 'bottom',
                }}
                title={v}
              >
                {v}
              </span>
              <span style={{ color: '#475569' }}>"</span>
            </span>
          ) : null)}
          {!hasChildren && <><span style={{ color: '#475569' }}> /&gt;</span></>}
          {hasChildren && !isExpanded && (
            <span style={{ color: '#475569' }}>&gt;…&lt;/{node.tag}&gt;</span>
          )}
          {hasChildren && isExpanded && (
            <span style={{ color: '#475569' }}>&gt;</span>
          )}
        </span>
      </div>

      {/* Children */}
      {hasChildren && isExpanded && (
        <div>
          {node.children!.map((child, i) => (
            <XmlTreeView
              key={`${nodeKey}/${child.tag}[${i}]`}
              node={child}
              expanded={expanded}
              onToggle={onToggle}
              inspectedElId={inspectedElId}
              onInspect={onInspect}
              depth={depth + 1}
              parentKey={nodeKey}
              nodeIndex={i}
            />
          ))}
          {/* Closing tag */}
          <div style={{ paddingLeft: indent + 10, fontSize: 10, color: '#475569', lineHeight: 1.7 }}>
            &lt;/{node.tag}&gt;
          </div>
        </div>
      )}
    </div>
  )
}

// ─── Code Panel ───────────────────────────────────────────────────────────────

interface CodePanelProps {
  steps: RecStep[]
  lang: Lang
  viewTab: ViewTab
  opts: GenOpts
  testName: string
  className: string
  generatedCode: string
  generatedXML: string
  currentScreen: AppScreen
  inspectedElId: string | null
  onLangChange: (l: Lang) => void
  onViewTabChange: (t: ViewTab) => void
  onOptsChange: (o: GenOpts) => void
  onTestNameChange: (s: string) => void
  onClassNameChange: (s: string) => void
  onInspectEl: (shortId: string) => void
  onCopy: () => void
  onDownload: () => void
  onSaveCase: () => void
  onSaveSuite: () => void
  onExecute: () => void
  onExport: () => void
  copied: boolean
}

const CodePanel = React.memo(function CodePanel({
  steps,
  lang,
  viewTab,
  opts,
  testName,
  className,
  generatedCode,
  generatedXML,
  currentScreen,
  inspectedElId,
  onLangChange,
  onViewTabChange,
  onOptsChange,
  onTestNameChange,
  onClassNameChange,
  onInspectEl,
  onCopy,
  onDownload,
  onSaveCase,
  onSaveSuite,
  onExecute,
  onExport,
  copied,
}: CodePanelProps) {
  const [copiedLocator, setCopiedLocator] = useState<string | null>(null)
  const [xmlExpanded, setXmlExpanded] = useState<Set<string>>(new Set(['hierarchy', 'android.widget.FrameLayout']))

  const inspectedEl = inspectedElId ? getElById(inspectedElId) : null
  const xmlTree = useMemo(() => buildXmlTree(currentScreen), [currentScreen])

  const copyLocator = useCallback((value: string, key: string) => {
    navigator.clipboard.writeText(value).catch(() => {})
    setCopiedLocator(key)
    setTimeout(() => setCopiedLocator(null), 1500)
  }, [])

  const code = viewTab === 'code' ? generatedCode : generatedXML
  const lines = code.split('\n')

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: 'rgba(255,255,255,0.02)',
      }}
    >
      {/* Tabs + controls */}
      <div
        style={{
          padding: '10px 14px',
          borderBottom: '1px solid rgba(255,255,255,0.06)',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          flexShrink: 0,
        }}
      >
        {/* ── Row 1: Tabs + action buttons ── */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          {/* View tabs */}
          <div style={{ display: 'flex', gap: 1 }}>
            {([
              { id: 'code' as ViewTab, label: 'Código', icon: <Code2 size={10} /> },
              { id: 'xml' as ViewTab, label: 'XML', icon: <FileCode2 size={10} /> },
              { id: 'inspector' as ViewTab, label: 'Inspector', icon: <Eye size={10} /> },
              { id: 'locators' as ViewTab, label: 'Locators', icon: <Link2 size={10} /> },
            ]).map((tab) => (
              <button
                key={tab.id}
                onClick={() => onViewTabChange(tab.id)}
                style={{
                  padding: '5px 9px',
                  fontSize: 11,
                  fontWeight: 500,
                  cursor: 'pointer',
                  border: 'none',
                  borderBottom: viewTab === tab.id
                    ? '2px solid #6366f1'
                    : '2px solid transparent',
                  borderRadius: viewTab === tab.id ? '6px 6px 0 0' : 6,
                  background: viewTab === tab.id
                    ? 'linear-gradient(135deg, rgba(99,102,241,0.18), rgba(129,140,248,0.12))'
                    : 'transparent',
                  color: viewTab === tab.id ? '#818cf8' : '#475569',
                  transition: 'all 0.15s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </div>

          {/* Copy / Download action buttons */}
          <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
            <button
              onClick={onCopy}
              title="Copiar código"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 3,
                padding: '4px 8px',
                backgroundColor: copied ? 'rgba(52,211,153,0.15)' : 'rgba(255,255,255,0.06)',
                border: `1px solid ${copied ? '#34d399' : 'rgba(255,255,255,0.1)'}`,
                borderRadius: 5,
                color: copied ? '#34d399' : '#d4d4d4',
                fontSize: 10,
                cursor: 'pointer',
                transition: 'all 0.15s',
              }}
            >
              {copied ? <Check size={10} /> : <Copy size={10} />}
              {copied ? 'Copiado' : 'Copiar'}
            </button>

            <button
              onClick={onDownload}
              title="Descargar archivo"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 3,
                padding: '4px 8px',
                backgroundColor: 'rgba(255,255,255,0.06)',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 5,
                color: '#d4d4d4',
                fontSize: 10,
                cursor: 'pointer',
              }}
            >
              <Download size={10} />
              Descargar
            </button>
          </div>
        </div>

        {/* ── Row 2: Language chips (only on Código tab) ── */}
        {viewTab === 'code' && (
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
            {LANG_OPTIONS.map(({ value, label, color }) => {
              const isActive = lang === value
              return (
                <button
                  key={value}
                  onClick={() => onLangChange(value)}
                  style={{
                    padding: '3px 10px',
                    borderRadius: 20,
                    fontSize: 10,
                    fontWeight: isActive ? 700 : 500,
                    cursor: 'pointer',
                    border: `1px solid ${isActive ? color : 'rgba(255,255,255,0.08)'}`,
                    background: isActive ? `${color}22` : 'rgba(255,255,255,0.03)',
                    color: isActive ? color : '#475569',
                    transition: 'all 0.15s',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {label}
                </button>
              )
            })}
          </div>
        )}
      </div>

      {/* ── Content area ── */}
      <div style={{ flex: 1, overflowY: 'auto', position: 'relative', minHeight: 0 }}>

        {/* ── Inspector tab ── */}
        {viewTab === 'inspector' && (
          <div style={{ padding: '14px 14px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            {!inspectedEl ? (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '40px 24px',
                  gap: 12,
                  textAlign: 'center',
                }}
              >
                <div
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: 14,
                    background: 'rgba(20,184,166,0.08)',
                    border: '1px solid rgba(20,184,166,0.18)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Eye size={22} color="rgba(20,184,166,0.5)" />
                </div>
                <p style={{ color: '#475569', fontSize: 12, fontWeight: 600, margin: 0 }}>
                  Inspector de Elementos
                </p>
                <p style={{ color: '#334155', fontSize: 11, margin: 0, lineHeight: 1.5 }}>
                  Haz clic en un paso grabado para inspeccionar el elemento.
                </p>
              </div>
            ) : (
              <>
                {/* Element header */}
                <div
                  style={{
                    background: 'rgba(20,184,166,0.07)',
                    border: '1px solid rgba(20,184,166,0.18)',
                    borderRadius: 10,
                    padding: '10px 12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                  }}
                >
                  <div
                    style={{
                      width: 32,
                      height: 32,
                      borderRadius: 8,
                      background: 'rgba(20,184,166,0.12)',
                      border: '1px solid rgba(20,184,166,0.25)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <Eye size={14} color="#14b8a6" />
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <p style={{ margin: 0, color: '#e2e8f0', fontSize: 12, fontWeight: 700 }}>
                      {inspectedEl.shortId}
                    </p>
                    <p style={{ margin: 0, color: '#14b8a6', fontSize: 10, fontFamily: 'monospace' }}>
                      {inspectedEl.className ?? 'android.view.View'}
                    </p>
                  </div>
                </div>

                {/* Properties table */}
                <div
                  style={{
                    borderRadius: 9,
                    border: '1px solid rgba(255,255,255,0.06)',
                    overflow: 'hidden',
                  }}
                >
                  {[
                    { key: 'resource-id', value: inspectedEl.resourceId, mono: true },
                    { key: 'content-desc', value: inspectedEl.accessId, mono: false },
                    { key: 'text', value: inspectedEl.text, mono: false },
                    { key: 'bounds', value: inspectedEl.bounds ?? '—', mono: true },
                    { key: 'enabled', value: 'true', mono: false, bool: true, positive: true },
                    { key: 'clickable', value: (inspectedEl.elType === 'btn' || inspectedEl.elType === 'input') ? 'true' : 'false', mono: false, bool: true, positive: inspectedEl.elType === 'btn' || inspectedEl.elType === 'input' },
                    { key: 'displayed', value: 'true', mono: false, bool: true, positive: true },
                    { key: 'XPath', value: deriveXPath(inspectedEl), mono: true },
                  ].map(({ key, value, mono, bool, positive }, i, arr) => (
                    <div
                      key={key}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '7px 12px',
                        borderBottom: i < arr.length - 1 ? '1px solid rgba(255,255,255,0.04)' : 'none',
                        background: i % 2 === 0 ? 'rgba(255,255,255,0.01)' : 'transparent',
                        gap: 8,
                      }}
                    >
                      <span
                        style={{
                          fontSize: 10,
                          color: '#475569',
                          fontFamily: 'monospace',
                          flexShrink: 0,
                          minWidth: 72,
                        }}
                      >
                        {key}
                      </span>
                      <span
                        style={{
                          fontSize: 10,
                          color: bool
                            ? (positive ? '#34d399' : '#f43f5e')
                            : mono ? '#93c5fd' : '#e2e8f0',
                          fontFamily: mono ? 'monospace' : 'inherit',
                          textAlign: 'right',
                          wordBreak: 'break-all',
                          flex: 1,
                        }}
                      >
                        {value}
                      </span>
                    </div>
                  ))}
                </div>

                {/* Copy Locator button */}
                <button
                  onClick={() => copyLocator(inspectedEl.resourceId, 'inspector-main')}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 6,
                    width: '100%',
                    padding: '9px 0',
                    background: copiedLocator === 'inspector-main'
                      ? 'rgba(52,211,153,0.12)'
                      : 'rgba(20,184,166,0.08)',
                    border: `1px solid ${copiedLocator === 'inspector-main' ? '#34d399' : 'rgba(20,184,166,0.25)'}`,
                    borderRadius: 8,
                    color: copiedLocator === 'inspector-main' ? '#34d399' : '#14b8a6',
                    fontSize: 11,
                    fontWeight: 600,
                    cursor: 'pointer',
                    transition: 'all 0.15s',
                  }}
                >
                  {copiedLocator === 'inspector-main' ? <Check size={12} /> : <Copy size={12} />}
                  {copiedLocator === 'inspector-main' ? 'Copiado' : 'Copiar Locator'}
                </button>
              </>
            )}
          </div>
        )}

        {/* ── Locators tab ── */}
        {viewTab === 'locators' && (
          <div style={{ padding: '14px 14px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            {!inspectedEl ? (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '40px 24px',
                  gap: 12,
                  textAlign: 'center',
                }}
              >
                <div
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: 14,
                    background: 'rgba(99,102,241,0.08)',
                    border: '1px solid rgba(99,102,241,0.18)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Link2 size={22} color="rgba(99,102,241,0.5)" />
                </div>
                <p style={{ color: '#475569', fontSize: 12, fontWeight: 600, margin: 0 }}>
                  Estrategia de Locators
                </p>
                <p style={{ color: '#334155', fontSize: 11, margin: 0, lineHeight: 1.5 }}>
                  Haz clic en un paso para ver los locators disponibles.
                </p>
              </div>
            ) : (() => {
              const locatorRows = [
                {
                  strategy: 'accessibilityId',
                  value: inspectedEl.accessId,
                  recommended: true,
                  warn: false,
                  appium: `MobileBy.accessibilityId("${inspectedEl.accessId}")`,
                },
                {
                  strategy: 'resource-id',
                  value: inspectedEl.resourceId,
                  recommended: true,
                  warn: false,
                  appium: `By.id("${inspectedEl.resourceId}")`,
                },
                {
                  strategy: 'content-desc',
                  value: inspectedEl.accessId,
                  recommended: false,
                  warn: false,
                  appium: `By.description("${inspectedEl.accessId}")`,
                },
                {
                  strategy: 'text',
                  value: inspectedEl.text,
                  recommended: false,
                  warn: false,
                  appium: `By.text("${inspectedEl.text}")`,
                },
                {
                  strategy: 'xpath',
                  value: deriveXPath(inspectedEl),
                  recommended: false,
                  warn: true,
                  appium: `By.xpath("${deriveXPath(inspectedEl)}")`,
                },
              ]
              return (
                <>
                  <p style={{ margin: 0, color: '#475569', fontSize: 10, fontWeight: 600, letterSpacing: 0.5 }}>
                    LOCATORS — {inspectedEl.shortId}
                  </p>
                  {locatorRows.map(({ strategy, value, recommended, warn, appium }, i) => (
                    <div
                      key={strategy}
                      style={{
                        borderRadius: 9,
                        border: `1px solid ${recommended ? 'rgba(52,211,153,0.15)' : warn ? 'rgba(245,158,11,0.15)' : 'rgba(255,255,255,0.06)'}`,
                        background: recommended ? 'rgba(52,211,153,0.04)' : 'rgba(255,255,255,0.02)',
                        padding: '10px 12px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 5,
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{ fontSize: 10, color: warn ? '#f59e0b' : '#64748b', fontFamily: 'monospace', fontWeight: 600 }}>
                          {strategy}
                        </span>
                        <div style={{ display: 'flex', gap: 5, alignItems: 'center' }}>
                          {recommended && (
                            <span style={{ fontSize: 9, color: '#34d399', background: 'rgba(52,211,153,0.1)', border: '1px solid rgba(52,211,153,0.2)', padding: '1px 6px', borderRadius: 20 }}>
                              recomendado
                            </span>
                          )}
                          {warn && (
                            <span style={{ fontSize: 9, color: '#f59e0b', background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.2)', padding: '1px 6px', borderRadius: 20 }}>
                              evitar
                            </span>
                          )}
                          <button
                            onClick={() => copyLocator(appium, `loc-${strategy}`)}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 3,
                              padding: '3px 7px',
                              background: copiedLocator === `loc-${strategy}` ? 'rgba(52,211,153,0.12)' : 'rgba(255,255,255,0.06)',
                              border: `1px solid ${copiedLocator === `loc-${strategy}` ? '#34d399' : 'rgba(255,255,255,0.1)'}`,
                              borderRadius: 5,
                              color: copiedLocator === `loc-${strategy}` ? '#34d399' : '#64748b',
                              fontSize: 9,
                              cursor: 'pointer',
                              transition: 'all 0.12s',
                            }}
                          >
                            {copiedLocator === `loc-${strategy}` ? <Check size={9} /> : <Copy size={9} />}
                            Copiar
                          </button>
                        </div>
                      </div>
                      <span style={{ fontSize: 9, color: '#334155', fontFamily: 'monospace', wordBreak: 'break-all' }}>
                        {value}
                      </span>
                      <span style={{ fontSize: 9, color: '#1e3a5f', fontFamily: 'monospace', wordBreak: 'break-all', borderTop: '1px solid rgba(255,255,255,0.04)', paddingTop: 4, marginTop: 1 }}>
                        {appium}
                      </span>
                    </div>
                  ))}
                </>
              )
            })()}
          </div>
        )}

        {/* ── XML tab — page source tree ── */}
        {viewTab === 'xml' && (
          <XmlTreeView
            node={xmlTree}
            expanded={xmlExpanded}
            onToggle={key => setXmlExpanded(prev => {
              const next = new Set(prev)
              if (next.has(key)) next.delete(key); else next.add(key)
              return next
            })}
            inspectedElId={inspectedElId}
            onInspect={id => { onInspectEl(id); onViewTabChange('inspector') }}
            depth={0}
          />
        )}

        {/* ── Código tab ── */}
        {viewTab === 'code' && (
          steps.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                gap: 14,
                padding: '32px 24px',
                textAlign: 'center',
              }}
            >
              <div
                style={{
                  width: 56,
                  height: 56,
                  borderRadius: 16,
                  background: 'rgba(99,102,241,0.08)',
                  border: '1px solid rgba(99,102,241,0.15)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <FileCode2 size={24} color="rgba(99,102,241,0.4)" />
              </div>
              <div>
                <p style={{ color: '#475569', fontSize: 13, fontWeight: 600, margin: '0 0 4px' }}>
                  Sin código generado
                </p>
                <p style={{ color: '#334155', fontSize: 11, margin: 0, lineHeight: 1.5 }}>
                  Graba pasos para generar código automáticamente.
                </p>
              </div>
            </div>
          ) : (
            <pre
              style={{
                margin: 0,
                padding: '12px 0',
                fontSize: 11,
                lineHeight: 1.6,
                fontFamily: '"Fira Code", "Consolas", monospace',
                overflowX: 'auto',
              }}
            >
              {lines.map((line, i) => (
                <div key={i} style={{ display: 'flex', padding: '0 8px' }}>
                  <span
                    style={{
                      color: 'rgba(255,255,255,0.2)',
                      userSelect: 'none',
                      width: 28,
                      textAlign: 'right',
                      marginRight: 12,
                      flexShrink: 0,
                      fontSize: 10,
                    }}
                  >
                    {i + 1}
                  </span>
                  <span style={{ flex: 1 }}>
                    <SyntaxLine line={line} lang={lang} />
                  </span>
                </div>
              ))}
            </pre>
          )
        )}
      </div>

      {/* Divider */}
      <div
        style={{ height: 1, backgroundColor: 'rgba(255,255,255,0.06)', flexShrink: 0 }}
      />

      {/* Options section */}
      <div
        style={{
          padding: '12px 14px',
          display: 'flex',
          flexDirection: 'column',
          gap: 10,
          flexShrink: 0,
          overflowY: 'auto',
          maxHeight: 220,
        }}
      >
        <div style={{ display: 'flex', gap: 12 }}>
          {/* Gen options */}
          <div style={{ flex: 1 }}>
            <p
              style={{
                color: '#888',
                fontSize: 10,
                margin: '0 0 6px',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              <Settings2 size={10} />
              Opciones de Generación
            </p>
            {(
              [
                { key: 'pageObjects',     label: 'Usar Page Objects' },
                { key: 'assertions',      label: 'Generar Assertions' },
                { key: 'smartWaits',      label: 'Agregar Esperas Inteligentes' },
                { key: 'screenshots',     label: 'Incluir Toma de Screenshots' },
                { key: 'allureLogs',      label: 'Generar Logs Allure' },
                { key: 'reusableMethods', label: 'Métodos Reutilizables' },
              ] as Array<{ key: keyof GenOpts; label: string }>
            ).map(({ key, label }) => (
              <label
                key={key}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  color: '#c0c0c0',
                  fontSize: 10,
                  cursor: 'pointer',
                  marginBottom: 4,
                }}
              >
                <input
                  type="checkbox"
                  checked={opts[key]}
                  onChange={(e) => onOptsChange({ ...opts, [key]: e.target.checked })}
                  style={{ accentColor: '#6366f1', cursor: 'pointer' }}
                />
                {label}
              </label>
            ))}
          </div>

          {/* Names */}
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6 }}>
            <p
              style={{
                color: '#888',
                fontSize: 10,
                margin: '0 0 2px',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              <Type size={10} />
              Nombre del Test
            </p>
            <input
              value={testName}
              onChange={(e) => onTestNameChange(e.target.value)}
              placeholder="myTest"
              style={{
                backgroundColor: '#141519',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 5,
                color: '#d4d4d4',
                padding: '5px 8px',
                fontSize: 11,
                fontFamily: 'monospace',
              }}
            />
            <input
              value={className}
              onChange={(e) => onClassNameChange(e.target.value)}
              placeholder="GeneratedTest"
              style={{
                backgroundColor: '#141519',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 5,
                color: '#d4d4d4',
                padding: '5px 8px',
                fontSize: 11,
                fontFamily: 'monospace',
              }}
            />
          </div>
        </div>

        {/* Action buttons */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
          <button
            onClick={onSaveCase}
            title="Guardar como caso de prueba"
            style={{
              padding: '7px 0',
              background: 'rgba(99,102,241,0.12)',
              border: '1px solid rgba(99,102,241,0.3)',
              borderRadius: 6,
              color: '#818cf8',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 5,
              transition: 'all 0.15s',
            }}
          >
            <CheckCircle size={11} />
            Guardar Caso
          </button>
          <button
            onClick={onSaveSuite}
            title="Guardar como suite"
            style={{
              padding: '7px 0',
              background: 'linear-gradient(90deg, rgba(99,102,241,0.2), rgba(129,140,248,0.15))',
              border: '1px solid rgba(99,102,241,0.4)',
              borderRadius: 6,
              color: '#a5b4fc',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 5,
              transition: 'all 0.15s',
            }}
          >
            <Layers3 size={11} />
            Guardar Suite
          </button>
          <button
            onClick={onExecute}
            title="Navegar a Ejecutar Pruebas"
            style={{
              padding: '7px 0',
              background: 'rgba(52,211,153,0.1)',
              border: '1px solid rgba(52,211,153,0.3)',
              borderRadius: 6,
              color: '#34d399',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 5,
              transition: 'all 0.15s',
            }}
          >
            <Play size={11} />
            Ejecutar
          </button>
          <button
            onClick={onExport}
            title="Exportar archivos de prueba"
            style={{
              padding: '7px 0',
              background: 'rgba(234,179,8,0.1)',
              border: '1px solid rgba(234,179,8,0.3)',
              borderRadius: 6,
              color: '#eab308',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 5,
              transition: 'all 0.15s',
            }}
          >
            <Download size={11} />
            Exportar
          </button>
        </div>
      </div>
    </div>
  )
})

// ─── Session Info Bar ─────────────────────────────────────────────────────────

interface SessionInfoBarProps {
  sessionStart: Date | null
  device: PhysicalDevice | null
  appConfig: DeviceAppConfig | null
  appMode: string
  elapsed: number
  stepCount: number
  expanded: boolean
  onToggle: () => void
}

function formatElapsed(secs: number): string {
  const h = Math.floor(secs / 3600)
  const m = Math.floor((secs % 3600) / 60)
  const s = secs % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

const SessionInfoBar = React.memo(function SessionInfoBar({
  sessionStart,
  device,
  appConfig,
  appMode,
  elapsed,
  stepCount,
  expanded,
  onToggle,
}: SessionInfoBarProps) {
  const items = [
    {
      label: 'Inicio',
      value: sessionStart
        ? sessionStart.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
        : '—',
    },
    {
      label: 'Dispositivo',
      value: device ? `${resolveDeviceDisplayName(device).title} (${device.udid.slice(0, 8)}...)` : '—',
    },
    {
      label: 'Aplicación',
      value: appConfig
        ? `${appConfig.appName} (${appConfig.appPackage || appConfig.bundleId})`
        : '—',
    },
    { label: 'Modo', value: appMode || '—' },
    { label: 'Duración', value: formatElapsed(elapsed) },
    { label: 'Pasos', value: String(stepCount) },
  ]

  return (
    <div
      style={{
        borderTop: '1px solid rgba(255,255,255,0.06)',
        backgroundColor: '#0d1117',
        flexShrink: 0,
      }}
    >
      <button
        onClick={onToggle}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '5px 16px',
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          color: '#666',
        }}
      >
        <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: 0.5 }}>SESIÓN INFO</span>
        {expanded ? <ChevronDown size={12} /> : <ChevronUp size={12} />}
      </button>

      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            style={{ overflow: 'hidden' }}
          >
            <div
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: '4px 24px',
                padding: '6px 16px 10px',
              }}
            >
              {items.map(({ label, value }) => (
                <span key={label} style={{ fontSize: 10, color: '#888' }}>
                  <span style={{ color: '#555', marginRight: 3 }}>{label}:</span>
                  <span style={{ color: '#aaa' }}>{value}</span>
                </span>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
})

// ─── Save Modal ───────────────────────────────────────────────────────────────

interface SaveSuiteModalProps {
  mode: 'caso' | 'suite'
  onClose: () => void
  onConfirm: (data: {
    name: string
    description: string
    country: string
    mode: 'caso' | 'suite'
    targetSuiteId?: string
    newSuiteName?: string
  }) => void
}

const SAVE_COUNTRIES = [
  { id: 'mexico',    label: 'México',    flag: '🇲🇽' },
  { id: 'argentina', label: 'Argentina', flag: '🇦🇷' },
  { id: 'chile',     label: 'Chile',     flag: '🇨🇱' },
]

function SaveSuiteModal({ mode, onClose, onConfirm }: SaveSuiteModalProps) {
  const [name,          setName]          = useState('')
  const [description,   setDescription]   = useState('')
  const [country,       setCountry]       = useState('mexico')
  const [targetSuiteId, setTargetSuiteId] = useState('')
  const [newSuiteName,  setNewSuiteName]  = useState('')
  const [saved,         setSaved]         = useState(false)

  const isSuite = mode === 'suite'
  const title   = isSuite ? 'Guardar como Suite' : 'Guardar como Caso de Prueba'
  const accent  = isSuite ? '#818cf8' : '#6366f1'

  // Load existing TestSuites to offer as targets (only relevant for 'caso' mode)
  const existingSuites = useMemo(
    () => suiteService.getSuites(),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  )

  const selectedSuite = existingSuites.find(s => s.id === targetSuiteId)

  const isCreatingNew = targetSuiteId === '__new__'
  const canSave = name.trim() && (!isCreatingNew || newSuiteName.trim())

  const handleSave = () => {
    if (!canSave) return
    onConfirm({
      name:         name.trim(),
      description:  description.trim(),
      country,
      mode,
      targetSuiteId: (!isCreatingNew && targetSuiteId) ? targetSuiteId : undefined,
      newSuiteName:  isCreatingNew ? newSuiteName.trim() : undefined,
    })
    setSaved(true)
    setTimeout(onClose, 1400)
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0,0,0,0.7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 300,
      }}
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.92, y: 12 }}
        animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.92, y: 12 }}
        onClick={(e) => e.stopPropagation()}
        style={{
          backgroundColor: '#111827',
          border: `1px solid ${accent}44`,
          borderRadius: 14,
          padding: 28,
          width: 420,
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          boxShadow: `0 20px 60px rgba(0,0,0,0.6), 0 0 0 1px ${accent}22`,
        }}
      >
        {saved ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, padding: '12px 0' }}>
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 320 }}
            >
              <Check size={40} color="#34d399" />
            </motion.div>
            <p style={{ color: '#34d399', fontWeight: 700, fontSize: 15, margin: 0 }}>
              ¡{isSuite ? 'Suite guardada' : 'Caso guardado'} exitosamente!
            </p>
            {isSuite && (
              <p style={{ color: '#64748b', fontSize: 11, margin: 0, textAlign: 'center' }}>
                Aparecerá automáticamente en Suites, Dashboard y Ejecutar Pruebas.
              </p>
            )}
          </div>
        ) : (
          <>
            {/* Header */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{
                  width: 30, height: 30, borderRadius: 8,
                  background: `${accent}22`, border: `1px solid ${accent}44`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  {isSuite ? <Layers3 size={14} color={accent} /> : <CheckCircle size={14} color={accent} />}
                </div>
                <span style={{ color: '#f1f5f9', fontWeight: 700, fontSize: 14 }}>{title}</span>
              </div>
              <button onClick={onClose} style={{ color: '#475569', background: 'none', border: 'none', cursor: 'pointer', padding: 4 }}>
                <X size={16} />
              </button>
            </div>

            {/* Name */}
            <div>
              <label style={{ display: 'block', color: '#94a3b8', fontSize: 11, marginBottom: 5, fontWeight: 600 }}>
                Nombre *
              </label>
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder={isSuite ? 'Flujo de Compra Cinépolis' : 'test_login_exitoso'}
                autoFocus
                style={{
                  width: '100%',
                  backgroundColor: '#0d1117',
                  border: `1px solid ${name.trim() ? accent + '55' : 'rgba(255,255,255,0.1)'}`,
                  borderRadius: 7,
                  color: '#e2e8f0',
                  padding: '8px 11px',
                  fontSize: 12,
                  boxSizing: 'border-box',
                  outline: 'none',
                  transition: 'border-color 0.15s',
                }}
              />
            </div>

            {/* Description */}
            <div>
              <label style={{ display: 'block', color: '#94a3b8', fontSize: 11, marginBottom: 5, fontWeight: 600 }}>
                Descripción
              </label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Descripción breve del flujo grabado..."
                rows={2}
                style={{
                  width: '100%',
                  backgroundColor: '#0d1117',
                  border: '1px solid rgba(255,255,255,0.1)',
                  borderRadius: 7,
                  color: '#e2e8f0',
                  padding: '8px 11px',
                  fontSize: 12,
                  boxSizing: 'border-box',
                  resize: 'none',
                  fontFamily: 'inherit',
                  outline: 'none',
                }}
              />
            </div>

            {/* Suite selector — visible in 'caso' mode always */}
            {!isSuite && (
              <div>
                <label style={{ display: 'block', color: '#94a3b8', fontSize: 11, marginBottom: 5, fontWeight: 600 }}>
                  Agregar a Suite <span style={{ color: '#475569', fontWeight: 400 }}>(opcional)</span>
                </label>
                <select
                  value={targetSuiteId}
                  onChange={(e) => { setTargetSuiteId(e.target.value); setNewSuiteName('') }}
                  style={{
                    width: '100%',
                    backgroundColor: '#0d1117',
                    border: `1px solid ${targetSuiteId ? accent : 'rgba(255,255,255,0.1)'}`,
                    borderRadius: 7,
                    color: targetSuiteId ? '#e2e8f0' : '#64748b',
                    padding: '8px 11px',
                    fontSize: 12,
                    boxSizing: 'border-box',
                    outline: 'none',
                    cursor: 'pointer',
                    fontFamily: 'inherit',
                  }}
                >
                  <option value="">— Sin suite —</option>
                  {existingSuites.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.icon} {s.name}
                    </option>
                  ))}
                  <option value="__new__">+ Crear nueva Suite…</option>
                </select>

                {/* Inline new-suite name input */}
                {isCreatingNew && (
                  <div style={{ marginTop: 8 }}>
                    <input
                      autoFocus
                      value={newSuiteName}
                      onChange={(e) => setNewSuiteName(e.target.value)}
                      placeholder="Nombre de la nueva Suite…"
                      style={{
                        width: '100%',
                        backgroundColor: '#0d1117',
                        border: `1px solid ${newSuiteName.trim() ? accent + '66' : 'rgba(255,255,255,0.15)'}`,
                        borderRadius: 7,
                        color: '#e2e8f0',
                        padding: '7px 11px',
                        fontSize: 12,
                        boxSizing: 'border-box',
                        outline: 'none',
                        fontFamily: 'inherit',
                      }}
                    />
                    <p style={{ margin: '4px 0 0', fontSize: 10, color: '#64748b' }}>
                      Se creará la suite y el caso quedará dentro de ella.
                    </p>
                  </div>
                )}

                {selectedSuite && !isCreatingNew && (
                  <p style={{ margin: '5px 0 0', fontSize: 11, color: accent }}>
                    Se agregará a: <strong>{selectedSuite.name}</strong>
                  </p>
                )}
              </div>
            )}

            {/* Country (only for suites — to know which Execute tab it appears under) */}
            {isSuite && (
              <div>
                <label style={{ display: 'block', color: '#94a3b8', fontSize: 11, marginBottom: 7, fontWeight: 600 }}>
                  País / Región
                </label>
                <div style={{ display: 'flex', gap: 8 }}>
                  {SAVE_COUNTRIES.map((c) => (
                    <button
                      key={c.id}
                      onClick={() => setCountry(c.id)}
                      style={{
                        flex: 1,
                        padding: '7px 0',
                        borderRadius: 7,
                        fontSize: 11,
                        fontWeight: country === c.id ? 700 : 500,
                        cursor: 'pointer',
                        border: `1px solid ${country === c.id ? accent : 'rgba(255,255,255,0.1)'}`,
                        background: country === c.id ? `${accent}18` : 'rgba(255,255,255,0.03)',
                        color: country === c.id ? accent : '#64748b',
                        transition: 'all 0.15s',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: 4,
                      }}
                    >
                      <span>{c.flag}</span>
                      <span>{c.label}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Save button */}
            <button
              onClick={handleSave}
              disabled={!canSave}
              style={{
                width: '100%',
                padding: '10px 0',
                background: canSave
                  ? `linear-gradient(90deg, ${accent}, #a5b4fc)`
                  : 'rgba(255,255,255,0.05)',
                border: 'none',
                borderRadius: 8,
                color: canSave ? '#fff' : '#475569',
                fontSize: 13,
                fontWeight: 700,
                cursor: canSave ? 'pointer' : 'not-allowed',
                marginTop: 2,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 7,
                transition: 'all 0.15s',
              }}
            >
              {isSuite ? <Layers3 size={13} /> : <CheckCircle size={13} />}
              {isSuite
                ? 'Guardar Suite'
                : isCreatingNew
                  ? `Guardar y crear Suite "${newSuiteName.trim() || '…'}"`
                  : selectedSuite
                    ? `Guardar y agregar a ${selectedSuite.name}`
                    : 'Guardar Caso de Prueba'
              }
            </button>
          </>
        )}
      </motion.div>
    </motion.div>
  )
}

// ─── Header Step Pill ─────────────────────────────────────────────────────────

interface HeaderStepProps {
  n: number
  label: string
  value: string | null
  sub?: string | null
  statusBadge?: 'available' | 'busy' | 'offline' | null
  active: boolean
  options: string[]
  onSelect: (val: string) => void
  placeholder?: string
  icon?: React.ReactNode
}

const STATUS_BADGE: Record<string, { label: string; color: string; bg: string }> = {
  available: { label: 'Disponible', color: '#4ade80', bg: 'rgba(74,222,128,0.12)' },
  busy:      { label: 'Ocupado',    color: '#f59e0b', bg: 'rgba(245,158,11,0.12)' },
  offline:   { label: 'Offline',   color: '#64748b', bg: 'rgba(100,116,139,0.12)' },
}

function HeaderStepPill({
  n, label, value, sub, statusBadge, active, options, onSelect, icon,
}: HeaderStepProps) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const badge = statusBadge ? STATUS_BADGE[statusBadge] : null

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        onClick={() => options.length > 0 && setOpen((p) => !p)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '8px 12px',
          borderRadius: 8,
          backgroundColor: active ? 'rgba(99,102,241,0.1)' : 'rgba(255,255,255,0.04)',
          border: `1px solid ${active ? 'rgba(99,102,241,0.35)' : 'rgba(255,255,255,0.08)'}`,
          cursor: options.length > 0 ? 'pointer' : 'default',
          color: '#d4d4d4',
          transition: 'all 0.15s',
          minWidth: 170,
          textAlign: 'left',
        }}
      >
        {/* Step number badge */}
        <div
          style={{
            width: 22,
            height: 22,
            borderRadius: '50%',
            backgroundColor: active ? '#6366f1' : 'rgba(255,255,255,0.08)',
            border: active ? 'none' : '1px solid rgba(255,255,255,0.12)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: active ? '#fff' : '#555',
            fontSize: 11,
            fontWeight: 700,
            flexShrink: 0,
          }}
        >
          {n}
        </div>

        {/* Optional icon */}
        {icon && (
          <div style={{ flexShrink: 0, opacity: active ? 1 : 0.4 }}>{icon}</div>
        )}

        {/* Text block */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 9, color: '#5c6370', lineHeight: 1, marginBottom: 3 }}>
            {label}
          </div>
          <div
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: value ? '#e2e8f0' : '#475569',
              lineHeight: 1,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: 140,
            }}
          >
            {value ?? 'Seleccionar'}
          </div>
          {sub && (
            <div
              style={{
                fontSize: 9,
                color: '#475569',
                lineHeight: 1,
                marginTop: 3,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                maxWidth: 140,
              }}
            >
              {sub}
            </div>
          )}
        </div>

        {/* Status badge */}
        {badge && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              padding: '2px 7px',
              borderRadius: 10,
              backgroundColor: badge.bg,
              flexShrink: 0,
            }}
          >
            <div
              style={{
                width: 5,
                height: 5,
                borderRadius: '50%',
                backgroundColor: badge.color,
              }}
            />
            <span style={{ fontSize: 9, color: badge.color, fontWeight: 600 }}>
              {badge.label}
            </span>
          </div>
        )}

        {options.length > 0 && (
          <ChevronDown size={11} color="#4b5563" style={{ flexShrink: 0 }} />
        )}
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 4 }}
            transition={{ duration: 0.12 }}
            style={{
              position: 'absolute',
              top: '100%',
              left: 0,
              marginTop: 4,
              backgroundColor: '#161b22',
              border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 8,
              minWidth: 200,
              zIndex: 200,
              boxShadow: '0 12px 32px rgba(0,0,0,0.5)',
              overflow: 'hidden',
            }}
          >
            {options.map((opt) => (
              <button
                key={opt}
                onClick={() => { onSelect(opt); setOpen(false) }}
                style={{
                  display: 'block',
                  width: '100%',
                  textAlign: 'left',
                  padding: '8px 14px',
                  fontSize: 12,
                  color: '#d4d4d4',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  borderBottom: '1px solid rgba(255,255,255,0.04)',
                }}
                onMouseEnter={(e) => {
                  ;(e.currentTarget as HTMLButtonElement).style.backgroundColor = 'rgba(99,102,241,0.12)'
                }}
                onMouseLeave={(e) => {
                  ;(e.currentTarget as HTMLButtonElement).style.backgroundColor = 'transparent'
                }}
              >
                {opt}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

// ─── Device Info Row ──────────────────────────────────────────────────────────

function DeviceInfoRow({
  label,
  value,
  valueColor = '#94a3b8',
  mono = false,
}: {
  label: string
  value: string
  valueColor?: string
  mono?: boolean
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '5px 12px',
      }}
    >
      <span style={{ fontSize: 10, color: '#475569' }}>{label}</span>
      <span
        style={{
          fontSize: 10,
          color: valueColor,
          fontWeight: 500,
          fontFamily: mono ? 'monospace' : 'inherit',
        }}
      >
        {value}
      </span>
    </div>
  )
}

// ─── Main Component ───────────────────────────────────────────────────────────

let _stepCounter = 0

interface RecordStudioProps {
  onNavigateToExecute?: () => void
}

const DRAFT_KEY = 'qa_record_draft'

function loadDraftSteps(): RecStep[] {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return []
    const draft = JSON.parse(raw) as { steps: RecStep[] }
    return Array.isArray(draft.steps) ? draft.steps : []
  } catch { return [] }
}

function saveDraftSteps(steps: RecStep[]): void {
  try {
    if (steps.length === 0) {
      localStorage.removeItem(DRAFT_KEY)
    } else {
      localStorage.setItem(DRAFT_KEY, JSON.stringify({ steps, draftedAt: new Date().toISOString() }))
    }
  } catch { /* quota exceeded — non-critical */ }
}

function clearDraft(): void {
  localStorage.removeItem(DRAFT_KEY)
}

export default function RecordStudio({ onNavigateToExecute }: RecordStudioProps = {}) {
  // ── Runner lifecycle ────────────────────────────────────────────────────────
  const { isOnline: runnerOnline, initialized: runnerInitialized, startRunner } = useRunnerLifecycle()

  // ── State ──────────────────────────────────────────────────────────────────
  const [recState, setRecState] = useState<RecState>('idle')
  const [elapsed, setElapsed] = useState(0)
  // Initialize from draft so steps survive navigation away and back
  const [steps, setSteps] = useState<RecStep[]>(() => loadDraftSteps())
  const [screen, setScreen] = useState<AppScreen>('home')
  const [selectedDevice, setSelectedDevice] = useState<PhysicalDevice | null>(null)
  const [appConfig, setAppConfig] = useState<DeviceAppConfig | null>(null)
  const [devices, setDevices] = useState<PhysicalDevice[]>([])
  const [appConfigs, setAppConfigs] = useState<Record<string, DeviceAppConfig>>({})
  const [appMode, setAppMode] = useState('INSTALLED')
  const [lang, setLang] = useState<Lang>('java-testng')
  const [viewTab, setViewTab] = useState<ViewTab>('code')
  const [opts, setOpts] = useState<GenOpts>({
    pageObjects:     true,
    assertions:      false,
    smartWaits:      true,
    screenshots:     false,
    allureLogs:      false,
    reusableMethods: false,
  })
  const [testName, setTestName] = useState('testLoginFlow')
  const [className, setClassName] = useState('CinepolisTest')
  const [showSave, setShowSave] = useState<'caso' | 'suite' | null>(null)
  const [copied, setCopied] = useState(false)
  const [savedSuiteInfo, setSavedSuiteInfo] = useState<{ id: string; name: string } | null>(null)
  const [savedCaseName, setSavedCaseName] = useState<string | null>(null)
  const [savedStepIds, setSavedStepIds] = useState<string[]>([])
  const [toastMsg, setToastMsg] = useState<string | null>(null)
  const [sessionStart, setSessionStart] = useState<Date | null>(null)
  const [infoExpanded, setInfoExpanded] = useState(true)
  const [debugMode, setDebugMode] = useState(false)
  // ── Inspector state ───────────────────────────────────────────────────────
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null)
  const [inspectedElId, setInspectedElId] = useState<string | null>(null)
  // ── Live device mirror — direct MJPEG from Runner (port 8082) ────────────
  const { url: previewUrl, state: previewState } = useMirrorStream(selectedDevice?.udid ?? null)
  // ── Recording session (Runner recording engine on port 8082) ──────────────
  const { sessionId, deviceWidth, deviceHeight, start: startSession, stop: stopSession, send: sendStep, onPhysicalStep } = useRecordingSession()
  // ── Device viewer state ────────────────────────────────────────────────────
  const [isLandscape, setIsLandscape] = useState(false)
  const [isVideoRecording, setIsVideoRecording] = useState(false)
  const [captureFlash, setCaptureFlash] = useState(false)
  const [deviceFps] = useState(60)
  const [deviceBattery] = useState(87)
  // ── Latencia del mirror (mismo patrón que DeviceMirrorPanel del Dashboard):
  // mide el tiempo entre "conectando/cargando" y el primer frame real recibido.
  // No agrega ninguna llamada nueva al Runner — solo instrumenta el <img> que
  // PhoneFrame ya renderiza (ver onFrameLoad más abajo).
  const [mirrorConnMs, setMirrorConnMs] = useState<number | null>(null)
  const mirrorConnectStartRef = useRef<number | null>(null)
  useEffect(() => {
    if (previewState === 'connecting' || previewState === 'loading') {
      mirrorConnectStartRef.current = performance.now()
      setMirrorConnMs(null)
    } else if (previewState !== 'available' && previewState !== 'updating') {
      mirrorConnectStartRef.current = null
    }
  }, [previewState])
  const handleFrameLoad = useCallback(() => {
    if (mirrorConnectStartRef.current !== null) {
      setMirrorConnMs(Math.round(performance.now() - mirrorConnectStartRef.current))
      mirrorConnectStartRef.current = null
    }
  }, [])
  const deviceStatusVisual = useMemo(
    () => computeDeviceStatusVisual(selectedDevice, previewState),
    [selectedDevice, previewState],
  )

  // ── Fetch devices + configs ────────────────────────────────────────────────
  useEffect(() => {
    getDevices()
      .then((d) => setDevices(d))
      .catch(() => {})
    getAllDeviceAppConfigs()
      .then((c) => setAppConfigs(c))
      .catch(() => {})
  }, [])

  // ── Draft persistence — survives navigation away and back ─────────────────
  useEffect(() => { saveDraftSteps(steps) }, [steps])

  // ── Timer ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    if (recState !== 'recording') return
    const id = setInterval(() => setElapsed((s) => s + 1), 1000)
    return () => clearInterval(id)
  }, [recState])

  const elapsedStr = useMemo(() => formatElapsed(elapsed), [elapsed])

  // ── Step type counts ───────────────────────────────────────────────────────
  const tapCount = useMemo(
    () => steps.filter(s => s.type === 'tap' || s.type === 'double_tap' || s.type === 'long_press').length,
    [steps],
  )
  const scrollCount = useMemo(
    () => steps.filter(s => s.type === 'scroll').length,
    [steps],
  )
  const inputCount = useMemo(
    () => steps.filter(s => s.type === 'input').length,
    [steps],
  )

  // ── Device selection ───────────────────────────────────────────────────────
  const handleSelectDevice = useCallback(
    (name: string) => {
      const d = devices.find((dev) => dev.deviceName === name) ?? null
      setSelectedDevice(d)
      if (d) {
        setAppConfig(appConfigs[d.udid] ?? null)
        setAppMode(appConfigs[d.udid]?.appMode ?? 'INSTALLED')
      } else {
        setAppConfig(null)
      }
    },
    [devices, appConfigs],
  )

  const handleSelectApp = useCallback(
    (name: string) => {
      if (!selectedDevice) return
      const cfg = Object.values(appConfigs).find((c) => c.appName === name) ?? null
      setAppConfig(cfg)
    },
    [appConfigs, selectedDevice],
  )

  const handleSelectMode = useCallback((mode: string) => {
    setAppMode(mode)
  }, [])

  // ── Recording ──────────────────────────────────────────────────────────────

  /** Convert a raw step object from the Runner API into a RecStep. */
  const mapApiStep = useCallback((raw: unknown): RecStep => {
    const s = raw as {
      id: string; n: number; type: string
      el: AppEl | null; inputVal?: string
      dir?: string; timeStr: string
      screenName?: string
    }
    _stepCounter = Math.max(_stepCounter, s.n)
    return {
      id:         s.id,
      n:          s.n,
      type:       s.type as StepType,
      el:         s.el ?? null,
      inputVal:   s.inputVal,
      dir:        s.dir as RecStep['dir'],
      timeStr:    s.timeStr,
      screenName: s.screenName,
    }
  }, [])

  // Wire SSE physical-device events → steps panel
  useEffect(() => {
    onPhysicalStep((raw) => {
      const step = mapApiStep(raw)
      if (debugMode && step.el) {
        console.group(`[SemanticAnalyzer] Step ${step.n} — ${step.type}`)
        console.log('Platform:    ', step.el.platform ?? '?')
        console.log('Screen:      ', step.screenName ?? '(unknown)')
        console.log('Class:       ', step.el.className ?? '?')
        console.log('semanticName:', step.el.semanticName ?? '(none)')
        console.log('varName:     ', step.el.varName ?? '(none)')
        console.log('Locator:     ', `${step.el.locatorStrategy ?? '?'} = ${step.el.locatorValue ?? '?'}`)
        if (step.el.pageObjectAnnotation) {
          console.log('Page Object:\n' + step.el.pageObjectAnnotation)
        }
        if (!step.el.locatorValue?.trim()) {
          console.warn('WARN: locatorValue is empty — element may not be identifiable')
        }
        console.groupEnd()
      }
      setSteps(prev => [...prev, step])
    })
  }, [onPhysicalStep, mapApiStep, debugMode])

  const handleToggleRecording = useCallback(async () => {
    // Prevent starting a new recording when the runner is stopped
    if (!runnerOnline && recState === 'idle') {
      console.warn('[RecordStudio] Runner detenido — no se puede iniciar grabación')
      return
    }
    if (recState === 'idle') {
      setRecState('recording')
      setSessionStart(new Date())
      setElapsed(0)
      if (selectedDevice?.udid) {
        try {
          await startSession(selectedDevice.udid)
        } catch (e) {
          // Runner not reachable — recording works in local-only mode (manual steps)
          console.warn('[RecordStudio] Runner recording session unavailable:', e)
        }
      }
    } else {
      stopSession()
      setRecState('idle')
    }
  }, [recState, selectedDevice, startSession, stopSession, runnerOnline])

  /**
   * Handles taps/swipes from the interactive overlay on the live device mirror.
   * Converts normalized container coords → device pixel coords using the
   * objectFit:cover mapping (scale to width for portrait devices).
   */
  const handleScreenInteract = useCallback(
    async (
      nx: number, ny: number,
      gesture: 'tap' | 'swipe' | 'long_press',
      nx2?: number, ny2?: number,
    ) => {
      if (!sessionId || recState !== 'recording') return

      const CONTAINER_W = 262, CONTAINER_H = 452
      const scaleX = CONTAINER_W / deviceWidth
      const scaleY = CONTAINER_H / deviceHeight
      const scale  = Math.max(scaleX, scaleY)                    // objectFit: cover
      const offsetX = (CONTAINER_W - deviceWidth  * scale) / 2  // 0 for portrait
      const offsetY = (CONTAINER_H - deviceHeight * scale) / 2  // negative for tall phones

      const toDevice = (normX: number, normY: number) => ({
        x: Math.round(Math.max(0, Math.min(deviceWidth,  (normX * CONTAINER_W - offsetX) / scale))),
        y: Math.round(Math.max(0, Math.min(deviceHeight, (normY * CONTAINER_H - offsetY) / scale))),
      })

      const { x, y } = toDevice(nx, ny)

      let action: RecordingAction
      if (gesture === 'swipe' && nx2 !== undefined && ny2 !== undefined) {
        const end = toDevice(nx2, ny2)
        action = { action: 'swipe', x1: x, y1: y, x2: end.x, y2: end.y }
      } else if (gesture === 'long_press') {
        action = { action: 'long_press', x, y }
      } else {
        action = { action: 'tap', x, y }
      }

      const raw = await sendStep(action)
      if (raw) setSteps(prev => [...prev, mapApiStep(raw)])
    },
    [sessionId, recState, deviceWidth, deviceHeight, sendStep, mapApiStep],
  )

  const handleRecordEl = useCallback(
    (el: AppEl) => {
      if (recState !== 'recording') return
      _stepCounter++
      const newStep: RecStep = {
        id: `step_${Date.now()}_${Math.random().toString(36).slice(2)}`,
        n: _stepCounter,
        type: 'tap',
        el,
        timeStr: formatElapsed(elapsed),
      }
      setSteps((prev) => [...prev, newStep])
    },
    [recState, elapsed],
  )

  const handleManualAdd = useCallback(
    (
      type: StepType,
      elementId: string,
      inputVal?: string,
      dir?: 'up' | 'down' | 'left' | 'right',
    ) => {
      if (recState !== 'recording') return
      _stepCounter++

      let el: AppEl | null = null
      if (elementId.trim()) {
        el = {
          shortId: elementId.trim(),
          resourceId: `${ANDROID_PKG}:id/${elementId.trim()}`,
          accessId: elementId.trim(),
          text: elementId.trim(),
          elType: 'btn',
        }
      }

      const newStep: RecStep = {
        id: `step_${Date.now()}_${Math.random().toString(36).slice(2)}`,
        n: _stepCounter,
        type,
        el,
        inputVal: inputVal && inputVal.trim() ? inputVal.trim() : undefined,
        dir,
        timeStr: formatElapsed(elapsed),
      }
      setSteps((prev) => [...prev, newStep])
    },
    [recState, elapsed],
  )

  const handleDeleteStep = useCallback((id: string) => {
    setSteps((prev) => prev.filter((s) => s.id !== id))
  }, [])

  const handleDuplicateStep = useCallback((id: string) => {
    setSteps((prev) => {
      const idx = prev.findIndex(s => s.id === id)
      if (idx === -1) return prev
      const original = prev[idx]
      const clone: RecStep = {
        ...original,
        id: `step_${Date.now()}_dup`,
        n: 0,
      }
      const next = [...prev.slice(0, idx + 1), clone, ...prev.slice(idx + 1)]
      return next.map((s, i) => ({ ...s, n: i + 1 }))
    })
  }, [])

  const handleMoveStep = useCallback((id: string, dir: 'up' | 'down') => {
    setSteps((prev) => {
      const idx = prev.findIndex(s => s.id === id)
      if (idx === -1) return prev
      const target = dir === 'up' ? idx - 1 : idx + 1
      if (target < 0 || target >= prev.length) return prev
      const next = [...prev]
      ;[next[idx], next[target]] = [next[target], next[idx]]
      return next.map((s, i) => ({ ...s, n: i + 1 }))
    })
  }, [])

  const handleSelectStep = useCallback((step: RecStep) => {
    setSelectedStepId(step.id)
    setInspectedElId(step.el?.shortId ?? null)
    setViewTab('inspector')
  }, [])

  const handleEditStep = useCallback((id: string, updates: { elementId?: string; inputVal?: string; dir?: 'up' | 'down' | 'left' | 'right' }) => {
    setSteps((prev) =>
      prev.map(s => {
        if (s.id !== id) return s
        return {
          ...s,
          inputVal: updates.inputVal ?? s.inputVal,
          dir: updates.dir ?? s.dir,
          el: updates.elementId && s.el
            ? { ...s.el, shortId: updates.elementId, resourceId: updates.elementId, accessId: updates.elementId }
            : s.el,
        }
      })
    )
  }, [])

  // ── Code generation ────────────────────────────────────────────────────────
  const generatedCode = useMemo(
    () =>
      generateCode(
        steps,
        opts,
        selectedDevice?.platform ?? 'ANDROID',
        testName,
        className,
        lang,
      ),
    [steps, opts, selectedDevice, testName, className, lang],
  )

  const generatedXML = useMemo(
    () => generateXML(steps, selectedDevice?.platform ?? 'ANDROID'),
    [steps, selectedDevice],
  )

  // ── Copy / Download ────────────────────────────────────────────────────────
  const handleCopy = useCallback(async () => {
    const code = viewTab === 'code' ? generatedCode : generatedXML
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* ignore */
    }
  }, [viewTab, generatedCode, generatedXML])

  const handleDownload = useCallback(() => {
    const code = viewTab === 'code' ? generatedCode : generatedXML
    const blob = new Blob([code], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = viewTab === 'code'
      ? `${className || 'GeneratedTest'}.${getLangFileExt(lang)}`
      : 'recording.xml'
    a.click()
    URL.revokeObjectURL(url)
  }, [viewTab, generatedCode, generatedXML, className])

  // ── Save state helpers ─────────────────────────────────────────────────────
  const hasChangedAfterSave = savedSuiteInfo !== null && (
    steps.length !== savedStepIds.length ||
    steps.some((s, i) => s.id !== savedStepIds[i])
  )

  const showToast = useCallback((msg: string) => {
    setToastMsg(msg)
    setTimeout(() => setToastMsg(null), 3500)
  }, [])

  // ── Save ───────────────────────────────────────────────────────────────────
  const handleSave = useCallback(
    (data: { name: string; description: string; country: string; mode: 'caso' | 'suite'; targetSuiteId?: string; newSuiteName?: string }) => {
      const detectedPlatform =
        (steps.find(s => s.el?.platform)?.el?.platform as 'android' | 'ios' | undefined) ??
        (selectedDevice?.platform?.toLowerCase().includes('ios') ? 'ios' as const : 'android' as const)

      const suiteSteps: SuiteStep[] = steps.map(s => ({
        id: s.id, n: s.n, type: s.type, timeStr: s.timeStr,
        inputVal: s.inputVal, dir: s.dir,
        el: s.el ? {
          platform: s.el.platform, className: s.el.className,
          varName: s.el.varName, semanticName: s.el.semanticName,
          locatorStrategy: s.el.locatorStrategy, locatorValue: s.el.locatorValue,
          resourceId: s.el.resourceId, accessId: s.el.accessId,
          text: s.el.text, elType: s.el.elType, bounds: s.el.bounds,
          accessibilityLabel: s.el.accessibilityLabel,
          pageObjectAnnotation: s.el.pageObjectAnnotation,
          enabled: s.el.enabled, clickable: s.el.clickable, visible: s.el.visible,
        } : null,
      }))

      const pageObjects = generatedCode.match(
        /public class \w+Page extends BasePage \{[\s\S]*?\n\}/g
      )?.join('\n\n') ?? ''

      const commonSuiteData = {
        platform: detectedPlatform, device: selectedDevice?.deviceName ?? '',
        udid: selectedDevice?.udid ?? '', appName: appConfig?.appName ?? '',
        appPackage: appConfig?.appPackage ?? appConfig?.bundleId ?? '',
        lang, country: data.country,
      }

      if (data.mode === 'suite') {
        // "Guardar Suite" — creates an empty TestSuite container (no cases yet)
        const ts = suiteService.createSuite({
          name: data.name, description: data.description, ...commonSuiteData,
        })
        setSavedSuiteInfo({ id: ts.id, name: ts.name })
        setSavedCaseName(null)
        setSavedStepIds(steps.map(s => s.id))
        clearDraft()
        showToast(`Suite "${ts.name}" creada`)
        return
      }

      // "Guardar Caso" — creates a TestCase inside a TestSuite
      const caseData = {
        name: data.name, description: data.description,
        steps: suiteSteps, generatedCode, generatedXML, pageObjects,
        ...commonSuiteData,
      }

      let targetSuiteId = data.targetSuiteId
      let suiteName = ''

      if (!targetSuiteId && data.newSuiteName) {
        // Inline "create new suite" path
        const newSuite = suiteService.createSuite({
          name: data.newSuiteName, description: '', ...commonSuiteData,
        })
        targetSuiteId = newSuite.id
        suiteName = newSuite.name
      } else if (targetSuiteId) {
        suiteName = suiteService.getSuiteById(targetSuiteId)?.name ?? ''
      }

      if (targetSuiteId) {
        const tc = suiteService.addCase(targetSuiteId, caseData)
        setSavedSuiteInfo({ id: targetSuiteId, name: suiteName })
        setSavedCaseName(tc?.name ?? data.name)
        setSavedStepIds(steps.map(s => s.id))
        clearDraft()
        showToast(`Caso "${data.name}" guardado en "${suiteName}"`)
      } else {
        // No suite selected — create a solo suite with the case inside it
        const soloSuite = suiteService.createSuite({
          name: data.name, description: data.description, ...commonSuiteData,
        })
        const tc = suiteService.addCase(soloSuite.id, caseData)
        setSavedSuiteInfo({ id: soloSuite.id, name: soloSuite.name })
        setSavedCaseName(tc?.name ?? data.name)
        setSavedStepIds(steps.map(s => s.id))
        clearDraft()
        showToast(`Caso "${data.name}" guardado correctamente`)
      }

      if (debugMode) {
        console.group('[RecordStudio] Caso saved')
        console.log('Platform:', detectedPlatform, '| Steps:', suiteSteps.length, '| Lang:', lang)
        suiteSteps.forEach(s => {
          if (s.el?.varName) console.log(`  ${s.n} ${s.type}: ${s.el.varName} [${s.el.locatorStrategy}]`)
        })
        console.groupEnd()
      }
    },
    [steps, generatedCode, generatedXML, lang, selectedDevice, appConfig, debugMode, showToast],
  )

  // ── Export ─────────────────────────────────────────────────────────────────
  const handleExport = useCallback(() => {
    // Download test file
    const testBlob = new Blob([generatedCode], { type: 'text/plain' })
    const testUrl  = URL.createObjectURL(testBlob)
    const testA    = document.createElement('a')
    testA.href     = testUrl
    testA.download = `${className || 'GeneratedTest'}.${getLangFileExt(lang)}`
    testA.click()
    URL.revokeObjectURL(testUrl)

    // Download XML recording
    const xmlBlob = new Blob([generatedXML], { type: 'application/xml' })
    const xmlUrl  = URL.createObjectURL(xmlBlob)
    const xmlA    = document.createElement('a')
    xmlA.href     = xmlUrl
    xmlA.download = `${className || 'GeneratedTest'}_recording.xml`
    xmlA.click()
    URL.revokeObjectURL(xmlUrl)
  }, [generatedCode, generatedXML, className, lang])

  // ── Dropdown data ──────────────────────────────────────────────────────────
  const deviceNames = useMemo(() => devices.map((d) => d.deviceName), [devices])
  const appNames = useMemo(
    () => [...new Set(Object.values(appConfigs).map((c) => c.appName))],
    [appConfigs],
  )
  const modes = ['INSTALLED', 'APK', 'IPA']

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <>
    <style>{`
      @keyframes pulse { 0%,100% { opacity: 0.85; transform: scale(1); } 50% { opacity: 0.4; transform: scale(1.4); } }
      @keyframes spin  { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
    `}</style>
    <div
      style={{
        height: '100vh',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: '#0d1117',
        color: '#d4d4d4',
        fontFamily: 'system-ui, -apple-system, sans-serif',
      }}
    >
      {/* ── Configuration header (title + 4 cards) ── */}
      <RecordStudioHeader
        devices={devices}
        selectedDevice={selectedDevice}
        onSelectDevice={handleSelectDevice}
        appConfigs={appConfigs}
        appConfig={appConfig}
        onSelectApp={handleSelectApp}
        appMode={appMode}
        onSelectMode={handleSelectMode}
        isRecording={recState === 'recording'}
        elapsed={elapsed}
        onToggleRecording={handleToggleRecording}
      />

      {/* ── Runner stopped banner ── */}
      <AnimatePresence>
        {runnerInitialized && !runnerOnline && (
          <motion.div
            key="runner-stopped-banner"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.22, ease: 'easeOut' }}
            style={{ overflow: 'hidden', flexShrink: 0, zIndex: 10 }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '0 20px',
                height: 46,
                gap: 12,
                background: 'linear-gradient(90deg, rgba(99,102,241,0.12) 0%, rgba(13,17,23,0.95) 60%)',
                borderBottom: '1px solid rgba(99,102,241,0.25)',
                borderLeft: '3px solid #6366f1',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <AlertCircle size={14} color="#818cf8" />
                <span style={{ fontSize: 12, color: '#94a3b8', fontWeight: 600 }}>
                  El Runner está detenido. Actívalo para iniciar una sesión, ver el mirror y grabar pasos.
                </span>
              </div>
              <button
                onClick={() => startRunner()}
                style={{
                  display: 'flex', alignItems: 'center', gap: 5,
                  padding: '5px 12px', borderRadius: 7,
                  background: 'rgba(99,102,241,0.18)', border: '1px solid rgba(99,102,241,0.35)',
                  color: '#818cf8', fontSize: 11, fontWeight: 700, cursor: 'pointer',
                  flexShrink: 0,
                }}
              >
                <PlayCircle size={11} />
                Activar Runner
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── Recording Bar ── */}
      <AnimatePresence>
        {recState === 'recording' && (
          <motion.div
            key="recording-bar"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.22, ease: 'easeOut' }}
            style={{ overflow: 'hidden', flexShrink: 0, zIndex: 9 }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                padding: '0 20px',
                height: 48,
                gap: 0,
                background: 'linear-gradient(90deg, rgba(239,68,68,0.08) 0%, rgba(13,17,23,0.95) 60%)',
                borderBottom: '1px solid rgba(239,68,68,0.2)',
                borderLeft: '3px solid #ef4444',
                position: 'relative',
                overflow: 'hidden',
              }}
            >
              {/* Subtle scan line */}
              <motion.div
                animate={{ x: ['-100%', '200%'] }}
                transition={{ repeat: Infinity, duration: 3, ease: 'linear' }}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '30%',
                  height: '100%',
                  background: 'linear-gradient(90deg, transparent, rgba(239,68,68,0.04), transparent)',
                  pointerEvents: 'none',
                }}
              />

              {/* 🔴 Indicator + label */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginRight: 20 }}>
                <motion.div
                  animate={{ scale: [1, 1.3, 1], opacity: [1, 0.6, 1] }}
                  transition={{ repeat: Infinity, duration: 1.1, ease: 'easeInOut' }}
                  style={{
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    backgroundColor: '#ef4444',
                    boxShadow: '0 0 8px rgba(239,68,68,0.8)',
                  }}
                />
                <span
                  style={{
                    color: '#ef4444',
                    fontWeight: 800,
                    fontSize: 12,
                    letterSpacing: 1.5,
                  }}
                >
                  GRABANDO
                </span>
              </div>

              {/* Separator */}
              <div style={{ width: 1, height: 24, background: 'rgba(255,255,255,0.06)', marginRight: 20 }} />

              {/* ⏱ Timer */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginRight: 20 }}>
                <Clock size={12} color="#64748b" />
                <span
                  style={{
                    fontFamily: '"JetBrains Mono", "Fira Code", monospace',
                    fontSize: 14,
                    fontWeight: 700,
                    color: '#e2e8f0',
                    minWidth: 58,
                    letterSpacing: 1,
                  }}
                >
                  {elapsedStr}
                </span>
              </div>

              {/* Separator */}
              <div style={{ width: 1, height: 24, background: 'rgba(255,255,255,0.06)', marginRight: 20 }} />

              {/* Metrics */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 16, flex: 1 }}>
                {[
                  {
                    label: 'Pasos',
                    value: steps.length,
                    color: '#818cf8',
                    bg: 'rgba(129,140,248,0.1)',
                    border: 'rgba(129,140,248,0.2)',
                  },
                  {
                    label: 'Taps',
                    value: tapCount,
                    color: '#818cf8',
                    bg: 'rgba(129,140,248,0.08)',
                    border: 'rgba(129,140,248,0.15)',
                  },
                  {
                    label: 'Scroll',
                    value: scrollCount,
                    color: '#60a5fa',
                    bg: 'rgba(96,165,250,0.08)',
                    border: 'rgba(96,165,250,0.15)',
                  },
                  {
                    label: 'Inputs',
                    value: inputCount,
                    color: '#34d399',
                    bg: 'rgba(52,211,153,0.08)',
                    border: 'rgba(52,211,153,0.15)',
                  },
                ].map(({ label, value, color, bg, border }) => (
                  <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ fontSize: 11, color: '#475569' }}>{label}</span>
                    <motion.span
                      key={value}
                      initial={{ scale: 1.3, opacity: 0.6 }}
                      animate={{ scale: 1, opacity: 1 }}
                      transition={{ duration: 0.2 }}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        minWidth: 26,
                        height: 20,
                        borderRadius: 5,
                        background: bg,
                        border: `1px solid ${border}`,
                        color,
                        fontSize: 11,
                        fontWeight: 700,
                        fontFamily: 'monospace',
                        padding: '0 5px',
                      }}
                    >
                      {value}
                    </motion.span>
                  </div>
                ))}
              </div>

              {/* ⏹ Stop button */}
              <button
                onClick={handleToggleRecording}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 7,
                  padding: '8px 18px',
                  background: 'linear-gradient(135deg, rgba(239,68,68,0.2), rgba(220,38,38,0.15))',
                  border: '1px solid rgba(239,68,68,0.45)',
                  borderRadius: 8,
                  color: '#f87171',
                  fontSize: 12,
                  fontWeight: 700,
                  cursor: 'pointer',
                  transition: 'all 0.15s',
                  flexShrink: 0,
                  boxShadow: '0 0 14px rgba(239,68,68,0.1)',
                }}
                onMouseEnter={e => {
                  const b = e.currentTarget as HTMLButtonElement
                  b.style.background = 'linear-gradient(135deg, rgba(239,68,68,0.3), rgba(220,38,38,0.25))'
                  b.style.boxShadow = '0 0 20px rgba(239,68,68,0.2)'
                  b.style.color = '#fca5a5'
                }}
                onMouseLeave={e => {
                  const b = e.currentTarget as HTMLButtonElement
                  b.style.background = 'linear-gradient(135deg, rgba(239,68,68,0.2), rgba(220,38,38,0.15))'
                  b.style.boxShadow = '0 0 14px rgba(239,68,68,0.1)'
                  b.style.color = '#f87171'
                }}
              >
                <Square size={11} />
                Detener Grabación
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── Main body ── */}
      <div
        style={{
          flex: 1,
          display: 'grid',
          gridTemplateColumns: '440px 1fr 460px',
          minHeight: 0,
          overflow: 'hidden',
        }}
      >
        {/* ── Left column: Device Panel — mismo lenguaje visual que el Dashboard ── */}
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.4, ease: 'easeOut' }}
          className="flex flex-col overflow-hidden rounded-2xl m-3"
          style={{
            background: 'var(--panel-bg)',
            border: '1px solid var(--panel-border)',
            boxShadow: 'var(--panel-shadow)',
            minHeight: 0,
          }}
        >
          {/* ── Header: icono + nombre + modelo/OS + estado ── */}
          <div
            className="flex items-center justify-between px-5 py-4 flex-shrink-0 gap-2"
            style={{ borderBottom: '1px solid var(--panel-divide)' }}
          >
            <div className="flex items-center gap-2.5 min-w-0">
              <div
                className="flex items-center justify-center rounded-xl flex-shrink-0"
                style={{
                  width: 32, height: 32,
                  background: 'linear-gradient(135deg, rgba(99,102,241,0.2), rgba(129,140,248,0.1))',
                  border: '1px solid rgba(99,102,241,0.3)',
                }}
              >
                <Smartphone size={15} className="text-indigo-400" />
              </div>
              <div className="min-w-0">
                <div className="text-sm font-bold text-slate-100 truncate">
                  {selectedDevice ? resolveDeviceDisplayName(selectedDevice).title : 'Dispositivo en Vivo'}
                </div>
                <div className="text-xs text-slate-500 mt-0.5 truncate">
                  {selectedDevice
                    ? `${selectedDevice.model ?? (selectedDevice.platform === 'IOS' ? 'iPhone' : 'Android')} · ${selectedDevice.platform === 'IOS' ? 'iOS' : 'Android'}${selectedDevice.platformVersion ? ' ' + selectedDevice.platformVersion : ''}`
                    : 'Sin dispositivo seleccionado'}
                </div>
              </div>
            </div>
            <span
              className="flex items-center gap-1.5 px-2 py-1 rounded-lg text-[10px] font-bold flex-shrink-0"
              style={{
                color:      deviceStatusVisual.color,
                background: `${deviceStatusVisual.color}1f`,
                border:     `1px solid ${deviceStatusVisual.color}4d`,
              }}
            >
              {deviceStatusVisual.pulse ? (
                <motion.span
                  className="w-1.5 h-1.5 rounded-full inline-block"
                  style={{ background: deviceStatusVisual.color }}
                  animate={{ opacity: [1, 0.3, 1] }}
                  transition={{ duration: 1.2, repeat: Infinity }}
                />
              ) : (
                <span className="w-1.5 h-1.5 rounded-full inline-block" style={{ background: deviceStatusVisual.color }} />
              )}
              {deviceStatusVisual.label}
            </span>
          </div>

          {/* ── Meta row: Resolución · Latencia · FPS (mismo tratamiento tipográfico que el Dashboard) ── */}
          <div
            className="flex items-center gap-5 px-5 py-2.5 flex-shrink-0"
            style={{ borderBottom: '1px solid var(--panel-divide)' }}
          >
            {[
              { label: 'Resolución', value: '1080 × 2400' },
              { label: 'Latencia',   value: mirrorConnMs != null ? `${mirrorConnMs} ms` : '—' },
              { label: 'FPS',        value: String(deviceFps) },
            ].map(({ label, value }) => (
              <div key={label} className="flex flex-col">
                <span className="text-[9px] uppercase tracking-wider text-slate-600 font-bold">{label}</span>
                <span className="text-[11px] font-semibold text-slate-300 tabular-nums">{value}</span>
              </div>
            ))}
          </div>

          {/* ── Toolbar: mismo componente ToolbarIconButton para los 6 controles ── */}
          <div
            className="flex items-center gap-1.5 px-4 py-2.5 flex-shrink-0"
            style={{ borderBottom: '1px solid var(--panel-divide)' }}
          >
            <ToolbarIconButton
              icon={Camera}
              title="Capturar pantalla"
              active={captureFlash}
              onClick={() => {
                setCaptureFlash(true)
                setTimeout(() => setCaptureFlash(false), 300)
              }}
            />
            <ToolbarIconButton
              icon={isVideoRecording ? Square : Video}
              title={isVideoRecording ? 'Detener video' : 'Grabar video'}
              active={isVideoRecording}
              onClick={() => setIsVideoRecording(v => !v)}
            />
            <ToolbarIconButton
              icon={RotateCw}
              title="Rotar dispositivo"
              active={isLandscape}
              onClick={() => setIsLandscape(l => !l)}
            />
            <ToolbarIconButton
              icon={RotateCcw}
              title="Actualizar pantalla"
              onClick={() => setScreen('home')}
            />
            <ToolbarIconButton
              icon={Maximize2}
              title="Pantalla completa"
              onClick={() => {}}
            />
            <ToolbarIconButton
              icon={Eye}
              title={debugMode ? 'Desactivar modo debug' : 'Activar modo debug'}
              active={debugMode}
              onClick={() => setDebugMode(v => !v)}
            />
          </div>

          {/* ── Phone frame area — el dispositivo como protagonista visual ── */}
          <div
            className="flex-1 min-h-0 flex items-center justify-center relative"
            style={{
              padding: isLandscape ? '10px 16px' : '20px 16px',
              overflow: 'hidden',
              background: 'var(--terminal-bg)',
            }}
          >
            {/* Capture flash overlay */}
            <AnimatePresence>
              {captureFlash && (
                <motion.div
                  initial={{ opacity: 0.6 }}
                  animate={{ opacity: 0 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.3 }}
                  style={{
                    position: 'absolute',
                    inset: 0,
                    background: '#fff',
                    zIndex: 10,
                    pointerEvents: 'none',
                    borderRadius: 8,
                  }}
                />
              )}
            </AnimatePresence>

            {/* Transición suave cuando cambia la pantalla — solo el contenedor, PhoneFrame no cambia */}
            <motion.div
              key={screen}
              initial={{ opacity: 0.4 }}
              animate={{ opacity: 1 }}
              transition={{ duration: 0.25, ease: 'easeOut' }}
            >
              {/* Phone with rotation animation */}
              <motion.div
                animate={{
                  rotate: isLandscape ? -90 : 0,
                  scale: isLandscape ? 0.58 : 1,
                }}
                transition={{ type: 'spring', stiffness: 200, damping: 24 }}
                style={{ transformOrigin: 'center center' }}
              >
                <PhoneFrame
                  recording={recState === 'recording'}
                  screen={screen}
                  onRecord={handleRecordEl}
                  onScreenChange={setScreen}
                  isLandscape={isLandscape}
                  inspectedElId={inspectedElId ?? undefined}
                  previewUrl={previewUrl}
                  previewState={previewState}
                  onScreenInteract={sessionId ? handleScreenInteract : undefined}
                  onFrameLoad={handleFrameLoad}
                />
              </motion.div>
            </motion.div>
          </div>

          {/* ── Device info panel — mismo sistema de cards anidadas que el Dashboard ── */}
          <div
            className="mx-3.5 mb-3.5 rounded-xl overflow-hidden flex-shrink-0"
            style={{ background: 'rgba(255,255,255,0.025)', border: '1px solid var(--panel-divide)' }}
          >
            <div
              className="px-3 py-2 flex items-center justify-between"
              style={{ borderBottom: '1px solid var(--panel-divide)' }}
            >
              <div className="flex items-center gap-1.5">
                <Wifi size={10} className="text-indigo-400" />
                <span className="text-[10px] font-bold tracking-wide text-slate-500">
                  INFO DEL DISPOSITIVO
                </span>
              </div>
              <span className="text-[9px] text-slate-700">
                {selectedDevice?.platform ?? 'ANDROID'}
              </span>
            </div>

            <div style={{ padding: '6px 0' }}>
              {/* Name */}
              <DeviceInfoRow
                label="Nombre"
                value={selectedDevice ? resolveDeviceDisplayName(selectedDevice).title : 'Samsung Galaxy A52'}
                valueColor="#e2e8f0"
              />
              {/* Platform chip */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '5px 12px' }}>
                <span style={{ fontSize: 10, color: '#475569' }}>Plataforma</span>
                <span
                  style={{
                    fontSize: 9,
                    fontWeight: 600,
                    color: (selectedDevice?.platform ?? 'ANDROID') === 'IOS' ? '#a78bfa' : '#34d399',
                    background: (selectedDevice?.platform ?? 'ANDROID') === 'IOS'
                      ? 'rgba(167,139,250,0.1)'
                      : 'rgba(52,211,153,0.1)',
                    border: `1px solid ${(selectedDevice?.platform ?? 'ANDROID') === 'IOS' ? 'rgba(167,139,250,0.25)' : 'rgba(52,211,153,0.25)'}`,
                    padding: '1px 7px',
                    borderRadius: 20,
                  }}
                >
                  {selectedDevice?.platform ?? 'ANDROID'}
                </span>
              </div>
              {/* Model (real cuando el backend lo reporta; mismo fallback de antes si no) */}
              <DeviceInfoRow
                label="Modelo"
                value={selectedDevice?.model ?? (selectedDevice?.platform === 'IOS' ? 'iPhone' : 'Galaxy A52')}
                valueColor="#94a3b8"
              />
              {/* Version (real cuando el backend lo reporta; mismo fallback de antes si no) */}
              <DeviceInfoRow
                label="Versión"
                value={
                  selectedDevice?.platformVersion
                    ?? ((selectedDevice?.platform ?? 'ANDROID') === 'IOS' ? 'iOS 17.4' : 'Android 13')
                }
                valueColor="#94a3b8"
              />
              {/* Resolution */}
              <DeviceInfoRow label="Resolución" value="1080 × 2400" valueColor="#94a3b8" mono />
              {/* FPS */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '5px 12px' }}>
                <span style={{ fontSize: 10, color: '#475569' }}>FPS</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                  <span style={{ fontSize: 10, color: '#60a5fa', fontWeight: 600, fontFamily: 'monospace' }}>
                    {deviceFps}
                  </span>
                  <div style={{ display: 'flex', gap: 2 }}>
                    {[...Array(5)].map((_, i) => (
                      <div
                        key={i}
                        style={{
                          width: 3,
                          height: 4 + i * 2,
                          borderRadius: 1,
                          backgroundColor: i < 4 ? '#60a5fa' : 'rgba(96,165,250,0.3)',
                        }}
                      />
                    ))}
                  </div>
                </div>
              </div>
              {/* Battery */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '5px 12px' }}>
                <span style={{ fontSize: 10, color: '#475569' }}>Batería</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <div
                    style={{
                      width: 28,
                      height: 12,
                      borderRadius: 3,
                      border: '1px solid rgba(255,255,255,0.15)',
                      position: 'relative',
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        position: 'absolute',
                        left: 1,
                        top: 1,
                        width: `${deviceBattery - 4}%`,
                        height: 'calc(100% - 2px)',
                        background: deviceBattery > 20
                          ? 'linear-gradient(90deg, #34d399, #4ade80)'
                          : '#ef4444',
                        borderRadius: 2,
                        transition: 'width 0.3s',
                      }}
                    />
                  </div>
                  <span style={{ fontSize: 10, color: '#94a3b8', fontFamily: 'monospace' }}>
                    {deviceBattery}%
                  </span>
                </div>
              </div>
              {/* Orientation */}
              <DeviceInfoRow
                label="Orientación"
                value={isLandscape ? 'Landscape' : 'Portrait'}
                valueColor="#94a3b8"
              />
              {/* Pantalla actual */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '5px 12px',
                  borderTop: '1px solid rgba(255,255,255,0.05)',
                  marginTop: 2,
                }}
              >
                <span style={{ fontSize: 10, color: '#475569' }}>Pantalla</span>
                <span style={{ fontSize: 10, color: '#818cf8', fontWeight: 600 }}>
                  {screen === 'home' ? 'Home' : 'Login'}
                </span>
              </div>
            </div>
          </div>

          {/* ── Manual action bar when recording ── */}
          {recState === 'recording' && (
            <div
              className="mx-3.5 mb-3.5 rounded-xl flex-shrink-0"
              style={{ background: 'rgba(255,255,255,0.025)', border: '1px solid var(--panel-divide)', padding: '10px 10px 8px' }}
            >
              <p className="text-[9px] font-bold tracking-wide text-slate-600" style={{ margin: '0 0 7px 2px' }}>
                AGREGAR ACCIÓN MANUAL
              </p>
              <ManualActionBar onManualAdd={handleManualAdd} />
            </div>
          )}
        </motion.div>

        {/* ── Middle column: Steps ── */}
        <div
          style={{ minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
        >
          <StepsPanel
            steps={steps}
            recording={recState === 'recording'}
            isDraft={recState === 'idle' && steps.length > 0 && savedSuiteInfo === null}
            savedSuiteName={savedSuiteInfo?.name ?? null}
            savedCaseName={savedCaseName}
            hasChangesAfterSave={hasChangedAfterSave}
            selectedStepId={selectedStepId}
            onDeleteStep={handleDeleteStep}
            onDuplicateStep={handleDuplicateStep}
            onMoveStep={handleMoveStep}
            onEditStep={handleEditStep}
            onSelectStep={handleSelectStep}
            onManualAdd={handleManualAdd}
          />
        </div>

        {/* ── Right column: Code ── */}
        <div
          style={{
            borderLeft: '1px solid rgba(255,255,255,0.07)',
            minHeight: 0,
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <CodePanel
            steps={steps}
            lang={lang}
            viewTab={viewTab}
            opts={opts}
            testName={testName}
            className={className}
            generatedCode={generatedCode}
            generatedXML={generatedXML}
            currentScreen={screen}
            inspectedElId={inspectedElId}
            onLangChange={setLang}
            onViewTabChange={setViewTab}
            onOptsChange={setOpts}
            onTestNameChange={setTestName}
            onClassNameChange={setClassName}
            onInspectEl={id => { setInspectedElId(id); setViewTab('inspector') }}
            onCopy={handleCopy}
            onDownload={handleDownload}
            onSaveCase={() => setShowSave('caso')}
            onSaveSuite={() => setShowSave('suite')}
            onExecute={() => onNavigateToExecute?.()}
            onExport={handleExport}
            copied={copied}
          />
        </div>
      </div>

      {/* ── Session info bar ── */}
      <SessionInfoBar
        sessionStart={sessionStart}
        device={selectedDevice}
        appConfig={appConfig}
        appMode={appMode}
        elapsed={elapsed}
        stepCount={steps.length}
        expanded={infoExpanded}
        onToggle={() => setInfoExpanded((p) => !p)}
      />

      {/* ── Save Modal ── */}
      <AnimatePresence>
        {showSave && (
          <SaveSuiteModal
            mode={showSave}
            onClose={() => setShowSave(null)}
            onConfirm={(data) => {
              handleSave(data)
            }}
          />
        )}
      </AnimatePresence>

      {/* ── Toast notification ── */}
      <AnimatePresence>
        {toastMsg && (
          <motion.div
            key="toast"
            initial={{ opacity: 0, y: 20, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 10, scale: 0.95 }}
            style={{
              position: 'fixed',
              bottom: 28,
              right: 28,
              zIndex: 9999,
              background: 'linear-gradient(135deg, #1e293b, #0f172a)',
              border: '1px solid rgba(52,211,153,0.35)',
              borderRadius: 10,
              padding: '11px 18px',
              display: 'flex',
              alignItems: 'center',
              gap: 9,
              boxShadow: '0 8px 32px rgba(0,0,0,0.5), 0 0 0 1px rgba(52,211,153,0.15)',
              maxWidth: 340,
            }}
          >
            <Check size={15} color="#34d399" style={{ flexShrink: 0 }} />
            <span style={{ color: '#e2e8f0', fontSize: 12, fontWeight: 500 }}>{toastMsg}</span>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
    </>
  )
}
