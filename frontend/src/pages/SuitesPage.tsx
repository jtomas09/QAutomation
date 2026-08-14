import React, { useState, useEffect, useCallback, useMemo, useRef, DragEvent } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { suiteService } from '../services/SuiteService'
import type { TestSuite, TestCase } from '../services/SuiteService'
import { useConfirmation } from '../hooks/useConfirmation'
import { appIconResolver } from '../services/ApplicationIconResolver'
import { executionTrackingService } from '../services/ExecutionTrackingService'
import { getDevices } from '../api'
import type { PhysicalDevice } from '../types'
import { resolveDeviceDisplayName } from '../utils/displayNames'
import {
  Layers3, Trash2, Play, PencilLine, MoreHorizontal,
  Search, X, ChevronDown, ChevronRight,
  CheckCircle2, Clock, Smartphone,
  Code2, FileCode2, ListChecks, Copy, Check,
  Plus, GripVertical, AlertCircle, Package, Zap,
} from 'lucide-react'

// ── Helpers ───────────────────────────────────────────────────────────────────

function fmtDate(iso: string): string {
  if (!iso) return '—'
  try {
    const d   = new Date(iso)
    const now = new Date()
    const yday = new Date(now); yday.setDate(now.getDate() - 1)
    const time = d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
    if (d.toDateString() === now.toDateString())  return `Hoy, ${time}`
    if (d.toDateString() === yday.toDateString()) return `Ayer, ${time}`
    return d.toLocaleDateString('es-MX', { day: '2-digit', month: 'short' }) + ` ${time}`
  } catch { return iso }
}

function platformBadge(platform: string): { label: string; color: string } {
  if (platform === 'ios')     return { label: 'iOS',     color: '#60a5fa' }
  if (platform === 'android') return { label: 'Android', color: '#4ade80' }
  return                             { label: 'Multi',   color: '#94a3b8' }
}

function langLabel(lang: string): string {
  const MAP: Record<string, string> = {
    'java-testng': 'TestNG', 'java-junit': 'JUnit',
    'python': 'Python', 'javascript': 'JS',
    'csharp': 'C#', 'kotlin': 'Kotlin',
  }
  return MAP[lang] ?? lang
}

function suiteType(name: string, desc: string): { label: string; color: string; bg: string } {
  const t = `${name} ${desc}`.toLowerCase()
  if (t.includes('smoke'))              return { label: 'SMOKE', color: '#fb923c', bg: 'rgba(249,115,22,0.15)' }
  if (t.includes('e2e') || t.includes('flujo')) return { label: 'E2E', color: '#818cf8', bg: 'rgba(99,102,241,0.15)' }
  if (t.includes('reg'))                return { label: 'REG',   color: '#c084fc', bg: 'rgba(192,132,252,0.15)' }
  return                                       { label: 'SUITE', color: '#94a3b8', bg: 'rgba(148,163,184,0.1)'  }
}

// ── AppIcon — auto-resolves logo from package name ────────────────────────────

function AppIcon({ pkg, appName, size = 32 }: { pkg: string; appName: string; size?: number }) {
  const app = appIconResolver.resolveApplication(pkg, '', appName)
  const [failed, setFailed] = useState(false)
  const r = Math.min(size * 0.28, 10)

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
      fontSize: size * 0.44, fontWeight: 800,
    }}>
      {app.fallbackEmoji || app.displayName.charAt(0)}
    </div>
  )
}

// ── ExecuteSuiteModal ─────────────────────────────────────────────────────────

interface ExecuteSuiteModalProps {
  suite: TestSuite
  onClose(): void
  onExecute(device: PhysicalDevice | null, environment: string): void
}

const ENVIRONMENTS = [
  { id: 'qa',      label: 'QA',         flag: '🧪' },
  { id: 'staging', label: 'Staging',    flag: '🔶' },
  { id: 'prod',    label: 'Producción', flag: '🟢' },
]

