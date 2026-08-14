import React, { useState, useRef, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ChevronDown, Smartphone } from 'lucide-react'
import type { PhysicalDevice } from '../../types'
import { resolveDeviceDisplayName } from '../../utils/displayNames'

interface DeviceSelectorProps {
  devices: PhysicalDevice[]
  selected: PhysicalDevice | null
  onSelect: (deviceName: string) => void
}

const STATUS_COLORS: Record<string, { dot: string; label: string; bg: string }> = {
  AVAILABLE: { dot: '#4ade80', label: 'Disponible', bg: 'rgba(74,222,128,0.12)' },
  BUSY:      { dot: '#f59e0b', label: 'Ocupado',    bg: 'rgba(245,158,11,0.12)'  },
  OFFLINE:   { dot: '#64748b', label: 'Offline',    bg: 'rgba(100,116,139,0.12)' },
}

function PlatformIcon({ platform }: { platform: string }) {
  const isIOS = platform.toUpperCase() === 'IOS'
  return (
    <div
      style={{
        width: 28,
        height: 28,
        borderRadius: 6,
        background: isIOS ? 'rgba(167,139,250,0.15)' : 'rgba(52,211,153,0.12)',
        border: `1px solid ${isIOS ? 'rgba(167,139,250,0.3)' : 'rgba(52,211,153,0.25)'}`,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
      }}
    >
      <Smartphone size={14} color={isIOS ? '#a78bfa' : '#34d399'} />
    </div>
  )
}

export function DeviceSelector({ devices, selected, onSelect }: DeviceSelectorProps) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const status = selected ? STATUS_COLORS[selected.status] ?? STATUS_COLORS.OFFLINE : null

  return (
    <div ref={ref} style={{ position: 'relative', flex: 1 }}>
      {/* Trigger */}
      <button
        onClick={() => devices.length > 0 && setOpen((p) => !p)}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '10px 12px',
          background: 'rgba(255,255,255,0.03)',
          border: '1px solid rgba(255,255,255,0.1)',
          borderRadius: 8,
          cursor: devices.length > 0 ? 'pointer' : 'default',
          textAlign: 'left',
          color: '#d4d4d4',
          transition: 'border-color 0.15s',
        }}
        onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'rgba(99,102,241,0.4)')}
        onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)')}
      >
        {selected ? (
          <PlatformIcon platform={selected.platform} />
        ) : (
          <div
            style={{
              width: 28,
              height: 28,
              borderRadius: 6,
              background: 'rgba(255,255,255,0.04)',
              border: '1px solid rgba(255,255,255,0.08)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <Smartphone size={14} color="#374151" />
          </div>
        )}

        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              fontSize: 13,
              fontWeight: 600,
              color: selected ? '#e2e8f0' : '#475569',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              lineHeight: 1.3,
            }}
          >
            {selected ? resolveDeviceDisplayName(selected).title : 'Seleccionar dispositivo'}
          </div>
          {selected && (
            <div style={{ fontSize: 11, color: '#64748b', marginTop: 2, lineHeight: 1 }}>
              {selected.platform} {selected.platformVersion ?? ''}
            </div>
          )}
        </div>

        {/* Status badge */}
        {status && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              padding: '2px 8px',
              borderRadius: 10,
              background: status.bg,
              flexShrink: 0,
            }}
          >
            <div
              style={{
                width: 5,
                height: 5,
                borderRadius: '50%',
                backgroundColor: status.dot,
              }}
            />
            <span style={{ fontSize: 9, color: status.dot, fontWeight: 600 }}>
              {status.label}
            </span>
          </div>
        )}

        <ChevronDown
          size={12}
          color="#4b5563"
          style={{ flexShrink: 0, transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }}
        />
      </button>

      {/* Dropdown */}
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 4 }}
            transition={{ duration: 0.12 }}
            style={{
              position: 'absolute',
              top: '100%',
              left: 0,
              right: 0,
              marginTop: 4,
              background: '#161b22',
              border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 10,
              zIndex: 300,
              boxShadow: '0 16px 40px rgba(0,0,0,0.6)',
              overflow: 'hidden',
            }}
          >
            {devices.length === 0 ? (
              <div style={{ padding: '12px 14px', fontSize: 11, color: '#475569' }}>
                Sin dispositivos disponibles
              </div>
            ) : (
              devices.map((d) => {
                const st = STATUS_COLORS[d.status] ?? STATUS_COLORS.OFFLINE
                return (
                  <button
                    key={d.udid}
                    onClick={() => { onSelect(d.deviceName); setOpen(false) }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 10,
                      width: '100%',
                      padding: '10px 14px',
                      background: 'none',
                      border: 'none',
                      borderBottom: '1px solid rgba(255,255,255,0.04)',
                      cursor: 'pointer',
                      textAlign: 'left',
                      transition: 'background 0.1s',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.background = 'rgba(99,102,241,0.1)')}
                    onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                  >
                    <PlatformIcon platform={d.platform} />
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ fontSize: 12, fontWeight: 600, color: '#e2e8f0' }}>
                        {resolveDeviceDisplayName(d).title}
                      </div>
                      <div style={{ fontSize: 10, color: '#64748b', marginTop: 1 }}>
                        {d.platform} {d.platformVersion ?? ''} · {d.udid.slice(0, 12)}…
                      </div>
                    </div>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 4,
                        padding: '2px 7px',
                        borderRadius: 8,
                        background: st.bg,
                      }}
                    >
                      <div style={{ width: 5, height: 5, borderRadius: '50%', backgroundColor: st.dot }} />
                      <span style={{ fontSize: 9, color: st.dot, fontWeight: 600 }}>{st.label}</span>
                    </div>
                  </button>
                )
              })
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
