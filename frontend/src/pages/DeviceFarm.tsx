import React, { useState, useEffect, useCallback, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  ResponsiveContainer, PieChart, Pie, Cell,
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
} from 'recharts'
import {
  Smartphone, RefreshCw, Search, WifiOff, Activity,
  AlertCircle, Monitor, Apple, Clock, CheckCircle2,
  Server, ChevronLeft, ChevronRight, ChevronDown,
  MoreHorizontal, Zap, HardDrive, BatteryMedium,
  BatteryLow, BatteryFull, Download, ShieldAlert,
  ScanLine, Loader2, Stethoscope, Package, ArrowRight,
  Terminal, Copy, Check, CheckCircle, XCircle, Wifi,
} from 'lucide-react'
import { getDevices, getRunners, updateDeviceStatus, removeDevice } from '../api'
import type { PhysicalDevice, DeviceStatus, Runner } from '../types'
import { detectOs, type OsType } from '../hooks/useOs'

// ─── Types ───────────────────────────────────────────────────────────────────

interface ActivityEvent {
  id:       string
  time:     string
  type:     'device_connected' | 'runner_heartbeat' | 'sync' | 'info'
  title:    string
  subtitle: string
}

type InfraState = 'loading' | 'not_installed' | 'offline' | 'scanning' | 'ready'

// ─── Constants ────────────────────────────────────────────────────────────────

const STATUS_CFG: Record<DeviceStatus, { label: string; color: string; bg: string }> = {
  AVAILABLE:   { label: 'Disponible',    color: '#10b981', bg: 'rgba(16,185,129,0.15)' },
  BUSY:        { label: 'En Uso',        color: '#f59e0b', bg: 'rgba(245,158,11,0.15)' },
  OFFLINE:     { label: 'Sin conexión',  color: '#6b7280', bg: 'rgba(107,114,128,0.15)' },
  MAINTENANCE: { label: 'Mantenimiento', color: '#8b5cf6', bg: 'rgba(139,92,246,0.15)' },
}

const PIE_COLORS = ['#10b981', '#818cf8', '#6b7280']

// ─── Helpers ──────────────────────────────────────────────────────────────────

function timeAgo(iso: string | null | undefined): string {
  if (!iso) return '—'
  const diff = Math.floor((Date.now() - new Date(iso).getTime()) / 1000)
  if (diff < 5)    return 'hace unos segundos'
  if (diff < 60)   return `hace ${diff} segundos`
  if (diff < 3600) return `hace ${Math.floor(diff / 60)} minutos`
  return `hace ${Math.floor(diff / 3600)}h`
}

function fmtClock(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function pct(n: number, total: number) {
  return total === 0 ? '0.0' : ((n / total) * 100).toFixed(1)
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
  if (os === 'MACOS')  return 'macOS'
  if (os === 'LINUX')  return 'Linux'
  return 'Windows'
}

function generateActivity(devices: PhysicalDevice[], runners: Runner[]): ActivityEvent[] {
  const events: ActivityEvent[] = []
  devices.forEach((d, i) => {
    if (d.lastSeen) events.push({
      id: `d-${i}`, time: d.lastSeen, type: 'device_connected',
      title: 'Dispositivo conectado',
      subtitle: `${d.deviceName ?? d.udid} · ${d.runnerId ?? '—'}`,
    })
  })
  runners.forEach((r, i) => {
    if (r.lastSeen && r.status !== 'OFFLINE') events.push({
      id: `r-${i}`, time: r.lastSeen, type: 'runner_heartbeat',
      title: 'Runner heartbeat', subtitle: r.runnerId,
    })
  })
  events.push({
    id: 'sync', time: new Date(Date.now() - 30_000).toISOString(), type: 'sync',
    title: 'Sincronización completada',
    subtitle: `${devices.length} dispositivo${devices.length !== 1 ? 's' : ''} sincronizado${devices.length !== 1 ? 's' : ''}`,
  })
  return events.sort((a, b) => new Date(b.time).getTime() - new Date(a.time).getTime()).slice(0, 10)
}

function generate24h(connected: number, disconnected: number) {
  const now = new Date()
  return Array.from({ length: 13 }, (_, i) => {
    const h = (now.getHours() - 24 + i * 2 + 24) % 24
    const t = i / 12
    return {
      time:          `${h.toString().padStart(2, '0')}:00`,
      conectados:    Math.max(0, Math.round(connected    + Math.sin(t * Math.PI * 3) * 1.2)),
      desconectados: Math.max(0, Math.round(disconnected + Math.sin(t * Math.PI * 2 + 1) * 0.5)),
    }
  })
}

function generateRunnerStats(runnerId: string) {
  const { cpu, mem } = fakeRunnerStats(runnerId)
  const now = new Date()
  return Array.from({ length: 13 }, (_, i) => {
    const h = (now.getHours() - 24 + i * 2 + 24) % 24
    const t = i / 12
    return {
      time: `${h.toString().padStart(2, '0')}:00`,
      cpu:  Math.max(2, Math.round(cpu + Math.sin(t * Math.PI * 4) * 6)),
      mem:  Math.max(10, Math.round(mem + Math.sin(t * Math.PI * 2 + 0.5) * 8)),
    }
  })
}

// ─── CopyButton ───────────────────────────────────────────────────────────────

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false)
  const copy = () => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }
  return (
    <button onClick={copy} title="Copiar"
      className="p-1 rounded transition-colors flex-shrink-0"
      style={{ color: copied ? '#10b981' : '#6b7280' }}>
      {copied ? <Check size={11} /> : <Copy size={11} />}
    </button>
  )
}

// ─── OS Step row (in modal) ───────────────────────────────────────────────────

function StepRow({ n, text, code }: { n: number; text: string; code?: string }) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center gap-2">
        <span className="w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-black flex-shrink-0"
          style={{ background: 'rgba(99,102,241,0.2)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.3)' }}>
          {n}
        </span>
        <span className="text-[12px]" style={{ color: 'var(--text-sec)' }}>{text}</span>
      </div>
      {code && (
        <div className="ml-7 flex items-center gap-2 px-3 py-1.5 rounded-lg"
          style={{ background: 'rgba(0,0,0,0.5)', border: '1px solid rgba(255,255,255,0.08)' }}>
          <span className="flex-1 text-[11px] font-mono text-emerald-300">{code}</span>
          <CopyButton text={code} />
        </div>
      )}
    </div>
  )
}

// ─── Download / Install Modal ─────────────────────────────────────────────────

interface InstallModalProps {
  infraState:   InfraState
  runners:      Runner[]
  onClose:      () => void
}

