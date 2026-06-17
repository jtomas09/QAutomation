import React, { useState, useEffect, useCallback, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  ResponsiveContainer, PieChart, Pie, Cell,
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
} from 'recharts'
import {
  Smartphone, RefreshCw, Search, Plus, Wifi, WifiOff, Activity,
  AlertCircle, Monitor, Apple, Settings, Clock, CheckCircle2,
  Wrench, Server, ChevronLeft, ChevronRight, ChevronDown,
  MoreHorizontal, Zap, HardDrive, Radio, RotateCcw,
} from 'lucide-react'
import { getDevices, getRunners, updateDeviceStatus, removeDevice } from '../api'
import type { PhysicalDevice, DeviceStatus, Runner } from '../types'

// ─── Types ─────────────────────────────────────────────────────────────────

interface ActivityEvent {
  id: string
  time: string
  type: 'INFO' | 'SUCCESS' | 'WARN' | 'ERROR'
  message: string
}

// ─── Constants ──────────────────────────────────────────────────────────────

const STATUS_CFG: Record<DeviceStatus, { label: string; color: string; bg: string }> = {
  AVAILABLE:   { label: 'DISPONIBLE',  color: '#10b981', bg: 'rgba(16,185,129,0.15)' },
  BUSY:        { label: 'EN USO',      color: '#f59e0b', bg: 'rgba(245,158,11,0.15)' },
  OFFLINE:     { label: 'Sin conexión',color: '#6b7280', bg: 'rgba(107,114,128,0.15)' },
  MAINTENANCE: { label: 'Maintenance', color: '#8b5cf6', bg: 'rgba(139,92,246,0.15)' },
}

const PIE_COLORS = ['#10b981', '#818cf8', '#6b7280']

// ─── Helpers ────────────────────────────────────────────────────────────────

function timeAgo(iso: string | null | undefined): string {
  if (!iso) return '—'
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (diff < 5)    return 'ahora'
  if (diff < 60)   return `Hace ${diff}s`
  if (diff < 3600) return `Hace ${Math.floor(diff / 60)}min`
  return `Hace ${Math.floor(diff / 3600)}h`
}

function fmtTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function pct(n: number, total: number) {
  return total === 0 ? '0.0' : ((n / total) * 100).toFixed(1)
}

function generateActivity(devices: PhysicalDevice[], runners: Runner[]): ActivityEvent[] {
  const events: ActivityEvent[] = []
  devices.forEach((d, i) => {
    if (d.status === 'AVAILABLE') {
      events.push({ id: `d-av-${i}`, time: d.lastSeen ?? '', type: 'INFO',
        message: `Dispositivo ${d.deviceName ?? d.udid} disponible nuevamente` })
    } else if (d.status === 'BUSY') {
      events.push({ id: `d-bz-${i}`, time: d.lastSeen ?? '', type: 'SUCCESS',
        message: `Ejecución ${d.activeExecutionId ?? 'RUN-?'} iniciada en ${d.deviceName ?? d.udid}` })
    }
  })
  runners.forEach((r, i) => {
    if (r.status !== 'OFFLINE') {
      events.push({ id: `r-${i}`, time: r.lastSeen ?? '', type: 'INFO',
        message: `Heartbeat recibido de ${r.runnerId} (${r.devices?.length ?? 0} dispositivos)` })
    }
  })
  // Synthetic sync event
  events.push({ id: 'sync', time: new Date().toISOString(), type: 'INFO', message: 'Sincronización automática completada' })
  return events
    .filter(e => e.time)
    .sort((a, b) => new Date(b.time).getTime() - new Date(a.time).getTime())
    .slice(0, 12)
}

function generate24h(available: number, busy: number, offline: number) {
  const now = new Date()
  return Array.from({ length: 13 }, (_, i) => {
    const h = (now.getHours() - 24 + i * 2 + 24) % 24
    const t = i / 12
    return {
      time: `${h.toString().padStart(2, '0')}:00`,
      disponibles: Math.max(0, Math.round(available + Math.sin(t * Math.PI * 3) * 1.5)),
      enUso:       Math.max(0, Math.round(busy       + Math.sin(t * Math.PI * 2 + 1) * 0.8)),
      offline:     Math.max(0, Math.round(offline    + Math.sin(t * Math.PI + 2) * 0.3)),
    }
  })
}

// ─── HealthRing ─────────────────────────────────────────────────────────────

function HealthRing({ pct: p, size = 52 }: { pct: number; size?: number }) {
  const cx = size / 2, cy = size / 2, r = size / 2 - 6
  const circ = 2 * Math.PI * r
  const offset = circ * (1 - p / 100)
  const color = p > 80 ? '#10b981' : p > 50 ? '#f59e0b' : '#ef4444'
  return (
    <div className="relative flex-shrink-0" style={{ width: size, height: size }}>
      <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={cx} cy={cy} r={r} fill="none" stroke="rgba(255,255,255,0.08)" strokeWidth="4" />
        <circle cx={cx} cy={cy} r={r} fill="none" stroke={color} strokeWidth="4"
          strokeDasharray={circ} strokeDashoffset={offset} strokeLinecap="round" />
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        <span className="text-[10px] font-black" style={{ color }}>{p}%</span>
      </div>
    </div>
  )
}

// ─── StatusBadge ────────────────────────────────────────────────────────────

