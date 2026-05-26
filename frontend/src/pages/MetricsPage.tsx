import React, { useState, useEffect, useMemo } from 'react'
import {
  LineChart, Line, BarChart, Bar, PieChart, Pie, Cell,
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip,
  ResponsiveContainer,
} from 'recharts'
import {
  CheckCircle2, XCircle, Clock, Target, Smartphone,
  ChevronDown, ArrowUp, ArrowDown, AlertTriangle, Info,
  BarChart3, Activity, Calendar,
} from 'lucide-react'
import { getExecutions } from '../api'
import type { ExecutionSummary } from '../types'

// ─── helpers ──────────────────────────────────────────────────────────────────

function fmtMs(ms: number): string {
  if (ms <= 0) return '0s'
  const s = Math.round(ms / 1000)
  const m = Math.floor(s / 60)
  const sec = s % 60
  return m > 0 ? `${m}m ${sec}s` : `${sec}s`
}

function pctDelta(curr: number, prev: number): number | null {
  if (prev === 0) return null
  return Math.round(((curr - prev) / prev) * 100)
}

const PERIODS = [
  { label: 'Últimos 7 días',  days: 7  },
  { label: 'Últimos 30 días', days: 30 },
  { label: 'Últimos 60 días', days: 60 },
  { label: 'Últimos 90 días', days: 90 },
]

const AREA_COLOR = '#8b5cf6'
const BAR_COLOR  = '#6366f1'

// ─── tiny sub-components ──────────────────────────────────────────────────────

function Panel({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  return (
    <div
      className={`rounded-2xl overflow-hidden ${className}`}
      style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)' }}
    >
      {children}
    </div>
  )
}

function PHead({ title, subtitle, action }: {
  title: string; subtitle?: string; action?: React.ReactNode
}) {
  return (
    <div className="flex items-start justify-between px-5 pt-5 pb-3">
      <div>
        <div className="text-sm font-semibold" style={{ color: 'var(--text-pri)' }}>{title}</div>
        {subtitle && (
          <div className="text-[11px] mt-0.5" style={{ color: 'var(--text-dim)' }}>{subtitle}</div>
        )}
      </div>
      {action}
    </div>
  )
}

function MiniSelect({ value, options, onChange }: {
  value: string; options: string[]; onChange: (v: string) => void
}) {
  return (
    <div className="relative">
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        className="appearance-none text-[11px] font-semibold pr-5 pl-2 py-1 rounded-lg cursor-pointer outline-none"
        style={{
          background: 'rgba(99,102,241,0.12)',
          border: '1px solid rgba(99,102,241,0.25)',
          color: 'var(--text-sec)',
        }}
      >
        {options.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
      <ChevronDown size={10} className="absolute right-1.5 top-1/2 -translate-y-1/2 pointer-events-none"
        style={{ color: 'var(--text-dim)' }} />
    </div>
  )
}

function Delta({ pct, inverse = false }: { pct: number | null; inverse?: boolean }) {
  if (pct === null) return null
  const good  = inverse ? pct < 0 : pct >= 0
  const color = good ? '#10b981' : '#f43f5e'
  const Icon  = pct >= 0 ? ArrowUp : ArrowDown
  return (
    <span className="flex items-center gap-0.5 text-[11px] font-semibold" style={{ color }}>
      <Icon size={11} />{Math.abs(pct)}%
    </span>
  )
}

function EmptyChart({ small = false }: { small?: boolean }) {
  return (
    <div
      className={`flex items-center justify-center ${small ? 'h-28' : 'h-44'} rounded-xl text-xs`}
      style={{ background: 'rgba(255,255,255,0.02)', color: 'var(--text-dim)' }}
    >
      Sin datos en este período
    </div>
  )
}

// ─── StatCard ─────────────────────────────────────────────────────────────────

function StatCard({ icon, iconColor, label, value, sub }: {
  icon: React.ReactNode; iconColor: string; label: string
  value: string | number; sub: React.ReactNode
}) {
  return (
    <div className="rounded-2xl p-4" style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)' }}>
      <div className="flex items-center gap-3 mb-3">
        <div
          className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{ background: `${iconColor}22`, color: iconColor }}
        >
          {icon}
        </div>
        <span className="text-[11px] leading-tight" style={{ color: 'var(--text-dim)' }}>{label}</span>
      </div>
      <div className="text-[26px] font-bold leading-none mb-1.5" style={{ color: 'var(--text-pri)' }}>{value}</div>
      <div className="flex items-center gap-1 flex-wrap text-[11px]">{sub}</div>
    </div>
  )
}

