import React, { useState } from 'react'
import { motion } from 'framer-motion'
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid,
  Tooltip, ResponsiveContainer, Legend,
} from 'recharts'

const RAW_DAYS = ['13 May','14 May','15 May','16 May','17 May','18 May','19 May']

const DATA = RAW_DAYS.map((day, i) => ({
  day,
  Exitosas: [80, 95, 88, 110, 145, 130, 120][i],
  Fallidas: [15, 12, 18,  20,  23,  17,  14][i],
  Omitidas: [ 8, 10,  6,  14,  12,   9,  10][i],
}))

const SERIES = [
  { key: 'Exitosas', color: '#10b981' },
  { key: 'Fallidas', color: '#f43f5e' },
  { key: 'Omitidas', color: '#f59e0b' },
] as const

export default function DailyChart() {
  const [hidden, setHidden] = useState<Set<string>>(new Set())

  function toggle(key: string) {
    setHidden(prev => {
      const n = new Set(prev)
      n.has(key) ? n.delete(key) : n.add(key)
      return n
    })
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut', delay: 0.1 }}
      className="flex flex-col h-full overflow-hidden rounded-2xl"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
        border: '1px solid rgba(255,255,255,0.08)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.4)',
      }}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-5 py-4 flex-shrink-0"
        style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
        <div>
          <div className="text-sm font-bold text-slate-100">Ejecuciones por Día</div>
          <div className="text-xs text-slate-500 mt-0.5">Últimos 7 días</div>
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
          <AreaChart data={DATA} margin={{ top: 4, right: 8, bottom: 0, left: -24 }}>
            <defs>
              {SERIES.map(s => (
                <linearGradient key={s.key} id={`grad-${s.key}`} x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%"   stopColor={s.color} stopOpacity={0.3} />
                  <stop offset="100%" stopColor={s.color} stopOpacity={0}   />
                </linearGradient>
              ))}
            </defs>
            <CartesianGrid
              strokeDasharray="3 3"
              stroke="rgba(255,255,255,0.05)"
              vertical={false}
            />
            <XAxis
              dataKey="day"
              tick={{ fill: '#475569', fontSize: 10 }}
              axisLine={false}
              tickLine={false}
            />
            <YAxis
              tick={{ fill: '#475569', fontSize: 10 }}
              axisLine={false}
              tickLine={false}
            />
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