function ExecuteSuiteModal({ suite, onClose, onExecute }: ExecuteSuiteModalProps) {
  const [env,     setEnv]     = useState('qa')
  const [devices, setDevices] = useState<PhysicalDevice[]>([])
  const [deviceId, setDeviceId] = useState('')
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    getDevices().then(d => {
      setDevices(d)
      if (d.length > 0 && !deviceId) setDeviceId(d[0].udid)
    }).catch(() => {})
  }, [deviceId])

  const selectedDevice = devices.find(d => d.udid === deviceId) ?? null
  const caseCount      = suite.testCases.length

  const handleRun = async () => {
    setLoading(true)
    await onExecute(selectedDevice, env)
    setLoading(false)
    onClose()
  }

  return (
    <motion.div
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      style={{
        position: 'fixed', inset: 0, zIndex: 600,
        background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24,
      }}
      onClick={onClose}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.94, y: 10 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.94, y: 10 }}
        transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
        onClick={e => e.stopPropagation()}
        style={{
          background: '#111827', border: '1px solid rgba(52,211,153,0.25)',
          borderRadius: 14, padding: 28, width: '100%', maxWidth: 420,
          display: 'flex', flexDirection: 'column', gap: 18,
          boxShadow: '0 24px 64px rgba(0,0,0,0.6)',
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <AppIcon pkg={suite.appPackage} appName={suite.appName} size={40} />
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: '#f1f5f9', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {suite.name}
            </div>
            <div style={{ fontSize: 11, color: '#64748b' }}>
              {caseCount} caso{caseCount !== 1 ? 's' : ''} · {suite.platform || 'Multi'}
            </div>
          </div>
          <button onClick={onClose} style={{ color: '#475569', background: 'none', border: 'none', cursor: 'pointer', padding: 4 }}>
            <X size={16} />
          </button>
        </div>

        {/* Environment */}
        <div>
          <label style={{ display: 'block', fontSize: 11, color: '#94a3b8', fontWeight: 600, marginBottom: 7 }}>Ambiente</label>
          <div style={{ display: 'flex', gap: 8 }}>
            {ENVIRONMENTS.map(e => (
              <button
                key={e.id}
                onClick={() => setEnv(e.id)}
                style={{
                  flex: 1, padding: '8px 0', borderRadius: 7, cursor: 'pointer', fontSize: 11,
                  fontWeight: env === e.id ? 700 : 500,
                  border: `1px solid ${env === e.id ? 'rgba(52,211,153,0.5)' : 'rgba(255,255,255,0.1)'}`,
                  background: env === e.id ? 'rgba(52,211,153,0.1)' : 'rgba(255,255,255,0.03)',
                  color: env === e.id ? '#34d399' : '#64748b',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 5,
                }}
              >
                {e.flag} {e.label}
              </button>
            ))}
          </div>
        </div>

        {/* Device */}
        <div>
          <label style={{ display: 'block', fontSize: 11, color: '#94a3b8', fontWeight: 600, marginBottom: 5 }}>
            Dispositivo
          </label>
          {devices.length === 0 ? (
            <div style={{
              padding: '10px 12px', borderRadius: 7, fontSize: 11, color: '#f59e0b',
              background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.2)',
            }}>
              Sin dispositivos conectados — la ejecución se realizará sin dispositivo físico.
            </div>
          ) : (
            <select
              value={deviceId}
              onChange={e => setDeviceId(e.target.value)}
              style={{
                width: '100%', background: '#0d1117', border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 7, color: '#e2e8f0', padding: '8px 11px', fontSize: 12,
                boxSizing: 'border-box', outline: 'none', fontFamily: 'inherit',
              }}
            >
              <option value="">— Sin dispositivo —</option>
              {devices.map(d => (
                <option key={d.udid} value={d.udid}>{resolveDeviceDisplayName(d).title} ({d.platform})</option>
              ))}
            </select>
          )}
        </div>

        {/* Execute */}
        <button
          onClick={handleRun}
          disabled={loading || caseCount === 0}
          style={{
            width: '100%', padding: '11px 0', borderRadius: 8, border: 'none',
            background: loading || caseCount === 0
              ? 'rgba(255,255,255,0.05)'
              : 'linear-gradient(90deg, #059669, #34d399)',
            color: loading || caseCount === 0 ? '#475569' : '#fff',
            fontSize: 13, fontWeight: 700, cursor: loading || caseCount === 0 ? 'not-allowed' : 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
          }}
        >
          <Zap size={14} />
          {loading ? 'Iniciando…' : caseCount === 0 ? 'Sin casos de prueba' : `Ejecutar ${caseCount} caso${caseCount !== 1 ? 's' : ''}`}
        </button>
      </motion.div>
    </motion.div>
  )
}

// ── Tabs shared ───────────────────────────────────────────────────────────────

const TABS: { id: string; label: string; icon: React.ElementType }[] = [
  { id: 'pasos',   label: 'Pasos',       icon: ListChecks },
  { id: 'code',    label: 'Código',      icon: Code2       },
  { id: 'objects', label: 'Page Objects', icon: FileCode2   },
]

// ── CaseDetailModal ───────────────────────────────────────────────────────────

interface CaseDetailModalProps {
  tc: TestCase
  suiteName: string
  onClose(): void
  onDelete(): void
}

