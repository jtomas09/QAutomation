import React, { useState, useRef, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ChevronDown, Package } from 'lucide-react'
import type { DeviceAppConfig } from '../../types'

interface ApplicationSelectorProps {
  appConfigs: Record<string, DeviceAppConfig>
  selected: DeviceAppConfig | null
  onSelect: (appName: string) => void
}

export function ApplicationSelector({ appConfigs, selected, onSelect }: ApplicationSelectorProps) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  const apps = [...new Map(
    Object.values(appConfigs).map((c) => [c.appName, c])
  ).values()]

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div ref={ref} style={{ position: 'relative', flex: 1 }}>
      <button
        onClick={() => apps.length > 0 && setOpen((p) => !p)}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '10px 12px',
          background: 'rgba(255,255,255,0.03)',
          border: '1px solid rgba(255,255,255,0.1)',
          borderRadius: 8,
          cursor: apps.length > 0 ? 'pointer' : 'default',
          textAlign: 'left',
          color: '#d4d4d4',
          transition: 'border-color 0.15s',
        }}
        onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'rgba(99,102,241,0.4)')}
        onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)')}
      >
        <div
          style={{
            width: 28,
            height: 28,
            borderRadius: 6,
            background: selected ? 'rgba(251,146,60,0.15)' : 'rgba(255,255,255,0.04)',
            border: `1px solid ${selected ? 'rgba(251,146,60,0.3)' : 'rgba(255,255,255,0.08)'}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Package size={14} color={selected ? '#fb923c' : '#374151'} />
        </div>

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
            {selected?.appName ?? 'Seleccionar aplicación'}
          </div>
          {selected && (
            <div style={{ fontSize: 10, color: '#64748b', marginTop: 2, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {selected.appPackage || selected.bundleId || '—'}
            </div>
          )}
        </div>

        <ChevronDown
          size={12}
          color="#4b5563"
          style={{ flexShrink: 0, transform: open ? 'rotate(180deg)' : 'none', transition: 'transform 0.15s' }}
        />
      </button>

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
            {apps.length === 0 ? (
              <div style={{ padding: '12px 14px', fontSize: 11, color: '#475569' }}>
                Sin aplicaciones configuradas
              </div>
            ) : (
              apps.map((app) => (
                <button
                  key={app.appName}
                  onClick={() => { onSelect(app.appName); setOpen(false) }}
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
                  <div
                    style={{
                      width: 28,
                      height: 28,
                      borderRadius: 6,
                      background: 'rgba(251,146,60,0.12)',
                      border: '1px solid rgba(251,146,60,0.25)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <Package size={13} color="#fb923c" />
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 12, fontWeight: 600, color: '#e2e8f0' }}>{app.appName}</div>
                    <div style={{ fontSize: 10, color: '#64748b', marginTop: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {app.appPackage || app.bundleId || '—'}
                    </div>
                  </div>
                  <span
                    style={{
                      fontSize: 9,
                      padding: '2px 6px',
                      borderRadius: 6,
                      background: 'rgba(99,102,241,0.12)',
                      color: '#818cf8',
                      fontWeight: 600,
                      flexShrink: 0,
                    }}
                  >
                    {app.platform}
                  </span>
                </button>
              ))
            )}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
