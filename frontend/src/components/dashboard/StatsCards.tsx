import React, { useMemo } from 'react'
import s from './StatsCards.module.css'

interface Stat {
  label:  string
  value:  string
  trend:  string
  up:     boolean | null  // null = neutral
  icon:   string
  color:  string
  data:   number[]
}

interface Props {
  passed:  number
  failed:  number
  skipped: number
  total:   number
}

const SPARK_BASE = [40,45,38,52,48,55,50,60,58,65,62,70,68,75,72,80,78,82,79,85]

function jitter(base: number[]): number[] {
  return base.map(v => v + (Math.random() * 8 - 4))
}

export default function StatsCards({ passed, failed, skipped, total }: Props) {
  const stats = useMemo<Stat[]>(() => [
    {
      label: 'Ejecuciones Totales',
      value: total > 0 ? String(total) : '1,247',
      trend: '+12.5%', up: true,
      icon: '⊟', color: '#6366f1',
      data: jitter(SPARK_BASE),
    },
    {
      label: 'Pruebas Exitosas',
      value: total > 0 ? String(passed) : '1,024',
      trend: '82.1% éxito', up: true,
      icon: '✓', color: '#22c55e',
      data: jitter([30,40,35,50,45,55,52,60,58,65,63,70,68,74,72,80,78,82,80,85]),
    },
    {
      label: 'Pruebas Fallidas',
      value: total > 0 ? String(failed) : '156',
      trend: '12.5% fallo', up: false,
      icon: '✗', color: '#ef4444',
      data: jitter([50,45,55,40,48,38,42,35,38,30,33,28,30,25,28,22,25,20,22,18]),
    },
    {
      label: 'Pruebas Omitidas',
      value: total > 0 ? String(skipped) : '67',
      trend: '5.4% omitido', up: null,
      icon: '—', color: '#eab308',
      data: jitter([20,22,18,24,20,26,22,28,24,22,26,20,24,18,22,20,18,22,20,18]),
    },
    {
      label: 'Tiempo Promedio',
      value: '2m 45s',
      trend: '-8.3% más rápido', up: true,
      icon: '⏱', color: '#3b82f6',
      data: jitter([60,58,62,55,58,52,55,50,53,48,50,45,48,43,46,42,44,40,42,38]),
    },
  ], [passed, failed, skipped, total])

  return (
    <div className={s.grid}>
      {stats.map((stat, i) => <StatCard key={i} stat={stat} />)}
    </div>
  )
}

function sparkPath(data: number[], w: number, h: number): string {
  const min = Math.min(...data), max = Math.max(...data)
  const range = max - min || 1
  const step  = w / (data.length - 1)
  return data.map((v, i) => {
    const x = i * step
    const y = h - ((v - min) / range) * (h - 4) - 2
    return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
}

function StatCard({ stat }: { stat: Stat }) {
  const path = sparkPath(stat.data, 120, 40)
  return (
    <div className={s.card}>
      <div className={s.top}>
        <span className={s.label}>{stat.label}</span>
        <span className={s.iconCircle} style={{ background: `${stat.color}22`, color: stat.color }}>
          {stat.icon}
        </span>
      </div>
      <div className={s.value}>{stat.value}</div>
      <div className={s.trend} style={{
        color: stat.up === null ? 'var(--text-dim)' : stat.up ? '#22c55e' : '#ef4444'
      }}>
        {stat.up === null ? '' : stat.up ? '▲ ' : '▼ '}
        {stat.trend}
      </div>
      <svg className={s.spark} viewBox="0 0 120 40" preserveAspectRatio="none">
        <defs>
          <linearGradient id={`sg${stat.color.replace('#','')}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={stat.color} stopOpacity=".3" />
            <stop offset="100%" stopColor={stat.color} stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={`${path} L120,40 L0,40 Z`} fill={`url(#sg${stat.color.replace('#','')})`} />
        <path d={path} fill="none" stroke={stat.color} strokeWidth="1.5" strokeLinecap="round" />
      </svg>
    </div>
  )
}
