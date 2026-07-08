import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import {
  Smartphone, Camera, RefreshCw, Maximize2, Power, WifiOff, RotateCw, Wifi, Loader2,
} from 'lucide-react'
import { useMirrorStream } from '../../hooks/useMirrorStream'
import type { StreamState } from '../../services/deviceStream'
import type { ConfiguredDevice } from '../../hooks/useExecutionDevices'
import { executionTrackingService } from '../../services/ExecutionTrackingService'
import type { ExecutionRecord, ExecStatus } from '../../services/ExecutionTrackingService'

interface Props {
  device: ConfiguredDevice | null
}

// ── Estados visuales del Mirror (Fase 5) ─────────────────────────────────────
// Formaliza en un único lugar las 7 combinaciones visuales requeridas, derivadas
// de señales que YA existen (useMirrorStream.state, el toggle local "paused" de
// Fase 1, y el ExecutionRecord por-dispositivo de Fase 2/3) — no se agrega
// ningún mecanismo de conexión nuevo, solo se prioriza y etiqueta lo que ya hay.
type MirrorStatus =
  | 'sin-dispositivo'
  | 'conectando'
  | 'reconectando'
  | 'disponible'
  | 'ejecutando'
  | 'pausado'
  | 'desconectado'
  | 'error'

const MIRROR_STATUS_CFG: Record<MirrorStatus, {
  label: string; color: string; pulse: boolean; bodyMessage: string; showsVideo: boolean
}> = {
  'sin-dispositivo': { label: 'Sin dispositivo',      color: '#64748b', pulse: false, showsVideo: false, bodyMessage: 'Selecciona un dispositivo para visualizar su pantalla.' },
  'conectando':       { label: 'Conectando',          color: '#60a5fa', pulse: true,  showsVideo: false, bodyMessage: 'Conectando al stream…' },
  // showsVideo:true — el <img> se remonta (nueva key) e intenta reconectar de
  // inmediato; si se ocultara el video aquí, el <img> nunca llegaría a montarse
  // y su onLoad (que es lo único que limpia el estado "reconectando") jamás
  // dispararía hasta el timeout de seguridad.
  'reconectando':     { label: 'Reconectando Mirror…', color: '#60a5fa', pulse: true,  showsVideo: true,  bodyMessage: '' },
  'disponible':       { label: 'Conectado',           color: '#34d399', pulse: false, showsVideo: true,  bodyMessage: '' },
  'ejecutando':       { label: 'Ejecución en curso',  color: '#34d399', pulse: true,  showsVideo: true,  bodyMessage: '' },
  'pausado':          { label: 'Pausado',             color: '#f59e0b', pulse: false, showsVideo: false, bodyMessage: 'Mirror en pausa.' },
  'desconectado':     { label: 'Desconectado',        color: '#f87171', pulse: false, showsVideo: false, bodyMessage: 'El dispositivo o el Runner no están disponibles.' },
  'error':            { label: 'Error',               color: '#f87171', pulse: false, showsVideo: false, bodyMessage: 'No fue posible establecer el stream.' },
}

function computeMirrorStatus(params: {
  device:             ConfiguredDevice | null
  streamState:        StreamState
  imgError:           boolean
  paused:             boolean
  hasActiveExecution: boolean
  reconnecting:       boolean
}): MirrorStatus {
  const { device, streamState, imgError, paused, hasActiveExecution, reconnecting } = params
  if (!device) return 'sin-dispositivo'
  if (paused) return 'pausado'
  if (reconnecting) return 'reconectando'
  if (streamState === 'connecting') return 'conectando'
  if (streamState === 'error' || imgError) return 'error'
  if (streamState === 'device_disconnected' || streamState === 'runner_offline') return 'desconectado'
  if (streamState === 'available') return hasActiveExecution ? 'ejecutando' : 'disponible'
  return 'desconectado'
}

