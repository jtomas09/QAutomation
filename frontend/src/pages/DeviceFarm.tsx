import React, { useState, useEffect, useCallback, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ResponsiveContainer, PieChart, Pie, Cell, Tooltip } from 'recharts'
import {
  Smartphone, RefreshCw, Search, Monitor, Apple, Clock, CheckCircle2,
  Server, ChevronLeft, ChevronRight, ChevronDown, MoreHorizontal,
  HardDrive, BatteryMedium, BatteryLow, BatteryFull, Download,
  ShieldAlert, Loader2, Package, CheckCircle, XCircle, RotateCcw,
  Play, TrendingUp, BarChart3, Flag, Wifi, WifiOff, Activity,
  AlertCircle, Zap,
} from 'lucide-react'
import { getDevices, getRunners, getExecutions, updateDeviceStatus, removeDevice } from '../api'
import type { PhysicalDevice, DeviceStatus, Runner, ExecutionSummary } from '../types'
import { detectOs, type OsType } from '../hooks/useOs'
import { OsAvatar, PlatformBadge } from '../components/PlatformIcon'

// ─── Types ───────────────────────────────────────────────────────────────────

interface ActivityEvent {
  id:       string
  time:     string
  type:     'host_connected' | 'host_disconnected' | 'device_connected' | 'execution_started' | 'execution_completed'
  title:    string
  subtitle: string
}

type DownloadPhase  = 'idle' | 'preparing' | 'done' | 'error'
type InstallerType  = 'proper' | 'temp' | 'unavailable'
type InfraState     = 'loading' | 'not_installed' | 'offline' | 'scanning' | 'ready'

interface PlatformPackage {
  available: boolean
  type:      InstallerType
  filename:  string
  label:     string
}

// ─── Constants ────────────────────────────────────────────────────────────────

const BACKEND_BASE = (import.meta.env.VITE_BACKEND_URL as string | undefined) ?? 'https://qautomation-production.up.railway.app'

const STATUS_CFG: Record<DeviceStatus, { label: string; color: string; bg: string }> = {
  AVAILABLE:   { label: 'Disponible',       color: '#10b981', bg: 'rgba(16,185,129,0.15)' },
  BUSY:        { label: 'En uso',           color: '#3b82f6', bg: 'rgba(59,130,246,0.15)' },
  OFFLINE:     { label: 'Offline',          color: '#6b7280', bg: 'rgba(107,114,128,0.15)' },
  MAINTENANCE: { label: 'En mantenimiento', color: '#f59e0b', bg: 'rgba(245,158,11,0.15)' },
}

const DONUT_COLORS = ['#10b981', '#3b82f6', '#f59e0b', '#ef4444']

const UNAVAILABLE_PKG: PlatformPackage = { available: false, type: 'unavailable', filename: '', label: 'No disponible' }

// ─── Helpers ──────────────────────────────────────────────────────────────────

function timeAgo(iso: string | null | undefined): string {
  if (!iso) return '—'
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (diff < 5)    return 'ahora'
  if (diff < 60)   return `hace ${diff} seg`
  if (diff < 3600) return `hace ${Math.floor(diff / 60)} min`
  return `hace ${Math.floor(diff / 3600)} horas`
}

function fmtClock(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function fakeBattery(udid: string): number {
  let h = 0
  for (let i = 0; i < udid.length; i++) h = (h * 31 + udid.charCodeAt(i)) | 0
  return 50 + (Math.abs(h) % 48)
}

function fakeRunnerStats(runnerId: string): { cpu: number; mem: number } {
  let h = 0
  for (let i = 0; i < runnerId.length; i++) h = (h * 31 + runnerId.charCodeAt(i)) | 0
  return { cpu: 8 + (Math.abs(h) % 20), mem: 25 + (Math.abs(h >> 4) % 25) }
}

function resolveOs(runner: Runner): 'WINDOWS' | 'MACOS' | 'LINUX' {
  if (runner.os === 'MACOS' || runner.os === 'WINDOWS' || runner.os === 'LINUX') return runner.os
  const id = runner.runnerId?.toLowerCase() ?? ''
  if (id.includes('mac'))   return 'MACOS'
  if (id.includes('linux')) return 'LINUX'
  return 'WINDOWS'
}

function osDisplayLabel(os: string): string {
  if (os === 'MACOS') return 'macOS'
  if (os === 'LINUX') return 'Linux'
  return 'Windows'
}

function osVersionLabel(os: string): string {
  if (os === 'MACOS') return 'macOS 14.4'
  if (os === 'LINUX') return 'Ubuntu 22.04'
  return 'Windows 11'
}

function osArchLabel(os: string): string {
  if (os === 'MACOS') return 'Apple Silicon'
  return '64-bit'
}

function generateActivityEvents(
  devices: PhysicalDevice[],
  runners: Runner[],
  executions: ExecutionSummary[],
): ActivityEvent[] {
  const events: ActivityEvent[] = []

  runners.forEach((r, i) => {
    if (!r.lastSeen) return
    if (r.status !== 'OFFLINE') {
      events.push({
        id: `hc-${i}`, time: r.lastSeen, type: 'host_connected',
        title: 'Host conectado',
        subtitle: `${r.hostname ?? r.runnerId} · ${osDisplayLabel(resolveOs(r))}`,
      })
    } else {
      events.push({
        id: `hd-${i}`, time: r.lastSeen, type: 'host_disconnected',
        title: 'Host desconectado',
        subtitle: `${r.hostname ?? r.runnerId} perdió conexión`,
      })
    }
  })

  devices.forEach((d, i) => {
    if (!d.lastSeen) return
    events.push({
      id: `dc-${i}`, time: d.lastSeen, type: 'device_connected',
      title: 'Dispositivo conectado',
      subtitle: `${d.deviceName ?? d.udid} · ${d.runnerId ?? '—'}`,
    })
  })

  executions.forEach((ex, i) => {
    if (ex.status === 'RUNNING' && ex.startTime) {
      events.push({
        id: `es-${i}`, time: ex.startTime, type: 'execution_started',
        title: 'Ejecución iniciada',
        subtitle: `${ex.suite} · ${ex.device}`,
      })
    } else if ((ex.status === 'PASSED' || ex.status === 'FAILED') && ex.endTime) {
      events.push({
        id: `ec-${i}`, time: ex.endTime, type: 'execution_completed',
        title: `Ejecución ${ex.status === 'PASSED' ? 'completada' : 'fallida'}`,
        subtitle: `${ex.suite} · ${ex.passed}P ${ex.failed}F`,
      })
    }
  })

  return events
    .sort((a, b) => new Date(b.time).getTime() - new Date(a.time).getTime())
    .slice(0, 10)
}

// ─── Download helpers ─────────────────────────────────────────────────────────

async function fetchAvailability(): Promise<Record<string, PlatformPackage>> {
  try {
    const res = await fetch(`${BACKEND_BASE}/api/runner/download/availability`)
    if (!res.ok) return {}
    return await res.json() as Record<string, PlatformPackage>
  } catch {
    return {}
  }
}

async function downloadRunnerPackage(
  platform: string,
  pkg:      PlatformPackage,
  onPhase:  (p: DownloadPhase, msg?: string) => void,
) {
  const url      = `${BACKEND_BASE}/api/runner/download/${platform}`
  const filename = pkg.filename || `AutomationQA-Runner-${platform}`
  onPhase('preparing')
  try {
    const res = await fetch(url, { method: 'GET' })
    if (!res.ok) {
      let msg = 'No hay una version disponible del Runner para descargar.'
      try {
        const body = await res.json() as { message?: string }
        if (body.message) msg = body.message
      } catch { /* ignore */ }
      onPhase('error', msg)
      return
    }
    const contentType = res.headers.get('content-type') ?? ''
    if (contentType.includes('application/json')) {
      const body = await res.json() as { message?: string }
      onPhase('error', body.message ?? 'No hay version disponible.')
      return
    }
    const blob    = await res.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a       = document.createElement('a')
    a.href        = blobUrl
    a.download    = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(blobUrl), 5_000)
    onPhase('done')
  } catch {
    onPhase('error', 'No se pudo conectar al servidor. Verifica tu conexion e intenta de nuevo.')
  }
}