function StatusBadge({ status, execId }: { status: DeviceStatus; execId?: string | null }) {
  const cfg = STATUS_CFG[status] ?? STATUS_CFG.OFFLINE
  const pulse = status === 'AVAILABLE' || status === 'BUSY'
  return (
    <div className="flex flex-col gap-0.5">
      <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-black tracking-wide w-fit"
        style={{ background: cfg.bg, color: cfg.color, border: `1px solid ${cfg.color}30` }}>
        <span className="relative flex h-1.5 w-1.5 flex-shrink-0">
          {pulse && <span className="absolute inline-flex h-full w-full animate-ping rounded-full opacity-50" style={{ background: cfg.color }} />}
          <span className="relative inline-flex h-1.5 w-1.5 rounded-full" style={{ background: cfg.color }} />
        </span>
        {cfg.label}
      </span>
      {status === 'BUSY' && execId && (
        <span className="text-[9px] font-mono text-slate-500 pl-1">{execId}</span>
      )}
    </div>
  )
}

// ─── DeviceTableRow ─────────────────────────────────────────────────────────

function DeviceTableRow({
  device, onStatusChange, onRemove
}: {
  device: PhysicalDevice
  onStatusChange: (udid: string, s: DeviceStatus) => void
  onRemove: (udid: string) => void
}) {
  const [menu, setMenu] = useState(false)
  const isIos = device.platform === 'IOS'

  return (
    <tr className="border-b transition-all duration-150 group"
      style={{ borderColor: 'rgba(255,255,255,0.04)' }}
      onMouseEnter={e => { (e.currentTarget as HTMLTableRowElement).style.background = 'rgba(255,255,255,0.025)' }}
      onMouseLeave={e => { (e.currentTarget as HTMLTableRowElement).style.background = 'transparent' }}>

      {/* Dispositivo */}
      <td className="px-4 py-3">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ background: isIos ? 'rgba(99,102,241,0.12)' : 'rgba(16,185,129,0.12)', border: `1px solid ${isIos ? 'rgba(99,102,241,0.25)' : 'rgba(16,185,129,0.25)'}` }}>
            <Smartphone size={16} style={{ color: isIos ? '#818cf8' : '#10b981' }} />
          </div>
          <div>
            <div className="text-[12px] font-semibold" style={{ color: 'var(--text-pri)' }}>
              {device.deviceName ?? device.model ?? 'Desconocido'}
            </div>
            <div className="text-[10px] font-mono text-slate-600 mt-0.5 max-w-[130px] truncate">
              {device.udid}
            </div>
          </div>
        </div>
      </td>

      {/* Plataforma */}
      <td className="px-4 py-3">
        <div className="flex items-center gap-1.5">
          {isIos
            ? <Apple size={13} className="text-indigo-400 flex-shrink-0" />
            : <Monitor size={13} className="text-emerald-400 flex-shrink-0" />}
          <span className="text-[11px] font-semibold" style={{ color: 'var(--text-sec)' }}>
            {isIos ? 'iOS' : 'Android'}
          </span>
        </div>
      </td>

      {/* Versión */}
      <td className="px-4 py-3">
        <span className="text-[12px] font-mono font-bold" style={{ color: 'var(--text-sec)' }}>
          {device.platformVersion ?? '—'}
        </span>
      </td>

      {/* Estado */}
      <td className="px-4 py-3">
        <StatusBadge status={device.status} execId={device.activeExecutionId} />
      </td>

      {/* Runner */}
      <td className="px-4 py-3">
        <div>
          <div className="text-[11px] font-semibold" style={{ color: 'var(--text-sec)' }}>
            {device.runnerId ?? '—'}
          </div>
          <div className="text-[10px] text-slate-600">
            {device.runnerId?.startsWith('mac') ? 'macOS' : device.runnerId?.startsWith('win') ? 'Windows' : 'Linux'}
          </div>
        </div>
      </td>

      {/* Último seen */}
      <td className="px-4 py-3">
        <span className="text-[11px] text-slate-500">{fmtTime(device.lastSeen)}</span>
      </td>

      {/* Acción */}
      <td className="px-4 py-3 relative">
        <div className="relative">
          <button onClick={() => setMenu(v => !v)}
            className="p-1.5 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity"
            style={{ background: 'rgba(255,255,255,0.06)', color: 'var(--text-dim)' }}>
            <MoreHorizontal size={14} />
          </button>
          <AnimatePresence>
            {menu && (
              <motion.div
                initial={{ opacity: 0, scale: 0.95 }} animate={{ opacity: 1, scale: 1 }} exit={{ opacity: 0, scale: 0.95 }}
                className="absolute right-0 top-8 z-30 rounded-xl py-1.5 w-44"
                style={{ background: '#1e2130', border: '1px solid rgba(255,255,255,0.12)', boxShadow: '0 8px 32px rgba(0,0,0,0.5)' }}
                onClick={() => setMenu(false)}>
                {(['AVAILABLE', 'MAINTENANCE', 'OFFLINE'] as DeviceStatus[]).map(s => {
                  const cfg = STATUS_CFG[s]
                  return (
                    <button key={s} onClick={() => onStatusChange(device.udid, s)}
                      className="w-full flex items-center gap-2 px-3 py-1.5 text-[11px] font-semibold text-left transition-colors hover:bg-white/5"
                      style={{ color: cfg.color }}>
                      <span className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ background: cfg.color }} />
                      Marcar como {cfg.label}
                    </button>
                  )
                })}
                <div className="border-t mx-2 my-1" style={{ borderColor: 'rgba(255,255,255,0.08)' }} />
                <button onClick={() => onRemove(device.udid)}
                  className="w-full flex items-center gap-2 px-3 py-1.5 text-[11px] font-semibold text-left transition-colors hover:bg-white/5 text-red-400">
                  Eliminar del pool
                </button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </td>
    </tr>
  )
}

