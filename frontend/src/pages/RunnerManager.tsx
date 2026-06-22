import React, { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Server, Smartphone, RefreshCw, Play, Square, RotateCcw,
  Wifi, WifiOff, Activity, Clock, ChevronDown, ChevronUp,
  Monitor, Apple, AlertCircle, CheckCircle2, XCircle,
  Cpu, Globe,
} from 'lucide-react'
import {
  getRunners, getRunnerDevices,
  startRunner, stopRunner, restartRunner,
  getRunnerDiagnostics,
} from '../api'
import type { RunnerDiagnostics } from '../api'
import type { Runner, RunnerDevice, RunnerStatus } from '../types'

// ─── Status helpers ─────────────────────────────────────────────────────────

const STATUS_COLORS: Record<RunnerStatus, string> = {
  ONLINE:   '#10b981',
  BUSY:     '#f59e0b',
  STARTING: '#6366f1',
  STOPPING: '#f97316',
  OFFLINE:  '#6b7280',
  DEGRADED: '#f59e0b',
}

const STATUS_LABELS: Record<RunnerStatus, string> = {
  ONLINE:   'En línea',
  BUSY:     'Ejecutando',
  STARTING: 'Iniciando',
  STOPPING: 'Deteniendo',
  OFFLINE:  'Sin conexión',
  DEGRADED: 'Degraded',
}

function statusColor(s: RunnerStatus) { return STATUS_COLORS[s] ?? '#6b7280' }

function timeAgo(iso: string | null) {
  if (!iso) return '—'
  const d = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (d < 5)    return 'ahora mismo'
  if (d < 60)   return `hace ${d}s`
  if (d < 3600) return `hace ${Math.floor(d / 60)}min`
  return `hace ${Math.floor(d / 3600)}h`
}

// ─── OS helpers ─────────────────────────────────────────────────────────────

function resolveOs(runner: Runner): 'WINDOWS' | 'MACOS' | 'LINUX' {
  if (runner.os === 'MACOS' || runner.os === 'WINDOWS' || runner.os === 'LINUX')
    return runner.os
  const id = runner.runnerId?.toLowerCase() ?? ''
  if (id.startsWith('mac')) return 'MACOS'
  if (id.startsWith('linux')) return 'LINUX'
  return 'WINDOWS'
}

function OsIcon({ os, size = 18 }: { os: string; size?: number }) {
  if (os === 'MACOS') return <Apple size={size} />
  return <Monitor size={size} />
}

function osLabel(os: string, hostname?: string) {
  const base = os === 'MACOS' ? 'macOS' : os === 'LINUX' ? 'Linux' : 'Windows'
  return hostname ? `${base} · ${hostname}` : base
}

// ─── Pulse dot ───────────────────────────────────────────────────────────────

function StatusDot({ status }: { status: RunnerStatus }) {
  const color = statusColor(status)
  const pulse = status === 'ONLINE' || status === 'BUSY' || status === 'DEGRADED'
  return (
    <span className="relative flex h-2.5 w-2.5 flex-shrink-0">
      {pulse && <span className="absolute inline-flex h-full w-full animate-ping rounded-full opacity-60" style={{ background: color }} />}
      <span className="relative inline-flex h-2.5 w-2.5 rounded-full" style={{ background: color }} />
    </span>
  )
}

// ─── Capability chip ────────────────────────────────────────────────────────

function CapChip({ supported, label }: { supported: boolean; label: string }) {
  const color  = supported ? '#10b981' : '#6b7280'
  const Icon   = supported ? CheckCircle2 : XCircle
  return (
    <div className="flex items-center gap-1 px-2 py-1 rounded-lg text-[10px] font-semibold"
      style={{ background: `${color}14`, color, border: `1px solid ${color}25` }}>
      <Icon size={11} />
      {label}
    </div>
  )
}

// ─── Component badge (v4 telemetry) ──────────────────────────────────────────

