import React, { useState, useEffect, useCallback, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { suiteService, resolveAppIcon } from '../services/SuiteService'
import type { Suite } from '../services/SuiteService'
import { useConfirmation } from '../hooks/useConfirmation'
import {
  Layers3, Trash2, Play, PencilLine, MoreHorizontal,
  Search, X, ChevronLeft, ChevronRight,
  CheckCircle2, XCircle, Clock, Smartphone,
  LayoutList, LayoutGrid, BarChart3, ListChecks,
  Code2, FileCode2, AlertCircle,
} from 'lucide-react'

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmtDate(iso: string): string {
  if (!iso) return '—'
  try {
    const d    = new Date(iso)
    const now  = new Date()
    const yday = new Date(now); yday.setDate(now.getDate() - 1)
    const time = d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
    if (d.toDateString() === now.toDateString())  return `Hoy, ${time}`
    if (d.toDateString() === yday.toDateString()) return `Ayer, ${time}`
    return d.toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' }) + ` ${time}`
  } catch { return iso }
}

function fmtShort(iso: string): string {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleDateString('es-MX', { day: '2-digit', month: '2-digit', year: 'numeric' })
  } catch { return iso }
}

function deriveTestType(s: Suite): { label: string; color: string; bg: string } {
  const t = `${s.name} ${s.description}`.toLowerCase()
  if (t.includes('smoke'))                               return { label:'SMOKE', color:'#fb923c', bg:'rgba(249,115,22,0.15)' }
  if (t.includes('e2e') || t.includes('flujo'))          return { label:'E2E',   color:'#818cf8', bg:'rgba(99,102,241,0.15)' }
  if (t.includes('reg') || t.includes('regres'))         return { label:'REG',   color:'#c084fc', bg:'rgba(192,132,252,0.15)' }
  if (s.mode === 'caso')                                 return { label:'CASO',  color:'#34d399', bg:'rgba(52,211,153,0.15)' }
  return                                                        { label:'SUITE', color:'#94a3b8', bg:'rgba(148,163,184,0.1)'  }
}

function deriveAmbiente(s: Suite): { label: string; color: string } {
  const t = `${s.name} ${s.description}`.toLowerCase()
  if (t.includes('prod'))                          return { label:'Producción', color:'#4ade80' }
  if (t.includes('staging') || t.includes('stage'))return { label:'Staging',   color:'#f59e0b' }
  return                                                  { label:'QA',         color:'#60a5fa' }
}

function deriveTags(s: Suite): string[] {
  const t = `${s.name} ${s.description}`.toLowerCase()
  const r: string[] = []
  if (t.includes('login'))   r.push('login')
  if (t.includes('compra'))  r.push('compra')
  if (t.includes('menú') || t.includes('menu')) r.push('menú')
  if (t.includes('perfil'))  r.push('perfil')
  if (t.includes('registro'))r.push('registro')
  r.push(s.mode)
  return [...new Set(r)].slice(0, 4)
}

function platformLabel(p: string): { text: string; sub: string; color: string } {
  if (p?.toLowerCase() === 'ios')     return { text:'iOS',     sub:'iOS 16+',  color:'#60a5fa' }
  if (p?.toLowerCase() === 'android') return { text:'Android', sub:'API 13+',  color:'#4ade80' }
  return                                     { text:'—',        sub:'',         color:'#475569' }
}

// ── Stat card ─────────────────────────────────────────────────────────────────

