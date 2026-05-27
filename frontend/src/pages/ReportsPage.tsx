import React, { useEffect, useState, useMemo } from 'react'
import { motion } from 'framer-motion'
import {
  BarChart3, CheckCircle2, XCircle, MinusCircle, Clock, ShieldCheck,
  ExternalLink, FileText, Download, GitCompare, SlidersHorizontal,
  Settings, TrendingUp, TrendingDown, ChevronDown, X,
  CheckSquare, Square, Archive, Table2, FileCode2, AlignLeft,
} from 'lucide-react'
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip as RTooltip, ResponsiveContainer, PieChart, Pie, Cell,
} from 'recharts'
import { getExecutions } from '../api'
import type { ExecutionSummary } from '../types'

// ─── Constants ────────────────────────────────────────────────────────────────
const TABS = [
  'Resumen', 'Ejecuciones', 'Allure Reports',
  'Comparaciones', 'Historial de Reportes', 'Configuración',
] as const
type Tab = (typeof TABS)[number]

const PERIODS = [
  'Últimos 7 días', 'Últimos 30 días', 'Últimos 90 días', 'Todo el historial',
] as const
type Period = (typeof PERIODS)[number]

const STATUS_COLORS: Record<string, string> = {
  PASSED:   '#10b981',
  FAILED:   '#f43f5e',
  SKIPPED:  '#f59e0b',
  ABORTED:  '#6366f1',
  RUNNING:  '#3b82f6',
  QUEUED:   '#8b5cf6',
  PENDING:  '#64748b',
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function formatDuration(ms: number): string {
  if (ms <= 0) return '—'
  const s = Math.floor(ms / 1000)
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (h > 0) return `${h}h ${m}m ${sec}s`
  if (m > 0) return `${m}m ${String(sec).padStart(2, '0')}s`
  return `${sec}s`
}

function execDurationMs(e: ExecutionSummary): number {
  if (!e.endTime) return 0
  return new Date(e.endTime).getTime() - new Date(e.startTime).getTime()
}

function getPeriodCutoff(period: Period): Date | null {
  const now = new Date()
  if (period === 'Últimos 7 días')  { const d = new Date(now); d.setDate(d.getDate() - 7);  return d }
  if (period === 'Últimos 30 días') { const d = new Date(now); d.setDate(d.getDate() - 30); return d }
  if (period === 'Últimos 90 días') { const d = new Date(now); d.setDate(d.getDate() - 90); return d }
  return null
}

function filterExecs(
  execs: ExecutionSummary[],
  period: Period,
  suite: string,
  device: string,
  status: string,
): ExecutionSummary[] {
  let result = [...execs]
  const cutoff = getPeriodCutoff(period)
  if (cutoff) result = result.filter(e => new Date(e.startTime) >= cutoff)
  if (suite  !== 'Todas') result = result.filter(e => e.suite   === suite)
  if (device !== 'Todos') result = result.filter(e => e.device  === device)
  if (status !== 'Todos') result = result.filter(e => e.status  === status)
  return result
}

function buildTimeSeries(execs: ExecutionSummary[], days: number) {
  const today = new Date()
  return Array.from({ length: days }, (_, i) => {
    const d = new Date(today)
    d.setDate(d.getDate() - (days - 1 - i))
    const label   = d.toLocaleDateString('es-MX', { day: 'numeric', month: 'short' })
    const dateKey = d.toDateString()
    const day     = execs.filter(e => new Date(e.startTime).toDateString() === dateKey)
    return {
      day,
      label,
      Exitosas: day.reduce((s, e) => s + e.passed,  0),
      Fallidas: day.reduce((s, e) => s + e.failed,  0),
      Omitidas: day.reduce((s, e) => s + e.skipped, 0),
    }
  }).map(({ label, Exitosas, Fallidas, Omitidas }) => ({ day: label, Exitosas, Fallidas, Omitidas }))
}

// ─── Atoms ────────────────────────────────────────────────────────────────────
function Panel({ children, delay = 0, className = '' }: {
  children: React.ReactNode; delay?: number; className?: string
}) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: 'easeOut', delay }}
      className={`rounded-2xl ${className}`}
      style={{
        background: 'var(--panel-bg)',
        border: '1px solid var(--panel-border)',
        boxShadow: 'var(--panel-shadow)',
      }}
    >
      {children}
    </motion.div>
  )
}