function CompBadge({ ok, label, version }: { ok?: boolean; label: string; version?: string }) {
  if (ok === undefined) return null
  const color = ok ? '#10b981' : '#ef4444'
  const Icon  = ok ? CheckCircle2 : XCircle
  return (
    <div className="flex items-center gap-1 px-2 py-1 rounded-lg text-[10px] font-semibold"
      style={{ background: `${color}12`, color, border: `1px solid ${color}20` }}>
      <Icon size={10} />
      {label}
      {version && ok && version !== 'unavailable' && version !== 'N/A' && (
        <span className="text-[9px] opacity-60 ml-0.5">{version}</span>
      )}
    </div>
  )
}

// ─── Host diagnostics panel ──────────────────────────────────────────────────

function DiagRow({ label, ok, detail }: { label: string; ok: boolean; detail?: string | null }) {
  const color = ok ? '#10b981' : '#ef4444'
  const Icon  = ok ? CheckCircle2 : XCircle
  return (
    <div className="flex items-center gap-2 py-1.5 border-b border-white/5 last:border-0">
      <Icon size={12} style={{ color, flexShrink: 0 }} />
      <span className="text-[11px] font-semibold w-16 text-slate-400">{label}</span>
      {detail && detail !== 'unavailable' && detail !== 'N/A' && (
        <span className="text-[10px] font-mono text-slate-500 truncate">{detail}</span>
      )}
      {!ok && !detail && (
        <span className="text-[10px] text-red-500/70 italic">no disponible</span>
      )}
    </div>
  )
}

function HostDiagnosticsPanel({ diag, loading }: { diag: RunnerDiagnostics | null; loading: boolean }) {
  if (loading) {
    return (
      <div className="flex items-center gap-2 py-3 text-[11px] text-slate-500">
        <RefreshCw size={12} className="animate-spin" />
        Cargando diagnósticos...
      </div>
    )
  }
  if (!diag) return null

  const { components, adb } = diag

  return (
    <div className="pt-3 space-y-3">
      <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase">
        Diagnósticos del host
      </div>

      {/* Components */}
      <div className="rounded-xl overflow-hidden"
        style={{ background: 'rgba(255,255,255,0.025)', border: '1px solid rgba(255,255,255,0.06)' }}>
        <div className="px-3 py-2 text-[9px] font-black tracking-widest uppercase text-slate-600 border-b border-white/5">
          Runtimes
        </div>
        <div className="px-3 py-1">
          <DiagRow label="JRE 17"  ok={components.jre.installed}    detail={components.jre.version} />
          <DiagRow label="Node.js" ok={components.node.installed}   detail={components.node.version} />
          <DiagRow label="Appium"  ok={components.appium.installed} detail={components.appium.version} />
          {components.xcode.installed !== undefined && (
            <DiagRow label="Xcode" ok={components.xcode.installed}  detail={components.xcode.version} />
          )}
        </div>
      </div>

      {/* ADB */}
      <div className="rounded-xl overflow-hidden"
        style={{ background: 'rgba(255,255,255,0.025)', border: '1px solid rgba(255,255,255,0.06)' }}>
        <div className="px-3 py-2 text-[9px] font-black tracking-widest uppercase text-slate-600 border-b border-white/5">
          Android Debug Bridge
        </div>
        <div className="px-3 py-1">
          <DiagRow label="ADB"    ok={adb.ok}      detail={adb.version} />
          <DiagRow label="Tools"  ok={adb.platformToolsInstalled} detail={adb.path ?? undefined} />
          <div className="flex items-center gap-2 py-1.5">
            <Activity size={12} style={{ color: '#6366f1', flexShrink: 0 }} />
            <span className="text-[11px] font-semibold w-16 text-slate-400">Disp.</span>
            <span className="text-[10px] font-mono text-slate-500">{adb.devicesFound ?? 0} detectado(s)</span>
          </div>
        </div>
      </div>
    </div>
  )
}

// ─── Runner card ─────────────────────────────────────────────────────────────

interface RunnerCardProps {
  runner: Runner
  onStart:   () => Promise<void>
  onStop:    () => Promise<void>
  onRestart: () => Promise<void>
}

