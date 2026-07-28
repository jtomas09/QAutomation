import { useEffect, useState, useCallback } from 'react'
import { motion } from 'framer-motion'
import { Plus, Calendar, Layers3, CheckCircle2, Package } from 'lucide-react'
import type { RunState } from '../types'
import type { ConfiguredDevice } from '../hooks/useExecutionDevices'
import { getExecutions } from '../api'
import { suiteService } from '../services/SuiteService'
import StatsCards          from '../components/dashboard/StatsCards'
import RunTestsPanel       from '../components/dashboard/RunTestsPanel'
import RecentExecutions    from '../components/dashboard/RecentExecutions'
import ActivityLog         from '../components/dashboard/ActivityLog'
import ResultsDonut        from '../components/dashboard/ResultsDonut'
import DailyChart          from '../components/dashboard/DailyChart'
import ConnectedDevices    from '../components/dashboard/ConnectedDevices'
import DeviceMirrorPanel   from '../components/dashboard/DeviceMirrorPanel'

interface Props {
  state:              RunState
  suite:              string
  env:                string
  configured:         ConfiguredDevice[]
  activeDevice:       ConfiguredDevice | null
  country:            string
  videoEnabled:       boolean
  saving:             boolean
  isDirty:            boolean
  onSuiteChange:      (v: string) => void
  onEnvChange:        (v: string) => void
  onCountryChange:    (v: string) => void
  onVideoToggle:      (v: boolean) => void
  onToggleDevice:     (device: import('../types').PhysicalDevice) => void
  onSaveConfig:       () => void
  onSyncLive:         (liveDevices: import('../types').PhysicalDevice[]) => string[]
  onRun:              () => void
  onStop:             () => void
  onClearLog:         () => void
  onViewAll:          () => void
  onManageDevices:    () => void
  onAttach:           (executionId: string, suiteName: string) => void
  onNavigate?:        (page: string) => void
}

interface AggStats { passed: number; failed: number; skipped: number; total: number; avgMs: number }

const DAYS_OPTIONS = [
  { label: 'Últimos 7 días',  value: 7  },
  { label: 'Últimos 14 días', value: 14 },
  { label: 'Últimos 30 días', value: 30 },
]

