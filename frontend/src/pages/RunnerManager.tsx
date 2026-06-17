import React, { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Server, Smartphone, RefreshCw, Play, Square, RotateCcw,
  Wifi, WifiOff, Activity, Cpu, Clock, ChevronDown, ChevronUp,
  Monitor, Apple, AlertCircle
} from 'lucide-react'
import {
  getRunners, getRunnersStatus, getRunnerDevices,
  startRunner, stopRunner, restartRunner
} from '../api'
import type { Runner, RunnerDevice, RunnerStatus } from '../types'

// ─── Status helpers ─────────────────────────────────────────────────────────

const STATUS_COLORS: Record<RunnerStatus, string> = {
  ONLINE:   '#10b981',
  BUSY:     '#f59e0b',
  STARTING: '#6366f1',
  STOPPING: '#f97316',
  OFFLINE:  '#6b7280',
}

const STATUS_LABELS: Record<RunnerStatus, string> = {
  ONLINE:   'En línea',
  BUSY:     'Ejecutando',
  STARTING: 'Iniciando',
  STOPPING: 'Deteniendo',
  OFFLINE:  'Sin conexión',
}

function statusColor(status: RunnerStatus): string {
  return STATUS_COLORS[status] ?? '#6b7280'
}

function timeAgo(iso: string | null): string {
  if (!iso) return '—'
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (diff < 5)   return 'ahora mismo'
  if (diff < 60)  return `hace ${diff}s`
  if (diff < 3600) return `hace ${Math.floor(diff / 60)}min`
  return `hace ${Math.floor(diff / 3600)}h`
}

// ─── Pulse indicator ────────────────────────────────────────────────────────

function StatusDot({ status }: { status: RunnerStatus }) {
  const color = statusColor(status)
  const pulse = status === 'ONLINE' || status === 'BUSY'
  return (
    <span className="relative flex h-2.5 w-2.5 flex-shrink-0">
      {pulse && (
        <span className="absolute inline-flex h-full w-full animate-ping rounded-full opacity-60"
          style={{ background: color }} />
      )}
      <span className="relative inline-flex h-2.5 w-2.5 rounded-full"
        style={{ background: color }} />
    </span>
  )
}

// ─── Runner card ────────────────────────────────────────────────────────────

interface RunnerCardProps {
  runner:    Runner
  onStart:   () => Promise<void>
  onStop:    () => Promise<void>
  onRestart: () => Promise<void>
}

