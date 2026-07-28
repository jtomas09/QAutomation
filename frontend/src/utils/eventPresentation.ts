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
 * migrar todavía — ver ExecutionService.addLog en el backend):
 *
 * FIX real (evidencia en vivo — el Pre-flight de iOS, IosPreflightManager/
 * AppleDeveloperTeamManager/CoreDeviceTunnelManager, sigue mandando su
 * narración completa a nivel "INFO" sin migrar todavía a un EventType propio;
 * el fallback ORIGINAL de esta función delegaba TODO RAW_LOG al clasificador
 * heredado sin mirar `category`, así que aunque el backend ya clasificara
 * correctamente un mensaje como DEBUG (fromLegacyLog), esta función lo
 * ignoraba y lo mostraba igual si el regex heredado no lo reconocía — que es
 * exactamente el bug original que esta arquitectura debía resolver). Ahora
 * category SÍ es la primera autoridad también para RAW_LOG: DEBUG/TRACE se
 * ocultan siempre, sin excepción — nunca fueron pensados para el Timeline. El
 * regex heredado solo decide en la zona ambigua real (TECHNICAL, es decir
 * INFO/WARN/ERROR legacy que fromLegacyLog no puede distinguir de narración de
 * negocio todavía no migrada) — así no se pierde nada que hoy sea visible,
 * pero DEBUG/TRACE ya no dependen de que el regex adivine bien.
 */
export function isVisibleInTimeline(e: ExecutionEvent): boolean {
  if (e.category === 'DEBUG' || e.category === 'TRACE') return false
  if (e.type !== 'RAW_LOG') return e.category === 'BUSINESS'
  return isFunctionalLog({ id: e.id, time: e.timestamp, level: e.severity as any, message: e.message })
}
