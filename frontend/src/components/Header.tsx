import React from 'react'
import { ENVIRONMENTS, DEVICES, SUITES } from '../data'
import type { RunStatus } from '../types'
import { useBackendHealth } from '../hooks/useBackendHealth'
import s from './Header.module.css'

interface Props {
  env: string;    onEnvChange: (v: string) => void
  device: string; onDeviceChange: (v: string) => void
  suite: string;  onSuiteChange: (v: string) => void
  status: RunStatus
  onRun: () => void
  onStop: () => void
}

export default function Header({ env, onEnvChange, device, onDeviceChange, suite, onSuiteChange, status, onRun, onStop }: Props) {
  const running = status === 'running'
  const backend = useBackendHealth()

  return (
    <header className={s.header}>
      <div className={s.left}>
        <Combo label="Ambiente"   icon="🔍" value={env}    options={ENVIRONMENTS} onChange={onEnvChange} />
        <Combo label="Dispositivo" icon="📱" value={device} options={DEVICES}      onChange={onDeviceChange} />
        <Combo label="Suite"      icon="📋" value={suite}  options={SUITES}       onChange={onSuiteChange} />
      </div>

      <div className={s.right}>
        <div className={`${s.backendBadge} ${s[`backend_${backend.status}`]}`} title={backend.message}>
          <span className={s.backendDot} />
          <span className={s.backendText}>
            {backend.status === 'checking' ? 'Conectando…' : backend.status === 'online' ? 'Backend Online' : 'Backend Offline'}
          </span>
        </div>

        <div className={s.status}>
          <span className={`${s.dot} ${running ? s.dotRunning : s.dotReady}`} />
          <span className={s.statusText}>{running ? 'Ejecutando…' : 'Ready'}</span>
        </div>

        {running ? (
          <button className={`${s.runBtn} ${s.stopBtn}`} onClick={onStop}>
            ⏹ DETENER
          </button>
        ) : (
          <button className={s.runBtn} onClick={onRun}>
            ▶ EJECUTAR PRUEBAS
          </button>
        )}

        <button className={s.menuBtn} title="Más opciones">···</button>
      </div>
    </header>
  )
}

function Combo({ label, icon, value, options, onChange }: {
  label: string; icon: string; value: string
  options: string[]; onChange: (v: string) => void
}) {
  return (
    <div className={s.combo}>
      <span className={s.comboIcon}>{icon}</span>
      <span className={s.comboLabel}>{label}:</span>
      <select className={s.select} value={value} onChange={e => onChange(e.target.value)}>
        {options.map(o => <option key={o} value={o}>{o}</option>)}
      </select>
    </div>
  )
}