function RunnerCard({ runner, onStart, onStop, onRestart }: RunnerCardProps) {
  const [expanded,    setExpanded]    = useState(false)
  const [loading,     setLoading]     = useState<string | null>(null)
  const [diag,        setDiag]        = useState<RunnerDiagnostics | null>(null)
  const [diagLoading, setDiagLoading] = useState(false)

  const color = statusColor(runner.status)
  const os    = resolveOs(runner)

  useEffect(() => {
    if (!expanded) return
    setDiagLoading(true)
    getRunnerDiagnostics(runner.runnerId)
      .then(setDiag)
      .catch(() => setDiag(null))
      .finally(() => setDiagLoading(false))
  }, [expanded, runner.runnerId])

  const androidOk = runner.androidSupported ?? true
  const iosOk     = runner.iosSupported ?? (runner.platform === 'ios')

  const hasComponentTelemetry = runner.jreInstalled !== undefined
    || runner.nodeInstalled !== undefined
    || runner.appiumInstalled !== undefined
    || runner.xcodeInstalled !== undefined

  async function act(fn: () => Promise<void>, label: string) {
    setLoading(label)
    try { await fn() } catch (e) { console.error(e) }
    finally { setLoading(null) }
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, y: -8 }}
      className="rounded-2xl overflow-hidden"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
        border: `1px solid ${color}28`,
        backdropFilter: 'blur(12px)',
        boxShadow: `0 0 24px ${color}10`,
      }}>

      {/* ── Header ──────────────────────────────────────────── */}
      <div className="flex items-start gap-3 p-5 pb-3">

        {/* OS icon */}
        <div className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 mt-0.5"
          style={{ background: `${color}16`, border: `1px solid ${color}30` }}>
          <span style={{ color }}>
            <OsIcon os={os} size={22} />
          </span>
        </div>

        {/* Identity */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm font-black truncate" style={{ color: 'var(--text-pri)' }}>
              {runner.runnerId}
            </span>
            <span className="text-[9px] font-black px-1.5 py-0.5 rounded-full flex-shrink-0"
              style={{ background: `${color}20`, color, border: `1px solid ${color}35` }}>
              {STATUS_LABELS[runner.status] ?? runner.status}
            </span>
          </div>

          {/* OS + hostname */}
          <div className="text-[11px] text-slate-500 mt-0.5 font-medium">
            {osLabel(os, runner.hostname)}
          </div>

          {/* Last seen + version */}
          <div className="flex items-center gap-2 mt-1">
            <StatusDot status={runner.status} />
            <span className="text-[11px] text-slate-500">{timeAgo(runner.lastSeen)}</span>
            <span className="text-slate-700">·</span>
            <span className="text-[10px] text-slate-600 font-mono">v{runner.version ?? '—'}</span>
          </div>
        </div>

        {/* Device count */}
        <div className="flex items-center gap-1.5 flex-shrink-0">
          <Smartphone size={12} style={{ color: 'var(--text-dim)' }} />
          <span className="text-xs font-bold" style={{ color: 'var(--text-sec)' }}>
            {runner.devices?.length ?? 0}
          </span>
        </div>

        {/* Expand */}
        <button onClick={() => setExpanded(v => !v)}
          className="p-1.5 rounded-lg flex-shrink-0"
          style={{ color: 'var(--text-dim)', background: 'rgba(255,255,255,0.04)' }}>
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>
      </div>

      {/* ── Capability chips ──────────────────────────────── */}
      <div className="flex items-center gap-2 px-5 pb-3">
        <span className="text-[10px] text-slate-600 font-semibold mr-1">Capacidades:</span>
        <CapChip supported={androidOk} label="Android" />
        <CapChip supported={iosOk}     label="iOS" />
        {!androidOk && !iosOk && (
          <span className="text-[10px] text-slate-600 italic">Sin capacidades detectadas</span>
        )}
      </div>

      {/* ── Component health (v4 telemetry) ──────────────── */}
      {hasComponentTelemetry && (
        <div className="flex items-center gap-2 px-5 pb-3 flex-wrap">
          <span className="text-[10px] text-slate-600 font-semibold mr-1">Componentes:</span>
          <CompBadge ok={runner.jreInstalled}    label="JRE"    version={runner.jreVersion} />
          <CompBadge ok={runner.nodeInstalled}   label="Node"   version={runner.nodeVersion} />
          <CompBadge ok={runner.appiumInstalled} label="Appium" version={runner.appiumVersion} />
          {os === 'MACOS' && (
            <CompBadge ok={runner.xcodeInstalled} label="Xcode" version={runner.xcodeVersion} />
          )}
        </div>
      )}

      {/* ── Control buttons ────────────────────────────────── */}
      <div className="flex items-center gap-2 px-5 pb-4"
        style={{ borderBottom: expanded ? '1px solid rgba(255,255,255,0.06)' : 'none' }}>
        <CtrlBtn icon={<Play size={12} />}    label="Activar"  accent="#10b981"
          loading={loading === 'start'}
          disabled={runner.status === 'ONLINE' || runner.status === 'BUSY'}
          onClick={() => act(onStart, 'start')} />
        <CtrlBtn icon={<Square size={12} />}  label="Detener"  accent="#ef4444"
          loading={loading === 'stop'}
          disabled={runner.status === 'OFFLINE'}
          onClick={() => act(onStop, 'stop')} />
        <CtrlBtn icon={<RotateCcw size={12} />} label="Reiniciar" accent="#6366f1"
          loading={loading === 'restart'}
          disabled={runner.status === 'OFFLINE'}
          onClick={() => act(onRestart, 'restart')} />
      </div>

      {/* ── Expanded device list ────────────────────────────── */}
      <AnimatePresence>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden">
            <div className="px-5 py-4 space-y-2">
              <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase mb-3">
                Dispositivos ({runner.devices?.length ?? 0})
              </div>
              {(!runner.devices || runner.devices.length === 0) && (
                <div className="text-[11px] text-slate-600 italic">Sin dispositivos detectados</div>
              )}
              {runner.devices?.map(d => <DeviceRow key={d.deviceId} device={d} />)}

              <HostDiagnosticsPanel diag={diag} loading={diagLoading} />
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  )
}

