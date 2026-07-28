export interface TestSuite {
  id: string;
  title: string;
  description: string;
  icon: string;
  accent: string;
}

export interface IndividualTest {
  id: string;
  title: string;
  description: string;
}

export interface Country {
  id: string;
  name: string;
  flag: string;
  hasSubMenu: boolean;
}

export type RunStatus = 'idle' | 'running' | 'finished';

export type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'PASS' | 'FAIL' | 'SKIP';

export interface LogEntry {
  id: string;
  time: string;
  level: LogLevel;
  message: string;
}

// ─── Execution events (arquitectura de eventos — Actividad en Tiempo Real) ────

export type EventCategory = 'BUSINESS' | 'TECHNICAL' | 'DEBUG' | 'TRACE';
export type EventSeverity = 'INFO' | 'SUCCESS' | 'WARN' | 'ERROR';

export interface ExecutionEventProgress {
  current: number;
  total: number;
}

export interface ExecutionEvent {
  id: string;
  executionId: string;
  timestamp: string;
  severity: EventSeverity;
  category: EventCategory;
  source: string;
  /** Vocabulario cerrado del Runner (ver EventType.java), p.ej. "CASE_PASSED" — solo valores publicados explícitamente. */
  type: string;
  message: string;
  details?: string | null;
  progress?: ExecutionEventProgress | null;
  suite?: string | null;
  test?: string | null;
  device?: string | null;
}

export interface RunState {
  status: RunStatus;
  passed: number;
  failed: number;
  skipped: number;
  total: number;
  /** Total de tests esperados para esta ejecución (0 = desconocido). */
  totalExpected: number;
  lastRun: string | null;
  logs: LogEntry[];
  events: ExecutionEvent[];
  activeSuite: string | null;
  executionId: string | null;
}

export type ExecutionStatus =
  | 'PENDING' | 'QUEUED' | 'STARTING' | 'RUNNING' | 'FINALIZING' | 'ABORTING'
  | 'PASSED'  | 'FAILED' | 'FAILED_FINALIZATION' | 'SKIPPED'
  | 'COMPLETED' | 'ABORTED' | 'INCOMPLETE'

export interface DeviceConfig {
  id:              string
  name:            string
  platform:        'android' | 'ios'
  platformVersion: string
  deviceName:      string
  udid:            string
  automationName:  'UiAutomator2' | 'XCUITest' | 'Espresso'
  hub:             'local' | 'browserstack' | 'aws-device-farm' | 'genymotion'
  // Android: appPackage / iOS: bundleId (reutilizado para evitar migración)
  appPackage:      string
  appActivity:     string  // Android only
  status:          'available' | 'inuse' | 'offline'
  isActive:        boolean
  // iOS-specific (optional)
  xcodeOrgId?:     string
  xcodeSigningId?: string
  wdaLocalPort?:   string
  ipaPath?:        string
};

export interface TestCaseResult {
  name: string;
  status: 'PASS' | 'FAIL' | 'SKIP';
}

// ─── Device Farm ──────────────────────────────────────────────────────────────

export type DeviceStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE' | 'MAINTENANCE' | 'DISCOVERED'

export interface PhysicalDevice {
  udid:            string
  deviceName:      string
  model:           string | null
  manufacturer:    string | null
  platform:        'ANDROID' | 'IOS' | string
  platformVersion: string | null
  status:          DeviceStatus
  runnerId:        string | null
  activeExecutionId: string | null
  lastSeen:        string | null
  registeredAt:    string | null
  // DeviceAvailability model — computed by Runner
  presence?:          'USB' | 'LOCAL_NETWORK' | 'UNKNOWN' | null
  tunnel?:            'CONNECTED' | 'DISCONNECTED' | 'UNKNOWN' | null
  readyForExecution?: boolean | null
  notReadyReason?:    string | null
}

// ─── Runner Manager ──────────────────────────────────────────────────────────

export type RunnerStatus = 'ONLINE' | 'OFFLINE' | 'BUSY' | 'STARTING' | 'STOPPING' | 'DEGRADED'

export interface RunnerDevice {
  deviceId:   string
  deviceName: string
  platform:   string
  status:     string
  runnerId?:  string
}

export interface Runner {
  runnerId:          string
  platform:          'android' | 'ios' | string
  version:           string
  status:            RunnerStatus
  lastSeen:          string | null
  registeredAt:      string | null
  devices:           RunnerDevice[]
  // Universal Runner fields (auto-detected at startup)
  os?:               'WINDOWS' | 'MACOS' | 'LINUX' | string
  hostname?:         string
  androidSupported?: boolean
  iosSupported?:     boolean
  // Embedded ADB diagnostics (from PlatformToolsManager)
  adbPath?:               string
  adbVersion?:            string
  adbExists?:             boolean
  adbOk?:                 boolean
  devicesFound?:          number
  platformToolsInstalled?: boolean
  // Component telemetry (v4.0 — enterprise agent)
  jreInstalled?:          boolean
  jreVersion?:            string
  nodeInstalled?:         boolean
  nodeVersion?:           string
  appiumInstalled?:       boolean
  appiumVersion?:         string
  xcodeInstalled?:        boolean
  xcodeVersion?:          string
}

// ─── Device App Config ────────────────────────────────────────────────────────

export interface DeviceAppConfig {
  deviceId:   string
  platform:   string
  appMode:    'INSTALLED' | 'APK' | 'IPA'
  appName:    string
  appPackage: string
  bundleId:   string
  appVersion: string
  source:     string
}

// ─── Videos ───────────────────────────────────────────────────────────────────

export type VideoStatus = 'PASS' | 'FAIL' | 'SKIP' | 'UNKNOWN'

export interface VideoRecord {
  id:           string
  executionId:  string
  suiteName:    string
  testName:     string
  originalName: string
  sizeBytes:    number
  createdAt:    string
  status:       VideoStatus | null
  device:       string | null
  env:          string | null
}

export interface VideoSuiteSummary {
  suiteName:        string
  videoCount:       number
  lastExecutionAt:  string | null
  totalSizeBytes:   number
  overallStatus:    'PASSED' | 'FAILED' | 'MIXED' | 'UNKNOWN'
}

export interface VideoQueryResult {
  items:    VideoRecord[]
  total:    number
  page:     number
  pageSize: number
}

export interface ExecutionSummary {
  executionId: string;
  suite: string;
  env: string;
  device: string;
  country: string;
  status: ExecutionStatus;
  startTime: string;
  endTime: string | null;
  passed: number;
  failed: number;
  skipped: number;
  total: number;
  /** Planificado por el Runner; 0 = desconocido. Menor que total => status INCOMPLETE. */
  expectedCount?: number;
  allureUrl: string | null;
  testCases?: TestCaseResult[];
}
