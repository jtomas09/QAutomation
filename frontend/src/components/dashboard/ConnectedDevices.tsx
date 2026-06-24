import React, { useState, useEffect, useCallback, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Settings, Wifi, WifiOff, Activity, Save, Check } from 'lucide-react'
import { getDevices, getAllDeviceAppConfigs } from '../../api'
import type { PhysicalDevice, DeviceAppConfig } from '../../types'
import type { ConfiguredDevice } from '../../hooks/useExecutionDevices'
import { PlatformIcon } from '../PlatformIcon'

import ip15  from '../../assets/devices/iphone-15.svg'
import p8pro from '../../assets/devices/pixel-8-pro.svg'
import s24   from '../../assets/devices/galaxy-s24.svg'
import a56   from '../../assets/devices/galaxy-a56.svg'
import rn13  from '../../assets/devices/redmi-note13.svg'

// ─── Helpers ──────────────────────────────────────────────────────────────────

const ANDROID_MOCKS = [a56, p8pro, s24, rn13]

function pickImage(device: PhysicalDevice): string {
  if (device.platform?.toUpperCase() === 'IOS') return ip15
  let hash = 0
  for (let i = 0; i < device.udid.length; i++)
    hash = (hash * 31 + device.udid.charCodeAt(i)) & 0xffff
  return ANDROID_MOCKS[hash % ANDROID_MOCKS.length]
}

function accentFor(device: PhysicalDevice) {
  if (device.platform?.toUpperCase() === 'IOS')
    return { color: '#a8b8d8', glow: 'rgba(168,184,216,0.35)' }
  return { color: '#3DDC84', glow: 'rgba(61,220,132,0.35)' }
}

type CardStatus = 'available' | 'inuse' | 'offline'

function mapStatus(raw: string): CardStatus {
  const u = (raw ?? '').toUpperCase()
  if (u === 'AVAILABLE')               return 'available'
  if (u === 'BUSY' || u === 'INUSE')  return 'inuse'
  return 'offline'
}

const STATUS: Record<CardStatus, { label: string; color: string; bg: string; Icon: React.ElementType }> = {
  available: { label: 'Disponible', color: '#10b981', bg: 'rgba(16,185,129,0.12)',  Icon: Wifi     },
  inuse:     { label: 'En uso',     color: '#6366f1', bg: 'rgba(99,102,241,0.12)',  Icon: Activity },
  offline:   { label: 'Offline',    color: '#f43f5e', bg: 'rgba(244,63,94,0.12)',   Icon: WifiOff  },
}

// ─── Notification banner ──────────────────────────────────────────────────────

function DisconnectBanner({ names, onDismiss }: { names: string[]; onDismiss: () => void }) {
  useEffect(() => {
    const t = setTimeout(onDismiss, 6000)
    return () => clearTimeout(t)
  }, [onDismiss])

  return (
    <motion.div
      initial={{ opacity: 0, y: -8, height: 0 }}
      animate={{ opacity: 1, y: 0, height: 'auto' }}
      exit={{ opacity: 0, height: 0 }}
      className="overflow-hidden"
    >
      <div
        className="flex items-center justify-between mx-4 mt-3 px-3 py-2.5 rounded-xl text-xs"
        style={{ background: 'rgba(244,63,94,0.1)', border: '1px solid rgba(244,63,94,0.25)' }}
      >
        <span style={{ color: '#f87171' }}>
          ⚠️ {names.join(', ')} {names.length === 1 ? 'fue removido' : 'fueron removidos'} de la configuración porque se desconectó.
        </span>
        <button onClick={onDismiss} className="ml-3 text-slate-500 hover:text-slate-300 flex-shrink-0">✕</button>
      </div>
    </motion.div>
  )
}

// ─── Config summary bar ───────────────────────────────────────────────────────

