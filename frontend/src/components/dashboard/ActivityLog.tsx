import React, { useEffect, useRef, useState, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Trash2, ExternalLink, Terminal, FileText, Copy, Download, X, Check } from 'lucide-react'
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

function buildPlainText(logs: LogEntry[]): string {
  return logs
    .map(e => `[${e.time}] ${e.level.padEnd(5)}  ${e.message}`)
    .join('\n')
}

export default function ActivityLog({ logs, onClear, onViewAll }: Props) {
  const bottomRef  = useRef<HTMLDivElement>(null)
  const [showExtract, setShowExtract] = useState(false)
  const [copied,      setCopied]      = useState(false)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [logs])

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(buildPlainText(logs))
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* clipboard not available */
    }
  }, [logs])

  const handleDownload = useCallback(() => {
    const text = buildPlainText(logs)
    const blob = new Blob([text], { type: 'text/plain' })
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href     = url
    a.download = `log_${new Date().toISOString().replace(/[:.]/g, '-')}.txt`
    a.click()
    URL.revokeObjectURL(url)
  }, [logs])

  return (
    <div
      className="flex flex-col h-full overflow-hidden rounded-2xl"
      style={{
        background: 'var(--panel-bg)',
        border: '1px solid var(--panel-border)',
        boxShadow: 'var(--panel-shadow)',
      }}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-5 py-4 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--panel-divide)' }}
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
            style={{ background: 'var(--btn-bg)', border: '1px solid var(--btn-border)' }}
          >
            <Trash2 size={11} />
            Limpiar
          </button>
          <button
            onClick={() => setShowExtract(true)}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-[11px] font-semibold text-emerald-400 hover:text-emerald-300 transition-colors"
            style={{ background: 'rgba(16,185,129,0.08)', border: '1px solid rgba(16,185,129,0.2)' }}
          >
            <FileText size={11} />
            Extraer Log
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
        style={{ background: 'var(--terminal-bg)' }}
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

      {/* ── Extract Log Modal ────────────────────────────────────────────────── */}
      <AnimatePresence>
        {showExtract && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center p-6"
            style={{ background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)' }}
            onClick={e => { if (e.target === e.currentTarget) setShowExtract(false) }}
          >
            <motion.div
              initial={{ opacity: 0, scale: 0.94, y: 16 }}
              animate={{ opacity: 1, scale: 1,    y: 0  }}
              exit={{    opacity: 0, scale: 0.94, y: 16 }}
              transition={{ duration: 0.22 }}
              className="flex flex-col rounded-2xl overflow-hidden w-full max-w-3xl"
              style={{
                background: '#0d1117',
                border: '1px solid rgba(255,255,255,0.1)',
                boxShadow: '0 24px 80px rgba(0,0,0,0.6)',
                maxHeight: '80vh',
              }}
            >
              {/* Modal header */}
              <div
                className="flex items-center justify-between px-5 py-4 flex-shrink-0"
                style={{ borderBottom: '1px solid rgba(255,255,255,0.07)' }}
              >
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-xl flex items-center justify-center"
                    style={{ background: 'rgba(16,185,129,0.15)' }}>
                    <FileText size={15} className="text-emerald-400" />
                  </div>
                  <div>
                    <div className="text-sm font-bold text-slate-100">Extraer Log en Texto</div>
                    <div className="text-[11px] text-slate-500 mt-0.5">{logs.length} entradas</div>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={handleCopy}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold transition-all"
                    style={{
                      background: copied ? 'rgba(16,185,129,0.2)' : 'rgba(255,255,255,0.06)',
                      border: `1px solid ${copied ? 'rgba(16,185,129,0.4)' : 'rgba(255,255,255,0.1)'}`,
                      color: copied ? '#10b981' : '#94a3b8',
                    }}
                  >
                    {copied ? <Check size={11} /> : <Copy size={11} />}
                    {copied ? 'Copiado!' : 'Copiar'}
                  </button>
                  <button
                    onClick={handleDownload}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold text-indigo-300 transition-colors hover:text-indigo-200"
                    style={{ background: 'rgba(99,102,241,0.12)', border: '1px solid rgba(99,102,241,0.25)' }}
                  >
                    <Download size={11} />
                    Descargar .txt
                  </button>
                  <button
                    onClick={() => setShowExtract(false)}
                    className="w-7 h-7 flex items-center justify-center rounded-lg text-slate-500 hover:text-slate-300 transition-colors"
                    style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.08)' }}
                  >
                    <X size={13} />
                  </button>
                </div>
              </div>

              {/* Plain-text body */}
              <div className="flex-1 min-h-0 overflow-y-auto">
                {logs.length === 0 ? (
                  <div className="flex items-center justify-center h-40 text-xs text-slate-600">
                    Sin entradas de log
                  </div>
                ) : (
                  <pre
                    className="p-5 text-[11px] leading-relaxed font-mono whitespace-pre-wrap break-all select-all"
                    style={{ color: '#94a3b8' }}
                  >
                    {buildPlainText(logs)}
                  </pre>
                )}
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
