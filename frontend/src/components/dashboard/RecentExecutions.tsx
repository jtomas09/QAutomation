import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { ExternalLink, Play } from 'lucide-react'
import type { ExecutionSummary, ExecutionStatus } from '../../types'
import { getExecutions } from '../../api'

const STATUS_LABEL: Record<ExecutionStatus, string> = {
  PENDING:   'Pendiente',
  QUEUED:    'En Cola',
  RUNNING:   'Ejecutando',
  PASSED:    'Passed',
  FAILED:    'Failed',
  SKIPPED:   'Skipped',
  COMPLETED: 'Completado',
  ABORTED:   'Abortado',
}
const STATUS_COLOR: Record<ExecutionStatus, string> = {
  PENDING:   '#facc15',
  QUEUED:    '#fb923c',
  RUNNING:   '#22d3ee',
  PASSED:    '#4ade80',
  FAILED:    '#f87171',
  SKIPPED:   '#eab308',
  COMPLETED: '#10b981',
  ABORTED:   '#94a3b8',
}
const STATUS_BG: Record<ExecutionStatus, string> = {
  PENDING:   'rgba(250,204,21,0.10)',
  QUEUED:    'rgba(251,146,60,0.12)',
  RUNNING:   'rgba(34,211,238,0.12)',
  PASSED:    'rgba(74,222,128,0.12)',
  FAILED:    'rgba(248,113,113,0.12)',
  SKIPPED:   'rgba(234,179,8,0.10)',
  COMPLETED: 'rgba(16,185,129,0.12)',
  ABORTED:   'rgba(148,163,184,0.10)',
}

function fmt(iso: string) {
  return new Date(iso).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
}
function dur(start: string, end: string | null) {
  if (!end) return '—'
  const ms = new Date(end).getTime() - new Date(start).getTime()
  if (ms < 60000) return `${Math.round(ms / 1000)}s`
  return `${Math.round(ms / 60000)}m ${Math.round((ms % 60000) / 1000)}s`
}

const MOCK: ExecutionSummary[] = [
  { executionId: 'RUN-1247', suite: 'Smoke Tests',        env: 'QA',   device: 'Galaxy A56 5G',  country: 'mexico',    status: 'PASSED', startTime: new Date(Date.now()-165000).toISOString(),    endTime: new Date(Date.now()-300).toISOString(),             passed: 12, failed: 0, skipped: 0, total: 12, allureUrl: null },
  { executionId: 'RUN-1246', suite: 'Flujo Completo',     env: 'QA',   device: 'Pixel 8 Pro',    country: 'mexico',    status: 'FAILED',    startTime: new Date(Date.now()-3900000).toISOString(),  endTime: new Date(Date.now()-3900000+192000).toISOString(),  passed: 8,  failed: 3, skipped: 0, total: 11, allureUrl: null },
  { executionId: 'RUN-1245', suite: 'Carrito de Compras', env: 'PROD', device: 'iPhone 15',      country: 'argentina', status: 'PASSED', startTime: new Date(Date.now()-7200000).toISOString(),  endTime: new Date(Date.now()-7200000+118000).toISOString(),  passed: 10, failed: 0, skipped: 0, total: 10, allureUrl: null },
  { executionId: 'RUN-1244', suite: 'Checkout',           env: 'STG',  device: 'Galaxy S24',     country: 'chile',     status: 'ABORTED',   startTime: new Date(Date.now()-86400000).toISOString(), endTime: new Date(Date.now()-86400000+45000).toISOString(),  passed: 0,  failed: 0, skipped: 0, total: 0,  allureUrl: null },
  { executionId: 'RUN-1243', suite: 'Alimentos',          env: 'QA',   device: 'Redmi Note 13',  country: 'mexico',    status: 'PASSED', startTime: new Date(Date.now()-90000000).toISOString(), endTime: new Date(Date.now()-90000000+150000).toISOString(), passed: 9,  failed: 0, skipped: 1, total: 10, allureUrl: null },
]

interface Props { onViewAll?: () => void }

export default function RecentExecutions({ onViewAll }: Props) {
  const [rows, setRows] = useState<ExecutionSummary[]>(MOCK)

  useEffect(() => {
    const load = () =>
      getExecutions()
        .then(data => { if (data.length > 0) setRows(data.slice(0, 5)) })
        .catch(() => {})
    load()
    const id = setInterval(load, 10_000)
    return () => clearInterval(id)
  }, [])

  return (
    <div
      className="flex flex-col h-full overflow-hidden rounded-2xl"
      style={{
        background: 'linear-gradient(135deg, rgba(255,255,255,0.04) 0%, rgba(255,255,255,0.02) 100%)',
        border: '1px solid rgba(255,255,255,0.08)',
        boxShadow: '0 4px 24px rgba(0,0,0,0.4)',
      }}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-5 py-4 flex-shrink-0"
        style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}
      >
        <div>
          <div className="text-sm font-bold text-slate-100">Ejecuciones Recientes</div>
          <div className="text-xs text-slate-500 mt-0.5">Últimas 5 ejecuciones</div>
        </div>
        <button
          onClick={onViewAll}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-[11px] font-semibold text-indigo-400 hover:text-indigo-300 transition-colors"
          style={{ background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.2)' }}
        >
          <ExternalLink size={11} />
          Ver todas
        </button>
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto min-h-0">
        <table className="w-full text-xs border-collapse">
          <thead>
            <tr style={{ borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
              {['EJECUCIÓN','SUITE','DISPOSITIVO','ESTADO','INICIO','DURACIÓN',''].map(h => (
                <th
                  key={h}
                  className="px-4 py-3 text-left font-bold tracking-widest text-[10px] uppercase"
                  style={{ color: '#475569' }}
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => {
              const color  = STATUS_COLOR[row.status]
              const bg     = STATUS_BG[row.status]
              const isRun  = row.status === 'RUNNING'

              return (
                <motion.tr
                  key={row.executionId}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.05 }}
                  className="group transition-colors"
                  style={{
                    borderBottom: '1px solid rgba(255,255,255,0.04)',
                    background: isRun ? 'rgba(99,102,241,0.04)' : 'transparent',
                  }}
                  onMouseEnter={e => (e.currentTarget.style.background = 'rgba(255,255,255,0.03)')}
                  onMouseLeave={e => (e.currentTarget.style.background = isRun ? 'rgba(99,102,241,0.04)' : 'transparent')}
                >
                  <td className="px-4 py-3 font-mono font-bold text-indigo-400">{row.executionId}</td>
                  <td className="px-4 py-3 text-slate-200 font-medium">{row.suite}</td>
                  <td className="px-4 py-3 text-slate-500">{row.device}</td>
                  <td className="px-4 py-3">
                    <span
                      className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider"
                      style={{ color, background: bg }}
                    >
                      {isRun && (
                        <motion.span
                          className="w-1.5 h-1.5 rounded-full"
                          style={{ background: color }}
                          animate={{ opacity: [1, 0.3, 1] }}
                          transition={{ duration: 1, repeat: Infinity }}
                        />
                      )}
                      {STATUS_LABEL[row.status]}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-500 tabular-nums">{fmt(row.startTime)}</td>
                  <td className="px-4 py-3 text-slate-500 tabular-nums">{dur(row.startTime, row.endTime)}</td>
                  <td className="px-4 py-3">
                    <button
                      className="w-6 h-6 rounded-lg flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                      style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8' }}
                    >
                      <Play size={10} />
                    </button>
                  </td>
                </motion.tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