function PanelHead({ title, sub, right }: { title: string; sub?: string; right?: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between px-5 py-3 flex-shrink-0"
      style={{ borderBottom: '1px solid var(--panel-divide)' }}>
      <div>
        <div className="text-sm font-bold text-slate-100">{title}</div>
        {sub && <div className="text-xs text-slate-500 mt-0.5">{sub}</div>}
      </div>
      {right}
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  const color = STATUS_COLORS[status] ?? '#64748b'
  return (
    <span className="text-[10px] font-bold px-2 py-0.5 rounded-full whitespace-nowrap"
      style={{ background: `${color}20`, color, border: `1px solid ${color}40` }}>
      {status}
    </span>
  )
}

function Toggle({ enabled, onChange }: { enabled: boolean; onChange: () => void }) {
  return (
    <button onClick={onChange}
      style={{
        width: 36, height: 20, borderRadius: 10,
        background: enabled ? '#10b981' : 'rgba(255,255,255,0.1)',
        border: `1.5px solid ${enabled ? '#10b981' : 'rgba(255,255,255,0.15)'}`,
        transition: 'background .25s, border-color .25s',
        cursor: 'pointer', padding: 2, flexShrink: 0, position: 'relative' as const, display: 'inline-flex',
      }}
    >
      <div style={{
        width: 12, height: 12, borderRadius: '50%', background: '#fff',
        transition: 'transform .25s',
        transform: enabled ? 'translateX(16px)' : 'translateX(0)',
      }} />
    </button>
  )
}

function MiniSelect({ value, options, onChange, label }: {
  value: string; options: string[]; onChange: (v: string) => void; label?: string
}) {
  return (
    <div className="flex flex-col gap-1">
      {label && <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">{label}</span>}
      <div className="relative">
        <select value={value} onChange={e => onChange(e.target.value)}
          className="w-full appearance-none text-xs font-medium text-slate-300 rounded-xl px-3 py-2 pr-7"
          style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)', outline: 'none', cursor: 'pointer' }}>
          {options.map(o => <option key={o} value={o} style={{ background: '#0c1226' }}>{o}</option>)}
        </select>
        <ChevronDown size={11} className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
      </div>
    </div>
  )
}

function ExportRow({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <button className="w-full flex items-center justify-between px-3 py-2 rounded-xl text-xs font-medium text-slate-400 transition-all group"
      style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.07)' }}>
      <div className="flex items-center gap-2.5">
        <span className="text-slate-500">{icon}</span>
        {label}
      </div>
      <ChevronDown size={11} className="-rotate-90 text-slate-600" />
    </button>
  )
}

