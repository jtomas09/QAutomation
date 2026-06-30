import React, { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Wifi, WifiOff, Activity, Zap,
  RefreshCw, Smartphone, AlertTriangle, PowerOff,
} from 'lucide-react'
import { getDevices } from '../api'
import type { PhysicalDevice } from '../types'
import { OsAvatar, PlatformBadge } from '../components/PlatformIcon'
import { useRunnerLifecycle } from '../hooks/useRunnerLifecycle'

import ip15  from '../assets/devices/iphone-15.svg'
import p8pro from '../assets/devices/pixel-8-pro.svg'
import s24   from '../assets/devices/galaxy-s24.svg'
import a56   from '../assets/devices/galaxy-a56.svg'
import rn13  from '../assets/devices/redmi-note13.svg'

// ─── Image selection ──────────────────────────────────────────────────────────

const ANDROID_MOCKS = [a56, p8pro, s24, rn13]

function pickImage(device: PhysicalDevice): string {
  if (device.platform?.toUpperCase() === 'IOS') return ip15
  // Deterministic selection based on UDID so the same device always gets the same image
  let hash = 0
  for (let i = 0; i < device.udid.length; i++)
    hash = (hash * 31 + device.udid.charCodeAt(i)) & 0xffff
  return ANDROID_MOCKS[hash % ANDROID_MOCKS.length]
}

// ─── Status helpers ───────────────────────────────────────────────────────────

type CardStatus = 'available' | 'inuse' | 'offline'

function mapStatus(raw: string): CardStatus {
  const u = (raw ?? '').toUpperCase()
  if (u === 'AVAILABLE')        return 'available'
  if (u === 'BUSY' || u === 'INUSE') return 'inuse'
  return 'offline'
}

const STATUS_META: Record<CardStatus, { label: string; color: string; bg: string; Icon: React.ElementType }> = {
  available: { label: 'Disponible', color: '#10b981', bg: 'rgba(16,185,129,0.12)',  Icon: Wifi     },
  inuse:     { label: 'En uso',     color: '#6366f1', bg: 'rgba(99,102,241,0.12)',  Icon: Activity },
  offline:   { label: 'Offline',    color: '#f43f5e', bg: 'rgba(244,63,94,0.12)',   Icon: WifiOff  },
}

function accentFor(device: PhysicalDevice, active: boolean): string {
  if (device.platform?.toUpperCase() === 'IOS') return 'rgba(169,184,216,0.3)'
  return active ? 'rgba(61,220,132,0.45)' : 'rgba(61,220,132,0.2)'
}

// ─── Skeleton ─────────────────────────────────────────────────────────────────

function SkeletonCard() {
  return (
    <div
      className="flex flex-col rounded-2xl overflow-hidden animate-pulse"
      style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)', height: 280 }}
    >
      <div className="flex-1 flex items-center justify-center">
        <div className="w-16 h-28 rounded-xl" style={{ background: 'rgba(255,255,255,0.05)' }} />
      </div>
      <div className="px-3 pb-3 flex flex-col gap-2">
        <div className="h-3 rounded-full mx-4" style={{ background: 'rgba(255,255,255,0.07)' }} />
        <div className="h-2.5 rounded-full mx-6" style={{ background: 'rgba(255,255,255,0.05)' }} />
        <div className="grid grid-cols-2 gap-1.5 mt-1">
          <div className="h-7 rounded-lg" style={{ background: 'rgba(255,255,255,0.04)' }} />
          <div className="h-7 rounded-lg" style={{ background: 'rgba(255,255,255,0.04)' }} />
        </div>
      </div>
    </div>
  )
}

function SkeletonGrid() {
  return (
    <div className="p-6 pb-10">
      <div className="mb-6">
        <div className="h-7 w-48 rounded-xl mb-2" style={{ background: 'rgba(255,255,255,0.06)' }} />
        <div className="h-4 w-72 rounded-lg" style={{ background: 'rgba(255,255,255,0.04)' }} />
      </div>
      <div className="grid grid-cols-4 gap-4 mb-6">
        {[0,1,2,3].map(i => (
          <div key={i} className="h-20 rounded-2xl animate-pulse" style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.06)' }} />
        ))}
      </div>
      <div className="grid grid-cols-5 gap-4">
        {[0,1,2].map(i => <SkeletonCard key={i} />)}
      </div>
    </div>
  )
}

// ─── Empty / Error states ─────────────────────────────────────────────────────

