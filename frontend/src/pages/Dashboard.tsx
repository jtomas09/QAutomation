import React from 'react'
import type { RunState } from '../types'
import StatsCards       from '../components/dashboard/StatsCards'
import RunTestsPanel    from '../components/dashboard/RunTestsPanel'
import RecentExecutions from '../components/dashboard/RecentExecutions'
import ActivityLog      from '../components/dashboard/ActivityLog'
import ResultsDonut     from '../components/dashboard/ResultsDonut'
import DailyChart       from '../components/dashboard/DailyChart'
import ConnectedDevices from '../components/dashboard/ConnectedDevices'
import s from './Dashboard.module.css'

interface Props {
  state:           RunState
  suite:           string
  env:             string
  device:          string
  country:         string
  onSuiteChange:   (v: string) => void
  onEnvChange:     (v: string) => void
  onDeviceChange:  (v: string) => void
  onCountryChange: (v: string) => void
  onRun:           () => void
  onStop:          () => void
  onClearLog:      () => void
  onViewAll:       () => void
}

export default function Dashboard({
  state, suite, env, device, country,
  onSuiteChange, onEnvChange, onDeviceChange, onCountryChange,
  onRun, onStop, onClearLog, onViewAll,
}: Props) {
  return (
    <div className={s.page}>
      {/* Welcome bar */}
      <div className={s.welcome}>
        <div>
          <h1 className={s.welcomeTitle}>¡Bienvenido de vuelta, Jairo! 👋</h1>
          <p className={s.welcomeSub}>Aquí tienes un resumen de la actividad de pruebas automatizadas</p>
        </div>
        <div className={s.welcomeActions}>
          <select className={s.dateFilter}>
            <option>7 días</option>
            <option>14 días</option>
            <option>30 días</option>
          </select>
          <button className={s.newExecBtn} onClick={onRun}>
            + Nueva Ejecución
          </button>
        </div>
      </div>

      {/* Stats cards */}
      <StatsCards
        passed={state.passed}
        failed={state.failed}
        skipped={state.skipped}
        total={state.total}
      />

      {/* Run panel + Recent executions */}
      <div className={s.row2}>
        <div className={s.runPanel}>
          <RunTestsPanel
            suite={suite}           env={env}
            device={device}         country={country}
            status={state.status}   executionId={state.executionId ?? null}
            onSuiteChange={onSuiteChange}     onEnvChange={onEnvChange}
            onDeviceChange={onDeviceChange}   onCountryChange={onCountryChange}
            onRun={onRun}           onStop={onStop}
          />
        </div>
        <div className={s.recentPanel}>
          <RecentExecutions onViewAll={onViewAll} />
        </div>
      </div>

      {/* Activity log + Donut + Daily chart */}
      <div className={s.row3}>
        <div className={s.activityPanel}>
          <ActivityLog logs={state.logs} onClear={onClearLog} onViewAll={onViewAll} />
        </div>
        <div className={s.donutPanel}>
          <ResultsDonut
            passed={state.passed}
            failed={state.failed}
            skipped={state.skipped}
          />
        </div>
        <div className={s.chartPanel}>
          <DailyChart />
        </div>
      </div>

      {/* Devices + Quick Access */}
      <div className={s.row4}>
        <div className={s.devicesPanel}>
          <ConnectedDevices />
        </div>
        <div className={s.quickPanel}>
          <QuickAccess />
        </div>
      </div>
    </div>
  )
}

// ── Quick Access ─────────────────────────────────────────────────────────────

const QUICK = [
  { icon: '📊', label: 'Ver Reportes',   sub: 'Allure Reports', color: '#22c55e' },
  { icon: '📄', label: 'Documentación', sub: 'Guías y API',     color: '#3b82f6' },
  { icon: '🎬', label: 'Videos',         sub: 'Tutoriales',     color: '#6366f1' },
  { icon: '💬', label: 'Soporte',        sub: '¿Necesitas ayuda?', color: '#f97316' },
]

function QuickAccess() {
  return (
    <div style={{
      background: 'var(--bg-card)', border: '1px solid #131d38',
      borderRadius: 'var(--radius-card)', overflow: 'hidden',
    }}>
      <div style={{ padding: '16px 18px 12px', borderBottom: '1px solid #131d38' }}>
        <div style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-pri)' }}>Accesos Rápidos</div>
        <div style={{ fontSize: 11, color: 'var(--text-dim)', marginTop: 2 }}>Herramientas y recursos</div>
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 12, padding: 16 }}>
        {QUICK.map(q => (
          <button key={q.label} style={{
            background: '#0a0f24', border: '1px solid #1e2d55', borderRadius: 10,
            padding: '14px 10px', display: 'flex', flexDirection: 'column',
            alignItems: 'center', gap: 6, cursor: 'pointer',
            transition: 'border-color .15s, background .15s',
          }}
          onMouseEnter={e => (e.currentTarget.style.borderColor = q.color)}
          onMouseLeave={e => (e.currentTarget.style.borderColor = '#1e2d55')}>
            <div style={{
              width: 36, height: 36, borderRadius: 9, fontSize: 18,
              background: `${q.color}20`, display: 'flex',
              alignItems: 'center', justifyContent: 'center',
            }}>{q.icon}</div>
            <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--text-pri)', textAlign: 'center', lineHeight: 1.3 }}>{q.label}</div>
            <div style={{ fontSize: 10, color: 'var(--text-dim)' }}>{q.sub}</div>
          </button>
        ))}
      </div>
    </div>
  )
}