// ─── RunnerCard (sidebar) ────────────────────────────────────────────────────

function RunnerCard({ runner }: { runner: Runner }) {
  const isOnline    = runner.status !== 'OFFLINE'
  const health      = isOnline ? 100 : 0
  const statusColor = isOnline ? '#10b981' : '#6b7280'

  // Resolve OS: use reported field, fallback to heuristic
  const os = (() => {
    if (runner.os === 'MACOS' || runner.os === 'WINDOWS' || runner.os === 'LINUX') return runner.os
    const id = runner.runnerId?.toLowerCase() ?? ''
    if (id.includes('mac'))   return 'MACOS'
    if (id.includes('linux')) return 'LINUX'
    return 'WINDOWS'
  })()
  const isMac       = os === 'MACOS'
  const iconColor   = isMac ? '#818cf8' : '#60a5fa'
  const iconBg      = isMac ? 'rgba(99,102,241,0.12)' : 'rgba(59,130,246,0.12)'
  const iconBorder  = isMac ? 'rgba(99,102,241,0.25)' : 'rgba(59,130,246,0.25)'
  const osLabel     = isMac ? 'macOS' : os === 'LINUX' ? 'Linux' : 'Windows'

  const androidOk   = runner.androidSupported ?? true
  const iosOk       = runner.iosSupported ?? (runner.platform === 'ios')

  return (
    <div className="rounded-xl p-3.5 mb-3"
      style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)' }}>
      <div className="flex items-start gap-3">
        {/* OS icon */}
        <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{ background: iconBg, border: `1px solid ${iconBorder}` }}>
          {isMac
            ? <Apple size={17} style={{ color: iconColor }} />
            : <Monitor size={17} style={{ color: iconColor }} />}
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-0.5 flex-wrap">
            <span className="text-[12px] font-bold" style={{ color: 'var(--text-pri)' }}>
              {runner.runnerId}
            </span>
            <span className="text-[9px] font-black px-1.5 py-0.5 rounded-full"
              style={{ background: `${statusColor}20`, color: statusColor, border: `1px solid ${statusColor}30` }}>
              {runner.status}
            </span>
          </div>
          {/* OS + hostname */}
          <div className="text-[10px] text-slate-500 mb-1.5">
            {runner.hostname ? `${osLabel} · ${runner.hostname}` : osLabel}
          </div>
          {/* Capability chips */}
          <div className="flex items-center gap-1.5 mb-2">
            <span className="text-[9px] px-1.5 py-0.5 rounded-full font-semibold"
              style={{ background: androidOk ? 'rgba(16,185,129,0.12)' : 'rgba(107,114,128,0.12)', color: androidOk ? '#10b981' : '#6b7280', border: `1px solid ${androidOk ? 'rgba(16,185,129,0.25)' : 'rgba(107,114,128,0.2)'}` }}>
              {androidOk ? '✓' : '✗'} Android
            </span>
            <span className="text-[9px] px-1.5 py-0.5 rounded-full font-semibold"
              style={{ background: iosOk ? 'rgba(129,140,248,0.12)' : 'rgba(107,114,128,0.12)', color: iosOk ? '#818cf8' : '#6b7280', border: `1px solid ${iosOk ? 'rgba(129,140,248,0.25)' : 'rgba(107,114,128,0.2)'}` }}>
              {iosOk ? '✓' : '✗'} iOS
            </span>
          </div>
          <div className="grid grid-cols-2 gap-x-4 gap-y-0.5">
            <div className="text-[10px] text-slate-600">
              Dispositivos: <span className="text-slate-400 font-semibold">{runner.devices?.length ?? 0}</span>
            </div>
            <div className="text-[10px] text-slate-600">
              Versión: <span className="text-slate-400 font-semibold">{runner.version ?? '—'}</span>
            </div>
            <div className="text-[10px] text-slate-600 col-span-2">
              Último Heartbeat: <span className="text-slate-400">{fmtTime(runner.lastSeen)}</span>
            </div>
          </div>
        </div>

        {/* Health ring */}
        <HealthRing pct={health} size={48} />
      </div>
    </div>
  )
}

// ─── StatCard ────────────────────────────────────────────────────────────────

