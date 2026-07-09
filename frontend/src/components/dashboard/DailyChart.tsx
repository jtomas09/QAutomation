import React, { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer,
} from 'recharts'
import { getExecutions } from '../../api'
import type { ExecutionSummary } from '../../types'

interface DayEntry { day: string; Exitosas: number; Fallidas: number; Omitidas: number }

const TERMINAL_STATUSES = ['PASSED', 'COMPLETED', 'FAILED', 'ABORTED', 'INCOMPLETE']

const SERIES = [
  { key: 'Exitosas' as const, color: '#10b981' },
  { key: 'Fallidas' as const, color: '#f43f5e' },
  { key: 'Omitidas' as const, color: '#f59e0b' },
]

function buildDays(executions: ExecutionSummary[]): DayEntry[] {
  const today = new Date()
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(today)
    d.setDate(d.getDate() - (6 - i))
    const label   = d.toLocaleDateString('es-MX', { day: 'numeric', month: 'short' })
    const dateKey = d.toDateString()
    const dayExecs = executions.filter(e => new Date(e.startTime).toDateString() === dateKey)
    return {
      day:      label,
      Exitosas: dayExecs.reduce((s, e) => s + e.passed,  0),
      Fallidas: dayExecs.reduce((s, e) => s + e.failed,  0),
      Omitidas: dayExecs.reduce((s, e) => s + e.skipped, 0),
    }
  })
}

interface Props { isLive?: boolean }

function DailyChart({ isLive = false }: Props) {
  const [data,   setData]   = useState<DayEntry[]>(() => buildDays([]))
  const [hidden, setHidden] = useState<Set<string>>(new Set())

  useEffect(() => {
    const load = async () => {
      // Freeze chart during active execution — same pattern as Dashboard aggregate().
      if (isLive) return
      try {
        const execs = await getExecutions()
        setData(buildDays(execs.filter(e => TERMINAL_STATUSES.includes(e.status))))
      } catch { /* backend offline — keep last known */ }
    }
    // isLive in deps: restarts when execution ends → immediate refresh.
    load()
    const id = setInterval(load, 10_000)
    return () => clearInterval(id)
  }, [isLive])

  const toggle = (key: string) =>
    setHidden(prev => { const n = new Set(prev); n.has(key) ? n.delete(key) : n.add(key); return n })

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut', delay: 0.1 }}
      className="flex flex-col h-full overflow-hidden rounded-2xl"
      style={{
        background: 'var(--panel-bg)',
        border: '1px solid var(--panel-border)',
        boxShadow: 'var(--panel-shadow)',
      }}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-5 py-4 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--panel-divide)' }}>
        <div>
          <div className="text-sm font-bold text-slate-100">Ejecuciones por Día</div>
          <div className="text-xs mt-0.5 flex items-center gap-1.5">
            {isLive ? (
              <>
                <motion.span
                  className="w-1.5 h-1.5 rounded-full inline-block"
                  style={{ background: '#818cf8' }}
                  animate={{ opacity: [1, 0.3, 1] }}
                  transition={{ duration: 1.2, repeat: Infinity }}
                />
                <span style={{ color: '#818cf8', fontWeight: 700 }}>En ejecución — pausado</span>
              </>
            ) : <span className="text-slate-500">Últimos 7 días</span>}
          </div>
        </div>
        <div className="flex items-center gap-3">
          {SERIES.map(s => (
            <button
              key={s.key}
              onClick={() => toggle(s.key)}
              className="flex items-center gap-1.5 text-[11px] font-semibold transition-opacity"
              style={{ opacity: hidden.has(s.key) ? 0.35 : 1, color: s.color }}
            >
              <div className="w-2 h-2 rounded-full" style={{ background: s.color }} />
              {s.key}
            </button>
          ))}
        </div>
      </div>

      {/* Chart */}
      <div className="flex-1 min-h-0 px-2 py-3">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: -24 }}>
            <defs>
              {SERIES.map(s => (
                <linearGradient key={s.key} id={`grad-${s.key}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%"   stopColor={s.color} stopOpacity={0.3} />
                  <stop offset="100%" stopColor={s.color} stopOpacity={0}   />
                </linearGradient>
              ))}
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
            <XAxis dataKey="day" tick={{ fill: '#475569', fontSize: 10 }} axisLine={false} tickLine={false} />
            <YAxis tick={{ fill: '#475569', fontSize: 10 }} axisLine={false} tickLine={false} allowDecimals={false} />
            <Tooltip
              content={({ active, payload, label }) => {
                if (!active || !payload?.length) return null
                return (
                  <div
                    className="px-3 py-2.5 rounded-xl text-xs"
                    style={{
                      background: 'rgba(4,8,22,0.95)',
                      border: '1px solid rgba(255,255,255,0.1)',
                      boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
                    }}
                  >
                    <div className="font-bold text-slate-300 mb-1.5">{label}</div>
                    {payload.map((p: any) => (
                      <div key={p.dataKey} className="flex items-center gap-2 mb-0.5">
                        <div className="w-1.5 h-1.5 rounded-full" style={{ background: p.color }} />
                        <span className="text-slate-400">{p.dataKey}:</span>
                        <span className="font-bold" style={{ color: p.color }}>{p.value}</span>
                      </div>
                    ))}
                  </div>
                )
              }}
            />
            {SERIES.map(s => (
              <Area
                key={s.key}
                type="monotone"
                dataKey={s.key}
                stroke={s.color}
                strokeWidth={2}
                fill={`url(#grad-${s.key})`}
                dot={false}
                hide={hidden.has(s.key)}
                isAnimationActive={true}
              />
            ))}
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </motion.div>
  )
}

// data/hidden son estado interno propio (congelado mientras isLive=true); sin
// memo, este gráfico (Recharts) igual se re-renderiza en cada línea de log que
// actualiza el Dashboard padre, aunque su contenido no haya cambiado.
export default React.memo(DailyChart)