function CaseDetailModal({ tc, suiteName, onClose, onDelete }: CaseDetailModalProps) {
  const [tab,    setTab]    = useState('pasos')
  const [copied, setCopied] = useState(false)

  const copyCode = useCallback(() => {
    const src = tab === 'code' ? tc.generatedCode : tab === 'objects' ? tc.pageObjects : ''
    if (!src) return
    navigator.clipboard.writeText(src).then(() => {
      setCopied(true); setTimeout(() => setCopied(false), 1500)
    })
  }, [tab, tc])

  return (
    <motion.div
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      style={{
        position: 'fixed', inset: 0, zIndex: 500,
        background: 'rgba(0,0,0,0.72)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24,
      }}
      onClick={onClose}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.95, y: 10 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.95, y: 10 }}
        transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
        onClick={e => e.stopPropagation()}
        style={{
          background: '#0f172a', border: '1px solid #1e293b',
          borderRadius: 14, width: '100%', maxWidth: 700, maxHeight: '85vh',
          display: 'flex', flexDirection: 'column',
          boxShadow: '0 24px 64px rgba(0,0,0,0.6)',
        }}
      >
        {/* Header */}
        <div style={{
          padding: '16px 20px', borderBottom: '1px solid #1e293b',
          display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12,
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 2 }}>
              <span style={{ fontSize: 11, color: '#64748b' }}>
                {suiteName} <span style={{ color: '#334155', margin: '0 4px' }}>/</span>
              </span>
              <span style={{ fontSize: 14, fontWeight: 700, color: '#f1f5f9' }}>{tc.name}</span>
            </div>
            <div style={{ display: 'flex', gap: 10 }}>
              <span style={{ fontSize: 10, color: '#64748b' }}>{tc.stepCount} pasos</span>
              <span style={{ fontSize: 10, color: '#64748b' }}>·</span>
              <span style={{ fontSize: 10, color: '#64748b' }}>{langLabel(tc.lang)}</span>
              <span style={{ fontSize: 10, color: '#64748b' }}>·</span>
              <span style={{ fontSize: 10, color: '#64748b' }}>{fmtDate(tc.updatedAt)}</span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button
              onClick={onDelete}
              style={{
                padding: '6px 10px', borderRadius: 7, cursor: 'pointer',
                background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)',
                color: '#f87171', fontSize: 11, fontWeight: 600, display: 'flex', alignItems: 'center', gap: 5,
              }}
            >
              <Trash2 size={12} /> Eliminar
            </button>
            <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#475569', cursor: 'pointer', padding: 4 }}>
              <X size={16} />
            </button>
          </div>
        </div>

        {/* Tabs */}
        <div style={{ display: 'flex', gap: 2, padding: '8px 16px 0', borderBottom: '1px solid #1e293b' }}>
          {TABS.map(t => {
            const active = tab === t.id
            return (
              <button
                key={t.id}
                onClick={() => setTab(t.id)}
                style={{
                  padding: '7px 14px', border: 'none', cursor: 'pointer', borderRadius: '8px 8px 0 0',
                  background: active ? '#1e293b' : 'none',
                  color: active ? '#e2e8f0' : '#64748b',
                  fontSize: 12, fontWeight: active ? 700 : 500,
                  display: 'flex', alignItems: 'center', gap: 5,
                  borderBottom: active ? '2px solid #6366f1' : '2px solid transparent',
                  marginBottom: -1,
                }}
              >
                <t.icon size={12} /> {t.label}
              </button>
            )
          })}
          {(tab === 'code' || tab === 'objects') && (
            <button
              onClick={copyCode}
              style={{
                marginLeft: 'auto', padding: '5px 10px', borderRadius: 6,
                background: copied ? 'rgba(52,211,153,0.12)' : 'rgba(255,255,255,0.05)',
                border: `1px solid ${copied ? 'rgba(52,211,153,0.3)' : 'rgba(255,255,255,0.1)'}`,
                color: copied ? '#34d399' : '#64748b', cursor: 'pointer', fontSize: 11,
                display: 'flex', alignItems: 'center', gap: 5, marginBottom: 4,
              }}
            >
              {copied ? <Check size={11} /> : <Copy size={11} />}
              {copied ? 'Copiado' : 'Copiar'}
            </button>
          )}
        </div>

        {/* Tab content */}
        <div style={{ flex: 1, overflow: 'auto', padding: 20 }}>
          {tab === 'pasos' && (
            tc.steps.length === 0
              ? <p style={{ color: '#475569', fontSize: 13, textAlign: 'center', padding: 32 }}>Sin pasos grabados.</p>
              : <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  {tc.steps.map((s, i) => (
                    <div key={s.id || i} style={{
                      display: 'flex', alignItems: 'flex-start', gap: 10,
                      padding: '7px 10px', borderRadius: 7,
                      background: 'rgba(255,255,255,0.03)',
                      border: '1px solid rgba(255,255,255,0.06)',
                    }}>
                      <span style={{
                        minWidth: 20, height: 20, borderRadius: 6,
                        background: 'rgba(99,102,241,0.18)', display: 'flex',
                        alignItems: 'center', justifyContent: 'center',
                        fontSize: 9, fontWeight: 700, color: '#818cf8', flexShrink: 0,
                      }}>{s.n}</span>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <span style={{ fontSize: 12, color: '#e2e8f0', fontWeight: 600, textTransform: 'capitalize' }}>
                          {s.type.replace(/_/g, ' ')}
                        </span>
                        {s.el?.varName && (
                          <span style={{ fontSize: 11, color: '#64748b', marginLeft: 8 }}>
                            {s.el.varName}
                          </span>
                        )}
                        {s.inputVal && (
                          <span style={{
                            marginLeft: 6, fontSize: 10, color: '#94a3b8',
                            background: 'rgba(148,163,184,0.08)', padding: '0 5px', borderRadius: 4,
                          }}>
                            "{s.inputVal}"
                          </span>
                        )}
                      </div>
                      <span style={{ fontSize: 10, color: '#334155', flexShrink: 0 }}>{s.timeStr}</span>
                    </div>
                  ))}
                </div>
          )}
          {(tab === 'code' || tab === 'objects') && (
            <pre style={{
              margin: 0, fontSize: 11, lineHeight: 1.6,
              color: '#94a3b8', fontFamily: 'monospace',
              whiteSpace: 'pre-wrap', wordBreak: 'break-all',
            }}>
              {(tab === 'code' ? tc.generatedCode : tc.pageObjects) || '// (sin contenido)'}
            </pre>
          )}
        </div>
      </motion.div>
    </motion.div>
  )
}

// ── CreateSuiteModal ──────────────────────────────────────────────────────────

interface CreateSuiteModalProps {
  onClose(): void
  onCreate(data: { name: string; description: string; platform: 'android' | 'ios' | '' }): void
}

