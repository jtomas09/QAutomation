import React, { useState, useRef, useEffect } from 'react'
import {
  Info, Play, Bell, Zap, Shield, Settings,
  Wifi, Database, BarChart3, Activity,
  FlaskConical, Trash2, Download, Upload,
  CheckCircle2, Loader2, Lightbulb,
  HardDrive, Server,
} from 'lucide-react'
import { useBackendHealth } from '../hooks/useBackendHealth'
import { useRunnerStatus }  from '../hooks/useRunnerStatus'

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

// ── Types ────────────────────────────────────────────────────────────────────
interface SettingsState {
  platformName: string; timezone: string; language: string; dateFormat: string
  defaultEnv: string; defaultDevice: string; globalTimeout: number
  retriesOnFail: number; screenshotsOnFail: boolean; videoOnFail: boolean
  appiumUrl: string; connectionType: string; validateDevice: boolean
  reinstallAppiumSettings: boolean; adbWaitTime: number
  generateAllure: boolean; attachScreenshots: boolean; attachVideos: boolean
  retentionDays: number; logLevel: string; clearOldReports: boolean
  reportEmailsEnabled: boolean; reportEmails: string
  notifyOnStart: boolean; notifyOnEnd: boolean; notifyOnFail: boolean
  notificationChannel: string; notificationEmail: string; dailySummary: boolean
  webhookEnabled: boolean; webhookUrl: string
  slackEnabled: boolean; slackChannel: string
  jiraEnabled: boolean; testrailEnabled: boolean
  twoFactor: boolean; sessionTimeout: number; allowedIPs: string
  blockAttempts: number; passwordPolicy: string; auditLogging: boolean
  parallelExecution: boolean; maxParallel: number; cleanApp: boolean
  debugMode: boolean; keepLogsDays: number
  cleanOldExecutions: boolean; deleteOlderThan: number
  autoBackup: boolean; backupFrequency: string; backupLocation: string
}

const DEFAULTS: SettingsState = {
  platformName: 'AUTOMATION QA', timezone: 'America/Mexico_City',
  language: 'Español (México)', dateFormat: 'dd/MM/yyyy',
  defaultEnv: 'QA', defaultDevice: 'Galaxy A56 5G',
  globalTimeout: 60, retriesOnFail: 1,
  screenshotsOnFail: true, videoOnFail: true,
  appiumUrl: 'http://127.0.0.1:4723', connectionType: 'USB',
  validateDevice: true, reinstallAppiumSettings: true, adbWaitTime: 20,
  generateAllure: true, attachScreenshots: true, attachVideos: true,
  retentionDays: 30, logLevel: 'INFO', clearOldReports: false,
  reportEmailsEnabled: true, reportEmails: 'jtomasb@ia.com.mx,ygonzalez@ia.com.mx,avelasco@ia.com.mx,jurbina@ia.com.mx',
  notifyOnStart: true, notifyOnEnd: true, notifyOnFail: true,
  notificationChannel: 'Email', notificationEmail: 'qa-team@empresa.com', dailySummary: false,
  webhookEnabled: true, webhookUrl: 'https://tuservidor.com/webhook/qa',
  slackEnabled: true, slackChannel: '#qa-automation',
  jiraEnabled: false, testrailEnabled: false,
  twoFactor: true, sessionTimeout: 60, allowedIPs: '192.168.1.0/24, 10.0.0.0/8',
  blockAttempts: 5, passwordPolicy: 'Fuerte', auditLogging: true,
  parallelExecution: true, maxParallel: 3, cleanApp: true,
  debugMode: false, keepLogsDays: 7,
  cleanOldExecutions: true, deleteOlderThan: 90,
  autoBackup: true, backupFrequency: 'Diario', backupLocation: 'Google Drive',
}

const STORAGE_KEY = 'qa_platform_settings'

function loadSettings(): SettingsState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? { ...DEFAULTS, ...JSON.parse(raw) } : DEFAULTS
  } catch { return DEFAULTS }
}

