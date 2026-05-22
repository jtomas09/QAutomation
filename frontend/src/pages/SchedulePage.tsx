import React, { useState, useEffect, useCallback } from 'react'
import {
  CalendarClock, Plus, Play, Pencil, Trash2, Clock,
  RefreshCw, CheckCircle2, XCircle, AlertCircle, ToggleLeft, ToggleRight,
} from 'lucide-react'

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

interface ScheduledJob {
  id:             string
  name:           string
  suite:          string
  testClass?:     string
  device:         string
  env:            string
  country:        string
  videoEnabled:   boolean
  cronExpression: string
  enabled:        boolean
  lastRun?:       string
  nextRun?:       string
  lastStatus?:    string
}

type JobDraft = Omit<ScheduledJob, 'id'>

const emptyDraft = (): JobDraft => ({
  name:           '',
  suite:          'alimentos',
  testClass:      '',
  device:         'Galaxy A56 5G',
  env:            'QA',
  country:        'mexico',
  videoEnabled:   false,
  cronExpression: '0 8 * * 1-5',
  enabled:        true,
})

const CRON_PRESETS = [
  { label: 'Cada día hábil 8 AM',   value: '0 8 * * 1-5' },
  { label: 'Lunes y Jueves 9 AM',   value: '0 9 * * 1,4' },
  { label: 'Cada hora',             value: '0 * * * *'   },
  { label: 'Cada 2 hrs',            value: '0 */2 * * *' },
  { label: 'Cada 30 min',           value: '*/30 * * * *' },
  { label: 'Domingos 6 AM',         value: '0 6 * * 0'   },
]

const SUITES = ['alimentos', 'smoke', 'flujo-completo', 'asientos', 'checkout', 'carrito', 'RunAllTests']

const ALIMENTOS_SUBCATEGORIES = [
  { label: 'Todas',            value: '' },
  { label: 'Menú Atmósfera',   value: 'MenuAtmosfera' },
  { label: 'Menú Tradicional', value: 'MenuTradicional' },
  { label: 'Menú VIP',         value: 'MenuVIP' },
  { label: 'Coffee Tree',      value: 'MenuCoffeTree' },
  { label: 'Mi Cine',          value: 'MenuMiCine' },
]
const ENVS   = ['QA', 'PROD', 'STG']
const COUNTRIES = [
  { id: 'mexico',    label: 'México'    },
  { id: 'argentina', label: 'Argentina' },
  { id: 'chile',     label: 'Chile'     },
  { id: 'colombia',  label: 'Colombia'  },
]

// ── helpers ────────────────────────────────────────────────────────────────

function statusBadge(status?: string) {
  if (!status) return null
  const map: Record<string, { color: string; bg: string; icon: React.ReactNode; label: string }> = {
    PENDING:   { color: '#8b949e', bg: 'rgba(139,148,158,0.12)', icon: <Clock size={11} />,         label: 'PENDIENTE'  },
    TRIGGERED: { color: '#818cf8', bg: 'rgba(129,140,248,0.12)', icon: <Play size={11} />,          label: 'DISPARADO'  },
    ERROR:     { color: '#f85149', bg: 'rgba(248,81,73,0.12)',   icon: <XCircle size={11} />,       label: 'ERROR'      },
    PASSED:    { color: '#2ea043', bg: 'rgba(46,160,67,0.12)',   icon: <CheckCircle2 size={11} />,  label: 'PASADO'     },
    RUNNING:   { color: '#f97316', bg: 'rgba(249,115,22,0.12)', icon: <RefreshCw size={11} className="animate-spin" />, label: 'EJECUTANDO' },
  }
  const m = map[status] ?? map['PENDING']
  return (
    <span style={{
      display: 'inline-flex', alignItems: 'center', gap: 4,
      fontSize: 10, fontWeight: 700, color: m.color,
      background: m.bg, padding: '2px 8px', borderRadius: 6,
    }}>
      {m.icon} {m.label}
    </span>
  )
}

function formatInstant(iso?: string) {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString('es-MX', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    })
  } catch { return iso }
}