function RunnerCard({ runner, onStart, onStop, onRestart }: RunnerCardProps) {
  const [expanded, setExpanded] = useState(false)
  const [loading, setLoading]   = useState<string | null>(null)
  const color = statusColor(runner.status)
  const isAndroid = runner.platform?.toLowerCase() === 'android'
  const PlatformIcon = isAndroid ? Monitor : Apple

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
        boxShadow: `0 0 24px ${color}12`,
      }}
    >
      {/* Header */}
      <div className="flex items-center gap-3 p-5">
        {/* Platform icon */}
        <div className="w-11 h-11 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{ background: `${color}18`, border: `1px solid ${color}30` }}>
          <PlatformIcon size={20} style={{ color }} />
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-bold truncate" style={{ color: 'var(--text-pri)' }}>
              {runner.runnerId}
            </span>
            <span className="text-[10px] font-medium px-1.5 py-0.5 rounded-full flex-shrink-0"
              style={{ background: `${color}20`, color }}>
              {runner.platform?.toUpperCase() ?? '—'}
            </span>
          </div>
          <div className="flex items-center gap-2 mt-0.5">
            <StatusDot status={runner.status} />
            <span className="text-[11px]" style={{ color: 'var(--text-dim)' }}>
              {STATUS_LABELS[runner.status]}
            </span>
            <span className="text-[11px] text-slate-600">·</span>
            <span className="text-[11px] text-slate-500">
              {timeAgo(runner.lastSeen)}
            </span>
          </div>
        </div>

        {/* Devices badge */}
        <div className="flex items-center gap-1.5 mr-2">
          <Smartphone size={12} style={{ color: 'var(--text-dim)' }} />
          <span className="text-xs font-semibold" style={{ color: 'var(--text-sec)' }}>
            {runner.devices?.length ?? 0}
          </span>
        </div>

        {/* Expand toggle */}
        <button
          onClick={() => setExpanded(v => !v)}
          className="p-1.5 rounded-lg transition-colors"
          style={{ color: 'var(--text-dim)', background: 'rgba(255,255,255,0.04)' }}>
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </button>
      </div>

      {/* Control buttons */}
      <div className="flex items-center gap-2 px-5 pb-4">
        <CtrlBtn
          icon={<Play size={12} />} label="Activar"
          accent="#10b981" loading={loading === 'start'}
          disabled={runner.status === 'ONLINE' || runner.status === 'BUSY'}
          onClick={() => act(onStart, 'start')}
        />
        <CtrlBtn
          icon={<Square size={12} />} label="Detener"
          accent="#ef4444" loading={loading === 'stop'}
          disabled={runner.status === 'OFFLINE'}
          onClick={() => act(onStop, 'stop')}
        />
        <CtrlBtn
          icon={<RotateCcw size={12} />} label="Reiniciar"
          accent="#6366f1" loading={loading === 'restart'}
          disabled={runner.status === 'OFFLINE'}
          onClick={() => act(onRestart, 'restart')}
        />
        <div className="ml-auto text-[10px] text-slate-600">
          v{runner.version ?? '—'}
        </div>
      </div>

      {/* Expanded: device list */}
      <AnimatePresence>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden"
          >
            <div className="px-5 pb-5 space-y-2"
              style={{ borderTop: '1px solid rgba(255,255,255,0.06)' }}>
              <div className="text-[10px] font-bold tracking-widest text-slate-600 uppercase pt-4 mb-3">
                Dispositivos conectados ({runner.devices?.length ?? 0})
              </div>
              {(!runner.devices || runner.devices.length === 0) && (
                <div className="text-[11px] text-slate-600 italic">Sin dispositivos detectados</div>
              )}
              {runner.devices?.map(device => (
                <DeviceRow key={device.deviceId} device={device} />
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  )
}

function CtrlBtn({
  icon, label, accent, loading, disabled, onClick
}: {
  icon: React.ReactNode; label: string; accent: string
  loading: boolean; disabled: boolean; onClick: () => void
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled || loading}
      className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold transition-all"
      style={{
        background: disabled ? 'rgba(255,255,255,0.04)' : `${accent}18`,
        color: disabled ? 'var(--text-dim)' : accent,
        border: `1px solid ${disabled ? 'rgba(255,255,255,0.06)' : accent + '30'}`,
        opacity: disabled ? 0.5 : 1,
        cursor: disabled ? 'not-allowed' : 'pointer',
      }}
    >
      {loading ? <RefreshCw size={12} className="animate-spin" /> : icon}
      {label}
    </button>
  )
}

function DeviceRow({ device }: { device: RunnerDevice }) {
  const isOnline = device.status === 'available' || device.status === 'inuse'
  const color    = device.status === 'inuse' ? '#f59e0b' : isOnline ? '#10b981' : '#6b7280'
  return (
    <div className="flex items-center gap-2.5 px-3 py-2 rounded-xl"
      style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.05)' }}>
      <Smartphone size={13} style={{ color, flexShrink: 0 }} />
      <span className="text-[11px] font-medium flex-1 truncate" style={{ color: 'var(--text-sec)' }}>
        {device.deviceName}
      </span>
      <span className="text-[10px] px-1.5 py-0.5 rounded-full font-medium"
        style={{ background: `${color}18`, color }}>
        {device.status}
      </span>
      <span className="text-[10px] text-slate-600 truncate max-w-[100px]">{device.deviceId}</span>
    </div>
  )
}