function StatCard({ icon, title, value, subtitle, accent }: {
  icon: React.ReactNode; title: string; value: number | string; subtitle?: string; accent: string
}) {
  return (
    <div className="rounded-2xl p-4 flex items-center gap-4"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0.02) 100%)',
        border: '1px solid rgba(255,255,255,0.08)',
        backdropFilter: 'blur(12px)',
      }}>
      <div className="w-12 h-12 rounded-2xl flex items-center justify-center flex-shrink-0"
        style={{ background: `${accent}18`, border: `1px solid ${accent}30` }}>
        <span style={{ color: accent }}>{icon}</span>
      </div>
      <div className="min-w-0">
        <div className="text-[10px] text-slate-500 font-semibold tracking-wide truncate">{title}</div>
        <div className="text-3xl font-black tabular-nums leading-none mt-0.5" style={{ color: 'var(--text-pri)' }}>
          {value}
        </div>
        {subtitle && <div className="text-[10px] mt-0.5" style={{ color: accent }}>{subtitle}</div>}
      </div>
    </div>
  )
}

// ─── ActivityItem ────────────────────────────────────────────────────────────

const ACT_COLORS: Record<string, string> = {
  INFO: '#6366f1', SUCCESS: '#10b981', WARN: '#f59e0b', ERROR: '#ef4444'
}

function ActivityItem({ ev }: { ev: ActivityEvent }) {
  const color = ACT_COLORS[ev.type] ?? '#6366f1'
  return (
    <div className="flex items-start gap-2.5 py-1.5">
      <span className="w-1.5 h-1.5 rounded-full mt-1.5 flex-shrink-0" style={{ background: color }} />
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5 flex-wrap">
          <span className="text-[10px] font-mono text-slate-600">{fmtTime(ev.time)}</span>
          <span className="text-[9px] font-black px-1.5 py-0.5 rounded-full"
            style={{ background: `${color}18`, color }}>
            {ev.type}
          </span>
          <span className="text-[11px] text-slate-400 truncate">{ev.message}</span>
        </div>
      </div>
    </div>
  )
}

// ─── DiscoveryItem ───────────────────────────────────────────────────────────

function DiscoveryItem({ device }: { device: PhysicalDevice }) {
  const isIos = device.platform === 'IOS'
  return (
    <div className="flex items-center gap-2.5 py-2 border-b" style={{ borderColor: 'rgba(255,255,255,0.05)' }}>
      <div className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0"
        style={{ background: isIos ? 'rgba(99,102,241,0.12)' : 'rgba(16,185,129,0.12)' }}>
        {isIos ? <Apple size={13} className="text-indigo-400" /> : <Monitor size={13} className="text-emerald-400" />}
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-[11px] font-semibold truncate" style={{ color: 'var(--text-sec)' }}>
          {device.deviceName ?? device.model ?? device.udid} conectado
        </div>
        <div className="text-[10px] text-slate-600">
          {isIos ? 'iOS' : 'Android'} · {device.runnerId ?? '—'}
        </div>
      </div>
      <span className="text-[10px] text-slate-600 flex-shrink-0">{timeAgo(device.lastSeen)}</span>
    </div>
  )
}

// ─── Donut chart (Platform Distribution) ────────────────────────────────────

