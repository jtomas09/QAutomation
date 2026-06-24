import React, { useState } from 'react'
import { motion } from 'framer-motion'
import ReactCountryFlag from 'react-country-flag'
import { ENVIRONMENTS, SUITES, COUNTRIES } from '../../data'
import type { RunStatus } from '../../types'
import type { ConfiguredDevice } from '../../hooks/useExecutionDevices'
import { RefreshCw, X, ChevronDown, Video, Check } from 'lucide-react'
import { PlatformIcon } from '../PlatformIcon'

interface Props {
  suite:              string
  env:                string
  configuredDevices:  ConfiguredDevice[]
  country:            string
  status:             RunStatus
  executionId:        string | null
  videoEnabled:       boolean
  passed:             number
  failed:             number
  skipped:            number
  totalExpected:      number
  onSuiteChange:      (v: string) => void
  onEnvChange:        (v: string) => void
  onCountryChange:    (v: string) => void
  onVideoToggle:      (v: boolean) => void
  onRun:              () => void
  onStop:             () => void
  onConfigureDevices: () => void
}

const COUNTRY_ISO: Record<string, string> = {
  mexico: 'MX', argentina: 'AR', chile: 'CL',
  colombia: 'CO', peru: 'PE', espana: 'ES',
}

// ── Progress bar ──────────────────────────────────────────────────────────────