// ── components ────────────────────────────────────────────────────────────

function JobCard({ job, onEdit, onDelete, onRunNow }: {
  job: ScheduledJob
  onEdit: () => void
  onDelete: () => void
  onRunNow: () => void
}) {
  const [confirmDelete, setConfirmDelete] = useState(false)

  return (
    <div style={{
      background: 'var(--terminal-bg)',
      border: '1px solid var(--btn-border)',
      borderRadius: 12, padding: '16px 20px',
      display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap',
    }}>
      {/* Enabled dot */}
      <div style={{
        width: 8, height: 8, borderRadius: '50%', flexShrink: 0,
        background: job.enabled ? '#2ea043' : '#484f58',
        boxShadow: job.enabled ? '0 0 6px #2ea043' : 'none',
      }} />

      {/* Info */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4, flexWrap: 'wrap' }}>
          <span style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-pri)' }}>{job.name}</span>
          {statusBadge(job.lastStatus)}
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px 14px', fontSize: 12, color: 'var(--text-dim)' }}>
          <span>Suite: <b style={{ color: 'var(--text-sec)' }}>{job.suite}</b></span>
          {job.testClass && (
            <span>Cat: <b style={{ color: '#10b981' }}>{job.testClass}</b></span>
          )}
          <span>Env: <b style={{ color: 'var(--text-sec)' }}>{job.env}</b></span>
          <span>País: <b style={{ color: 'var(--text-sec)' }}>{job.country}</b></span>
          <span style={{ fontFamily: 'monospace', color: '#8b5cf6' }}>{job.cronExpression}</span>
        </div>
        <div style={{ display: 'flex', gap: 14, marginTop: 4, fontSize: 11, color: '#484f58' }}>
          <span>Último: {formatInstant(job.lastRun)}</span>
        </div>
      </div>

      {/* Actions */}
      <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
        <ActionBtn
          icon={<Play size={13} />}
          title="Ejecutar ahora"
          color="#10b981"
          onClick={onRunNow}
        />
        <ActionBtn
          icon={<Pencil size={13} />}
          title="Editar"
          color="#818cf8"
          onClick={onEdit}
        />
        {confirmDelete ? (
          <>
            <ActionBtn icon={<CheckCircle2 size={13} />} title="Confirmar eliminar" color="#f85149" onClick={onDelete} />
            <ActionBtn icon={<XCircle size={13} />} title="Cancelar" color="#484f58" onClick={() => setConfirmDelete(false)} />
          </>
        ) : (
          <ActionBtn icon={<Trash2 size={13} />} title="Eliminar" color="#484f58" onClick={() => setConfirmDelete(true)} />
        )}
      </div>
    </div>
  )
}

function ActionBtn({ icon, title, color, onClick }: {
  icon: React.ReactNode; title: string; color: string; onClick: () => void
}) {
  const [hov, setHov] = useState(false)
  return (
    <button
      title={title}
      onClick={onClick}
      onMouseEnter={() => setHov(true)}
      onMouseLeave={() => setHov(false)}
      style={{
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        width: 30, height: 30, borderRadius: 8, border: '1px solid var(--btn-border)',
        background: hov ? `${color}22` : 'transparent',
        color: hov ? color : 'var(--text-dim)',
        cursor: 'pointer', transition: 'all 0.15s',
      }}
    >
      {icon}
    </button>
  )
}

function EmptyState({ onAdd }: { onAdd: () => void }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center',
      justifyContent: 'center', padding: '64px 0', gap: 12,
    }}>
      <CalendarClock size={40} style={{ color: '#30363d', opacity: 0.6 }} />
      <div style={{ fontSize: 15, fontWeight: 700, color: 'var(--text-dim)' }}>
        Sin ejecuciones programadas
      </div>
      <div style={{ fontSize: 13, color: '#484f58', textAlign: 'center', maxWidth: 300 }}>
        Crea una programación para ejecutar suites automáticamente con una expresión cron.
      </div>
      <button
        onClick={onAdd}
        style={{
          marginTop: 8, display: 'flex', alignItems: 'center', gap: 6,
          background: '#8b5cf6', color: '#fff', border: 'none',
          borderRadius: 8, padding: '8px 16px', fontSize: 13, fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        <Plus size={14} /> Nueva programación
      </button>
    </div>
  )
}