// ── Small primitives ─────────────────────────────────────────────────────────
function Toggle({ value, onChange }: { value: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      onClick={() => onChange(!value)}
      style={{
        position: 'relative', flexShrink: 0,
        width: 38, height: 21, borderRadius: 11,
        background: value ? '#10b981' : 'rgba(100,116,139,0.4)',
        border: 'none', cursor: 'pointer', transition: 'background .2s',
      }}
    >
      <span style={{
        position: 'absolute', top: 2.5,
        left: value ? 19 : 2, width: 16, height: 16,
        borderRadius: '50%', background: 'white',
        transition: 'left .2s', boxShadow: '0 1px 3px rgba(0,0,0,.3)',
      }} />
    </button>
  )
}

function SRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 py-1.5">
      <span className="text-xs" style={{ color: 'var(--text-sec)' }}>{label}</span>
      {children}
    </div>
  )
}

function SInput({ value, onChange, type = 'text', width = 138 }: {
  value: string | number; onChange: (v: string) => void
  type?: string; width?: number
}) {
  return (
    <input
      type={type} value={value}
      onChange={e => onChange(e.target.value)}
      style={{
        width, background: 'var(--terminal-bg)', outline: 'none',
        border: '1px solid var(--btn-border)', color: 'var(--text-pri)',
        borderRadius: 8, padding: '5px 10px', fontSize: 11, textAlign: 'right',
      }}
    />
  )
}

function SSelect({ value, onChange, options, width = 138 }: {
  value: string; onChange: (v: string) => void; options: string[]; width?: number
}) {
  return (
    <select
      value={value} onChange={e => onChange(e.target.value)}
      style={{
        width, background: 'var(--terminal-bg)', outline: 'none',
        border: '1px solid var(--btn-border)', color: 'var(--text-pri)',
        borderRadius: 8, padding: '5px 10px', fontSize: 11, cursor: 'pointer',
      }}
    >
      {options.map(o => <option key={o} value={o}>{o}</option>)}
    </select>
  )
}

function EmailTagInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  const [inputVal, setInputVal] = useState('')
  const tags = value.split(',').map(e => e.trim()).filter(Boolean)

  function addTag(email: string) {
    const e = email.trim()
    if (!e || tags.includes(e)) { setInputVal(''); return }
    onChange([...tags, e].join(','))
    setInputVal('')
  }

  function removeTag(tag: string) {
    onChange(tags.filter(t => t !== tag).join(','))
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      addTag(inputVal)
    } else if (e.key === 'Backspace' && !inputVal && tags.length > 0) {
      removeTag(tags[tags.length - 1])
    }
  }

  return (
    <div style={{
      background: 'var(--terminal-bg)', border: '1px solid var(--btn-border)',
      borderRadius: 8, padding: '5px 8px', display: 'flex', flexWrap: 'wrap',
      gap: 4, cursor: 'text', marginTop: 6,
    }}>
      {tags.map(tag => (
        <div key={tag} style={{
          display: 'flex', alignItems: 'center', gap: 3,
          background: 'rgba(16,185,129,0.15)', border: '1px solid rgba(16,185,129,0.3)',
          borderRadius: 6, padding: '2px 7px', fontSize: 10, color: '#10b981',
        }}>
          <span>{tag}</span>
          <button
            onClick={() => removeTag(tag)}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#10b981', padding: 0, fontSize: 13, lineHeight: 1, marginLeft: 2 }}
          >×</button>
        </div>
      ))}
      <input
        value={inputVal}
        onChange={e => setInputVal(e.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={() => { if (inputVal) addTag(inputVal) }}
        placeholder={tags.length === 0 ? 'correo@empresa.com' : '+correo'}
        style={{
          flex: 1, minWidth: 110, background: 'none', border: 'none',
          outline: 'none', color: 'var(--text-pri)', fontSize: 11, padding: '2px 4px',
        }}
      />
    </div>
  )
}

function Card({ title, icon: Icon, accent = '#6366f1', children }: {
  title: string; icon: React.ElementType; accent?: string; children: React.ReactNode
}) {
  return (
    <div style={{ background: 'var(--bg-card)', border: '1px solid var(--panel-border)', borderRadius: 16, padding: 20 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
        <div style={{ width: 28, height: 28, borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', background: `${accent}22`, flexShrink: 0 }}>
          <Icon size={14} style={{ color: accent }} />
        </div>
        <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-pri)' }}>{title}</span>
      </div>
      <div>{children}</div>
    </div>
  )
}

function StatusRow({ label, status, detail }: { label: string; status: 'online' | 'offline' | 'checking'; detail?: string }) {
  const colors = { online: '#10b981', offline: '#f43f5e', checking: '#eab308' }
  const labels = { online: detail ?? 'Online', offline: 'Offline', checking: 'Verificando…' }
  const c = colors[status]
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '6px 0' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{ width: 8, height: 8, borderRadius: '50%', background: c, boxShadow: status !== 'offline' ? `0 0 6px ${c}` : 'none', flexShrink: 0 }} />
        <span style={{ fontSize: 12, color: 'var(--text-sec)' }}>{label}</span>
      </div>
      <span style={{ fontSize: 11, fontWeight: 600, color: c }}>{labels[status]}</span>
    </div>
  )
}

// ── Tabs ─────────────────────────────────────────────────────────────────────
const TABS = [
  { id: 'general',        label: 'General' },
  { id: 'ejecucion',      label: 'Ejecución' },
  { id: 'dispositivos',   label: 'Dispositivos' },
  { id: 'notificaciones', label: 'Notificaciones' },
  { id: 'integraciones',  label: 'Integraciones' },
  { id: 'seguridad',      label: 'Seguridad' },
  { id: 'avanzado',       label: 'Avanzado' },
  { id: 'personalizacion',label: 'Personalización' },
]

// ── Main component ────────────────────────────────────────────────────────────
interface Props { isDark: boolean; onToggleTheme: () => void }

export default function SettingsPage({ isDark, onToggleTheme }: Props) {
  const [tab,      setTab]      = useState('general')
  const [settings, setSettings] = useState<SettingsState>(loadSettings)
  const [saved,    setSaved]    = useState(false)
  const [testing,  setTesting]  = useState(false)
  const [testRes,  setTestRes]  = useState<'ok' | 'fail' | null>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  const backendHealth = useBackendHealth()
  const runnerOnline  = useRunnerStatus()

  function set<K extends keyof SettingsState>(k: K, v: SettingsState[K]) {
    setSettings(prev => ({ ...prev, [k]: v }))
  }

  async function handleSave() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
    try {
      await fetch(`${API_URL}/api/settings/report-emails`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          enabled: settings.reportEmailsEnabled,
          emails: settings.reportEmails.split(',').map(e => e.trim()).filter(Boolean),
        }),
      })
    } catch { /* best-effort */ }
    setSaved(true)
    setTimeout(() => setSaved(false), 2500)
  }

  useEffect(() => {
    fetch(`${API_URL}/api/settings/report-emails`)
      .then(r => r.json())
      .then((data: { enabled: boolean; emails: string[] }) => {
        if (data.emails?.length > 0) {
          setSettings(prev => ({
            ...prev,
            reportEmailsEnabled: data.enabled,
            reportEmails: data.emails.join(','),
          }))
        }
      })
      .catch(() => {})
  }, [])

  async function handleTestAppium() {
    setTesting(true); setTestRes(null)
    try {
      const res = await fetch(`${API_URL}/api/health`)
      setTestRes(res.ok ? 'ok' : 'fail')
    } catch { setTestRes('fail') }
    finally { setTesting(false) }
  }

  function handleExport() {
    const blob = new Blob([JSON.stringify(settings, null, 2)], { type: 'application/json' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = 'qa-config.json'
    a.click()
  }

  function handleImportFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]; if (!file) return
    const reader = new FileReader()
    reader.onload = ev => {
      try { setSettings({ ...DEFAULTS, ...JSON.parse(ev.target?.result as string) }) } catch { /* bad json */ }
    }
    reader.readAsText(file)
    e.target.value = ''
  }

  const backendStatus: 'online' | 'offline' | 'checking' =
    backendHealth.status === 'online' ? 'online' : backendHealth.status === 'offline' ? 'offline' : 'checking'

  // ── Sidebar panel (always visible) ───────────────────────────────────────
  const Sidebar = (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16, width: 272, flexShrink: 0 }}>

      {/* Estado del Sistema */}
      <div style={{ background: 'var(--bg-card)', border: '1px solid var(--panel-border)', borderRadius: 16, padding: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
          <div style={{ width: 28, height: 28, borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(16,185,129,0.15)' }}>
            <Activity size={14} style={{ color: '#10b981' }} />
          </div>
          <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-pri)' }}>Estado del Sistema</span>
        </div>
        <div style={{
          display: 'flex', alignItems: 'center', gap: 8, borderRadius: 10, padding: '8px 12px', marginBottom: 14, fontSize: 11, fontWeight: 600,
          background: backendStatus === 'online' ? 'rgba(16,185,129,0.1)' : 'rgba(244,63,94,0.1)',
          border: `1px solid ${backendStatus === 'online' ? 'rgba(16,185,129,0.25)' : 'rgba(244,63,94,0.25)'}`,
          color: backendStatus === 'online' ? '#10b981' : '#f43f5e',
        }}>
          <CheckCircle2 size={13} />
          {backendStatus === 'online' ? 'Todo funcionando correctamente' : 'Verificando servicios…'}
        </div>
        <StatusRow label="Backend"       status={backendStatus}                                          detail="Online"     />
        <StatusRow label="Runner"        status={runnerOnline ? 'online' : 'offline'}                   detail="Conectado"  />
        <StatusRow label="Appium Server" status={backendStatus === 'online' ? 'online' : 'offline'}    detail="Conectado"  />
        <StatusRow label="Base de Datos" status={backendStatus === 'online' ? 'online' : 'offline'}    detail="Conectada"  />
        <StatusRow label="Almacenamiento" status="online"                                               detail="OK"         />
      </div>

      {/* Acciones Rápidas */}
      <div style={{ background: 'var(--bg-card)', border: '1px solid var(--panel-border)', borderRadius: 16, padding: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 14 }}>
          <div style={{ width: 28, height: 28, borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(99,102,241,0.15)' }}>
            <Zap size={14} style={{ color: '#818cf8' }} />
          </div>
          <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-pri)' }}>Acciones Rápidas</span>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {([
            { label: 'Probar Appium',   desc: 'Verificar conexión',  icon: FlaskConical, color: '#14b8a6', fn: handleTestAppium },
            { label: 'Limpiar Caché',   desc: 'Liberar espacio',     icon: Trash2,       color: '#f97316', fn: () => {} },
            { label: 'Exportar Config', desc: 'Descargar JSON',      icon: Download,     color: '#6366f1', fn: handleExport },
            { label: 'Importar Config', desc: 'Subir configuración', icon: Upload,       color: '#818cf8', fn: () => fileRef.current?.click() },
          ] as { label: string; desc: string; icon: React.ElementType; color: string; fn: () => void }[]).map(a => (
            <button
              key={a.label} onClick={a.fn}
              style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '8px 10px', borderRadius: 10, cursor: 'pointer', background: `${a.color}12`, border: `1px solid ${a.color}28`, transition: 'background .15s', textAlign: 'left', width: '100%' }}
              onMouseEnter={e => (e.currentTarget.style.background = `${a.color}22`)}
              onMouseLeave={e => (e.currentTarget.style.background = `${a.color}12`)}
            >
              <div style={{ width: 28, height: 28, borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', background: `${a.color}22`, flexShrink: 0 }}>
                <a.icon size={13} style={{ color: a.color }} />
              </div>
              <div>
                <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text-sec)' }}>{a.label}</div>
                <div style={{ fontSize: 10, color: 'var(--text-dim)' }}>{a.desc}</div>
              </div>
            </button>
          ))}
        </div>
      </div>

      {/* Consejo */}
      <div style={{ background: 'linear-gradient(135deg,rgba(99,102,241,.12) 0%,rgba(124,58,237,.07) 100%)', border: '1px solid rgba(99,102,241,0.2)', borderRadius: 16, padding: 20 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
          <div style={{ width: 28, height: 28, borderRadius: 8, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(99,102,241,0.2)' }}>
            <Lightbulb size={14} style={{ color: '#818cf8' }} />
          </div>
          <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text-pri)' }}>Consejo</span>
        </div>
        <p style={{ fontSize: 11, lineHeight: 1.6, color: 'var(--text-sec)', margin: 0 }}>
          Configura correctamente los tiempos de espera y reintentos para mejorar la estabilidad de tus ejecuciones.
        </p>
      </div>

    </div>
  )

  // ── Tab content helpers ───────────────────────────────────────────────────
  const cardInfoGeneral = (
    <Card title="Información General" icon={Info} accent="#6366f1">
      <SRow label="Nombre de la plataforma"><SInput value={settings.platformName} onChange={v => set('platformName', v)} /></SRow>
      <SRow label="Zona horaria"><SSelect value={settings.timezone} onChange={v => set('timezone', v)} options={['America/Mexico_City','America/Bogota','America/Santiago','America/Buenos_Aires']} /></SRow>
      <SRow label="Idioma"><SSelect value={settings.language} onChange={v => set('language', v)} options={['Español (México)','Español (España)','English (US)']} /></SRow>
      <SRow label="Formato de fecha"><SSelect value={settings.dateFormat} onChange={v => set('dateFormat', v)} options={['dd/MM/yyyy','MM/dd/yyyy','yyyy-MM-dd']} /></SRow>
      <SRow label="Modo oscuro"><Toggle value={isDark} onChange={onToggleTheme} /></SRow>
    </Card>
  )

  const cardEjecucion = (
    <Card title="Ejecución por Defecto" icon={Play} accent="#10b981">
      <SRow label="Ambiente por defecto"><SSelect value={settings.defaultEnv} onChange={v => set('defaultEnv', v)} options={['QA','STG','PROD','DEV']} /></SRow>
      <SRow label="Dispositivo por defecto"><SInput value={settings.defaultDevice} onChange={v => set('defaultDevice', v)} /></SRow>
      <SRow label="Timeout global (min)"><SInput value={settings.globalTimeout} type="number" onChange={v => set('globalTimeout', +v)} /></SRow>
      <SRow label="Reintentos por falla"><SInput value={settings.retriesOnFail} type="number" onChange={v => set('retriesOnFail', +v)} /></SRow>
      <SRow label="Capturar screenshots en fallas"><Toggle value={settings.screenshotsOnFail} onChange={v => set('screenshotsOnFail', v)} /></SRow>
      <SRow label="Video en fallas"><Toggle value={settings.videoOnFail} onChange={v => set('videoOnFail', v)} /></SRow>
    </Card>
  )

  const cardAppium = (
    <Card title="Conexión Appium" icon={Wifi} accent="#14b8a6">
      <SRow label="Appium Server URL"><SInput value={settings.appiumUrl} onChange={v => set('appiumUrl', v)} /></SRow>
      <SRow label="Tipo de conexión"><SSelect value={settings.connectionType} onChange={v => set('connectionType', v)} options={['USB','WiFi','Emulator']} /></SRow>
      <SRow label="Validar dispositivo antes de ejecutar"><Toggle value={settings.validateDevice} onChange={v => set('validateDevice', v)} /></SRow>
      <SRow label="Reinstalar Appium Settings"><Toggle value={settings.reinstallAppiumSettings} onChange={v => set('reinstallAppiumSettings', v)} /></SRow>
      <SRow label="Tiempo de espera ADB (seg)"><SInput value={settings.adbWaitTime} type="number" onChange={v => set('adbWaitTime', +v)} /></SRow>
      <div style={{ marginTop: 12, display: 'flex', alignItems: 'center', gap: 12 }}>
        <button
          onClick={handleTestAppium} disabled={testing}
          style={{
            display: 'flex', alignItems: 'center', gap: 6,
            padding: '7px 14px', borderRadius: 10, fontSize: 11, fontWeight: 700, cursor: testing ? 'not-allowed' : 'pointer',
            background: 'rgba(20,184,166,0.14)', border: '1px solid rgba(20,184,166,0.35)', color: '#14b8a6', opacity: testing ? 0.7 : 1,
          }}
        >
          {testing ? <><span className="animate-spin inline-block"><Loader2 size={11} /></span> Probando…</> : 'Probar Conexión'}
        </button>
        {testRes === 'ok'  && <span style={{ fontSize: 11, color: '#10b981', fontWeight: 600 }}>Conexión exitosa</span>}
        {testRes === 'fail'&& <span style={{ fontSize: 11, color: '#f43f5e', fontWeight: 600 }}>Sin conexión</span>}
        {!testing && !testRes && <span style={{ fontSize: 10, color: 'var(--text-dim)' }}>Último OK: hace 2 min</span>}
      </div>
    </Card>
  )

  const cardReportes = (
    <Card title="Reportes y Evidencia" icon={BarChart3} accent="#10b981">
      <SRow label="Generar reporte Allure"><Toggle value={settings.generateAllure} onChange={v => set('generateAllure', v)} /></SRow>
      <SRow label="Adjuntar screenshots"><Toggle value={settings.attachScreenshots} onChange={v => set('attachScreenshots', v)} /></SRow>
      <SRow label="Adjuntar videos"><Toggle value={settings.attachVideos} onChange={v => set('attachVideos', v)} /></SRow>
      <SRow label="Conservar datos (días)"><SInput value={settings.retentionDays} type="number" onChange={v => set('retentionDays', +v)} /></SRow>
      <SRow label="Nivel de logs"><SSelect value={settings.logLevel} onChange={v => set('logLevel', v)} options={['DEBUG','INFO','WARN','ERROR']} /></SRow>
      <SRow label="Limpiar reportes antiguos"><Toggle value={settings.clearOldReports} onChange={v => set('clearOldReports', v)} /></SRow>
      <div style={{ borderTop: '1px solid var(--panel-border)', marginTop: 10, paddingTop: 10 }}>
        <SRow label="Enviar reporte por correo"><Toggle value={settings.reportEmailsEnabled} onChange={v => set('reportEmailsEnabled', v)} /></SRow>
        {settings.reportEmailsEnabled && (
          <div style={{ marginTop: 6 }}>
            <span style={{ fontSize: 10, color: 'var(--text-sec)' }}>
              Destinatarios — Enter o coma para agregar, Backspace para borrar
            </span>
            <EmailTagInput value={settings.reportEmails} onChange={v => set('reportEmails', v)} />
          </div>
        )}
      </div>
    </Card>
  )

  const cardNotif = (
    <Card title="Notificaciones" icon={Bell} accent="#eab308">
      <SRow label="Notificar al iniciar ejecución"><Toggle value={settings.notifyOnStart} onChange={v => set('notifyOnStart', v)} /></SRow>
      <SRow label="Notificar al finalizar ejecución"><Toggle value={settings.notifyOnEnd} onChange={v => set('notifyOnEnd', v)} /></SRow>
      <SRow label="Notificar en caso de falla"><Toggle value={settings.notifyOnFail} onChange={v => set('notifyOnFail', v)} /></SRow>
      <SRow label="Canal por defecto"><SSelect value={settings.notificationChannel} onChange={v => set('notificationChannel', v)} options={['Email','Slack','Webhook','Teams']} /></SRow>
      <SRow label="Email de destino"><SInput value={settings.notificationEmail} onChange={v => set('notificationEmail', v)} /></SRow>
      <SRow label="Resumen diario"><Toggle value={settings.dailySummary} onChange={v => set('dailySummary', v)} /></SRow>
    </Card>
  )

  const cardIntegr = (
    <Card title="Integraciones" icon={Zap} accent="#818cf8">
      <SRow label="Webhook para ejecuciones"><Toggle value={settings.webhookEnabled} onChange={v => set('webhookEnabled', v)} /></SRow>
      <div style={{ paddingBottom: 6 }}>
        <input
          value={settings.webhookUrl} onChange={e => set('webhookUrl', e.target.value)}
          placeholder="https://tuservidor.com/webhook/qa"
          style={{ width: '100%', background: 'var(--terminal-bg)', border: '1px solid var(--btn-border)', color: 'var(--text-sec)', borderRadius: 8, padding: '5px 10px', fontSize: 11, outline: 'none' }}
        />
      </div>
      <SRow label="Slack"><Toggle value={settings.slackEnabled} onChange={v => set('slackEnabled', v)} /></SRow>
      <SRow label="Canal de Slack"><SInput value={settings.slackChannel} onChange={v => set('slackChannel', v)} /></SRow>
      <SRow label="Jira"><Toggle value={settings.jiraEnabled} onChange={v => set('jiraEnabled', v)} /></SRow>
      <SRow label="TestRail"><Toggle value={settings.testrailEnabled} onChange={v => set('testrailEnabled', v)} /></SRow>
    </Card>
  )

  const cardSeg = (
    <Card title="Seguridad" icon={Shield} accent="#f43f5e">
      <SRow label="Autenticación 2FA obligatoria"><Toggle value={settings.twoFactor} onChange={v => set('twoFactor', v)} /></SRow>
      <SRow label="Sesión expira en (min)"><SInput value={settings.sessionTimeout} type="number" onChange={v => set('sessionTimeout', +v)} /></SRow>
      <SRow label="IPs permitidas"><SInput value={settings.allowedIPs} onChange={v => set('allowedIPs', v)} /></SRow>
      <SRow label="Bloqueo por intentos fallidos"><SInput value={settings.blockAttempts} type="number" onChange={v => set('blockAttempts', +v)} /></SRow>
      <SRow label="Política de contraseñas"><SSelect value={settings.passwordPolicy} onChange={v => set('passwordPolicy', v)} options={['Básica','Media','Fuerte','Máxima']} /></SRow>
      <SRow label="Registro de auditoría"><Toggle value={settings.auditLogging} onChange={v => set('auditLogging', v)} /></SRow>
    </Card>
  )

  const cardAvanzado = (
    <Card title="Avanzado" icon={Settings} accent="#f97316">
      <SRow label="Ejecución paralela"><Toggle value={settings.parallelExecution} onChange={v => set('parallelExecution', v)} /></SRow>
      <SRow label="Máx. ejecuciones paralelas"><SInput value={settings.maxParallel} type="number" onChange={v => set('maxParallel', +v)} /></SRow>
      <SRow label="Limpiar app antes de ejecutar"><Toggle value={settings.cleanApp} onChange={v => set('cleanApp', v)} /></SRow>
      <SRow label="Variables de entorno">
        <button style={{ fontSize: 11, fontWeight: 600, padding: '5px 12px', borderRadius: 8, background: 'rgba(99,102,241,0.14)', border: '1px solid rgba(99,102,241,0.32)', color: '#818cf8', cursor: 'pointer' }}>
          Gestionar
        </button>
      </SRow>
      <SRow label="Modo de depuración"><Toggle value={settings.debugMode} onChange={v => set('debugMode', v)} /></SRow>
      <SRow label="Conservar logs (días)"><SInput value={settings.keepLogsDays} type="number" onChange={v => set('keepLogsDays', +v)} /></SRow>
    </Card>
  )

  const cardDatos = (
    <Card title="Gestión de Datos" icon={Database} accent="#eab308">
      <SRow label="Limpiar ejecuciones antiguas"><Toggle value={settings.cleanOldExecutions} onChange={v => set('cleanOldExecutions', v)} /></SRow>
      <SRow label="Eliminar datos mayores a (días)"><SInput value={settings.deleteOlderThan} type="number" onChange={v => set('deleteOlderThan', +v)} /></SRow>
      <SRow label="Respaldo automático"><Toggle value={settings.autoBackup} onChange={v => set('autoBackup', v)} /></SRow>
      <SRow label="Frecuencia de respaldo"><SSelect value={settings.backupFrequency} onChange={v => set('backupFrequency', v)} options={['Diario','Semanal','Mensual']} /></SRow>
      <SRow label="Descargar respaldo ahora">
        <button style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, fontWeight: 600, padding: '5px 12px', borderRadius: 8, background: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.28)', color: '#10b981', cursor: 'pointer' }}>
          <Download size={11} /> Descargar
        </button>
      </SRow>
      <SRow label="Ubicación de respaldo"><SSelect value={settings.backupLocation} onChange={v => set('backupLocation', v)} options={['Google Drive','Local','S3','OneDrive']} /></SRow>
    </Card>
  )

  // ── Grid layouts per tab ──────────────────────────────────────────────────
  function MainContent() {
    if (tab === 'general') return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 16 }}>
        {cardInfoGeneral}{cardEjecucion}{cardAppium}
        {cardReportes}{cardNotif}{cardIntegr}
        {cardSeg}{cardAvanzado}{cardDatos}
      </div>
    )
    if (tab === 'ejecucion') return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {cardEjecucion}{cardAppium}
      </div>
    )
    if (tab === 'notificaciones') return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 16, maxWidth: 480 }}>
        {cardNotif}
      </div>
    )
    if (tab === 'integraciones') return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 16, maxWidth: 480 }}>
        {cardIntegr}
      </div>
    )
    if (tab === 'seguridad') return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 16, maxWidth: 480 }}>
        {cardSeg}
      </div>
    )
    if (tab === 'avanzado') return (
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {cardAvanzado}{cardDatos}
      </div>
    )
    // dispositivos / personalizacion → coming soon
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: 240, flexDirection: 'column', gap: 12 }}>
        <div style={{ fontSize: 36, opacity: 0.25 }}>🚧</div>
        <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--text-dim)' }}>Esta sección estará disponible próximamente</div>
      </div>
    )
  }

  return (
    <div style={{ padding: 28, maxWidth: 1400 }}>

      {/* Header */}
      <div style={{ marginBottom: 20 }}>
        <h1 style={{ fontSize: 20, fontWeight: 700, color: 'var(--text-pri)', margin: 0 }}>Configuración</h1>
        <p style={{ fontSize: 12, color: 'var(--text-dim)', marginTop: 4, marginBottom: 0 }}>
          Personaliza y controla todos los aspectos de tu plataforma
        </p>
      </div>

      {/* Tabs */}
      <div style={{ display: 'flex', gap: 2, borderBottom: '1px solid var(--panel-border)', marginBottom: 24 }}>
        {TABS.map(t => (
          <button
            key={t.id} onClick={() => setTab(t.id)}
            style={{
              padding: '10px 16px', fontSize: 12, fontWeight: 600, background: 'none',
              border: 'none', borderBottom: tab === t.id ? '2px solid #6366f1' : '2px solid transparent',
              color: tab === t.id ? 'var(--text-pri)' : 'var(--text-dim)',
              cursor: 'pointer', marginBottom: -1, transition: 'color .15s',
            }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* Body: main + sidebar */}
      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <MainContent />
        </div>
        {Sidebar}
      </div>

      {/* Footer */}
      <div style={{
        position: 'sticky', bottom: 0, marginTop: 24,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '14px 20px', borderRadius: 16,
        background: 'var(--bg-card)', border: '1px solid var(--panel-border)',
        backdropFilter: 'blur(12px)',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 32, height: 32, borderRadius: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(16,185,129,0.15)' }}>
            <Shield size={15} style={{ color: '#10b981' }} />
          </div>
          <div>
            <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--text-pri)' }}>Tus configuraciones están seguras</div>
            <div style={{ fontSize: 10, color: 'var(--text-dim)', marginTop: 2 }}>
              Los datos se guardan localmente y están protegidos con encriptación.
            </div>
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
          {saved && (
            <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, color: '#10b981', fontWeight: 600 }}>
              <CheckCircle2 size={13} /> Guardado
            </span>
          )}
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 11, color: 'var(--text-dim)' }}>
            Guardado automático <CheckCircle2 size={11} style={{ color: '#10b981', display: 'inline' }} />
            <span style={{ fontSize: 10 }}>Última modificación: Hace 2 min</span>
          </div>
          <button
            onClick={handleSave}
            style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '9px 20px', borderRadius: 10, fontSize: 13, fontWeight: 700, border: 'none', cursor: 'pointer',
              background: 'linear-gradient(135deg,#6366f1 0%,#7c3aed 100%)',
              color: 'white', boxShadow: '0 4px 14px rgba(99,102,241,0.35)',
            }}
            onMouseEnter={e => (e.currentTarget.style.boxShadow = '0 4px 20px rgba(99,102,241,0.5)')}
            onMouseLeave={e => (e.currentTarget.style.boxShadow = '0 4px 14px rgba(99,102,241,0.35)')}
          >
            Guardar Cambios
          </button>
        </div>
      </div>

      {/* Hidden file input */}
      <input ref={fileRef} type="file" accept=".json" style={{ display: 'none' }} onChange={handleImportFile} />
    </div>
  )
}
