import React, { useMemo } from 'react'
import { motion } from 'framer-motion'
import { AreaChart, Area, ResponsiveContainer } from 'recharts'
import { TrendingUp, TrendingDown, Minus } from 'lucide-react'

interface Stat {
  label:   string
  value:   string
  trend:   string
  up:      boolean | null
  color:   string
  glow:    string
  data:    { v: number }[]
  icon:    string
}

interface Props { passed: number; failed: number; skipped: number; total: number }

function mkData(arr: number[]) { return arr.map(v => ({ v })) }

const BASE = [40,45,38,52,48,55,50,60,58,65,62,70,68,75,72,80]
const jit  = (a: number[]) => a.map(v => ({ v: v + Math.random()*8-4 }))

export default function StatsCards({ passed, failed, skipped, total }: Props) {
  const stats = useMemo<Stat[]>(() => [
    {
      label: 'Ejecuciones Totales', value: total > 0 ? total.toLocaleString() : '1,247',
      trend: '+12.5% vs anterior', up: true,
      color: '#818cf8', glow: 'rgba(129,140,248,0.3)', icon: '◎',
      data: jit(BASE),
    },
    {
      label: 'Pruebas Exitosas', value: total > 0 ? passed.toLocaleString() : '1,024',
      trend: '82.1% éxito', up: true,
      color: '#10b981', glow: 'rgba(16,185,129,0.3)', icon: '✓',
      data: jit([30,40,35,50,45,55,52,60,58,65,63,70,68,74,72,80]),
    },
    {
      label: 'Pruebas Fallidas', value: total > 0 ? failed.toLocaleString() : '156',
      trend: '12.5% fallo', up: false,
      color: '#f43f5e', glow: 'rgba(244,63,94,0.3)', icon: '✗',
      data: jit([50,45,55,40,48,38,42,35,38,30,33,28,30,25,22,18]),
    },
    {
      label: 'Pruebas Omitidas', value: total > 0 ? skipped.toLocaleString() : '67',
      trend: '5.4% omitido', up: null,
      color: '#f59e0b', glow: 'rgba(245,158,11,0.3)', icon: '—',
      data: jit([20,22,18,24,20,26,22,28,24,22,26,20,24,18,20,18]),
    },
    {
      label: 'Tiempo Promedio', value: '2m 45s',
      trend: '-8.3% más rápido', up: true,
      color: '#38bdf8', glow: 'rgba(56,189,248,0.3)', icon: '⏱',
      data: jit([60,58,62,55,58,52,55,50,53,48,50,45,48,43,42,38]),
    },
  ], [passed, failed, skipped, total])

  return (
    <div className="grid grid-cols-5 gap-3.5">
      {stats.map((s, i) => <StatCard key={i} stat={s} index={i} />)}
    </div>
  )
}

function StatCard({ stat, index }: { stat: Stat; index: number }) {
  const TrendIcon = stat.up === null ? Minus : stat.up ? TrendingUp : TrendingDown
  const trendColor = stat.up === null ? '#64748b' : stat.up ? '#10b981' : '#f43f5e'

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.07, duration: 0.4, ease: 'easeOut' }}
      whileHover={{ y: -3, transition: { duration: 0.2 } }}
      className="relative overflow-hidden rounded-2xl flex flex-col"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
        border: '1px solid rgba(255,255,255,0.08)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.4)',
        paddingBottom: 0,
      }}
    >
      {/* Top color stripe */}
      <div className="absolute top-0 left-0 right-0 h-px" style={{ background: `linear-gradient(90deg, transparent, ${stat.color}, transparent)` }} />

      {/* Ambient glow */}
      <div className="absolute top-0 right-0 w-24 h-24 pointer-events-none"
        style={{ background: `radial-gradient(circle at top right, ${stat.glow} 0%, transparent 70%)` }} />

      <div className="p-4 pb-2 flex-1">
        {/* Header row */}
        <div className="flex items-start justify-between mb-3">
          <span className="text-xs text-slate-400 font-medium leading-tight max-w-[100px]">{stat.label}</span>
          <div
            className="w-8 h-8 rounded-xl flex items-center justify-center text-sm font-bold flex-shrink-0"
            style={{ background: `${stat.color}22`, color: stat.color, boxShadow: `0 0 12px ${stat.glow}` }}
          >
            {stat.icon}
          </div>
        </div>

        {/* Value */}
        <div className="text-3xl font-black tracking-tight mb-1" style={{ color: stat.color }}>
          {stat.value}
        </div>

        {/* Trend */}
        <div className="flex items-center gap-1">
          <TrendIcon size={11} style={{ color: trendColor }} />
          <span className="text-[11px] font-semibold" style={{ color: trendColor }}>{stat.trend}</span>
        </div>
      </div>

      {/* Sparkline */}
      <div className="h-11 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={stat.data} margin={{ top: 0, right: 0, bottom: 0, left: 0 }}>
            <defs>
              <linearGradient id={`spark-${index}`} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={stat.color} stopOpacity={0.4} />
                <stop offset="100%" stopColor={stat.color} stopOpacity={0} />
              </linearGradient>
            </defs>
            <Area
              type="monotone"
              dataKey="v"
              stroke={stat.color}
              strokeWidth={1.5}
              fill={`url(#spark-${index})`}
              dot={false}
              isAnimationActive={true}
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </motion.div>
  )
}