// ─── Summary stat ────────────────────────────────────────────────────────────

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
  const [runners, setRunners]     = useState<Runner[]>([])
  const [devices, setDevices]     = useState<RunnerDevice[]>([])
  const [loading, setLoading]     = useState(true)
  const [error, setError]         = useState<string | null>(null)
  const [allLoading, setAllLoading] = useState<string | null>(null)
  const [lastRefresh, setLastRefresh] = useState(Date.now())

  const refresh = useCallback(async () => {
    try {
      setError(null)
      const [runnersData, devicesData] = await Promise.all([
        getRunners(),
        getRunnerDevices(),
      ])
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

  const online  = runners.filter(r => r.status !== 'OFFLINE').length
  const busy    = runners.filter(r => r.status === 'BUSY').length
  const totalDev = devices.length

  return (
    <div className="p-7 space-y-6 max-w-5xl">
      {/* Page header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-xl font-black" style={{ color: 'var(--text-pri)' }}>
            Runner Manager
          </h1>
          <p className="text-xs text-slate-500 mt-1">
            Gestiona y monitorea los runners de automatización en tiempo real
          </p>
        </div>

        <div className="flex items-center gap-2">
          {/* Global controls */}
          <button
            onClick={() => doAll(startRunner, 'start')}
            disabled={allLoading !== null}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-[11px] font-semibold transition-all"
            style={{ background: 'rgba(16,185,129,0.15)', color: '#10b981', border: '1px solid rgba(16,185,129,0.3)' }}>
            {allLoading === 'start' ? <RefreshCw size={12} className="animate-spin" /> : <Play size={12} />}
            Activar todos
          </button>
          <button
            onClick={() => doAll(stopRunner, 'stop')}
            disabled={allLoading !== null}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-[11px] font-semibold transition-all"
            style={{ background: 'rgba(239,68,68,0.12)', color: '#ef4444', border: '1px solid rgba(239,68,68,0.25)' }}>
            {allLoading === 'stop' ? <RefreshCw size={12} className="animate-spin" /> : <Square size={12} />}
            Detener todos
          </button>
          <button
            onClick={() => doAll(restartRunner, 'restart')}
            disabled={allLoading !== null}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-[11px] font-semibold transition-all"
            style={{ background: 'rgba(99,102,241,0.12)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.25)' }}>
            {allLoading === 'restart' ? <RefreshCw size={12} className="animate-spin" /> : <RotateCcw size={12} />}
            Reiniciar todos
          </button>
          <button
            onClick={refresh}
            className="p-2 rounded-xl transition-colors"
            style={{ background: 'rgba(255,255,255,0.05)', color: 'var(--text-dim)', border: '1px solid rgba(255,255,255,0.08)' }}
            title="Refrescar">
            <RefreshCw size={14} />
          </button>
        </div>
      </div>

      {/* Stats row */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        <StatCard label="Total runners"  value={runners.length} icon={<Server size={18} />}     color="#6366f1" />
        <StatCard label="En línea"       value={online}         icon={<Wifi size={18} />}        color="#10b981" />
        <StatCard label="Ejecutando"     value={busy}           icon={<Activity size={18} />}    color="#f59e0b" />
        <StatCard label="Dispositivos"   value={totalDev}       icon={<Smartphone size={18} />}  color="#14b8a6" />
      </div>

      {/* Error */}
      {error && (
        <div className="flex items-center gap-2 p-4 rounded-2xl text-sm"
          style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)', color: '#ef4444' }}>
          <AlertCircle size={16} />
          {error}
        </div>
      )}

      {/* Loading skeleton */}
      {loading && (
        <div className="space-y-4">
          {[1, 2].map(i => (
            <div key={i} className="rounded-2xl p-5 animate-pulse"
              style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.06)', height: 120 }} />
          ))}
        </div>
      )}

      {/* Runner cards */}
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
              Inicia un Runner Agent para que aparezca aquí automáticamente
            </div>
          </div>
          <div className="text-[11px] text-slate-600 font-mono bg-slate-800/50 px-4 py-2 rounded-xl">
            java -jar runner.jar
          </div>
        </div>
      )}

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

      {/* Last refresh indicator */}
      <div className="flex items-center gap-1.5 text-[10px] text-slate-600">
        <Clock size={10} />
        Actualizado: {new Date(lastRefresh).toLocaleTimeString('es-MX')} · Refresco automático cada 15s
      </div>
    </div>
  )
}
