import React, { useEffect, useRef, useState } from 'react'
import type { LogEntry } from '../types'
import s from './LogPanel.module.css'

interface Props {
  logs: LogEntry[]
  onClear: () => void
}

const COLOR: Record<string, string> = {
  INFO:  '#a0afd7',
  WARN:  '#eab308',
  ERROR: '#ef4444',
  PASS:  '#22c55e',
  FAIL:  '#ef4444',
}

export default function LogPanel({ logs, onClear }: Props) {
  const [activeTab, setActiveTab] = useState<'log' | 'ai'>('log')
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [logs])

  return (
    <div className={s.panel}>
      {/* Tab bar */}
      <div className={s.tabBar}>
        <button
          className={`${s.tab} ${activeTab === 'log' ? s.tabActive : ''}`}
          onClick={() => setActiveTab('log')}
        >
          <span className={s.tabIcon}>›_</span> LOG DE EJECUCIÓN
        </button>
        <button
          className={`${s.tab} ${activeTab === 'ai' ? s.tabActive : ''}`}
          onClick={() => setActiveTab('ai')}
        >
          ✦ ASISTENTE IA
        </button>
        <div className={s.tabSpacer} />
        <button className={s.clearBtn} onClick={onClear} title="Limpiar log">
          ✕ LIMPIAR LOG
        </button>
      </div>

      {/* Log content */}
      {activeTab === 'log' && (
        <div className={s.logWrap}>
          <div className={s.logHeader}>›_ LOG DE EJECUCIÓN</div>
          <div className={s.logBody}>
            {logs.length === 0 && (
              <div className={s.empty}>Esperando ejecución…</div>
            )}
            {logs.map((e: LogEntry) => (
              <div key={e.id} className={s.line}>
                <span className={s.time}>{e.time}</span>
                <span className={s.level} style={{ color: COLOR[e.level] }}>
                  [{e.level}]
                </span>
                <span className={s.msg} style={{ color: COLOR[e.level] }}>
                  {e.message}
                </span>
              </div>
            ))}
            <div ref={bottomRef} />
          </div>
        </div>
      )}

      {activeTab === 'ai' && (
        <div className={s.aiWrap}>
          <p className={s.aiMsg}>
            ✦ Asistente IA — configura tu API key de Anthropic en ⚙ para comenzar.
          </p>
        </div>
      )}
    </div>
  )
}