function ConfigSummary({ configured, isDirty, saving, onSave }: {
  configured: ConfiguredDevice[]
  isDirty:    boolean
  saving:     boolean
  onSave:     () => void
}) {
  const platforms = [...new Set(
    configured.map(d => d.platform?.toUpperCase() === 'IOS' ? 'iOS' : 'Android')
  )]

  return (
    <div
      className="flex items-center justify-between px-5 py-3"
      style={{ borderBottom: '1px solid rgba(255,255,255,0.06)', background: 'rgba(99,102,241,0.04)' }}
    >
      <div className="flex items-center gap-3">
        <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Configuración de Ejecución</span>

        {configured.length === 0 ? (
          <span className="text-[10px] text-slate-600 italic">Sin dispositivos configurados</span>
        ) : (
          <div className="flex items-center gap-1.5">
            {platforms.map(p => (
              <span
                key={p}
                className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-bold"
                style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8' }}
              >
                <Check size={8} />
                {p}
              </span>
            ))}
          </div>
        )}
      </div>

      <motion.button
        whileHover={isDirty ? { scale: 1.03 } : undefined}
        whileTap={isDirty ? { scale: 0.97 } : undefined}
        onClick={isDirty && !saving ? onSave : undefined}
        disabled={!isDirty || saving}
        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[10px] font-bold transition-all"
        style={{
          background:   isDirty ? 'rgba(99,102,241,0.2)'  : 'rgba(255,255,255,0.04)',
          border:       isDirty ? '1px solid rgba(99,102,241,0.4)' : '1px solid rgba(255,255,255,0.07)',
          color:        isDirty ? '#818cf8' : '#475569',
          cursor:       isDirty && !saving ? 'pointer' : 'default',
        }}
      >
        <Save size={10} />
        {saving ? 'Guardando…' : isDirty ? 'Guardar Configuración' : 'Configuración guardada'}
      </motion.button>
    </div>
  )
}

// ─── Component ────────────────────────────────────────────────────────────────

interface Props {
  configured:     ConfiguredDevice[]
  onToggleDevice: (device: PhysicalDevice) => void
  onSave:         () => void
  saving:         boolean
  isDirty:        boolean
  onSyncLive:     (liveDevices: PhysicalDevice[]) => string[]
  onManage?:      () => void
}

export default function ConnectedDevices({
  configured, onToggleDevice, onSave, saving, isDirty, onSyncLive, onManage,
}: Props) {
  const [devices,       setDevices]       = useState<PhysicalDevice[]>([])
  const [notifications, setNotifications] = useState<string[]>([])
  const [appConfigs,    setAppConfigs]    = useState<Record<string, DeviceAppConfig>>({})
  const prevLiveRef                       = useRef<PhysicalDevice[]>([])

  const refresh = useCallback(async () => {
    try {
      const [data, cfgs] = await Promise.all([getDevices(), getAllDeviceAppConfigs()])
      setDevices(data)
      setAppConfigs(cfgs)

      // Detect disconnected configured devices
      const removed = onSyncLive(data)
      if (removed.length > 0) {
        setNotifications(removed)
      }

      prevLiveRef.current = data
    } catch {
      // Silently keep last state — widget must never crash the dashboard
    }
  }, [onSyncLive])

  useEffect(() => {
    refresh()
    const id = setInterval(refresh, 15_000)
    return () => clearInterval(id)
  }, [refresh])

  // Only show devices that are actually reachable — OFFLINE/MAINTENANCE devices are
  // excluded so users cannot configure a device that is no longer connected.
  const onlineDevices = devices.filter(d => d.status !== 'OFFLINE' && d.status !== 'MAINTENANCE')

  const configuredSet = new Set(configured.map(d => d.udid))
  const available     = onlineDevices.filter(d => mapStatus(d.status) === 'available').length
  const inuse         = onlineDevices.filter(d => mapStatus(d.status) === 'inuse').length

  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, ease: 'easeOut' }}
      className="overflow-hidden rounded-2xl"
      style={{
        background: 'var(--panel-bg)',
        border:     '1px solid var(--panel-border)',
        boxShadow:  'var(--panel-shadow)',
      }}
    >
      {/* Config summary bar */}
      <ConfigSummary
        configured={configured}
        isDirty={isDirty}
        saving={saving}
        onSave={onSave}
      />

      {/* Header */}
      <div
        className="flex items-center justify-between px-5 py-4"
        style={{ borderBottom: '1px solid var(--panel-divide)' }}
      >
        <div>
          <div className="text-sm font-bold text-slate-100">Dispositivos Conectados</div>
          <div className="text-xs text-slate-500 mt-0.5">
            {onlineDevices.length === 0 ? (
              <span className="text-slate-600">Sin dispositivos conectados</span>
            ) : (
              <>
                <span style={{ color: '#10b981' }}>{available} disponible{available !== 1 ? 's' : ''}</span>
                <span className="mx-1.5 opacity-30">·</span>
                <span style={{ color: '#6366f1' }}>{inuse} en uso</span>
                {configured.length > 0 && (
                  <>
                    <span className="mx-1.5 opacity-30">·</span>
                    <span style={{ color: '#818cf8' }}>{configured.length} configurado{configured.length !== 1 ? 's' : ''}</span>
                  </>
                )}
              </>
            )}
          </div>
        </div>
        <button
          onClick={onManage}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold text-slate-400 hover:text-slate-200 transition-colors"
          style={{ background: 'var(--btn-bg)', border: '1px solid var(--btn-border)' }}
        >
          <Settings size={11} />
          Gestionar
        </button>
      </div>

      {/* Disconnect notifications */}
      <AnimatePresence>
        {notifications.length > 0 && (
          <DisconnectBanner
            names={notifications}
            onDismiss={() => setNotifications([])}
          />
        )}
      </AnimatePresence>

      {/* Device cards */}
      {onlineDevices.length === 0 ? (
        <div className="flex items-center justify-center py-10 text-xs text-slate-600">
          {devices.length > 0
            ? 'Todos los dispositivos detectados están offline'
            : 'Conecta un dispositivo al Runner para verlo aquí'}
        </div>
      ) : (
        <div className="grid grid-cols-5 gap-3 p-4">
          <AnimatePresence mode="popLayout">
            {onlineDevices.map((device, i) => (
              <DeviceCard
                key={device.udid}
                device={device}
                index={i}
                selected={configuredSet.has(device.udid)}
                onToggle={() => onToggleDevice(device)}
                appConfig={appConfigs[device.udid]}
              />
            ))}
          </AnimatePresence>
        </div>
      )}
    </motion.div>
  )
}

