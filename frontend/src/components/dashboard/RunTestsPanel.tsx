import React, { useState } from 'react'
import { motion } from 'framer-motion'
import ReactCountryFlag from 'react-country-flag'
import { ENVIRONMENTS, DEVICES, SUITES, COUNTRIES } from '../../data'
import type { RunStatus } from '../../types'
import { RefreshCw, X, ChevronDown } from 'lucide-react'

interface Props {
  suite:           string
  env:             string
  device:          string
  country:         string
  status:          RunStatus
  executionId:     string | null
  onSuiteChange:   (v: string) => void
  onEnvChange:     (v: string) => void
  onDeviceChange:  (v: string) => void
  onCountryChange: (v: string) => void
  onRun:           () => void
  onStop:          () => void
}

const COUNTRY_ISO: Record<string, string> = {
  mexico: 'MX', argentina: 'AR', chile: 'CL',
  colombia: 'CO', peru: 'PE', espana: 'ES',
}

export default function RunTestsPanel(props: Props) {
  const { suite, env, device, country, status, executionId,
          onSuiteChange, onEnvChange, onDeviceChange, onCountryChange, onRun, onStop } = props
  const [advanced, setAdvanced] = useState(false)
  const running = status === 'running'

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
      <div className="flex items-center justify-between px-5 py-4"
        style={{ borderBottom: '1px solid var(--panel-divide)' }}>
        <div>
          <div className="text-sm font-bold text-slate-100">Ejecutar Pruebas</div>
          <div className="text-xs text-slate-500 mt-0.5">Selecciona las opciones para ejecutar</div>
        </div>
        <div className="flex items-center gap-2">
          <SmallBtn><RefreshCw size={12} /></SmallBtn>
          <SmallBtn><X size={12} /></SmallBtn>
        </div>
      </div>

      {/* Body: form + rocket */}
      <div className="flex flex-1 min-h-0">
        {/* Form */}
        <div className="flex-1 flex flex-col p-4 gap-3 overflow-auto">
          {/* Row 1 */}
          <div className="grid grid-cols-2 gap-3">
            <Field label="Suite">
              <PremiumSelect value={suite} onChange={onSuiteChange} options={SUITES} />
            </Field>
            <Field label="Dispositivo">
              <PremiumSelect value={device} onChange={onDeviceChange} options={DEVICES} />
            </Field>
          </div>

          {/* Row 2 */}
          <div className="grid grid-cols-2 gap-3">
            <Field label="País">
              <div className="relative flex items-center">
                <span className="absolute left-2.5 z-10 flex-shrink-0" style={{ lineHeight: 0 }}>
                  <ReactCountryFlag
                    countryCode={COUNTRY_ISO[country] ?? 'MX'}
                    svg
                    style={{ width: '1.25em', height: '1.25em', borderRadius: 2 }}
                  />
                </span>
                <select
                  value={country}
                  onChange={e => onCountryChange(e.target.value)}
                  className="w-full appearance-none pl-9 pr-7 py-2 rounded-xl text-xs font-semibold text-slate-200 outline-none"
                  style={{
                    background: 'var(--input-bg)',
                    border: '1px solid var(--input-border)',
                    backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%2364748b'/%3E%3C/svg%3E\")",
                    backgroundRepeat: 'no-repeat',
                    backgroundPosition: 'right 8px center',
                  }}
                >
                  {COUNTRIES.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>
            </Field>
            <Field label="Ambiente">
              <PremiumSelect value={env} onChange={onEnvChange} options={ENVIRONMENTS} />
            </Field>
          </div>

          {/* Advanced */}
          <button
            className="flex items-center gap-1.5 text-xs text-slate-500 hover:text-slate-300 transition-colors"
            onClick={() => setAdvanced(a => !a)}
          >
            <ChevronDown size={12} className={advanced ? 'rotate-180' : ''} style={{ transition: 'transform .2s' }} />
            Opciones Avanzadas
          </button>

          {advanced && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="grid grid-cols-2 gap-3"
            >
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
              <motion.button
                whileTap={{ scale: 0.97 }}
                onClick={onStop}
                className="w-full py-3 rounded-xl text-sm font-bold text-white flex items-center justify-center gap-2"
                style={{
                  background: 'linear-gradient(135deg, #dc2626, #f43f5e)',
                  boxShadow: '0 4px 20px rgba(244,63,94,0.4)',
                }}
              >
                ⏹ DETENER EJECUCIÓN
              </motion.button>
            ) : (
              <motion.button
                whileHover={{ scale: 1.01 }}
                whileTap={{ scale: 0.97 }}
                onClick={onRun}
                className="w-full py-3 rounded-xl text-sm font-bold text-white flex items-center justify-center gap-2"
                style={{
                  background: 'linear-gradient(135deg, #4f46e5, #6366f1, #7c3aed)',
                  backgroundSize: '200% 200%',
                  boxShadow: '0 4px 20px rgba(99,102,241,0.45)',
                }}
              >
                ▶ EJECUTAR PRUEBAS
              </motion.button>
            )}
            <p className="text-center text-[11px] text-slate-600">
              {executionId
                ? <span style={{ color: '#818cf8' }}>ID: {executionId}</span>
                : 'Se generará un nuevo ID de ejecución'}
            </p>
          </div>
        </div>

        {/* Rocket illustration */}
        <div
          className="w-36 flex flex-col items-center justify-center relative flex-shrink-0"
          style={{ borderLeft: '1px solid rgba(255,255,255,0.05)' }}
        >
          {/* BG glow */}
          <div className="absolute inset-0 pointer-events-none"
            style={{ background: 'radial-gradient(ellipse at 50% 60%, rgba(99,102,241,0.12) 0%, transparent 70%)' }} />

          <motion.div
            animate={{ y: [0, -10, 0], rotate: [-4, 4, -4] }}
            transition={{ duration: 3.5, repeat: Infinity, ease: 'easeInOut' }}
            className="relative z-10"
          >
            {/* SVG Rocket */}
            <svg width="72" height="100" viewBox="0 0 72 100" fill="none" xmlns="http://www.w3.org/2000/svg">
              <defs>
                <linearGradient id="rBody" x1="36" y1="5" x2="36" y2="80" gradientUnits="userSpaceOnUse">
                  <stop offset="0%" stopColor="#818cf8" />
                  <stop offset="100%" stopColor="#4f46e5" />
                </linearGradient>
                <linearGradient id="rWindow" x1="36" y1="30" x2="36" y2="50" gradientUnits="userSpaceOnUse">
                  <stop offset="0%" stopColor="#e0e7ff" />
                  <stop offset="100%" stopColor="#818cf8" />
                </linearGradient>
                <filter id="rGlow">
                  <feGaussianBlur stdDeviation="3" result="blur" />
                  <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
                </filter>
                <radialGradient id="rFlame" cx="50%" cy="0%" r="80%">
                  <stop offset="0%" stopColor="#fbbf24" stopOpacity="1"/>
                  <stop offset="50%" stopColor="#f97316" stopOpacity="0.8"/>
                  <stop offset="100%" stopColor="#dc2626" stopOpacity="0"/>
                </radialGradient>
              </defs>
              {/* Flame */}
              <ellipse cx="36" cy="86" rx="9" ry="14" fill="url(#rFlame)" />
              <ellipse cx="36" cy="82" rx="5" ry="8" fill="#fbbf24" opacity="0.9"/>
              {/* Body */}
              <path d="M36 5C36 5 16 32 16 58H56C56 32 36 5 36 5Z" fill="url(#rBody)" />
              {/* Nose cone highlight */}
              <path d="M36 5C36 5 28 25 28 38H36V5Z" fill="rgba(255,255,255,0.15)" />
              {/* Fins left */}
              <path d="M16 58L6 78H20L16 58Z" fill="#6366f1" />
              {/* Fins right */}
              <path d="M56 58L66 78H52L56 58Z" fill="#6366f1" />
              {/* Base */}
              <rect x="16" y="56" width="40" height="6" rx="3" fill="#4f46e5" />
              {/* Window */}
              <circle cx="36" cy="40" r="10" fill="url(#rWindow)" />
              <circle cx="36" cy="40" r="7"  fill="rgba(255,255,255,0.15)" />
              <circle cx="33" cy="37" r="2.5" fill="rgba(255,255,255,0.6)" />
            </svg>
          </motion.div>

          {/* Glow under rocket */}
          <div className="absolute bottom-8 left-1/2 -translate-x-1/2 w-12 h-4 rounded-full pointer-events-none"
            style={{ background: 'rgba(99,102,241,0.4)', filter: 'blur(10px)' }} />
        </div>
      </div>
    </div>
  )
}

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
    <div className="relative">
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        className="w-full appearance-none px-3 py-2 pr-7 rounded-xl text-xs font-semibold text-slate-200 outline-none transition-all"
        style={{
          background: 'var(--input-bg)',
          border: '1px solid var(--input-border)',
          backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%2364748b'/%3E%3C/svg%3E\")",
          backgroundRepeat: 'no-repeat',
          backgroundPosition: 'right 8px center',
        }}
      >
        {options.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    </div>
  )
}

function SmallBtn({ children }: { children: React.ReactNode }) {
  return (
    <button
      className="w-7 h-7 rounded-lg flex items-center justify-center text-slate-500 hover:text-slate-300 transition-colors"
      style={{ background: 'var(--btn-bg)', border: '1px solid var(--btn-border)' }}
    >{children}</button>
  )
}
