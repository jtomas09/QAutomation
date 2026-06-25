import React, { useState, useRef, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { ChevronDown, PlayCircle, HardDrive, Link2, Cpu } from 'lucide-react'

const MODES: Array<{ id: string; label: string; icon: React.ReactNode; desc: string }> = [
  {
    id: 'INSTALLED',
    label: 'App Instalada (Play Store)',
    desc: 'La app ya está instalada en el dispositivo',
    icon: <PlayCircle size={13} color="#60a5fa" />,
  },
  {
    id: 'APK',
    label: 'APK Local',
    desc: 'Instala un archivo APK desde el equipo',
    icon: <HardDrive size={13} color="#34d399" />,
  },
  {
    id: 'DEEP_LINK',
    label: 'Deep Link',
    desc: 'Inicia la app con un URL scheme',
    icon: <Link2 size={13} color="#f59e0b" />,
  },
  {
    id: 'APPIUM_SESSION',
    label: 'Appium Session',
    desc: 'Conecta a una sesión Appium existente',
    icon: <Cpu size={13} color="#a78bfa" />,
  },
]

interface ExecutionModeSelectorProps {
  value: string
  onChange: (mode: string) => void
}

export function ExecutionModeSelector({ value, onChange }: ExecutionModeSelectorProps) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const current = MODES.find((m) => m.id === value) ?? MODES[0]

  return (
    <div ref={ref} style={{ position: 'relative', flex: 1 }}>
      <button
        onClick={() => setOpen((p) => !p)}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '10px 12px',
          background: 'rgba(255,255,255,0.03)',
          border: '1px solid rgba(255,255,255,0.1)',
          borderRadius: 8,
          cursor: 'pointer',
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
            background: 'rgba(96,165,250,0.12)',
            border: '1px solid rgba(96,165,250,0.25)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          {current.icon}
        </div>

        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              fontSize: 13,
              fontWeight: 600,
              color: '#e2e8f0',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              lineHeight: 1.3,
            }}
          >
            {current.label}
          </div>
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
            {MODES.map((mode) => (
              <button
                key={mode.id}
                onClick={() => { onChange(mode.id); setOpen(false) }}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 10,
                  width: '100%',
                  padding: '10px 14px',
                  background: mode.id === value ? 'rgba(99,102,241,0.1)' : 'none',
                  border: 'none',
                  borderBottom: '1px solid rgba(255,255,255,0.04)',
                  cursor: 'pointer',
                  textAlign: 'left',
                  transition: 'background 0.1s',
                }}
                onMouseEnter={(e) => {
                  if (mode.id !== value) e.currentTarget.style.background = 'rgba(255,255,255,0.04)'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.background = mode.id === value ? 'rgba(99,102,241,0.1)' : 'transparent'
                }}
              >
                <div
                  style={{
                    width: 26,
                    height: 26,
                    borderRadius: 6,
                    background: 'rgba(255,255,255,0.05)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                  }}
                >
                  {mode.icon}
                </div>
                <div>
                  <div style={{ fontSize: 12, fontWeight: 600, color: mode.id === value ? '#818cf8' : '#e2e8f0' }}>
                    {mode.label}
                  </div>
                  <div style={{ fontSize: 10, color: '#475569', marginTop: 1 }}>{mode.desc}</div>
                </div>
                {mode.id === value && (
                  <div
                    style={{
                      marginLeft: 'auto',
                      width: 6,
                      height: 6,
                      borderRadius: '50%',
                      background: '#6366f1',
                      flexShrink: 0,
                    }}
                  />
                )}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
