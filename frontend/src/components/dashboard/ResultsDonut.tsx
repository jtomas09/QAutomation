import { motion } from 'framer-motion'
import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer } from 'recharts'

interface Props { passed: number; failed: number; skipped: number }

const SERIES = [
  { name: 'Exitosas', color: '#10b981' },
  { name: 'Fallidas', color: '#f43f5e' },
  { name: 'Omitidas', color: '#f59e0b' },
]

const EMPTY_DATA = [{ name: 'Sin datos', value: 1, color: 'rgba(255,255,255,0.07)' }]

export default function ResultsDonut({ passed, failed, skipped }: Props) {
  const total = passed + failed + skipped
  const isEmpty = total === 0

  const chartData = isEmpty
    ? EMPTY_DATA
    : [
        { name: 'Exitosas', value: passed,  color: '#10b981' },
        { name: 'Fallidas', value: failed,  color: '#f43f5e' },
        { name: 'Omitidas', value: skipped, color: '#f59e0b' },
      ]

  const pct = (v: number) => total > 0 ? ((v / total) * 100).toFixed(1) : '0.0'

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut' }}
      className="flex flex-col h-full overflow-hidden rounded-2xl"
      style={{
        background: 'var(--panel-bg)',
        border: '1px solid var(--panel-border)',
        boxShadow: 'var(--panel-shadow)',
      }}
    >
      {/* Header */}
      <div className="px-5 py-4 flex-shrink-0" style={{ borderBottom: '1px solid var(--panel-divide)' }}>
        <div className="text-sm font-bold text-slate-100">Distribución de Resultados</div>
        <div className="text-xs text-slate-500 mt-0.5">Resumen del período</div>
      </div>

      {/* Chart */}
      <div className="flex-1 flex flex-col items-center justify-center px-4 py-2 min-h-0">
        <div className="relative w-full" style={{ height: 140 }}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie
                data={chartData}
                cx="50%"
                cy="50%"
                innerRadius={42}
                outerRadius={60}
                paddingAngle={isEmpty ? 0 : 3}
                dataKey="value"
                startAngle={90}
                endAngle={-270}
                strokeWidth={0}
              >
                {chartData.map((entry, i) => (
                  <Cell key={i} fill={entry.color} />
                ))}
              </Pie>
              {!isEmpty && (
                <Tooltip
                  content={({ active, payload }) => {
                    if (!active || !payload?.length) return null
                    const item = payload[0].payload as { name: string; value: number; color: string }
                    return (
                      <div
                        className="px-3 py-2 rounded-xl text-xs font-semibold"
                        style={{
                          background: 'rgba(7,12,28,0.95)',
                          border: `1px solid ${item.color}44`,
                          color: item.color,
                        }}
                      >
                        {item.name}: {item.value.toLocaleString()} ({pct(item.value)}%)
                      </div>
                    )
                  }}
                />
              )}
            </PieChart>
          </ResponsiveContainer>

          {/* Center label */}
          <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
            {isEmpty ? (
              <>
                <div className="text-lg font-black text-slate-600">0</div>
                <div className="text-[9px] font-semibold text-slate-700 uppercase tracking-wider">Sin datos</div>
              </>
            ) : (
              <>
                <div className="text-2xl font-black text-slate-100">{total.toLocaleString()}</div>
                <div className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">Total</div>
              </>
            )}
          </div>
        </div>

        {/* Legend */}
        <div className="w-full space-y-2 mt-2">
          {SERIES.map((s, i) => {
            const val = [passed, failed, skipped][i]
            return (
              <div key={i} className="flex items-center gap-2">
                <div
                  className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                  style={{
                    background: isEmpty ? 'rgba(255,255,255,0.1)' : s.color,
                    boxShadow: isEmpty ? 'none' : `0 0 6px ${s.color}`,
                  }}
                />
                <div className="flex-1 flex items-center justify-between">
                  <span className="text-[11px] font-medium" style={{ color: isEmpty ? '#334155' : '#94a3b8' }}>
                    {s.name}
                  </span>
                  <div className="flex items-center gap-2">
                    <span className="text-[11px] font-bold" style={{ color: isEmpty ? '#334155' : s.color }}>
                      {pct(val)}%
                    </span>
                    <span className="text-[10px] text-slate-600">({val.toLocaleString()})</span>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </motion.div>
  )
}
