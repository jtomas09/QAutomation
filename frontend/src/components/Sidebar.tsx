import React, { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  LayoutDashboard, Play, ListOrdered, Layers3,
  Smartphone, Globe, Settings2, BarChart3,
  TrendingUp, Clock4, Activity, BookOpen, Video, Headphones,
  Zap, ChevronDown, CalendarClock, Server, HardDrive, Download, Clapperboard,
} from 'lucide-react'
import { cn } from '../lib/utils'

export type Page =
  | 'dashboard' | 'execute' | 'executions' | 'suites' | 'devices' | 'environments' | 'settings'
  | 'reports' | 'metrics' | 'history' | 'trends'
  | 'docs' | 'videos' | 'support' | 'schedule'
  | 'runner-manager' | 'device-farm' | 'download-agent'
  | 'record-studio'

interface NavItem { id: Page; label: string; icon: React.ElementType; accent?: string; tag?: string }

const MAIN_NAV: NavItem[] = [
  { id: 'dashboard',     label: 'Dashboard',        icon: LayoutDashboard, accent: '#6366f1' },
  { id: 'execute',       label: 'Ejecutar Pruebas', icon: Play,            accent: '#10b981' },
  { id: 'executions',    label: 'Ejecuciones',      icon: ListOrdered,     accent: '#818cf8' },
  { id: 'suites',        label: 'Suites',           icon: Layers3,         accent: '#14b8a6' },
  { id: 'devices',       label: 'Dispositivos',     icon: Smartphone,      accent: '#f97316' },
  { id: 'environments',  label: 'Ambientes',        icon: Globe,           accent: '#eab308' },
  { id: 'settings',      label: 'Configuración',    icon: Settings2,       accent: '#94a3b8' },
  { id: 'schedule',      label: 'Programación',     icon: CalendarClock,   accent: '#8b5cf6' },
  { id: 'record-studio', label: 'Record Studio',    icon: Clapperboard,    accent: '#e11d48', tag: 'NUEVO' },
]

const ANALYTICS_NAV: NavItem[] = [
  { id: 'reports', label: 'Reportes',   icon: BarChart3,   accent: '#10b981' },
  { id: 'metrics', label: 'Métricas',   icon: TrendingUp,  accent: '#6366f1' },
  { id: 'history', label: 'Historial',  icon: Clock4,      accent: '#818cf8' },
  { id: 'trends',  label: 'Tendencias', icon: Activity,    accent: '#f43f5e' },
]

const INFRASTRUCTURE_NAV: NavItem[] = [
  { id: 'device-farm',    label: 'Device Farm',     icon: HardDrive, accent: '#10b981', tag: 'NUEVO' },
  { id: 'runner-manager', label: 'Runner Manager',  icon: Server,    accent: '#06b6d4' },
  { id: 'download-agent', label: 'Descargar Agent', icon: Download,  accent: '#6366f1' },
]

const RESOURCES_NAV: NavItem[] = [
  { id: 'docs',    label: 'Documentación', icon: BookOpen,    accent: '#3b82f6' },
  { id: 'videos',  label: 'Videos',        icon: Video,       accent: '#14b8a6' },
  { id: 'support', label: 'Soporte',       icon: Headphones,  accent: '#f97316' },
]

interface Props {
  page:         Page
  onPageChange: (p: Page) => void
  runningCount?: number
}

