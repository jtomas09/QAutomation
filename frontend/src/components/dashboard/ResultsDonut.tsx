import React from 'react'
import s from './ResultsDonut.module.css'

interface Props {
  passed:  number
  failed:  number
  skipped: number
}

export default function ResultsDonut({ passed, failed, skipped }: Props) {
  const total = passed + failed + skipped
  const p = total > 0 ? passed  : 1024
  const f = total > 0 ? failed  : 156
  const sk = total > 0 ? skipped : 67
  const t = p + f + sk

  const pct = (v: number) => Math.round((v / t) * 1000) / 10

  const r = 70
  const circ = 2 * Math.PI * r
  const passedArc  = (p  / t) * circ
  const failedArc  = (f  / t) * circ
  const skippedArc = (sk / t) * circ

  const passedOff  = 0
  const failedOff  = -(passedArc)
  const skippedOff = -(passedArc + failedArc)

  return (
    <div className={s.card}>
      <div className={s.header}>
        <div className={s.title}>Distribución de Resultados</div>
        <div className={s.subtitle}>Estadísticas de resultados</div>
      </div>

      <div className={s.body}>
        <div className={s.chartWrap}>
          <svg viewBox="0 0 180 180" className={s.svg}>
            <circle cx="90" cy="90" r={r} fill="none" stroke="#1e2d55" strokeWidth="18" />

            {/* Passed (green) */}
            <circle cx="90" cy="90" r={r} fill="none"
              stroke="#22c55e" strokeWidth="18"
              strokeDasharray={`${passedArc} ${circ - passedArc}`}
              strokeDashoffset={circ * 0.25}
              strokeLinecap="round"
            />
            {/* Failed (red) */}
            <circle cx="90" cy="90" r={r} fill="none"
              stroke="#ef4444" strokeWidth="18"
              strokeDasharray={`${failedArc} ${circ - failedArc}`}
              strokeDashoffset={circ * 0.25 + failedOff}
              strokeLinecap="round"
            />
            {/* Skipped (yellow) */}
            <circle cx="90" cy="90" r={r} fill="none"
              stroke="#eab308" strokeWidth="18"
              strokeDasharray={`${skippedArc > 4 ? skippedArc : 0} ${circ}`}
              strokeDashoffset={circ * 0.25 + skippedOff}
              strokeLinecap="round"
            />

            <text x="90" y="86" textAnchor="middle" className={s.centerNum}>{t.toLocaleString()}</text>
            <text x="90" y="102" textAnchor="middle" className={s.centerLbl}>Total</text>
          </svg>
        </div>

        <div className={s.legend}>
          <LegendItem color="#22c55e" label="Passed"  pct={pct(p)}  count={p} />
          <LegendItem color="#ef4444" label="Failed"  pct={pct(f)}  count={f} />
          <LegendItem color="#eab308" label="Skipped" pct={pct(sk)} count={sk} />
        </div>
      </div>
    </div>
  )
}

function LegendItem({ color, label, pct, count }: {
  color: string; label: string; pct: number; count: number
}) {
  return (
    <div className={s.legendItem}>
      <div className={s.legendDot} style={{ background: color }} />
      <div className={s.legendBody}>
        <div className={s.legendLabel} style={{ color }}>{label}</div>
        <div className={s.legendVal}>{pct}%</div>
        <div className={s.legendCount}>({count.toLocaleString()})</div>
      </div>
    </div>
  )
}
