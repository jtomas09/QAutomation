import React from 'react'
import type { RunState } from '../types'
import s from './SummaryBar.module.css'

interface Props { state: RunState }

export default function SummaryBar({ state }: Props) {
  return (
    <div className={s.bar}>
      <div className={s.label}>
        <span className={s.icon}>▦</span> RESUMEN DE EJECUCIÓN
      </div>

      <Stat icon="✓" value={state.passed}  color="var(--color-ok)"   label="PASSED"  />
      <Stat icon="✗" value={state.failed}  color="var(--color-fail)" label="FAILED"  />
      <Stat icon="▶▶" value={state.skipped} color="var(--color-skip)" label="SKIPPED" />

      <div className={s.divV} />

      <div className={s.total}>
        <span className={s.totalNum}>{state.total}</span>
        <span className={s.totalLbl}>TOTAL</span>
      </div>

      <div className={s.spacer} />

      <div className={s.lastRun}>
        <span className={s.clockIcon}>⏱</span>
        Última ejecución: <strong>{state.lastRun ?? '—'}</strong>
      </div>
    </div>
  )
}

function Stat({ icon, value, color, label }: { icon: string; value: number; color: string; label: string }) {
  return (
    <div className={s.stat}>
      <span style={{ color }}>{icon}</span>
      <span className={s.statNum} style={{ color }}>{value}</span>
      <span className={s.statLbl}>{label}</span>
    </div>
  )
}