export default function Dashboard({
  state, suite, env, configured, activeDevice, country, videoEnabled,
  saving, isDirty,
  onSuiteChange, onEnvChange, onCountryChange,
  onVideoToggle, onToggleDevice, onSaveConfig, onSyncLive,
  onRun, onStop, onClearLog, onViewAll, onManageDevices, onAttach,
  onNavigate,
}: Props) {
  const [daysBack,   setDaysBack]   = useState<number>(7)
  const [clearedAt,  setClearedAt]  = useState<number>(0)
  const [aggStats,   setAggStats]   = useState<AggStats>({ passed: 0, failed: 0, skipped: 0, total: 0, avgMs: 0 })
  const [suiteMetrics, setSuiteMetrics] = useState({ suites: 0, cases: 0, steps: 0 })

  // True while a run is actively in progress — historical stats freeze during this window.
  const isLive = state.status === 'running'

  // Only terminal executions contribute to global stats. RUNNING / FINALIZING / ABORTING / QUEUED
  // are never included so an in-progress run never pollutes historical metrics.
  const TERMINAL_STATUSES = ['PASSED', 'COMPLETED', 'FAILED', 'ABORTED', 'INCOMPLETE']

  useEffect(() => {
    const aggregate = async () => {
      // Freeze while a run is active — stats update only after Backend confirms COMPLETED / FAILED.
      if (isLive) return
      try {
        const execs    = await getExecutions()
        const windowMs = daysBack * 24 * 60 * 60 * 1000
        const cutoff   = Math.max(clearedAt, Date.now() - windowMs)
        const filtered = execs.filter(e =>
          TERMINAL_STATUSES.includes(e.status) &&
          new Date(e.startTime).getTime() >= cutoff
        )
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
    // isLive in deps: when execution finishes (true → false), effect restarts and aggregate()
    // fires immediately — Dashboard refreshes the moment the Backend confirms the run is done.
    aggregate()
    const id = setInterval(aggregate, 10_000)
    return () => clearInterval(id)
  }, [clearedAt, daysBack, isLive]) // eslint-disable-line react-hooks/exhaustive-deps

  // Auto-attach to scheduled executions that started without user interaction
  useEffect(() => {
    if (state.status === 'running') return
    const detect = async () => {
      try {
        const execs = await getExecutions()
        const running = execs.find(e => e.status === 'RUNNING')
        if (running && state.executionId === null) {
          onAttach(running.executionId, running.suite)
        }
      } catch { /* backend offline */ }
    }
    detect()
    const id = setInterval(detect, 5_000)
    return () => clearInterval(id)
  }, [state.status, state.executionId, onAttach])

  useEffect(() => {
    const refresh = () => setSuiteMetrics(suiteService.getMetrics())
    refresh()
    const events = ['qa:suite:created', 'qa:suite:updated', 'qa:suite:deleted', 'qa:case:created', 'qa:case:deleted']
    events.forEach(e => window.addEventListener(e, refresh))
    return () => events.forEach(e => window.removeEventListener(e, refresh))
  }, [])

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

      {/* Suite metrics row */}
      <div className="flex gap-3">
        {([
          { label: 'Suites',   value: suiteMetrics.suites, Icon: Layers3,       color: '#818cf8' },
          { label: 'Casos',    value: suiteMetrics.cases,  Icon: CheckCircle2,  color: '#34d399' },
          { label: 'Pasos',    value: suiteMetrics.steps,  Icon: Package,       color: '#60a5fa' },
        ] as const).map(({ label, value, Icon, color }) => (
          <button
            key={label}
            onClick={() => onNavigate?.('suites')}
            className="flex items-center gap-3 px-4 py-3 rounded-xl flex-1 text-left"
            style={{
              background: 'rgba(255,255,255,0.03)',
              border: '1px solid rgba(255,255,255,0.06)',
              cursor: onNavigate ? 'pointer' : 'default',
            }}
          >
            <div style={{
              width: 32, height: 32, borderRadius: 8, flexShrink: 0,
              background: `${color}18`, border: `1px solid ${color}33`,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Icon size={14} color={color} />
            </div>
            <div>
              <div style={{ fontSize: 16, fontWeight: 800, color: '#f1f5f9', lineHeight: 1 }}>{value.toLocaleString()}</div>
              <div style={{ fontSize: 10, color: '#475569', marginTop: 2 }}>{label}</div>
            </div>
          </button>
        ))}
        <div style={{ flex: 2 }} />
      </div>

      {/* Stats cards row */}
      <StatsCards
        passed={aggStats.passed}
        failed={aggStats.failed}
        skipped={aggStats.skipped}
        total={aggStats.total}
        avgMs={aggStats.avgMs}
        isLive={isLive}
        onClear={handleClear}
      />

      {/* Row 2+3: (Run panel + Recent / Activity + Donut + Chart) alongside Mirror de Dispositivo + Quick Access */}
      {/* Altura explícita en el grid (en vez de depender de align-items:stretch implícito
          a través de una cadena de flex anidados, que no se resolvía igual en todos los
          navegadores y dejaba el panel Mirror con un desfase respecto a la columna
          izquierda): 420 (fila 1) + 16 (gap-4) + 380 (fila 2) = 816.
          minHeight: 0 en ambas columnas es necesario porque el min-height por defecto
          de un item de grid es "auto" (= al menos su contenido), lo que ignoraba la
          altura fija de 816 cuando el contenido (Mirror + Quick Access) pedía más
          espacio y desbordaba sobre la sección de abajo en vez de ajustarse a ella. */}
      <div className="grid gap-4" style={{ gridTemplateColumns: '1fr 380px', height: 816 }}>
        <div className="flex flex-col gap-4" style={{ height: '100%', minHeight: 0 }}>
          {/* Run panel (fixed width) + Recent executions (flex) */}
          <div className="grid gap-4" style={{ gridTemplateColumns: '400px 1fr', height: 420 }}>
            <RunTestsPanel
              suite={suite}                 env={env}
              configuredDevices={configured}
              country={country}
              status={state.status}         executionId={state.executionId ?? null}
              videoEnabled={videoEnabled}   onVideoToggle={onVideoToggle}
              onSuiteChange={onSuiteChange} onEnvChange={onEnvChange}
              onCountryChange={onCountryChange}
              onRun={onRun}                 onStop={onStop}
              onConfigureDevices={() => {
                // Scroll to the ConnectedDevices widget
                const el = document.getElementById('connected-devices')
                el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
              }}
              passed={state.passed}
              failed={state.failed}
              skipped={state.skipped}
              totalExpected={state.totalExpected}
            />
            <RecentExecutions onViewAll={onViewAll} />
          </div>

          {/* Activity log + Donut + Daily chart */}
          <div className="grid gap-4" style={{ gridTemplateColumns: '1fr 230px 1fr', height: 380 }}>
            <ActivityLog logs={state.logs} events={state.events} onClear={onClearLog} onViewAll={onViewAll} />
            <ResultsDonut
              passed={aggStats.passed}
              failed={aggStats.failed}
              skipped={aggStats.skipped}
              isLive={isLive}
            />
            <DailyChart isLive={isLive} />
          </div>
        </div>

        {/* Mirror de Dispositivo */}
        <div style={{ height: '100%', minHeight: 0 }}>
          <DeviceMirrorPanel device={activeDevice ?? configured[0] ?? null} />
        </div>
      </div>

      {/* Row 4: Connected devices (full width) */}
      <div id="connected-devices">
        <ConnectedDevices
          configured={configured}
          onToggleDevice={onToggleDevice}
          onSave={onSaveConfig}
          saving={saving}
          isDirty={isDirty}
          onSyncLive={onSyncLive}
          onManage={onManageDevices}
        />
      </div>

    </div>
  )
}