function CtrlBtn({ icon, label, accent, loading, disabled, onClick }: {
  icon: React.ReactNode; label: string; accent: string
  loading: boolean; disabled: boolean; onClick: () => void
}) {
  return (
    <button onClick={onClick} disabled={disabled || loading}
      className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold transition-all"
      style={{
        background: disabled ? 'rgba(255,255,255,0.04)' : `${accent}18`,
        color:      disabled ? 'var(--text-dim)' : accent,
        border:     `1px solid ${disabled ? 'rgba(255,255,255,0.06)' : accent + '30'}`,
        opacity:    disabled ? 0.5 : 1,
        cursor:     disabled ? 'not-allowed' : 'pointer',
      }}>
      {loading ? <RefreshCw size={12} className="animate-spin" /> : icon}
      {label}
    </button>
  )
}

function DeviceRow({ device }: { device: RunnerDevice }) {
  const isInUse   = device.status === 'inuse' || device.status === 'BUSY'
  const isOnline  = device.status === 'available' || device.status === 'AVAILABLE' || isInUse
  const color     = isInUse ? '#f59e0b' : isOnline ? '#10b981' : '#6b7280'
  const isIos     = device.platform?.toUpperCase() === 'IOS'
  return (
    <div className="flex items-center gap-2.5 px-3 py-2 rounded-xl"
      style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.05)' }}>
      {isIos ? <Apple size={13} style={{ color, flexShrink: 0 }} />
             : <Monitor size={13} style={{ color, flexShrink: 0 }} />}
      <span className="text-[11px] font-medium flex-1 truncate" style={{ color: 'var(--text-sec)' }}>
        {device.deviceName}
      </span>
      <span className="text-[9px] font-semibold px-1.5 py-0.5 rounded-full"
        style={{ background: `${color}18`, color }}>
        {device.platform?.toUpperCase() ?? '?'}
      </span>
      <span className="text-[10px] px-1.5 py-0.5 rounded-full"
        style={{ background: `${color}12`, color }}>
        {isInUse ? 'EN USO' : isOnline ? 'OK' : 'offline'}
      </span>
    </div>
  )
}

// ─── Stat card ───────────────────────────────────────────────────────────────