function InstallModal({ infraState, runners, onClose }: InstallModalProps) {
  const os          = detectOs()
  const [tab, setTab] = useState<OsType>(os === 'unknown' ? 'windows' : os)
  const offlineRunner = runners.find(r => r.status === 'OFFLINE')

  const tabs: { id: OsType; label: string; icon: React.ReactNode }[] = [
    { id: 'windows', label: 'Windows', icon: <Monitor size={13} /> },
    { id: 'macos',   label: 'macOS',   icon: <Apple size={13} />   },
  ]

  return (
    <motion.div
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ background: 'rgba(0,0,0,0.8)', backdropFilter: 'blur(8px)' }}
      onClick={onClose}>
      <motion.div
        initial={{ scale: 0.95, opacity: 0, y: 12 }}
        animate={{ scale: 1,    opacity: 1, y: 0  }}
        exit={{   scale: 0.95, opacity: 0, y: 8  }}
        transition={{ type: 'spring', stiffness: 300, damping: 28 }}
        className="rounded-3xl overflow-hidden max-w-xl w-full"
        style={{ background: '#10131f', border: '1px solid rgba(255,255,255,0.1)', boxShadow: '0 32px 80px rgba(0,0,0,0.8)' }}
        onClick={e => e.stopPropagation()}>

        {/* ── Modal header ── */}
        <div className="px-6 pt-6 pb-4"
          style={{ borderBottom: '1px solid rgba(255,255,255,0.07)' }}>
          <div className="flex items-start gap-3">
            <div className="w-12 h-12 rounded-2xl flex items-center justify-center flex-shrink-0"
              style={{ background: infraState === 'not_installed' ? 'rgba(99,102,241,0.15)' : 'rgba(239,68,68,0.12)', border: `1px solid ${infraState === 'not_installed' ? 'rgba(99,102,241,0.3)' : 'rgba(239,68,68,0.25)'}` }}>
              {infraState === 'not_installed'
                ? <Package size={22} className="text-indigo-400" />
                : infraState === 'offline'
                  ? <ShieldAlert size={22} className="text-red-400" />
                  : <Stethoscope size={22} className="text-amber-400" />}
            </div>
            <div>
              <h2 className="text-[15px] font-black" style={{ color: 'var(--text-pri)' }}>
                {infraState === 'not_installed' ? 'Instalación del Runner Service'
                  : infraState === 'offline'    ? 'Runner Offline — Diagnóstico'
                  : 'Diagnóstico de Dispositivos'}
              </h2>
              <p className="text-[11px] text-slate-500 mt-0.5">
                {infraState === 'not_installed'
                  ? 'Instalación única — el Runner arrancará automáticamente desde ahora'
                  : infraState === 'offline' && offlineRunner
                  ? `Último heartbeat de ${offlineRunner.runnerId}: ${timeAgo(offlineRunner.lastSeen)}`
                  : 'El Runner está online pero no detecta dispositivos'}
              </p>
            </div>
            <button onClick={onClose} className="ml-auto p-2 rounded-xl hover:bg-white/5 flex-shrink-0"
              style={{ color: 'var(--text-dim)' }}>
              <XCircle size={16} />
            </button>
          </div>
        </div>

        <div className="px-6 py-5 space-y-5 max-h-[70vh] overflow-y-auto">

          {/* ── NOT INSTALLED: Installation guide ── */}
          {infraState === 'not_installed' && (
            <>
              {/* Alert */}
              <div className="flex items-start gap-3 rounded-2xl p-4"
                style={{ background: 'rgba(99,102,241,0.07)', border: '1px solid rgba(99,102,241,0.15)' }}>
                <Package size={16} className="text-indigo-400 flex-shrink-0 mt-0.5" />
                <div className="text-[11px] text-slate-400 leading-relaxed">
                  <span className="font-semibold text-slate-300">Automation QA Runner</span> no está instalado en este equipo.
                  La instalación tarda menos de 2 minutos y es permanente — no tendrás que hacer nada más.
                </div>
              </div>

              {/* OS tabs */}
              <div>
                <div className="flex gap-1 mb-4 p-1 rounded-xl w-fit"
                  style={{ background: 'rgba(255,255,255,0.05)' }}>
                  {tabs.map(t => (
                    <button key={t.id} onClick={() => setTab(t.id)}
                      className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-[11px] font-semibold transition-all"
                      style={{
                        background: tab === t.id ? 'rgba(99,102,241,0.25)' : 'transparent',
                        color:      tab === t.id ? '#818cf8' : 'var(--text-dim)',
                        border:     tab === t.id ? '1px solid rgba(99,102,241,0.35)' : '1px solid transparent',
                      }}>
                      {t.icon}{t.label}
                      {os === t.id && (
                        <span className="text-[8px] font-black px-1 rounded"
                          style={{ background: 'rgba(16,185,129,0.2)', color: '#10b981' }}>
                          TU OS
                        </span>
                      )}
                    </button>
                  ))}
                </div>

                {/* Windows steps */}
                {tab === 'windows' && (
                  <div className="space-y-3">
                    <StepRow n={1} text="Clona o descarga el repositorio del proyecto (si aún no lo tienes)" />
                    <StepRow n={2} text="Asegúrate de tener Java 17+ instalado"
                      code="java -version" />
                    <StepRow n={3} text="Ejecuta el instalador (doble clic o desde CMD):"
                      code="runner\service\windows\install-service.bat" />
                    <StepRow n={4} text="Cuando pregunte '¿Iniciar ahora?', responde S" />
                    <div className="flex items-start gap-2 px-3 py-2 rounded-xl text-[10px] text-slate-500"
                      style={{ background: 'rgba(16,185,129,0.06)', border: '1px solid rgba(16,185,129,0.12)' }}>
                      <CheckCircle size={12} className="text-emerald-500 flex-shrink-0 mt-0.5" />
                      El servicio se registra como Tarea Programada. Al reiniciar Windows, arrancará automáticamente — sin CMD, sin scripts manuales.
                    </div>
                  </div>
                )}

                {/* macOS steps */}
                {tab === 'macos' && (
                  <div className="space-y-3">
                    <StepRow n={1} text="Clona o descarga el repositorio (si aún no lo tienes)" />
                    <StepRow n={2} text="Asegúrate de tener Java 17+ instalado"
                      code="java -version" />
                    <StepRow n={3} text="Ejecuta el instalador (una sola vez):"
                      code="bash runner/install.sh" />
                    <div className="flex items-start gap-2 px-3 py-2 rounded-xl text-[10px] text-slate-500"
                      style={{ background: 'rgba(16,185,129,0.06)', border: '1px solid rgba(16,185,129,0.12)' }}>
                      <CheckCircle size={12} className="text-emerald-500 flex-shrink-0 mt-0.5" />
                      Se instala como LaunchAgent. Arranca automáticamente al iniciar sesión.
                      En Mac con Xcode detecta Android + iOS. Sin configuración adicional.
                    </div>
                  </div>
                )}
              </div>

              {/* What happens next */}
              <div className="rounded-2xl p-4"
                style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)' }}>
                <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase mb-3">Después de instalar</div>
                <div className="space-y-2">
                  {[
                    'El servicio inicia automáticamente',
                    'Detecta dispositivos Android/iOS conectados por USB',
                    'Se registra en el Dashboard en ~15 segundos',
                    'Arranca solo en cada inicio de Windows o macOS',
                  ].map((item, i) => (
                    <div key={i} className="flex items-center gap-2 text-[11px] text-slate-400">
                      <ArrowRight size={11} className="text-indigo-400 flex-shrink-0" />
                      {item}
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}

          {/* ── OFFLINE: Recovery guide ── */}
          {infraState === 'offline' && (
            <>
              <div className="flex items-start gap-3 rounded-2xl p-4"
                style={{ background: 'rgba(239,68,68,0.07)', border: '1px solid rgba(239,68,68,0.15)' }}>
                <ShieldAlert size={16} className="text-red-400 flex-shrink-0 mt-0.5" />
                <div className="text-[11px] text-red-300 leading-relaxed">
                  El Runner Service estaba funcionando pero ha perdido la conexión. Puede que el equipo se haya apagado o el servicio se haya detenido por un error.
                </div>
              </div>

              <div>
                <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase mb-3">Cómo recuperar</div>
                <div className="space-y-3">
                  <div className="rounded-2xl overflow-hidden" style={{ border: '1px solid rgba(255,255,255,0.07)' }}>
                    <div className="flex items-center gap-2 px-4 py-2"
                      style={{ background: 'rgba(96,165,250,0.07)', borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
                      <Monitor size={12} className="text-blue-400" />
                      <span className="text-[10px] font-bold text-blue-400">Windows — reiniciar servicio</span>
                    </div>
                    <div className="p-3 space-y-2" style={{ background: 'rgba(0,0,0,0.3)' }}>
                      <div className="text-[10px] text-slate-600">Opción 1: reinicia el equipo (el servicio arranca automáticamente)</div>
                      <div className="text-[10px] text-slate-600">Opción 2: abre el Programador de Tareas → "Automation QA Runner" → Ejecutar</div>
                    </div>
                  </div>
                  <div className="rounded-2xl overflow-hidden" style={{ border: '1px solid rgba(255,255,255,0.07)' }}>
                    <div className="flex items-center gap-2 px-4 py-2"
                      style={{ background: 'rgba(129,140,248,0.07)', borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
                      <Apple size={12} className="text-indigo-400" />
                      <span className="text-[10px] font-bold text-indigo-400">macOS — reiniciar LaunchAgent</span>
                    </div>
                    <div className="p-3" style={{ background: 'rgba(0,0,0,0.3)' }}>
                      <div className="flex items-center gap-2 py-0.5">
                        <span className="text-[11px] font-mono text-emerald-300 flex-1">
                          launchctl kickstart -k gui/$(id -u)/com.automationqa.runner
                        </span>
                        <CopyButton text="launchctl kickstart -k gui/$(id -u)/com.automationqa.runner" />
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              {/* Runner status list */}
              {runners.length > 0 && (
                <div>
                  <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase mb-2">Estado de Runners</div>
                  <div className="space-y-2">
                    {runners.map(r => (
                      <div key={r.runnerId} className="flex items-center gap-3 px-3 py-2 rounded-xl"
                        style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' }}>
                        <span className="w-2 h-2 rounded-full flex-shrink-0"
                          style={{ background: r.status === 'OFFLINE' ? '#6b7280' : '#10b981' }} />
                        <span className="text-[11px] font-semibold flex-1" style={{ color: 'var(--text-sec)' }}>
                          {r.runnerId}
                        </span>
                        <span className="text-[10px] text-slate-600">{timeAgo(r.lastSeen)}</span>
                        <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full"
                          style={{ background: r.status === 'OFFLINE' ? 'rgba(107,114,128,0.2)' : 'rgba(16,185,129,0.2)', color: r.status === 'OFFLINE' ? '#9ca3af' : '#10b981' }}>
                          {r.status}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </>
          )}

          {/* ── SCANNING: USB checklist ── */}
          {infraState === 'scanning' && (
            <>
              <div className="flex items-start gap-3 rounded-2xl p-4"
                style={{ background: 'rgba(245,158,11,0.07)', border: '1px solid rgba(245,158,11,0.15)' }}>
                <ScanLine size={16} className="text-amber-400 flex-shrink-0 mt-0.5" />
                <div className="text-[11px] text-amber-300 leading-relaxed">
                  El Runner está online pero no detecta ningún dispositivo. Verifica la conexión USB.
                </div>
              </div>
              <div>
                <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase mb-3">Checklist de diagnóstico</div>
                <div className="space-y-2">
                  {[
                    { check: 'Cable USB conectado físicamente al equipo del Runner' },
                    { check: 'Depuración USB habilitada (Configuración → Opciones de Desarrollador)' },
                    { check: 'Pantalla desbloqueada — acepta "Confiar en esta computadora"' },
                    { check: 'Android: ejecuta "adb devices" para verificar que el dispositivo aparece', note: 'android' },
                    { check: 'iOS: dispositivo trust aprobado en Ajustes → General → VPN y Administración', note: 'ios' },
                    { check: 'Driver USB instalado en Windows (Android: Universal ADB, iOS: Apple Mobile Device Support)', note: 'windows' },
                  ].map((item, i) => (
                    <div key={i} className="flex items-start gap-2.5 text-[11px] text-slate-400">
                      <span className="w-4 h-4 rounded flex items-center justify-center flex-shrink-0 mt-0.5"
                        style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)', fontSize: 9 }}>
                        {i + 1}
                      </span>
                      <span>{item.check}</span>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>

        <div className="px-6 pb-6">
          <button onClick={onClose}
            className="w-full py-2.5 rounded-xl text-[12px] font-semibold"
            style={{ background: 'rgba(99,102,241,0.2)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.3)' }}>
            Cerrar
          </button>
        </div>
      </motion.div>
    </motion.div>
  )
}

// ─── Enterprise empty states ───────────────────────────────────────────────────

function InfrastructureSetupRequired({ onInstall }: { onInstall: () => void }) {
  const os = detectOs()
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }}
      className="flex flex-col items-center py-12 gap-5">

      {/* Icon */}
      <div className="relative">
        <div className="w-20 h-20 rounded-3xl flex items-center justify-center"
          style={{ background: 'rgba(99,102,241,0.1)', border: '1px solid rgba(99,102,241,0.2)' }}>
          <Package size={36} className="text-indigo-400" />
        </div>
        <div className="absolute -bottom-1 -right-1 w-7 h-7 rounded-full flex items-center justify-center"
          style={{ background: '#10131f', border: '2px solid rgba(239,68,68,0.5)' }}>
          <XCircle size={16} className="text-red-400" />
        </div>
      </div>

      {/* Text */}
      <div className="text-center max-w-sm">
        <div className="text-[16px] font-black mb-1" style={{ color: 'var(--text-pri)' }}>
          Automation QA Runner no está instalado
        </div>
        <div className="text-[12px] text-slate-500 leading-relaxed">
          La infraestructura local aún no ha sido configurada.
          Instala el Runner una sola vez para habilitar la detección
          automática de dispositivos Android e iOS.
        </div>
      </div>

      {/* Features preview */}
      <div className="grid grid-cols-3 gap-3 w-full max-w-sm">
        {[
          { icon: <Monitor size={16} />, label: 'Android', sub: 'Detección automática', color: '#10b981' },
          { icon: <Apple size={16} />,   label: 'iOS',     sub: os === 'macos' ? 'Auto en macOS' : 'Solo en macOS', color: '#818cf8' },
          { icon: <Wifi size={16} />,    label: 'Online',  sub: 'Sin intervención', color: '#6366f1' },
        ].map(f => (
          <div key={f.label} className="rounded-xl p-3 text-center"
            style={{ background: `${f.color}08`, border: `1px solid ${f.color}18` }}>
            <span style={{ color: f.color }} className="flex justify-center mb-1">{f.icon}</span>
            <div className="text-[11px] font-bold" style={{ color: 'var(--text-sec)' }}>{f.label}</div>
            <div className="text-[9px] text-slate-600">{f.sub}</div>
          </div>
        ))}
      </div>

      {/* CTA */}
      <button onClick={onInstall}
        className="flex items-center gap-2 px-6 py-3 rounded-2xl text-[13px] font-black transition-all hover:opacity-90"
        style={{ background: 'linear-gradient(135deg, #6366f1, #7c3aed)', color: '#fff', boxShadow: '0 8px 24px rgba(99,102,241,0.35)' }}>
        <Download size={15} />
        Descargar e Instalar Runner
      </button>
      <div className="text-[10px] text-slate-600">
        Instalación única · Auto-arranca con {os === 'macos' ? 'macOS' : 'Windows'} · Sin mantenimiento manual
      </div>
    </motion.div>
  )
}

function RunnerOfflineCard({ runners, onDiag }: { runners: Runner[]; onDiag: () => void }) {
  const lastSeen = runners.reduce((latest, r) => {
    if (!r.lastSeen) return latest
    if (!latest || new Date(r.lastSeen) > new Date(latest)) return r.lastSeen
    return latest
  }, null as string | null)

  return (
    <div className="flex flex-col items-center py-10 gap-4">
      <div className="relative">
        <div className="w-16 h-16 rounded-2xl flex items-center justify-center"
          style={{ background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)' }}>
          <ShieldAlert size={28} className="text-red-400" />
        </div>
      </div>
      <div className="text-center">
        <div className="text-[14px] font-black text-red-400">Runner Offline</div>
        {lastSeen && (
          <div className="text-[11px] text-slate-500 mt-1">
            Último heartbeat: <span className="text-slate-400">{timeAgo(lastSeen)}</span>
          </div>
        )}
        <div className="text-[11px] text-slate-600 mt-1 max-w-xs">
          El servicio se instaló previamente pero está detenido. Reinicia el equipo o el servicio.
        </div>
      </div>
      <div className="flex gap-2">
        <button onClick={onDiag}
          className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-[11px] font-semibold"
          style={{ background: 'rgba(239,68,68,0.12)', color: '#ef4444', border: '1px solid rgba(239,68,68,0.25)' }}>
          <Stethoscope size={12} />
          Diagnosticar
        </button>
      </div>
    </div>
  )
}

function DeviceScanningCard() {
  return (
    <div className="flex flex-col items-center py-10 gap-3">
      <div className="w-16 h-16 rounded-2xl flex items-center justify-center"
        style={{ background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.2)' }}>
        <ScanLine size={28} className="text-emerald-400 animate-pulse" />
      </div>
      <div className="text-center">
        <div className="flex items-center gap-2 justify-center mb-0.5">
          <Loader2 size={12} className="text-emerald-400 animate-spin" />
          <span className="text-[13px] font-bold text-emerald-400">Runner conectado · Escaneando</span>
        </div>
        <div className="text-[11px] text-slate-500 max-w-xs leading-relaxed">
          Conecta un <span className="text-slate-300 font-semibold">Android</span> o{' '}
          <span className="text-slate-300 font-semibold">iPhone</span> por USB.
          El dispositivo aparecerá automáticamente en el Dashboard.
        </div>
        <div className="flex items-center justify-center gap-3 mt-2.5 text-[10px] text-slate-600">
          <span>Android: Depuración USB</span>
          <span className="text-slate-700">·</span>
          <span>iOS: Confiar en equipo</span>
        </div>
      </div>
    </div>
  )
}

// ─── StatusBadge ──────────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: DeviceStatus }) {
  const cfg = STATUS_CFG[status] ?? STATUS_CFG.OFFLINE
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

function BatteryChip({ pct: p }: { pct: number }) {
  const color = p > 60 ? '#10b981' : p > 30 ? '#f59e0b' : '#ef4444'
  const Icon  = p > 70 ? BatteryFull : p > 40 ? BatteryMedium : BatteryLow
  return (
    <div className="flex items-center gap-1">
      <Icon size={13} style={{ color }} />
      <span className="text-[11px] font-semibold" style={{ color }}>{p}%</span>
    </div>
  )
}

// ─── DeviceTableRow ───────────────────────────────────────────────────────────

function DeviceTableRow({
  device, runners, onStatusChange, onRemove,
}: {
  device:  PhysicalDevice
  runners: Runner[]
  onStatusChange: (udid: string, s: DeviceStatus) => void
  onRemove: (udid: string) => void
}) {
  const [menu, setMenu] = useState(false)
  const isIos   = device.platform === 'IOS'
  const battery = fakeBattery(device.udid)
  const runner  = runners.find(r => r.runnerId === device.runnerId)
  const rOs     = runner ? osDisplayLabel(resolveOs(runner)) : (device.runnerId?.toLowerCase().includes('mac') ? 'macOS' : 'Windows')

  return (
    <tr className="border-b transition-all duration-150 group"
      style={{ borderColor: 'rgba(255,255,255,0.04)' }}
      onMouseEnter={e => { (e.currentTarget as HTMLTableRowElement).style.background = 'rgba(255,255,255,0.025)' }}
      onMouseLeave={e => { (e.currentTarget as HTMLTableRowElement).style.background = 'transparent' }}>

      <td className="px-4 py-3">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ background: isIos ? 'rgba(129,140,248,0.12)' : 'rgba(16,185,129,0.12)', border: `1px solid ${isIos ? 'rgba(129,140,248,0.25)' : 'rgba(16,185,129,0.25)'}` }}>
            <Smartphone size={16} style={{ color: isIos ? '#818cf8' : '#10b981' }} />
          </div>
          <div>
            <div className="text-[12px] font-semibold" style={{ color: 'var(--text-pri)' }}>
              {device.deviceName ?? device.model ?? 'Desconocido'}
            </div>
            <div className="text-[10px] font-mono text-slate-600 mt-0.5 max-w-[120px] truncate">{device.udid}</div>
          </div>
        </div>
      </td>

      <td className="px-4 py-3">
        <div className="flex items-center gap-1.5 px-2 py-1 rounded-lg w-fit"
          style={{ background: isIos ? 'rgba(129,140,248,0.1)' : 'rgba(16,185,129,0.1)', border: `1px solid ${isIos ? 'rgba(129,140,248,0.2)' : 'rgba(16,185,129,0.2)'}` }}>
          {isIos ? <Apple size={11} style={{ color: '#818cf8' }} /> : <Monitor size={11} style={{ color: '#10b981' }} />}
          <span className="text-[10px] font-bold" style={{ color: isIos ? '#818cf8' : '#10b981' }}>
            {isIos ? 'iOS' : 'Android'}
          </span>
        </div>
      </td>

      <td className="px-4 py-3">
        <div className="text-[11px] font-semibold" style={{ color: 'var(--text-sec)' }}>{device.runnerId ?? '—'}</div>
        <div className="text-[10px] text-slate-600">{rOs}</div>
      </td>

      <td className="px-4 py-3"><StatusBadge status={device.status} /></td>

      <td className="px-4 py-3"><BatteryChip pct={battery} /></td>

      <td className="px-4 py-3">
        <span className="text-[11px] font-mono text-slate-400">
          {device.platform === 'ANDROID' ? 'Android' : 'iOS'} {device.platformVersion ?? '—'}
        </span>
      </td>

      <td className="px-4 py-3">
        <span className="text-[11px] text-slate-500">{timeAgo(device.lastSeen)}</span>
      </td>

      <td className="px-3 py-3 relative">
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
                      className="w-full flex items-center gap-2 px-3 py-1.5 text-[11px] font-semibold text-left hover:bg-white/5"
                      style={{ color: cfg.color }}>
                      <span className="w-1.5 h-1.5 rounded-full flex-shrink-0" style={{ background: cfg.color }} />
                      Marcar como {cfg.label}
                    </button>
                  )
                })}
                <div className="border-t mx-2 my-1" style={{ borderColor: 'rgba(255,255,255,0.08)' }} />
                <button onClick={() => onRemove(device.udid)}
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
}

// ─── RunnerCardMain ───────────────────────────────────────────────────────────

function RunnerCardMain({ runner }: { runner: Runner }) {
  const isOnline    = runner.status !== 'OFFLINE'
  const statusColor = isOnline ? '#10b981' : '#6b7280'
  const statusLabel = isOnline ? 'ONLINE' : 'OFFLINE'
  const os          = resolveOs(runner)
  const isMac       = os === 'MACOS'
  const osTagColor  = isMac ? '#818cf8' : '#60a5fa'
  const stats       = fakeRunnerStats(runner.runnerId)
  const androidOk   = runner.androidSupported ?? true
  const iosOk       = runner.iosSupported ?? (runner.platform === 'ios')

  return (
    <div className="rounded-2xl overflow-hidden"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0.025) 100%)',
        border: `1px solid ${statusColor}22`, backdropFilter: 'blur(12px)',
      }}>
      <div className="flex items-center justify-between px-5 pt-4 pb-3"
        style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
        <div className="flex items-center gap-2.5">
          <span className="relative flex h-2 w-2">
            {isOnline && <span className="absolute inline-flex h-full w-full animate-ping rounded-full opacity-60" style={{ background: statusColor }} />}
            <span className="relative inline-flex h-2 w-2 rounded-full" style={{ background: statusColor }} />
          </span>
          <span className="text-[13px] font-black" style={{ color: 'var(--text-pri)' }}>{runner.runnerId}</span>
          <span className="text-[9px] font-black px-2 py-0.5 rounded-full"
            style={{ background: `${osTagColor}18`, color: osTagColor, border: `1px solid ${osTagColor}35` }}>
            {os}
          </span>
        </div>
        <span className="text-[10px] font-black px-2 py-0.5 rounded-full"
          style={{ background: `${statusColor}18`, color: statusColor, border: `1px solid ${statusColor}35` }}>
          {statusLabel}
        </span>
      </div>
      <div className="px-5 py-1.5 text-[11px] text-slate-500">
        Último heartbeat: <span className="text-slate-400">{timeAgo(runner.lastSeen)}</span>
      </div>
      <div className="flex px-5 pb-4">
        <div className="flex-1">
          <div className="text-[10px] font-black tracking-widest text-slate-600 uppercase mb-2">Capacidades</div>
          <div className="space-y-2">
            {[
              { icon: <Monitor size={12} />, label: 'Android', ok: androidOk, color: '#10b981' },
              { icon: <Apple size={12} />,   label: 'iOS',     ok: iosOk,     color: '#818cf8' },
            ].map(c => (
              <div key={c.label} className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-lg flex items-center justify-center flex-shrink-0"
                  style={{ background: c.ok ? `${c.color}12` : 'rgba(107,114,128,0.08)', border: `1px solid ${c.ok ? `${c.color}25` : 'rgba(107,114,128,0.15)'}` }}>
                  <span style={{ color: c.ok ? c.color : '#6b7280' }}>{c.icon}</span>
                </div>
                <div>
                  <div className="text-[11px] font-semibold" style={{ color: 'var(--text-sec)' }}>{c.label}</div>
                  <div className="text-[9px]" style={{ color: c.ok ? c.color : '#6b7280' }}>
                    {c.ok ? 'Soportado' : 'No soportado'}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
        <div className="w-px mx-4" style={{ background: 'rgba(255,255,255,0.06)' }} />
        <div className="flex-1 space-y-1.5">
          {[
            { label: 'Dispositivos', value: `${runner.devices?.length ?? 0}` },
            { label: 'CPU',    value: `${stats.cpu}%`, bar: stats.cpu, color: '#6366f1' },
            { label: 'Memoria', value: `${stats.mem}%`, bar: stats.mem, color: '#f59e0b' },
            { label: 'Versión', value: runner.version ?? '—' },
          ].map(s => (
            <div key={s.label} className="flex items-center justify-between gap-2">
              <span className="text-[10px] text-slate-600 w-20 flex-shrink-0">{s.label}</span>
              {s.bar !== undefined ? (
                <div className="flex items-center gap-2 flex-1">
                  <div className="flex-1 h-1 rounded-full" style={{ background: 'rgba(255,255,255,0.08)' }}>
                    <div className="h-1 rounded-full" style={{ width: `${s.bar}%`, background: s.color }} />
                  </div>
                  <span className="text-[11px] font-semibold w-8 text-right" style={{ color: 'var(--text-sec)' }}>{s.value}</span>
                </div>
              ) : (
                <span className="text-[11px] font-bold" style={{ color: 'var(--text-sec)' }}>{s.value}</span>
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

// ─── Charts ───────────────────────────────────────────────────────────────────

function PlatformDonutSidebar({ android, ios, total }: { android: number; ios: number; total: number }) {
  const data     = [{ name: 'Android', value: android || 0 }, { name: 'iOS', value: ios || 0 }].filter(d => d.value > 0)
  const fallback = data.length === 0 ? [{ name: 'Sin datos', value: 1 }] : data
  const colors   = data.length === 0 ? ['rgba(255,255,255,0.08)'] : PIE_COLORS
  const CenterLabel = ({ viewBox }: { viewBox?: { cx: number; cy: number } }) => {
    if (!viewBox) return null
    const { cx, cy } = viewBox
    return (
      <g>
        <text x={cx} y={cy - 6} textAnchor="middle" fill="var(--text-pri)" fontSize={24} fontWeight={900}>{total}</text>
        <text x={cx} y={cy + 10} textAnchor="middle" fill="#6b7280" fontSize={9} fontWeight={700} letterSpacing={1}>TOTAL</text>
      </g>
    )
  }
  return (
    <div>
      <ResponsiveContainer width="100%" height={148}>
        <PieChart>
          <Pie data={fallback} innerRadius={44} outerRadius={60} paddingAngle={3}
            dataKey="value" startAngle={90} endAngle={-270} label={false} labelLine={false}>
            {fallback.map((_, i) => <Cell key={i} fill={colors[i] ?? PIE_COLORS[i % PIE_COLORS.length]} />)}
            {/* @ts-ignore */}
            <CenterLabel />
          </Pie>
        </PieChart>
      </ResponsiveContainer>
      <div className="flex items-center justify-center gap-4 mt-0.5">
        {[{ label: 'Android', count: android, color: PIE_COLORS[0] }, { label: 'iOS', count: ios, color: PIE_COLORS[1] }].map(r => (
          <div key={r.label} className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full" style={{ background: r.color }} />
            <span className="text-[10px] text-slate-400">{r.label}: {r.count} ({pct(r.count, total)}%)</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function StatusDonut({ available, busy, offline, total }: { available: number; busy: number; offline: number; total: number }) {
  const avPct = total === 0 ? 0 : Math.round((available / total) * 100)
  const data = [{ name: 'Disponibles', value: available }, { name: 'En uso', value: busy }, { name: 'Offline', value: offline }].filter(d => d.value > 0)
  const fallback = data.length === 0 ? [{ name: 'Sin datos', value: 1 }] : data
  const colors   = data.length === 0 ? ['rgba(255,255,255,0.08)'] : ['#10b981', '#f59e0b', '#ef4444']
  const CenterLabel = ({ viewBox }: { viewBox?: { cx: number; cy: number } }) => {
    if (!viewBox) return null
    const { cx, cy } = viewBox
    return (
      <g>
        <text x={cx} y={cy - 8} textAnchor="middle" fill="#10b981" fontSize={22} fontWeight={900}>{avPct}%</text>
        <text x={cx} y={cy + 8} textAnchor="middle" fill="#6b7280" fontSize={9} fontWeight={700}>Disponibles</text>
      </g>
    )
  }
  return (
    <div>
      <ResponsiveContainer width="100%" height={138}>
        <PieChart>
          <Pie data={fallback} innerRadius={44} outerRadius={60} paddingAngle={2}
            dataKey="value" startAngle={90} endAngle={-270} label={false} labelLine={false}>
            {fallback.map((_, i) => <Cell key={i} fill={colors[i] ?? '#6b7280'} />)}
            {/* @ts-ignore */}
            <CenterLabel />
          </Pie>
        </PieChart>
      </ResponsiveContainer>
      <div className="space-y-1.5 mt-1">
        {[
          { label: 'Disponibles', count: available, pct: pct(available, total), color: '#10b981' },
          { label: 'En uso',      count: busy,      pct: pct(busy, total),      color: '#f59e0b' },
          { label: 'Offline',     count: offline,   pct: pct(offline, total),   color: '#ef4444' },
        ].map(r => (
          <div key={r.label} className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: r.color }} />
              <span className="text-[11px] text-slate-400">{r.label}</span>
            </div>
            <span className="text-[11px] font-semibold text-slate-300">{r.count} <span className="text-slate-600">({r.pct}%)</span></span>
          </div>
        ))}
      </div>
    </div>
  )
}

function HistorialChart({ data }: { data: ReturnType<typeof generate24h> }) {
  return (
    <ResponsiveContainer width="100%" height={128}>
      <LineChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: -22 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
        <XAxis dataKey="time" tick={{ fill: '#6b7280', fontSize: 8 }} tickLine={false} axisLine={false} interval={3} />
        <YAxis tick={{ fill: '#6b7280', fontSize: 8 }} tickLine={false} axisLine={false} domain={[0, 'dataMax + 1']} />
        <Tooltip contentStyle={{ background: '#1e2130', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, fontSize: 11 }} labelStyle={{ color: '#94a3b8' }} itemStyle={{ color: '#e2e8f0' }} />
        <Line type="monotone" dataKey="conectados"    stroke="#10b981" strokeWidth={2} dot={false} name="Conectados" />
        <Line type="monotone" dataKey="desconectados" stroke="#ef4444" strokeWidth={2} dot={false} name="Desconectados" />
      </LineChart>
    </ResponsiveContainer>
  )
}

function RunnerStatsChart({ data }: { data: ReturnType<typeof generateRunnerStats> }) {
  return (
    <ResponsiveContainer width="100%" height={128}>
      <LineChart data={data} margin={{ top: 4, right: 4, bottom: 0, left: -22 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.06)" />
        <XAxis dataKey="time" tick={{ fill: '#6b7280', fontSize: 8 }} tickLine={false} axisLine={false} interval={3} />
        <YAxis tick={{ fill: '#6b7280', fontSize: 8 }} tickLine={false} axisLine={false} domain={[0, 100]} />
        <Tooltip contentStyle={{ background: '#1e2130', border: '1px solid rgba(255,255,255,0.1)', borderRadius: 8, fontSize: 11 }} labelStyle={{ color: '#94a3b8' }} itemStyle={{ color: '#e2e8f0' }} />
        <Line type="monotone" dataKey="cpu" stroke="#6366f1" strokeWidth={2} dot={false} name="CPU (%)" />
        <Line type="monotone" dataKey="mem" stroke="#a78bfa" strokeWidth={2} dot={false} name="Memoria (%)" />
      </LineChart>
    </ResponsiveContainer>
  )
}

// ─── ActivityItem ─────────────────────────────────────────────────────────────

const ACTIVITY_ICONS: Record<string, React.ReactNode> = {
  device_connected: <Smartphone size={14} className="text-emerald-400" />,
  runner_heartbeat: <Activity   size={14} className="text-indigo-400"  />,
  sync:             <RefreshCw  size={14} className="text-amber-400"   />,
  info:             <Zap        size={14} className="text-blue-400"    />,
}

function ActivityItem({ ev }: { ev: ActivityEvent }) {
  const icon = ACTIVITY_ICONS[ev.type] ?? ACTIVITY_ICONS.info
  return (
    <div className="flex items-start gap-2.5 py-2.5" style={{ borderBottom: '1px solid rgba(255,255,255,0.04)' }}>
      <div className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0 mt-0.5"
        style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}>
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-[11px] font-semibold" style={{ color: 'var(--text-sec)' }}>{ev.title}</div>
        <div className="text-[10px] text-slate-600 truncate">{ev.subtitle}</div>
      </div>
      <span className="text-[9px] text-slate-600 flex-shrink-0 mt-0.5">{fmtClock(ev.time)}</span>
    </div>
  )
}

// ─── StatCard ─────────────────────────────────────────────────────────────────

function StatCard({ icon, title, value, subtitle, accent, dim }: {
  icon: React.ReactNode; title: string; value: number | string; subtitle?: string; accent: string; dim?: boolean
}) {
  return (
    <div className="rounded-2xl p-4 flex items-center gap-3 transition-all"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.05) 0%, rgba(255,255,255,0.02) 100%)',
        border: '1px solid rgba(255,255,255,0.08)', backdropFilter: 'blur(12px)',
        opacity: dim ? 0.4 : 1,
      }}>
      <div className="w-11 h-11 rounded-2xl flex items-center justify-center flex-shrink-0"
        style={{ background: `${accent}18`, border: `1px solid ${accent}30` }}>
        <span style={{ color: accent }}>{icon}</span>
      </div>
      <div className="min-w-0">
        <div className="text-[10px] text-slate-500 font-semibold mb-0.5">{title}</div>
        <div className="text-xl font-black leading-none" style={{ color: 'var(--text-pri)' }}>{value}</div>
        {subtitle && <div className="text-[10px] mt-1" style={{ color: accent }}>{subtitle}</div>}
      </div>
    </div>
  )
}

// ─── FilterDropdown ───────────────────────────────────────────────────────────

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
        {selected?.label ?? label}<ChevronDown size={11} />
      </button>
      <AnimatePresence>
        {open && (
          <motion.div initial={{ opacity: 0, y: -4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}
            className="absolute top-9 left-0 z-30 rounded-xl py-1 min-w-[180px]"
            style={{ background: '#1e2130', border: '1px solid rgba(255,255,255,0.12)', boxShadow: '0 8px 32px rgba(0,0,0,0.5)' }}>
            {options.map(opt => (
              <button key={opt.id} onClick={() => { onChange(opt.id); setOpen(false) }}
                className="w-full flex items-center gap-2 px-3 py-1.5 text-[11px] font-medium text-left hover:bg-white/5"
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

// ─── Main DeviceFarm ──────────────────────────────────────────────────────────

const ROWS_OPTIONS = [5, 10, 20]

export default function DeviceFarm() {
  const [devices,     setDevices]     = useState<PhysicalDevice[]>([])
  const [runners,     setRunners]     = useState<Runner[]>([])
  const [loading,     setLoading]     = useState(true)
  const [error,       setError]       = useState<string | null>(null)
  const [search,      setSearch]      = useState('')
  const [platFilter,  setPlatFilter]  = useState('ALL')
  const [stateFilter, setStateFilter] = useState('ALL')
  const [page,        setPage]        = useState(1)
  const [rows,        setRows]        = useState(10)
  const [lastRefresh, setLastRefresh] = useState(Date.now())
  const [showModal,   setShowModal]   = useState(false)

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

  // ── Infrastructure state machine ─────────────────────────────────────────

  const infraState: InfraState = loading ? 'loading'
    : runners.length === 0                                  ? 'not_installed'
    : runners.every(r => r.status === 'OFFLINE')            ? 'offline'
    : devices.length === 0                                  ? 'scanning'
    : 'ready'

  const runnersOnline = runners.filter(r => r.status !== 'OFFLINE')

  // ── Device stats ─────────────────────────────────────────────────────────

  const total     = devices.length
  const available = devices.filter(d => d.status === 'AVAILABLE').length
  const busy      = devices.filter(d => d.status === 'BUSY').length
  const offline   = devices.filter(d => d.status === 'OFFLINE').length
  const android   = devices.filter(d => d.platform === 'ANDROID').length
  const ios       = devices.filter(d => d.platform === 'IOS').length
  const platforms = [android > 0 && 'Android', ios > 0 && 'iOS'].filter(Boolean) as string[]
  const dim       = infraState === 'not_installed' || infraState === 'loading'

  const firstRunner = runners[0]
  const runnerStats = useMemo(() =>
    firstRunner ? generateRunnerStats(firstRunner.runnerId) : generateRunnerStats('default'),
  [firstRunner])

  // ── Filtered + paginated ─────────────────────────────────────────────────

  const filtered = useMemo(() => {
    let list = devices
    if (platFilter !== 'ALL')  list = list.filter(d => d.platform === platFilter)
    if (stateFilter !== 'ALL') list = list.filter(d => d.status === stateFilter)
    if (search.trim()) {
      const q = search.toLowerCase()
      list = list.filter(d =>
        d.deviceName?.toLowerCase().includes(q) || d.udid?.toLowerCase().includes(q) ||
        d.runnerId?.toLowerCase().includes(q)    || d.platformVersion?.includes(q)
      )
    }
    return list
  }, [devices, platFilter, stateFilter, search])

  const totalPages = Math.max(1, Math.ceil(filtered.length / rows))
  const safePage   = Math.min(page, totalPages)
  const paginated  = filtered.slice((safePage - 1) * rows, safePage * rows)

  const activity  = useMemo(() => generateActivity(devices, runners), [devices, runners])
  const chartData = useMemo(() => generate24h(total, offline), [total, offline])

  async function handleStatusChange(udid: string, status: DeviceStatus) {
    await updateDeviceStatus(udid, status)
    setDevices(prev => prev.map(d => d.udid === udid ? { ...d, status } : d))
  }

  async function handleRemove(udid: string) {
    if (!confirm('¿Eliminar este dispositivo del pool?')) return
    await removeDevice(udid)
    setDevices(prev => prev.filter(d => d.udid !== udid))
  }

  const platOptions = [
    { id: 'ALL', label: 'Todos los Plataformas' },
    { id: 'ANDROID', label: 'Android' },
    { id: 'IOS',     label: 'iOS' },
  ]
  const stateOptions = [
    { id: 'ALL',         label: 'Todos los Estados' },
    { id: 'AVAILABLE',   label: 'Disponible' },
    { id: 'BUSY',        label: 'En Uso' },
    { id: 'OFFLINE',     label: 'Offline' },
    { id: 'MAINTENANCE', label: 'Mantenimiento' },
  ]

  return (
    <div className="p-6 min-h-full" style={{ background: 'var(--bg-main)' }}>

      {/* ── Error ── */}
      {error && (
        <div className="flex items-center gap-2 p-3 rounded-2xl text-sm mb-4"
          style={{ background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)', color: '#ef4444' }}>
          <AlertCircle size={14} />{error}
        </div>
      )}

      {/* ── Stats row ── */}
      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3 mb-5">
        <StatCard icon={<HardDrive size={20} />}    title="Total Dispositivos"       value={dim ? '—' : total}               accent="#6366f1" dim={dim} />
        <StatCard icon={<CheckCircle2 size={20} />} title="Disponibles"              value={dim ? '—' : available}           accent="#10b981" subtitle={!dim ? `${pct(available, total)}% del total` : undefined} dim={dim} />
        <StatCard icon={<Activity size={20} />}     title="En Uso"                   value={dim ? '—' : busy}                accent="#f59e0b" subtitle={!dim ? `${pct(busy, total)}% del total` : undefined} dim={dim} />
        <StatCard icon={<WifiOff size={20} />}      title="Offline"                  value={dim ? '—' : offline}             accent="#6b7280" dim={dim} />
        <StatCard icon={<Server size={20} />}       title="Runners Activos"          value={runnersOnline.length}            accent="#10b981" subtitle={runnersOnline.length > 0 ? `${Math.round((runnersOnline.length / Math.max(runners.length, 1)) * 100)}%` : undefined} />
        <StatCard icon={<Smartphone size={20} />}   title="Plataformas"              value={platforms.length || '—'}         accent="#818cf8" subtitle={platforms.join(', ') || (dim ? 'No configurado' : 'Sin datos')} />
      </div>

      {/* ── Body ── */}
      <div className="flex gap-4 items-start">

        {/* ── LEFT ── */}
        <div className="flex-1 min-w-0 space-y-4">

          {/* ── Runners / Infrastructure section ── */}
          <div className="rounded-2xl overflow-hidden"
            style={{
              background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
              border: infraState === 'not_installed' ? '1px solid rgba(99,102,241,0.15)' : infraState === 'offline' ? '1px solid rgba(239,68,68,0.12)' : '1px solid rgba(255,255,255,0.07)',
              backdropFilter: 'blur(12px)',
            }}>
            <div className="flex items-center justify-between px-5 py-3"
              style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
              <div className="flex items-center gap-2">
                <span className="text-[13px] font-bold" style={{ color: 'var(--text-pri)' }}>
                  {infraState === 'not_installed' ? 'Configuración de Infraestructura' : 'Runners Conectados'}
                </span>
                {infraState === 'not_installed' && (
                  <span className="text-[9px] font-black px-2 py-0.5 rounded-full"
                    style={{ background: 'rgba(239,68,68,0.15)', color: '#ef4444', border: '1px solid rgba(239,68,68,0.3)' }}>
                    No instalado
                  </span>
                )}
                {infraState === 'offline' && (
                  <span className="text-[9px] font-black px-2 py-0.5 rounded-full"
                    style={{ background: 'rgba(239,68,68,0.15)', color: '#ef4444', border: '1px solid rgba(239,68,68,0.3)' }}>
                    Offline
                  </span>
                )}
                {(infraState === 'scanning' || infraState === 'ready') && (
                  <span className="text-[9px] font-black px-2 py-0.5 rounded-full"
                    style={{ background: 'rgba(16,185,129,0.15)', color: '#10b981', border: '1px solid rgba(16,185,129,0.3)' }}>
                    {runnersOnline.length} online
                  </span>
                )}
              </div>
              {infraState === 'not_installed' && (
                <button onClick={() => setShowModal(true)}
                  className="flex items-center gap-1.5 text-[11px] font-black px-4 py-2 rounded-xl transition-all"
                  style={{ background: 'linear-gradient(135deg, #6366f1, #7c3aed)', color: '#fff', boxShadow: '0 4px 12px rgba(99,102,241,0.4)' }}>
                  <Download size={12} />
                  Descargar Runner
                </button>
              )}
              {infraState === 'offline' && (
                <button onClick={() => setShowModal(true)}
                  className="flex items-center gap-1.5 text-[11px] font-semibold px-3 py-1.5 rounded-xl"
                  style={{ background: 'rgba(239,68,68,0.12)', color: '#ef4444', border: '1px solid rgba(239,68,68,0.25)' }}>
                  <Stethoscope size={11} />
                  Diagnosticar
                </button>
              )}
              {(infraState === 'scanning' || infraState === 'ready') && (
                <button onClick={() => setShowModal(true)}
                  className="flex items-center gap-1.5 text-[11px] font-semibold px-3 py-1.5 rounded-xl"
                  style={{ background: 'rgba(255,255,255,0.05)', color: 'var(--text-dim)', border: '1px solid rgba(255,255,255,0.08)' }}>
                  <Stethoscope size={11} />
                  Diagnóstico
                </button>
              )}
            </div>

            <div className="p-4">
              {infraState === 'not_installed' && !loading && (
                <InfrastructureSetupRequired onInstall={() => setShowModal(true)} />
              )}
              {infraState === 'offline' && (
                <RunnerOfflineCard runners={runners} onDiag={() => setShowModal(true)} />
              )}
              {(infraState === 'scanning' || infraState === 'ready') && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {runners.filter(r => r.status !== 'OFFLINE').map(r => <RunnerCardMain key={r.runnerId} runner={r} />)}
                </div>
              )}
            </div>
          </div>

          {/* ── Device table ── */}
          <div className="rounded-2xl overflow-hidden"
            style={{
              background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
              border: '1px solid rgba(255,255,255,0.07)', backdropFilter: 'blur(12px)',
            }}>
            <div className="flex flex-wrap items-center gap-2 px-5 py-3"
              style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
              <div className="flex items-center gap-2 flex-1 min-w-0">
                <span className="text-[13px] font-bold" style={{ color: 'var(--text-pri)' }}>Dispositivos Conectados</span>
                <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full"
                  style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.25)' }}>
                  {filtered.length} dispositivos
                </span>
              </div>
              <div className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg"
                style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)', width: 190 }}>
                <Search size={11} style={{ color: 'var(--text-dim)', flexShrink: 0 }} />
                <input type="text" value={search} onChange={e => { setSearch(e.target.value); setPage(1) }}
                  placeholder="Buscar dispositivo..."
                  className="flex-1 bg-transparent text-[11px] outline-none placeholder-slate-600"
                  style={{ color: 'var(--text-sec)' }} />
              </div>
              <FilterDropdown label="Todos los Plataformas" options={platOptions}  value={platFilter}  onChange={v => { setPlatFilter(v);  setPage(1) }} />
              <FilterDropdown label="Todos los Estados"     options={stateOptions} value={stateFilter} onChange={v => { setStateFilter(v); setPage(1) }} />
            </div>

            {/* Empty states */}
            {!loading && infraState === 'not_installed' && (
              <div className="flex flex-col items-center py-10 gap-3">
                <Package size={32} className="text-slate-700" />
                <div className="text-center">
                  <div className="text-[12px] font-semibold text-slate-600">Infraestructura no configurada</div>
                  <div className="text-[10px] text-slate-700 mt-1">Instala el Runner para detectar dispositivos</div>
                </div>
                <button onClick={() => setShowModal(true)}
                  className="flex items-center gap-2 px-4 py-2 rounded-xl text-[11px] font-semibold"
                  style={{ background: 'rgba(99,102,241,0.12)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.25)' }}>
                  <Download size={11} />
                  Descargar Runner
                </button>
              </div>
            )}
            {!loading && infraState === 'offline' && (
              <div className="flex flex-col items-center py-10 gap-2">
                <ShieldAlert size={28} className="text-red-400 opacity-60" />
                <div className="text-[12px] font-semibold text-red-400 opacity-70">Runner Offline</div>
                <div className="text-[10px] text-slate-600">Los dispositivos aparecerán cuando el Runner se reconecte</div>
              </div>
            )}
            {!loading && infraState === 'scanning' && (
              <DeviceScanningCard />
            )}

            {/* Table */}
            {(loading || devices.length > 0) && (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
                      {['Dispositivo', 'Plataforma', 'Runner', 'Estado', 'Batería', 'OS Version', 'Última Vez Visto', ''].map(h => (
                        <th key={h} className="px-4 py-2.5 text-left text-[9px] font-black tracking-widest text-slate-600 uppercase whitespace-nowrap">
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {loading && !devices.length && Array.from({ length: 3 }, (_, i) => (
                      <tr key={i} className="border-b" style={{ borderColor: 'rgba(255,255,255,0.04)' }}>
                        {Array.from({ length: 8 }, (__, j) => (
                          <td key={j} className="px-4 py-3">
                            <div className="h-4 rounded animate-pulse" style={{ background: 'rgba(255,255,255,0.06)', width: j === 0 ? '140px' : '70px' }} />
                          </td>
                        ))}
                      </tr>
                    ))}
                    <AnimatePresence>
                      {paginated.map(device => (
                        <DeviceTableRow key={device.udid} device={device} runners={runners}
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
                  Filas:
                  <select value={rows} onChange={e => { setRows(+e.target.value); setPage(1) }}
                    className="bg-transparent text-[11px] outline-none cursor-pointer"
                    style={{ color: 'var(--text-sec)' }}>
                    {ROWS_OPTIONS.map(n => <option key={n} value={n}>{n}</option>)}
                  </select>
                </div>
                <div className="flex items-center gap-1.5">
                  <button onClick={() => setPage(p => Math.max(1, p - 1))} disabled={safePage === 1}
                    className="p-1 rounded-lg disabled:opacity-30" style={{ color: 'var(--text-dim)' }}>
                    <ChevronLeft size={14} />
                  </button>
                  <span className="text-[11px] font-semibold px-2 py-0.5 rounded-lg"
                    style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8' }}>
                    {safePage} / {totalPages}
                  </span>
                  <button onClick={() => setPage(p => Math.min(totalPages, p + 1))} disabled={safePage === totalPages}
                    className="p-1 rounded-lg disabled:opacity-30" style={{ color: 'var(--text-dim)' }}>
                    <ChevronRight size={14} />
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* ── Bottom charts ── */}
          <div className="grid grid-cols-3 gap-4">
            <div className="rounded-2xl p-4"
              style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)', border: '1px solid rgba(255,255,255,0.07)' }}>
              <div className="text-[12px] font-bold" style={{ color: 'var(--text-pri)' }}>Historial de Dispositivos</div>
              <div className="text-[10px] text-slate-600 mb-3">Últimas 24h</div>
              <HistorialChart data={chartData} />
              <div className="flex items-center gap-4 mt-2">
                {[{ c: '#10b981', l: 'Conectados' }, { c: '#ef4444', l: 'Desconectados' }].map(i => (
                  <div key={i.l} className="flex items-center gap-1.5">
                    <span className="w-3 h-0.5 rounded-full" style={{ background: i.c }} />
                    <span className="text-[9px] text-slate-500">{i.l}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="rounded-2xl p-4"
              style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)', border: '1px solid rgba(255,255,255,0.07)' }}>
              <div className="text-[12px] font-bold" style={{ color: 'var(--text-pri)' }}>Estado de Dispositivos</div>
              <div className="text-[10px] text-slate-600 mb-2">Últimas 24h</div>
              <StatusDonut available={available} busy={busy} offline={offline} total={total} />
            </div>

            <div className="rounded-2xl p-4"
              style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)', border: '1px solid rgba(255,255,255,0.07)' }}>
              <div className="text-[12px] font-bold" style={{ color: 'var(--text-pri)' }}>Estadísticas del Runner</div>
              <div className="text-[10px] text-slate-600 mb-3">{firstRunner?.runnerId ?? 'Sin runner activo'}</div>
              <RunnerStatsChart data={runnerStats} />
              <div className="flex items-center gap-4 mt-2">
                {[{ c: '#6366f1', l: 'CPU (%)' }, { c: '#a78bfa', l: 'Memoria (%)' }].map(i => (
                  <div key={i.l} className="flex items-center gap-1.5">
                    <span className="w-3 h-0.5 rounded-full" style={{ background: i.c }} />
                    <span className="text-[9px] text-slate-500">{i.l}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* ── RIGHT sidebar ── */}
        <div className="w-72 flex-shrink-0 space-y-4">
          <div className="rounded-2xl p-4"
            style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)', border: '1px solid rgba(255,255,255,0.07)' }}>
            <div className="text-[12px] font-bold mb-3" style={{ color: 'var(--text-pri)' }}>Dispositivos por Plataforma</div>
            <PlatformDonutSidebar android={android} ios={ios} total={total} />
          </div>

          <div className="rounded-2xl p-4"
            style={{ background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)', border: '1px solid rgba(255,255,255,0.07)' }}>
            <div className="flex items-center justify-between mb-1">
              <div className="text-[12px] font-bold" style={{ color: 'var(--text-pri)' }}>Actividad en Tiempo Real</div>
              <span className="text-[10px] text-indigo-400 cursor-pointer hover:text-indigo-300">Ver todo</span>
            </div>
            <div className="text-[10px] text-slate-600 mb-2">{activity.length} eventos recientes</div>
            <div>
              {activity.slice(0, 6).map(ev => <ActivityItem key={ev.id} ev={ev} />)}
              {activity.length === 0 && (
                <div className="text-[11px] text-slate-600 py-6 text-center">Sin actividad reciente</div>
              )}
            </div>
          </div>

          <div className="flex items-center gap-1.5 text-[10px] text-slate-600 px-1">
            <Clock size={10} />
            {new Date(lastRefresh).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
            · Refresco cada 10s
          </div>
        </div>
      </div>

      {/* ── Modal ── */}
      <AnimatePresence>
        {showModal && (
          <InstallModal
            infraState={infraState}
            runners={runners}
            onClose={() => setShowModal(false)}
          />
        )}
      </AnimatePresence>
    </div>
  )
}
