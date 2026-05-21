import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { Plus, Calendar } from 'lucide-react'
import type { RunState } from '../types'
import { getExecutions } from '../api'
import StatsCards       from '../components/dashboard/StatsCards'
import RunTestsPanel    from '../components/dashboard/RunTestsPanel'
import RecentExecutions from '../components/dashboard/RecentExecutions'
import ActivityLog      from '../components/dashboard/ActivityLog'
import ResultsDonut     from '../components/dashboard/ResultsDonut'
import DailyChart       from '../components/dashboard/DailyChart'
import ConnectedDevices from '../components/dashboard/ConnectedDevices'

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
  onManageDevices: () => void
}

interface AggStats { passed: number; failed: number; skipped: number; total: number; avgMs: number }

const DAYS_OPTIONS = [
  { label: 'Últimos 7 días',  value: 7  },
  { label: 'Últimos 14 días', value: 14 },
  { label: 'Últimos 30 días', value: 30 },
]

export default function Dashboard({
  state, suite, env, device, country,
  onSuiteChange, onEnvChange, onDeviceChange, onCountryChange,
  onRun, onStop, onClearLog, onViewAll, onManageDevices,
}: Props) {
  const [daysBack,   setDaysBack]   = useState<number>(7)
  const [clearedAt,  setClearedAt]  = useState<number>(0)
  const [aggStats,   setAggStats]   = useState<AggStats>({ passed: 0, failed: 0, skipped: 0, total: 0, avgMs: 0 })

  useEffect(() => {
    const aggregate = async () => {
      try {
        const execs    = await getExecutions()
        const windowMs = daysBack * 24 * 60 * 60 * 1000
        const cutoff   = Math.max(clearedAt, Date.now() - windowMs)
        const filtered = execs.filter(e => new Date(e.startTime).getTime() >= cutoff)
        const passed   = filtered.reduce((s, e) => s + e.passed,  0)
        const failed   = filtered.reduce((s, e) => s + e.failed,  0)
        const skipped  = filtered.reduce((s, e) => s + e.skipped, 0)
        const total    = filtered.reduce((s, e) => s + e.total,   0)
        const durs     = filtered
          .filter(e => e.endTime != null)
          .map(e => new Date(e.endTime!).getTime() - new Date(e.startTime).getTime())
        const avgMs    = durs.length > 0 ? Math.round(durs.reduce((a, b) => a + b, 0) / durs.length) : 0
        setAggStats({ passed, failed, skipped, total, avgMs })
      } catch { /* backend offline — keep last known values */ }
    }
    aggregate()
    const id = setInterval(aggregate, 10_000)
    return () => clearInterval(id)
  }, [clearedAt, daysBack])

  const handleClear = () => {
    setClearedAt(Date.now())
    setAggStats({ passed: 0, failed: 0, skipped: 0, total: 0, avgMs: 0 })
  }

  const handleDaysChange = (days: number) => {
    setDaysBack(days)
    setClearedAt(0)
  }

  return (
    <div className="flex flex-col gap-5 p-6 pb-8">

      {/* Welcome bar */}
      <motion.div
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35 }}
        className="flex items-start justify-between gap-4"
      >
        <div>
          <h1 className="text-2xl font-extrabold text-slate-100 leading-tight">
            ¡Bienvenido de vuelta, Jairo! 👋
          </h1>
          <p className="text-sm text-slate-500 mt-1">
            Aquí tienes un resumen de la actividad de pruebas automatizadas
          </p>
        </div>

        <div className="flex items-center gap-2.5 flex-shrink-0">
          {/* Date filter */}
          <div className="relative flex items-center">
            <Calendar size={13} className="absolute left-3 text-slate-500 pointer-events-none" />
            <select
              value={daysBack}
              onChange={e => handleDaysChange(Number(e.target.value))}
              className="appearance-none pl-8 pr-8 py-2 rounded-xl text-xs font-semibold text-slate-300 outline-none"
              style={{
                background: 'rgba(255,255,255,0.05)',
                border: '1px solid rgba(255,255,255,0.1)',
                backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%2364748b'/%3E%3C/svg%3E\")",
                backgroundRepeat: 'no-repeat',
                backgroundPosition: 'right 8px center',
              }}
            >
              {DAYS_OPTIONS.map(o => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </div>

          {/* New execution button */}
          <motion.button
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.97 }}
            onClick={onRun}
            className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold text-white"
            style={{
              background: 'linear-gradient(135deg, #4f46e5, #6366f1)',
              boxShadow: '0 4px 14px rgba(99,102,241,0.4)',
            }}
          >
            <Plus size={14} />
            Nueva Ejecución
          </motion.button>
        </div>
      </motion.div>

      {/* Stats cards row */}
      <StatsCards
        passed={aggStats.passed}
        failed={aggStats.failed}
        skipped={aggStats.skipped}
        total={aggStats.total}
        avgMs={aggStats.avgMs}
        onClear={handleClear}
      />

      {/* Row 2: Run panel (fixed width) + Recent executions (flex) */}
      <div className="grid gap-4" style={{ gridTemplateColumns: '400px 1fr', height: 420 }}>
        <RunTestsPanel
          suite={suite}           env={env}
          device={device}         country={country}
          status={state.status}   executionId={state.executionId ?? null}
          onSuiteChange={onSuiteChange}     onEnvChange={onEnvChange}
          onDeviceChange={onDeviceChange}   onCountryChange={onCountryChange}
          onRun={onRun}           onStop={onStop}
        />
        <RecentExecutions onViewAll={onViewAll} />
      </div>

      {/* Row 3: Activity log + Donut + Daily chart */}
      <div className="grid gap-4" style={{ gridTemplateColumns: '1fr 230px 1fr', height: 380 }}>
        <ActivityLog logs={state.logs} onClear={onClearLog} onViewAll={onViewAll} />
        <ResultsDonut
          passed={aggStats.passed}
          failed={aggStats.failed}
          skipped={aggStats.skipped}
        />
        <DailyChart />
      </div>

      {/* Row 4: Devices + Quick Access */}
      <div className="grid gap-4" style={{ gridTemplateColumns: '1fr 380px' }}>
        <ConnectedDevices onManage={onManageDevices} />
        <QuickAccess />
      </div>

    </div>
  )
}