// Sin recibir un frame nuevo durante esta ventana, se asume que el stream MJPEG
// murió en silencio (el <img> no siempre dispara onError cuando el SO mata la
// conexión durante una suspensión) y se dispara una reconexión automática.
const STALL_THRESHOLD_MS = 6_000

const EXEC_STATUS_CFG: Partial<Record<ExecStatus, { label: string; color: string; pulse: boolean }>> = {
  queued:       { label: 'En cola',    color: '#94a3b8', pulse: false },
  initializing: { label: 'Iniciando',  color: '#f59e0b', pulse: true  },
  running:      { label: 'Ejecutando', color: '#34d399', pulse: true  },
}

function fmtElapsed(ms: number): string {
  if (ms < 0) ms = 0
  const totalSec = Math.floor(ms / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}

// Tamaño base del bisel — en retrato usa este ancho (con el aspecto real de un
// teléfono); en apaisado (rotación 90°/270°) se recalculan a partir del mismo
// valor para que el marco cambie de forma sin depender de mediciones en vivo.
const PORTRAIT_W = 240
const LANDSCAPE_W = PORTRAIT_W
const LANDSCAPE_H = Math.round(PORTRAIT_W * (9 / 19.5))

function IconButton({
  icon: Icon, onClick, title, active = false, disabled = false,
}: {
  icon:      React.ElementType
  onClick:   () => void
  title:     string
  active?:   boolean
  disabled?: boolean
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      title={title}
      className="flex items-center justify-center rounded-lg transition-colors"
      style={{
        width: 26, height: 26,
        background: active ? 'rgba(99,102,241,0.18)' : 'var(--btn-bg)',
        border: `1px solid ${active ? 'rgba(99,102,241,0.4)' : 'var(--btn-border)'}`,
        color: disabled ? '#334155' : active ? '#818cf8' : '#94a3b8',
        cursor: disabled ? 'default' : 'pointer',
        opacity: disabled ? 0.5 : 1,
      }}
    >
      <Icon size={12} />
    </button>
  )
}

export default function DeviceMirrorPanel({ device }: Props) {
  const udid = device?.udid ?? null
  const { url, state, reconnect: reconnectStream } = useMirrorStream(udid)

  const [imgError, setImgError]     = useState(false)
  const [reloadKey, setReloadKey]   = useState(0)
  const [paused, setPaused]         = useState(false)
  const [connMs, setConnMs]         = useState<number | null>(null)
  const [rotation, setRotation]     = useState<0 | 90 | 180 | 270>(0)
  const [isReconnecting, setIsReconnecting] = useState(false)

  const frameRef        = useRef<HTMLDivElement>(null)
  const imgRef          = useRef<HTMLImageElement>(null)
  const connectStartRef = useRef<number | null>(null)
  const retryTimerRef   = useRef<ReturnType<typeof setTimeout> | null>(null)

  // ── Recuperación automática tras suspensión / pérdida de visibilidad ──────
  const lastFrameAtRef          = useRef<number>(Date.now())
  const reconnectingRef         = useRef(false)
  const reconnectSafetyTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // ── Sincronización con la ejecución (Fase 2/3) ─────────────────────────────
  // No se toca useTestRunner/ExecutionTrackingService/Runner: se reutiliza el
  // mismo registro por-dispositivo (record.udid) que ya alimenta LiveExecutionPanel,
  // y el mismo patrón de eventos de window ('qa:exec:*') que ese componente ya usa.
  const [execRecord, setExecRecord] = useState<ExecutionRecord | null>(null)
  const [now, setNow]               = useState(() => Date.now())

  useEffect(() => {
    const refresh = () => {
      if (!udid) { setExecRecord(null); return }
      const active = executionTrackingService
        .getActiveExecutions()
        .find(r => r.udid === udid)
      setExecRecord(active ?? null)
    }
    refresh()
    const events = ['qa:exec:created', 'qa:exec:updated', 'qa:exec:finished']
    events.forEach(e => window.addEventListener(e, refresh))
    return () => events.forEach(e => window.removeEventListener(e, refresh))
  }, [udid])

  // Solo se ejecuta un tick de reloj mientras hay una ejecución activa que mostrar —
  // no agrega ningún polling ni consulta nueva, únicamente refresca "tiempo transcurrido".
  useEffect(() => {
    if (!execRecord) return
    const id = setInterval(() => setNow(Date.now()), 1_000)
    return () => clearInterval(id)
  }, [execRecord])

  // Reset the connection-time measurement whenever a fresh connection attempt starts.
  useEffect(() => {
    if (state === 'connecting') {
      connectStartRef.current = performance.now()
      setConnMs(null)
    } else if (state !== 'available') {
      connectStartRef.current = null
    }
  }, [state])

  useEffect(() => {
    setImgError(false)
    setPaused(false)
    setRotation(0)
    lastFrameAtRef.current = Date.now()
    reconnectingRef.current = false
    setIsReconnecting(false)
  }, [udid])

  useEffect(() => () => {
    if (retryTimerRef.current) clearTimeout(retryTimerRef.current)
    if (reconnectSafetyTimerRef.current) clearTimeout(reconnectSafetyTimerRef.current)
  }, [])

  const handleLoad = useCallback(() => {
    lastFrameAtRef.current = Date.now()
    setImgError(false)
    if (reconnectingRef.current) {
      reconnectingRef.current = false
      setIsReconnecting(false)
      if (reconnectSafetyTimerRef.current) { clearTimeout(reconnectSafetyTimerRef.current); reconnectSafetyTimerRef.current = null }
    }
    if (connectStartRef.current !== null) {
      setConnMs(Math.round(performance.now() - connectStartRef.current))
      connectStartRef.current = null
    }
  }, [])

  const handleError = useCallback(() => {
    setImgError(true)
    if (retryTimerRef.current) clearTimeout(retryTimerRef.current)
    retryTimerRef.current = setTimeout(() => {
      setReloadKey(k => k + 1)
      setImgError(false)
    }, 2_000)
  }, [])

  /** Refresh: fuerza un redibujado del frame actual sin reiniciar la medición de conexión. */
  const handleRefresh = useCallback(() => {
    setReloadKey(k => k + 1)
  }, [])

  /**
   * Reconexión completa de la sesión de streaming — la usan tanto el botón
   * manual "Reconectar" (Fase 4) como el watchdog automático de abajo.
   * reconnectingRef garantiza una sola reconexión activa a la vez (varios
   * eventos de visibilidad pueden llegar casi juntos al volver de suspensión).
   */
  const performReconnect = useCallback(() => {
    if (!udid) return
    if (reconnectingRef.current) return
    reconnectingRef.current = true
    setIsReconnecting(true)

    // Cierra la conexión anterior (retry-timer de error) y arranca una nueva
    // medición de conexión desde cero.
    if (retryTimerRef.current) { clearTimeout(retryTimerRef.current); retryTimerRef.current = null }
    setImgError(false)
    connectStartRef.current = performance.now()
    setConnMs(null)

    // Re-verifica el Runner de inmediato (no espera al siguiente tick del
    // polling interno) y desmonta/remonta el <img> para abrir una sesión
    // MJPEG nueva — el dispositivo seleccionado (udid) no cambia.
    reconnectStream()
    setReloadKey(k => k + 1)

    // Red de seguridad: si nunca llega un frame ni un error tras reconectar,
    // no dejar el guard bloqueado para siempre.
    if (reconnectSafetyTimerRef.current) clearTimeout(reconnectSafetyTimerRef.current)
    reconnectSafetyTimerRef.current = setTimeout(() => {
      reconnectingRef.current = false
      setIsReconnecting(false)
    }, 10_000)
  }, [udid, reconnectStream])

  /** Botón manual "Reconectar" (Fase 4) — reutiliza el mismo flujo de recuperación. */
  const handleReconnect = useCallback(() => {
    performReconnect()
  }, [performReconnect])

  /**
   * Se ejecuta cuando la pestaña vuelve a estar activa (visibilitychange,
   * focus, blur, pageshow — se suscriben los cuatro para cubrir las distintas
   * formas en que cada navegador/SO señala un bloqueo de pantalla o una
   * suspensión). Solo reconecta si de verdad no han llegado frames nuevos en
   * STALL_THRESHOLD_MS; de lo contrario no hace nada.
   */
  const attemptAutoRecovery = useCallback(() => {
    if (!udid || paused) return
    if (document.visibilityState === 'hidden') return
    const stale = Date.now() - lastFrameAtRef.current > STALL_THRESHOLD_MS
    if (!stale) return
    performReconnect()
  }, [udid, paused, performReconnect])

  const attemptAutoRecoveryRef = useRef(attemptAutoRecovery)
  useEffect(() => { attemptAutoRecoveryRef.current = attemptAutoRecovery }, [attemptAutoRecovery])

  useEffect(() => {
    const onActivity = () => attemptAutoRecoveryRef.current()
    document.addEventListener('visibilitychange', onActivity)
    window.addEventListener('focus',    onActivity)
    window.addEventListener('blur',     onActivity)
    window.addEventListener('pageshow', onActivity)
    return () => {
      document.removeEventListener('visibilitychange', onActivity)
      window.removeEventListener('focus',    onActivity)
      window.removeEventListener('blur',     onActivity)
      window.removeEventListener('pageshow', onActivity)
    }
  }, [])

  const handleScreenshot = useCallback(() => {
    const img = imgRef.current
    if (!img) return
    try {
      const canvas = document.createElement('canvas')
      canvas.width  = img.naturalWidth  || img.width
      canvas.height = img.naturalHeight || img.height
      const ctx = canvas.getContext('2d')
      if (!ctx) return
      ctx.drawImage(img, 0, 0)
      canvas.toBlob(blob => {
        if (!blob) return
        const link = document.createElement('a')
        link.href = URL.createObjectURL(blob)
        link.download = `mirror_${(device?.name ?? 'device').replace(/[^a-zA-Z0-9_-]/g, '_')}_${Date.now()}.png`
        link.click()
        URL.revokeObjectURL(link.href)
      }, 'image/png')
    } catch {
      // El stream MJPEG viene de otro origen (Runner en :8082) — algunos navegadores
      // marcan el canvas como "tainted" y bloquean la exportación. No es un error
      // crítico: simplemente no se genera la descarga.
    }
  }, [device])

  const handleFullscreen = useCallback(() => {
    const el = frameRef.current
    if (!el) return
    if (!document.fullscreenElement) el.requestFullscreen?.().catch(() => {})
    else document.exitFullscreen?.().catch(() => {})
  }, [])

  const handleRotate = useCallback(() => {
    setRotation(r => (((r + 90) % 360) as 0 | 90 | 180 | 270))
  }, [])

  const mirrorStatus = useMemo(() => computeMirrorStatus({
    device,
    streamState:        state,
    imgError,
    paused,
    hasActiveExecution: !!execRecord,
    reconnecting:       isReconnecting,
  }), [device, state, imgError, paused, execRecord, isReconnecting])

  const statusCfg = MIRROR_STATUS_CFG[mirrorStatus]
  const online    = statusCfg.showsVideo && !!url
  const isLandscape = rotation === 90 || rotation === 270

  // Overlay de ejecución (Fase 3) — solo datos ya existentes en ExecutionRecord,
  // nada nuevo se le pide al Runner ni al backend.
  const execCfg  = execRecord ? EXEC_STATUS_CFG[execRecord.status] : undefined
  const showOverlay = !!execRecord && !!execCfg
  const currentCaseLabel = execRecord && execRecord.totalCases > 0
    ? `Caso ${Math.min(execRecord.completedCases + 1, execRecord.totalCases)} de ${execRecord.totalCases}`
    : null
  const lastStep = execRecord?.activity.length
    ? execRecord.activity[execRecord.activity.length - 1].msg
    : null
  const elapsedLabel = execRecord?.startedAt
    ? fmtElapsed(now - new Date(execRecord.startedAt).getTime())
    : '00:00'

  return (
    <div
      className="flex flex-col h-full overflow-hidden rounded-2xl"
      style={{
        background: 'var(--panel-bg)',
        border: '1px solid var(--panel-border)',
        boxShadow: 'var(--panel-shadow)',
      }}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-5 py-4 flex-shrink-0 gap-2"
        style={{ borderBottom: '1px solid var(--panel-divide)' }}
      >
        <div className="flex items-center gap-2 min-w-0">
          <Smartphone size={14} className="text-indigo-400 flex-shrink-0" />
          <div className="min-w-0">
            <div className="text-sm font-bold text-slate-100">Mirror de Dispositivo</div>
            <div className="text-xs text-slate-500 mt-0.5 truncate">
              {device
                ? `${device.name} · ${device.platform?.toUpperCase() === 'IOS' ? 'iOS' : 'Android'}${device.platformVersion ? ' ' + device.platformVersion : ''} · USB`
                : 'Sin dispositivo configurado'}
            </div>
          </div>
        </div>
        <span
          className="flex items-center gap-1.5 px-2 py-1 rounded-lg text-[10px] font-bold flex-shrink-0"
          style={{
            color:      statusCfg.color,
            background: `${statusCfg.color}1f`,
            border:     `1px solid ${statusCfg.color}4d`,
          }}
        >
          {mirrorStatus === 'conectando' || mirrorStatus === 'reconectando' ? (
            <Loader2 size={9} className="animate-spin" />
          ) : mirrorStatus === 'desconectado' || mirrorStatus === 'error' ? (
            <WifiOff size={9} />
          ) : statusCfg.pulse ? (
            <motion.span
              className="w-1.5 h-1.5 rounded-full inline-block"
              style={{ background: statusCfg.color }}
              animate={{ opacity: [1, 0.3, 1] }}
              transition={{ duration: 1.2, repeat: Infinity }}
            />
          ) : (
            <span className="w-1.5 h-1.5 rounded-full inline-block" style={{ background: statusCfg.color }} />
          )}
          {statusCfg.label}
        </span>
      </div>

      {/* Controls (Fase 4) */}
      <div
        className="flex items-center gap-1.5 px-5 py-2 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--panel-divide)' }}
      >
        <IconButton icon={RefreshCw}  onClick={handleRefresh}     title="Refresh" disabled={!online} />
        <IconButton icon={Maximize2}  onClick={handleFullscreen}  title="Pantalla completa" disabled={!online} />
        <IconButton icon={Camera}     onClick={handleScreenshot}  title="Captura" disabled={!online} />
        <IconButton icon={Wifi}       onClick={handleReconnect}   title="Reconectar" />
        <IconButton icon={RotateCw}   onClick={handleRotate}      title="Rotar" active={rotation !== 0} disabled={!device} />
        <IconButton icon={Power}      onClick={() => setPaused(p => !p)} title={paused ? 'Reanudar' : 'Pausar mirror'} active={paused} disabled={!device} />
      </div>

      {/* Phone frame */}
      <div
        className="flex-1 min-h-0 flex flex-col items-center justify-center gap-2 py-4"
        style={{ background: 'var(--terminal-bg)' }}
      >
        {/* Overlay de ejecución — vive FUERA del bisel del teléfono, nunca sobre
            la pantalla del dispositivo; solo se renderiza mientras hay una
            ejecución activa para este dispositivo. */}
        {showOverlay && execCfg && (
          <div
            className="flex flex-col gap-1 px-3 py-2 flex-shrink-0"
            style={{
              width: '82%',
              maxWidth: PORTRAIT_W,
              borderRadius: 10,
              background: 'rgba(15,23,42,0.85)',
              border: `1px solid ${execCfg.color}33`,
              backdropFilter: 'blur(4px)',
            }}
          >
            <div className="flex items-center justify-between gap-2">
              <span className="flex items-center gap-1.5 min-w-0">
                {execCfg.pulse ? (
                  <motion.span
                    className="w-1.5 h-1.5 rounded-full flex-shrink-0"
                    style={{ background: execCfg.color }}
                    animate={{ opacity: [1, 0.3, 1] }}
                    transition={{ duration: 1.2, repeat: Infinity }}
                  />
                ) : (
                  <span className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ background: execCfg.color }} />
                )}
                <span className="text-[10px] font-bold truncate" style={{ color: execCfg.color }}>
                  {execCfg.label}
                </span>
                {execRecord?.suiteName && (
                  <span className="text-[10px] text-slate-400 truncate">· {execRecord.suiteName}</span>
                )}
              </span>
              <span className="text-[10px] text-slate-400 tabular-nums flex-shrink-0">{elapsedLabel}</span>
            </div>
            {(currentCaseLabel || lastStep) && (
              <div className="flex items-center gap-1.5 min-w-0">
                {currentCaseLabel && (
                  <span className="text-[9px] text-slate-500 flex-shrink-0">{currentCaseLabel}</span>
                )}
                {currentCaseLabel && lastStep && <span className="text-[9px] text-slate-700 flex-shrink-0">·</span>}
                {lastStep && (
                  <span className="text-[9px] text-slate-500 truncate" title={lastStep}>{lastStep}</span>
                )}
              </div>
            )}
          </div>
        )}

        <div
          ref={frameRef}
          className="relative overflow-hidden flex items-center justify-center"
          style={
            isLandscape
              ? {
                  width: LANDSCAPE_W, height: LANDSCAPE_H,
                  borderRadius: 30, border: '6px solid #1e293b',
                  background: '#05070d', boxShadow: '0 12px 40px rgba(0,0,0,0.55)',
                }
              : {
                  width: '82%', maxWidth: PORTRAIT_W, aspectRatio: '9 / 19.5',
                  borderRadius: 30, border: '6px solid #1e293b',
                  background: '#05070d', boxShadow: '0 12px 40px rgba(0,0,0,0.55)',
                }
          }
        >
          {online ? (
            <img
              key={reloadKey}
              ref={imgRef}
              src={url ?? undefined}
              onLoad={handleLoad}
              onError={handleError}
              draggable={false}
              alt="Vista en vivo del dispositivo"
              style={
                isLandscape
                  ? {
                      position: 'absolute', top: '50%', left: '50%',
                      width: LANDSCAPE_H, height: LANDSCAPE_W,
                      transform: `translate(-50%, -50%) rotate(${rotation}deg)`,
                      objectFit: 'cover',
                    }
                  : {
                      width: '100%', height: '100%', objectFit: 'cover',
                      transform: rotation === 180 ? 'rotate(180deg)' : 'none',
                    }
              }
            />
          ) : (
            <div className="flex flex-col items-center justify-center h-full gap-2.5 px-4 text-center">
              <Smartphone size={26} className="opacity-25 text-slate-500" />
              <span className="text-[10px] text-slate-600 leading-relaxed">
                {statusCfg.bodyMessage}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Footer */}
      <div
        className="flex items-center justify-between px-5 py-3 flex-shrink-0"
        style={{ borderTop: '1px solid var(--panel-divide)' }}
      >
        <div className="flex items-center gap-2">
          <span className="text-[11px] text-slate-500">Calidad</span>
          <span
            className="px-2 py-0.5 rounded text-[10px] font-bold"
            style={{ background: 'rgba(255,255,255,0.06)', color: '#94a3b8' }}
          >
            Alta
          </span>
        </div>
        <span className="text-[11px] text-slate-500 tabular-nums">
          {online && connMs != null ? `${connMs} ms` : '—'}
        </span>
      </div>
    </div>
  )
}
