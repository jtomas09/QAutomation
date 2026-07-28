import React, { useMemo, useState, useCallback } from 'react'
import { motion } from 'framer-motion'
import { Wrench, Copy, Download, X, Check } from 'lucide-react'
import type { ExecutionEvent, EventCategory } from '../../types'

const CHIPS: EventCategory[] = ['TECHNICAL', 'DEBUG', 'TRACE']

const SEVERITY_TEXT_COLOR: Record<string, string> = {
  ERROR: '#f43f5e',
  WARN:  '#f59e0b',
  INFO:  '#8fa39d',
}

function buildPlainText(entries: ExecutionEvent[]): string {
  return entries
    .map(e => `[${e.timestamp?.slice(11, 19) ?? ''}] ${e.category.padEnd(9)} ${e.message}`)
    .join('\n')
}

interface Props {
  events: ExecutionEvent[]
  onClose: () => void
}

/**
 * Reemplaza el modal "Log Técnico" cuando hay eventos de dominio disponibles —
 * mismo contenido completo (nada se pierde: TECHNICAL/DEBUG/TRACE siguen todos
 * ahí), pero filtrable por categoría con chips en vez de un dump de texto plano
 * sin estructura. BUSINESS no aparece aquí — ya vive en el Timeline principal.
 */
function TechnicalConsole({ events, onClose }: Props) {
  const [active, setActive] = useState<Set<EventCategory>>(new Set(CHIPS))
  const [copied, setCopied] = useState(false)

  const technical = useMemo(() => events.filter(e => e.category !== 'BUSINESS'), [events])
  const filtered   = useMemo(() => technical.filter(e => active.has(e.category)), [technical, active])

  const toggle = useCallback((cat: EventCategory) => {
    setActive(prev => {
      const next = new Set(prev)
      next.has(cat) ? next.delete(cat) : next.add(cat)
      return next
    })
  }, [])

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(buildPlainText(filtered))
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch { /* clipboard unavailable */ }
  }, [filtered])

  const handleDownload = useCallback(() => {
    const blob = new Blob([buildPlainText(filtered)], { type: 'text/plain' })
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href     = url
    a.download = `log_tecnico_${new Date().toISOString().replace(/[:.]/g, '-')}.txt`
    a.click()
    URL.revokeObjectURL(url)
  }, [filtered])

  return (
    <motion.div
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center p-6"
      style={{ background: 'rgba(0,0,0,0.75)', backdropFilter: 'blur(4px)' }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.94, y: 16 }}
        animate={{ opacity: 1, scale: 1,    y: 0  }}
        exit={{    opacity: 0, scale: 0.94, y: 16 }}
        transition={{ duration: 0.22 }}
        className="flex flex-col rounded-2xl overflow-hidden w-full max-w-3xl"
        style={{ background: '#0d1117', border: '1px solid rgba(255,255,255,0.1)', boxShadow: '0 24px 80px rgba(0,0,0,0.6)', maxHeight: '80vh' }}
      >
        <div className="flex items-center justify-between px-5 py-4 flex-shrink-0" style={{ borderBottom: '1px solid rgba(255,255,255,0.07)' }}>
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl flex items-center justify-center" style={{ background: 'rgba(249,115,22,0.15)' }}>
              <Wrench size={15} color="#f97316" />
            </div>
            <div>
              <div className="text-sm font-bold text-slate-100">Log Técnico</div>
              <div className="text-[11px] text-slate-500 mt-0.5">{filtered.length} de {technical.length} entradas — infraestructura completa</div>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={handleCopy}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold transition-all"
              style={{ background: copied ? 'rgba(16,185,129,0.2)' : 'rgba(255,255,255,0.06)', border: `1px solid ${copied ? 'rgba(16,185,129,0.4)' : 'rgba(255,255,255,0.1)'}`, color: copied ? '#10b981' : '#94a3b8' }}>
              {copied ? <Check size={11} /> : <Copy size={11} />}
              {copied ? 'Copiado!' : 'Copiar'}
            </button>
            <button onClick={handleDownload}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold text-indigo-300 transition-colors hover:text-indigo-200"
              style={{ background: 'rgba(99,102,241,0.12)', border: '1px solid rgba(99,102,241,0.25)' }}>
              <Download size={11} />
              Descargar .txt
            </button>
            <button onClick={onClose}
              className="w-7 h-7 flex items-center justify-center rounded-lg text-slate-500 hover:text-slate-300 transition-colors"
              style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.08)' }}>
              <X size={13} />
            </button>
          </div>
        </div>

        <div className="flex items-center gap-2 px-5 py-2.5 flex-shrink-0" style={{ borderBottom: '1px solid rgba(255,255,255,0.07)' }}>
          {CHIPS.map(cat => (
            <button
              key={cat}
              onClick={() => toggle(cat)}
              className="text-[10.5px] font-semibold px-2.5 py-1 rounded-full font-mono transition-colors"
              style={active.has(cat)
                ? { background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.4)', color: '#a5b4fc' }
                : { background: 'transparent', border: '1px solid rgba(255,255,255,0.12)', color: '#64748b' }}
            >
              {cat}
            </button>
          ))}
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto">
          {filtered.length === 0 ? (
            <div className="flex items-center justify-center h-40 text-xs text-slate-600">Sin entradas para las categorías seleccionadas</div>
          ) : (
            <pre className="p-5 text-[11px] leading-relaxed font-mono whitespace-pre-wrap break-all select-all" style={{ color: '#8fa39d' }}>
              {filtered.map((e, i) => (
                <div key={`${e.timestamp}-${i}`} style={{ color: SEVERITY_TEXT_COLOR[e.severity] ?? '#8fa39d' }}>
                  [{e.timestamp?.slice(11, 19) ?? ''}] {e.category.padEnd(9)} {e.message}
                </div>
              ))}
            </pre>
          )}
        </div>
      </motion.div>
    </motion.div>
  )
}

export default React.memo(TechnicalConsole)
