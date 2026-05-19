import React, { useState } from 'react'
import { ENVIRONMENTS, DEVICES, SUITES, COUNTRIES } from '../../data'
import type { RunStatus } from '../../types'
import s from './RunTestsPanel.module.css'

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

export default function RunTestsPanel(props: Props) {
  const { suite, env, device, country, status, executionId,
          onSuiteChange, onEnvChange, onDeviceChange, onCountryChange,
          onRun, onStop } = props

  const [advanced, setAdvanced] = useState(false)
  const running = status === 'running'

  const flag = COUNTRIES.find(c => c.id === country)?.flag ?? '🌐'

  return (
    <div className={s.card}>
      <div className={s.header}>
        <div>
          <div className={s.title}>Ejecutar Pruebas</div>
          <div className={s.subtitle}>Selecciona las opciones para ejecutar tus pruebas</div>
        </div>
        <div className={s.headerActions}>
          <button className={s.iconBtn}>⟳</button>
          <button className={s.iconBtn}>✕</button>
        </div>
      </div>

      <div className={s.body}>
        <div className={s.formArea}>
          {/* Row 1 */}
          <div className={s.row}>
            <Field label="Suite">
              <Select value={suite} onChange={onSuiteChange} options={SUITES} />
            </Field>
            <Field label="Dispositivo">
              <Select value={device} onChange={onDeviceChange} options={DEVICES} />
            </Field>
          </div>

          {/* Row 2 */}
          <div className={s.row}>
            <Field label="País">
              <div className={s.selectWrap}>
                <span className={s.flagPrefix}>{flag}</span>
                <select
                  className={`${s.select} ${s.selectPadded}`}
                  value={country}
                  onChange={e => onCountryChange(e.target.value)}
                >
                  {COUNTRIES.map(c => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
              </div>
            </Field>
            <Field label="Ambiente">
              <Select value={env} onChange={onEnvChange} options={ENVIRONMENTS} />
            </Field>
          </div>

          {/* Advanced */}
          <button className={s.advancedToggle} onClick={() => setAdvanced(a => !a)}>
            <span>{advanced ? '▾' : '›'} Opciones Avanzadas</span>
          </button>
          {advanced && (
            <div className={s.advancedContent}>
              <Field label="Reintentos">
                <Select value="0" onChange={() => {}} options={['0','1','2','3']} />
              </Field>
              <Field label="Timeout (min)">
                <Select value="30" onChange={() => {}} options={['10','30','60','120']} />
              </Field>
            </div>
          )}

          {/* Run button */}
          {running ? (
            <button className={s.stopBtn} onClick={onStop}>
              ⏹ DETENER EJECUCIÓN
            </button>
          ) : (
            <button className={s.runBtn} onClick={onRun}>
              ▶ EJECUTAR PRUEBAS
            </button>
          )}

          {executionId
            ? <div className={s.caption} style={{ color: '#6366f1' }}>ID: {executionId}</div>
            : <div className={s.caption}>Se generará un nuevo ID de ejecución</div>
          }
        </div>

        {/* Rocket illustration */}
        <div className={s.rocket}>
          <div className={s.rocketEmoji}>🚀</div>
          <div className={s.rocketGlow} />
        </div>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className={s.field}>
      <label className={s.fieldLabel}>{label}</label>
      {children}
    </div>
  )
}

function Select({ value, onChange, options }: {
  value: string; onChange: (v: string) => void; options: string[]
}) {
  return (
    <div className={s.selectWrap}>
      <select className={s.select} value={value} onChange={e => onChange(e.target.value)}>
        {options.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    </div>
  )
}
