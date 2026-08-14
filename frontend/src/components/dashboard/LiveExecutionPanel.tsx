/**
 * LiveExecutionPanel — real-time execution monitor for the Dashboard.
 *
 * Renders a compact card for each active execution (queued / initializing / running).
 * Listens to qa:exec:* window events and refreshes automatically.
 * Collapses entirely when there are no active executions — dashboard layout unchanged.
 */

import React, { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Play, Square, ChevronDown, ChevronRight, CheckCircle2,
  XCircle, Clock, Loader2, Smartphone, AlertCircle,
} from 'lucide-react'
import { executionTrackingService, ACTIVE_STATUSES, DONE_STATUSES } from '../../services/ExecutionTrackingService'
import { cleanBonjourHostname } from '../../utils/displayNames'
import type { ExecutionRecord, ExecStatus, CaseRun } from '../../services/ExecutionTrackingService'
import { appIconResolver } from '../../services/ApplicationIconResolver'

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmtDuration(ms?: number): string {
  if (!ms) return '—'
  const s = Math.floor(ms / 1000)
  const m = Math.floor(s / 60)
  const h = Math.floor(m / 60)
  if (h > 0) return `${h}h ${m % 60}m`
  if (m > 0) return `${m}m ${s % 60}s`
  return `${s}s`
}

function fmtTime(iso?: string): string {
  if (!iso) return ''
  try { return new Date(iso).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) }
  catch { return '' }
}

// ── Status config ─────────────────────────────────────────────────────────────

const STATUS_CFG: Record<ExecStatus, { label: string; color: string; bg: string; pulse: boolean }> = {
  queued:       { label: 'En cola',     color: '#94a3b8', bg: 'rgba(148,163,184,0.12)', pulse: false },
  initializing: { label: 'Iniciando',   color: '#f59e0b', bg: 'rgba(245,158,11,0.12)',  pulse: true  },
  running:      { label: 'Ejecutando',  color: '#34d399', bg: 'rgba(52,211,153,0.12)',  pulse: true  },
  passed:       { label: 'Pasado',      color: '#4ade80', bg: 'rgba(74,222,128,0.12)',  pulse: false },
  failed:       { label: 'Fallado',     color: '#f87171', bg: 'rgba(248,113,113,0.12)', pulse: false },
  skipped:      { label: 'Omitido',     color: '#94a3b8', bg: 'rgba(148,163,184,0.12)', pulse: false },
  cancelled:    { label: 'Cancelado',   color: '#f59e0b', bg: 'rgba(245,158,11,0.12)',  pulse: false },
  error:        { label: 'Error',       color: '#fb923c', bg: 'rgba(251,146,60,0.12)',  pulse: false },
}

const CASE_STATUS_CFG: Record<CaseRun['status'], { color: string; icon: React.ElementType | null }> = {
  pending:      { color: '#475569', icon: null        },
  queued:       { color: '#94a3b8', icon: Clock       },
  initializing: { color: '#f59e0b', icon: Loader2     },
  running:      { color: '#60a5fa', icon: Play        },
  passed:       { color: '#4ade80', icon: CheckCircle2 },
  failed:       { color: '#f87171', icon: XCircle     },
  skipped:      { color: '#94a3b8', icon: Clock       },
  cancelled:    { color: '#f59e0b', icon: Square      },
  error:        { color: '#fb923c', icon: AlertCircle  },
}

// ── AppIcon — inline component ────────────────────────────────────────────────

function AppIcon({ pkg, appName, size = 28 }: { pkg: string; appName: string; size?: number }) {
  const app = appIconResolver.resolveApplication(pkg, '', appName)
  const [failed, setFailed] = useState(false)
  const r = Math.min(size * 0.28, 8)

  if (app.iconUrl && !failed) {
    return (
      <img
        src={app.iconUrl}
        alt={app.displayName}
        onError={() => setFailed(true)}
        style={{ width: size, height: size, borderRadius: r, objectFit: 'cover', flexShrink: 0 }}
      />
    )
  }
  return (
    <div style={{
      width: size, height: size, borderRadius: r, flexShrink: 0,
      background: app.color,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontSize: size * 0.44, color: '#fff', fontWeight: 800,
    }}>
      {app.fallbackEmoji || app.displayName.charAt(0)}
    </div>
  )
}

// ── Single execution card ─────────────────────────────────────────────────────

