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
  | 'PENDING' | 'QUEUED' | 'RUNNING'
  | 'PASSED'  | 'FAILED' | 'SKIPPED'
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
