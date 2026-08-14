/**
 * mirrorStatus — único punto de verdad para decidir qué overlay mostrar sobre
 * el mirror de un dispositivo (Dashboard y Record Studio consumen exactamente
 * esta misma lógica, ver DeviceMirrorPanel.tsx y RecordStudio.tsx).
 *
 * Arquitectura clave (lección ya aprendida una vez en DeviceMirrorPanel — ver
 * historial de "Problema 2"): "¿existe el stream (se monta el <img>)?" y
 * "¿qué overlay se dibuja encima?" son DOS preguntas independientes. Antes,
 * ambas eran la misma señal — eso crea un ciclo circular: si el <img> solo se
 * monta cuando mirrorPhase === MIRROR_ACTIVE, pero el Runner solo reporta
 * MIRROR_ACTIVE una vez que el stream ya está siendo consumido, el <img>
 * nunca se monta y la fase nunca avanza. El stream se monta con la única
 * condición real (UDID + Runner alcanzable, ya encapsulados en `url` de
 * useMirrorStream) — mirrorPhase decide SOLO qué overlay dibujar encima,
 * nunca si el stream existe.
 */

import type { MirrorPhase } from '../hooks/useMirrorStream'
import type { StreamState } from '../services/deviceStream'

export type MirrorStatus =
  | 'sin-dispositivo'
  | 'conectando'
  | 'reconectando'
  | 'disponible'
  | 'ejecutando'
  | 'pausado'
  | 'desconectado'
  | 'error'
  // ── Fases WDA (iOS) — desacopladas de "el Runner responde" ────────────────
  | 'ios-esperando-appium'
  | 'ios-iniciando-wda'
  | 'ios-construyendo-wda'
  | 'ios-arrancando-wda'
  | 'ios-verificando-wda'
  | 'ios-error-wda'

export const MIRROR_STATUS_CFG: Record<MirrorStatus, {
  label: string; color: string; pulse: boolean; bodyMessage: string
}> = {
  'sin-dispositivo': { label: 'Sin dispositivo',      color: '#64748b', pulse: false, bodyMessage: 'Selecciona un dispositivo para visualizar su pantalla.' },
  'conectando':       { label: 'Conectando',          color: '#60a5fa', pulse: true,  bodyMessage: 'Conectando al stream…' },
  'reconectando':     { label: 'Reconectando Mirror…', color: '#60a5fa', pulse: true,  bodyMessage: '' },
  'disponible':       { label: 'Conectado',           color: '#34d399', pulse: false, bodyMessage: '' },
  'ejecutando':       { label: 'Ejecución en curso',  color: '#34d399', pulse: true,  bodyMessage: '' },
  'pausado':          { label: 'Pausado',             color: '#f59e0b', pulse: false, bodyMessage: 'Mirror en pausa.' },
  'desconectado':     { label: 'Desconectado',        color: '#f87171', pulse: false, bodyMessage: 'El dispositivo o el Runner no están disponibles.' },
  'error':            { label: 'Error',               color: '#f87171', pulse: false, bodyMessage: 'No fue posible establecer el stream.' },
  'ios-esperando-appium': {
    label: 'Dispositivo conectado', color: '#60a5fa', pulse: false,
    bodyMessage: 'Esperando inicio de sesión Appium…',
  },
  'ios-iniciando-wda': {
    label: 'Iniciando WDA', color: '#f59e0b', pulse: true,
    bodyMessage: 'Iniciando WebDriverAgent…',
  },
  'ios-construyendo-wda': {
    label: 'Compilando WDA', color: '#f59e0b', pulse: true,
    bodyMessage: 'Compilando WebDriverAgent…\n\nPuede tardar varios minutos la primera vez en este dispositivo.',
  },
  'ios-arrancando-wda': {
    label: 'Arrancando WDA', color: '#f59e0b', pulse: true,
    bodyMessage: 'WebDriverAgent compilado — arrancando en el dispositivo…',
  },
  'ios-verificando-wda': {
    label: 'Verificando WDA', color: '#f59e0b', pulse: true,
    bodyMessage: 'Verificando que WebDriverAgent responda…',
  },
  'ios-error-wda': {
    label: 'Error de WDA', color: '#f87171', pulse: false,
    bodyMessage: 'No fue posible iniciar el Mirror.\n\nMotivo:\nWebDriverAgent no pudo iniciarse.',
  },
}

/** El stream (el <img> del mirror) se monta con esta única condición real. */
export const NO_STREAM_STATUSES: ReadonlySet<MirrorStatus> = new Set(['sin-dispositivo', 'desconectado', 'pausado'])
/** 'reconectando' se mantiene sin overlay para no tapar el último frame válido mientras se reabre la conexión. */
export const NO_OVERLAY_STATUSES: ReadonlySet<MirrorStatus> = new Set(['disponible', 'ejecutando', 'reconectando'])

/** Construye el mensaje de error con el motivo REAL (p.ej. "xcodebuild failed with code 65"). */
export function computeErrorBodyMessage(reason: string | null): string {
  const base = 'No fue posible iniciar el Mirror.\n\nMotivo:\nWebDriverAgent no pudo iniciarse.'
  return reason ? `${base}\n\nError:\n${reason}` : base
}

export interface ComputeMirrorStatusParams {
  hasDevice:           boolean
  streamState:         StreamState
  imgError?:           boolean
  paused?:             boolean
  hasActiveExecution?: boolean
  reconnecting?:       boolean
  mirrorPhase:         MirrorPhase | null
}

export function computeMirrorStatus(params: ComputeMirrorStatusParams): MirrorStatus {
  const {
    hasDevice, streamState, imgError = false, paused = false,
    hasActiveExecution = false, reconnecting = false, mirrorPhase,
  } = params
  if (!hasDevice) return 'sin-dispositivo'
  if (paused) return 'pausado'
  if (reconnecting) return 'reconectando'
  if (streamState === 'connecting') return 'conectando'
  if (streamState === 'error' || imgError) return 'error'
  if (streamState === 'device_disconnected' || streamState === 'runner_offline') return 'desconectado'

  // A partir de aquí el Runner responde (streamState === 'available'), pero eso
  // por sí solo no significa que WDA esté realmente produciendo frames — de ahí
  // la fase reportada por el Runner (IOSMirrorStateTracker). Para Android,
  // mirrorPhase siempre es MIRROR_ACTIVE (conectado) o DEVICE_DISCONNECTED.
  if (mirrorPhase === 'DEVICE_DISCONNECTED') return 'desconectado'
  if (mirrorPhase === 'ERROR') return 'ios-error-wda'
  if (mirrorPhase === 'INITIALIZING_WDA') return 'ios-iniciando-wda'
  if (mirrorPhase === 'BUILDING_WDA') return 'ios-construyendo-wda'
  if (mirrorPhase === 'STARTING_WDA') return 'ios-arrancando-wda'
  if (mirrorPhase === 'VERIFYING_WDA') return 'ios-verificando-wda'
  if (mirrorPhase === 'DEVICE_DETECTED') return 'ios-esperando-appium'

  if (streamState === 'available') return hasActiveExecution ? 'ejecutando' : 'disponible'
  return 'desconectado'
}