// ─── Main Component ───────────────────────────────────────────────────────────
export default function ReportsPage() {
  const [activeTab,  setActiveTab]  = useState<Tab>('Resumen')
  const [executions, setExecutions] = useState<ExecutionSummary[]>([])
  const [loading,    setLoading]    = useState(true)

  // Filters
  const [period,  setPeriod]  = useState<Period>('Últimos 30 días')
  const [suite,   setSuite]   = useState('Todas')
  const [device,  setDevice]  = useState('Todos')
  const [status,  setStatus]  = useState('Todos')

  // Allure toggles
  const [allure, setAllure] = useState({
    autoGen: true, screenshots: true, videos: true, logs: true,
  })
  const [retainDays, setRetainDays] = useState('30 días')

  // Report type checkboxes
  const [rTypes, setRTypes] = useState({
    resumen: true, porSuite: true, porTest: true,
    evidencias: true, videos: true, logs: true,
    dispositivos: true, duracion: true,
  })

  // Compare
  const [cmpBase,   setCmpBase]   = useState('')
  const [cmpTarget, setCmpTarget] = useState('')

  // Fetch
  useEffect(() => {
    const load = async () => {
      try { setExecutions(await getExecutions()) } catch { /* offline */ }
      setLoading(false)
    }
    load()
    const id = setInterval(load, 30_000)
    return () => clearInterval(id)
  }, [])

  // Filtered data
  const filtered = useMemo(
    () => filterExecs(executions, period, suite, device, status),
    [executions, period, suite, device, status],
  )

  // Previous period (for deltas)
  const prevFiltered = useMemo(() => {
    const cutoff = getPeriodCutoff(period)
    if (!cutoff) return executions
    const now = new Date()
    const span = now.getTime() - cutoff.getTime()
    const prevStart = new Date(cutoff.getTime() - span)
    return executions.filter(e => {
      const t = new Date(e.startTime)
      return t >= prevStart && t < cutoff
    })
  }, [executions, period])

  // Stats
  const S = useMemo(() => {
    const total   = filtered.reduce((s, e) => s + e.total,   0)
    const passed  = filtered.reduce((s, e) => s + e.passed,  0)
    const failed  = filtered.reduce((s, e) => s + e.failed,  0)
    const skipped = filtered.reduce((s, e) => s + e.skipped, 0)
    const withDur = filtered.filter(e => e.endTime)
    const avgMs   = withDur.length
      ? withDur.reduce((s, e) => s + execDurationMs(e), 0) / withDur.length : 0

    const pTotal   = prevFiltered.reduce((s, e) => s + e.total,  0)
    const pPassed  = prevFiltered.reduce((s, e) => s + e.passed, 0)
    const pWithDur = prevFiltered.filter(e => e.endTime)
    const pAvgMs   = pWithDur.length
      ? pWithDur.reduce((s, e) => s + execDurationMs(e), 0) / pWithDur.length : 0

    const coverage  = total > 0 ? Math.round((passed  / total)  * 100) : 0
    const pCoverage = pTotal > 0 ? Math.round((pPassed / pTotal) * 100) : 0

    const pct = (v: number) => total > 0 ? ((v / total) * 100).toFixed(1) : '0.0'
    const delta = (curr: number, prev: number) =>
      prev > 0 ? Math.round(((curr - prev) / prev) * 100) : null

    return {
      total, passed, failed, skipped, avgMs, coverage,
      passedPct:  pct(passed),
      failedPct:  pct(failed),
      skippedPct: pct(skipped),
      totalDelta:    delta(total,    pTotal),
      avgDelta:      delta(avgMs,    pAvgMs),
      coverageDelta: pCoverage > 0 ? coverage - pCoverage : null,
    }
  }, [filtered, prevFiltered])

  // Chart data
  const periodDays = period === 'Últimos 7 días' ? 7 : period === 'Últimos 90 días' ? 60 : 30
  const timeSeries = useMemo(() => buildTimeSeries(filtered, periodDays), [filtered, periodDays])

  // Filter options
  const suiteOpts  = useMemo(() => ['Todas', ...Array.from(new Set(executions.map(e => e.suite)))],  [executions])
  const deviceOpts = useMemo(() => ['Todos', ...Array.from(new Set(executions.map(e => e.device)))], [executions])
  const statusOpts = ['Todos', 'PASSED', 'FAILED', 'SKIPPED', 'ABORTED']

  // Recent (last 5 in filtered)
  const recent = useMemo(() =>
    [...filtered]
      .sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime())
      .slice(0, 5),
    [filtered],
  )

  // Latest Allure URL
  const allureUrl = useMemo(() => executions.find(e => e.allureUrl)?.allureUrl ?? null, [executions])

  // Execution options for comparison dropdown
  const execOpts = useMemo(() =>
    ['Selecciona ejecución', ...filtered.slice(0, 30).map(e => e.executionId)],
    [filtered],
  )

  const clearFilters = () => {
    setPeriod('Últimos 30 días'); setSuite('Todas'); setDevice('Todos'); setStatus('Todos')
  }

  // ── Donut data ────────────────────────────────────────────────────────────
  const donutTotal = S.passed + S.failed + S.skipped
  const donutData  = donutTotal === 0
    ? [{ name: 'Sin datos', value: 1, color: 'rgba(255,255,255,0.07)' }]
    : [
        { name: 'Exitosas', value: S.passed,  color: '#10b981' },
        { name: 'Fallidas', value: S.failed,  color: '#f43f5e' },
        { name: 'Omitidas', value: S.skipped, color: '#f59e0b' },
      ]
  const pct = (v: number) => donutTotal > 0 ? ((v / donutTotal) * 100).toFixed(1) : '0.0'

  // ── Chart tooltip ─────────────────────────────────────────────────────────
  const ChartTip = ({ active, payload, label }: any) => {
    if (!active || !payload?.length) return null
    return (
      <div className="px-3 py-2.5 rounded-xl text-xs"
        style={{ background: 'rgba(4,8,22,0.96)', border: '1px solid rgba(255,255,255,0.1)', boxShadow: '0 8px 32px rgba(0,0,0,0.6)' }}>
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
  }

  // ── Stat card ─────────────────────────────────────────────────────────────
  const StatCard = ({
    icon, label, value, sub, delta, color, delay,
  }: {
    icon: React.ReactNode; label: string; value: string
    sub?: string; delta?: number | null; color: string; delay: number
  }) => (
    <Panel delay={delay} className="p-5 flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider leading-tight">{label}</span>
        <div className="w-8 h-8 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{ background: `${color}20` }}>
          <span style={{ color }}>{icon}</span>
        </div>
      </div>
      <div className="text-3xl font-black" style={{ color: 'var(--text-pri)' }}>{value}</div>
      <div className="flex flex-col gap-0.5 min-h-[28px]">
        {sub && <span className="text-[10px] text-slate-500">{sub}</span>}
        {delta != null && (
          <span className="flex items-center gap-0.5 text-[10px] font-bold"
            style={{ color: delta >= 0 ? '#10b981' : '#f43f5e' }}>
            {delta >= 0 ? <TrendingUp size={10} /> : <TrendingDown size={10} />}
            {Math.abs(delta)}% vs periodo anterior
          </span>
        )}
      </div>
    </Panel>
  )

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="p-6 space-y-5" style={{ background: 'var(--bg-main)', minHeight: '100%' }}>

      {/* Page header */}
      <div>
        <h1 className="text-2xl font-black" style={{ color: 'var(--text-pri)' }}>Reportes</h1>
        <p className="text-sm text-slate-500 mt-0.5">
          Visualiza, analiza y gestiona todos los reportes de tus pruebas automatizadas
        </p>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-0 border-b overflow-x-auto" style={{ borderColor: 'var(--panel-border)' }}>
        {TABS.map(tab => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className="px-4 py-2.5 text-sm font-semibold relative whitespace-nowrap flex items-center gap-1.5 transition-colors flex-shrink-0"
            style={{ color: activeTab === tab ? 'var(--text-pri)' : 'var(--text-dim)' }}
          >
            {tab === 'Configuración' && <Settings size={13} />}
            {tab}
            {activeTab === tab && (
              <motion.div
                layoutId="rp-tab"
                className="absolute bottom-0 left-0 right-0 h-0.5 rounded-full"
                style={{ background: '#6366f1' }}
              />
            )}
          </button>
        ))}
      </div>

      {/* ═══════════════════ TAB: RESUMEN ═══════════════════ */}
      {activeTab === 'Resumen' && (
        <>
          {/* Stats row */}
          <div className="grid grid-cols-6 gap-3">
            <StatCard icon={<BarChart3 size={15} />}    label="Ejecuciones Totales" value={S.total.toLocaleString()}            delta={S.totalDelta}    color="#10b981" delay={0}    />
            <StatCard icon={<CheckCircle2 size={15} />} label="Exitosas"            value={S.passed.toLocaleString()}           sub={`${S.passedPct}% del total`}  color="#10b981" delay={0.04} />
            <StatCard icon={<XCircle size={15} />}      label="Falladas"            value={S.failed.toLocaleString()}           sub={`${S.failedPct}% del total`}  color="#f43f5e" delay={0.08} />
            <StatCard icon={<MinusCircle size={15} />}  label="Omitidas"            value={S.skipped.toLocaleString()}          sub={`${S.skippedPct}% del total`} color="#f59e0b" delay={0.12} />
            <StatCard icon={<Clock size={15} />}        label="Tiempo Promedio"     value={formatDuration(S.avgMs)}             delta={S.avgDelta}      color="#8b5cf6" delay={0.16} />
            <StatCard icon={<ShieldCheck size={15} />}  label="Cobertura"           value={`${S.coverage}%`}                    delta={S.coverageDelta} color="#3b82f6" delay={0.2}  />
          </div>

          {/* Charts row */}
          <div className="grid gap-4" style={{ gridTemplateColumns: '1fr 320px', height: 288 }}>

            {/* Time series */}
            <Panel delay={0.1} className="flex flex-col overflow-hidden">
              <PanelHead
                title="Ejecuciones en el Tiempo"
                sub={period}
                right={
                  <div className="flex items-center gap-4">
                    {[{ l: 'Exitosas', c: '#10b981' }, { l: 'Fallidas', c: '#f43f5e' }, { l: 'Omitidas', c: '#f59e0b' }].map(s => (
                      <div key={s.l} className="flex items-center gap-1.5 text-[11px]" style={{ color: s.c }}>
                        <div className="w-2 h-2 rounded-full" style={{ background: s.c }} />
                        {s.l}
                      </div>
                    ))}
                    <MiniSelect value={period} options={[...PERIODS]} onChange={v => setPeriod(v as Period)} />
                  </div>
                }
              />
              <div className="flex-1 min-h-0 px-2 py-2">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={timeSeries} margin={{ top: 4, right: 12, bottom: 0, left: -28 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                    <XAxis dataKey="day" tick={{ fill: '#475569', fontSize: 9 }} axisLine={false} tickLine={false}
                      interval={Math.max(0, Math.floor(timeSeries.length / 8) - 1)} />
                    <YAxis tick={{ fill: '#475569', fontSize: 10 }} axisLine={false} tickLine={false} allowDecimals={false} />
                    <RTooltip content={<ChartTip />} />
                    {[
                      { key: 'Exitosas', color: '#10b981' },
                      { key: 'Fallidas', color: '#f43f5e' },
                      { key: 'Omitidas', color: '#f59e0b' },
                    ].map(s => (
                      <Line key={s.key} type="monotone" dataKey={s.key}
                        stroke={s.color} strokeWidth={2}
                        dot={false} activeDot={{ r: 4, fill: s.color }}
                      />
                    ))}
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </Panel>

            {/* Donut */}
            <Panel delay={0.15} className="flex flex-col overflow-hidden">
              <PanelHead title="Distribución por Estado" sub="Resumen del período" />
              <div className="flex-1 flex flex-col items-center justify-center px-4 py-2 min-h-0">
                <div className="relative w-full" style={{ height: 150 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={donutData} cx="50%" cy="50%"
                        innerRadius={44} outerRadius={62}
                        paddingAngle={donutTotal === 0 ? 0 : 3}
                        dataKey="value" startAngle={90} endAngle={-270} strokeWidth={0}>
                        {donutData.map((d, i) => <Cell key={i} fill={d.color} />)}
                      </Pie>
                      {donutTotal > 0 && (
                        <RTooltip content={({ active, payload }) => {
                          if (!active || !payload?.length) return null
                          const item = payload[0].payload as typeof donutData[0]
                          return (
                            <div className="px-3 py-2 rounded-xl text-xs font-semibold"
                              style={{ background: 'rgba(7,12,28,0.96)', border: `1px solid ${item.color}44`, color: item.color }}>
                              {item.name}: {item.value} ({pct(item.value)}%)
                            </div>
                          )
                        }} />
                      )}
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                    <div className="text-2xl font-black text-slate-100">{donutTotal === 0 ? '0' : donutTotal.toLocaleString()}</div>
                    <div className="text-[9px] font-semibold text-slate-500 uppercase tracking-wider">
                      {donutTotal === 0 ? 'Sin datos' : 'Total'}
                    </div>
                  </div>
                </div>
                <div className="w-full space-y-2 mt-1">
                  {[
                    { name: 'Exitosas', val: S.passed,  color: '#10b981' },
                    { name: 'Fallidas', val: S.failed,  color: '#f43f5e' },
                    { name: 'Omitidas', val: S.skipped, color: '#f59e0b' },
                  ].map(s => (
                    <div key={s.name} className="flex items-center gap-2">
                      <div className="w-2 h-2 rounded-full"
                        style={{ background: donutTotal ? s.color : 'rgba(255,255,255,0.1)' }} />
                      <span className="text-[11px] text-slate-500 flex-1">{s.name}</span>
                      <span className="text-[11px] font-bold" style={{ color: donutTotal ? s.color : '#334155' }}>
                        {pct(s.val)}%
                      </span>
                      <span className="text-[10px] text-slate-600">({s.val})</span>
                    </div>
                  ))}
                </div>
              </div>
            </Panel>
          </div>

          {/* Recent activity */}
          <Panel delay={0.2} className="overflow-hidden">
            <PanelHead
              title="Actividad Reciente"
              sub="Últimos reportes generados"
              right={
                <button className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 transition-colors">
                  Ver todos
                </button>
              }
            />
            {loading ? (
              <div className="py-8 text-center text-xs text-slate-600">Cargando...</div>
            ) : recent.length === 0 ? (
              <div className="py-8 text-center text-xs text-slate-600">Sin ejecuciones en el período seleccionado</div>
            ) : (
              <div className="divide-y divide-[var(--panel-border)]">
                {recent.map((e, i) => {
                  const dur = execDurationMs(e)
                  const isToday = new Date(e.startTime).toDateString() === new Date().toDateString()
                  const dateStr = new Date(e.startTime).toLocaleString('es-MX', {
                    hour: '2-digit', minute: '2-digit', hour12: true,
                    ...(!isToday ? { month: 'short', day: 'numeric' } : {}),
                  })
                  return (
                    <div key={e.executionId}
                      className="flex items-center gap-4 px-5 py-3"
                      style={{ borderTop: i === 0 ? 'none' : '1px solid var(--panel-divide)' }}>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="text-xs font-bold text-indigo-400">{e.executionId}</span>
                          <span className="text-xs font-semibold text-slate-300 truncate">— {e.suite}</span>
                        </div>
                        <div className="text-[10px] text-slate-600 mt-0.5">
                          {e.device}{e.env ? ` • ${e.env}` : ''}
                        </div>
                      </div>
                      <StatusBadge status={e.status} />
                      <span className="text-[10px] text-slate-600 whitespace-nowrap">
                        {isToday ? 'Hoy, ' : ''}{dateStr}
                        {dur > 0 && <span className="ml-1.5 text-slate-700">• {formatDuration(dur)}</span>}
                      </span>
                    </div>
                  )
                })}
              </div>
            )}
          </Panel>

          {/* Bottom 5 cards */}
          <div className="grid grid-cols-5 gap-3">

            {/* 1 — Reportes Allure */}
            <Panel delay={0.25} className="p-4 flex flex-col gap-3">
              <div>
                <div className="text-sm font-bold text-slate-100">Reportes Allure</div>
                <div className="text-[10px] text-slate-500 mt-0.5">Configuración y acceso a reportes Allure</div>
              </div>
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl flex items-center justify-center text-xl"
                  style={{ background: 'rgba(249,115,22,0.15)' }}>🔥</div>
                <span className="text-sm font-bold text-slate-200">Allure</span>
              </div>
              <button
                onClick={() => { if (allureUrl) window.open(allureUrl, '_blank') }}
                className="flex items-center justify-center gap-1.5 text-xs font-bold py-2 rounded-xl transition-opacity"
                style={{
                  background: allureUrl ? 'rgba(249,115,22,0.12)' : 'rgba(255,255,255,0.04)',
                  border: `1px solid ${allureUrl ? 'rgba(249,115,22,0.3)' : 'rgba(255,255,255,0.08)'}`,
                  color: allureUrl ? '#f97316' : '#475569',
                  cursor: allureUrl ? 'pointer' : 'not-allowed',
                  opacity: allureUrl ? 1 : 0.6,
                }}
              >
                <ExternalLink size={11} /> Abrir Allure Report
              </button>
              <div className="space-y-2">
                {([
                  ['autoGen',      'Generación automática'],
                  ['screenshots',  'Adjuntar screenshots'],
                  ['videos',       'Adjuntar videos'],
                  ['logs',         'Adjuntar logs'],
                ] as [keyof typeof allure, string][]).map(([k, lbl]) => (
                  <div key={k} className="flex items-center justify-between">
                    <span className="text-[11px] text-slate-400">{lbl}</span>
                    <Toggle enabled={allure[k]} onChange={() => setAllure(p => ({ ...p, [k]: !p[k] }))} />
                  </div>
                ))}
              </div>
              <MiniSelect label="Historial de días a conservar"
                value={retainDays}
                options={['7 días', '14 días', '30 días', '60 días', '90 días']}
                onChange={setRetainDays}
              />
              <button className="text-[11px] font-semibold py-1.5 rounded-lg transition-colors"
                style={{ background: 'rgba(248,81,73,0.1)', color: '#f85149', border: '1px solid rgba(248,81,73,0.2)' }}>
                Limpiar reportes antiguos
              </button>
            </Panel>

            {/* 2 — Tipos de Reporte */}
            <Panel delay={0.3} className="p-4 flex flex-col gap-3">
              <div>
                <div className="text-sm font-bold text-slate-100">Tipos de Reporte</div>
                <div className="text-[10px] text-slate-500 mt-0.5">Selecciona qué información incluir</div>
              </div>
              <div className="space-y-1.5 flex-1">
                {([
                  ['resumen',      'Resumen ejecutivo'],
                  ['porSuite',     'Resultados por suite'],
                  ['porTest',      'Resultados por test case'],
                  ['evidencias',   'Evidencias (screenshots)'],
                  ['videos',       'Videos de ejecución'],
                  ['logs',         'Logs completos'],
                  ['dispositivos', 'Dispositivos y ambiente'],
                  ['duracion',     'Duración y tiempos'],
                ] as [keyof typeof rTypes, string][]).map(([k, lbl]) => (
                  <label key={k} className="flex items-center gap-2 cursor-pointer" onClick={() => setRTypes(p => ({ ...p, [k]: !p[k] }))}>
                    <span style={{ color: rTypes[k] ? '#10b981' : '#334155' }}>
                      {rTypes[k] ? <CheckSquare size={13} /> : <Square size={13} />}
                    </span>
                    <span className="text-[11px]" style={{ color: rTypes[k] ? '#cbd5e1' : '#475569' }}>{lbl}</span>
                  </label>
                ))}
              </div>
              <button className="text-xs font-bold py-2 rounded-xl"
                style={{ background: 'linear-gradient(135deg, #4f46e5, #7c3aed)', color: '#fff' }}>
                Guardar Preferencias
              </button>
            </Panel>

            {/* 3 — Exportar Reportes */}
            <Panel delay={0.35} className="p-4 flex flex-col gap-3">
              <div>
                <div className="text-sm font-bold text-slate-100">Exportar Reportes</div>
                <div className="text-[10px] text-slate-500 mt-0.5">Descarga reportes en diferentes formatos</div>
              </div>
              <div className="space-y-2 flex-1">
                <ExportRow icon={<FileText size={13} />}     label="Exportar a PDF" />
                <ExportRow icon={<Table2 size={13} />}       label="Exportar a Excel" />
                <ExportRow icon={<AlignLeft size={13} />}    label="Exportar a CSV" />
                <ExportRow icon={<FileCode2 size={13} />}    label="Exportar a JSON" />
                <ExportRow icon={<Archive size={13} />}      label="Exportar Evidencias (ZIP)" />
              </div>
            </Panel>

            {/* 4 — Comparar Ejecuciones */}
            <Panel delay={0.4} className="p-4 flex flex-col gap-3">
              <div>
                <div className="text-sm font-bold text-slate-100">Comparar Ejecuciones</div>
                <div className="text-[10px] text-slate-500 mt-0.5">Compara resultados entre ejecuciones</div>
              </div>
              <div className="space-y-3 flex-1">
                <MiniSelect label="Ejecución base"
                  value={cmpBase || 'Selecciona ejecución'}
                  options={execOpts}
                  onChange={setCmpBase}
                />
                <MiniSelect label="Ejecución a comparar"
                  value={cmpTarget || 'Selecciona ejecución'}
                  options={execOpts}
                  onChange={setCmpTarget}
                />
              </div>
              <button
                className="flex items-center justify-center gap-2 text-xs font-bold py-2 rounded-xl"
                style={{
                  background: (cmpBase && cmpTarget && cmpBase !== 'Selecciona ejecución' && cmpTarget !== 'Selecciona ejecución')
                    ? 'linear-gradient(135deg, #4f46e5, #6366f1)' : 'rgba(255,255,255,0.04)',
                  color: (cmpBase && cmpTarget) ? '#fff' : '#475569',
                  border: '1px solid rgba(255,255,255,0.08)',
                }}
              >
                <GitCompare size={13} />
                Comparar Resultados
              </button>
            </Panel>

            {/* 5 — Filtros Rápidos */}
            <Panel delay={0.45} className="p-4 flex flex-col gap-3">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-sm font-bold text-slate-100">Filtros Rápidos</div>
                  <div className="text-[10px] text-slate-500 mt-0.5">Filtra reportes rápidamente</div>
                </div>
                <SlidersHorizontal size={14} className="text-slate-600" />
              </div>
              <div className="space-y-2.5 flex-1">
                <MiniSelect label="Período"     value={period} options={[...PERIODS]}  onChange={v => setPeriod(v as Period)} />
                <MiniSelect label="Suite"       value={suite}  options={suiteOpts}     onChange={setSuite} />
                <MiniSelect label="Dispositivo" value={device} options={deviceOpts}    onChange={setDevice} />
                <MiniSelect label="Estado"      value={status} options={statusOpts}    onChange={setStatus} />
              </div>
              <button onClick={clearFilters}
                className="flex items-center justify-center gap-1.5 text-xs font-semibold py-1.5 rounded-xl"
                style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)', color: '#64748b' }}>
                <X size={11} /> Limpiar Filtros
              </button>
            </Panel>
          </div>
        </>
      )}

      {/* ═══════════════════ TAB: EJECUCIONES ═══════════════════ */}
      {activeTab === 'Ejecuciones' && (
        <Panel delay={0} className="overflow-hidden">
          <PanelHead
            title="Todas las Ejecuciones"
            sub={`${filtered.length} resultado${filtered.length !== 1 ? 's' : ''}`}
            right={
              <div className="flex items-center gap-2">
                <MiniSelect value={status} options={statusOpts} onChange={setStatus} />
                <MiniSelect value={suite}  options={suiteOpts}  onChange={setSuite} />
              </div>
            }
          />
          {loading ? (
            <div className="py-12 text-center text-xs text-slate-600">Cargando ejecuciones...</div>
          ) : filtered.length === 0 ? (
            <div className="py-12 text-center text-xs text-slate-600">Sin ejecuciones para los filtros seleccionados</div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--panel-divide)' }}>
                    {['ID', 'Suite', 'Dispositivo', 'Env', 'País', 'Estado', 'Total', 'Exitosos', 'Fallados', 'Omitidos', 'Duración', 'Inicio'].map(h => (
                      <th key={h} className="px-4 py-2.5 text-left font-semibold text-slate-500 whitespace-nowrap">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((e, i) => (
                    <tr key={e.executionId}
                      style={{ borderBottom: '1px solid var(--panel-divide)', background: i % 2 ? 'rgba(255,255,255,0.012)' : 'transparent' }}>
                      <td className="px-4 py-2.5 font-bold text-indigo-400">{e.executionId}</td>
                      <td className="px-4 py-2.5 text-slate-300 max-w-[130px] truncate">{e.suite}</td>
                      <td className="px-4 py-2.5 text-slate-400 max-w-[110px] truncate">{e.device}</td>
                      <td className="px-4 py-2.5 text-slate-500">{e.env}</td>
                      <td className="px-4 py-2.5 text-slate-500 capitalize">{e.country}</td>
                      <td className="px-4 py-2.5"><StatusBadge status={e.status} /></td>
                      <td className="px-4 py-2.5 text-slate-300 text-center font-semibold">{e.total}</td>
                      <td className="px-4 py-2.5 text-center font-bold" style={{ color: '#10b981' }}>{e.passed}</td>
                      <td className="px-4 py-2.5 text-center font-bold" style={{ color: '#f43f5e' }}>{e.failed}</td>
                      <td className="px-4 py-2.5 text-center font-bold" style={{ color: '#f59e0b' }}>{e.skipped}</td>
                      <td className="px-4 py-2.5 text-slate-500 whitespace-nowrap">{formatDuration(execDurationMs(e))}</td>
                      <td className="px-4 py-2.5 text-slate-600 whitespace-nowrap">
                        {new Date(e.startTime).toLocaleString('es-MX', {
                          hour: '2-digit', minute: '2-digit', hour12: true, month: 'short', day: 'numeric',
                        })}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Panel>
      )}

      {/* ═══════════════════ OTHER TABS (placeholder) ═══════════════════ */}
      {!['Resumen', 'Ejecuciones'].includes(activeTab) && (
        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }}
          className="flex items-center justify-center rounded-2xl py-24"
          style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)' }}>
          <div className="text-center">
            <div className="text-4xl mb-3 opacity-20">🚧</div>
            <div className="text-sm font-bold text-slate-500">{activeTab}</div>
            <div className="text-xs text-slate-600 mt-1">Esta sección estará disponible próximamente</div>
          </div>
        </motion.div>
      )}
    </div>
  )
}