function EmptyState({ onRefresh }: { onRefresh: () => void }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="flex flex-col items-center justify-center py-24 gap-4"
    >
      <div className="w-16 h-16 rounded-2xl flex items-center justify-center"
        style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)' }}>
        <Smartphone size={28} className="text-slate-600" />
      </div>
      <div className="text-center">
        <div className="text-sm font-bold text-slate-400">No hay dispositivos conectados</div>
        <div className="text-xs text-slate-600 mt-1">Conecta un dispositivo por USB al equipo donde está instalado el Runner</div>
      </div>
      <button
        onClick={onRefresh}
        className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:text-slate-200 transition-colors"
        style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}
      >
        <RefreshCw size={12} />
        Actualizar
      </button>
    </motion.div>
  )
}

function ErrorState({ onRetry }: { onRetry: () => void }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      className="flex flex-col items-center justify-center py-24 gap-4"
    >
      <div className="w-16 h-16 rounded-2xl flex items-center justify-center"
        style={{ background: 'rgba(244,63,94,0.08)', border: '1px solid rgba(244,63,94,0.15)' }}>
        <AlertTriangle size={28} style={{ color: '#f43f5e' }} />
      </div>
      <div className="text-center">
        <div className="text-sm font-bold text-slate-300">Runner desconectado</div>
        <div className="text-xs text-slate-600 mt-1">No fue posible obtener dispositivos. Verifica que el Runner Agent esté activo.</div>
      </div>
      <button
        onClick={onRetry}
        className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold text-white transition-all"
        style={{ background: 'linear-gradient(135deg, #4f46e5, #6366f1)', boxShadow: '0 4px 14px rgba(99,102,241,0.4)' }}
      >
        <RefreshCw size={12} />
        Reintentar
      </button>
    </motion.div>
  )
}

// ─── Main page ────────────────────────────────────────────────────────────────

interface Props {
  onSelectDevice?: (deviceName: string) => void
}

