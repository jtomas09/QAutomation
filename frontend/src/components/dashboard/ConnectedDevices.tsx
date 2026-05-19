import React from 'react'
import { motion } from 'framer-motion'
import { Settings, Wifi, WifiOff, Activity } from 'lucide-react'

interface Device {
  name:   string
  os:     string
  status: 'available' | 'inuse' | 'offline'
}

const DEVICES: Device[] = [
  { name: 'Galaxy A56 5G', os: 'Android 14', status: 'available' },
  { name: 'Pixel 8 Pro',   os: 'Android 14', status: 'inuse'     },
  { name: 'iPhone 15',     os: 'iOS 17.4',   status: 'available' },
  { name: 'Galaxy S24',    os: 'Android 14', status: 'available' },
  { name: 'Redmi Note 13', os: 'Android 13', status: 'inuse'     },
]

const STATUS_COLOR = {
  available: '#10b981',
  inuse:     '#6366f1',
  offline:   '#f43f5e',
}
const STATUS_BG = {
  available: 'rgba(16,185,129,0.12)',
  inuse:     'rgba(99,102,241,0.12)',
  offline:   'rgba(244,63,94,0.12)',
}
const STATUS_LABEL = {
  available: 'Disponible',
  inuse:     'En uso',
  offline:   'Offline',
}
const StatusIcon = ({ status }: { status: Device['status'] }) => {
  if (status === 'available') return <Wifi size={10} />
  if (status === 'inuse')     return <Activity size={10} />
  return <WifiOff size={10} />
}

function PhoneIcon({ color }: { color: string }) {
  return (
    <svg width="28" height="44" viewBox="0 0 28 44" fill="none">
      <rect x="1" y="1" width="26" height="42" rx="4" stroke={color} strokeWidth="1.5" fill={`${color}10`} />
      <rect x="8" y="3" width="12" height="2" rx="1" fill={color} opacity="0.5" />
      <circle cx="14" cy="40" r="1.5" fill={color} opacity="0.4" />
      <rect x="4" y="8" width="20" height="28" rx="2" fill={`${color}15`} />
    </svg>
  )
}

export default function ConnectedDevices() {
  const available = DEVICES.filter(d => d.status === 'available').length
  const inuse     = DEVICES.filter(d => d.status === 'inuse').length

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut', delay: 0.05 }}
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
            {' · '}
            <span style={{ color: '#6366f1' }}>{inuse} en uso</span>
          </div>
        </div>
        <button
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold text-slate-400 hover:text-slate-200 transition-colors"
          style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)' }}
        >
          <Settings size={11} />
          Gestionar
        </button>
      </div>

      {/* Device grid */}
      <div className="grid grid-cols-5 gap-3 p-4">
        {DEVICES.map((d, i) => {
          const color = STATUS_COLOR[d.status]
          const bg    = STATUS_BG[d.status]

          return (
            <motion.div
              key={d.name}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              transition={{ delay: i * 0.06, duration: 0.3 }}
              whileHover={{ y: -3, transition: { duration: 0.2 } }}
              className="flex flex-col items-center gap-2.5 p-3 rounded-xl cursor-pointer transition-colors"
              style={{
                background: 'rgba(255,255,255,0.03)',
                border: `1px solid ${color}22`,
                boxShadow: `0 0 16px ${color}08`,
              }}
            >
              {/* Phone SVG */}
              <div className="relative">
                <PhoneIcon color={color} />
                {/* Status pulse on top right */}
                {d.status !== 'offline' && (
                  <motion.div
                    className="absolute -top-0.5 -right-0.5 w-2.5 h-2.5 rounded-full"
                    style={{ background: color, boxShadow: `0 0 6px ${color}` }}
                    animate={{ opacity: d.status === 'inuse' ? [1, 0.4, 1] : 1 }}
                    transition={{ duration: 1.5, repeat: Infinity }}
                  />
                )}
              </div>

              {/* Device name */}
              <div className="text-center">
                <div className="text-[10px] font-bold text-slate-200 leading-tight line-clamp-2">{d.name}</div>
                <div className="text-[9px] text-slate-600 mt-0.5">{d.os}</div>
              </div>

              {/* Status badge */}
              <span
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-bold uppercase tracking-wider"
                style={{ color, background: bg }}
              >
                <StatusIcon status={d.status} />
                {STATUS_LABEL[d.status]}
              </span>
            </motion.div>
          )
        })}
      </div>
    </motion.div>
  )
}
