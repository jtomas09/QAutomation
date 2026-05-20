import React, { useState } from 'react'
import { motion } from 'framer-motion'
import { Search, Bell, Moon, Sun, ChevronDown, Command } from 'lucide-react'
import type { BackendHealth } from '../hooks/useBackendHealth'

interface Props {
  backendHealth:    BackendHealth
  runnerOnline:     boolean
  isDark:           boolean
  onToggleTheme:    () => void
  onNewExecution?:  () => void
}

export default function TopBar({ backendHealth, runnerOnline, isDark, onToggleTheme }: Props) {
  const [search, setSearch] = useState('')

  return (
    <header
      className="flex items-center gap-4 px-6 flex-shrink-0"
      style={{
        height: 'var(--topbar-h)',
        background: 'var(--topbar-bg)',
        backdropFilter: 'blur(20px)',
        WebkitBackdropFilter: 'blur(20px)',
        borderBottom: '1px solid var(--topbar-border)',
      }}
    >
      {/* Search bar — centered */}
      <div className="flex-1 flex justify-center">
        <div
          className="flex items-center gap-3 px-4 h-9 rounded-xl w-full max-w-md transition-all duration-200"
          style={{
            background: 'rgba(255,255,255,0.04)',
            border: '1px solid rgba(255,255,255,0.08)',
          }}
          onFocus={() => {}}
        >
          <Search size={14} className="text-slate-500 flex-shrink-0" />
          <input
            className="flex-1 bg-transparent border-none outline-none text-sm text-slate-300 placeholder-slate-600"
            placeholder="Buscar suites, dispositivos, ejecuciones..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
          <div className="flex items-center gap-1 flex-shrink-0">
            <Command size={11} className="text-slate-600" />
            <span className="text-[11px] text-slate-600 font-mono">K</span>
          </div>
        </div>
      </div>

      {/* Right side */}
      <div className="flex items-center gap-3">
        {/* Status pills */}
        <StatusPill label="Backend" status={backendHealth.status === 'online' ? 'online' : backendHealth.status === 'checking' ? 'checking' : 'offline'} text={backendHealth.status === 'online' ? 'Online' : backendHealth.status === 'checking' ? 'Verificando' : 'Offline'} />
        <StatusPill label="Runner"  status={runnerOnline ? 'online' : 'offline'} text={runnerOnline ? 'Conectado' : 'Desconectado'} />

        <div className="w-px h-5 bg-white/10" />

        {/* Theme */}
        <IconBtn onClick={onToggleTheme} title={isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}>
          {isDark
            ? <Sun  size={15} className="text-slate-400" />
            : <Moon size={15} className="text-slate-400" />}
        </IconBtn>

        {/* Notifications */}
        <div className="relative">
          <IconBtn>
            <Bell size={15} className="text-slate-400" />
          </IconBtn>
          <span
            className="absolute -top-1 -right-1 w-4 h-4 rounded-full text-[9px] font-bold text-white flex items-center justify-center"
            style={{ background: '#f43f5e', boxShadow: '0 0 8px rgba(244,63,94,0.6)' }}
          >3</span>
        </div>

        <div className="w-px h-5 bg-white/10" />

        {/* User */}
        <div
          className="flex items-center gap-2.5 px-3 py-1.5 rounded-xl cursor-pointer transition-all"
          style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.07)' }}
        >
          <div
            className="w-7 h-7 rounded-lg flex items-center justify-center text-xs font-bold text-white flex-shrink-0"
            style={{
              background: 'linear-gradient(135deg, #6366f1, #7c3aed)',
              boxShadow: '0 0 12px rgba(99,102,241,0.4)',
            }}
          >J</div>
          <div className="flex flex-col">
            <span className="text-xs font-bold text-slate-100 leading-tight">Jairo</span>
            <span className="text-[10px] text-slate-500 leading-tight">QA Engineer</span>
          </div>
          <ChevronDown size={12} className="text-slate-500" />
        </div>
      </div>
    </header>
  )
}

function StatusPill({ label, status, text }: { label: string; status: 'online'|'offline'|'checking'; text: string }) {
  const colors = {
    online:   { dot: '#10b981', bg: 'rgba(16,185,129,0.1)',  border: 'rgba(16,185,129,0.25)',  text: '#10b981' },
    offline:  { dot: '#f43f5e', bg: 'rgba(244,63,94,0.1)',   border: 'rgba(244,63,94,0.25)',   text: '#f43f5e' },
    checking: { dot: '#f59e0b', bg: 'rgba(245,158,11,0.1)',  border: 'rgba(245,158,11,0.25)',  text: '#f59e0b' },
  }
  const c = colors[status]

  return (
    <div
      className="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs"
      style={{ background: c.bg, border: `1px solid ${c.border}` }}
    >
      <motion.div
        className="w-1.5 h-1.5 rounded-full flex-shrink-0"
        style={{ background: c.dot, boxShadow: `0 0 6px ${c.dot}` }}
        animate={{ opacity: status === 'checking' ? [1, 0.3, 1] : 1 }}
        transition={{ duration: 1.5, repeat: Infinity }}
      />
      <span className="font-semibold text-slate-400">{label}</span>
      <span className="font-bold" style={{ color: c.text }}>{text}</span>
    </div>
  )
}

function IconBtn({ children, onClick, title }: { children: React.ReactNode; onClick?: () => void; title?: string }) {
  return (
    <button
      onClick={onClick}
      title={title}
      className="w-8 h-8 rounded-lg flex items-center justify-center transition-all relative"
      style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid var(--panel-border)' }}
      onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'rgba(128,128,128,0.12)' }}
      onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'rgba(255,255,255,0.04)' }}
    >
      {children}
    </button>
  )
}