// ─── InsightCard ──────────────────────────────────────────────────────────────

function InsightCard({ type, title, desc }: {
  type: 'success' | 'warning' | 'info'; title: string; desc: string
}) {
  const cfg = {
    success: { icon: <ArrowUp size={14} />,        bg: '#10b98122', color: '#10b981' },
    warning: { icon: <AlertTriangle size={14} />,  bg: '#f59e0b22', color: '#f59e0b' },
    info:    { icon: <Info size={14} />,            bg: '#3b82f622', color: '#3b82f6' },
  }[type]
  return (
    <div className="rounded-xl p-3" style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid var(--panel-border)' }}>
      <div
        className="w-7 h-7 rounded-lg flex items-center justify-center mb-2"
        style={{ background: cfg.bg, color: cfg.color }}
      >
        {cfg.icon}
      </div>
      <div className="text-[12px] font-semibold mb-1" style={{ color: 'var(--text-pri)' }}>{title}</div>
      <div className="text-[11px] leading-relaxed" style={{ color: 'var(--text-dim)' }}>{desc}</div>
    </div>
  )
}

// ─── TrendItem ────────────────────────────────────────────────────────────────

function TrendItem({ label, pct, inverse = false }: {
  label: string; pct: number | null; inverse?: boolean
}) {
  const noData  = pct === null
  const good    = !noData && (inverse ? pct < 0 : pct >= 0)
  const color   = noData ? 'var(--text-dim)' : good ? '#10b981' : '#f43f5e'
  const sign    = pct !== null && pct > 0 ? '+' : ''
  return (
    <div className="text-center">
      <div className="text-2xl font-bold" style={{ color }}>
        {noData ? '—' : `${sign}${pct}%`}
      </div>
      <div className="text-[12px] font-semibold mt-0.5" style={{ color: 'var(--text-sec)' }}>{label}</div>
      <div className="text-[11px] mt-0.5" style={{ color: 'var(--text-dim)' }}>vs. período anterior</div>
    </div>
  )
}

// ─── MetricsPage ──────────────────────────────────────────────────────────────

