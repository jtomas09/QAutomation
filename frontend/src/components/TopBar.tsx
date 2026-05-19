import React, { useState } from 'react'
import type { BackendHealth } from '../hooks/useBackendHealth'
import s from './TopBar.module.css'

interface Props {
  backendHealth: BackendHealth
  runnerOnline:  boolean
  onNewExecution?: () => void
}

export default function TopBar({ backendHealth, runnerOnline, onNewExecution }: Props) {
  const [search, setSearch] = useState('')

  return (
    <header className={s.bar}>
      {/* Search */}
      <div className={s.searchWrap}>
        <span className={s.searchIcon}>🔍</span>
        <input
          className={s.searchInput}
          placeholder="Buscar suites, dispositivos, ejecuciones..."
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        <span className={s.searchHint}>⌘K</span>
      </div>

      {/* Right side */}
      <div className={s.right}>
        {/* Backend status */}
        <StatusPill
          label="Backend"
          online={backendHealth.status === 'online'}
          checking={backendHealth.status === 'checking'}
        />

        {/* Runner status */}
        <StatusPill label="Runner" online={runnerOnline} />

        <div className={s.sep} />

        {/* Theme toggle */}
        <button className={s.iconBtn} title="Cambiar tema">🌙</button>

        {/* Notifications */}
        <button className={s.iconBtn} title="Notificaciones">
          🔔
          <span className={s.notifBadge}>3</span>
        </button>

        <div className={s.sep} />

        {/* User */}
        <div className={s.user}>
          <div className={s.avatar}>J</div>
          <div className={s.userInfo}>
            <span className={s.userName}>Jairo</span>
            <span className={s.userRole}>QA Engineer</span>
          </div>
          <span className={s.userChevron}>▾</span>
        </div>
      </div>
    </header>
  )
}

function StatusPill({ label, online, checking = false }: {
  label: string; online: boolean; checking?: boolean
}) {
  const cls = checking ? s.pillChecking : online ? s.pillOnline : s.pillOffline
  const txt = checking ? 'Verificando' : online ? 'Online' : 'Offline'
  if (label === 'Runner') {
    return (
      <div className={`${s.pill} ${cls}`}>
        <span className={s.pillDot} />
        <span className={s.pillLabel}>{label}</span>
        <span className={s.pillStatus}>{online ? 'Conectado' : 'Desconectado'}</span>
      </div>
    )
  }
  return (
    <div className={`${s.pill} ${cls}`}>
      <span className={s.pillDot} />
      <span className={s.pillLabel}>{label}</span>
      <span className={s.pillStatus}>{txt}</span>
    </div>
  )
}
