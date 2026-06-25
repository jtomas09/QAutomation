import React from 'react'
import { motion } from 'framer-motion'
import { Circle, Square } from 'lucide-react'
import { RecordingTimer } from './RecordingTimer'

interface RecordingStatusProps {
  isRecording: boolean
  elapsed: number
  onToggle: () => void
}

export function RecordingStatus({ isRecording, elapsed, onToggle }: RecordingStatusProps) {
  return (
    <div
      style={{
        background: '#111827',
        border: `1px solid ${isRecording ? 'rgba(239,68,68,0.35)' : '#2A3144'}`,
        borderRadius: 12,
        padding: '14px 16px',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        minHeight: 110,
        flex: 1,
        gap: 10,
        transition: 'border-color 0.2s',
      }}
    >
      {/* Status indicator */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {isRecording ? (
          <>
            <motion.div
              animate={{ scale: [1, 1.35, 1], opacity: [1, 0.5, 1] }}
              transition={{ repeat: Infinity, duration: 1, ease: 'easeInOut' }}
              style={{
                width: 10,
                height: 10,
                borderRadius: '50%',
                backgroundColor: '#ef4444',
                boxShadow: '0 0 8px rgba(239,68,68,0.8)',
                flexShrink: 0,
              }}
            />
            <span
              style={{
                color: '#ef4444',
                fontWeight: 800,
                fontSize: 12,
                letterSpacing: 1.5,
              }}
            >
              GRABANDO
            </span>
          </>
        ) : (
          <>
            <div
              style={{
                width: 10,
                height: 10,
                borderRadius: '50%',
                backgroundColor: '#475569',
                flexShrink: 0,
              }}
            />
            <span
              style={{
                color: '#64748b',
                fontWeight: 700,
                fontSize: 12,
                letterSpacing: 1.5,
              }}
            >
              DETENIDO
            </span>
          </>
        )}
      </div>

      {/* Timer — only when recording */}
      {isRecording && <RecordingTimer elapsed={elapsed} />}

      {/* Action button */}
      <button
        onClick={onToggle}
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 7,
          padding: '9px 14px',
          borderRadius: 8,
          fontSize: 12,
          fontWeight: 700,
          cursor: 'pointer',
          letterSpacing: 0.3,
          transition: 'all 0.15s',
          width: '100%',
          ...(isRecording
            ? {
                background: 'rgba(239,68,68,0.12)',
                border: '1px solid rgba(239,68,68,0.4)',
                color: '#f87171',
              }
            : {
                background: 'linear-gradient(135deg, rgba(34,197,94,0.18), rgba(74,222,128,0.1))',
                border: '1px solid rgba(34,197,94,0.45)',
                color: '#4ade80',
              }),
        }}
      >
        {isRecording ? (
          <>
            <Square size={11} fill="#f87171" />
            Detener Grabación
          </>
        ) : (
          <>
            <Circle size={11} />
            Iniciar Grabación
          </>
        )}
      </button>
    </div>
  )
}