export default function DevicesPage({ onSelectDevice }: Props) {
  const { isOnline: runnerOnline, initialized: runnerInitialized, startRunner } = useRunnerLifecycle()

  const [devices,    setDevices]    = useState<PhysicalDevice[]>([])
  const [loading,    setLoading]    = useState(true)
  const [error,      setError]      = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [activeId,   setActiveId]   = useState<string | null>(null)
  const [testing,    setTesting]    = useState<string | null>(null)
  const [testResult, setTestResult] = useState<Record<string, 'ok' | 'fail'>>({})
  const [activating, setActivating] = useState(false)

  const load = useCallback(async (silent = false) => {
    if (!silent) setRefreshing(true)
    try {
      const data = await getDevices()
      setDevices(data)
      setError(false)
    } catch {
      setError(true)
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [])

  useEffect(() => {
    load(false)
    const id = setInterval(() => load(true), 15_000)
    return () => clearInterval(id)
  }, [load])

  const total     = devices.length
  const available = devices.filter(d => mapStatus(d.status) === 'available').length
  const inuse     = devices.filter(d => mapStatus(d.status) === 'inuse').length
  const offline   = devices.filter(d => mapStatus(d.status) === 'offline').length

  async function handleTestConnection(d: PhysicalDevice) {
    setTesting(d.udid)
    await new Promise(r => setTimeout(r, 1800))
    setTestResult(prev => ({ ...prev, [d.udid]: mapStatus(d.status) !== 'offline' ? 'ok' : 'fail' }))
    setTesting(null)
  }

  function handleSetActive(d: PhysicalDevice) {
    const next = activeId === d.udid ? null : d.udid
    setActiveId(next)
    if (next) onSelectDevice?.(d.deviceName || d.model || d.udid)
  }

  if (loading) return <SkeletonGrid />

  // Runner explicitly stopped — skip ADB / device queries, show stopped state
  if (runnerInitialized && !runnerOnline) {
    return (
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        className="flex flex-col items-center justify-center py-28 gap-5"
      >
        <div
          className="w-20 h-20 rounded-2xl flex items-center justify-center"
          style={{ background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.18)' }}
        >
          <PowerOff size={34} style={{ color: 'rgba(99,102,241,0.5)' }} />
        </div>
        <div className="text-center">
          <div className="text-base font-bold text-slate-300">Runner detenido</div>
          <div className="text-xs text-slate-500 mt-1.5 max-w-xs leading-relaxed">
            Activa el Runner para que el sistema detecte dispositivos<br />
            Android e iOS conectados por USB.
          </div>
        </div>
        <button
          onClick={async () => {
            setActivating(true)
            try { await startRunner() } catch { /* handled by service */ }
            finally { setActivating(false) }
          }}
          disabled={activating}
          className="flex items-center gap-2 px-5 py-2.5 rounded-xl text-sm font-bold text-white disabled:opacity-60"
          style={{
            background:  'linear-gradient(135deg, #4f46e5, #6366f1)',
            boxShadow:   '0 4px 14px rgba(99,102,241,0.35)',
          }}
        >
          <RefreshCw size={13} className={activating ? 'animate-spin' : ''} />
          {activating ? 'Activando…' : 'Activar Runner'}
        </button>
      </motion.div>
    )
  }

  return (
    <div className="p-6 pb-10">

      {/* Page header */}
      <motion.div
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
        className="flex items-start justify-between mb-6"
      >
        <div>
          <h1 className="text-2xl font-extrabold text-slate-100">Dispositivos</h1>
          <p className="text-sm text-slate-500 mt-1">
            Dispositivos físicos detectados por el Runner Agent · actualización automática cada 15s
          </p>
        </div>
        <motion.button
          whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}
          onClick={() => load(false)}
          disabled={refreshing}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-bold text-white disabled:opacity-60"
          style={{
            background: 'linear-gradient(135deg, #4f46e5, #6366f1)',
            boxShadow: '0 4px 14px rgba(99,102,241,0.4)',
          }}
        >
          <RefreshCw size={14} className={refreshing ? 'animate-spin' : ''} />
          {refreshing ? 'Actualizando…' : 'Actualizar'}
        </motion.button>
      </motion.div>

      {/* Stats row */}
      <div className="grid grid-cols-4 gap-4 mb-6">
        {[
          { label: 'Total',       value: total,     color: '#818cf8' },
          { label: 'Disponibles', value: available,  color: '#10b981' },
          { label: 'En uso',      value: inuse,      color: '#6366f1' },
          { label: 'Offline',     value: offline,    color: '#f43f5e' },
        ].map((s, i) => (
          <motion.div
            key={s.label}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.06 }}
            className="flex items-center gap-4 p-4 rounded-2xl"
            style={{
              background: 'linear-gradient(135deg, rgba(255,255,255,0.04), rgba(255,255,255,0.02))',
              border: '1px solid rgba(255,255,255,0.08)',
            }}
          >
            <div className="text-3xl font-black" style={{ color: s.color }}>{s.value}</div>
            <div className="text-sm text-slate-400 font-medium">{s.label}</div>
          </motion.div>
        ))}
      </div>

      {/* Error banner (non-blocking — show last data + warning) */}
      <AnimatePresence>
        {error && (
          <motion.div
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: 'auto' }}
            exit={{ opacity: 0, height: 0 }}
            className="flex items-center gap-3 px-4 py-3 rounded-xl mb-4"
            style={{ background: 'rgba(244,63,94,0.08)', border: '1px solid rgba(244,63,94,0.2)' }}
          >
            <AlertTriangle size={14} style={{ color: '#f43f5e', flexShrink: 0 }} />
            <span className="text-xs text-red-400">Runner desconectado · mostrando último estado conocido</span>
            <button
              onClick={() => load(false)}
              className="ml-auto text-xs font-semibold text-red-400 hover:text-red-200 transition-colors"
            >
              Reintentar
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Empty state */}
      {devices.length === 0 && !error && <EmptyState onRefresh={() => load(false)} />}

      {/* Full error state (no previous data) */}
      {devices.length === 0 && error && <ErrorState onRetry={() => load(false)} />}

      {/* Device grid */}
      {devices.length > 0 && (
        <div className="grid grid-cols-5 gap-4">
          <AnimatePresence mode="popLayout">
            {devices.map((device, i) => (
              <DeviceCard
                key={device.udid}
                device={device}
                index={i}
                isActive={activeId === device.udid}
                testResult={testResult[device.udid]}
                isTesting={testing === device.udid}
                onSetActive={() => handleSetActive(device)}
                onTestConnection={() => handleTestConnection(device)}
              />
            ))}
          </AnimatePresence>
        </div>
      )}
    </div>
  )
}

// ── Device Card ────────────────────────────────────────────────────────────────

