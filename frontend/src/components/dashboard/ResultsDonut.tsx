import React from 'react'
import { motion } from 'framer-motion'
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts'

interface Props { passed: number; failed: number; skipped: number }

const FALLBACK = { passed: 1024, failed: 156, skipped: 67 }

export default function ResultsDonut({ passed, failed, skipped }: Props) {
  const total = passed + failed + skipped
  const p  = total > 0 ? passed  : FALLBACK.passed
  const f  = total > 0 ? failed  : FALLBACK.failed
  const sk = total > 0 ? skipped : FALLBACK.skipped
  const t  = p + f + sk

  const data = [
    { name: 'Exitosas',  value: p,  color: '#10b981', glow: 'rgba(16,185,129,0.4)'  },
    { name: 'Fallidas',  value: f,  color: '#f43f5e', glow: 'rgba(244,63,94,0.4)'   },
    { name: 'Omitidas',  value: sk, color: '#f59e0b', glow: 'rgba(245,158,11,0.4)'  },
  ]

  const pct = (v: number) => ((v / t) * 100).toFixed(1)

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut' }}
      className="flex flex-col h-full overflow-hidden rounded-2xl"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
        border: '1px solid rgba(255,255,255,0.08)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.4)',
      }}
    >
      {/* Header */}
      <div className="px-5 py-4 flex-shrink-0" style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
        <div className="text-sm font-bold text-slate-100">Distribución de Resultados</div>
        <div className="text-xs text-slate-500 mt-0.5">Resumen del período</div>
      </div>

      {/* Chart */}
      <div className="flex-1 flex flex-col items-center justify-center px-4 py-2 min-h-0">
        <div className="relative w-full" style={{ height: 140 }}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <defs>
                {data.map((d, i) => (
                  <filter key={i} id={`glow-donut-${i}`}>
                    <feGaussianBlur stdDeviation="3" result="blur" />
                    <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
                  </filter>
                ))}
              </defs>
              <Pie
                data={data}
                cx="50%"
                cy="50%"
                innerRadius={42}
                outerRadius={60}
                paddingAngle={3}
                dataKey="value"
                startAngle={90}
                endAngle={-270}
                strokeWidth={0}
              >
                {data.map((entry, i) => (
                  <Cell key={i} fill={entry.color} />
                ))}
              </Pie>
              <Tooltip
                content={({ active, payload }) => {
                  if (!active || !payload?.length) return null
                  const d = payload[0].payload
                  return (
                    <div
                      className="px-3 py-2 rounded-xl text-xs font-semibold"
                      style={{
                        background: 'rgba(7,12,28,0.95)',
                        border: `1px solid ${d.color}44`,
                        boxShadow: `0 4px 20px rgba(0,0,0,0.5)`,
                        color: d.color,
                      }}
                    >
                      {d.name}: {d.value.toLocaleString()} ({pct(d.value)}%)
                    </div>
                  )
                }}
              />
            </PieChart>
          </ResponsiveContainer>

          {/* Center label */}
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
            <div className="text-2xl font-black text-slate-100">{t.toLocaleString()}</div>
            <div className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Total</div>
          </div>
        </div>

        {/* Legend */}
        <div className="w-full space-y-2 mt-2">
          {data.map((d, i) => (
            <div key={i} className="flex items-center gap-2">
              <div className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: d.color, boxShadow: `0 0 6px ${d.color}` }} />
              <div className="flex-1 flex items-center justify-between">
                <span className="text-[11px] text-slate-400 font-medium">{d.name}</span>
                <div className="flex items-center gap-2">
                  <span className="text-[11px] font-bold" style={{ color: d.color }}>{pct(d.value)}%</span>
                  <span className="text-[10px] text-slate-600">({d.value.toLocaleString()})</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </motion.div>
  )
}