function JobFormModal({ draft, editingId, saving, error, onChange, onSave, onCancel }: {
  draft: JobDraft
  editingId: string | null
  saving: boolean
  error: string
  onChange: (partial: Partial<JobDraft>) => void
  onSave: () => void
  onCancel: () => void
}) {
  const inp: React.CSSProperties = {
    width: '100%', background: '#0b0f14', border: '1px solid var(--btn-border)',
    borderRadius: 8, color: 'var(--text-pri)', fontSize: 13,
    padding: '8px 10px', outline: 'none', boxSizing: 'border-box',
  }
  const lbl: React.CSSProperties = {
    fontSize: 11, fontWeight: 700, color: 'var(--text-dim)',
    textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4, display: 'block',
  }
  const row: React.CSSProperties = { display: 'flex', flexDirection: 'column', gap: 4 }

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 100,
      background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(4px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24,
    }}>
      <div style={{
        background: 'var(--sidebar-bg)', border: '1px solid var(--sidebar-border)',
        borderRadius: 16, padding: 28, width: '100%', maxWidth: 520,
        maxHeight: '90vh', overflowY: 'auto',
      }}>
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 24 }}>
          <CalendarClock size={18} style={{ color: '#8b5cf6' }} />
          <span style={{ fontSize: 16, fontWeight: 800, color: 'var(--text-pri)' }}>
            {editingId ? 'Editar programación' : 'Nueva programación'}
          </span>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Name */}
          <div style={row}>
            <label style={lbl}>Nombre</label>
            <input style={inp} value={draft.name}
              placeholder="Ej. Smoke diario producción"
              onChange={e => onChange({ name: e.target.value })} />
          </div>

          {/* Suite + Env */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div style={row}>
              <label style={lbl}>Suite</label>
              <select style={{ ...inp, cursor: 'pointer' }}
                value={draft.suite}
                onChange={e => onChange({ suite: e.target.value, testClass: '' })}>
                {SUITES.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
            <div style={row}>
              <label style={lbl}>Ambiente</label>
              <select style={{ ...inp, cursor: 'pointer' }}
                value={draft.env} onChange={e => onChange({ env: e.target.value })}>
                {ENVS.map(e => <option key={e} value={e}>{e}</option>)}
              </select>
            </div>
          </div>

          {/* Alimentos subcategory — visible only when suite === 'alimentos' */}
          {draft.suite === 'alimentos' && (
            <div style={row}>
              <label style={lbl}>Categoría</label>
              <select style={{ ...inp, cursor: 'pointer' }}
                value={draft.testClass ?? ''}
                onChange={e => onChange({ testClass: e.target.value })}>
                {ALIMENTOS_SUBCATEGORIES.map(s => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
            </div>
          )}

          {/* Country + Device */}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div style={row}>
              <label style={lbl}>País</label>
              <select style={{ ...inp, cursor: 'pointer' }}
                value={draft.country} onChange={e => onChange({ country: e.target.value })}>
                {COUNTRIES.map(c => <option key={c.id} value={c.id}>{c.label}</option>)}
              </select>
            </div>
            <div style={row}>
              <label style={lbl}>Dispositivo</label>
              <input style={inp} value={draft.device}
                placeholder="Galaxy A56 5G"
                onChange={e => onChange({ device: e.target.value })} />
            </div>
          </div>

          {/* Cron expression */}
          <div style={row}>
            <label style={lbl}>Expresión Cron</label>
            <input style={{ ...inp, fontFamily: 'monospace', color: '#8b5cf6' }}
              value={draft.cronExpression}
              placeholder="0 8 * * 1-5"
              onChange={e => onChange({ cronExpression: e.target.value })} />
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
              {CRON_PRESETS.map(p => (
                <button
                  key={p.value}
                  onClick={() => onChange({ cronExpression: p.value })}
                  style={{
                    fontSize: 11, color: draft.cronExpression === p.value ? '#8b5cf6' : 'var(--text-dim)',
                    background: draft.cronExpression === p.value ? 'rgba(139,92,246,0.15)' : 'rgba(255,255,255,0.04)',
                    border: `1px solid ${draft.cronExpression === p.value ? 'rgba(139,92,246,0.4)' : 'var(--btn-border)'}`,
                    borderRadius: 6, padding: '3px 8px', cursor: 'pointer',
                  }}
                >{p.label}</button>
              ))}
            </div>
          </div>

          {/* Toggles */}
          <div style={{ display: 'flex', gap: 20 }}>
            <ToggleRow
              label="Grabar video"
              checked={draft.videoEnabled}
              onChange={v => onChange({ videoEnabled: v })}
            />
            <ToggleRow
              label="Habilitado"
              checked={draft.enabled}
              onChange={v => onChange({ enabled: v })}
            />
          </div>
        </div>

        {error && (
          <div style={{
            marginTop: 14, fontSize: 12, color: '#f85149',
            background: 'rgba(248,81,73,0.08)', borderRadius: 8, padding: '8px 12px',
          }}>
            {error}
          </div>
        )}

        {/* Footer */}
        <div style={{ display: 'flex', gap: 10, marginTop: 24, justifyContent: 'flex-end' }}>
          <button
            onClick={onCancel}
            style={{
              padding: '8px 18px', borderRadius: 8, fontSize: 13, fontWeight: 600,
              background: 'transparent', border: '1px solid var(--btn-border)',
              color: 'var(--text-dim)', cursor: 'pointer',
            }}
          >Cancelar</button>
          <button
            onClick={onSave}
            disabled={saving || !draft.name || !draft.cronExpression}
            style={{
              padding: '8px 18px', borderRadius: 8, fontSize: 13, fontWeight: 600,
              background: saving || !draft.name ? '#30363d' : '#8b5cf6',
              border: 'none', color: '#fff', cursor: saving ? 'not-allowed' : 'pointer',
            }}
          >
            {saving ? 'Guardando…' : editingId ? 'Actualizar' : 'Crear programación'}
          </button>
        </div>
      </div>
    </div>
  )
}

function ToggleRow({ label, checked, onChange }: {
  label: string; checked: boolean; onChange: (v: boolean) => void
}) {
  return (
    <div
      style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', userSelect: 'none' }}
      onClick={() => onChange(!checked)}
    >
      {checked
        ? <ToggleRight size={20} style={{ color: '#8b5cf6' }} />
        : <ToggleLeft  size={20} style={{ color: '#484f58' }} />}
      <span style={{ fontSize: 13, color: 'var(--text-sec)' }}>{label}</span>
    </div>
  )
}

// ── main page ────────────────────────────────────────────────────────────

export default function SchedulePage() {
  const [jobs,      setJobs]      = useState<ScheduledJob[]>([])
  const [loading,   setLoading]   = useState(true)
  const [showForm,  setShowForm]  = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [draft,     setDraft]     = useState<JobDraft>(emptyDraft())
  const [saving,    setSaving]    = useState(false)
  const [saveError, setSaveError] = useState('')

  const fetchJobs = useCallback(async () => {
    try {
      const r = await fetch(`${API_URL}/api/scheduler/jobs`)
      if (r.ok) setJobs(await r.json())
    } catch { /* noop */ } finally { setLoading(false) }
  }, [])

  useEffect(() => { fetchJobs() }, [fetchJobs])

  async function save() {
    setSaving(true)
    setSaveError('')
    try {
      const url = editingId
        ? `${API_URL}/api/scheduler/jobs/${editingId}`
        : `${API_URL}/api/scheduler/jobs`
      const body = editingId ? { ...draft } : draft
      const r = await fetch(url, {
        method: editingId ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      if (!r.ok) throw new Error(`Error del servidor: HTTP ${r.status}`)
      closeForm()
      await fetchJobs()
    } catch (e: any) {
      setSaveError(e.message ?? 'Error desconocido')
    } finally {
      setSaving(false)
    }
  }

  async function deleteJob(id: string) {
    await fetch(`${API_URL}/api/scheduler/jobs/${id}`, { method: 'DELETE' })
    await fetchJobs()
  }

  async function runNow(id: string) {
    await fetch(`${API_URL}/api/scheduler/jobs/${id}/run`, { method: 'POST' })
    setTimeout(fetchJobs, 500)
  }

  function openEdit(job: ScheduledJob) {
    setEditingId(job.id)
    setDraft({ name: job.name, suite: job.suite, testClass: job.testClass ?? '',
               device: job.device, env: job.env,
               country: job.country, videoEnabled: job.videoEnabled,
               cronExpression: job.cronExpression, enabled: job.enabled })
    setSaveError('')
    setShowForm(true)
  }

  function openCreate() {
    setEditingId(null)
    setDraft(emptyDraft())
    setSaveError('')
    setShowForm(true)
  }

  function closeForm() {
    setShowForm(false)
    setEditingId(null)
  }

  return (
    <div style={{ padding: 28, maxWidth: 900 }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 28 }}>
        <div style={{
          width: 40, height: 40, borderRadius: 12,
          background: 'rgba(139,92,246,0.15)', border: '1px solid rgba(139,92,246,0.3)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
        }}>
          <CalendarClock size={20} style={{ color: '#8b5cf6' }} />
        </div>
        <div>
          <div style={{ fontSize: 20, fontWeight: 800, color: 'var(--text-pri)' }}>
            Programación de Ejecuciones
          </div>
          <div style={{ fontSize: 12, color: 'var(--text-dim)', marginTop: 2 }}>
            Ejecuta suites automáticamente con expresiones cron
          </div>
        </div>
        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 10 }}>
          <button
            onClick={fetchJobs}
            title="Actualizar"
            style={{
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              width: 34, height: 34, borderRadius: 8,
              background: 'transparent', border: '1px solid var(--btn-border)',
              color: 'var(--text-dim)', cursor: 'pointer',
            }}
          >
            <RefreshCw size={14} />
          </button>
          <button
            onClick={openCreate}
            style={{
              display: 'flex', alignItems: 'center', gap: 6,
              background: '#8b5cf6', color: '#fff',
              border: 'none', borderRadius: 8,
              padding: '8px 16px', fontSize: 13, fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            <Plus size={14} /> Nueva programación
          </button>
        </div>
      </div>

      {/* Help strip */}
      <div style={{
        background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.2)',
        borderRadius: 10, padding: '10px 14px', marginBottom: 20,
        fontSize: 12, color: 'var(--text-dim)',
        display: 'flex', alignItems: 'center', gap: 8,
      }}>
        <AlertCircle size={13} style={{ color: '#8b5cf6', flexShrink: 0 }} />
        <span>
          Usa expresiones cron estándar de 5 campos (minuto hora día mes díaSemana).
          Ejemplos: <code style={{ color: '#8b5cf6' }}>0 8 * * 1-5</code> = lunes a viernes 8AM, &nbsp;
          <code style={{ color: '#8b5cf6' }}>0 */2 * * *</code> = cada 2 horas.
        </span>
      </div>

      {/* Content */}
      {loading ? (
        <div style={{ color: 'var(--text-dim)', fontSize: 14, padding: '40px 0', textAlign: 'center' }}>
          Cargando programaciones…
        </div>
      ) : jobs.length === 0 ? (
        <EmptyState onAdd={openCreate} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {jobs.map(job => (
            <JobCard
              key={job.id}
              job={job}
              onEdit={() => openEdit(job)}
              onDelete={() => deleteJob(job.id)}
              onRunNow={() => runNow(job.id)}
            />
          ))}
        </div>
      )}

      {/* Form modal */}
      {showForm && (
        <JobFormModal
          draft={draft}
          editingId={editingId}
          saving={saving}
          error={saveError}
          onChange={partial => setDraft(prev => ({ ...prev, ...partial }))}
          onSave={save}
          onCancel={closeForm}
        />
      )}
    </div>
  )
}