function StatCard({
  icon, iconBg, iconColor, label, value, sub, subColor,
}: {
  icon: React.ReactNode; iconBg: string; iconColor: string
  label: string; value: string; sub: string; subColor?: string
}) {
  return (
    <div style={{
      flex: '1 1 155px', minWidth: 0,
      background: 'rgba(255,255,255,0.03)',
      border: '1px solid rgba(255,255,255,0.07)',
      borderRadius: 12, padding: '14px 16px',
      display: 'flex', alignItems: 'flex-start', gap: 12,
    }}>
      <div style={{
        width: 38, height: 38, borderRadius: 10, flexShrink: 0,
        background: iconBg, border: `1px solid ${iconColor}33`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <span style={{ color: iconColor }}>{icon}</span>
      </div>
      <div style={{ minWidth: 0 }}>
        <p style={{ margin: 0, fontSize: 9, color: '#475569', fontWeight: 700, textTransform: 'uppercase', letterSpacing: 0.5 }}>{label}</p>
        <p style={{ margin: '3px 0 0', fontSize: 20, fontWeight: 800, color: '#e2e8f0', lineHeight: 1.1 }}>{value}</p>
        <p style={{ margin: '3px 0 0', fontSize: 10, color: subColor ?? '#475569' }}>{sub}</p>
      </div>
    </div>
  )
}

// ── Small helpers ─────────────────────────────────────────────────────────────

function FilterSelect({
  label, value, onChange, options,
}: {
  label: string; value: string; onChange: (v: string) => void
  options: { value: string; label: string }[]
}) {
  return (
    <div style={{
      display: 'flex', alignItems: 'center', gap: 0,
      background: 'rgba(255,255,255,0.05)',
      border: '1px solid rgba(255,255,255,0.09)', borderRadius: 8,
      overflow: 'hidden',
    }}>
      <span style={{
        padding: '5px 8px 5px 10px', fontSize: 10, color: '#475569', fontWeight: 600,
        borderRight: '1px solid rgba(255,255,255,0.07)', whiteSpace: 'nowrap',
      }}>
        {label}
      </span>
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        style={{
          background: 'transparent', border: 'none', outline: 'none',
          color: '#94a3b8', fontSize: 11, padding: '5px 8px', cursor: 'pointer',
        }}
      >
        {options.map(o => (
          <option key={o.value} value={o.value} style={{ background: '#1e293b' }}>{o.label}</option>
        ))}
      </select>
    </div>
  )
}

function ToggleBtn({ active, onClick, title, children }: {
  active: boolean; onClick: () => void; title: string; children: React.ReactNode
}) {
  return (
    <button
      title={title}
      onClick={onClick}
      style={{
        padding: '6px 9px', border: 'none', cursor: 'pointer',
        background: active ? 'rgba(99,102,241,0.2)' : 'transparent',
        color: active ? '#818cf8' : '#475569',
        display: 'flex', alignItems: 'center', transition: 'all 0.12s',
      }}
    >
      {children}
    </button>
  )
}

function PageBtn({ active, disabled, onClick, children }: {
  active?: boolean; disabled?: boolean; onClick: () => void; children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      style={{
        minWidth: 28, height: 28, borderRadius: 6, border: 'none',
        background: active
          ? 'rgba(99,102,241,0.25)'
          : 'rgba(255,255,255,0.04)',
        color: active ? '#818cf8' : disabled ? '#1e293b' : '#64748b',
        fontSize: 11, fontWeight: active ? 700 : 400, cursor: disabled ? 'default' : 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        transition: 'all 0.12s', padding: '0 6px',
      }}
    >
      {children}
    </button>
  )
}

function ActionBtn({ title, color, bg, onClick, children }: {
  title: string; color: string; bg: string; onClick?: (e: React.MouseEvent) => void; children: React.ReactNode
}) {
  return (
    <button
      title={title}
      onClick={onClick}
      style={{
        width: 26, height: 26, borderRadius: 6, flexShrink: 0,
        background: bg, border: `1px solid ${color}28`,
        color, display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer',
      }}
    >
      {children}
    </button>
  )
}

// ── Suite row (list view) ─────────────────────────────────────────────────────

const COL = '2fr 110px 170px 110px 80px 148px 90px 106px 104px'

function SuiteRow({ suite, onView, onDelete }: {
  suite: Suite; onView: (s: Suite) => void; onDelete: (id: string) => void
}) {
  const [hov, setHov]       = useState(false)
  const [menu, setMenu]     = useState(false)
  const type  = deriveTestType(suite)
  const amb   = deriveAmbiente(suite)
  const tags  = deriveTags(suite)
  const pl    = platformLabel(suite.platform)
  const icon  = resolveAppIcon(suite.appPackage ?? '', suite.appName ?? '') || suite.icon || '🎬'
  const confirm = useConfirmation()

  const handleDeleteClick = async () => {
    setMenu(false)
    const label = suite.mode === 'caso' ? 'Caso de Prueba' : 'Suite'
    const ok = await confirm({
      title: `Eliminar ${label}`,
      description: `¿Estás seguro de eliminar "${suite.name}"? Esta acción no podrá deshacerse.`,
      type: 'delete',
    })
    if (ok) onDelete(suite.id)
  }

  return (
    <div
      onMouseEnter={() => setHov(true)}
      onMouseLeave={() => { setHov(false); setMenu(false) }}
      onClick={() => onView(suite)}
      style={{
        display: 'grid', gridTemplateColumns: COL, alignItems: 'center',
        padding: '10px 20px', gap: 8, cursor: 'pointer',
        borderBottom: '1px solid rgba(255,255,255,0.04)',
        background: hov ? 'rgba(255,255,255,0.028)' : 'transparent',
        transition: 'background 0.1s',
      }}
    >
      {/* SUITE */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 11, minWidth: 0 }}>
        <div style={{
          width: 40, height: 40, borderRadius: 10, flexShrink: 0,
          background: `${suite.accent}22`, border: `1px solid ${suite.accent}44`,
          display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 19,
        }}>
          {icon}
        </div>
        <div style={{ minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 2 }}>
            <span style={{
              fontSize: 12, fontWeight: 700, color: '#e2e8f0',
              overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
            }}>
              {suite.name}
            </span>
            <span style={{
              fontSize: 9, fontWeight: 700, color: type.color,
              background: type.bg, borderRadius: 4, padding: '2px 5px', flexShrink: 0,
            }}>
              {type.label}
            </span>
          </div>
          <p style={{
            margin: '0 0 4px', fontSize: 10, color: '#475569',
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          }}>
            {suite.description || '—'}
          </p>
          <div style={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
            {tags.map(t => (
              <span key={t} style={{
                fontSize: 9, color: '#334155',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.07)',
                borderRadius: 4, padding: '1px 5px',
              }}>
                {t}
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* PLATAFORMA */}
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginBottom: 2 }}>
          <Smartphone size={11} color={pl.color} />
          <span style={{ fontSize: 11, color: pl.color, fontWeight: 600 }}>{pl.text}</span>
        </div>
        <span style={{ fontSize: 9, color: '#334155' }}>{pl.sub}</span>
      </div>

      {/* APLICACIÓN */}
      <div style={{ minWidth: 0 }}>
        <p style={{ margin: 0, fontSize: 11, color: '#94a3b8', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {suite.appName || 'App'}
        </p>
        <p style={{ margin: '2px 0 0', fontSize: 9, color: '#334155', fontFamily: 'monospace', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {suite.appPackage || '—'}
        </p>
      </div>

      {/* AMBIENTE */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <div style={{ width: 7, height: 7, borderRadius: '50%', flexShrink: 0, background: amb.color, boxShadow: `0 0 5px ${amb.color}88` }} />
        <span style={{ fontSize: 11, color: amb.color, fontWeight: 500 }}>{amb.label}</span>
      </div>

      {/* PASOS */}
      <div>
        <p style={{ margin: 0, fontSize: 14, fontWeight: 700, color: '#e2e8f0' }}>{suite.stepCount}</p>
        <p style={{ margin: '1px 0 0', fontSize: 9, color: '#334155' }}>pasos</p>
      </div>

      {/* ÚLTIMA EJECUCIÓN */}
      <div>
        <p style={{ margin: 0, fontSize: 11, color: '#94a3b8' }}>{fmtDate(suite.updatedAt || suite.savedAt)}</p>
        <p style={{ margin: '2px 0 0', fontSize: 9, color: '#334155' }}>{fmtShort(suite.savedAt)}</p>
      </div>

      {/* ÉXITO */}
      <div>
        <p style={{ margin: 0, fontSize: 12, color: '#334155' }}>—</p>
      </div>

      {/* ESTADO */}
      <div>
        {suite.status === 'active' ? (
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 4,
            fontSize: 10, fontWeight: 600, borderRadius: 6, padding: '3px 8px',
            color: '#4ade80', background: 'rgba(74,222,128,0.1)', border: '1px solid rgba(74,222,128,0.25)',
          }}>
            <div style={{ width: 5, height: 5, borderRadius: '50%', background: '#4ade80' }} />
            Activa
          </span>
        ) : (
          <span style={{
            display: 'inline-flex', alignItems: 'center', gap: 4,
            fontSize: 10, fontWeight: 600, borderRadius: 6, padding: '3px 8px',
            color: '#f87171', background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)',
          }}>
            <div style={{ width: 5, height: 5, borderRadius: '50%', background: '#f87171' }} />
            {suite.status === 'pending' ? 'Inactiva' : 'Borrador'}
          </span>
        )}
      </div>

      {/* ACCIONES */}
      <div
        style={{ display: 'flex', alignItems: 'center', gap: 4, opacity: hov ? 1 : 0, transition: 'opacity 0.12s' }}
        onClick={e => e.stopPropagation()}
      >
        <ActionBtn title="Ejecutar" color="#4ade80" bg="rgba(74,222,128,0.1)">
          <Play size={9} fill="#4ade80" />
        </ActionBtn>
        <ActionBtn title="Editar" color="#818cf8" bg="rgba(99,102,241,0.1)">
          <PencilLine size={9} />
        </ActionBtn>
        <div style={{ position: 'relative' }}>
          <ActionBtn
            title="Más opciones"
            color="#64748b"
            bg="rgba(100,116,139,0.08)"
            onClick={() => setMenu(v => !v)}
          >
            <MoreHorizontal size={9} />
          </ActionBtn>
          {menu && (
            <div style={{
              position: 'absolute', right: 0, top: '110%', zIndex: 100,
              background: '#1e293b', border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 8, overflow: 'hidden', minWidth: 130,
              boxShadow: '0 8px 28px rgba(0,0,0,0.6)',
            }}>
              <ContextMenuItem label="Ver detalle" onClick={() => { onView(suite); setMenu(false) }} />
              <ContextMenuItem
                label="Eliminar"
                color="#f87171"
                onClick={handleDeleteClick}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function ContextMenuItem({ label, color, onClick }: { label: string; color?: string; onClick: () => void }) {
  const [h, setH] = useState(false)
  return (
    <button
      onClick={onClick}
      onMouseEnter={() => setH(true)}
      onMouseLeave={() => setH(false)}
      style={{
        width: '100%', padding: '8px 14px', textAlign: 'left',
        background: h ? 'rgba(255,255,255,0.05)' : 'transparent',
        border: 'none', color: color ?? '#94a3b8', fontSize: 11, cursor: 'pointer',
      }}
    >
      {label}
    </button>
  )
}

// ── Suite card (grid view) ────────────────────────────────────────────────────

function SuiteCard({ suite, onView, onDelete }: {
  suite: Suite; onView: (s: Suite) => void; onDelete: (id: string) => void
}) {
  const [hov, setHov] = useState(false)
  const type    = deriveTestType(suite)
  const amb     = deriveAmbiente(suite)
  const pl      = platformLabel(suite.platform)
  const icon    = resolveAppIcon(suite.appPackage ?? '', suite.appName ?? '') || suite.icon || '🎬'
  const confirm = useConfirmation()

  const handleDeleteClick = async (e: React.MouseEvent) => {
    e.stopPropagation()
    const label = suite.mode === 'caso' ? 'Caso de Prueba' : 'Suite'
    const ok = await confirm({
      title: `Eliminar ${label}`,
      description: `¿Estás seguro de eliminar "${suite.name}"? Esta acción no podrá deshacerse.`,
      type: 'delete',
    })
    if (ok) onDelete(suite.id)
  }

  return (
    <div
      onMouseEnter={() => setHov(true)}
      onMouseLeave={() => setHov(false)}
      onClick={() => onView(suite)}
      style={{
        background: hov ? 'rgba(255,255,255,0.045)' : 'rgba(255,255,255,0.025)',
        border: `1px solid ${hov ? 'rgba(99,102,241,0.3)' : 'rgba(255,255,255,0.07)'}`,
        borderRadius: 12, padding: '14px 16px',
        display: 'flex', flexDirection: 'column', gap: 10,
        cursor: 'pointer', transition: 'all 0.15s', position: 'relative',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
        <div style={{
          width: 36, height: 36, borderRadius: 9, flexShrink: 0,
          background: `${suite.accent}22`, border: `1px solid ${suite.accent}44`,
          display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18,
        }}>
          {icon}
        </div>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 5, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: '#e2e8f0', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {suite.name}
            </span>
            <span style={{ fontSize: 9, fontWeight: 700, color: type.color, background: type.bg, borderRadius: 4, padding: '2px 5px', flexShrink: 0 }}>
              {type.label}
            </span>
          </div>
          {suite.description && (
            <p style={{ margin: '2px 0 0', fontSize: 10, color: '#475569', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {suite.description}
            </p>
          )}
        </div>
        <button
          onClick={handleDeleteClick}
          style={{
            background: 'rgba(239,68,68,0.06)', border: '1px solid rgba(239,68,68,0.15)',
            borderRadius: 6, padding: '4px 6px', color: '#ef4444', cursor: 'pointer',
            opacity: hov ? 1 : 0, transition: 'opacity 0.15s', flexShrink: 0,
            display: 'flex', alignItems: 'center',
          }}
        >
          <Trash2 size={10} />
        </button>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        <span style={{ fontSize: 10, color: pl.color, display: 'flex', alignItems: 'center', gap: 4 }}>
          <Smartphone size={10} />{pl.text}
        </span>
        <span style={{ fontSize: 10, color: amb.color, display: 'flex', alignItems: 'center', gap: 4 }}>
          <div style={{ width: 6, height: 6, borderRadius: '50%', background: amb.color }} />
          {amb.label}
        </span>
        <span style={{ fontSize: 10, color: '#475569', display: 'flex', alignItems: 'center', gap: 4 }}>
          <ListChecks size={10} />{suite.stepCount} pasos
        </span>
      </div>

      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        paddingTop: 8, borderTop: '1px solid rgba(255,255,255,0.05)',
      }}>
        <span style={{ fontSize: 9, color: '#334155', display: 'flex', alignItems: 'center', gap: 3 }}>
          <Clock size={8} />{fmtDate(suite.savedAt).split(',')[0]}
        </span>
        {suite.status === 'active' ? (
          <span style={{ fontSize: 9, fontWeight: 700, color: '#4ade80', background: 'rgba(74,222,128,0.1)', border: '1px solid rgba(74,222,128,0.2)', borderRadius: 4, padding: '2px 7px' }}>
            Activa
          </span>
        ) : (
          <span style={{ fontSize: 9, fontWeight: 700, color: '#f87171', background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.2)', borderRadius: 4, padding: '2px 7px' }}>
            Borrador
          </span>
        )}
      </div>
    </div>
  )
}

// ── Detail modal ──────────────────────────────────────────────────────────────

function DetailModal({ suite, onClose }: { suite: Suite; onClose: () => void }) {
  const [tab, setTab] = useState<'steps' | 'code' | 'pageobjects'>('steps')
  const icon = resolveAppIcon(suite.appPackage ?? '', suite.appName ?? '') || suite.icon || '🎬'

  return (
    <div
      style={{
        position: 'fixed', inset: 0, zIndex: 1000,
        background: 'rgba(0,0,0,0.72)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24,
      }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <div style={{
        background: '#0d1117', border: '1px solid rgba(255,255,255,0.1)',
        borderRadius: 16, width: '100%', maxWidth: 860, maxHeight: '88vh',
        display: 'flex', flexDirection: 'column', overflow: 'hidden',
      }}>
        {/* header */}
        <div style={{
          padding: '14px 20px', borderBottom: '1px solid rgba(255,255,255,0.07)',
          display: 'flex', alignItems: 'center', gap: 12,
        }}>
          <div style={{
            width: 36, height: 36, borderRadius: 9, flexShrink: 0, fontSize: 18,
            background: `${suite.accent}22`, border: `1px solid ${suite.accent}44`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            {icon}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <p style={{ margin: 0, fontSize: 14, fontWeight: 700, color: '#e2e8f0' }}>{suite.name}</p>
            <p style={{ margin: 0, fontSize: 10, color: '#475569' }}>{suite.description}</p>
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 7, padding: '5px 9px', color: '#64748b', cursor: 'pointer', display: 'flex',
            }}
          >
            <X size={13} />
          </button>
        </div>

        {/* tabs */}
        <div style={{ display: 'flex', gap: 2, padding: '8px 20px 0', borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
          {([
            { id: 'steps',       label: 'Pasos',        icon: <Layers3 size={10} /> },
            { id: 'code',        label: 'Código',       icon: <Code2 size={10} /> },
            { id: 'pageobjects', label: 'Page Objects', icon: <FileCode2 size={10} /> },
          ] as const).map(t => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              style={{
                display: 'flex', alignItems: 'center', gap: 5, padding: '5px 11px',
                fontSize: 11, fontWeight: 600, cursor: 'pointer', border: 'none', borderRadius: '5px 5px 0 0',
                color: tab === t.id ? '#818cf8' : '#475569',
                background: tab === t.id ? 'rgba(99,102,241,0.12)' : 'transparent',
                borderBottom: tab === t.id ? '2px solid #6366f1' : '2px solid transparent',
              }}
            >
              {t.icon}{t.label}
            </button>
          ))}
        </div>

        {/* body */}
        <div style={{ flex: 1, overflow: 'auto', padding: 20 }}>
          {tab === 'steps' && (
            suite.steps.length === 0
              ? <p style={{ color: '#334155', fontSize: 12, textAlign: 'center', padding: '36px 0' }}>Sin pasos registrados.</p>
              : <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                  {suite.steps.map(s => (
                    <div key={s.id} style={{
                      display: 'flex', alignItems: 'flex-start', gap: 9,
                      padding: '7px 11px', background: 'rgba(255,255,255,0.03)',
                      border: '1px solid rgba(255,255,255,0.06)', borderRadius: 7,
                    }}>
                      <span style={{ fontSize: 9, color: '#475569', background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 3, padding: '1px 5px', fontWeight: 600, flexShrink: 0, fontFamily: 'monospace' }}>
                        #{s.n}
                      </span>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <span style={{ fontSize: 10, fontWeight: 700, color: '#e2e8f0', textTransform: 'uppercase' }}>{s.type}</span>
                        {s.el?.varName && <span style={{ fontSize: 10, color: '#818cf8', fontFamily: 'monospace', marginLeft: 7 }}>{s.el.varName}</span>}
                        {s.el?.locatorValue && <span style={{ fontSize: 10, color: '#475569', marginLeft: 7, fontFamily: 'monospace' }}>{s.el.locatorStrategy}={s.el.locatorValue}</span>}
                        {s.inputVal && <span style={{ fontSize: 10, color: '#34d399', marginLeft: 7 }}>"{s.inputVal}"</span>}
                      </div>
                      <span style={{ fontSize: 9, color: '#334155', fontFamily: 'monospace', flexShrink: 0 }}>{s.timeStr}</span>
                    </div>
                  ))}
                </div>
          )}
          {tab === 'code' && (
            <pre style={{ margin: 0, fontSize: 11, lineHeight: 1.65, color: '#94a3b8', fontFamily: '"JetBrains Mono","Fira Code",monospace', background: 'rgba(0,0,0,0.3)', padding: 16, borderRadius: 8, overflowX: 'auto' }}>
              {suite.generatedCode || '// Sin código generado'}
            </pre>
          )}
          {tab === 'pageobjects' && (
            <pre style={{ margin: 0, fontSize: 11, lineHeight: 1.65, color: '#94a3b8', fontFamily: '"JetBrains Mono","Fira Code",monospace', background: 'rgba(0,0,0,0.3)', padding: 16, borderRadius: 8, overflowX: 'auto' }}>
              {suite.pageObjects || '// Sin Page Objects generados'}
            </pre>
          )}
        </div>
      </div>
    </div>
  )
}

// ── Empty state ───────────────────────────────────────────────────────────────

function EmptyState({ hasFilters }: { hasFilters: boolean }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center',
      justifyContent: 'center', padding: '64px 32px', gap: 14, textAlign: 'center',
    }}>
      <div style={{
        width: 60, height: 60, borderRadius: 16,
        background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.18)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Layers3 size={26} color="rgba(99,102,241,0.45)" />
      </div>
      <div>
        <p style={{ margin: 0, fontSize: 13, fontWeight: 700, color: '#e2e8f0' }}>
          {hasFilters ? 'Sin resultados' : 'Sin suites guardadas'}
        </p>
        <p style={{ margin: '5px 0 0', fontSize: 11, color: '#475569', lineHeight: 1.6 }}>
          {hasFilters
            ? 'Ninguna suite coincide con los filtros aplicados.'
            : <>Graba una sesión en Record Studio y guárdala<br />como Suite para que aparezca aquí.</>}
        </p>
      </div>
      {!hasFilters && (
        <div style={{
          display: 'flex', alignItems: 'center', gap: 5, padding: '7px 13px',
          background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.2)', borderRadius: 8,
        }}>
          <AlertCircle size={10} color="#6366f1" />
          <span style={{ fontSize: 11, color: '#6366f1' }}>Record Studio → Guardar Suite</span>
        </div>
      )}
    </div>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function SuitesPage() {
  const [suites,         setSuites]         = useState<Suite[]>(() => suiteService.getAllSuites())
  const [viewing,        setViewing]        = useState<Suite | null>(null)
  const [viewMode,       setViewMode]       = useState<'list' | 'grid'>('list')
  const [search,         setSearch]         = useState('')
  const [filterPlatform, setFilterPlatform] = useState('all')
  const [filterStatus,   setFilterStatus]   = useState('all')
  const [page,           setPage]           = useState(1)
  const [pageSize,       setPageSize]       = useState(10)
  const [toastMsg,       setToastMsg]       = useState<string | null>(null)

  const showToast = useCallback((msg: string) => {
    setToastMsg(msg)
    setTimeout(() => setToastMsg(null), 3200)
  }, [])

  const reload = useCallback(() => setSuites(suiteService.getAllSuites()), [])

  useEffect(() => {
    window.addEventListener('qa:suite:created', reload)
    window.addEventListener('qa:suite:updated', reload)
    window.addEventListener('qa:suite:deleted', reload)
    return () => {
      window.removeEventListener('qa:suite:created', reload)
      window.removeEventListener('qa:suite:updated', reload)
      window.removeEventListener('qa:suite:deleted', reload)
    }
  }, [reload])

  // Stats
  const activeCount   = useMemo(() => suites.filter(s => s.status === 'active').length, [suites])
  const inactiveCount = suites.length - activeCount
  const totalSteps    = useMemo(() => suites.reduce((sum, s) => sum + s.stepCount, 0), [suites])
  const lastSuite     = useMemo(() => {
    if (!suites.length) return null
    return [...suites].sort((a, b) => (b.savedAt > a.savedAt ? 1 : -1))[0]
  }, [suites])

  // Filters
  const hasFilters = search !== '' || filterPlatform !== 'all' || filterStatus !== 'all'
  const filtered = useMemo(() => suites.filter(s => {
    if (search) {
      const q = search.toLowerCase()
      if (!s.name.toLowerCase().includes(q) &&
          !s.description.toLowerCase().includes(q) &&
          !(s.appPackage ?? '').toLowerCase().includes(q)) return false
    }
    if (filterPlatform !== 'all' && s.platform !== filterPlatform) return false
    if (filterStatus !== 'all' && s.status !== filterStatus) return false
    return true
  }), [suites, search, filterPlatform, filterStatus])

  // Pagination
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize))
  const paginated  = filtered.slice((page - 1) * pageSize, page * pageSize)

  useEffect(() => setPage(1), [search, filterPlatform, filterStatus, pageSize])

  function handleDelete(id: string) {
    const suite = suites.find(s => s.id === id)
    suiteService.deleteSuite(id)
    if (viewing?.id === id) setViewing(null)
    const label = suite?.mode === 'caso' ? 'Caso de prueba' : 'Suite'
    showToast(`${label} "${suite?.name ?? ''}" eliminada correctamente`)
  }

  // Build visible page numbers
  const pageNums = useMemo(() => {
    if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i + 1)
    const around = new Set([1, 2, page - 1, page, page + 1, totalPages - 1, totalPages].filter(n => n >= 1 && n <= totalPages))
    return [...around].sort((a, b) => a - b)
  }, [totalPages, page])

  return (
    <div style={{ padding: '24px 28px', display: 'flex', flexDirection: 'column', gap: 18, minHeight: '100%' }}>

      {/* ── Page header ── */}
      <div>
        <h1 style={{ margin: 0, fontSize: 22, fontWeight: 800, color: '#e2e8f0', letterSpacing: -0.4 }}>Suites</h1>
        <p style={{ margin: '4px 0 0', fontSize: 12, color: '#475569' }}>
          Gestiona y organiza todas tus suites de pruebas automatizadas
        </p>
      </div>

      {/* ── Stats row ── */}
      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <StatCard
          icon={<BarChart3 size={15} />}
          iconBg="rgba(99,102,241,0.12)" iconColor="#818cf8"
          label="Total Suites"
          value={String(suites.length)}
          sub={suites.length === 0 ? 'Sin suites' : `+${suites.length} grabadas`}
        />
        <StatCard
          icon={<Layers3 size={15} />}
          iconBg="rgba(74,222,128,0.12)" iconColor="#4ade80"
          label="Suites Activas"
          value={String(activeCount)}
          sub={suites.length > 0 ? `${Math.round(activeCount / suites.length * 100)}% del total` : '—'}
          subColor="#4ade80"
        />
        <StatCard
          icon={<XCircle size={15} />}
          iconBg="rgba(239,68,68,0.1)" iconColor="#f87171"
          label="Suites Inactivas"
          value={String(inactiveCount)}
          sub={suites.length > 0 ? `${Math.round(inactiveCount / suites.length * 100)}% del total` : '—'}
          subColor="#f87171"
        />
        <StatCard
          icon={<ListChecks size={15} />}
          iconBg="rgba(168,85,247,0.12)" iconColor="#c084fc"
          label="Total Pasos"
          value={totalSteps.toLocaleString('es-MX')}
          sub="pasos grabados"
        />
        <StatCard
          icon={<Clock size={15} />}
          iconBg="rgba(20,184,166,0.12)" iconColor="#2dd4bf"
          label="Última Grabación"
          value={lastSuite ? fmtDate(lastSuite.savedAt).split(',')[0] : '—'}
          sub={lastSuite ? lastSuite.name : 'Sin registros'}
        />
        <StatCard
          icon={<CheckCircle2 size={15} />}
          iconBg="rgba(74,222,128,0.08)" iconColor="#4ade80"
          label="Éxito Promedio"
          value="—"
          sub="sin datos de ejecución"
        />
      </div>

      {/* ── Table / grid container ── */}
      <div style={{
        background: 'rgba(255,255,255,0.02)',
        border: '1px solid rgba(255,255,255,0.07)',
        borderRadius: 14, overflow: 'hidden',
      }}>
        {/* Filter bar */}
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap',
          padding: '11px 16px', borderBottom: '1px solid rgba(255,255,255,0.06)',
        }}>
          {/* Search */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: 7,
            background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.09)',
            borderRadius: 8, padding: '5px 10px', flex: '1 1 170px', maxWidth: 260,
          }}>
            <Search size={11} color="#475569" />
            <input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Buscar suites..."
              style={{ flex: 1, background: 'none', border: 'none', outline: 'none', fontSize: 11, color: '#94a3b8' }}
            />
            {search && (
              <button onClick={() => setSearch('')} style={{ background: 'none', border: 'none', color: '#475569', cursor: 'pointer', padding: 0, display: 'flex' }}>
                <X size={10} />
              </button>
            )}
          </div>

          <FilterSelect
            label="Plataforma"
            value={filterPlatform}
            onChange={setFilterPlatform}
            options={[{ value:'all',label:'Todas' },{ value:'android',label:'Android' },{ value:'ios',label:'iOS' }]}
          />
          <FilterSelect
            label="Estado"
            value={filterStatus}
            onChange={setFilterStatus}
            options={[{ value:'all',label:'Todos' },{ value:'active',label:'Activa' },{ value:'draft',label:'Borrador' }]}
          />

          {hasFilters && (
            <button
              onClick={() => { setSearch(''); setFilterPlatform('all'); setFilterStatus('all') }}
              style={{
                display: 'flex', alignItems: 'center', gap: 5, padding: '5px 10px', borderRadius: 7,
                background: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)',
                color: '#f87171', fontSize: 11, cursor: 'pointer',
              }}
            >
              <X size={9} /> Limpiar filtros
            </button>
          )}

          <div style={{ flex: 1 }} />

          <div style={{ display: 'flex', background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 8, overflow: 'hidden' }}>
            <ToggleBtn active={viewMode === 'list'} onClick={() => setViewMode('list')} title="Vista lista"><LayoutList size={13} /></ToggleBtn>
            <ToggleBtn active={viewMode === 'grid'} onClick={() => setViewMode('grid')} title="Vista cuadrícula"><LayoutGrid size={13} /></ToggleBtn>
          </div>
        </div>

        {/* Table header — list mode */}
        {viewMode === 'list' && filtered.length > 0 && (
          <div style={{
            display: 'grid', gridTemplateColumns: COL,
            padding: '7px 20px', gap: 8,
            background: 'rgba(255,255,255,0.02)', borderBottom: '1px solid rgba(255,255,255,0.05)',
          }}>
            {['SUITE','PLATAFORMA','APLICACIÓN','AMBIENTE','PASOS','ÚLTIMA EJECUCIÓN','ÉXITO','ESTADO','ACCIONES'].map(c => (
              <span key={c} style={{ fontSize: 9, fontWeight: 700, color: '#334155', letterSpacing: 0.5, textTransform: 'uppercase' }}>
                {c}
              </span>
            ))}
          </div>
        )}

        {/* Rows / cards / empty */}
        {filtered.length === 0 ? (
          <EmptyState hasFilters={hasFilters} />
        ) : viewMode === 'list' ? (
          paginated.map(s => (
            <SuiteRow key={s.id} suite={s} onView={setViewing} onDelete={handleDelete} />
          ))
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 12, padding: 16 }}>
            {paginated.map(s => (
              <SuiteCard key={s.id} suite={s} onView={setViewing} onDelete={handleDelete} />
            ))}
          </div>
        )}

        {/* Pagination */}
        {filtered.length > 0 && (
          <div style={{
            display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 8,
            padding: '10px 20px', borderTop: '1px solid rgba(255,255,255,0.05)',
          }}>
            <span style={{ fontSize: 11, color: '#475569' }}>
              Mostrando {Math.min((page - 1) * pageSize + 1, filtered.length)} a{' '}
              {Math.min(page * pageSize, filtered.length)} de {filtered.length} suite{filtered.length !== 1 ? 's' : ''}
            </span>

            <div style={{ display: 'flex', alignItems: 'center', gap: 3 }}>
              <PageBtn onClick={() => setPage(p => Math.max(1, p - 1))} disabled={page === 1}>
                <ChevronLeft size={11} />
              </PageBtn>
              {pageNums.map((n, i) => (
                <React.Fragment key={n}>
                  {i > 0 && pageNums[i - 1] !== n - 1 && (
                    <span style={{ fontSize: 11, color: '#334155', padding: '0 2px' }}>…</span>
                  )}
                  <PageBtn active={n === page} onClick={() => setPage(n)}>{n}</PageBtn>
                </React.Fragment>
              ))}
              <PageBtn onClick={() => setPage(p => Math.min(totalPages, p + 1))} disabled={page === totalPages}>
                <ChevronRight size={11} />
              </PageBtn>
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ fontSize: 11, color: '#475569' }}>Mostrar</span>
              <select
                value={pageSize}
                onChange={e => setPageSize(Number(e.target.value))}
                style={{
                  background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)',
                  borderRadius: 6, color: '#94a3b8', fontSize: 11, padding: '3px 6px', cursor: 'pointer',
                  outline: 'none',
                }}
              >
                <option value={10} style={{ background: '#1e293b' }}>10</option>
                <option value={25} style={{ background: '#1e293b' }}>25</option>
                <option value={50} style={{ background: '#1e293b' }}>50</option>
              </select>
            </div>
          </div>
        )}
      </div>

      {/* Detail modal */}
      {viewing && <DetailModal suite={viewing} onClose={() => setViewing(null)} />}

      {/* Toast */}
      <AnimatePresence>
        {toastMsg && (
          <motion.div
            key="suites-toast"
            initial={{ opacity: 0, y: 20, scale: 0.95 }}
            animate={{ opacity: 1, y: 0,  scale: 1    }}
            exit={{   opacity: 0, y: 10,  scale: 0.95 }}
            style={{
              position: 'fixed', bottom: 28, right: 28, zIndex: 9999,
              background: 'linear-gradient(135deg, #1e293b, #0f172a)',
              border: '1px solid rgba(52,211,153,0.35)',
              borderRadius: 10, padding: '11px 18px',
              display: 'flex', alignItems: 'center', gap: 9,
              boxShadow: '0 8px 32px rgba(0,0,0,0.5), 0 0 0 1px rgba(52,211,153,0.12)',
              maxWidth: 340,
            }}
          >
            <span style={{ fontSize: 14 }}>✓</span>
            <span style={{ color: '#e2e8f0', fontSize: 12, fontWeight: 500 }}>{toastMsg}</span>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
