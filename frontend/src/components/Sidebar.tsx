import React from 'react'
import s from './Sidebar.module.css'

export type Page =
  | 'dashboard' | 'execute' | 'executions' | 'suites' | 'devices' | 'environments' | 'settings'
  | 'reports' | 'metrics' | 'history' | 'trends'
  | 'docs' | 'videos' | 'support'

interface NavItem { id: Page; label: string; icon: string; hasBadge?: boolean }

const MAIN_NAV: NavItem[] = [
  { id: 'dashboard',    label: 'Dashboard',        icon: '⊞' },
  { id: 'execute',      label: 'Ejecutar Pruebas', icon: '▶', hasBadge: true },
  { id: 'executions',   label: 'Ejecuciones',      icon: '≡' },
  { id: 'suites',       label: 'Suites',           icon: '◫' },
  { id: 'devices',      label: 'Dispositivos',     icon: '◻' },
  { id: 'environments', label: 'Ambientes',         icon: '🌐' },
  { id: 'settings',     label: 'Configuración',    icon: '⚙' },
]

const ANALYTICS_NAV: NavItem[] = [
  { id: 'reports', label: 'Reportes',   icon: '📊' },
  { id: 'metrics', label: 'Métricas',   icon: '📈' },
  { id: 'history', label: 'Historial',  icon: '📋' },
  { id: 'trends',  label: 'Tendencias', icon: '📉' },
]

const RESOURCES_NAV: NavItem[] = [
  { id: 'docs',    label: 'Documentación', icon: '📄' },
  { id: 'videos',  label: 'Videos',        icon: '🎬' },
  { id: 'support', label: 'Soporte',       icon: '💬' },
]

interface Props {
  page:         Page
  onPageChange: (p: Page) => void
  runningCount?: number
}

export default function Sidebar({ page, onPageChange, runningCount = 0 }: Props) {
  return (
    <aside className={s.sidebar}>
      <div className={s.brand}>
        <div className={s.brandIcon}>QA</div>
        <div>
          <div className={s.brandName}>AUTOMATION QA</div>
          <div className={s.brandSub}>Test Launcher</div>
        </div>
      </div>

      <div className={s.divider} />

      <nav className={s.nav}>
        <Group items={MAIN_NAV}      activePage={page} onSelect={onPageChange} runningCount={runningCount} />
        <div className={s.divider} />
        <div className={s.groupLabel}>ANALYTICS</div>
        <Group items={ANALYTICS_NAV} activePage={page} onSelect={onPageChange} />
        <div className={s.divider} />
        <div className={s.groupLabel}>RECURSOS</div>
        <Group items={RESOURCES_NAV} activePage={page} onSelect={onPageChange} />
      </nav>

      <div className={s.divider} />
      <div className={s.planCard}>
        <div className={s.planBadge}>🔥 Plan Enterprise</div>
        <div className={s.planName}>Plan Enterprise</div>
        <div className={s.planStatus}><span>●</span>Activo</div>
        <div className={s.planExpiry}>Vence el 25/12/2025</div>
        <button className={s.planBtn}>Gestionar Plan</button>
      </div>
    </aside>
  )
}

function Group({ items, activePage, onSelect, runningCount = 0 }: {
  items: NavItem[]; activePage: Page; onSelect: (p: Page) => void; runningCount?: number
}) {
  return (
    <div className={s.group}>
      {items.map(item => (
        <button
          key={item.id}
          className={`${s.item} ${activePage === item.id ? s.itemActive : ''}`}
          onClick={() => onSelect(item.id)}
        >
          <span className={s.itemIcon}>{item.icon}</span>
          <span className={s.itemLabel}>{item.label}</span>
          {item.hasBadge && runningCount > 0 && (
            <span className={s.badge}>{runningCount}</span>
          )}
        </button>
      ))}
    </div>
  )
}