// ─── StatusBadge ──────────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: DeviceStatus }) {
  const cfg   = STATUS_CFG[status] ?? STATUS_CFG.OFFLINE
  const pulse = status === 'AVAILABLE' || status === 'BUSY'
  return (
    <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-black tracking-wide"
      style={{ background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.color}30` }}>
      <span className="relative flex h-1.5 w-1.5 flex-shrink-0">
        {pulse && <span className="absolute inline-flex h-full w-full animate-ping rounded-full opacity-50" style={{ background: cfg.color }} />}
        <span className="relative inline-flex h-1.5 w-1.5 rounded-full" style={{ background: cfg.color }} />
      </span>
      {cfg.label}
    </span>
  )
}

// ─── BatteryChip ──────────────────────────────────────────────────────────────

function BatteryChip({ pct }: { pct: number }) {
  const color = pct > 60 ? '#10b981' : pct > 30 ? '#f59e0b' : '#ef4444'
  const Icon  = pct > 70 ? BatteryFull : pct > 40 ? BatteryMedium : BatteryLow
  return (
    <div className="flex items-center gap-1">
      <Icon size={13} style={{ color }} />
      <span className="text-[11px] font-semibold" style={{ color }}>{pct}%</span>
    </div>
  )
}

// ─── DownloadModal ────────────────────────────────────────────────────────────

interface DownloadModalProps {
  infraState: InfraState
  runners:    Runner[]
  onClose:    () => void
}

function DownloadModal({ infraState, runners, onClose }: DownloadModalProps) {
  const os                 = detectOs()
  const defaultTab: OsType = os === 'macos' ? 'macos' : 'windows'
  const [tab, setTab]      = useState<OsType>(defaultTab)
  const [phase, setPhase]  = useState<DownloadPhase>('idle')
  const [errorMsg, setErr] = useState('')
  const [avail, setAvail]  = useState<Record<string, PlatformPackage>>({})
  const [loadingAvail, setLoadingAvail] = useState(true)

  useEffect(() => {
    setLoadingAvail(true)
    fetchAvailability().then(data => {
      setAvail(data)
      setLoadingAvail(false)
    })
  }, [])

  const offlineLastSeen = runners.reduce((latest, r) => {
    if (!r.lastSeen) return latest
    if (!latest || new Date(r.lastSeen) > new Date(latest)) return r.lastSeen
    return latest
  }, null as string | null)

  function handlePhase(p: DownloadPhase, msg?: string) {
    setPhase(p)
    if (msg) setErr(msg)
  }

  const platformKey         = tab === 'macos' ? 'macos' : 'windows'
  const currentPkg: PlatformPackage = avail[platformKey] ?? UNAVAILABLE_PKG

  function handleDownload() {
    downloadRunnerPackage(platformKey, currentPkg, handlePhase)
  }

  function handleRetry() {
    setPhase('idle')
    setErr('')
  }

  const osTabs: { id: OsType; label: string; icon: React.ReactNode }[] = [
    { id: 'windows', label: 'Windows', icon: <Monitor size={13} /> },
    { id: 'macos',   label: 'macOS',   icon: <Apple   size={13} /> },
  ]

  const afterInstall = [
    { icon: <CheckCircle size={14} className="text-emerald-400 flex-shrink-0" />, text: 'Se inicia automaticamente con tu equipo' },
    { icon: <CheckCircle size={14} className="text-emerald-400 flex-shrink-0" />, text: 'Detecta dispositivos Android conectados por USB' },
    { icon: <CheckCircle size={14} className="text-emerald-400 flex-shrink-0" />, text: tab === 'macos' ? 'Detecta iPhone y iPad en macOS' : 'Detecta iPhone y iPad (requiere macOS)' },
    { icon: <CheckCircle size={14} className="text-emerald-400 flex-shrink-0" />, text: 'Se conecta al Dashboard automaticamente' },
    { icon: <CheckCircle size={14} className="text-emerald-400 flex-shrink-0" />, text: 'No requiere CMD, Terminal ni intervencion manual' },
  ]

  const usbSteps = [
    { icon: '①', text: 'Conecta el dispositivo por cable USB al equipo donde instalaste el Runner.' },
    { icon: '②', text: 'En el telefono o tablet, acepta el mensaje "Confiar en este equipo".' },
    { icon: '③', text: 'El dispositivo aparecera automaticamente en el Dashboard en segundos.' },
  ]

  return (
    <motion.div
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(10px)' }}
      onClick={onClose}>
      <motion.div
        initial={{ scale: 0.95, opacity: 0, y: 16 }}
        animate={{ scale: 1,    opacity: 1, y: 0  }}
        exit={{   scale: 0.95, opacity: 0, y: 8  }}
        transition={{ type: 'spring', stiffness: 300, damping: 28 }}
        className="rounded-3xl overflow-hidden w-full max-w-lg"
        style={{ background: '#0e1120', border: '1px solid rgba(255,255,255,0.09)', boxShadow: '0 40px 100px rgba(0,0,0,0.9)' }}
        onClick={e => e.stopPropagation()}>

        {/* Header */}
        <div className="px-7 pt-7 pb-5" style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
          <div className="flex items-start justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="w-12 h-12 rounded-2xl flex items-center justify-center flex-shrink-0"
                style={{
                  background: infraState === 'not_installed' ? 'rgba(99,102,241,0.15)' : infraState === 'offline' ? 'rgba(239,68,68,0.12)' : 'rgba(245,158,11,0.12)',
                  border:     infraState === 'not_installed' ? '1px solid rgba(99,102,241,0.3)' : infraState === 'offline' ? '1px solid rgba(239,68,68,0.25)' : '1px solid rgba(245,158,11,0.25)',
                }}>
                {infraState === 'not_installed' && <Package     size={22} className="text-indigo-400" />}
                {infraState === 'offline'        && <ShieldAlert size={22} className="text-red-400"    />}
                {(infraState === 'scanning' || infraState === 'ready') && <Wifi size={22} className="text-amber-400" />}
              </div>
              <div>
                <h2 className="text-[16px] font-black leading-tight" style={{ color: 'var(--text-pri)' }}>
                  {infraState === 'not_installed' && 'Descargar Automation QA Runner'}
                  {infraState === 'offline'        && 'Runner sin conexion'}
                  {(infraState === 'scanning' || infraState === 'ready') && 'Registrar Host'}
                </h2>
                <p className="text-[11px] mt-0.5" style={{ color: 'var(--text-dim)' }}>
                  {infraState === 'not_installed' && 'Instalacion unica · Auto-inicio · Sin intervencion manual'}
                  {infraState === 'offline'        && (offlineLastSeen ? `Ultimo contacto: ${timeAgo(offlineLastSeen)}` : 'El servicio se detuvo temporalmente')}
                  {(infraState === 'scanning' || infraState === 'ready') && 'Descarga e instala el Runner en el nuevo equipo'}
                </p>
              </div>
            </div>
            <button onClick={onClose} className="p-2 rounded-xl hover:bg-white/5 flex-shrink-0"
              style={{ color: 'var(--text-dim)' }}>
              <XCircle size={16} />
            </button>
          </div>
        </div>

        {/* Body */}
        <div className="px-7 py-6 space-y-5 max-h-[72vh] overflow-y-auto">

          {/* OS selector */}
          <div className="flex gap-1 p-1 rounded-xl w-fit" style={{ background: 'rgba(255,255,255,0.05)' }}>
            {osTabs.map(t => (
              <button key={t.id}
                disabled={phase === 'preparing'}
                onClick={() => { setTab(t.id); setPhase('idle'); setErr('') }}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-[12px] font-semibold transition-all disabled:opacity-40"
                style={{
                  background: tab === t.id ? 'rgba(99,102,241,0.25)' : 'transparent',
                  color:      tab === t.id ? '#818cf8' : 'var(--text-dim)',
                  border:     tab === t.id ? '1px solid rgba(99,102,241,0.35)' : '1px solid transparent',
                }}>
                {t.icon}
                {t.label}
                {os === t.id && (
                  <span className="text-[8px] font-black px-1.5 py-0.5 rounded-full"
                    style={{ background: 'rgba(16,185,129,0.2)', color: '#10b981' }}>
                    TU OS
                  </span>
                )}
              </button>
            ))}
          </div>

          {/* Notice when type is temp */}
          {!loadingAvail && currentPkg.type === 'temp' && phase !== 'done' && (
            <div className="flex items-start gap-3 rounded-2xl p-4"
              style={{ background: 'rgba(245,158,11,0.07)', border: '1px solid rgba(245,158,11,0.2)' }}>
              <ShieldAlert size={15} className="text-amber-400 flex-shrink-0 mt-0.5" />
              <div>
                <div className="text-[11px] font-black text-amber-400 mb-0.5">Instalador provisional</div>
                <div className="text-[11px] text-slate-400 leading-relaxed">
                  El instalador incluye todos los componentes necesarios.
                  La version definitiva con asistente grafico estara disponible proximamente.
                </div>
              </div>
            </div>
          )}

          {/* Loading availability */}
          {loadingAvail && (
            <div className="flex items-center gap-3 px-5 py-4 rounded-2xl"
              style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' }}>
              <Loader2 size={16} className="text-slate-600 animate-spin flex-shrink-0" />
              <span className="text-[12px] text-slate-500">Verificando disponibilidad...</span>
            </div>
          )}

          {/* idle + preparing */}
          {!loadingAvail && (phase === 'idle' || phase === 'preparing') && (
            <>
              {currentPkg.available ? (
                <div className="rounded-2xl p-5 flex items-center gap-4"
                  style={{ background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.2)' }}>
                  <div className="w-14 h-14 rounded-2xl flex items-center justify-center flex-shrink-0"
                    style={{ background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.3)' }}>
                    {tab === 'macos' ? <Apple size={28} className="text-indigo-300" /> : <Monitor size={28} className="text-indigo-300" />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-0.5">
                      <span className="text-[13px] font-black" style={{ color: 'var(--text-pri)' }}>
                        {currentPkg.label}
                      </span>
                      {currentPkg.type === 'temp' && (
                        <span className="text-[8px] font-black px-1.5 py-0.5 rounded-full flex-shrink-0"
                          style={{ background: 'rgba(245,158,11,0.2)', color: '#f59e0b', border: '1px solid rgba(245,158,11,0.3)' }}>
                          PROVISIONAL
                        </span>
                      )}
                    </div>
                    <div className="text-[11px] text-slate-500 truncate">
                      {phase === 'preparing' ? 'Preparando descarga...' : 'Incluye todos los componentes necesarios'}
                    </div>
                  </div>
                  <button
                    onClick={handleDownload}
                    disabled={phase === 'preparing'}
                    className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-[12px] font-black transition-all flex-shrink-0 disabled:opacity-60 disabled:cursor-not-allowed"
                    style={{ background: 'linear-gradient(135deg, #6366f1, #7c3aed)', color: '#fff', boxShadow: phase === 'preparing' ? 'none' : '0 6px 18px rgba(99,102,241,0.4)' }}>
                    {phase === 'preparing' ? <Loader2 size={13} className="animate-spin" /> : <Download size={13} />}
                    {phase === 'preparing' ? 'Preparando...' : 'Descargar'}
                  </button>
                </div>
              ) : (
                <div className="rounded-2xl p-5 flex items-center gap-4"
                  style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' }}>
                  <div className="w-14 h-14 rounded-2xl flex items-center justify-center flex-shrink-0"
                    style={{ background: 'rgba(107,114,128,0.12)', border: '1px solid rgba(107,114,128,0.2)' }}>
                    {tab === 'macos' ? <Apple size={28} className="text-slate-600" /> : <Monitor size={28} className="text-slate-600" />}
                  </div>
                  <div className="flex-1">
                    <div className="text-[13px] font-bold text-slate-500">Proximamente disponible</div>
                    <div className="text-[11px] text-slate-600 mt-0.5">
                      El instalador para {tab === 'macos' ? 'macOS' : 'Windows'} estara disponible pronto.
                    </div>
                  </div>
                  <span className="text-[10px] font-black px-2 py-1 rounded-full flex-shrink-0"
                    style={{ background: 'rgba(107,114,128,0.15)', color: '#6b7280', border: '1px solid rgba(107,114,128,0.25)' }}>
                    PROXIMAMENTE
                  </span>
                </div>
              )}
            </>
          )}

          {/* done */}
          {phase === 'done' && (
            <div className="rounded-2xl p-5" style={{ background: 'rgba(16,185,129,0.07)', border: '1px solid rgba(16,185,129,0.2)' }}>
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
                  style={{ background: 'rgba(16,185,129,0.15)', border: '1px solid rgba(16,185,129,0.3)' }}>
                  <CheckCircle size={20} className="text-emerald-400" />
                </div>
                <div>
                  <div className="text-[13px] font-black text-emerald-400">Descarga completada</div>
                  <div className="text-[11px] text-slate-500">Abre el instalador para continuar</div>
                </div>
              </div>
              <div className="space-y-2">
                <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase mb-2">Como instalar</div>
                {tab === 'windows' ? (
                  <>
                    <div className="flex items-start gap-2 text-[11px] text-slate-400">
                      <span className="text-slate-600 flex-shrink-0 font-bold mt-px">①</span>
                      Abre el archivo descargado desde tu carpeta de Descargas
                    </div>
                    <div className="flex items-start gap-2 text-[11px] text-slate-400">
                      <span className="text-slate-600 flex-shrink-0 font-bold mt-px">②</span>
                      Si Windows muestra un aviso de seguridad, haz clic en <strong className="text-slate-300">"Ejecutar de todas formas"</strong>
                    </div>
                    <div className="flex items-start gap-2 text-[11px] text-slate-400">
                      <span className="text-slate-600 flex-shrink-0 font-bold mt-px">③</span>
                      El Runner se conectara automaticamente — aparecera en el Dashboard en ~15 segundos
                    </div>
                  </>
                ) : (
                  <>
                    <div className="flex items-start gap-2 text-[11px] text-slate-400">
                      <span className="text-slate-600 flex-shrink-0 font-bold mt-px">①</span>
                      Abre el archivo descargado desde tu carpeta de Descargas
                    </div>
                    <div className="flex items-start gap-2 text-[11px] text-slate-400">
                      <span className="text-slate-600 flex-shrink-0 font-bold mt-px">②</span>
                      Si macOS solicita autorizacion, ingresala para completar la instalacion
                    </div>
                    <div className="flex items-start gap-2 text-[11px] text-slate-400">
                      <span className="text-slate-600 flex-shrink-0 font-bold mt-px">③</span>
                      El Runner se conectara automaticamente — aparecera en el Dashboard en ~15 segundos
                    </div>
                  </>
                )}
              </div>
            </div>
          )}

          {/* error */}
          {phase === 'error' && (
            <div className="rounded-2xl p-5" style={{ background: 'rgba(239,68,68,0.07)', border: '1px solid rgba(239,68,68,0.2)' }}>
              <div className="flex items-start gap-3 mb-4">
                <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
                  style={{ background: 'rgba(239,68,68,0.12)', border: '1px solid rgba(239,68,68,0.25)' }}>
                  <ShieldAlert size={18} className="text-red-400" />
                </div>
                <div>
                  <div className="text-[13px] font-black text-red-400">No disponible</div>
                  <div className="text-[12px] text-slate-400 mt-0.5 leading-relaxed">
                    {errorMsg || 'No hay una version disponible del Runner para descargar.'}
                  </div>
                </div>
              </div>
              <button onClick={handleRetry}
                className="flex items-center gap-2 px-4 py-2 rounded-xl text-[11px] font-semibold"
                style={{ background: 'rgba(239,68,68,0.1)', color: '#ef4444', border: '1px solid rgba(239,68,68,0.25)' }}>
                <RotateCcw size={12} />
                Reintentar
              </button>
            </div>
          )}

          {/* After install checklist */}
          <div className="rounded-2xl p-5" style={{ background: 'rgba(255,255,255,0.025)', border: '1px solid rgba(255,255,255,0.06)' }}>
            <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase mb-3">
              Despues de instalar, el Runner:
            </div>
            <div className="space-y-2.5">
              {afterInstall.map((item, i) => (
                <div key={i} className="flex items-center gap-2.5">
                  {item.icon}
                  <span className="text-[12px] text-slate-300">{item.text}</span>
                </div>
              ))}
            </div>
          </div>

          {/* USB steps */}
          <div className="space-y-3">
            <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase">Como conectar un dispositivo</div>
            {usbSteps.map((s, i) => (
              <div key={i} className="flex items-start gap-3 px-4 py-3 rounded-xl"
                style={{ background: 'rgba(255,255,255,0.025)', border: '1px solid rgba(255,255,255,0.06)' }}>
                <span className="text-[18px] leading-none flex-shrink-0 text-slate-600">{s.icon}</span>
                <span className="text-[12px] text-slate-300 leading-relaxed">{s.text}</span>
              </div>
            ))}
          </div>

          {/* Runners list when offline */}
          {infraState === 'offline' && runners.length > 0 && (
            <div>
              <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase mb-2">Equipos registrados</div>
              <div className="space-y-2">
                {runners.map(r => (
                  <div key={r.runnerId} className="flex items-center gap-3 px-4 py-2.5 rounded-xl"
                    style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' }}>
                    <span className="w-2 h-2 rounded-full flex-shrink-0"
                      style={{ background: r.status === 'OFFLINE' ? '#6b7280' : r.status === 'DEGRADED' ? '#f59e0b' : '#10b981' }} />
                    <div className="flex-1 min-w-0">
                      <div className="text-[11px] font-semibold truncate" style={{ color: 'var(--text-sec)' }}>
                        {r.hostname ?? r.runnerId}
                      </div>
                      <div className="text-[10px] text-slate-600">{osDisplayLabel(resolveOs(r))}</div>
                    </div>
                    <span className="text-[10px] text-slate-600 flex-shrink-0">{timeAgo(r.lastSeen)}</span>
                    <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full flex-shrink-0"
                      style={{
                        background: r.status === 'OFFLINE' ? 'rgba(107,114,128,0.18)' : r.status === 'DEGRADED' ? 'rgba(245,158,11,0.18)' : 'rgba(16,185,129,0.18)',
                        color:      r.status === 'OFFLINE' ? '#9ca3af'               : r.status === 'DEGRADED' ? '#f59e0b'               : '#10b981',
                      }}>
                      {r.status === 'OFFLINE' ? 'Offline' : r.status === 'DEGRADED' ? 'Degraded' : 'Online'}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="px-7 pb-7 pt-2">
          <button onClick={onClose}
            className="w-full py-2.5 rounded-xl text-[12px] font-semibold transition-all hover:bg-white/5"
            style={{ color: 'var(--text-dim)', border: '1px solid rgba(255,255,255,0.07)' }}>
            Cerrar
          </button>
        </div>
      </motion.div>
    </motion.div>
  )
}

// ─── Activity icon map ────────────────────────────────────────────────────────

const ACTIVITY_CFG: Record<ActivityEvent['type'], { icon: React.ReactNode; bg: string }> = {
  host_connected:      { icon: <CheckCircle2 size={13} className="text-emerald-400" />,  bg: 'rgba(16,185,129,0.15)'  },
  host_disconnected:   { icon: <XCircle      size={13} className="text-red-400"     />,  bg: 'rgba(239,68,68,0.15)'   },
  device_connected:    { icon: <Smartphone   size={13} className="text-emerald-400" />,  bg: 'rgba(16,185,129,0.15)'  },
  execution_started:   { icon: <Play         size={13} className="text-blue-400"    />,  bg: 'rgba(59,130,246,0.15)'  },
  execution_completed: { icon: <Flag         size={13} className="text-yellow-400"  />,  bg: 'rgba(234,179,8,0.15)'   },
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface Props {
  onNavigate?: (page: string) => void
  initialOpenDownload?: boolean
}

// ─── Main Component ───────────────────────────────────────────────────────────

export default function DeviceFarm({ onNavigate, initialOpenDownload = false }: Props) {
  const [devices,     setDevices]     = useState<PhysicalDevice[]>([])
  const [runners,     setRunners]     = useState<Runner[]>([])
  const [executions,  setExecutions]  = useState<ExecutionSummary[]>([])
  const [loading,     setLoading]     = useState(true)
  const [error,       setError]       = useState<string | null>(null)
  const [lastRefresh, setLastRefresh] = useState(Date.now())
  const [showModal,   setShowModal]   = useState(initialOpenDownload)

  // Hosts table state
  const [hostPage, setHostPage] = useState(1)
  const HOST_ROWS = 5

  // Devices table state
  const [devicePage,   setDevicePage]   = useState(1)
  const [hostFilter,   setHostFilter]   = useState('ALL')
  const [deviceMenuId, setDeviceMenuId] = useState<string | null>(null)
  const DEVICE_ROWS = 5

  const refresh = useCallback(async () => {
    try {
      setError(null)
      const [devs, runs, execs] = await Promise.all([getDevices(), getRunners(), getExecutions()])
      setDevices(devs)
      setRunners(runs)
      setExecutions(execs)
      setLastRefresh(Date.now())
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Error de conexion')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
    const id = setInterval(refresh, 30_000)
    return () => clearInterval(id)
  }, [refresh])

  // ── Infrastructure state ─────────────────────────────────────────────────

  const infraState: InfraState = loading ? 'loading'
    : runners.length === 0                                                       ? 'not_installed'
    : runners.every(r => r.status === 'OFFLINE')                                 ? 'offline'
    : runners.every(r => r.status === 'OFFLINE' || r.status === 'DEGRADED')
      && devices.length === 0                                                     ? 'scanning'
    : devices.length === 0                                                        ? 'scanning'
    : 'ready'

  // ── KPI metrics ──────────────────────────────────────────────────────────

  const hostsOnline    = runners.filter(r => r.status === 'ONLINE' || r.status === 'BUSY').length
  const hostsDegraded  = runners.filter(r => r.status === 'DEGRADED').length
  const hostsOffline   = runners.filter(r => r.status === 'OFFLINE').length
  const totalRunners = runners.length

  const devAvailable   = devices.filter(d => d.status === 'AVAILABLE').length
  const devBusy        = devices.filter(d => d.status === 'BUSY').length
  const devMaintenance = devices.filter(d => d.status === 'MAINTENANCE').length
  const devOffline     = devices.filter(d => d.status === 'OFFLINE').length
  const totalDevices   = devices.length

  const devInUsePct    = totalDevices === 0 ? 0 : Math.round((devBusy / totalDevices) * 100)

  const activeExecs = executions.filter(e => e.status === 'RUNNING').length

  const todayExecs = useMemo(() => {
    const today = new Date().toDateString()
    return executions.filter(e => new Date(e.startTime).toDateString() === today).length
  }, [executions])

  const yesterdayExecs = useMemo(() => {
    const yest = new Date(Date.now() - 86_400_000).toDateString()
    return executions.filter(e => new Date(e.startTime).toDateString() === yest).length
  }, [executions])

  const todayVsYestPct = yesterdayExecs === 0
    ? (todayExecs > 0 ? 100 : 0)
    : Math.round(((todayExecs - yesterdayExecs) / yesterdayExecs) * 100)

  // ── Host table ───────────────────────────────────────────────────────────

  const totalHostPages = Math.max(1, Math.ceil(runners.length / HOST_ROWS))
  const safeHostPage   = Math.min(hostPage, totalHostPages)
  const paginatedHosts = runners.slice((safeHostPage - 1) * HOST_ROWS, safeHostPage * HOST_ROWS)

  // ── Devices table ────────────────────────────────────────────────────────

  const hostFilterOptions = useMemo(() => {
    const opts = [{ id: 'ALL', label: 'Todos los hosts' }]
    runners.forEach(r => {
      opts.push({ id: r.runnerId, label: r.hostname ?? r.runnerId })
    })
    return opts
  }, [runners])

  const filteredDevices = useMemo(() => {
    if (hostFilter === 'ALL') return devices
    return devices.filter(d => d.runnerId === hostFilter)
  }, [devices, hostFilter])

  const totalDevicePages = Math.max(1, Math.ceil(filteredDevices.length / DEVICE_ROWS))
  const safeDevicePage   = Math.min(devicePage, totalDevicePages)
  const paginatedDevices = filteredDevices.slice((safeDevicePage - 1) * DEVICE_ROWS, safeDevicePage * DEVICE_ROWS)

  // ── Activity events ──────────────────────────────────────────────────────

  const activityEvents = useMemo(
    () => generateActivityEvents(devices, runners, executions),
    [devices, runners, executions],
  )

  // ── Donut chart data ─────────────────────────────────────────────────────

  const donutData = useMemo(() => [
    { name: 'Disponibles',      value: devAvailable   || 0 },
    { name: 'En uso',           value: devBusy         || 0 },
    { name: 'En mantenimiento', value: devMaintenance  || 0 },
    { name: 'Offline',          value: devOffline      || 0 },
  ].filter(d => d.value > 0), [devAvailable, devBusy, devMaintenance, devOffline])

  const donutFallback = donutData.length === 0 ? [{ name: 'Sin datos', value: 1 }] : donutData
  const donutColors   = donutData.length === 0 ? ['rgba(255,255,255,0.08)'] : DONUT_COLORS

  // ── Actions ──────────────────────────────────────────────────────────────

  async function handleStatusChange(udid: string, status: DeviceStatus) {
    await updateDeviceStatus(udid, status)
    setDevices(prev => prev.map(d => d.udid === udid ? { ...d, status } : d))
    setDeviceMenuId(null)
  }

  async function handleRemoveDevice(udid: string) {
    if (!confirm('Eliminar este dispositivo del pool?')) return
    await removeDevice(udid)
    setDevices(prev => prev.filter(d => d.udid !== udid))
    setDeviceMenuId(null)
  }

  // ─── Render ───────────────────────────────────────────────────────────────

  return (
    <div className="flex flex-col h-full overflow-y-auto p-6 gap-5" style={{ background: 'var(--bg-main)' }}>

      {/* Error banner */}
      {error && (
        <div className="flex items-center gap-2 p-3 rounded-2xl text-sm"
          style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)', color: '#ef4444' }}>
          <AlertCircle size={14} />
          {error}
          <button onClick={refresh} className="ml-auto flex items-center gap-1 text-[11px] font-semibold underline">
            <RefreshCw size={11} /> Reintentar
          </button>
        </div>
      )}

      {/* ── KPI Row ─────────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-6 gap-4">

        {/* 1. Hosts Online */}
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0 }}
          className="rounded-2xl p-4 flex items-center gap-3"
          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
          <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ background: 'rgba(16,185,129,0.15)', border: '1px solid rgba(16,185,129,0.25)' }}>
            <Monitor size={18} style={{ color: '#10b981' }} />
          </div>
          <div className="min-w-0">
            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">Hosts Online</div>
            <div className="text-xl font-black leading-none" style={{ color: 'var(--text-pri)' }}>{hostsOnline}</div>
            <div className="text-[10px] mt-0.5" style={{ color: '#10b981' }}>de {totalRunners} registrados</div>
          </div>
        </motion.div>

        {/* 2. Hosts Degraded */}
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.04 }}
          className="rounded-2xl p-4 flex items-center gap-3"
          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
          <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ background: 'rgba(245,158,11,0.12)', border: '1px solid rgba(245,158,11,0.25)' }}>
            <Monitor size={18} style={{ color: '#f59e0b' }} />
          </div>
          <div className="min-w-0">
            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">Hosts Degraded</div>
            <div className="text-xl font-black leading-none" style={{ color: 'var(--text-pri)' }}>{hostsDegraded}</div>
            <div className="text-[10px] mt-0.5" style={{ color: '#f59e0b' }}>ADB / componentes faltantes</div>
          </div>
        </motion.div>

        {/* 3. Hosts Offline */}
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.06 }}
          className="rounded-2xl p-4 flex items-center gap-3"
          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
          <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ background: 'rgba(239,68,68,0.12)', border: '1px solid rgba(239,68,68,0.22)' }}>
            <Monitor size={18} style={{ color: '#ef4444' }} />
          </div>
          <div className="min-w-0">
            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">Hosts Offline</div>
            <div className="text-xl font-black leading-none" style={{ color: 'var(--text-pri)' }}>{hostsOffline}</div>
            <div className="text-[10px] mt-0.5" style={{ color: '#ef4444' }}>de {totalRunners} registrados</div>
          </div>
        </motion.div>

        {/* 3. Dispositivos Disponibles */}
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.08 }}
          className="rounded-2xl p-4 flex items-center gap-3"
          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
          <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ background: 'rgba(16,185,129,0.15)', border: '1px solid rgba(16,185,129,0.25)' }}>
            <Smartphone size={18} style={{ color: '#10b981' }} />
          </div>
          <div className="min-w-0">
            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">Dispositivos Disponibles</div>
            <div className="text-xl font-black leading-none" style={{ color: 'var(--text-pri)' }}>{devAvailable}</div>
            <div className="text-[10px] mt-0.5" style={{ color: '#10b981' }}>de {totalDevices} en total</div>
          </div>
        </motion.div>

        {/* 4. Dispositivos En Uso */}
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.12 }}
          className="rounded-2xl p-4 flex items-center gap-3"
          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
          <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ background: 'rgba(245,158,11,0.12)', border: '1px solid rgba(245,158,11,0.22)' }}>
            <Smartphone size={18} style={{ color: '#f59e0b' }} />
          </div>
          <div className="min-w-0">
            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">Dispositivos En Uso</div>
            <div className="text-xl font-black leading-none" style={{ color: 'var(--text-pri)' }}>{devBusy}</div>
            <div className="text-[10px] mt-0.5" style={{ color: '#f59e0b' }}>{devInUsePct}% en uso</div>
          </div>
        </motion.div>

        {/* 5. Ejecuciones Activas */}
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.16 }}
          className="rounded-2xl p-4 flex items-center gap-3"
          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
          <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ background: 'rgba(59,130,246,0.12)', border: '1px solid rgba(59,130,246,0.22)' }}>
            <Play size={18} style={{ color: '#3b82f6' }} />
          </div>
          <div className="min-w-0">
            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">Ejecuciones Activas</div>
            <div className="text-xl font-black leading-none" style={{ color: 'var(--text-pri)' }}>{activeExecs}</div>
            <div className="text-[10px] mt-0.5" style={{ color: '#3b82f6' }}>en este momento</div>
          </div>
        </motion.div>

        {/* 6. Ejecuciones Hoy */}
        <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}
          className="rounded-2xl p-4 flex items-center gap-3"
          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
          <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ background: 'rgba(139,92,246,0.12)', border: '1px solid rgba(139,92,246,0.22)' }}>
            <TrendingUp size={18} style={{ color: '#8b5cf6' }} />
          </div>
          <div className="min-w-0">
            <div className="text-[10px] font-semibold text-slate-500 mb-0.5">Ejecuciones Hoy</div>
            <div className="text-xl font-black leading-none" style={{ color: 'var(--text-pri)' }}>{todayExecs}</div>
            <div className="text-[10px] mt-0.5" style={{ color: '#8b5cf6' }}>
              {todayVsYestPct >= 0 ? '+' : ''}{todayVsYestPct}% vs ayer
            </div>
          </div>
        </motion.div>
      </div>

      {/* ── Main content ─────────────────────────────────────────────────────── */}
      <div className="flex gap-5 min-h-0 flex-1">

        {/* ── Left column ─────────────────────────────────────────────────── */}
        <div className="flex flex-col gap-5 flex-1 min-w-0">

          {/* ── Hosts table ─────────────────────────────────────────────── */}
          <div className="rounded-2xl overflow-hidden"
            style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>

            {/* Header */}
            <div className="flex items-center justify-between px-5 py-3.5"
              style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
              <div className="flex items-center gap-2">
                <span className="text-[13px] font-bold" style={{ color: 'var(--text-pri)' }}>
                  Hosts (Maquinas)
                </span>
                <span className="text-[10px] font-black px-2 py-0.5 rounded-full"
                  style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.25)' }}>
                  {runners.length}
                </span>
              </div>
              <button
                onClick={() => onNavigate?.('runners')}
                className="text-[11px] font-semibold hover:underline"
                style={{ color: '#818cf8' }}>
                Ver todos los hosts
              </button>
            </div>

            {/* Table */}
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
                    {['HOST', 'SISTEMA OPERATIVO', 'ESTADO', 'ADB', 'DISPOSITIVOS', 'CPU', 'MEMORIA', 'ULTIMO CONTACTO'].map(h => (
                      <th key={h} className="px-4 py-2.5 text-left text-[9px] font-black tracking-widest whitespace-nowrap"
                        style={{ color: 'var(--text-dim)' }}>
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {loading && runners.length === 0 && Array.from({ length: 3 }, (_, i) => (
                    <tr key={i} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                      {Array.from({ length: 8 }, (__, j) => (
                        <td key={j} className="px-4 py-3">
                          <div className="h-4 rounded animate-pulse" style={{ background: 'rgba(255,255,255,0.06)', width: j === 0 ? 140 : 70 }} />
                        </td>
                      ))}
                    </tr>
                  ))}
                  {paginatedHosts.map(runner => {
                    const isOnline   = runner.status !== 'OFFLINE' && runner.status !== 'DEGRADED'
                    const isDegraded = runner.status === 'DEGRADED'
                    const os         = resolveOs(runner)
                    const isMac     = os === 'MACOS'
                    const stats     = fakeRunnerStats(runner.runnerId)
                    const hostName  = runner.hostname ?? runner.runnerId

                    return (
                      <tr key={runner.runnerId}
                        className="transition-all duration-150"
                        style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}
                        onMouseEnter={e => { (e.currentTarget as HTMLTableRowElement).style.background = 'rgba(255,255,255,0.025)' }}
                        onMouseLeave={e => { (e.currentTarget as HTMLTableRowElement).style.background = 'transparent' }}>

                        {/* HOST */}
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <OsAvatar os={os} size={36} status={runner.status} />
                            <div>
                              <div className="text-[12px] font-semibold" style={{ color: 'var(--text-pri)' }}>{hostName}</div>
                              <div className="text-[10px] font-mono text-slate-600 mt-0.5">ID: {runner.runnerId.slice(0, 16)}</div>
                            </div>
                          </div>
                        </td>

                        {/* SISTEMA OPERATIVO */}
                        <td className="px-4 py-3">
                          <div className="text-[11px] font-semibold" style={{ color: 'var(--text-sec)' }}>{osVersionLabel(os)}</div>
                          <div className="text-[10px] text-slate-600">{osArchLabel(os)}</div>
                        </td>

                        {/* ESTADO */}
                        <td className="px-4 py-3">
                          <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-black"
                            style={{
                              background: isDegraded ? 'rgba(245,158,11,0.15)' : isOnline ? 'rgba(16,185,129,0.15)' : 'rgba(107,114,128,0.15)',
                              color:      isDegraded ? '#f59e0b'               : isOnline ? '#10b981'               : '#6b7280',
                              border:     `1px solid ${isDegraded ? 'rgba(245,158,11,0.3)' : isOnline ? 'rgba(16,185,129,0.3)' : 'rgba(107,114,128,0.3)'}`,
                            }}>
                            <span className="relative flex h-1.5 w-1.5 flex-shrink-0">
                              {(isOnline || isDegraded) && <span className={`absolute inline-flex h-full w-full animate-ping rounded-full opacity-50 ${isDegraded ? 'bg-amber-400' : 'bg-emerald-400'}`} />}
                              <span className="relative inline-flex h-1.5 w-1.5 rounded-full"
                                style={{ background: isDegraded ? '#f59e0b' : isOnline ? '#10b981' : '#6b7280' }} />
                            </span>
                            {isDegraded ? 'Degraded' : isOnline ? 'Online' : 'Offline'}
                          </span>
                        </td>

                        {/* ADB */}
                        <td className="px-4 py-3">
                          {runner.adbOk === undefined ? (
                            <span className="text-[10px] text-slate-600">—</span>
                          ) : runner.adbOk ? (
                            <div className="flex flex-col gap-0.5">
                              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-black w-fit"
                                style={{ background: 'rgba(16,185,129,0.15)', color: '#10b981', border: '1px solid rgba(16,185,129,0.3)' }}>
                                ✓ OK
                              </span>
                              {runner.adbVersion && runner.adbVersion !== 'unavailable' && (
                                <span className="text-[9px] font-mono text-slate-600">v{runner.adbVersion}</span>
                              )}
                            </div>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-black w-fit"
                              style={{ background: 'rgba(239,68,68,0.15)', color: '#ef4444', border: '1px solid rgba(239,68,68,0.3)' }}>
                              ✗ ERROR
                            </span>
                          )}
                        </td>

                        {/* DISPOSITIVOS */}
                        <td className="px-4 py-3">
                          <span className="text-[12px] font-bold" style={{ color: 'var(--text-sec)' }}>
                            {runner.devicesFound ?? runner.devices?.length ?? 0}
                          </span>
                        </td>

                        {/* CPU */}
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <div className="w-16 h-1.5 rounded-full" style={{ background: 'rgba(255,255,255,0.08)' }}>
                              <div className="h-1.5 rounded-full" style={{ width: `${stats.cpu}%`, background: '#6366f1' }} />
                            </div>
                            <span className="text-[10px] font-semibold w-8" style={{ color: 'var(--text-sec)' }}>{stats.cpu}%</span>
                          </div>
                        </td>

                        {/* MEMORIA */}
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-2">
                            <div className="w-16 h-1.5 rounded-full" style={{ background: 'rgba(255,255,255,0.08)' }}>
                              <div className="h-1.5 rounded-full" style={{ width: `${stats.mem}%`, background: '#f59e0b' }} />
                            </div>
                            <span className="text-[10px] font-semibold w-8" style={{ color: 'var(--text-sec)' }}>{stats.mem}%</span>
                          </div>
                        </td>

                        {/* ULTIMO CONTACTO */}
                        <td className="px-4 py-3">
                          <span className="text-[11px] text-slate-500">{timeAgo(runner.lastSeen)}</span>
                        </td>
                      </tr>
                    )
                  })}
                  {!loading && runners.length === 0 && (
                    <tr>
                      <td colSpan={8} className="px-4 py-10 text-center">
                        <div className="flex flex-col items-center gap-2">
                          <Server size={28} className="text-slate-700" />
                          <span className="text-[12px] text-slate-600">Sin hosts registrados</span>
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            <div className="flex items-center justify-center gap-2 px-5 py-2.5"
              style={{ borderTop: '1px solid rgba(255,255,255,0.05)' }}>
              <button onClick={() => setHostPage(p => Math.max(1, p - 1))} disabled={safeHostPage === 1}
                className="p-1 rounded-lg disabled:opacity-30" style={{ color: 'var(--text-dim)' }}>
                <ChevronLeft size={14} />
              </button>
              <span className="text-[11px] font-semibold px-2 py-0.5 rounded-lg"
                style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8' }}>
                {safeHostPage}
              </span>
              <button onClick={() => setHostPage(p => Math.min(totalHostPages, p + 1))} disabled={safeHostPage === totalHostPages}
                className="p-1 rounded-lg disabled:opacity-30" style={{ color: 'var(--text-dim)' }}>
                <ChevronRight size={14} />
              </button>
            </div>
          </div>

          {/* ── Devices table ────────────────────────────────────────────── */}
          <div className="rounded-2xl overflow-hidden"
            style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>

            {/* Header */}
            <div className="flex items-center justify-between px-5 py-3.5 gap-3"
              style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
              <div className="flex items-center gap-2 flex-1 min-w-0">
                <span className="text-[13px] font-bold" style={{ color: 'var(--text-pri)' }}>Dispositivos</span>
                <span className="text-[10px] font-black px-2 py-0.5 rounded-full"
                  style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.25)' }}>
                  {filteredDevices.length}
                </span>
              </div>

              {/* Host filter */}
              <div className="relative">
                <button
                  onClick={() => {}}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold"
                  style={{ background: 'rgba(255,255,255,0.06)', color: 'var(--text-sec)', border: '1px solid rgba(255,255,255,0.1)' }}>
                  <select
                    value={hostFilter}
                    onChange={e => { setHostFilter(e.target.value); setDevicePage(1) }}
                    className="bg-transparent outline-none cursor-pointer text-[11px]"
                    style={{ color: 'var(--text-sec)' }}>
                    {hostFilterOptions.map(opt => (
                      <option key={opt.id} value={opt.id}>{opt.label}</option>
                    ))}
                  </select>
                  <ChevronDown size={11} />
                </button>
              </div>

              <button
                onClick={() => onNavigate?.('devices')}
                className="text-[11px] font-semibold hover:underline flex-shrink-0"
                style={{ color: '#818cf8' }}>
                Ver todos los dispositivos
              </button>
            </div>

            {/* Table */}
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
                    {['DISPOSITIVO', 'PLATAFORMA', 'HOST', 'ESTADO', 'BATERIA', 'SO', 'ULTIMO USO', ''].map(h => (
                      <th key={h} className="px-4 py-2.5 text-left text-[9px] font-black tracking-widest whitespace-nowrap"
                        style={{ color: 'var(--text-dim)' }}>
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {loading && devices.length === 0 && Array.from({ length: 3 }, (_, i) => (
                    <tr key={i} style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                      {Array.from({ length: 8 }, (__, j) => (
                        <td key={j} className="px-4 py-3">
                          <div className="h-4 rounded animate-pulse" style={{ background: 'rgba(255,255,255,0.06)', width: j === 0 ? 140 : 70 }} />
                        </td>
                      ))}
                    </tr>
                  ))}
                  {paginatedDevices.map(device => {
                    const isIos  = device.platform === 'IOS'
                    const bat    = fakeBattery(device.udid)
                    const runner = runners.find(r => r.runnerId === device.runnerId)
                    const rOs    = runner ? osDisplayLabel(resolveOs(runner)) : '—'

                    return (
                      <tr key={device.udid}
                        className="group transition-all duration-150"
                        style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}
                        onMouseEnter={e => { (e.currentTarget as HTMLTableRowElement).style.background = 'rgba(255,255,255,0.025)' }}
                        onMouseLeave={e => { (e.currentTarget as HTMLTableRowElement).style.background = 'transparent' }}>

                        {/* DISPOSITIVO */}
                        <td className="px-4 py-3">
                          <div className="flex items-center gap-3">
                            <OsAvatar os={device.platform ?? (isIos ? 'IOS' : 'ANDROID')} size={36} status={device.status} />
                            <div>
                              <div className="text-[12px] font-semibold" style={{ color: 'var(--text-pri)' }}>
                                {device.deviceName ?? device.model ?? 'Desconocido'}
                              </div>
                              <div className="text-[10px] font-mono text-slate-600 mt-0.5 max-w-[110px] truncate">{device.udid}</div>
                            </div>
                          </div>
                        </td>

                        {/* PLATAFORMA */}
                        <td className="px-4 py-3">
                          <PlatformBadge platform={device.platform ?? (isIos ? 'IOS' : 'ANDROID')} />
                        </td>

                        {/* HOST */}
                        <td className="px-4 py-3">
                          <div className="text-[11px] font-semibold" style={{ color: 'var(--text-sec)' }}>
                            {runner?.hostname ?? device.runnerId ?? '—'}
                          </div>
                          <div className="text-[10px] text-slate-600">{rOs}</div>
                        </td>

                        {/* ESTADO */}
                        <td className="px-4 py-3"><StatusBadge status={device.status} /></td>

                        {/* BATERIA */}
                        <td className="px-4 py-3"><BatteryChip pct={bat} /></td>

                        {/* SO */}
                        <td className="px-4 py-3">
                          <PlatformBadge
                            platform={device.platform ?? (isIos ? 'IOS' : 'ANDROID')}
                            version={device.platformVersion}
                            size="xs"
                          />
                        </td>

                        {/* ULTIMO USO */}
                        <td className="px-4 py-3">
                          <span className="text-[11px] text-slate-500">{timeAgo(device.lastSeen)}</span>
                        </td>

                        {/* MENU */}
                        <td className="px-3 py-3 relative">
                          <div className="relative">
                            <button
                              onClick={() => setDeviceMenuId(v => v === device.udid ? null : device.udid)}
                              className="p-1.5 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity"
                              style={{ background: 'rgba(255,255,255,0.06)', color: 'var(--text-dim)' }}>
                              <MoreHorizontal size={14} />
                            </button>
                            <AnimatePresence>
                              {deviceMenuId === device.udid && (
                                <motion.div
                                  initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}
                                  className="absolute right-0 top-8 z-30 rounded-xl py-1.5 w-44"
                                  style={{ background: '#1e2130', border: '1px solid rgba(255,255,255,0.12)', boxShadow: '0 8px 32px rgba(0,0,0,0.5)' }}>
                                  {(['AVAILABLE', 'MAINTENANCE', 'OFFLINE'] as DeviceStatus[]).map(s => {
                                    const cfg = STATUS_CFG[s]
                                    return (
                                      <button key={s} onClick={() => handleStatusChange(device.udid, s)}
                                        className="w-full flex items-center gap-2 px-3 py-1.5 text-[11px] font-semibold text-left hover:bg-white/5"
                                        style={{ color: cfg.color }}>
                                        <span className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ background: cfg.color }} />
                                        Marcar como {cfg.label}
                                      </button>
                                    )
                                  })}
                                  <div className="border-t mx-2 my-1" style={{ borderColor: 'rgba(255,255,255,0.08)' }} />
                                  <button onClick={() => handleRemoveDevice(device.udid)}
                                    className="w-full flex items-center gap-2 px-3 py-1.5 text-[11px] font-semibold text-left hover:bg-white/5 text-red-400">
                                    Eliminar del pool
                                  </button>
                                </motion.div>
                              )}
                            </AnimatePresence>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                  {!loading && devices.length === 0 && (
                    <tr>
                      <td colSpan={8} className="px-4 py-10 text-center">
                        <div className="flex flex-col items-center gap-2">
                          <Smartphone size={28} className="text-slate-700" />
                          <span className="text-[12px] text-slate-600">Sin dispositivos registrados</span>
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            <div className="flex items-center justify-between px-5 py-2.5"
              style={{ borderTop: '1px solid rgba(255,255,255,0.05)' }}>
              <div className="flex items-center gap-2">
                <button onClick={() => setDevicePage(p => Math.max(1, p - 1))} disabled={safeDevicePage === 1}
                  className="p-1 rounded-lg disabled:opacity-30" style={{ color: 'var(--text-dim)' }}>
                  <ChevronLeft size={14} />
                </button>
                <span className="text-[11px] font-semibold px-2 py-0.5 rounded-lg"
                  style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8' }}>
                  {safeDevicePage}
                </span>
                <button onClick={() => setDevicePage(p => Math.min(totalDevicePages, p + 1))} disabled={safeDevicePage === totalDevicePages}
                  className="p-1 rounded-lg disabled:opacity-30" style={{ color: 'var(--text-dim)' }}>
                  <ChevronRight size={14} />
                </button>
              </div>
              <button
                onClick={() => onNavigate?.('devices')}
                className="text-[11px] font-semibold hover:underline"
                style={{ color: '#818cf8' }}>
                Ver todos los dispositivos
              </button>
            </div>
          </div>
        </div>

        {/* ── Right sidebar ────────────────────────────────────────────────── */}
        <div style={{ width: 320, flexShrink: 0 }} className="flex flex-col gap-4">

          {/* ── Activity feed ─────────────────────────────────────────────── */}
          <div className="rounded-2xl p-4"
            style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
            <div className="flex items-center justify-between mb-3">
              <span className="text-[13px] font-bold" style={{ color: 'var(--text-pri)' }}>Actividad en Tiempo Real</span>
              <button className="text-[11px] font-semibold hover:underline" style={{ color: '#818cf8' }}>
                Ver todo
              </button>
            </div>
            <div className="space-y-0">
              {activityEvents.length === 0 && (
                <div className="text-[11px] text-slate-600 py-6 text-center">Sin actividad reciente</div>
              )}
              {activityEvents.slice(0, 8).map(ev => {
                const cfg = ACTIVITY_CFG[ev.type]
                return (
                  <div key={ev.id} className="flex items-start gap-2.5 py-2.5"
                    style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
                    <div className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5"
                      style={{ background: cfg.bg, border: '1px solid rgba(255,255,255,0.08)' }}>
                      {cfg.icon}
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-[11px] font-semibold" style={{ color: 'var(--text-sec)' }}>{ev.title}</div>
                      <div className="text-[10px] text-slate-600 truncate">{ev.subtitle}</div>
                    </div>
                    <span className="text-[9px] text-slate-600 flex-shrink-0 mt-0.5">{fmtClock(ev.time)}</span>
                  </div>
                )
              })}
            </div>
            <div className="flex items-center gap-1.5 text-[10px] text-slate-600 mt-3 pt-2"
              style={{ borderTop: '1px solid rgba(255,255,255,0.04)' }}>
              <Clock size={10} />
              {fmtClock(new Date(lastRefresh).toISOString())} · Refresco cada 30s
            </div>
          </div>

          {/* ── Usage donut ───────────────────────────────────────────────── */}
          <div className="rounded-2xl p-4"
            style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
            <div className="flex items-center justify-between mb-3">
              <span className="text-[13px] font-bold" style={{ color: 'var(--text-pri)' }}>Uso de Dispositivos</span>
              <button className="text-[11px] font-semibold hover:underline" style={{ color: '#818cf8' }}>
                Ver reporte
              </button>
            </div>

            {/* Donut */}
            <div className="relative">
              <ResponsiveContainer width="100%" height={150}>
                <PieChart>
                  <Pie
                    data={donutFallback}
                    innerRadius={50}
                    outerRadius={68}
                    paddingAngle={donutData.length > 1 ? 3 : 0}
                    dataKey="value"
                    startAngle={90}
                    endAngle={-270}
                    labelLine={false}
                    label={false}>
                    {donutFallback.map((_, i) => (
                      <Cell key={i} fill={donutColors[i] ?? '#6b7280'} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ background: '#1e2130', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, fontSize: 11 }}
                    labelStyle={{ color: '#94a3b8' }}
                    itemStyle={{ color: '#e2e8f0' }} />
                </PieChart>
              </ResponsiveContainer>
              {/* Center label */}
              <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                <span className="text-2xl font-black" style={{ color: 'var(--text-pri)' }}>{totalDevices}</span>
                <span className="text-[9px] font-bold tracking-widest text-slate-600 uppercase">TOTAL</span>
              </div>
            </div>

            {/* Legend */}
            <div className="space-y-2 mt-2">
              {[
                { label: 'Disponibles',      count: devAvailable,   color: '#10b981' },
                { label: 'En uso',           count: devBusy,         color: '#3b82f6' },
                { label: 'En mantenimiento', count: devMaintenance,  color: '#f59e0b' },
                { label: 'Offline',          count: devOffline,      color: '#ef4444' },
              ].map(row => {
                const pct = totalDevices === 0 ? 0 : Math.round((row.count / totalDevices) * 100)
                return (
                  <div key={row.label} className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5">
                      <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: row.color }} />
                      <span className="text-[11px] text-slate-400">{row.label}</span>
                    </div>
                    <span className="text-[11px] font-semibold" style={{ color: 'var(--text-sec)' }}>
                      {row.count} <span className="text-slate-600">({pct}%)</span>
                    </span>
                  </div>
                )
              })}
            </div>
          </div>

          {/* ── Quick actions ─────────────────────────────────────────────── */}
          <div className="rounded-2xl p-4"
            style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
            <span className="text-[13px] font-bold block mb-3" style={{ color: 'var(--text-pri)' }}>Acciones Rapidas</span>
            <div className="grid grid-cols-2 gap-2">

              {/* Ejecutar Suite */}
              <button
                onClick={() => onNavigate?.('dashboard')}
                className="flex flex-col items-center gap-2 p-3 rounded-xl transition-all hover:scale-105 active:scale-95"
                style={{ background: 'rgba(59,130,246,0.1)', border: '1px solid rgba(59,130,246,0.2)' }}>
                <div className="w-9 h-9 rounded-xl flex items-center justify-center"
                  style={{ background: 'rgba(59,130,246,0.15)' }}>
                  <Play size={16} style={{ color: '#3b82f6' }} />
                </div>
                <span className="text-[11px] font-semibold" style={{ color: '#3b82f6' }}>Ejecutar Suite</span>
              </button>

              {/* Registrar Host */}
              <button
                onClick={() => setShowModal(true)}
                className="flex flex-col items-center gap-2 p-3 rounded-xl transition-all hover:scale-105 active:scale-95"
                style={{ background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.2)' }}>
                <div className="w-9 h-9 rounded-xl flex items-center justify-center"
                  style={{ background: 'rgba(16,185,129,0.15)' }}>
                  <Monitor size={16} style={{ color: '#10b981' }} />
                </div>
                <span className="text-[11px] font-semibold" style={{ color: '#10b981' }}>Registrar Host</span>
              </button>

              {/* Registrar Dispositivo */}
              <button
                onClick={() => setShowModal(true)}
                className="flex flex-col items-center gap-2 p-3 rounded-xl transition-all hover:scale-105 active:scale-95"
                style={{ background: 'rgba(139,92,246,0.1)', border: '1px solid rgba(139,92,246,0.2)' }}>
                <div className="w-9 h-9 rounded-xl flex items-center justify-center"
                  style={{ background: 'rgba(139,92,246,0.15)' }}>
                  <Smartphone size={16} style={{ color: '#8b5cf6' }} />
                </div>
                <span className="text-[11px] font-semibold" style={{ color: '#8b5cf6' }}>Registrar Dispositivo</span>
              </button>

              {/* Ver Reportes */}
              <button
                onClick={() => onNavigate?.('reports')}
                className="flex flex-col items-center gap-2 p-3 rounded-xl transition-all hover:scale-105 active:scale-95"
                style={{ background: 'rgba(234,179,8,0.1)', border: '1px solid rgba(234,179,8,0.2)' }}>
                <div className="w-9 h-9 rounded-xl flex items-center justify-center"
                  style={{ background: 'rgba(234,179,8,0.15)' }}>
                  <BarChart3 size={16} style={{ color: '#eab308' }} />
                </div>
                <span className="text-[11px] font-semibold" style={{ color: '#eab308' }}>Ver Reportes</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* ── Modal ─────────────────────────────────────────────────────────────── */}
      <AnimatePresence>
        {showModal && (
          <DownloadModal
            infraState={infraState}
            runners={runners}
            onClose={() => setShowModal(false)}
          />
        )}
      </AnimatePresence>
    </div>
  )
}