function ProgressBar({ completed, total, passed, failed, skipped }: {
  completed: number; total: number; passed: number; failed: number; skipped: number
}) {
  const pctPassed  = total > 0 ? (passed  / total) * 100 : 0
  const pctFailed  = total > 0 ? (failed  / total) * 100 : 0
  const pctSkipped = total > 0 ? (skipped / total) * 100 : 0
  const allGood    = failed === 0

  return (
    <div className="px-1 pt-1 pb-0.5">
      <div className="flex items-center justify-between mb-1.5">
        <span className="text-[10px] font-bold uppercase tracking-wide text-slate-500">Progreso</span>
        <span className="text-[13px] font-black tabular-nums" style={{ color: allGood ? '#34d399' : '#f87171' }}>
          {completed}<span className="text-[11px] font-semibold text-slate-500">/{total}</span>
        </span>
      </div>
      <div className="relative h-2.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.07)' }}>
        <div className="absolute inset-y-0 left-0 transition-all duration-500" style={{ width: `${pctPassed}%`,  background: '#22c55e' }} />
        <div className="absolute inset-y-0 transition-all duration-500" style={{ left: `${pctPassed}%`, width: `${pctFailed}%`,  background: '#ef4444' }} />
        <div className="absolute inset-y-0 transition-all duration-500" style={{ left: `${pctPassed + pctFailed}%`, width: `${pctSkipped}%`, background: '#f59e0b' }} />
      </div>
      <div className="flex items-center gap-2 mt-1.5 flex-wrap">
        {passed  > 0 && <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full" style={{ color: '#22c55e', background: 'rgba(34,197,94,0.12)' }}>✓ {passed} ok</span>}
        {failed  > 0 && <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full" style={{ color: '#f87171', background: 'rgba(239,68,68,0.12)' }}>✗ {failed} fail</span>}
        {skipped > 0 && <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full" style={{ color: '#fbbf24', background: 'rgba(245,158,11,0.12)' }}>⏭ {skipped} skip</span>}
        {completed < total && <span className="text-[10px] text-slate-600 ml-auto">{total - completed} restantes</span>}
      </div>
    </div>
  )
}

// ── Configured devices read-only panel ────────────────────────────────────────

function ConfiguredDevicesPanel({ devices, onConfigure }: { devices: ConfiguredDevice[]; onConfigure: () => void }) {
  if (devices.length === 0) {
    return (
      <div
        className="flex flex-col items-center justify-center gap-2 py-4 rounded-xl text-center"
        style={{ background: 'rgba(244,63,94,0.06)', border: '1px dashed rgba(244,63,94,0.3)' }}
      >
        <div className="text-[11px] text-slate-500">Sin dispositivos configurados</div>
        <button
          onClick={onConfigure}
          className="text-[10px] font-bold px-3 py-1.5 rounded-lg transition-colors"
          style={{ color: '#818cf8', background: 'rgba(99,102,241,0.12)', border: '1px solid rgba(99,102,241,0.25)' }}
        >
          Ir a Dispositivos Conectados →
        </button>
      </div>
    )
  }

  const platforms = [...new Set(devices.map(d => d.platform?.toUpperCase() === 'IOS' ? 'iOS' : 'Android'))]

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center gap-1.5 flex-wrap">
        {platforms.map(p => (
          <span key={p} className="flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-bold"
            style={{ background: 'rgba(99,102,241,0.15)', color: '#818cf8' }}>
            <Check size={8} />
            {p}
          </span>
        ))}
      </div>
      <div className="rounded-xl overflow-hidden"
        style={{ border: '1px solid rgba(99,102,241,0.2)', background: 'rgba(99,102,241,0.04)' }}>
        {devices.map((device, i) => {
          const isIos  = device.platform?.toUpperCase() === 'IOS'
          const accent = isIos ? '#a8b8d8' : '#3DDC84'
          return (
            <div key={device.udid} className="flex items-center gap-2.5 px-3 py-2"
              style={{ borderTop: i > 0 ? '1px solid rgba(255,255,255,0.05)' : 'none' }}>
              <div className="w-5 h-5 rounded flex items-center justify-center flex-shrink-0"
                style={{ background: 'rgba(99,102,241,0.2)', border: '1px solid rgba(99,102,241,0.4)' }}>
                <Check size={10} color="#818cf8" />
              </div>
              <PlatformIcon platform={device.platform} size={12} />
              <div className="flex-1 min-w-0">
                <div className="text-[11px] font-semibold text-slate-200 truncate">{device.name}</div>
                <div className="text-[9px]" style={{ color: `${accent}88` }}>
                  {isIos ? 'iOS' : 'Android'} {device.platformVersion ?? '—'}
                </div>
              </div>
            </div>
          )
        })}
      </div>
      <button onClick={onConfigure} className="text-[9px] font-semibold text-right mt-0.5 transition-colors"
        style={{ color: '#475569' }}
        onMouseEnter={e => ((e.currentTarget as HTMLButtonElement).style.color = '#818cf8')}
        onMouseLeave={e => ((e.currentTarget as HTMLButtonElement).style.color = '#475569')}>
        Cambiar configuración →
      </button>
    </div>
  )
}

// ── Main panel ────────────────────────────────────────────────────────────────

export default function RunTestsPanel(props: Props) {
  const {
    suite, env, configuredDevices, country, status, executionId,
    videoEnabled, onVideoToggle, passed, failed, skipped, totalExpected,
    onSuiteChange, onEnvChange, onCountryChange, onRun, onStop, onConfigureDevices,
  } = props
  const [advanced, setAdvanced] = useState(false)
  const running   = status === 'running'
  const completed = passed + failed + skipped
  const canRun    = configuredDevices.length > 0

  return (
    <div className="flex flex-col h-full overflow-hidden rounded-2xl"
      style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}>

      {/* Header */}
      <div className="flex items-center justify-between px-5 py-4"
        style={{ borderBottom: '1px solid var(--panel-divide)' }}>
        <div>
          <div className="text-sm font-bold text-slate-100">Ejecutar Pruebas</div>
          <div className="text-xs text-slate-500 mt-0.5">
            {configuredDevices.length > 0
              ? `${configuredDevices.length} dispositivo${configuredDevices.length > 1 ? 's' : ''} configurado${configuredDevices.length > 1 ? 's' : ''}`
              : 'Configura dispositivos en el panel inferior'
            }
          </div>
        </div>
        <div className="flex items-center gap-2">
          <SmallBtn><RefreshCw size={12} /></SmallBtn>
          <SmallBtn><X size={12} /></SmallBtn>
        </div>
      </div>

      {/* Body */}
      <div className="flex flex-1 min-h-0">
        <div className="flex-1 flex flex-col p-4 gap-3 overflow-auto">

          <Field label="Suite">
            <PremiumSelect value={suite} onChange={onSuiteChange} options={SUITES} />
          </Field>

          <Field label="Dispositivos configurados">
            <ConfiguredDevicesPanel devices={configuredDevices} onConfigure={onConfigureDevices} />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="País">
              <div className="relative flex items-center">
                <span className="absolute left-2.5 z-10 flex-shrink-0" style={{ lineHeight: 0 }}>
                  <ReactCountryFlag countryCode={COUNTRY_ISO[country] ?? 'MX'} svg
                    style={{ width: '1.25em', height: '1.25em', borderRadius: 2 }} />
                </span>
                <select value={country} onChange={e => onCountryChange(e.target.value)}
                  className="w-full appearance-none pl-9 pr-7 py-2 rounded-xl text-xs font-semibold text-slate-200 outline-none"
                  style={{
                    background: 'var(--input-bg)', border: '1px solid var(--input-border)',
                    backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%2364748b'/%3E%3C/svg%3E\")",
                    backgroundRepeat: 'no-repeat', backgroundPosition: 'right 8px center',
                  }}>
                  {COUNTRIES.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </div>
            </Field>
            <Field label="Ambiente">
              <PremiumSelect value={env} onChange={onEnvChange} options={ENVIRONMENTS} />
            </Field>
          </div>

          <VideoToggle enabled={videoEnabled} onChange={onVideoToggle} />

          <button className="flex items-center gap-1.5 text-xs text-slate-500 hover:text-slate-300 transition-colors"
            onClick={() => setAdvanced(a => !a)}>
            <ChevronDown size={12} className={advanced ? 'rotate-180' : ''} style={{ transition: 'transform .2s' }} />
            Opciones Avanzadas
          </button>

          {advanced && (
            <motion.div initial={{ opacity: 0, height: 0 }} animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }} className="grid grid-cols-2 gap-3">
              <Field label="Reintentos">
                <PremiumSelect value="0" onChange={() => {}} options={['0','1','2','3']} />
              </Field>
              <Field label="Timeout (min)">
                <PremiumSelect value="30" onChange={() => {}} options={['10','30','60','120']} />
              </Field>
            </motion.div>
          )}

          {/* CTA */}
          <div className="mt-auto space-y-2">
            {running ? (
              <motion.button whileTap={{ scale: 0.97 }} onClick={onStop}
                className="w-full py-3 rounded-xl text-sm font-bold text-white flex items-center justify-center gap-2"
                style={{ background: 'linear-gradient(135deg, #dc2626, #f43f5e)', boxShadow: '0 4px 20px rgba(244,63,94,0.4)' }}>
                ⏹ DETENER EJECUCIÓN
              </motion.button>
            ) : (
              <motion.button
                whileHover={canRun ? { scale: 1.01 } : undefined}
                whileTap={canRun ? { scale: 0.97 } : undefined}
                onClick={canRun ? onRun : undefined}
                disabled={!canRun}
                title={!canRun ? 'Seleccione al menos un dispositivo en Dispositivos Conectados' : undefined}
                className="w-full py-3 rounded-xl text-sm font-bold text-white flex items-center justify-center gap-2"
                style={{
                  background: canRun ? 'linear-gradient(135deg, #4f46e5, #6366f1, #7c3aed)' : 'rgba(99,102,241,0.3)',
                  boxShadow:  canRun ? '0 4px 20px rgba(99,102,241,0.45)' : 'none',
                  cursor:     canRun ? 'pointer' : 'not-allowed',
                  opacity:    canRun ? 1 : 0.6,
                }}>
                ▶ EJECUTAR PRUEBAS
              </motion.button>
            )}

            {!canRun && !running && (
              <p className="text-center text-[10px] font-semibold" style={{ color: '#f43f5e' }}>
                Seleccione al menos un dispositivo en Dispositivos Conectados
              </p>
            )}

            {running && totalExpected > 0 && (
              <ProgressBar completed={completed} total={totalExpected}
                passed={passed} failed={failed} skipped={skipped} />
            )}

            <p className="text-center text-[11px] text-slate-600">
              {executionId
                ? <span style={{ color: '#818cf8' }}>ID: {executionId}</span>
                : configuredDevices.length > 1
                ? `${configuredDevices.length} ejecuciones en paralelo`
                : 'Se generará un nuevo ID de ejecución'
              }
            </p>
          </div>
        </div>

        {/* Rocket */}
        <div className="w-36 flex flex-col items-center justify-center relative flex-shrink-0"
          style={{ borderLeft: '1px solid rgba(255,255,255,0.05)' }}>
          <div className="absolute inset-0 pointer-events-none"
            style={{ background: 'radial-gradient(ellipse at 50% 60%, rgba(99,102,241,0.12) 0%, transparent 70%)' }} />
          <motion.div animate={{ y: [0, -10, 0], rotate: [-4, 4, -4] }}
            transition={{ duration: 3.5, repeat: Infinity, ease: 'easeInOut' }} className="relative z-10">
            <svg width="72" height="100" viewBox="0 0 72 100" fill="none" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <linearGradient id="rBody2" x1="36" y1="5" x2="36" y2="80" gradientUnits="userSpaceOnUse">
                  <stop offset="0%" stopColor="#818cf8" /><stop offset="100%" stopColor="#4f46e5" />
                </linearGradient>
                <linearGradient id="rWindow2" x1="36" y1="30" x2="36" y2="50" gradientUnits="userSpaceOnUse">
                  <stop offset="0%" stopColor="#e0e7ff" /><stop offset="100%" stopColor="#818cf8" />
                </linearGradient>
                <radialGradient id="rFlame2" cx="50%" cy="0%" r="80%">
                  <stop offset="0%" stopColor="#fbbf24" stopOpacity="1"/>
                  <stop offset="50%" stopColor="#f97316" stopOpacity="0.8"/>
                  <stop offset="100%" stopColor="#dc2626" stopOpacity="0"/>
                </radialGradient>
              </defs>
              <ellipse cx="36" cy="86" rx="9" ry="14" fill="url(#rFlame2)" />
              <ellipse cx="36" cy="82" rx="5" ry="8"  fill="#fbbf24" opacity="0.9"/>
              <path d="M36 5C36 5 16 32 16 58H56C56 32 36 5 36 5Z" fill="url(#rBody2)" />
              <path d="M36 5C36 5 28 25 28 38H36V5Z" fill="rgba(255,255,255,0.15)" />
              <path d="M16 58L6 78H20L16 58Z"  fill="#6366f1" />
              <path d="M56 58L66 78H52L56 58Z" fill="#6366f1" />
              <rect x="16" y="56" width="40" height="6" rx="3" fill="#4f46e5" />
              <circle cx="36" cy="40" r="10" fill="url(#rWindow2)" />
              <circle cx="36" cy="40" r="7"  fill="rgba(255,255,255,0.15)" />
              <circle cx="33" cy="37" r="2.5" fill="rgba(255,255,255,0.6)" />
            </svg>
          </motion.div>
          <div className="absolute bottom-8 left-1/2 -translate-x-1/2 w-12 h-4 rounded-full pointer-events-none"
            style={{ background: 'rgba(99,102,241,0.4)', filter: 'blur(10px)' }} />
        </div>
      </div>
    </div>
  )
}

