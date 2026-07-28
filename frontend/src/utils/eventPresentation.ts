import type { ExecutionEvent } from '../types'

/**
 * Lookup por TIPO de evento — nunca regex sobre el mensaje. El canal
 * "execution-event" ya no recibe NADA derivado de un log (ver
 * ExecutionService.addLog en el backend — el puente que traducía cada log a
 * un evento fue eliminado): todo lo que llega aquí fue publicado
 * explícitamente por el Runner en su origen real (ver
 * qa.cinepolis.runner.events.ExecutionEventPublisher), así que no hace falta
 * ningún clasificador heredado como fallback — logFilter.ts/isFunctionalLog
 * queda reservado exclusivamente para el modo legacy de ActivityLog (cuando
 * no hay `events` disponible en absoluto).
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
 * Ya no hay zona ambigua ni fallback por regex: el canal "execution-event"
 * solo contiene eventos publicados explícitamente (ver EventPublisher/
 * ExecutionEventPublisher), así que `category === 'BUSINESS'` es la única
 * pregunta que hace falta — nunca se mira `message` ni ningún patrón de texto.
 * Esta función existe igual (en vez de solo `events` directo) por si en el
 * futuro se agrega alguna categoría no-BUSINESS a este mismo canal.
 */
export function isVisibleInTimeline(e: ExecutionEvent): boolean {
  return e.category === 'BUSINESS'
}