function CreateSuiteModal({ onClose, onCreate }: CreateSuiteModalProps) {
  const [name,     setName]     = useState('')
  const [desc,     setDesc]     = useState('')
  const [platform, setPlatform] = useState<'android' | 'ios' | ''>('')

  const canCreate = name.trim().length > 0

  return (
    <motion.div
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
      style={{
        position: 'fixed', inset: 0, zIndex: 600,
        background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24,
      }}
      onClick={onClose}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.94, y: 10 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.94, y: 10 }}
        transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
        onClick={e => e.stopPropagation()}
        style={{
          background: '#111827', border: '1px solid #6366f144',
          borderRadius: 14, padding: 28, width: '100%', maxWidth: 400,
          display: 'flex', flexDirection: 'column', gap: 16,
          boxShadow: '0 24px 64px rgba(0,0,0,0.6)',
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div style={{
              width: 30, height: 30, borderRadius: 8,
              background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.3)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <Layers3 size={14} color="#818cf8" />
            </div>
            <span style={{ color: '#f1f5f9', fontWeight: 700, fontSize: 14 }}>Nueva Suite</span>
          </div>
          <button onClick={onClose} style={{ color: '#475569', background: 'none', border: 'none', cursor: 'pointer', padding: 4 }}>
            <X size={16} />
          </button>
        </div>

        {/* Name */}
        <div>
          <label style={{ display: 'block', fontSize: 11, color: '#94a3b8', fontWeight: 600, marginBottom: 5 }}>Nombre *</label>
          <input
            value={name} onChange={e => setName(e.target.value)}
            placeholder="E2E Flujo de Compra"
            autoFocus
            style={{
              width: '100%', background: '#0d1117', border: `1px solid ${name.trim() ? '#6366f155' : 'rgba(255,255,255,0.1)'}`,
              borderRadius: 7, color: '#e2e8f0', padding: '8px 11px', fontSize: 12,
              boxSizing: 'border-box', outline: 'none',
            }}
          />
        </div>

        {/* Description */}
        <div>
          <label style={{ display: 'block', fontSize: 11, color: '#94a3b8', fontWeight: 600, marginBottom: 5 }}>Descripción</label>
          <textarea
            value={desc} onChange={e => setDesc(e.target.value)}
            placeholder="Descripción opcional…"
            rows={2}
            style={{
              width: '100%', background: '#0d1117', border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 7, color: '#e2e8f0', padding: '8px 11px', fontSize: 12,
              boxSizing: 'border-box', outline: 'none', resize: 'none', fontFamily: 'inherit',
            }}
          />
        </div>

        {/* Platform */}
        <div>
          <label style={{ display: 'block', fontSize: 11, color: '#94a3b8', fontWeight: 600, marginBottom: 7 }}>Plataforma</label>
          <div style={{ display: 'flex', gap: 8 }}>
            {(['', 'android', 'ios'] as const).map(p => (
              <button
                key={p || 'all'}
                onClick={() => setPlatform(p)}
                style={{
                  flex: 1, padding: '7px 0', borderRadius: 7, cursor: 'pointer',
                  border: `1px solid ${platform === p ? '#6366f1' : 'rgba(255,255,255,0.1)'}`,
                  background: platform === p ? 'rgba(99,102,241,0.12)' : 'rgba(255,255,255,0.03)',
                  color: platform === p ? '#818cf8' : '#64748b', fontSize: 11, fontWeight: platform === p ? 700 : 500,
                }}
              >
                {p === '' ? '🌐 Todos' : p === 'android' ? '🤖 Android' : '🍎 iOS'}
              </button>
            ))}
          </div>
        </div>

        {/* Action */}
        <button
          onClick={() => { if (canCreate) onCreate({ name: name.trim(), description: desc.trim(), platform }) }}
          disabled={!canCreate}
          style={{
            width: '100%', padding: '10px 0', borderRadius: 8, border: 'none',
            background: canCreate ? 'linear-gradient(90deg, #6366f1, #818cf8)' : 'rgba(255,255,255,0.05)',
            color: canCreate ? '#fff' : '#475569', fontSize: 13, fontWeight: 700,
            cursor: canCreate ? 'pointer' : 'not-allowed',
            display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 7,
          }}
        >
          <Plus size={13} /> Crear Suite
        </button>
      </motion.div>
    </motion.div>
  )
}

// ── CaseRow — one draggable row per TestCase ──────────────────────────────────

interface CaseRowProps {
  tc:          TestCase
  suiteId:     string
  suiteName:   string
  index:       number
  dragging:    boolean
  dragOver:    boolean
  onDragStart(i: number): void
  onDragOver(e: DragEvent, i: number): void
  onDrop(i: number): void
  onDragEnd(): void
  onOpen(tc: TestCase): void
  onDelete(suiteId: string, caseId: string): void
  onExecute(tc: TestCase): void
}

function CaseRow({
  tc, suiteId, suiteName, index, dragging, dragOver,
  onDragStart, onDragOver, onDrop, onDragEnd,
  onOpen, onDelete, onExecute,
}: CaseRowProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const pb = platformBadge(tc.platform)

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  return (
    <div
      draggable
      onDragStart={() => onDragStart(index)}
      onDragOver={e => onDragOver(e, index)}
      onDrop={() => onDrop(index)}
      onDragEnd={onDragEnd}
      style={{
        display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px 8px 28px',
        borderRadius: 7, cursor: 'grab',
        background: dragOver ? 'rgba(99,102,241,0.08)' : dragging ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.02)',
        border: `1px solid ${dragOver ? 'rgba(99,102,241,0.35)' : 'rgba(255,255,255,0.05)'}`,
        opacity: dragging ? 0.45 : 1,
        transition: 'background 0.1s, border-color 0.1s',
        position: 'relative',
      }}
    >
      {/* Drag handle */}
      <GripVertical size={13} color="#334155" style={{ flexShrink: 0, cursor: 'grab' }} />

      {/* Case icon */}
      <div style={{
        width: 24, height: 24, borderRadius: 6, flexShrink: 0,
        background: 'rgba(99,102,241,0.1)', border: '1px solid rgba(99,102,241,0.18)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <CheckCircle2 size={12} color="#818cf8" />
      </div>

      {/* Name */}
      <button
        onClick={() => onOpen(tc)}
        style={{
          flex: 1, textAlign: 'left', background: 'none', border: 'none', cursor: 'pointer',
          color: '#e2e8f0', fontSize: 12, fontWeight: 600, padding: 0,
          minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}
      >
        {tc.name}
      </button>

      {/* Meta chips */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
        <span style={{
          fontSize: 10, color: pb.color, background: `${pb.color}18`,
          border: `1px solid ${pb.color}33`, borderRadius: 4, padding: '1px 5px',
        }}>
          {pb.label}
        </span>
        <span style={{ fontSize: 10, color: '#64748b' }}>
          {tc.stepCount}p · {langLabel(tc.lang)}
        </span>
        <span style={{ fontSize: 10, color: '#334155' }}>{fmtDate(tc.updatedAt)}</span>

        {/* Execute */}
        <button
          onClick={() => onExecute(tc)}
          title="Ejecutar caso"
          style={{
            width: 26, height: 26, borderRadius: 6, cursor: 'pointer',
            background: 'rgba(52,211,153,0.1)', border: '1px solid rgba(52,211,153,0.2)',
            color: '#34d399', display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >
          <Play size={10} />
        </button>

        {/* More menu */}
        <div ref={menuRef} style={{ position: 'relative' }}>
          <button
            onClick={() => setMenuOpen(p => !p)}
            style={{
              width: 26, height: 26, borderRadius: 6, cursor: 'pointer',
              background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)',
              color: '#64748b', display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <MoreHorizontal size={12} />
          </button>
          {menuOpen && (
            <div style={{
              position: 'absolute', right: 0, top: '100%', marginTop: 4, zIndex: 200,
              background: '#1e293b', border: '1px solid #334155', borderRadius: 8,
              padding: '4px 0', minWidth: 140,
              boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
            }}>
              <button
                onClick={() => { setMenuOpen(false); onOpen(tc) }}
                style={menuItemStyle}
              >
                <PencilLine size={12} /> Ver / Editar
              </button>
              <div style={{ height: 1, background: '#334155', margin: '3px 0' }} />
              <button
                onClick={() => { setMenuOpen(false); onDelete(suiteId, tc.id) }}
                style={{ ...menuItemStyle, color: '#f87171' }}
              >
                <Trash2 size={12} /> Eliminar caso
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

const menuItemStyle: React.CSSProperties = {
  width: '100%', padding: '7px 12px', background: 'none', border: 'none',
  cursor: 'pointer', color: '#94a3b8', fontSize: 12,
  display: 'flex', alignItems: 'center', gap: 8, textAlign: 'left',
}

// ── SuiteAccordion — one expandable block per TestSuite ───────────────────────

interface SuiteAccordionProps {
  suite:     TestSuite
  onDelete(id: string): void
  onDeleteCase(suiteId: string, caseId: string): void
  onOpenCase(tc: TestCase, suiteName: string): void
  onExecuteCase(tc: TestCase): void
  onExecuteSuite(suite: TestSuite): void
  onReorder(suiteId: string, newOrder: string[]): void
}

function SuiteAccordion({
  suite, onDelete, onDeleteCase, onOpenCase,
  onExecuteCase, onExecuteSuite, onReorder,
}: SuiteAccordionProps) {
  const [expanded, setExpanded] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [dragIdx, setDragIdx]   = useState<number | null>(null)
  const [dropIdx, setDropIdx]   = useState<number | null>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  const st         = suiteType(suite.name, suite.description)
  const pb         = platformBadge(suite.platform)
  const totalSteps = suite.testCases.reduce((s, c) => s + c.stepCount, 0)

  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) setMenuOpen(false)
    }
    document.addEventListener('mousedown', h)
    return () => document.removeEventListener('mousedown', h)
  }, [])

  // ── Drag-and-drop ────────────────────────────────────────────────────────

  const handleDragStart = (i: number) => setDragIdx(i)

  const handleDragOver = (e: DragEvent, i: number) => {
    e.preventDefault(); setDropIdx(i)
  }

  const handleDrop = (targetIdx: number) => {
    if (dragIdx === null || dragIdx === targetIdx) { setDragIdx(null); setDropIdx(null); return }
    const ids = suite.testCases.map(c => c.id)
    const [moved] = ids.splice(dragIdx, 1)
    ids.splice(targetIdx, 0, moved)
    onReorder(suite.id, ids)
    setDragIdx(null); setDropIdx(null)
  }

  const handleDragEnd = () => { setDragIdx(null); setDropIdx(null) }

  return (
    <div style={{
      background: '#0f172a', border: `1px solid ${expanded ? '#1e293b' : '#1a2236'}`,
      borderRadius: 10, overflow: 'hidden',
      boxShadow: expanded ? '0 4px 16px rgba(0,0,0,0.3)' : 'none',
      transition: 'box-shadow 0.2s',
    }}>
      {/* Suite header row */}
      <div
        style={{
          display: 'flex', alignItems: 'center', gap: 12,
          padding: '12px 16px', cursor: 'pointer',
          background: expanded ? 'rgba(99,102,241,0.04)' : 'transparent',
        }}
      >
        {/* Expand toggle */}
        <button
          onClick={() => setExpanded(p => !p)}
          style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#64748b', padding: 2, flexShrink: 0 }}
        >
          {expanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
        </button>

        {/* App icon — auto-resolved from package name */}
        <AppIcon pkg={suite.appPackage} appName={suite.appName} size={34} />

        {/* Name + meta */}
        <div style={{ flex: 1, minWidth: 0 }} onClick={() => setExpanded(p => !p)}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <span style={{ fontSize: 13, fontWeight: 700, color: '#f1f5f9' }}>{suite.name}</span>
            <span style={{
              fontSize: 9, fontWeight: 700, color: st.color, background: st.bg,
              borderRadius: 4, padding: '1px 5px',
            }}>{st.label}</span>
            {suite.appName && (
              <span style={{ fontSize: 11, color: '#475569' }}>{suite.appName}</span>
            )}
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 2 }}>
            <span style={{ fontSize: 10, color: '#64748b' }}>
              {suite.testCases.length} {suite.testCases.length === 1 ? 'caso' : 'casos'}
            </span>
            <span style={{ fontSize: 10, color: '#334155' }}>·</span>
            <span style={{ fontSize: 10, color: '#64748b' }}>{totalSteps} pasos</span>
            <span style={{ fontSize: 10, color: '#334155' }}>·</span>
            <span style={{ fontSize: 10, color: pb.color }}>{pb.label}</span>
            <span style={{ fontSize: 10, color: '#334155' }}>·</span>
            <span style={{ fontSize: 10, color: '#475569' }}>{fmtDate(suite.updatedAt)}</span>
          </div>
        </div>

        {/* Actions */}
        <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
          {/* Execute suite */}
          <button
            onClick={e => { e.stopPropagation(); onExecuteSuite(suite) }}
            title="Ejecutar todos los casos"
            style={{
              height: 30, padding: '0 12px', borderRadius: 7, cursor: 'pointer',
              background: 'rgba(52,211,153,0.1)', border: '1px solid rgba(52,211,153,0.2)',
              color: '#34d399', fontSize: 11, fontWeight: 600,
              display: 'flex', alignItems: 'center', gap: 5,
            }}
          >
            <Play size={10} /> Ejecutar Suite
          </button>

          {/* More */}
          <div ref={menuRef} style={{ position: 'relative' }}>
            <button
              onClick={e => { e.stopPropagation(); setMenuOpen(p => !p) }}
              style={{
                width: 30, height: 30, borderRadius: 7, cursor: 'pointer',
                background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)',
                color: '#64748b', display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}
            >
              <MoreHorizontal size={14} />
            </button>
            {menuOpen && (
              <div style={{
                position: 'absolute', right: 0, top: '100%', marginTop: 4, zIndex: 300,
                background: '#1e293b', border: '1px solid #334155', borderRadius: 8,
                padding: '4px 0', minWidth: 150,
                boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
              }}>
                <button
                  onClick={() => { setMenuOpen(false); onDelete(suite.id) }}
                  style={{ ...menuItemStyle, color: '#f87171' }}
                >
                  <Trash2 size={12} /> Eliminar suite
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Cases list */}
      <AnimatePresence>
        {expanded && (
          <motion.div
            key="cases"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
            style={{ overflow: 'hidden' }}
          >
            <div style={{
              padding: '4px 12px 12px',
              borderTop: '1px solid #1e293b',
              display: 'flex', flexDirection: 'column', gap: 4,
            }}>
              {suite.testCases.length === 0 ? (
                <div style={{
                  padding: '20px 16px', textAlign: 'center',
                  color: '#334155', fontSize: 12,
                }}>
                  Sin casos de prueba — graba un flujo y guárdalo en esta suite.
                </div>
              ) : (
                suite.testCases.map((tc, i) => (
                  <CaseRow
                    key={tc.id}
                    tc={tc}
                    suiteId={suite.id}
                    suiteName={suite.name}
                    index={i}
                    dragging={dragIdx === i}
                    dragOver={dropIdx === i}
                    onDragStart={handleDragStart}
                    onDragOver={handleDragOver}
                    onDrop={handleDrop}
                    onDragEnd={handleDragEnd}
                    onOpen={t => onOpenCase(t, suite.name)}
                    onDelete={onDeleteCase}
                    onExecute={onExecuteCase}
                  />
                ))
              )}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

// ── SuitesPage ────────────────────────────────────────────────────────────────

interface SuitesPageProps {
  onNavigate?: (page: string) => void
}

export default function SuitesPage({ onNavigate }: SuitesPageProps = {}) {
  const [suites,      setSuites]      = useState<TestSuite[]>([])
  const [search,      setSearch]      = useState('')
  const [pfFilter,    setPfFilter]    = useState<'all' | 'android' | 'ios'>('all')
  const [showCreate,  setShowCreate]  = useState(false)
  const [detailCase,  setDetailCase]  = useState<{ tc: TestCase; suiteName: string } | null>(null)
  const [execTarget,  setExecTarget]  = useState<TestSuite | null>(null)
  const [toast,       setToast]       = useState<string | null>(null)

  const confirm = useConfirmation()

  const showToast = useCallback((msg: string) => {
    setToast(msg); setTimeout(() => setToast(null), 3500)
  }, [])

  const reload = useCallback(() => setSuites(suiteService.getSuites()), [])

  useEffect(() => {
    reload()
    const events = ['qa:suite:created', 'qa:suite:updated', 'qa:suite:deleted', 'qa:case:created', 'qa:case:updated', 'qa:case:deleted']
    events.forEach(e => window.addEventListener(e, reload))
    return () => events.forEach(e => window.removeEventListener(e, reload))
  }, [reload])

  // ── Metrics ──────────────────────────────────────────────────────────────

  const metrics = useMemo(() => {
    let cases = 0, steps = 0
    for (const s of suites) {
      cases += s.testCases.length
      for (const c of s.testCases) steps += c.stepCount
    }
    const lastMod = suites.reduce<string>((latest, s) =>
      s.updatedAt > latest ? s.updatedAt : latest, '')
    return { suites: suites.length, cases, steps, lastMod }
  }, [suites])

  // ── Filtered list ─────────────────────────────────────────────────────────

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    return suites.filter(s => {
      if (pfFilter !== 'all' && s.platform && s.platform !== pfFilter) return false
      if (q && !s.name.toLowerCase().includes(q) && !s.appName.toLowerCase().includes(q)) return false
      return true
    })
  }, [suites, search, pfFilter])

  // ── Actions ───────────────────────────────────────────────────────────────

  const handleDeleteSuite = useCallback(async (id: string) => {
    const suite = suites.find(s => s.id === id)
    if (!suite) return
    const ok = await confirm({
      title: 'Eliminar Suite',
      description: `¿Eliminar "${suite.name}" y sus ${suite.testCases.length} caso(s)? Esta acción no se puede deshacer.`,
      type: 'delete',
      confirmText: 'Eliminar',
    })
    if (ok) { suiteService.deleteSuite(id); reload() }
  }, [suites, confirm, reload])

  const handleDeleteCase = useCallback(async (suiteId: string, caseId: string) => {
    const suite = suites.find(s => s.id === suiteId)
    const tc = suite?.testCases.find(c => c.id === caseId)
    if (!tc) return
    const ok = await confirm({
      title: 'Eliminar Caso',
      description: `¿Eliminar el caso "${tc.name}"? Esta acción no se puede deshacer.`,
      type: 'delete',
      confirmText: 'Eliminar',
    })
    if (ok) { suiteService.deleteCase(suiteId, caseId); reload() }
  }, [suites, confirm, reload])

  const handleExecuteSuite = useCallback((suite: TestSuite) => {
    setExecTarget(suite)
  }, [])

  const handleExecuteCase = useCallback((tc: TestCase) => {
    // Execute a single case by wrapping it in a one-case suite run
    const parentSuite = suites.find(s => s.id === tc.suiteId)
    if (!parentSuite) return
    executionTrackingService.runSuite({
      suiteId:     parentSuite.id,
      suiteName:   `${parentSuite.name} › ${tc.name}`,
      appName:     parentSuite.appName,
      appPackage:  parentSuite.appPackage,
      platform:    parentSuite.platform,
      environment: 'qa',
      country:     parentSuite.country,
      cases:       [{ caseId: tc.id, caseName: tc.name, stepsTotal: tc.stepCount }],
      onNavigateToDashboard: () => onNavigate?.('dashboard'),
    }).catch(console.warn)
    showToast(`Ejecutando caso "${tc.name}"`)
  }, [suites, onNavigate, showToast])

  const handleExecuteConfirm = useCallback(async (device: PhysicalDevice | null, environment: string) => {
    if (!execTarget) return
    await executionTrackingService.runSuite({
      suiteId:     execTarget.id,
      suiteName:   execTarget.name,
      appName:     execTarget.appName,
      appPackage:  execTarget.appPackage,
      platform:    execTarget.platform,
      device,
      environment,
      country:     execTarget.country,
      cases:       execTarget.testCases.map(c => ({ caseId: c.id, caseName: c.name, stepsTotal: c.stepCount })),
      onNavigateToDashboard: () => onNavigate?.('dashboard'),
    })
    setExecTarget(null)
    showToast(`Suite "${execTarget.name}" enviada al runner`)
  }, [execTarget, onNavigate, showToast])

  const handleReorder = useCallback((suiteId: string, newOrder: string[]) => {
    suiteService.reorderCases(suiteId, newOrder)
    reload()
  }, [reload])

  const handleCreate = useCallback((data: { name: string; description: string; platform: 'android' | 'ios' | '' }) => {
    suiteService.createSuite(data)
    setShowCreate(false)
    reload()
  }, [reload])

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <div style={{
      flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0,
      background: '#060d1a', padding: '0 24px 24px',
    }}>
      {/* Page header */}
      <div style={{
        paddingTop: 24, paddingBottom: 16,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
      }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 20, fontWeight: 800, color: '#f1f5f9' }}>Suites de Prueba</h1>
          <p style={{ margin: '4px 0 0', fontSize: 12, color: '#475569' }}>
            Organiza tus casos de prueba en suites para ejecutarlos en secuencia.
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          style={{
            display: 'flex', alignItems: 'center', gap: 7,
            padding: '9px 16px', borderRadius: 8, cursor: 'pointer',
            background: 'linear-gradient(90deg, #6366f1, #818cf8)',
            border: 'none', color: '#fff', fontSize: 13, fontWeight: 700,
          }}
        >
          <Plus size={14} /> Nueva Suite
        </button>
      </div>

      {/* Stats row */}
      <div style={{ display: 'flex', gap: 12, marginBottom: 20 }}>
        {[
          { label: 'Suites',        value: metrics.suites, color: '#818cf8', icon: Layers3 },
          { label: 'Casos',         value: metrics.cases,  color: '#34d399', icon: CheckCircle2 },
          { label: 'Pasos totales', value: metrics.steps,  color: '#60a5fa', icon: Package },
          { label: 'Última mod.',   value: metrics.lastMod ? fmtDate(metrics.lastMod) : '—', color: '#f59e0b', icon: Clock },
        ].map(({ label, value, color, icon: Icon }) => (
          <div key={label} style={{
            flex: 1, background: '#0f172a', border: '1px solid #1e293b',
            borderRadius: 10, padding: '14px 16px',
            display: 'flex', alignItems: 'center', gap: 12,
          }}>
            <div style={{
              width: 36, height: 36, borderRadius: 9,
              background: `${color}18`, border: `1px solid ${color}33`,
              display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
            }}>
              <Icon size={16} color={color} />
            </div>
            <div>
              <div style={{ fontSize: 18, fontWeight: 800, color: '#f1f5f9', lineHeight: 1 }}>
                {typeof value === 'number' ? value.toLocaleString() : value}
              </div>
              <div style={{ fontSize: 10, color: '#475569', marginTop: 2 }}>{label}</div>
            </div>
          </div>
        ))}
      </div>

      {/* Search + filters */}
      <div style={{ display: 'flex', gap: 10, marginBottom: 16, alignItems: 'center' }}>
        <div style={{ flex: 1, position: 'relative' }}>
          <Search size={13} color="#475569" style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }} />
          <input
            value={search} onChange={e => setSearch(e.target.value)}
            placeholder="Buscar suite por nombre o app…"
            style={{
              width: '100%', background: '#0f172a', border: '1px solid #1e293b',
              borderRadius: 8, color: '#e2e8f0', padding: '8px 10px 8px 30px',
              fontSize: 12, boxSizing: 'border-box', outline: 'none',
            }}
          />
          {search && (
            <button
              onClick={() => setSearch('')}
              style={{ position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%)', background: 'none', border: 'none', cursor: 'pointer', color: '#475569', padding: 2 }}
            >
              <X size={12} />
            </button>
          )}
        </div>
        <div style={{ display: 'flex', gap: 6 }}>
          {(['all', 'android', 'ios'] as const).map(p => (
            <button
              key={p}
              onClick={() => setPfFilter(p)}
              style={{
                padding: '7px 12px', borderRadius: 7, cursor: 'pointer', fontSize: 11, fontWeight: pfFilter === p ? 700 : 500,
                border: `1px solid ${pfFilter === p ? '#6366f1' : 'rgba(255,255,255,0.08)'}`,
                background: pfFilter === p ? 'rgba(99,102,241,0.12)' : 'rgba(255,255,255,0.03)',
                color: pfFilter === p ? '#818cf8' : '#64748b',
              }}
            >
              {p === 'all' ? 'Todos' : p === 'android' ? '🤖 Android' : '🍎 iOS'}
            </button>
          ))}
        </div>
      </div>

      {/* Suite list */}
      <div style={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
        {filtered.length === 0 ? (
          <div style={{
            flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center',
            justifyContent: 'center', gap: 12, padding: 48,
          }}>
            <div style={{
              width: 56, height: 56, borderRadius: 14,
              background: 'rgba(99,102,241,0.1)', border: '1px solid rgba(99,102,241,0.2)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <AlertCircle size={24} color="#6366f1" />
            </div>
            <div style={{ textAlign: 'center' }}>
              <p style={{ color: '#e2e8f0', fontWeight: 700, fontSize: 15, margin: '0 0 4px' }}>
                {suites.length === 0 ? 'No hay suites aún' : 'Sin resultados'}
              </p>
              <p style={{ color: '#475569', fontSize: 12, margin: 0 }}>
                {suites.length === 0
                  ? 'Crea una suite o graba un flujo en Record Studio y guárdalo aquí.'
                  : 'Intenta con otro término de búsqueda o filtro.'}
              </p>
            </div>
            {suites.length === 0 && (
              <button
                onClick={() => setShowCreate(true)}
                style={{
                  padding: '9px 18px', borderRadius: 8, cursor: 'pointer',
                  background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.3)',
                  color: '#818cf8', fontSize: 13, fontWeight: 600,
                  display: 'flex', alignItems: 'center', gap: 6,
                }}
              >
                <Plus size={14} /> Crear primera Suite
              </button>
            )}
          </div>
        ) : (
          filtered.map(suite => (
            <SuiteAccordion
              key={suite.id}
              suite={suite}
              onDelete={handleDeleteSuite}
              onDeleteCase={handleDeleteCase}
              onOpenCase={(tc, name) => setDetailCase({ tc, suiteName: name })}
              onExecuteCase={handleExecuteCase}
              onExecuteSuite={handleExecuteSuite}
              onReorder={handleReorder}
            />
          ))
        )}
      </div>

      {/* Modals */}
      <AnimatePresence>
        {showCreate && (
          <CreateSuiteModal
            key="create-modal"
            onClose={() => setShowCreate(false)}
            onCreate={handleCreate}
          />
        )}
        {detailCase && (
          <CaseDetailModal
            key="detail-modal"
            tc={detailCase.tc}
            suiteName={detailCase.suiteName}
            onClose={() => setDetailCase(null)}
            onDelete={() => {
              handleDeleteCase(detailCase.tc.suiteId, detailCase.tc.id)
              setDetailCase(null)
            }}
          />
        )}
        {execTarget && (
          <ExecuteSuiteModal
            key="exec-modal"
            suite={execTarget}
            onClose={() => setExecTarget(null)}
            onExecute={handleExecuteConfirm}
          />
        )}
      </AnimatePresence>

      {/* Toast */}
      <AnimatePresence>
        {toast && (
          <motion.div
            key="toast"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 20 }}
            style={{
              position: 'fixed', bottom: 28, right: 28, zIndex: 999,
              background: '#1e293b', border: '1px solid rgba(52,211,153,0.35)',
              borderRadius: 10, padding: '10px 18px',
              fontSize: 12, fontWeight: 600, color: '#34d399',
              boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
              display: 'flex', alignItems: 'center', gap: 8,
            }}
          >
            <Zap size={13} /> {toast}
          </motion.div>
        )}
      </AnimatePresence>

    </div>
  )
}