// ── Quick Access ─────────────────────────────────────────────────────────────

const QUICK = [
  { icon: '📊', label: 'Ver Reportes',  sub: 'Allure Reports',  color: '#10b981' },
  { icon: '📄', label: 'Documentación', sub: 'Guías y API',     color: '#3b82f6' },
  { icon: '🎬', label: 'Videos',        sub: 'Tutoriales',      color: '#6366f1' },
  { icon: '💬', label: 'Soporte',       sub: '¿Necesitas ayuda?', color: '#f97316' },
]

function QuickAccess() {
  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut' }}
      className="overflow-hidden rounded-2xl"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
        border: '1px solid rgba(255,255,255,0.08)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.4)',
      }}
    >
      <div className="px-5 py-4" style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
        <div className="text-sm font-bold text-slate-100">Accesos Rápidos</div>
        <div className="text-xs text-slate-500 mt-0.5">Herramientas y recursos</div>
      </div>

      <div className="grid grid-cols-2 gap-3 p-4">
        {QUICK.map((q, i) => (
          <motion.button
            key={q.label}
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ delay: i * 0.07, duration: 0.3 }}
            whileHover={{ scale: 1.03, transition: { duration: 0.15 } }}
            whileTap={{ scale: 0.97 }}
            className="flex flex-col items-center gap-2.5 py-5 px-3 rounded-xl transition-colors"
            style={{
              background: 'rgba(255,255,255,0.03)',
              border: '1px solid rgba(255,255,255,0.07)',
              minHeight: 110,
            }}
            onMouseEnter={e => ((e.currentTarget as HTMLButtonElement).style.borderColor = `${q.color}44`)}
            onMouseLeave={e => ((e.currentTarget as HTMLButtonElement).style.borderColor = 'rgba(255,255,255,0.07)')}
          >
            <div
              className="w-10 h-10 rounded-xl flex items-center justify-center text-xl"
              style={{ background: `${q.color}20`, boxShadow: `0 0 14px ${q.color}25` }}
            >
              {q.icon}
            </div>
            <div className="text-xs font-bold text-slate-200 text-center leading-tight">{q.label}</div>
            <div className="text-[10px] text-slate-600 text-center">{q.sub}</div>
          </motion.button>
        ))}
      </div>
    </motion.div>
  )
}