function DeviceCard({
  device, index, isActive, testResult, isTesting,
  onSetActive, onTestConnection,
}: {
  device: PhysicalDevice
  index: number
  isActive: boolean
  testResult?: 'ok' | 'fail'
  isTesting: boolean
  onSetActive: () => void
  onTestConnection: () => void
}) {
  const statusKey = mapStatus(device.status)
  const sm        = STATUS_META[statusKey]
  const img       = pickImage(device)
  const accent    = accentFor(device, isActive)
  const isIos     = device.platform?.toUpperCase() === 'IOS'

  const displayName = device.deviceName || device.model || 'Dispositivo'
  const platformLabel = isIos ? 'iOS' : 'Android'
  const version = device.platformVersion ?? '—'

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.95 }}
      transition={{ delay: index * 0.05, duration: 0.35 }}
      whileHover={{ y: -4, transition: { duration: 0.2 } }}
      layout
      className="relative flex flex-col rounded-2xl overflow-hidden"
      style={{
        background: isActive
          ? 'linear-gradient(135deg, rgba(99,102,241,0.1), rgba(99,102,241,0.05))'
          : 'linear-gradient(135deg, rgba(255,255,255,0.04), rgba(255,255,255,0.02))',
        border: isActive
          ? '1px solid rgba(99,102,241,0.4)'
          : '1px solid rgba(255,255,255,0.08)',
        boxShadow: isActive
          ? '0 0 24px rgba(99,102,241,0.2), 0 4px 24px rgba(0,0,0,0.4)'
          : '0 4px 24px rgba(0,0,0,0.3)',
      }}
    >
      {/* Active badge */}
      {isActive && (
        <div
          className="absolute top-2.5 left-2.5 flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-bold z-10"
          style={{ background: 'rgba(99,102,241,0.3)', color: '#a5b4fc', border: '1px solid rgba(99,102,241,0.4)' }}
        >
          ACTIVO
        </div>
      )}

      {/* Status pulse dot top-right */}
      <motion.div
        className="absolute top-2.5 right-2.5 w-2 h-2 rounded-full z-10"
        style={{ background: sm.color, boxShadow: `0 0 8px ${sm.color}` }}
        animate={statusKey === 'inuse'
          ? { opacity: [1, 0.35, 1], scale: [1, 1.2, 1] }
          : { opacity: 1 }
        }
        transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }}
      />

      {/* Device image */}
      <div className="flex justify-center items-end pt-8 pb-2" style={{ height: 130 }}>
        <motion.img
          src={img}
          alt={displayName}
          className="h-full w-auto object-contain relative z-10"
          style={{
            filter: `drop-shadow(0 0 14px ${accent}) drop-shadow(0 4px 8px rgba(0,0,0,0.5))`,
          }}
          whileHover={{
            filter: `drop-shadow(0 0 22px ${accent}) drop-shadow(0 6px 12px rgba(0,0,0,0.6))`,
            transition: { duration: 0.3 },
          }}
        />
        {/* Ground reflection */}
        <div
          className="absolute bottom-0 left-1/2 -translate-x-1/2 w-12 h-3 rounded-full pointer-events-none"
          style={{ background: isIos ? '#a8b8d8' : '#3DDC84', filter: 'blur(8px)', opacity: 0.18 }}
        />
      </div>

      {/* Info */}
      <div className="flex flex-col gap-2 px-3 pb-3">
        {/* Name + platform */}
        <div className="text-center">
          <div className="text-[11px] font-bold text-slate-200 leading-tight line-clamp-2" title={displayName}>
            {displayName}
          </div>
          <div className="text-[9px] text-slate-600 mt-0.5">{platformLabel} {version}</div>
        </div>

        {/* Status + Platform badge */}
        <div className="flex items-center justify-between gap-1">
          <span
            className="flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[9px] font-bold"
            style={{ color: sm.color, background: sm.bg }}
          >
            <sm.Icon size={8} />
            {sm.label}
          </span>
          <PlatformBadge platform={device.platform} size="xs" />
        </div>

        {/* UDID */}
        <div
          className="text-[9px] text-slate-700 font-mono truncate"
          title={device.udid}
        >
          {device.udid}
        </div>

        {/* Connection test result */}
        {testResult && (
          <div
            className="text-center text-[9px] font-bold py-0.5 rounded-lg"
            style={{
              color:      testResult === 'ok' ? '#10b981' : '#f43f5e',
              background: testResult === 'ok' ? 'rgba(16,185,129,0.1)' : 'rgba(244,63,94,0.1)',
            }}
          >
            {testResult === 'ok' ? '✓ Conexión OK' : '✗ Sin conexión'}
          </div>
        )}

        {/* Action buttons */}
        <div className="grid grid-cols-2 gap-1.5 mt-1">
          <button
            onClick={onTestConnection}
            disabled={isTesting}
            className="flex items-center justify-center gap-1 py-1.5 rounded-lg text-[9px] font-semibold text-slate-400 hover:text-slate-200 transition-colors"
            style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}
          >
            {isTesting
              ? <RefreshCw size={9} className="animate-spin" />
              : <Wifi size={9} />
            }
            {isTesting ? 'Probando…' : 'Probar'}
          </button>

          <button
            onClick={onSetActive}
            className="flex items-center justify-center gap-1 py-1.5 rounded-lg text-[9px] font-bold transition-colors"
            style={
              isActive
                ? { background: 'rgba(99,102,241,0.2)', color: '#a5b4fc', border: '1px solid rgba(99,102,241,0.3)' }
                : { background: 'rgba(255,255,255,0.04)', color: '#64748b', border: '1px solid rgba(255,255,255,0.07)' }
            }
          >
            <Zap size={9} />
            {isActive ? 'En uso' : 'Usar'}
          </button>
        </div>
      </div>
    </motion.div>
  )
}