// ─── DeviceCard ───────────────────────────────────────────────────────────────

function DeviceCard({
  device, index, selected, onToggle, appConfig,
}: {
  device:    PhysicalDevice
  index:     number
  selected:  boolean
  onToggle:  () => void
  appConfig?: DeviceAppConfig
}) {
  const statusKey = mapStatus(device.status)
  const s         = STATUS[statusKey]
  const { color, glow } = accentFor(device)
  const img       = pickImage(device)
  const name      = device.deviceName || device.model || 'Dispositivo'

  // When selected, override with purple selection glow
  const selBorder  = 'rgba(99,102,241,0.55)'
  const selGlow    = 'rgba(99,102,241,0.3)'
  const activeBorder = selected ? selBorder : `${color}22`
  const activeGlow   = selected ? selGlow   : glow.replace('0.35', '0.08')

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, scale: 0.85 }}
      transition={{ delay: index * 0.07, duration: 0.4, ease: 'easeOut' }}
      className="relative flex flex-col items-center gap-2 p-3 rounded-xl overflow-hidden"
      style={{
        background:  selected ? 'rgba(99,102,241,0.07)' : 'var(--btn-bg)',
        border:      `1px solid ${activeBorder}`,
        boxShadow:   `0 0 20px ${activeGlow}`,
        transition:  'box-shadow 0.3s ease, border-color 0.3s ease, background 0.3s ease',
      }}
      onMouseEnter={e => {
        const el = e.currentTarget as HTMLDivElement
        el.style.boxShadow = selected
          ? `0 8px 32px rgba(99,102,241,0.45), 0 0 0 1px rgba(99,102,241,0.6)`
          : `0 8px 32px ${glow}, 0 0 0 1px ${color}33`
        el.style.borderColor = selected ? 'rgba(99,102,241,0.7)' : `${color}44`
      }}
      onMouseLeave={e => {
        const el = e.currentTarget as HTMLDivElement
        el.style.boxShadow   = `0 0 20px ${activeGlow}`
        el.style.borderColor = activeBorder
      }}
    >
      {/* Selection glow overlay */}
      {selected && (
        <div
          className="absolute top-0 right-0 w-24 h-24 pointer-events-none rounded-full"
          style={{ background: 'radial-gradient(circle at top right, rgba(99,102,241,0.18) 0%, transparent 70%)' }}
        />
      )}

      {/* Ambient glow */}
      {!selected && (
        <div
          className="absolute top-0 right-0 w-20 h-20 pointer-events-none rounded-full"
          style={{ background: `radial-gradient(circle at top right, ${glow.replace('0.35', '0.15')} 0%, transparent 70%)` }}
        />
      )}

      {/* Status dot */}
      <motion.div
        className="absolute top-2.5 right-2.5 w-2 h-2 rounded-full"
        style={{ background: s.color, boxShadow: `0 0 8px ${s.color}` }}
        animate={statusKey === 'inuse'
          ? { opacity: [1, 0.35, 1], scale: [1, 1.2, 1] }
          : { opacity: 1 }
        }
        transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }}
      />

      {/* Device image */}
      <div className="relative w-full flex justify-center" style={{ height: 100 }}>
        <motion.img
          src={img}
          alt={name}
          className="h-full w-auto object-contain relative z-10"
          style={{
            filter: selected
              ? `drop-shadow(0 0 16px rgba(99,102,241,0.6)) drop-shadow(0 4px 8px rgba(0,0,0,0.5))`
              : `drop-shadow(0 0 14px ${glow}) drop-shadow(0 4px 8px rgba(0,0,0,0.5))`,
          }}
        />
        <div
          className="absolute bottom-0 left-1/2 -translate-x-1/2 w-12 h-3 rounded-full pointer-events-none"
          style={{ background: selected ? '#6366f1' : color, filter: 'blur(8px)', opacity: 0.2 }}
        />
      </div>

      {/* Device info */}
      <div className="text-center w-full px-1">
        <div className="text-[10px] font-bold text-slate-200 leading-tight line-clamp-2">{name}</div>
        <div className="text-[9px] text-slate-600 mt-0.5 flex items-center justify-center gap-1">
          <PlatformIcon platform={device.platform} size={9} />
          {device.platform?.toUpperCase() === 'IOS' ? 'iOS' : 'Android'} {device.platformVersion ?? '—'}
        </div>
      </div>

      {/* Status badge */}
      <span
        className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider"
        style={{ color: s.color, background: s.bg }}
      >
        <s.Icon size={8} />
        {s.label}
      </span>

      {/* App config badge */}
      {appConfig && (
        <div className="w-full text-center">
          <div className="text-[8px] font-semibold text-slate-500 truncate">
            {appConfig.appName || 'App'}
            {appConfig.appVersion ? ` v${appConfig.appVersion}` : ''}
          </div>
          <div className="text-[8px] text-slate-600 truncate">
            {appConfig.source}
          </div>
        </div>
      )}

      {/* CONFIGURADO badge — only when selected */}
      <AnimatePresence>
        {selected && (
          <motion.span
            initial={{ opacity: 0, scale: 0.8, y: 4 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.8, y: 4 }}
            transition={{ duration: 0.2 }}
            className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[9px] font-black uppercase tracking-wider"
            style={{
              background: 'rgba(99,102,241,0.2)',
              border:     '1px solid rgba(99,102,241,0.4)',
              color:      '#818cf8',
            }}
          >
            <Check size={8} />
            CONFIGURADO
          </motion.span>
        )}
      </AnimatePresence>

      {/* Toggle button */}
      <button
        onClick={onToggle}
        className="w-full flex items-center justify-center gap-1.5 py-1.5 rounded-lg text-[9px] font-bold transition-all mt-0.5"
        style={{
          background:   selected ? 'rgba(99,102,241,0.25)' : 'rgba(255,255,255,0.05)',
          border:       selected ? '1px solid rgba(99,102,241,0.4)' : '1px solid rgba(255,255,255,0.08)',
          color:        selected ? '#818cf8' : '#64748b',
          transition:   'all .2s',
        }}
        onMouseEnter={e => {
          const el = e.currentTarget as HTMLButtonElement
          el.style.background = selected ? 'rgba(99,102,241,0.15)' : 'rgba(255,255,255,0.08)'
        }}
        onMouseLeave={e => {
          const el = e.currentTarget as HTMLButtonElement
          el.style.background = selected ? 'rgba(99,102,241,0.25)' : 'rgba(255,255,255,0.05)'
        }}
      >
        {/* Custom checkbox */}
        <div
          className="w-3 h-3 rounded flex items-center justify-center flex-shrink-0"
          style={{
            background: selected ? '#6366f1' : 'transparent',
            border:     selected ? '1px solid #6366f1' : '1px solid #475569',
            transition: 'all .2s',
          }}
        >
          {selected && <Check size={8} color="white" />}
        </div>
        Usar dispositivo
      </button>
    </motion.div>
  )
}
