import { useEffect, useState, useCallback } from 'react'
import { motion } from 'framer-motion'
import { Plus, Calendar, Clapperboard, Circle, ChevronRight, Clock, Code2, Layers3 } from 'lucide-react'
import type { RunState } from '../types'
import type { ConfiguredDevice } from '../hooks/useExecutionDevices'
import { getExecutions } from '../api'
import StatsCards       from '../components/dashboard/StatsCards'
import RunTestsPanel    from '../components/dashboard/RunTestsPanel'
import RecentExecutions from '../components/dashboard/RecentExecutions'
import ActivityLog      from '../components/dashboard/ActivityLog'
import ResultsDonut     from '../components/dashboard/ResultsDonut'
import DailyChart       from '../components/dashboard/DailyChart'
import ConnectedDevices from '../components/dashboard/ConnectedDevices'

interface Props {
  state:              RunState
  suite:              string
  env:                string
  configured:         ConfiguredDevice[]
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
  state, suite, env, configured, country, videoEnabled,
  saving, isDirty,
  onSuiteChange, onEnvChange, onCountryChange,
  onVideoToggle, onToggleDevice, onSaveConfig, onSyncLive,
  onRun, onStop, onClearLog, onViewAll, onManageDevices, onAttach,
  onNavigate,
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
      <div id="connected-devices" className="grid gap-4" style={{ gridTemplateColumns: '1fr 380px' }}>
        <ConnectedDevices
          configured={configured}
          onToggleDevice={onToggleDevice}
          onSave={onSaveConfig}
          saving={saving}
          isDirty={isDirty}
          onSyncLive={onSyncLive}
          onManage={onManageDevices}
        />
        <QuickAccess />
      </div>

      {/* Row 5: Record Studio Feature Highlight */}
      <RecordStudioWidget onOpen={() => onNavigate?.('record-studio')} />

    </div>
  )
}

// ── Record Studio Widget ──────────────────────────────────────────────────────

interface RecordStudioSession { id: string; name: string; date: string; steps: number; lang: string; mode?: string }