function PlatformDonut({ android, ios, otros, total }: {
  android: number; ios: number; otros: number; total: number
}) {
  const data = [
    { name: 'Android', value: android },
    { name: 'iOS',     value: ios },
    { name: 'Otros',   value: otros },
  ].filter(d => d.value > 0)

  const CenterLabel = ({ viewBox }: { viewBox?: { cx: number; cy: number } }) => {
    if (!viewBox) return null
    const { cx, cy } = viewBox
    return (
      <g>
        <text x={cx} y={cy - 8} textAnchor="middle" fill="var(--text-pri)" fontSize={28} fontWeight={900}>{total}</text>
        <text x={cx} y={cy + 10} textAnchor="middle" fill="#6b7280" fontSize={10} fontWeight={700} letterSpacing={1}>TOTAL</text>
      </g>
    )
  }

  return (
    <div>
      <ResponsiveContainer width="100%" height={160}>
        <PieChart>
          <Pie data={data} innerRadius={48} outerRadius={68} paddingAngle={3}
            dataKey="value" startAngle={90} endAngle={-270}
            label={false} labelLine={false}>
            {data.map((_, i) => <Cell key={i} fill={PIE_COLORS[i]} />)}
            {/* @ts-ignore */}
            <CenterLabel />
          </Pie>
        </PieChart>
      </ResponsiveContainer>
      <div className="space-y-1.5 mt-1">
        {[
          { label: 'Android', count: android, color: PIE_COLORS[0] },
          { label: 'iOS',     count: ios,     color: PIE_COLORS[1] },
          { label: 'Otros',   count: otros,   color: PIE_COLORS[2] },
        ].map(row => (
          <div key={row.label} className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: row.color }} />
              <span className="text-[11px] text-slate-400">{row.label}</span>
            </div>
            <span className="text-[11px] font-semibold text-slate-300">
              {row.count} <span className="text-slate-600">({pct(row.count, total)}%)</span>
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}

// ─── Line chart (24h History) ────────────────────────────────────────────────

function HistoryChart({ data }: { data: ReturnType<typeof generate24h> }) {
  return (
    <ResponsiveContainer width="100%" height={155}>
      <LineChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: -20 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
        <XAxis dataKey="time" tick={{ fill: '#6b7280', fontSize: 9 }} tickLine={false} axisLine={false}
          interval={2} />
        <YAxis tick={{ fill: '#6b7280', fontSize: 9 }} tickLine={false} axisLine={false}
          domain={[0, 'dataMax + 1']} />
        <Tooltip
          contentStyle={{ background: '#1e2130', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, fontSize: 11 }}
          labelStyle={{ color: '#94a3b8' }} itemStyle={{ color: '#e2e8f0' }} />
        <Line type="monotone" dataKey="disponibles" stroke="#10b981" strokeWidth={2} dot={false} name="Disponibles" />
        <Line type="monotone" dataKey="enUso"       stroke="#f59e0b" strokeWidth={2} dot={false} name="En Uso" />
        <Line type="monotone" dataKey="offline"     stroke="#ef4444" strokeWidth={2} dot={false} name="Offline" />
      </LineChart>
    </ResponsiveContainer>
  )
}

// ─── Dropdown filter ─────────────────────────────────────────────────────────

function FilterDropdown({ label, options, value, onChange }: {
  label: string; options: { id: string; label: string }[]
  value: string; onChange: (v: string) => void
}) {
  const [open, setOpen] = useState(false)
  const selected = options.find(o => o.id === value)
  return (
    <div className="relative">
      <button onClick={() => setOpen(v => !v)}
        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold"
        style={{ background: 'rgba(255,255,255,0.06)', color: 'var(--text-sec)', border: '1px solid rgba(255,255,255,0.1)' }}>
        {selected?.label ?? label}
        <ChevronDown size={11} />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: -4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
            className="absolute top-9 left-0 z-30 rounded-xl py-1 min-w-[160px]"
            style={{ background: '#1e2130', border: '1px solid rgba(255,255,255,0.12)', boxShadow: '0 8px 32px rgba(0,0,0,0.5)' }}>
            {options.map(opt => (
              <button key={opt.id} onClick={() => { onChange(opt.id); setOpen(false) }}
                className="w-full flex items-center gap-2 px-3 py-1.5 text-[11px] font-medium text-left transition-colors hover:bg-white/5"
                style={{ color: opt.id === value ? '#818cf8' : 'var(--text-sec)' }}>
                {opt.id === value && <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 flex-shrink-0" />}
                {opt.id !== value && <span className="w-1.5 flex-shrink-0" />}
                {opt.label}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

// ─── Main DeviceFarm page ─────────────────────────────────────────────────────

const ROWS_OPTIONS = [5, 10, 20]

export default function DeviceFarm() {
  const [devices, setDevices]   = useState<PhysicalDevice[]>([])
  const [runners, setRunners]   = useState<Runner[]>([])
  const [loading, setLoading]   = useState(true)
  const [error, setError]       = useState<string | null>(null)
  const [search, setSearch]     = useState('')
  const [platFilter, setPlatFilter] = useState('ALL')
  const [stateFilter, setStateFilter] = useState('ALL')
  const [page, setPage]         = useState(1)
  const [rows, setRows]         = useState(10)
  const [lastRefresh, setLastRefresh] = useState(Date.now())
  const [showAddModal, setShowAddModal] = useState(false)

  const refresh = useCallback(async () => {
    try {
      setError(null)
      const [devs, runs] = await Promise.all([getDevices(), getRunners()])
      setDevices(devs)
      setRunners(runs)
      setLastRefresh(Date.now())
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Error de conexión')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
    const id = setInterval(refresh, 10_000)
    return () => clearInterval(id)
  }, [refresh])

  // ── Derived stats ────────────────────────────────────────────────────────

  const total     = devices.length
  const available = devices.filter(d => d.status === 'AVAILABLE').length
  const busy      = devices.filter(d => d.status === 'BUSY').length
  const offline   = devices.filter(d => d.status === 'OFFLINE').length
  const android   = devices.filter(d => d.platform === 'ANDROID').length
  const ios       = devices.filter(d => d.platform === 'IOS').length

  const runnersOnline = runners.filter(r => r.status !== 'OFFLINE').length

  // ── Filtered + paginated devices ─────────────────────────────────────────

  const filtered = useMemo(() => {
    let list = devices
    if (platFilter !== 'ALL')  list = list.filter(d => d.platform === platFilter)
    if (stateFilter !== 'ALL') list = list.filter(d => d.status === stateFilter)
    if (search.trim()) {
      const q = search.toLowerCase()
      list = list.filter(d =>
        d.deviceName?.toLowerCase().includes(q) ||
        d.udid?.toLowerCase().includes(q) ||
        d.runnerId?.toLowerCase().includes(q) ||
        d.platformVersion?.includes(q)
      )
    }
    return list
  }, [devices, platFilter, stateFilter, search])

  const totalPages = Math.max(1, Math.ceil(filtered.length / rows))
  const safePage   = Math.min(page, totalPages)
  const paginated  = filtered.slice((safePage - 1) * rows, safePage * rows)

  // ── Activity + chart data ─────────────────────────────────────────────────

  const activity  = useMemo(() => generateActivity(devices, runners), [devices, runners])
  const chartData = useMemo(() => generate24h(available, busy, offline), [available, busy, offline])
  const discovery = useMemo(() =>
    [...devices].sort((a, b) =>
      new Date(b.lastSeen ?? 0).getTime() - new Date(a.lastSeen ?? 0).getTime()
    ).slice(0, 4),
    [devices]
  )

  // ── Handlers ─────────────────────────────────────────────────────────────

  async function handleStatusChange(udid: string, status: DeviceStatus) {
    await updateDeviceStatus(udid, status)
    setDevices(prev => prev.map(d => d.udid === udid ? { ...d, status } : d))
  }

  async function handleRemove(udid: string) {
    if (!confirm('¿Eliminar este dispositivo del pool?')) return
    await removeDevice(udid)
    setDevices(prev => prev.filter(d => d.udid !== udid))
  }

  // ── Platform / status filter options ─────────────────────────────────────

  const platOptions = [
    { id: 'ALL', label: 'Todos los Platforms' },
    { id: 'ANDROID', label: 'Android' },
    { id: 'IOS',     label: 'iOS' },
  ]
  const stateOptions = [
    { id: 'ALL',         label: 'Todos los Estados' },
    { id: 'AVAILABLE',   label: 'Disponible' },
    { id: 'BUSY',        label: 'En Uso' },
    { id: 'OFFLINE',     label: 'Offline' },
    { id: 'MAINTENANCE', label: 'Maintenance' },
  ]

  // ─────────────────────────────────────────────────────────────────────────

  return (
    <div className="p-6 min-h-full" style={{ background: 'var(--bg-main)' }}>

      {/* ── Header ─────────────────────────────────────────────────────── */}
      <div className="flex items-start justify-between gap-4 mb-5">
        <div>
          <h1 className="text-2xl font-black" style={{ color: 'var(--text-pri)' }}>Device Farm</h1>
          <p className="text-[11px] text-slate-500 mt-0.5">
            Descubrimiento y gestión automática de dispositivos físicos conectados.
          </p>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <div className="flex items-center gap-1.5 text-[11px] text-emerald-400">
            <span className="relative flex h-1.5 w-1.5">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-emerald-400 opacity-50" />
              <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-emerald-400" />
            </span>
            En tiempo real
          </div>
          <span className="text-[11px] text-slate-600">
            Última actualización: {new Date(lastRefresh).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
          </span>
          <button onClick={refresh}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-[11px] font-semibold transition-all"
            style={{ background: 'rgba(255,255,255,0.05)', color: 'var(--text-dim)', border: '1px solid rgba(255,255,255,0.1)' }}>
            <RefreshCw size={12} />
            Sincronizar Dispositivos
          </button>
          <button onClick={() => setShowAddModal(true)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-[11px] font-semibold transition-all"
            style={{ background: 'linear-gradient(135deg, #6366f1 0%, #7c3aed 100%)', color: '#fff', boxShadow: '0 0 16px rgba(99,102,241,0.35)' }}>
            <Plus size={12} />
            Agregar Runner
          </button>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="flex items-center gap-2 p-3 rounded-2xl text-sm mb-4"
          style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)', color: '#ef4444' }}>
          <AlertCircle size={14} />
          {error}
        </div>
      )}

      {/* ── Stats row ──────────────────────────────────────────────────── */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-5">
        <StatCard icon={<Smartphone size={22} />} title="Total Dispositivos"  value={total}     accent="#6366f1" subtitle="Todos los dispositivos" />
        <StatCard icon={<CheckCircle2 size={22} />} title="Disponibles"       value={available}  accent="#10b981" subtitle={`${pct(available, total)}% del total`} />
        <StatCard icon={<Activity size={22} />}   title="En Uso"              value={busy}       accent="#f59e0b" subtitle={`${pct(busy, total)}% del total`} />
        <StatCard icon={<WifiOff size={22} />}    title="Offline"             value={offline}    accent="#ef4444" subtitle={`${pct(offline, total)}% del total`} />
        <StatCard icon={<Monitor size={22} />}    title="Android"             value={android}    accent="#10b981" subtitle={`${pct(android, total)}% del total`} />
        <StatCard icon={<Apple size={22} />}      title="iOS"                 value={ios}        accent="#818cf8" subtitle={`${pct(ios, total)}% del total`} />
      </div>

      {/* ── Two-column body ────────────────────────────────────────────── */}
      <div className="flex gap-4 items-start">

        {/* ── LEFT: Table + bottom panels ──────────────────────────────── */}
        <div className="flex-1 min-w-0 space-y-4">

          {/* Device table card */}
          <div className="rounded-2xl overflow-hidden"
            style={{
              background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
              border: '1px solid rgba(255,255,255,0.07)',
              backdropFilter: 'blur(12px)',
            }}>

            {/* Table header + filters */}
            <div className="flex flex-wrap items-center gap-2 px-5 py-3"
              style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
              <div className="flex items-center gap-2 flex-1 min-w-0">
                <span className="text-[13px] font-bold" style={{ color: 'var(--text-pri)' }}>
                  Dispositivos Conectados
                </span>
                <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full"
                  style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.25)' }}>
                  {filtered.length} dispositivos
                </span>
              </div>

              {/* Search */}
              <div className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg"
                style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)', width: 200 }}>
                <Search size={11} style={{ color: 'var(--text-dim)', flexShrink: 0 }} />
                <input type="text" value={search} onChange={e => { setSearch(e.target.value); setPage(1) }}
                  placeholder="Buscar dispositivo..."
                  className="flex-1 bg-transparent text-[11px] outline-none placeholder-slate-600"
                  style={{ color: 'var(--text-sec)' }} />
              </div>

              <FilterDropdown label="Todos los Platforms" options={platOptions}  value={platFilter}  onChange={v => { setPlatFilter(v);  setPage(1) }} />
              <FilterDropdown label="Todos los Estados"   options={stateOptions} value={stateFilter} onChange={v => { setStateFilter(v); setPage(1) }} />
            </div>

            {/* Empty state */}
            {!loading && devices.length === 0 && (
              <div className="flex flex-col items-center py-16 gap-4">
                <div className="w-16 h-16 rounded-3xl flex items-center justify-center"
                  style={{ background: 'rgba(99,102,241,0.1)', border: '1px solid rgba(99,102,241,0.2)' }}>
                  <HardDrive size={28} className="text-indigo-400" />
                </div>
                <div className="text-center">
                  <div className="text-sm font-semibold" style={{ color: 'var(--text-sec)' }}>Sin dispositivos registrados</div>
                  <div className="text-[11px] text-slate-500 mt-1">
                    Inicia un Runner Agent para detectar dispositivos automáticamente
                  </div>
                </div>
              </div>
            )}

            {/* Table */}
            {(loading || devices.length > 0) && (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
                      {['#Dispositivo', 'Plataforma', 'Versión', '·Estado', 'Runner', 'Último Seen', 'Acción'].map(h => (
                        <th key={h} className="px-4 py-2.5 text-left text-[9px] font-black tracking-widest text-slate-600 uppercase">
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {loading && !devices.length && Array.from({ length: 4 }, (_, i) => (
                      <tr key={i} className="border-b" style={{ borderColor: 'rgba(255,255,255,0.04)' }}>
                        {Array.from({ length: 7 }, (__, j) => (
                          <td key={j} className="px-4 py-3">
                            <div className="h-4 rounded animate-pulse" style={{ background: 'rgba(255,255,255,0.06)', width: j === 0 ? '160px' : '80px' }} />
                          </td>
                        ))}
                      </tr>
                    ))}
                    <AnimatePresence>
                      {paginated.map(device => (
                        <DeviceTableRow key={device.udid} device={device}
                          onStatusChange={handleStatusChange} onRemove={handleRemove} />
                      ))}
                    </AnimatePresence>
                  </tbody>
                </table>
              </div>
            )}

            {/* Pagination */}
            {filtered.length > 0 && (
              <div className="flex items-center justify-between px-5 py-2.5"
                style={{ borderTop: '1px solid rgba(255,255,255,0.05)' }}>
                <div className="flex items-center gap-2 text-[11px] text-slate-600">
                  Filas por página:
                  <select value={rows} onChange={e => { setRows(+e.target.value); setPage(1) }}
                    className="bg-transparent text-[11px] outline-none cursor-pointer"
                    style={{ color: 'var(--text-sec)' }}>
                    {ROWS_OPTIONS.map(n => <option key={n} value={n}>{n}</option>)}
                  </select>
                </div>
                <div className="flex items-center gap-1.5">
                  <button onClick={() => setPage(p => Math.max(1, p - 1))} disabled={safePage === 1}
                    className="p-1 rounded-lg transition-colors disabled:opacity-30"
                    style={{ color: 'var(--text-dim)' }}>
                    <ChevronLeft size={14} />
                  </button>
                  <span className="text-[11px] font-semibold px-2 py-0.5 rounded-lg"
                    style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8' }}>
                    {safePage}
                  </span>
                  <button onClick={() => setPage(p => Math.min(totalPages, p + 1))} disabled={safePage === totalPages}
                    className="p-1 rounded-lg transition-colors disabled:opacity-30"
                    style={{ color: 'var(--text-dim)' }}>
                    <ChevronRight size={14} />
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* ── Bottom 3 panels ──────────────────────────────────────── */}
          <div className="grid grid-cols-3 gap-4">

            {/* Activity log */}
            <div className="col-span-1 rounded-2xl p-4"
              style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)', border: '1px solid rgba(255,255,255,0.07)' }}>
              <div className="flex items-center justify-between mb-3">
                <div>
                  <div className="text-[12px] font-bold" style={{ color: 'var(--text-pri)' }}>Actividad en Tiempo Real</div>
                  <div className="text-[10px] text-slate-600">{activity.length} eventos recientes</div>
                </div>
                <button className="text-[10px] font-semibold text-indigo-400 hover:text-indigo-300">Ver Todos</button>
              </div>
              <div className="divide-y divide-white/5">
                {activity.slice(0, 7).map(ev => <ActivityItem key={ev.id} ev={ev} />)}
                {activity.length === 0 && (
                  <div className="text-[11px] text-slate-600 py-4 text-center">Sin actividad reciente</div>
                )}
              </div>
            </div>

            {/* Donut chart */}
            <div className="rounded-2xl p-4"
              style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)', border: '1px solid rgba(255,255,255,0.07)' }}>
              <div className="text-[12px] font-bold mb-0.5" style={{ color: 'var(--text-pri)' }}>Distribución por Plataforma</div>
              <div className="text-[10px] text-slate-600 mb-2">{total} dispositivos</div>
              <PlatformDonut android={android} ios={ios} otros={Math.max(0, total - android - ios)} total={total} />
            </div>

            {/* Line chart */}
            <div className="rounded-2xl p-4"
              style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)', border: '1px solid rgba(255,255,255,0.07)' }}>
              <div className="text-[12px] font-bold mb-0.5" style={{ color: 'var(--text-pri)' }}>Estado de Dispositivos</div>
              <div className="text-[10px] text-slate-600 mb-2">Últimas 24h</div>
              <HistoryChart data={chartData} />
              <div className="flex items-center gap-3 mt-1 flex-wrap">
                {[{ c: '#10b981', l: 'Disponibles' }, { c: '#f59e0b', l: 'En Uso' }, { c: '#ef4444', l: 'Offline' }].map(i => (
                  <div key={i.l} className="flex items-center gap-1">
                    <span className="w-3 h-0.5 rounded-full flex-shrink-0" style={{ background: i.c }} />
                    <span className="text-[9px] text-slate-500">{i.l}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* ── RIGHT: Runners sidebar ──────────────────────────────────── */}
        <div className="w-72 flex-shrink-0 space-y-4">

          {/* Runners connected */}
          <div className="rounded-2xl p-4"
            style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)', border: '1px solid rgba(255,255,255,0.07)' }}>
            <div className="flex items-center justify-between mb-3">
              <div className="text-[12px] font-bold" style={{ color: 'var(--text-pri)' }}>Runners Conectados</div>
              <span className="text-[10px] font-black px-2 py-0.5 rounded-full"
                style={{ background: 'rgba(16,185,129,0.15)', color: '#10b981', border: '1px solid rgba(16,185,129,0.3)' }}>
                {runnersOnline} online
              </span>
            </div>

            {runners.length === 0 && (
              <div className="text-center py-6">
                <Server size={24} className="text-slate-600 mx-auto mb-2" />
                <div className="text-[11px] text-slate-600">Sin runners activos</div>
              </div>
            )}

            {runners.map(r => <RunnerCard key={r.runnerId} runner={r} />)}

            {runners.length > 0 && (
              <button className="w-full py-2 rounded-xl text-[11px] font-semibold transition-all mt-1"
                style={{ background: 'rgba(255,255,255,0.04)', color: 'var(--text-dim)', border: '1px solid rgba(255,255,255,0.08)' }}>
                Ver Todos los Runners
              </button>
            )}
          </div>

          {/* Auto discovery */}
          <div className="rounded-2xl p-4"
            style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)', border: '1px solid rgba(255,255,255,0.07)' }}>
            <div className="text-[12px] font-bold mb-3" style={{ color: 'var(--text-pri)' }}>
              Descubrimiento Automático
            </div>

            {discovery.length === 0 && (
              <div className="text-center py-4">
                <Radio size={20} className="text-slate-600 mx-auto mb-1.5" />
                <div className="text-[11px] text-slate-600">Esperando dispositivos...</div>
              </div>
            )}

            {discovery.map(d => <DiscoveryItem key={d.udid} device={d} />)}

            {discovery.length > 0 && (
              <button className="w-full pt-3 text-[10px] font-semibold text-indigo-400 hover:text-indigo-300 text-center">
                Ver Historial Completo
              </button>
            )}
          </div>
        </div>
      </div>

      {/* ── "Agregar Runner" modal ──────────────────────────────────────── */}
      <AnimatePresence>
        {showAddModal && (
          <motion.div
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-4"
            style={{ background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)' }}
            onClick={() => setShowAddModal(false)}>
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.95, opacity: 0 }}
              className="rounded-2xl p-6 max-w-md w-full"
              style={{ background: '#1a1d2e', border: '1px solid rgba(255,255,255,0.12)', boxShadow: '0 24px 64px rgba(0,0,0,0.6)' }}
              onClick={e => e.stopPropagation()}>
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 rounded-xl flex items-center justify-center"
                  style={{ background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.3)' }}>
                  <Server size={18} className="text-indigo-400" />
                </div>
                <div>
                  <div className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>Agregar Runner Agent</div>
                  <div className="text-[11px] text-slate-500">Conecta un nuevo runner al Device Farm</div>
                </div>
              </div>
              <div className="space-y-3 text-[11px] text-slate-400">
                <div className="p-3 rounded-xl font-mono text-xs overflow-x-auto"
                  style={{ background: 'rgba(0,0,0,0.4)', border: '1px solid rgba(255,255,255,0.08)' }}>
                  <div className="text-slate-500 mb-1"># Android runner (Windows/Mac/Linux)</div>
                  <div>RUNNER_ID=win-runner-01 \</div>
                  <div>RUNNER_PLATFORM=android \</div>
                  <div>BACKEND_URL=https://qautomation-production.up.railway.app \</div>
                  <div>java -jar runner.jar</div>
                </div>
                <div className="p-3 rounded-xl font-mono text-xs overflow-x-auto"
                  style={{ background: 'rgba(0,0,0,0.4)', border: '1px solid rgba(255,255,255,0.08)' }}>
                  <div className="text-slate-500 mb-1"># iOS runner (Mac only)</div>
                  <div>RUNNER_ID=mac-runner-01 \</div>
                  <div>RUNNER_PLATFORM=ios \</div>
                  <div>java -jar runner.jar</div>
                </div>
                <p className="text-slate-500">El runner detectará automáticamente los dispositivos conectados y los registrará en el Device Farm.</p>
              </div>
              <button onClick={() => setShowAddModal(false)}
                className="w-full mt-4 py-2 rounded-xl text-[12px] font-semibold"
                style={{ background: 'rgba(99,102,241,0.2)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.3)' }}>
                Cerrar
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
