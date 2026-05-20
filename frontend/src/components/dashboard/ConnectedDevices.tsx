import React from 'react'
import { motion } from 'framer-motion'
import { Settings, Wifi, WifiOff, Activity, Zap } from 'lucide-react'

import ip15    from '../../assets/devices/iphone-15.svg'
import p8pro   from '../../assets/devices/pixel-8-pro.svg'
import s24     from '../../assets/devices/galaxy-s24.svg'
import a56     from '../../assets/devices/galaxy-a56.svg'
import rn13    from '../../assets/devices/redmi-note13.svg'

interface Device {
  id:     string
  name:   string
  os:     string
  status: 'available' | 'inuse' | 'offline'
  image:  string
  accent: string
  glow:   string
}

const DEVICES: Device[] = [
  {
    id: 'a56',   name: 'Galaxy A56 5G', os: 'Android 14',
    status: 'available', image: a56,
    accent: '#818cf8', glow: 'rgba(129,140,248,0.4)',
  },
  {
    id: 'p8',    name: 'Pixel 8 Pro',   os: 'Android 14',
    status: 'inuse',     image: p8pro,
    accent: '#4285f4', glow: 'rgba(66,133,244,0.45)',
  },
  {
    id: 'ip15',  name: 'iPhone 15',     os: 'iOS 17.4',
    status: 'available', image: ip15,
    accent: '#e5e5ea', glow: 'rgba(229,229,234,0.25)',
  },
  {
    id: 's24',   name: 'Galaxy S24',    os: 'Android 14',
    status: 'available', image: s24,
    accent: '#14b8a6', glow: 'rgba(20,184,166,0.4)',
  },
  {
    id: 'rn13',  name: 'Redmi Note 13', os: 'Android 13',
    status: 'inuse',     image: rn13,
    accent: '#a855f7', glow: 'rgba(168,85,247,0.4)',
  },
]

const STATUS = {
  available: { label: 'Disponible', color: '#10b981', bg: 'rgba(16,185,129,0.12)',  Icon: Wifi     },
  inuse:     { label: 'En uso',     color: '#6366f1', bg: 'rgba(99,102,241,0.12)',  Icon: Activity },
  offline:   { label: 'Offline',    color: '#f43f5e', bg: 'rgba(244,63,94,0.12)',   Icon: WifiOff  },
}

interface Props { onManage?: () => void }

export default function ConnectedDevices({ onManage }: Props) {
  const available = DEVICES.filter(d => d.status === 'available').length
  const inuse     = DEVICES.filter(d => d.status === 'inuse').length

  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.45, ease: 'easeOut' }}
      className="overflow-hidden rounded-2xl"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
        border: '1px solid rgba(255,255,255,0.08)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.4)',
      }}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-5 py-4"
        style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}
      >
        <div>
          <div className="text-sm font-bold text-slate-100">Dispositivos Conectados</div>
          <div className="text-xs text-slate-500 mt-0.5">
            <span style={{ color: '#10b981' }}>{available} disponibles</span>
            <span className="mx-1.5 opacity-30">·</span>
            <span style={{ color: '#6366f1' }}>{inuse} en uso</span>
          </div>
        </div>
        <button
          onClick={onManage}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold text-slate-400 hover:text-slate-200 transition-colors"
          style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)' }}
        >
          <Settings size={11} />
          Gestionar
        </button>
      </div>

      {/* Device cards grid */}
      <div className="grid grid-cols-5 gap-3 p-4">
        {DEVICES.map((device, i) => (
          <DeviceCard key={device.id} device={device} index={i} />
        ))}
      </div>
    </motion.div>
  )
}

function DeviceCard({ device, index }: { device: Device; index: number }) {
  const s = STATUS[device.status]
  const StatusIcon = s.Icon

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.07, duration: 0.4, ease: 'easeOut' }}
      whileHover={{
        y: -4,
        scale: 1.02,
        transition: { duration: 0.25, ease: 'easeOut' },
      }}
      className="relative flex flex-col items-center gap-2 p-3 rounded-xl cursor-pointer overflow-hidden"
      style={{
        background: 'rgba(255,255,255,0.025)',
        border: `1px solid ${device.accent}22`,
        boxShadow: `0 0 20px ${device.glow.replace('0.4', '0.08')}`,
        transition: 'box-shadow 0.3s ease, border-color 0.3s ease',
      }}
      onMouseEnter={e => {
        const el = e.currentTarget as HTMLDivElement
        el.style.boxShadow = `0 8px 32px ${device.glow}, 0 0 0 1px ${device.accent}33`
        el.style.borderColor = `${device.accent}44`
      }}
      onMouseLeave={e => {
        const el = e.currentTarget as HTMLDivElement
        el.style.boxShadow = `0 0 20px ${device.glow.replace('0.4', '0.08')}`
        el.style.borderColor = `${device.accent}22`
      }}
    >
      {/* Ambient glow bg */}
      <div
        className="absolute top-0 right-0 w-20 h-20 pointer-events-none rounded-full"
        style={{
          background: `radial-gradient(circle at top right, ${device.glow.replace('0.4', '0.15')} 0%, transparent 70%)`,
        }}
      />

      {/* Status indicator top-right */}
      <motion.div
        className="absolute top-2.5 right-2.5 w-2 h-2 rounded-full"
        style={{ background: s.color, boxShadow: `0 0 8px ${s.color}` }}
        animate={
          device.status === 'inuse'
            ? { opacity: [1, 0.35, 1], scale: [1, 1.2, 1] }
            : { opacity: 1 }
        }
        transition={{ duration: 1.6, repeat: Infinity, ease: 'easeInOut' }}
      />

      {/* Device image */}
      <div className="relative w-full flex justify-center" style={{ height: 110 }}>
        <motion.img
          src={device.image}
          alt={device.name}
          className="h-full w-auto object-contain relative z-10"
          style={{
            filter: `drop-shadow(0 0 14px ${device.glow}) drop-shadow(0 4px 8px rgba(0,0,0,0.5))`,
          }}
          whileHover={{
            filter: `drop-shadow(0 0 22px ${device.glow}) drop-shadow(0 6px 12px rgba(0,0,0,0.6))`,
            transition: { duration: 0.3 },
          }}
        />
        {/* Ground reflection */}
        <div
          className="absolute bottom-0 left-1/2 -translate-x-1/2 w-12 h-3 rounded-full pointer-events-none"
          style={{ background: device.accent, filter: 'blur(8px)', opacity: 0.2 }}
        />
      </div>

      {/* Device info */}
      <div className="text-center w-full px-1">
        <div className="text-[10px] font-bold text-slate-200 leading-tight line-clamp-2">
          {device.name}
        </div>
        <div className="text-[9px] text-slate-600 mt-0.5">{device.os}</div>
      </div>

      {/* Status badge */}
      <span
        className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider"
        style={{ color: s.color, background: s.bg }}
      >
        <StatusIcon size={8} />
        {s.label}
      </span>

      {/* In-use indicator bar */}
      {device.status === 'inuse' && (
        <div className="w-full h-0.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.05)' }}>
          <motion.div
            className="h-full rounded-full"
            style={{ background: `linear-gradient(90deg, ${device.accent}, ${device.accent}66)` }}
            animate={{ width: ['30%', '75%', '50%', '90%', '60%'] }}
            transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut' }}
          />
        </div>
      )}

      {/* Available: Zap icon */}
      {device.status === 'available' && (
        <Zap size={10} style={{ color: s.color, opacity: 0.6 }} />
      )}
    </motion.div>
  )
}