function ExecCard({ exec }: { exec: ExecutionRecord }) {
  const [expanded, setExpanded] = useState(true)
  const [showLog,  setShowLog]  = useState(false)

  const cfg      = STATUS_CFG[exec.status]
  const isDone   = (DONE_STATUSES as string[]).includes(exec.status)
  const progress = exec.totalCases > 0 ? exec.completedCases / exec.totalCases : 0

  return (
    <div style={{
      background: '#0f172a', border: `1px solid ${isDone ? '#1e293b' : cfg.color + '33'}`,
      borderRadius: 10, overflow: 'hidden',
      transition: 'border-color 0.3s',
    }}>
      {/* Header */}
      <div
        style={{
          display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px',
          cursor: 'pointer', background: isDone ? 'transparent' : `${cfg.bg}`,
        }}
        onClick={() => setExpanded(p => !p)}
      >
        {/* App icon */}
        <AppIcon pkg={exec.appPackage} appName={exec.appName} size={26} />

        {/* Suite name */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: '#f1f5f9', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {exec.suiteName}
            </span>
            <span style={{
              fontSize: 9, fontWeight: 700, color: cfg.color,
              background: cfg.bg, border: `1px solid ${cfg.color}44`,
              borderRadius: 4, padding: '1px 5px',
              display: 'flex', alignItems: 'center', gap: 3,
            }}>
              {cfg.pulse && <Loader2 size={8} style={{ animation: 'spin 1s linear infinite' }} />}
              {cfg.label}
            </span>
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 2 }}>
            <span style={{ fontSize: 10, color: '#64748b' }}>
              {exec.completedCases}/{exec.totalCases} casos
            </span>
            {exec.device && (
              <>
                <span style={{ fontSize: 10, color: '#334155' }}>·</span>
                <span style={{ fontSize: 10, color: '#64748b' }}>
                  <Smartphone size={9} style={{ verticalAlign: 'middle', marginRight: 2 }} />{cleanBonjourHostname(exec.device)}
                </span>
              </>
            )}
            <span style={{ fontSize: 10, color: '#334155' }}>·</span>
            <span style={{ fontSize: 10, color: '#64748b' }}>{exec.environment.toUpperCase()}</span>
            {exec.startedAt && (
              <>
                <span style={{ fontSize: 10, color: '#334155' }}>·</span>
                <span style={{ fontSize: 10, color: '#475569' }}>{fmtTime(exec.startedAt)}</span>
              </>
            )}
            {isDone && exec.durationMs && (
              <>
                <span style={{ fontSize: 10, color: '#334155' }}>·</span>
                <span style={{ fontSize: 10, color: '#64748b' }}>{fmtDuration(exec.durationMs)}</span>
              </>
            )}
          </div>
        </div>

        {/* Progress bar */}
        <div style={{ width: 80, flexShrink: 0 }}>
          <div style={{ height: 4, borderRadius: 2, background: '#1e293b', overflow: 'hidden' }}>
            <motion.div
              animate={{ width: `${progress * 100}%` }}
              transition={{ duration: 0.4 }}
              style={{ height: '100%', borderRadius: 2, background: cfg.color }}
            />
          </div>
          <div style={{ fontSize: 9, color: '#475569', textAlign: 'right', marginTop: 2 }}>
            {Math.round(progress * 100)}%
          </div>
        </div>

        <div style={{ color: '#475569', flexShrink: 0 }}>
          {expanded ? <ChevronDown size={13} /> : <ChevronRight size={13} />}
        </div>
      </div>

      {/* Expanded detail */}
      <AnimatePresence>
        {expanded && (
          <motion.div
            key="detail"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            style={{ overflow: 'hidden', borderTop: '1px solid #1e293b' }}
          >
            <div style={{ display: 'flex', gap: 0 }}>
              {/* Cases */}
              <div style={{ flex: 1, padding: '8px 14px', borderRight: '1px solid #1e293b', display: 'flex', flexDirection: 'column', gap: 3 }}>
                <div style={{ fontSize: 9, fontWeight: 700, color: '#475569', textTransform: 'uppercase', letterSpacing: 0.5, marginBottom: 4 }}>
                  Casos de prueba
                </div>
                {exec.cases.map(c => {
                  const ccfg = CASE_STATUS_CFG[c.status]
                  return (
                    <div key={c.caseId} style={{
                      display: 'flex', alignItems: 'center', gap: 7, padding: '4px 8px',
                      borderRadius: 6, background: 'rgba(255,255,255,0.02)',
                      border: `1px solid ${c.status === 'running' ? ccfg.color + '44' : 'transparent'}`,
                    }}>
                      <div style={{
                        width: 16, height: 16, borderRadius: 4, flexShrink: 0,
                        background: `${ccfg.color}18`,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                      }}>
                        {ccfg.icon
                          ? <ccfg.icon
                              size={9}
                              color={ccfg.color}
                              style={c.status === 'running' ? { animation: 'spin 1s linear infinite' } : undefined}
                            />
                          : <div style={{ width: 5, height: 5, borderRadius: '50%', background: ccfg.color }} />
                        }
                      </div>
                      <span style={{
                        flex: 1, fontSize: 11, color: c.status === 'pending' ? '#334155' : '#94a3b8',
                        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                        fontWeight: c.status === 'running' ? 700 : 400,
                      }}>
                        {c.caseName}
                      </span>
                      {c.durationMs != null && (
                        <span style={{ fontSize: 9, color: '#475569', flexShrink: 0 }}>{fmtDuration(c.durationMs)}</span>
                      )}
                      {c.error && (
                        <span
                          title={c.error}
                          style={{ fontSize: 9, color: '#f87171', maxWidth: 80, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                        >
                          {c.error}
                        </span>
                      )}
                    </div>
                  )
                })}
              </div>

              {/* Activity log */}
              <div style={{ width: 260, flexShrink: 0, padding: '8px 12px', display: 'flex', flexDirection: 'column' }}>
                <div style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  marginBottom: 4,
                }}>
                  <span style={{ fontSize: 9, fontWeight: 700, color: '#475569', textTransform: 'uppercase', letterSpacing: 0.5 }}>
                    Actividad
                  </span>
                  <button
                    onClick={e => { e.stopPropagation(); setShowLog(p => !p) }}
                    style={{ fontSize: 9, color: '#475569', background: 'none', border: 'none', cursor: 'pointer' }}
                  >
                    {showLog ? 'Menos' : `+${exec.activity.length} entradas`}
                  </button>
                </div>
                <div style={{ flex: 1, overflow: 'hidden', display: 'flex', flexDirection: 'column', gap: 2 }}>
                  {(showLog ? exec.activity : exec.activity.slice(-6)).map((a, i) => {
                    const color = a.level === 'ok' ? '#4ade80' : a.level === 'error' ? '#f87171' : a.level === 'warn' ? '#f59e0b' : '#64748b'
                    return (
                      <div key={i} style={{ display: 'flex', gap: 6, alignItems: 'flex-start' }}>
                        <span style={{ fontSize: 9, color: '#334155', flexShrink: 0, fontFamily: 'monospace' }}>
                          {fmtTime(a.ts)}
                        </span>
                        <span style={{ fontSize: 10, color, lineHeight: 1.3, wordBreak: 'break-word' }}>
                          {a.msg}
                        </span>
                      </div>
                    )
                  })}
                </div>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

// ── Panel ─────────────────────────────────────────────────────────────────────

export default function LiveExecutionPanel() {
  const [executions, setExecutions] = useState<ExecutionRecord[]>([])
  const [showDone,   setShowDone]   = useState(false)

  const refresh = useCallback(() => {
    const all    = executionTrackingService.getHistory(20)
    setExecutions(all)
  }, [])

  useEffect(() => {
    refresh()
    const events = ['qa:exec:created', 'qa:exec:updated', 'qa:exec:finished']
    events.forEach(e => window.addEventListener(e, refresh))
    return () => events.forEach(e => window.removeEventListener(e, refresh))
  }, [refresh])

  const active = executions.filter(r => (ACTIVE_STATUSES as string[]).includes(r.status))
  const recent = executions.filter(r => (DONE_STATUSES   as string[]).includes(r.status)).slice(0, 5)

  if (active.length === 0 && recent.length === 0) return null

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {/* Active executions */}
      {active.length > 0 && (
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <div style={{
              width: 6, height: 6, borderRadius: '50%', background: '#34d399',
              animation: 'pulse 1.5s ease-in-out infinite',
            }} />
            <span style={{ fontSize: 11, fontWeight: 700, color: '#64748b', textTransform: 'uppercase', letterSpacing: 0.5 }}>
              Ejecuciones en curso
            </span>
            <span style={{
              fontSize: 9, fontWeight: 700, color: '#34d399',
              background: 'rgba(52,211,153,0.12)', border: '1px solid rgba(52,211,153,0.25)',
              borderRadius: 4, padding: '1px 6px',
            }}>
              {active.length}
            </span>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {active.map(r => <ExecCard key={r.id} exec={r} />)}
          </div>
        </div>
      )}

      {/* Recently finished */}
      {recent.length > 0 && (
        <div>
          <button
            onClick={() => setShowDone(p => !p)}
            style={{
              display: 'flex', alignItems: 'center', gap: 6, background: 'none', border: 'none',
              cursor: 'pointer', padding: '4px 0', marginBottom: showDone ? 6 : 0,
            }}
          >
            <span style={{ fontSize: 11, fontWeight: 700, color: '#475569', textTransform: 'uppercase', letterSpacing: 0.5 }}>
              Recientes
            </span>
            {showDone ? <ChevronDown size={12} color="#475569" /> : <ChevronRight size={12} color="#475569" />}
          </button>
          {showDone && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {recent.map(r => <ExecCard key={r.id} exec={r} />)}
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// Inject CSS keyframes once
if (typeof document !== 'undefined') {
  const id = 'qa-live-exec-styles'
  if (!document.getElementById(id)) {
    const s = document.createElement('style')
    s.id = id
    s.textContent = `
      @keyframes spin   { to { transform: rotate(360deg) } }
      @keyframes pulse  { 0%,100% { opacity:1 } 50% { opacity:0.4 } }
    `
    document.head.appendChild(s)
  }
}
