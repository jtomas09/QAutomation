import React, { useState, useEffect, useCallback } from 'react'
import { motion } from 'framer-motion'
import { Settings, Wifi, WifiOff, Activity } from 'lucide-react'
import { getDevices } from '../../api'
import type { PhysicalDevice } from '../../types'

import ip15  from '../../assets/devices/iphone-15.svg'
import p8pro from '../../assets/devices/pixel-8-pro.svg'
import s24   from '../../assets/devices/galaxy-s24.svg'
import a56   from '../../assets/devices/galaxy-a56.svg'
import rn13  from '../../assets/devices/redmi-note13.svg'

// ─── Image / accent helpers ───────────────────────────────────────────────────

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
    return { color: '#a8b8d8', glow: 'rgba(168,184,216,0.3)' }
  return { color: '#3DDC84', glow: 'rgba(61,220,132,0.35)' }
}

// ─── Status helpers ───────────────────────────────────────────────────────────

type CardStatus = 'available' | 'inuse' | 'offline'

function mapStatus(raw: string): CardStatus {
  const u = (raw ?? '').toUpperCase()
  if (u === 'AVAILABLE')              return 'available'
  if (u === 'BUSY' || u === 'INUSE') return 'inuse'
  return 'offline'
}

const STATUS: Record<CardStatus, { label: string; color: string; bg: string; Icon: React.ElementType }> = {
  available: { label: 'Disponible', color: '#10b981', bg: 'rgba(16,185,129,0.12)',  Icon: Wifi     },
  inuse:     { label: 'En uso',     color: '#6366f1', bg: 'rgba(99,102,241,0.12)',  Icon: Activity },
  offline:   { label: 'Offline',    color: '#f43f5e', bg: 'rgba(244,63,94,0.12)',   Icon: WifiOff  },
}

// ─── Component ────────────────────────────────────────────────────────────────

interface Props { onManage?: () => void }

export default function ConnectedDevices({ onManage }: Props) {
  const [devices, setDevices] = useState<PhysicalDevice[]>([])

  const refresh = useCallback(async () => {
    try {
      const data = await getDevices()
      setDevices(data)
    } catch {
      // Silently keep last state — widget should never crash the dashboard
    }
  }, [])

  useEffect(() => {
    refresh()
    const id = setInterval(refresh, 15_000)
    return () => clearInterval(id)
  }, [refresh])

  const available = devices.filter(d => mapStatus(d.status) === 'available').length
  const inuse     = devices.filter(d => mapStatus(d.status) === 'inuse').length

  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, ease: 'easeOut' }}
      className="overflow-hidden rounded-2xl"
      style={{
        background: 'var(--panel-bg)',
        border: '1px solid var(--panel-border)',
        boxShadow: 'var(--panel-shadow)',
      }}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-5 py-4"
        style={{ borderBottom: '1px solid var(--panel-divide)' }}
      >
        <div>
          <div className="text-sm font-bold text-slate-100">Dispositivos Conectados</div>
          <div className="text-xs text-slate-500 mt-0.5">
            {devices.length === 0
              ? <span className="text-slate-600">Sin dispositivos detectados</span>
              : <>
                  <span style={{ color: '#10b981' }}>{available} disponible{available !== 1 ? 's' : ''}</span>
                  <span className="mx-1.5 opacity-30">·</span>
                  <span style={{ color: '#6366f1' }}>{inuse} en uso</span>
                </>
            }
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

      {/* Device cards */}
      {devices.length === 0 ? (
        <div className="flex items-center justify-center py-10 text-xs text-slate-600">
          Conecta un dispositivo al Runner para verlo aquí
        </div>
      ) : (
        <div className="grid grid-cols-5 gap-3 p-4">
          {devices.map((device, i) => (
            <DeviceCard key={device.udid} device={device} index={i} />
          ))}
        </div>
      )}
    </motion.div>
  )
}

// ─── DeviceCard ───────────────────────────────────────────────────────────────

function DeviceCard({ device, index }: { device: PhysicalDevice; index: number }) {
  const statusKey = mapStatus(device.status)
  const s         = STATUS[statusKey]
  const { color, glow } = accentFor(device)
  const img       = pickImage(device)
  const name      = device.deviceName || device.model || 'Dispositivo'

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.07, duration: 0.4, ease: 'easeOut' }}
      whileHover={{ y: -4, scale: 1.02, transition: { duration: 0.25, ease: 'easeOut' } }}
      className="relative flex flex-col items-center gap-2 p-3 rounded-xl cursor-pointer overflow-hidden"
      style={{
        background: 'var(--btn-bg)',
        border: `1px solid ${color}22`,
        boxShadow: `0 0 20px ${glow.replace('0.35', '0.08')}`,
        transition: 'box-shadow 0.3s ease, border-color 0.3s ease',
      }}
      onMouseEnter={e => {
        const el = e.currentTarget as HTMLDivElement
        el.style.boxShadow = `0 8px 32px ${glow}, 0 0 0 1px ${color}33`
        el.style.borderColor = `${color}44`
      }}
      onMouseLeave={e => {
        const el = e.currentTarget as HTMLDivElement
        el.style.boxShadow = `0 0 20px ${glow.replace('0.35', '0.08')}`
        el.style.borderColor = `${color}22`
      }}
    >
      {/* Ambient glow */}
      <div
        className="absolute top-0 right-0 w-20 h-20 pointer-events-none rounded-full"
        style={{ background: `radial-gradient(circle at top right, ${glow.replace('0.35', '0.15')} 0%, transparent 70%)` }}
      />

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
      <div className="relative w-full flex justify-center" style={{ height: 110 }}>
        <motion.img
          src={img}
          alt={name}
          className="h-full w-auto object-contain relative z-10"
          style={{ filter: `drop-shadow(0 0 14px ${glow}) drop-shadow(0 4px 8px rgba(0,0,0,0.5))` }}
          whileHover={{
            filter: `drop-shadow(0 0 22px ${glow}) drop-shadow(0 6px 12px rgba(0,0,0,0.6))`,
            transition: { duration: 0.3 },
          }}
        />
        <div
          className="absolute bottom-0 left-1/2 -translate-x-1/2 w-12 h-3 rounded-full pointer-events-none"
          style={{ background: color, filter: 'blur(8px)', opacity: 0.2 }}
        />
      </div>

      {/* Device info */}
      <div className="text-center w-full px-1">
        <div className="text-[10px] font-bold text-slate-200 leading-tight line-clamp-2">{name}</div>
        <div className="text-[9px] text-slate-600 mt-0.5">
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
    </motion.div>
  )
}
