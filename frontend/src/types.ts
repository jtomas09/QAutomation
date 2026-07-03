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
  activeSuite: string | null;
  executionId: string | null;
}

export type ExecutionStatus =
  | 'PENDING' | 'QUEUED' | 'STARTING' | 'RUNNING' | 'FINALIZING' | 'ABORTING'
  | 'PASSED'  | 'FAILED' | 'FAILED_FINALIZATION' | 'SKIPPED'
  | 'COMPLETED' | 'ABORTED'

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
  allureUrl: string | null;
  testCases?: TestCaseResult[];
}
