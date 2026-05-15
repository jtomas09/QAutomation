export interface TestSuite {
  id: string;
  title: string;
  description: string;
  icon: string;
  accent: string;
}

export interface Country {
  id: string;
  name: string;
  flag: string;
  hasSubMenu: boolean;
}

export type RunStatus = 'idle' | 'running' | 'finished';

export type LogLevel = 'INFO' | 'WARN' | 'ERROR' | 'PASS' | 'FAIL';

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
  lastRun: string | null;
  logs: LogEntry[];
  activeSuite: string | null;
}