function StatCard({ label, value, icon, color }: {
  label: string; value: number | string; icon: React.ReactNode; color: string
}) {
  return (
    <div className="rounded-2xl p-4 flex items-center gap-3"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
        border: '1px solid rgba(255,255,255,0.07)',
        backdropFilter: 'blur(12px)',
      }}>
      <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
        style={{ background: `${color}18`, border: `1px solid ${color}30` }}>
        <span style={{ color }}>{icon}</span>
      </div>
      <div>
        <div className="text-xl font-black" style={{ color: 'var(--text-pri)' }}>{value}</div>
        <div className="text-[11px] text-slate-500">{label}</div>
      </div>
    </div>
  )
}

// ─── Main page ───────────────────────────────────────────────────────────────

export default function RunnerManager() {
  const [runners,     setRunners]     = useState<Runner[]>([])
  const [devices,     setDevices]     = useState<RunnerDevice[]>([])
  const [loading,     setLoading]     = useState(true)
  const [error,       setError]       = useState<string | null>(null)
  const [allLoading,  setAllLoading]  = useState<string | null>(null)
  const [lastRefresh, setLastRefresh] = useState(Date.now())

  const refresh = useCallback(async () => {
    try {
      setError(null)
      const [runnersData, devicesData] = await Promise.all([getRunners(), getRunnerDevices()])
      setRunners(runnersData)
      setDevices(devicesData)
      setLastRefresh(Date.now())
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Error al cargar runners')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
    const id = setInterval(refresh, 15_000)
    return () => clearInterval(id)
  }, [refresh])

  async function doAll(fn: (id?: string) => Promise<void>, label: string) {
    setAllLoading(label)
    try { await fn() } catch (e) { console.error(e) }
    finally { setAllLoading(null); setTimeout(refresh, 2000) }
  }

  const online      = runners.filter(r => r.status !== 'OFFLINE').length
  const busy        = runners.filter(r => r.status === 'BUSY').length
  const withAndroid = runners.filter(r => r.androidSupported !== false).length
  const withIos     = runners.filter(r => r.iosSupported === true).length

  return (
    <div className="p-7 space-y-6 max-w-5xl">

      {/* ── Header ─────────────────────────────────────────── */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-black" style={{ color: 'var(--text-pri)' }}>
            Universal Runner Manager
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Runners multiplataforma — Android y iOS descubiertos automáticamente según el OS
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => doAll(startRunner, 'start')} disabled={allLoading !== null}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-[11px] font-semibold"
            style={{ background: 'rgba(16,185,129,0.15)', color: '#10b981', border: '1px solid rgba(16,185,129,0.3)' }}>
            {allLoading === 'start' ? <RefreshCw size={12} className="animate-spin" /> : <Play size={12} />}
            Activar todos
          </button>
          <button onClick={() => doAll(stopRunner, 'stop')} disabled={allLoading !== null}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-[11px] font-semibold"
            style={{ background: 'rgba(239,68,68,0.12)', color: '#ef4444', border: '1px solid rgba(239,68,68,0.25)' }}>
            {allLoading === 'stop' ? <RefreshCw size={12} className="animate-spin" /> : <Square size={12} />}
            Detener todos
          </button>
          <button onClick={() => doAll(restartRunner, 'restart')} disabled={allLoading !== null}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-[11px] font-semibold"
            style={{ background: 'rgba(99,102,241,0.12)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.25)' }}>
            {allLoading === 'restart' ? <RefreshCw size={12} className="animate-spin" /> : <RotateCcw size={12} />}
            Reiniciar todos
          </button>
          <button onClick={refresh}
            className="p-2 rounded-xl"
            style={{ background: 'rgba(255,255,255,0.05)', color: 'var(--text-dim)', border: '1px solid rgba(255,255,255,0.08)' }}>
            <RefreshCw size={14} />
          </button>
        </div>
      </div>

      {/* ── Stats row ──────────────────────────────────────── */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <StatCard label="Total runners"   value={runners.length} icon={<Server size={18} />}       color="#6366f1" />
        <StatCard label="En línea"        value={online}         icon={<Wifi size={18} />}          color="#10b981" />
        <StatCard label="Con Android"     value={withAndroid}    icon={<Monitor size={18} />}       color="#10b981" />
        <StatCard label="Con iOS"         value={withIos}        icon={<Apple size={18} />}         color="#818cf8" />
      </div>

      {/* ── Architecture hint ──────────────────────────────── */}
      <div className="rounded-2xl p-4"
        style={{ background: 'rgba(99,102,241,0.06)', border: '1px solid rgba(99,102,241,0.15)' }}>
        <div className="flex items-start gap-3">
          <Globe size={16} className="text-indigo-400 flex-shrink-0 mt-0.5" />
          <div className="text-[11px] text-slate-400 leading-relaxed">
            <span className="font-semibold text-indigo-300">Universal Runner</span>
            {' '}— un único JAR para todas las plataformas.
            En <span className="font-semibold text-slate-300">Windows</span>: detecta Android (ADB).
            En <span className="font-semibold text-slate-300">macOS</span>: detecta Android (ADB) + iOS (Xcode).
            No se requiere configuración manual de plataforma.
          </div>
        </div>
      </div>

      {/* ── Error ──────────────────────────────────────────── */}
      {error && (
        <div className="flex items-center gap-2 p-4 rounded-2xl text-sm"
          style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)', color: '#ef4444' }}>
          <AlertCircle size={16} /> {error}
        </div>
      )}

      {/* ── Loading skeleton ───────────────────────────────── */}
      {loading && (
        <div className="space-y-4">
          {[1, 2].map(i => (
            <div key={i} className="rounded-2xl p-5 animate-pulse"
              style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.06)', height: 130 }} />
          ))}
        </div>
      )}

      {/* ── Empty state ────────────────────────────────────── */}
      {!loading && runners.length === 0 && !error && (
        <div className="flex flex-col items-center justify-center py-20 gap-4">
          <div className="w-16 h-16 rounded-2xl flex items-center justify-center"
            style={{ background: 'rgba(99,102,241,0.1)', border: '1px solid rgba(99,102,241,0.2)' }}>
            <Server size={28} className="text-indigo-400" />
          </div>
          <div className="text-center">
            <div className="text-sm font-semibold" style={{ color: 'var(--text-sec)' }}>
              Sin runners registrados
            </div>
            <div className="text-[11px] text-slate-500 mt-1">
              Inicia el Universal Runner Agent para que aparezca aquí automáticamente
            </div>
          </div>
          <div className="space-y-1 text-center">
            <div className="text-[10px] text-slate-600 font-semibold uppercase tracking-wide">Windows</div>
            <div className="text-[11px] text-slate-500 font-mono bg-slate-800/50 px-4 py-2 rounded-xl">
              runner\start-runner.bat
            </div>
            <div className="text-[10px] text-slate-600 font-semibold uppercase tracking-wide mt-2">macOS / Linux</div>
            <div className="text-[11px] text-slate-500 font-mono bg-slate-800/50 px-4 py-2 rounded-xl">
              bash runner/start-runner.sh
            </div>
          </div>
        </div>
      )}

      {/* ── Runner cards ───────────────────────────────────── */}
      {!loading && runners.length > 0 && (
        <div className="space-y-4">
          <div className="text-[10px] font-bold tracking-widest text-slate-600 uppercase">
            Runners activos ({runners.length})
          </div>
          <AnimatePresence>
            {runners.map(runner => (
              <RunnerCard
                key={runner.runnerId}
                runner={runner}
                onStart={() => startRunner(runner.runnerId).then(() => { setTimeout(refresh, 2000) })}
                onStop={() => stopRunner(runner.runnerId).then(() => { setTimeout(refresh, 2000) })}
                onRestart={() => restartRunner(runner.runnerId).then(() => { setTimeout(refresh, 2000) })}
              />
            ))}
          </AnimatePresence>
        </div>
      )}

      {/* ── Footer ────────────────────────────────────────── */}
      <div className="flex items-center gap-1.5 text-[10px] text-slate-600">
        <Clock size={10} />
        Actualizado: {new Date(lastRefresh).toLocaleTimeString('es-MX')} · Refresco automático cada 15s
      </div>
    </div>
  )
}
