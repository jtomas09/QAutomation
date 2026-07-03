import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { ExternalLink, Play } from 'lucide-react'
import type { ExecutionSummary, ExecutionStatus } from '../../types'
import { getExecutions } from '../../api'
import { PlatformBadge } from '../PlatformIcon'

const STATUS_LABEL: Record<ExecutionStatus, string> = {
  PENDING:    'Pendiente',
  QUEUED:     'En Cola',
  RUNNING:    'Ejecutando',
  FINALIZING: 'Finalizando',
  ABORTING:   'Abortando',
  PASSED:     'Passed',
  FAILED:     'Failed',
  SKIPPED:    'Skipped',
  COMPLETED:  'Completado',
  ABORTED:    'Abortado',
}
const STATUS_COLOR: Record<ExecutionStatus, string> = {
  PENDING:    '#facc15',
  QUEUED:     '#fb923c',
  RUNNING:    '#22d3ee',
  FINALIZING: '#a78bfa',
  ABORTING:   '#f97316',
  PASSED:     '#4ade80',
  FAILED:     '#f87171',
  SKIPPED:    '#eab308',
  COMPLETED:  '#10b981',
  ABORTED:    '#94a3b8',
}
const STATUS_BG: Record<ExecutionStatus, string> = {
  PENDING:    'rgba(250,204,21,0.10)',
  QUEUED:     'rgba(251,146,60,0.12)',
  RUNNING:    'rgba(34,211,238,0.12)',
  FINALIZING: 'rgba(167,139,250,0.12)',
  ABORTING:   'rgba(249,115,22,0.12)',
  PASSED:     'rgba(74,222,128,0.12)',
  FAILED:     'rgba(248,113,113,0.12)',
  SKIPPED:    'rgba(234,179,8,0.10)',
  COMPLETED:  'rgba(16,185,129,0.12)',
  ABORTED:    'rgba(148,163,184,0.10)',
}

const IOS_UDID = /^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{16}$/

function inferPlatform(device: string): string {
  if (IOS_UDID.test(device.trim())) return 'IOS'
  const d = device.toUpperCase()
  if (d.includes('IPHONE') || d.includes('IPAD')) return 'IOS'
  if (d.includes('PIXEL') || d.includes('GALAXY') || d.includes('REDMI') || d.includes('SM-')) return 'ANDROID'
  return 'ANDROID'
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

interface Props { onViewAll?: () => void }

export default function RecentExecutions({ onViewAll }: Props) {
  const [rows, setRows] = useState<ExecutionSummary[]>([])

  useEffect(() => {
    const load = () =>
      getExecutions()
        .then(data => setRows(data.slice(0, 5)))
        .catch(() => {})
    load()
    const id = setInterval(load, 10_000)
    return () => clearInterval(id)
  }, [])

  return (
    <div
      className="flex flex-col h-full overflow-hidden rounded-2xl"
      style={{
        background: 'var(--panel-bg)',
        border: '1px solid var(--panel-border)',
        boxShadow: 'var(--panel-shadow)',
      }}
    >
      {/* Header */}
      <div
        className="flex items-center justify-between px-5 py-4 flex-shrink-0"
        style={{ borderBottom: '1px solid var(--panel-divide)' }}
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
            <tr style={{ borderBottom: '1px solid var(--panel-divide)' }}>
              {['EJECUCIÓN','SUITE','DISPOSITIVO','PLATAFORMA','ESTADO','INICIO','DURACIÓN',''].map(h => (
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
            {rows.length === 0 && (
              <tr>
                <td colSpan={8} className="px-4 py-10 text-center text-xs text-slate-600">
                  Sin ejecuciones aún
                </td>
              </tr>
            )}
            {rows.map((row, i) => {
              const color  = STATUS_COLOR[row.status]
              const bg     = STATUS_BG[row.status]
              const isRun  = row.status === 'RUNNING' || row.status === 'FINALIZING' || row.status === 'ABORTING'

              return (
                <motion.tr
                  key={row.executionId}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.05 }}
                  className="group transition-colors"
                  style={{
                    borderBottom: '1px solid var(--panel-divide)',
                    background: isRun ? 'rgba(99,102,241,0.04)' : 'transparent',
                  }}
                  onMouseEnter={e => (e.currentTarget.style.background = 'rgba(255,255,255,0.03)')}
                  onMouseLeave={e => (e.currentTarget.style.background = isRun ? 'rgba(99,102,241,0.04)' : 'transparent')}
                >
                  <td className="px-4 py-3 font-mono font-bold text-indigo-400">{row.executionId}</td>
                  <td className="px-4 py-3 text-slate-200 font-medium">{row.suite}</td>
                  <td className="px-4 py-3 text-slate-500">{row.device}</td>
                  <td className="px-4 py-3">
                    <PlatformBadge platform={inferPlatform(row.device ?? '')} size="xs" />
                  </td>
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