export default function MetricsPage() {
  const [executions, setExecutions] = useState<ExecutionSummary[]>([])
  const [loading,    setLoading]    = useState(true)
  const [period,     setPeriod]     = useState(1)
  const [gran,       setGran]       = useState('Diario')

  useEffect(() => {
    setLoading(true)
    getExecutions()
      .then(setExecutions)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  const days = PERIODS[period].days

  // ── filtered / previous period ──────────────────────────────────────────────
  const { filtered, previous } = useMemo(() => {
    const now     = new Date()
    const cutoff  = new Date(now.getTime() - days * 86_400_000)
    const prevCut = new Date(cutoff.getTime() - days * 86_400_000)
    return {
      filtered: executions.filter(e => new Date(e.startTime) >= cutoff),
      previous: executions.filter(e => {
        const d = new Date(e.startTime)
        return d >= prevCut && d < cutoff
      }),
    }
  }, [executions, days])

  // ── aggregate stats ─────────────────────────────────────────────────────────
  const stats = useMemo(() => {
    const sum = (arr: ExecutionSummary[], fn: (e: ExecutionSummary) => number) =>
      arr.reduce((s, e) => s + fn(e), 0)

    const cp = sum(filtered, e => e.passed),  cf = sum(filtered, e => e.failed),
          cs = sum(filtered, e => e.skipped), ct = cp + cf + cs
    const pp = sum(previous, e => e.passed),  pf = sum(previous, e => e.failed),
          ps = sum(previous, e => e.skipped), pt = pp + pf + ps

    const avgMs = (arr: ExecutionSummary[]) => {
      const d = arr.filter(e => e.endTime)
        .map(e => new Date(e.endTime!).getTime() - new Date(e.startTime).getTime())
      return d.length ? d.reduce((s, v) => s + v, 0) / d.length : 0
    }

    return {
      totalExecs: filtered.length, prevExecs: previous.length,
      cp, cf, cs, ct, pp, pf, ps, pt,
      cRate: ct > 0 ? Math.round((cp / ct) * 1000) / 10 : 0,
      pRate: pt > 0 ? Math.round((pp / pt) * 1000) / 10 : 0,
      cAvgMs: avgMs(filtered), pAvgMs: avgMs(previous),
    }
  }, [filtered, previous])

  // ── date key helper ─────────────────────────────────────────────────────────
  const dateKey = (d: Date) =>
    `${d.getDate()} ${d.toLocaleString('es-MX', { month: 'short' })}`

  // ── daily evolution (lines) ─────────────────────────────────────────────────
  const dailyData = useMemo(() => {
    const map = new Map<string, { passed: number; failed: number; skipped: number; _d: Date }>()
    filtered.forEach(e => {
      const k = dateKey(new Date(e.startTime))
      if (!map.has(k)) map.set(k, { passed: 0, failed: 0, skipped: 0, _d: new Date(e.startTime) })
      const v = map.get(k)!
      v.passed += e.passed; v.failed += e.failed; v.skipped += e.skipped
    })
    return [...map.entries()]
      .sort((a, b) => a[1]._d.getTime() - b[1]._d.getTime())
      .map(([date, v]) => ({ date, passed: v.passed, failed: v.failed, skipped: v.skipped }))
  }, [filtered])

  // ── daily exec counts (bars) ────────────────────────────────────────────────
  const dailyCounts = useMemo(() => {
    const map = new Map<string, { count: number; _d: Date }>()
    filtered.forEach(e => {
      const k = dateKey(new Date(e.startTime))
      if (!map.has(k)) map.set(k, { count: 0, _d: new Date(e.startTime) })
      map.get(k)!.count++
    })
    return [...map.entries()]
      .sort((a, b) => a[1]._d.getTime() - b[1]._d.getTime())
      .map(([date, v]) => ({ date, count: v.count }))
  }, [filtered])

  // ── suite breakdown ─────────────────────────────────────────────────────────
  const suiteData = useMemo(() => {
    const map = new Map<string, number>()
    filtered.forEach(e => map.set(e.suite, (map.get(e.suite) ?? 0) + 1))
    return [...map.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5)
      .map(([suite, count]) => ({ suite, count }))
  }, [filtered])
  const suiteMax = suiteData[0]?.count ?? 1

  // ── device breakdown ────────────────────────────────────────────────────────
  const deviceData = useMemo(() => {
    const map = new Map<string, number>()
    filtered.forEach(e => { if (e.device) map.set(e.device, (map.get(e.device) ?? 0) + 1) })
    const entries = [...map.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5)
    const total   = entries.reduce((s, [, c]) => s + c, 0) || 1
    return entries.map(([device, count]) => ({ device, count, pct: Math.round((count / total) * 100) }))
  }, [filtered])

  // ── execution time by day ───────────────────────────────────────────────────
  const timeData = useMemo(() => {
    const map = new Map<string, { sum: number; n: number; _d: Date }>()
    filtered.filter(e => e.endTime).forEach(e => {
      const k = dateKey(new Date(e.startTime))
      if (!map.has(k)) map.set(k, { sum: 0, n: 0, _d: new Date(e.startTime) })
      const v = map.get(k)!
      v.sum += (new Date(e.endTime!).getTime() - new Date(e.startTime).getTime()) / 60_000
      v.n++
    })
    return [...map.entries()]
      .sort((a, b) => a[1]._d.getTime() - b[1]._d.getTime())
      .map(([date, v]) => ({ date, mins: Math.round((v.sum / v.n) * 10) / 10 }))
  }, [filtered])

  // ── donut ───────────────────────────────────────────────────────────────────
  const donutData = [
    { name: 'Pasados',  value: stats.cp, color: '#10b981' },
    { name: 'Fallidos', value: stats.cf, color: '#f43f5e' },
    { name: 'Skipped',  value: stats.cs, color: '#f59e0b' },
  ].filter(d => d.value > 0)

  // ── insights ────────────────────────────────────────────────────────────────
  const insights = useMemo(() => {
    const list: { type: 'success' | 'warning' | 'info'; title: string; desc: string }[] = []

    const rateDelta   = pctDelta(stats.cRate,   stats.pRate)
    const failedDelta = pctDelta(stats.cf,      stats.pf)
    const timeDelta   = pctDelta(stats.cAvgMs,  stats.pAvgMs)

    if (rateDelta !== null && rateDelta > 0)
      list.push({ type: 'success', title: 'Mejora en la tasa de éxito',
        desc: `Tu tasa de éxito ha mejorado ${rateDelta}% en los últimos ${days} días.` })

    if (failedDelta !== null && failedDelta > 0)
      list.push({ type: 'warning', title: 'Incremento en fallos',
        desc: `Se detectó un aumento del ${failedDelta}% en tests fallidos esta semana.` })

    if (timeDelta !== null && timeDelta < 0)
      list.push({ type: 'info', title: 'Rendimiento óptimo',
        desc: `El tiempo promedio de ejecución mejoró ${Math.abs(timeDelta)}% este período.` })

    // Ensure 3 cards
    if (!list.find(i => i.type === 'success') && stats.cRate >= 80)
      list.unshift({ type: 'success', title: 'Alta tasa de éxito',
        desc: `${stats.cRate}% de tests pasaron — excelente rendimiento.` })

    if (!list.find(i => i.type === 'warning') && stats.cf > 0)
      list.push({ type: 'warning', title: 'Tests fallidos detectados',
        desc: `${stats.cf} tests fallaron en este período.` })

    if (!list.find(i => i.type === 'info'))
      list.push({ type: 'info', title: 'Sin variaciones previas',
        desc: 'No hay datos del período anterior para comparar tendencias.' })

    return list.slice(0, 3)
  }, [stats, days])

  const tooltipStyle = {
    background: '#1e2130',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: 8,
    fontSize: 12,
  }

  return (
    <div className="p-6 space-y-4">

      {/* ── Header ──────────────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold" style={{ color: 'var(--text-pri)' }}>Métricas</h1>
          <p className="text-[12px] mt-0.5" style={{ color: 'var(--text-dim)' }}>
            Resumen y análisis del rendimiento de tus pruebas
          </p>
        </div>
        <div className="relative">
          <select
            value={period}
            onChange={e => setPeriod(Number(e.target.value))}
            className="appearance-none text-[12px] font-medium pl-9 pr-8 py-2 rounded-xl cursor-pointer outline-none"
            style={{
              background: 'var(--panel-bg)',
              border: '1px solid var(--panel-border)',
              color: 'var(--text-sec)',
            }}
          >
            {PERIODS.map((p, i) => <option key={i} value={i}>{p.label}</option>)}
          </select>
          <Calendar size={13} className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none"
            style={{ color: 'var(--text-dim)' }} />
          <ChevronDown size={11} className="absolute right-2.5 top-1/2 -translate-y-1/2 pointer-events-none"
            style={{ color: 'var(--text-dim)' }} />
        </div>
      </div>

      {loading && (
        <div className="text-center py-4 text-xs" style={{ color: 'var(--text-dim)' }}>Cargando datos...</div>
      )}

      {/* ── Row 1: 6 stat cards ─────────────────────────────────────────────── */}
      <div className="grid grid-cols-6 gap-3">
        <StatCard icon={<BarChart3 size={18} />} iconColor="#6366f1"
          label="Total de Ejecuciones" value={stats.totalExecs}
          sub={<><Delta pct={pctDelta(stats.totalExecs, stats.prevExecs)} />
               <span style={{ color: 'var(--text-dim)' }}>vs. {days} días anteriores</span></>}
        />
        <StatCard icon={<CheckCircle2 size={18} />} iconColor="#10b981"
          label="Tests Pasados" value={stats.cp}
          sub={<span style={{ color: 'var(--text-dim)' }}>
            {stats.ct > 0 ? ((stats.cp / stats.ct) * 100).toFixed(1) : 0}% del total
          </span>}
        />
        <StatCard icon={<XCircle size={18} />} iconColor="#f43f5e"
          label="Tests Fallidos" value={stats.cf}
          sub={<span style={{ color: 'var(--text-dim)' }}>
            {stats.ct > 0 ? ((stats.cf / stats.ct) * 100).toFixed(1) : 0}% del total
          </span>}
        />
        <StatCard icon={<Clock size={18} />} iconColor="#f59e0b"
          label="Tests Skipped" value={stats.cs}
          sub={<span style={{ color: 'var(--text-dim)' }}>
            {stats.ct > 0 ? ((stats.cs / stats.ct) * 100).toFixed(1) : 0}% del total
          </span>}
        />
        <StatCard icon={<Activity size={18} />} iconColor="#8b5cf6"
          label="Tiempo Promedio" value={fmtMs(stats.cAvgMs)}
          sub={<><Delta pct={pctDelta(stats.cAvgMs, stats.pAvgMs)} inverse />
               <span style={{ color: 'var(--text-dim)' }}>vs. {days} días anteriores</span></>}
        />
        <StatCard icon={<Target size={18} />} iconColor="#06b6d4"
          label="Tasa de Éxito" value={`${stats.cRate}%`}
          sub={<><Delta pct={pctDelta(stats.cRate, stats.pRate)} />
               <span style={{ color: 'var(--text-dim)' }}>vs. {days} días anteriores</span></>}
        />
      </div>

      {/* ── Row 2 ────────────────────────────────────────────────────────────── */}
      <div className="grid gap-4" style={{ gridTemplateColumns: '9fr 5fr 5fr' }}>

        {/* Line chart: evolución */}
        <Panel>
          <PHead title="Evolución de Ejecuciones" subtitle={`Últimos ${days} días`}
            action={<MiniSelect value={gran} options={['Diario', 'Semanal']} onChange={setGran} />}
          />
          <div className="px-5 pb-4">
            {dailyData.length === 0 ? <EmptyChart /> : (
              <>
                <div className="flex gap-4 mb-3">
                  {[{ color: '#10b981', label: 'Pasados' }, { color: '#f43f5e', label: 'Fallidos' }, { color: '#f59e0b', label: 'Skipped' }]
                    .map(l => (
                      <span key={l.label} className="flex items-center gap-1.5 text-[11px]" style={{ color: 'var(--text-dim)' }}>
                        <span className="w-2.5 h-2.5 rounded-full" style={{ background: l.color }} />
                        {l.label}
                      </span>
                    ))}
                </div>
                <ResponsiveContainer width="100%" height={195}>
                  <LineChart data={dailyData} margin={{ top: 4, right: 4, bottom: 0, left: -18 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                    <XAxis dataKey="date" tick={{ fontSize: 10, fill: 'var(--text-dim)' }} tickLine={false} axisLine={false} />
                    <YAxis tick={{ fontSize: 10, fill: 'var(--text-dim)' }} tickLine={false} axisLine={false} allowDecimals={false} />
                    <Tooltip contentStyle={tooltipStyle} />
                    <Line type="monotone" dataKey="passed"  stroke="#10b981" strokeWidth={2} dot={false} name="Pasados" />
                    <Line type="monotone" dataKey="failed"  stroke="#f43f5e" strokeWidth={2} dot={false} name="Fallidos" />
                    <Line type="monotone" dataKey="skipped" stroke="#f59e0b" strokeWidth={2} dot={false} name="Skipped" />
                  </LineChart>
                </ResponsiveContainer>
              </>
            )}
          </div>
        </Panel>

        {/* Donut: distribución */}
        <Panel>
          <PHead title="Distribución de Resultados" subtitle={`Últimos ${days} días`} />
          <div className="px-5 pb-5 flex flex-col items-center">
            {donutData.length === 0 ? <EmptyChart /> : (
              <>
                <div className="relative" style={{ width: 160, height: 160 }}>
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={donutData} cx="50%" cy="50%"
                        innerRadius={52} outerRadius={72}
                        startAngle={90} endAngle={-270}
                        paddingAngle={2} dataKey="value"
                      >
                        {donutData.map((d, i) => (
                          <Cell key={i} fill={d.color} stroke="transparent" />
                        ))}
                      </Pie>
                      <Tooltip contentStyle={tooltipStyle} />
                    </PieChart>
                  </ResponsiveContainer>
                  <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                    <span className="text-2xl font-bold" style={{ color: 'var(--text-pri)' }}>{stats.ct}</span>
                    <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>Total</span>
                  </div>
                </div>
                <div className="mt-3 space-y-2 w-full">
                  {donutData.map(d => (
                    <div key={d.name} className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: d.color }} />
                        <span className="text-[12px]" style={{ color: 'var(--text-sec)' }}>{d.name}</span>
                      </div>
                      <span className="text-[12px] font-semibold" style={{ color: 'var(--text-pri)' }}>
                        {d.value}{' '}
                        <span className="font-normal text-[11px]" style={{ color: 'var(--text-dim)' }}>
                          ({stats.ct > 0 ? ((d.value / stats.ct) * 100).toFixed(1) : 0}%)
                        </span>
                      </span>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        </Panel>

        {/* Suite bars */}
        <Panel>
          <PHead title="Ejecuciones por Suite" subtitle={`Últimos ${days} días`}
            action={
              <button className="text-[11px] font-semibold text-indigo-400 hover:text-indigo-300 transition-colors">
                Ver todas
              </button>
            }
          />
          <div className="px-5 pb-5 space-y-3">
            {suiteData.length === 0 ? (
              <div className="py-8 text-center text-xs" style={{ color: 'var(--text-dim)' }}>Sin datos</div>
            ) : suiteData.map(s => (
              <div key={s.suite}>
                <div className="flex justify-between items-center mb-1">
                  <span className="text-[12px] truncate" style={{ color: 'var(--text-sec)' }}>{s.suite}</span>
                  <span className="text-[12px] font-semibold ml-2 flex-shrink-0" style={{ color: 'var(--text-pri)' }}>{s.count}</span>
                </div>
                <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.06)' }}>
                  <div
                    className="h-full rounded-full transition-all duration-700"
                    style={{
                      width: `${(s.count / suiteMax) * 100}%`,
                      background: 'linear-gradient(90deg, #6366f1, #8b5cf6)',
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        </Panel>
      </div>

      {/* ── Row 3 ────────────────────────────────────────────────────────────── */}
      <div className="grid grid-cols-3 gap-4">

        {/* Bar chart: ejecuciones por día */}
        <Panel>
          <PHead title="Ejecuciones por Día" subtitle={`Últimos ${days} días`}
            action={<MiniSelect value={gran} options={['Diario', 'Semanal']} onChange={setGran} />}
          />
          <div className="px-4 pb-4">
            {dailyCounts.length === 0 ? <EmptyChart /> : (
              <ResponsiveContainer width="100%" height={180}>
                <BarChart data={dailyCounts} margin={{ top: 4, right: 4, bottom: 0, left: -20 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" vertical={false} />
                  <XAxis dataKey="date" tick={{ fontSize: 10, fill: 'var(--text-dim)' }} tickLine={false} axisLine={false} />
                  <YAxis tick={{ fontSize: 10, fill: 'var(--text-dim)' }} tickLine={false} axisLine={false} allowDecimals={false} />
                  <Tooltip contentStyle={tooltipStyle} cursor={{ fill: 'rgba(99,102,241,0.08)' }} />
                  <Bar dataKey="count" name="Ejecuciones" fill={BAR_COLOR} radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </div>
        </Panel>

        {/* Devices */}
        <Panel>
          <PHead title="Dispositivos más utilizados" subtitle={`Últimos ${days} días`} />
          <div className="px-5 pb-5 space-y-3">
            {deviceData.length === 0 ? (
              <div className="py-8 text-center text-xs" style={{ color: 'var(--text-dim)' }}>Sin datos de dispositivos</div>
            ) : deviceData.map((d, i) => (
              <div key={d.device} className="flex items-center gap-3">
                <div
                  className="w-7 h-7 rounded-lg flex items-center justify-center flex-shrink-0"
                  style={{ background: `rgba(99,102,241,${Math.max(0.05, 0.18 - i * 0.03)})` }}
                >
                  <Smartphone size={13} style={{ color: '#818cf8' }} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex justify-between items-center mb-1">
                    <span className="text-[12px] truncate" style={{ color: 'var(--text-sec)' }}>{d.device}</span>
                    <span className="text-[12px] font-semibold ml-2 flex-shrink-0" style={{ color: 'var(--text-pri)' }}>{d.pct}%</span>
                  </div>
                  <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.06)' }}>
                    <div
                      className="h-full rounded-full transition-all duration-700"
                      style={{ width: `${d.pct}%`, background: 'linear-gradient(90deg, #6366f1, #818cf8)' }}
                    />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </Panel>

        {/* Execution time */}
        <Panel>
          <PHead title="Tiempo de Ejecución" subtitle={`Últimos ${days} días`}
            action={<MiniSelect value={gran} options={['Diario', 'Semanal']} onChange={setGran} />}
          />
          <div className="px-5 pb-1">
            <div className="text-[28px] font-bold leading-none" style={{ color: 'var(--text-pri)' }}>
              {fmtMs(stats.cAvgMs)}
            </div>
            <div className="text-[11px] mt-0.5 mb-3" style={{ color: 'var(--text-dim)' }}>Promedio general</div>
          </div>
          <div className="px-4 pb-4">
            {timeData.length === 0 ? <EmptyChart small /> : (
              <ResponsiveContainer width="100%" height={130}>
                <AreaChart data={timeData} margin={{ top: 4, right: 4, bottom: 0, left: -20 }}>
                  <defs>
                    <linearGradient id="timeGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%"  stopColor={AREA_COLOR} stopOpacity={0.35} />
                      <stop offset="95%" stopColor={AREA_COLOR} stopOpacity={0}    />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.05)" />
                  <XAxis dataKey="date" tick={{ fontSize: 10, fill: 'var(--text-dim)' }} tickLine={false} axisLine={false} />
                  <YAxis tick={{ fontSize: 10, fill: 'var(--text-dim)' }} tickLine={false} axisLine={false} unit="m" />
                  <Tooltip contentStyle={tooltipStyle} formatter={(v) => [`${v} min`, 'Tiempo']} />
                  <Area type="monotone" dataKey="mins" stroke={AREA_COLOR} strokeWidth={2} fill="url(#timeGrad)" dot={false} />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </div>
        </Panel>
      </div>

      {/* ── Row 4 ────────────────────────────────────────────────────────────── */}
      <div className="grid gap-4" style={{ gridTemplateColumns: '5fr 4fr' }}>

        {/* Insights Inteligentes */}
        <Panel>
          <PHead title="Insights Inteligentes" subtitle="Análisis automático basado en tus datos" />
          <div className="px-5 pb-5 grid grid-cols-3 gap-3">
            {insights.map((ins, i) => (
              <InsightCard key={i} type={ins.type} title={ins.title} desc={ins.desc} />
            ))}
          </div>
        </Panel>

        {/* Tendencias */}
        <Panel>
          <PHead title="Tendencias" subtitle="Comparación con períodos anteriores" />
          <div className="px-5 pb-5 grid grid-cols-2 gap-6">
            <TrendItem label="Ejecuciones"    pct={pctDelta(stats.totalExecs, stats.prevExecs)} />
            <TrendItem label="Tasa de Éxito"  pct={pctDelta(stats.cRate, stats.pRate)} />
            <TrendItem label="Tiempo Promedio" pct={pctDelta(stats.cAvgMs, stats.pAvgMs)} inverse />
            <TrendItem label="Tests Fallidos"  pct={pctDelta(stats.cf, stats.pf)} inverse />
          </div>
        </Panel>
      </div>

    </div>
  )
}