// ── Primitives ────────────────────────────────────────────────────────────────

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label className="text-[10px] font-bold uppercase tracking-wider text-slate-500">{label}</label>
      {children}
    </div>
  )
}

function PremiumSelect({ value, onChange, options }: { value: string; onChange: (v: string) => void; options: string[] }) {
  return (
    <select value={value} onChange={e => onChange(e.target.value)}
      className="w-full appearance-none px-3 py-2 pr-7 rounded-xl text-xs font-semibold text-slate-200 outline-none"
      style={{
        background: 'var(--input-bg)', border: '1px solid var(--input-border)',
        backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%2364748b'/%3E%3C/svg%3E\")",
        backgroundRepeat: 'no-repeat', backgroundPosition: 'right 8px center',
      }}>
      {options.map(o => <option key={o} value={o}>{o}</option>)}
    </select>
  )
}

function SmallBtn({ children }: { children: React.ReactNode }) {
  return (
    <button className="w-7 h-7 rounded-lg flex items-center justify-center text-slate-500 hover:text-slate-300 transition-colors"
      style={{ background: 'var(--btn-bg)', border: '1px solid var(--btn-border)' }}>
      {children}
    </button>
  )
}

function VideoToggle({ enabled, onChange }: { enabled: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-center justify-between px-1 py-0.5">
      <div className="flex items-center gap-2">
        <Video size={13} style={{ color: enabled ? '#f97316' : 'var(--text-dim)', transition: 'color .2s' }} />
        <span className="text-xs font-semibold" style={{ color: enabled ? 'var(--text-sec)' : 'var(--text-dim)', transition: 'color .2s' }}>
          Grabar Video
        </span>
        {enabled && <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-full"
          style={{ background: 'rgba(249,115,22,0.15)', color: '#f97316' }}>ACTIVO</span>}
      </div>
      <button onClick={() => onChange(!enabled)}
        className="relative flex items-center rounded-full select-none flex-shrink-0"
        style={{
          width: 58, height: 26,
          background: enabled ? '#f97316' : 'var(--input-bg)',
          border: `1.5px solid ${enabled ? '#f97316' : 'var(--input-border)'}`,
          padding: 3, transition: 'background .25s, border-color .25s', cursor: 'pointer',
        }}>
        <span className="absolute text-[9px] font-black tracking-wide pointer-events-none"
          style={{ left: enabled ? 9 : 'auto', right: enabled ? 'auto' : 8, color: enabled ? 'white' : 'var(--text-dim)', transition: 'color .2s' }}>
          {enabled ? 'ON' : 'OFF'}
        </span>
        <div className="rounded-full" style={{
          width: 18, height: 18,
          background: enabled ? 'white' : '#f97316',
          transform: enabled ? 'translateX(32px)' : 'translateX(0)',
          transition: 'transform .25s, background .25s',
          boxShadow: '0 1px 4px rgba(0,0,0,0.25)', flexShrink: 0,
        }} />
      </button>
    </div>
  )
}