function RecordStudioWidget({ onOpen }: { onOpen: () => void }) {
  const [sessions,     setSessions]     = useState<RecordStudioSession[]>([])
  const [customSuites, setCustomSuites] = useState(0)

  useEffect(() => {
    try {
      const saved = localStorage.getItem('qa_record_sessions')
      if (saved) {
        // Normalize field names: savedAt → date, stepCount → steps
        const raw = JSON.parse(saved) as Array<Record<string, unknown>>
        const normalized: RecordStudioSession[] = raw.slice(-3).reverse().map(s => ({
          id:   String(s.id   ?? ''),
          name: String(s.name ?? 'Sin nombre'),
          date: String(s.savedAt ?? s.date ?? ''),
          steps: Number(s.stepCount ?? s.steps ?? 0),
          lang:  String(s.lang ?? ''),
          mode:  String(s.mode ?? ''),
        }))
        setSessions(normalized)
      }
    } catch { /* ignore */ }
    try {
      const cs = localStorage.getItem('qa_custom_suites')
      if (cs) setCustomSuites(JSON.parse(cs).length)
    } catch { /* ignore */ }
  }, [])

  return (
    <motion.div
      initial={{ opacity: 0, y: 12 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, ease: 'easeOut' }}
      className="overflow-hidden rounded-2xl"
      style={{
        background: 'linear-gradient(135deg, rgba(225,29,72,0.06) 0%, rgba(99,102,241,0.06) 100%)',
        border: '1px solid rgba(225,29,72,0.2)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.3)',
      }}
    >
      {/* Header */}
      <div className="flex items-center justify-between px-6 py-4"
        style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl flex items-center justify-center"
            style={{ background: 'rgba(225,29,72,0.15)', border: '1px solid rgba(225,29,72,0.3)' }}>
            <Clapperboard size={16} style={{ color: '#fb7185' }} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-bold text-slate-100">Record Studio</span>
              <span className="text-[9px] font-black px-1.5 py-0.5 rounded-full"
                style={{ background: 'rgba(225,29,72,0.2)', color: '#fb7185', border: '1px solid rgba(225,29,72,0.3)' }}>
                NUEVO
              </span>
            </div>
            <div className="text-xs text-slate-500 mt-0.5 flex items-center gap-2">
              Graba interacciones y genera pruebas Appium automáticamente
              {customSuites > 0 && (
                <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full"
                  style={{ background: 'rgba(99,102,241,0.2)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.3)' }}>
                  {customSuites} suite{customSuites !== 1 ? 's' : ''}
                </span>
              )}
            </div>
          </div>
        </div>
        <button
          onClick={onOpen}
          className="flex items-center gap-1.5 px-4 py-2 rounded-xl text-[12px] font-bold transition-all"
          style={{
            background: 'rgba(225,29,72,0.15)',
            border: '1px solid rgba(225,29,72,0.3)',
            color: '#fb7185',
          }}
          onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'rgba(225,29,72,0.25)' }}
          onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'rgba(225,29,72,0.15)' }}
        >
          <Clapperboard size={13} />
          Abrir Record Studio
          <ChevronRight size={12} />
        </button>
      </div>

      {/* Body */}
      <div className="grid gap-0" style={{ gridTemplateColumns: '1fr 1fr 1fr 1fr' }}>
        {/* Feature cards */}
        {[
          { icon: <Circle size={14} style={{ color: '#fb7185' }} />, label: 'Grabación en vivo', desc: 'Captura elementos reales' },
          { icon: <Code2 size={14} style={{ color: '#818cf8' }} />, label: 'Código automático', desc: 'Java + Appium + TestNG' },
          { icon: <Layers3 size={14} style={{ color: '#34d399' }} />, label: 'Page Objects', desc: 'Patrón profesional' },
          { icon: <Clock size={14} style={{ color: '#f59e0b' }} />, label: 'Smart Waits', desc: 'Esperas inteligentes' },
        ].map((f, i) => (
          <div key={i} className="flex items-center gap-3 px-5 py-4"
            style={{ borderRight: i < 3 ? '1px solid rgba(255,255,255,0.05)' : 'none' }}>
            <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
              style={{ background: 'rgba(255,255,255,0.05)' }}>
              {f.icon}
            </div>
            <div>
              <div className="text-[11px] font-bold text-slate-200">{f.label}</div>
              <div className="text-[10px] text-slate-600">{f.desc}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Recent sessions */}
      {sessions.length > 0 && (
        <div style={{ borderTop: '1px solid rgba(255,255,255,0.06)' }}>
          <div className="px-6 py-3 flex items-center gap-2">
            <span className="text-[10px] font-black tracking-widest text-slate-500">SESIONES RECIENTES</span>
          </div>
          <div className="flex gap-3 px-6 pb-4">
            {sessions.map(s => (
              <button key={s.id} onClick={onOpen}
                className="flex-1 flex flex-col gap-1 px-4 py-3 rounded-xl text-left transition-all"
                style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.07)' }}
                onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.borderColor = 'rgba(225,29,72,0.3)' }}
                onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.borderColor = 'rgba(255,255,255,0.07)' }}
              >
                <div className="text-[12px] font-bold text-slate-200 truncate">{s.name}</div>
                <div className="text-[10px] text-slate-500 flex items-center gap-1">
                  {s.steps} pasos · {s.lang}
                  {s.mode === 'suite' && (
                    <span className="text-[8px] font-bold px-1 py-0.5 rounded"
                      style={{ background: 'rgba(99,102,241,0.2)', color: '#818cf8' }}>
                      SUITE
                    </span>
                  )}
                </div>
                <div className="text-[9px] text-slate-600">{s.date}</div>
              </button>
            ))}
          </div>
        </div>
      )}
    </motion.div>
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
