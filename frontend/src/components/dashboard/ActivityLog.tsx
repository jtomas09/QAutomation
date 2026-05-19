import React, { useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Trash2, ExternalLink, Terminal } from 'lucide-react'
import type { LogEntry } from '../../types'

const LEVEL_COLOR: Record<string, string> = {
  INFO:    '#60a5fa',
  WARN:    '#f59e0b',
  ERROR:   '#f43f5e',
  FAIL:    '#f43f5e',
  PASS:    '#10b981',
  SUCCESS: '#10b981',
  SKIP:    '#f59e0b',
}

const LEVEL_BG: Record<string, string> = {
  INFO:    'rgba(96,165,250,0.12)',
  WARN:    'rgba(245,158,11,0.12)',
  ERROR:   'rgba(244,63,94,0.12)',
  FAIL:    'rgba(244,63,94,0.12)',
  PASS:    'rgba(16,185,129,0.12)',
  SUCCESS: 'rgba(16,185,129,0.12)',
  SKIP:    'rgba(245,158,11,0.12)',
}

interface Props {
  logs:      LogEntry[]
  onClear:   () => void
  onViewAll?: () => void
}

export default function ActivityLog({ logs, onClear, onViewAll }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [logs])

  return (
    <div
      className="flex flex-col h-full overflow-hidden rounded-2xl"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
        border: '1px solid rgba(255,255,255,0.08)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.4)',
      }}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-5 py-4 flex-shrink-0"
        style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}
      >
        <div className="flex items-center gap-2">
          <Terminal size={14} className="text-indigo-400" />
          <div>
            <div className="text-sm font-bold text-slate-100">Actividad en Tiempo Real</div>
            <div className="text-xs text-slate-500 mt-0.5">
              {logs.length} eventos recientes
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={onClear}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold text-slate-500 hover:text-slate-300 transition-colors"
            style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.07)' }}
          >
            <Trash2 size={11} />
            Limpiar
          </button>
          <button
            onClick={onViewAll}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold text-indigo-400 hover:text-indigo-300 transition-colors"
            style={{ background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.2)' }}
          >
            <ExternalLink size={11} />
            Ver todos
          </button>
        </div>
      </div>

      {/* Terminal body */}
      <div
        className="flex-1 min-h-0 overflow-y-auto px-4 py-3 font-mono text-[11px] leading-relaxed"
        style={{ background: 'rgba(0,0,0,0.3)' }}
      >
        {logs.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-slate-600">
            <Terminal size={28} className="opacity-30" />
            <span className="text-xs">Sin actividad reciente…</span>
          </div>
        ) : (
          <AnimatePresence initial={false}>
            {logs.slice(-60).map(entry => (
              <motion.div
                key={entry.id}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.2 }}
                className="flex items-start gap-2.5 py-0.5 group"
              >
                <span className="text-slate-600 flex-shrink-0 tabular-nums w-14">{entry.time}</span>
                <span
                  className="flex-shrink-0 px-1.5 py-0.5 rounded text-[9px] font-bold uppercase tracking-wider w-14 text-center"
                  style={{
                    color: LEVEL_COLOR[entry.level] ?? LEVEL_COLOR.INFO,
                    background: LEVEL_BG[entry.level] ?? LEVEL_BG.INFO,
                  }}
                >
                  {entry.level}
                </span>
                <span
                  className="flex-1 break-all"
                  style={{ color: LEVEL_COLOR[entry.level] ?? '#94a3b8' }}
                >
                  {entry.message}
                </span>
              </motion.div>
            ))}
          </AnimatePresence>
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
