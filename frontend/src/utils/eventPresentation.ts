import type { ExecutionEvent } from '../types'
import { isFunctionalLog } from './logFilter'

/**
 * Lookup por TIPO de evento — no regex sobre el mensaje. A diferencia de
 * logFilter.ts (que sigue existiendo solo como fallback para eventos legacy
 * "RAW_LOG", ver isVisibleInTimeline más abajo), esto nunca intenta adivinar
 * nada a partir de texto: el Runner ya decidió qué es cada evento en el
 * momento en que lo publicó (ver qa.cinepolis.runner.events.EventType).
 */
export const EVENT_ICON: Record<string, string> = {
  REPO_CLONE_START:     'GitBranch',
  REPO_CLONE_DONE:      'GitBranch',
  DEVICE_PREPARE_START: 'Smartphone',
  DEVICE_PREPARE_DONE:  'Smartphone',
  APPIUM_START:         'Play',
  APPIUM_READY:         'Play',
  DRIVER_CREATE_START:  'Cpu',
  DRIVER_CREATE_DONE:   'Cpu',
  SUITE_START:          'ListChecks',
  SUITE_DONE:           'ListChecks',
  CASE_START:           'ChevronRight',
  CASE_PASSED:          'CheckCircle2',
  CASE_FAILED:          'XCircle',
  CASE_SKIPPED:         'MinusCircle',
  CASE_RETRY:           'RotateCw',
  REPORT_GENERATING:    'FileBarChart',
  REPORT_READY:         'FileBarChart',
  MAIL_SENDING:         'Mail',
  MAIL_SENT:            'Mail',
  EXECUTION_FINISHED:   'FlagTriangleRight',
}

export const DEFAULT_EVENT_ICON = 'Circle'

export const SEVERITY_COLOR: Record<string, string> = {
  INFO:    '#60a5fa',
  SUCCESS: '#10b981',
  WARN:    '#f59e0b',
  ERROR:   '#f43f5e',
}

/**
 * ¿Este evento pertenece al Timeline principal ("Actividad")?
 *
 * Eventos ya migrados (type !== 'RAW_LOG'): decisión pura por category —
 * BUSINESS y nada más. Nunca se mira `message`.
 *
 * Eventos legacy (type === 'RAW_LOG', puente de BackendClient.sendLog() sin
 * migrar todavía — ver ExecutionService.addLog en el backend): se aplica el
 * clasificador heredado (isFunctionalLog) como fallback transicional, exactamente
 * como describe la Fase 3 de la migración — nunca se descarta narración que
 * hoy es visible solo porque su emisor todavía no fue migrado a
 * ExecutionEventPublisher.
 */
export function isVisibleInTimeline(e: ExecutionEvent): boolean {
  if (e.type !== 'RAW_LOG') return e.category === 'BUSINESS'
  return isFunctionalLog({ id: e.id, time: e.timestamp, level: e.severity as any, message: e.message })
}
