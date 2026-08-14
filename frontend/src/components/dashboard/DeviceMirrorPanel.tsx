import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { motion } from 'framer-motion'
import {
  Smartphone, Camera, RefreshCw, Maximize2, Power, WifiOff, RotateCw, Wifi, Loader2,
} from 'lucide-react'
import { useMirrorStream } from '../../hooks/useMirrorStream'
import { retryMirrorLaunch } from '../../services/mirrorService'
import type { ConfiguredDevice } from '../../hooks/useExecutionDevices'
import { executionTrackingService } from '../../services/ExecutionTrackingService'
import type { ExecutionRecord, ExecStatus } from '../../services/ExecutionTrackingService'
import {
  MIRROR_STATUS_CFG, NO_STREAM_STATUSES, NO_OVERLAY_STATUSES,
  computeMirrorStatus, computeErrorBodyMessage,
} from '../../utils/mirrorStatus'

interface Props {
  device: ConfiguredDevice | null
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
  const { url, state, reconnect: reconnectStream, mirrorPhase, mirrorReason } = useMirrorStream(udid)

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
    // TEMP LOG (auditoría Mirror — remover tras validar Problema 1)
    console.log('[Mirror][TEMP] Frame received by frontend', { udid })
    console.log('[Mirror][TEMP] Image rendered', { udid })
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
    // TEMP LOG (auditoría Mirror — remover tras validar Problema 1)
    console.log('[Mirror][TEMP] Frontend <img> onError — nunca recibió frame o la conexión se cortó', { udid })
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

  /**
   * Botón manual "Reconectar" — reutiliza el flujo de recuperación de siempre,
   * pero si el último intento de WDA terminó en ERROR (estado terminal en
   * WdaLaunchService, ver Runner), primero llama a retryMirrorLaunch(): es la
   * ÚNICA acción que saca a ese UDID del estado absorbente. El watchdog
   * automático (attemptAutoRecovery, más abajo) NUNCA llama a esto — solo
   * reabre el stream — precisamente para que un fallo de WDA no se reintente
   * solo, sin acción del usuario.
   */
  const handleReconnect = useCallback(() => {
    if (udid && mirrorPhase === 'ERROR') {
      void retryMirrorLaunch(udid)
    }
    performReconnect()
  }, [udid, mirrorPhase, performReconnect])

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
    // No interrumpir una compilación/verificación de WDA legítimamente en
    // curso (puede tardar varios minutos la primera vez con un dispositivo) —
    // reconectar antes de que el Runner resuelva por sí solo (éxito o error)
    // reinicia su propia tolerancia a fallos en un bucle infinito.
    const wdaBuilding = mirrorPhase != null
      && mirrorPhase !== 'MIRROR_ACTIVE'
      && mirrorPhase !== 'DEVICE_DISCONNECTED'
      && mirrorPhase !== 'ERROR'
    if (wdaBuilding) return
    const stale = Date.now() - lastFrameAtRef.current > STALL_THRESHOLD_MS
    if (!stale) return
    performReconnect()
  }, [udid, paused, mirrorPhase, performReconnect])

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

  // Vigía periódico — independiente de los eventos de arriba. Los eventos de
  // visibilidad/foco solo cubren "la pestaña estuvo oculta/perdió el foco y
  // volvió" — si el usuario deja la pestaña visible y en foco todo el tiempo
  // (el caso típico al monitorear una ejecución real en curso), un stream que
  // se cuelga por contención con WDA (la sesión real de Appium/XCTest tiene
  // prioridad sobre las capturas del Mirror — ver IOSMirrorProvider) nunca
  // dispara ninguno de esos eventos, así que attemptAutoRecovery() jamás se
  // llama y el <img> queda colgado indefinidamente mostrando su alt text en
  // vez del video (el bug reportado: "Vista en vivo del dispositivo" fijo en
  // pantalla). Mismo umbral (STALL_THRESHOLD_MS) y misma función de
  // recuperación — solo se agrega una segunda vía, más confiable, para
  // llamarla.
  useEffect(() => {
    const id = setInterval(() => attemptAutoRecoveryRef.current(), STALL_THRESHOLD_MS)
    return () => clearInterval(id)
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
    hasDevice:          !!device,
    streamState:        state,
    imgError,
    paused,
    hasActiveExecution: !!execRecord,
    reconnecting:       isReconnecting,
    mirrorPhase,
  }), [device, state, imgError, paused, execRecord, isReconnecting, mirrorPhase])

  const statusCfg = MIRROR_STATUS_CFG[mirrorStatus]
  const bodyMessage = mirrorStatus === 'ios-error-wda'
    ? computeErrorBodyMessage(mirrorReason)
    : statusCfg.bodyMessage

  // El stream se monta con la única condición real: UDID válido + Runner
  // alcanzable (ambos ya encapsulados en `url`, ver useMirrorStream.ts:110) y
  // sin pausa manual. Nunca depende de mirrorPhase — ver el comentario junto
  // a NO_STREAM_STATUSES arriba.
  const streamMounted = !!url && !NO_STREAM_STATUSES.has(mirrorStatus)
  const hasOverlay    = !NO_OVERLAY_STATUSES.has(mirrorStatus)
  // "Realmente activo" (habilita acciones que necesitan un frame real: refresh,
  // pantalla completa, captura) — distinto de "el stream existe".
  const online       = streamMounted && !hasOverlay
  const isLandscape = rotation === 90 || rotation === 270

  // TEMP LOG (auditoría Mirror/WDA — remover tras validar Problema 2)
  const prevHasOverlayRef = useRef(hasOverlay)
  useEffect(() => {
    if (prevHasOverlayRef.current && !hasOverlay) {
      console.log('[DeviceMirrorPanel][TEMP] Overlay removed', { udid, mirrorStatus })
    }
    prevHasOverlayRef.current = hasOverlay
  }, [hasOverlay, udid, mirrorStatus])

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
          {mirrorStatus === 'conectando' || mirrorStatus === 'reconectando' || mirrorStatus === 'ios-iniciando-wda'
              || mirrorStatus === 'ios-construyendo-wda' || mirrorStatus === 'ios-arrancando-wda'
              || mirrorStatus === 'ios-verificando-wda' ? (
            <Loader2 size={9} className="animate-spin" />
          ) : mirrorStatus === 'desconectado' || mirrorStatus === 'error' || mirrorStatus === 'ios-error-wda' ? (
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
          {streamMounted && (
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
          )}
          {hasOverlay && (
            // Encima del stream (montado o no) — nunca decide si existe la
            // conexión, solo qué mensaje mostrar sobre ella.
            <div
              className="absolute inset-0 flex flex-col items-center justify-center h-full gap-2.5 px-4 text-center"
              style={{ background: '#05070d' }}
            >
              <Smartphone size={26} className="opacity-25 text-slate-500" />
              <span
                className="text-[10px] text-slate-600 leading-relaxed"
                style={{ whiteSpace: 'pre-line' }}
              >
                {bodyMessage}
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