export default function Sidebar({ page, onPageChange, runningCount = 0 }: Props) {
  return (
    <aside
      className="flex flex-col h-full flex-shrink-0 overflow-hidden"
      style={{
        width: 'var(--sidebar-w)',
        background: 'var(--sidebar-bg)',
        borderRight: '1px solid var(--sidebar-border)',
      }}
    >
      {/* Ambient glow top */}
      <div className="absolute top-0 left-0 right-0 h-64 pointer-events-none"
        style={{ background: 'radial-gradient(ellipse at 50% 0%, rgba(99,102,241,0.12) 0%, transparent 70%)' }}
      />

      {/* Brand */}
      <div className="flex items-center gap-3 px-5 py-5 relative flex-shrink-0"
        style={{ borderBottom: '1px solid rgba(255,255,255,0.06)' }}>
        <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0 font-black text-white text-sm"
          style={{ background: 'linear-gradient(135deg, #6366f1 0%, #7c3aed 100%)', boxShadow: '0 0 20px rgba(99,102,241,0.4)' }}>
          QA
        </div>
        <div>
          <div className="text-xs font-bold tracking-widest uppercase" style={{ color: 'var(--text-pri)' }}>Automation QA</div>
          <div className="text-[10px] text-slate-500 mt-0.5">Test Launcher v2.0</div>
        </div>
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto py-4 px-3 space-y-6 relative">
        <NavSection label="NAVEGACIÓN"     items={MAIN_NAV}           page={page} onSelect={onPageChange} runningCount={runningCount} />
        <NavSection label="ANALYTICS"      items={ANALYTICS_NAV}      page={page} onSelect={onPageChange} />
        <NavSection label="INFRAESTRUCTURA" items={INFRASTRUCTURE_NAV} page={page} onSelect={onPageChange} />
        <NavSection label="RECURSOS"       items={RESOURCES_NAV}      page={page} onSelect={onPageChange} />
      </nav>

      {/* Agent download card */}
      <div className="flex-shrink-0 p-3" style={{ borderTop: '1px solid rgba(255,255,255,0.06)' }}>
        <div className="rounded-xl p-4 relative overflow-hidden"
          style={{
            background: 'linear-gradient(135deg, rgba(99,102,241,0.12) 0%, rgba(124,58,237,0.08) 100%)',
            border: '1px solid rgba(99,102,241,0.22)',
          }}>
          <div className="absolute inset-0 pointer-events-none"
            style={{ background: 'radial-gradient(ellipse at top right, rgba(99,102,241,0.18) 0%, transparent 60%)' }} />
          <div className="relative">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-6 h-6 rounded-lg flex items-center justify-center flex-shrink-0"
                style={{ background: 'rgba(99,102,241,0.2)', border: '1px solid rgba(99,102,241,0.3)' }}>
                <Download size={12} className="text-indigo-400" />
              </div>
              <span className="text-xs font-bold" style={{ color: 'var(--text-pri)' }}>¿Aún no tienes Agent?</span>
            </div>
            <p className="text-[10px] text-slate-400 leading-relaxed mb-3">
              Descarga Automation QA Agent e instálalo en tus máquinas para comenzar a ejecutar pruebas.
            </p>
            <button
              onClick={() => onPageChange('download-agent')}
              className="w-full py-2 rounded-lg text-[11px] font-bold text-white flex items-center justify-center gap-1.5 transition-all"
              style={{ background: 'linear-gradient(135deg, #6366f1, #7c3aed)', boxShadow: '0 4px 12px rgba(99,102,241,0.35)' }}
              onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.opacity = '0.88' }}
              onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.opacity = '1' }}>
              <Download size={12} />
              Descargar Agent
            </button>
          </div>
        </div>
      </div>
    </aside>
  )
}

function NavSection({ label, items, page, onSelect, runningCount = 0 }: {
  label: string; items: NavItem[]; page: Page; onSelect: (p: Page) => void; runningCount?: number
}) {
  return (
    <div>
      <div className="text-[10px] font-bold tracking-widest text-slate-600 px-3 mb-2">{label}</div>
      <div className="space-y-0.5">
        {items.map(item => (
          <NavRow key={item.id} item={item} active={page === item.id} onSelect={onSelect}
            badge={item.id === 'execute' && runningCount > 0 ? runningCount : undefined}
          />
        ))}
      </div>
    </div>
  )
}

function NavRow({ item, active, onSelect, badge }: {
  item: NavItem; active: boolean; onSelect: (p: Page) => void; badge?: number
}) {
  const [hovered, setHovered] = useState(false)
  const accent = item.accent ?? '#6366f1'

  return (
    <motion.button
      onClick={() => onSelect(item.id)}
      onHoverStart={() => setHovered(true)}
      onHoverEnd={() => setHovered(false)}
      whileTap={{ scale: 0.97 }}
      className="w-full flex items-center gap-3 px-3 py-2 rounded-xl text-left relative overflow-hidden transition-colors"
      style={{
        background: active
          ? `${accent}18`
          : hovered ? 'rgba(255,255,255,0.04)' : 'transparent',
        color: active ? 'var(--text-pri)' : hovered ? 'var(--text-sec)' : 'var(--text-dim)',
      }}
    >
      {/* Active left bar */}
      {active && (
        <motion.div
          layoutId="sidebar-active-bar"
          className="absolute left-0 top-2 bottom-2 w-0.5 rounded-r"
          style={{ background: accent, boxShadow: `0 0 8px ${accent}` }}
        />
      )}

      <item.icon
        size={16}
        style={{ color: active ? accent : hovered ? 'var(--text-lbl)' : 'var(--text-dim)', flexShrink: 0 }}
      />
      <span className="text-sm font-medium flex-1">{item.label}</span>
      {item.tag && (
        <span className="text-[9px] font-black px-1.5 py-0.5 rounded-full"
          style={{ background: 'rgba(16,185,129,0.2)', color: '#10b981', border: '1px solid rgba(16,185,129,0.3)' }}>
          {item.tag}
        </span>
      )}
      {badge !== undefined && (
        <span className="text-[10px] font-bold bg-rose-500 text-white rounded-full px-1.5 py-0.5 min-w-[18px] text-center">
          {badge}
        </span>
      )}
    </motion.button>
  )
}
