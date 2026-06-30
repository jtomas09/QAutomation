/**
 * Platform-independent accessibility types for RecordStudio.
 *
 * These types mirror the Java UIElement / AccessibilityInspector model so that
 * the frontend can handle both Android and iOS elements with a single code path.
 *
 * Backward compatibility: UIElement extends AppEl so that existing code
 * generators (which read el.resourceId / el.accessId / el.text) keep working
 * unchanged — the new fields are purely additive.
 */

// ── Platform ──────────────────────────────────────────────────────────────────

export type Platform = 'android' | 'ios'

// ── Locator strategies ────────────────────────────────────────────────────────

/** Resolved locator strategy, consistent across platforms. */
export type LocatorStrategy =
  | 'id'               // Android resource-id
  | 'accessibility_id' // Android content-desc / iOS name (accessibility identifier)
  | 'text'             // Android text attribute
  | 'predicate_string' // iOS NSPredicate (e.g. label == "Login")
  | 'class_chain'      // iOS XCUITest class chain
  | 'xpath'            // fallback — never preferred when a better strategy exists

export interface Locator {
  strategy: LocatorStrategy
  value:    string
}

// ── UI element ────────────────────────────────────────────────────────────────

/**
 * Enriched element model returned by the backend AccessibilityInspector.
 *
 * Backward-compat fields (also present on the old AppEl):
 *   shortId, resourceId, accessId, text, elType, bounds
 *
 * New fields (added for multi-platform support):
 *   platform, className, locatorStrategy, locatorValue,
 *   accessibilityLabel, packageName, bundleId,
 *   enabled, clickable, visible
 */
export interface UIElement {
  // ── Platform ──────────────────────────────────────────────────────────────
  platform: Platform

  // ── Element identity ──────────────────────────────────────────────────────
  className:          string   // e.g. "android.widget.Button" | "XCUIElementTypeButton"
  locatorStrategy:    string   // resolved best strategy (see LocatorStrategy)
  locatorValue:       string   // ready-to-use locator value
  accessibilityLabel: string   // content-desc (Android) | label (iOS)
  packageName:        string   // Android package; empty for iOS
  bundleId:           string   // iOS bundle id; empty for Android

  // ── State ─────────────────────────────────────────────────────────────────
  enabled:  boolean
  clickable: boolean
  visible:  boolean

  // ── Backward-compat (kept for code generators) ────────────────────────────
  shortId:    string   // short name used in page-object method names
  resourceId: string   // Android resource-id; empty for iOS
  accessId:   string   // Android content-desc OR iOS accessibility identifier
  text:       string   // visible text / label
  elType:     string   // btn | input | text | list | image
  bounds:     string   // "[x1,y1][x2,y2]"
}

// ── Recorded step ─────────────────────────────────────────────────────────────

export type ActionType =
  | 'tap' | 'double_tap' | 'long_press'
  | 'input' | 'swipe' | 'scroll'
  | 'hide_keyboard' | 'back' | 'home'
  | 'assertion' | 'screenshot'

export interface RecordedStep {
  /** Unique step identifier */
  id:          string
  /** Step number in recording sequence */
  n:           number
  action:      ActionType
  /** The UI element that was interacted with (null for scroll/swipe/key events) */
  element:     UIElement | null
  /** Typed text for 'input' steps */
  inputVal?:   string
  /** Direction for 'scroll' and 'swipe' steps */
  dir?:        'up' | 'down' | 'left' | 'right'
  /** Wall-clock timestamp (mm:ss) from recording start */
  timeStr:     string
  /** Screen/Activity name at the time of recording */
  screen?:     string
  /** Application package (Android) or bundle id (iOS) */
  application?: string
}

// ── Hierarchy ─────────────────────────────────────────────────────────────────

export interface ViewHierarchy {
  platform: Platform
  xml:      string
  /** ISO timestamp when the hierarchy was captured */
  capturedAt: string
}

// ── Inspector result ──────────────────────────────────────────────────────────

/**
 * Shape of the JSON object returned by the backend recording endpoints.
 * This is what RecordStudio.mapApiStep() should map to its internal RecStep.
 */
export interface ApiStep {
  id:       string
  n:        number
  type:     ActionType
  el:       UIElement | null
  inputVal?: string
  dir?:     'up' | 'down' | 'left' | 'right'
  timeStr:  string
}
