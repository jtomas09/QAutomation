import React, { useEffect, useRef } from 'react'
import type { LogEntry } from '../../types'
import s from './ActivityLog.module.css'

const COLOR: Record<string, string> = {
  INFO:    '#a0afd7',
  WARN:    '#eab308',
  ERROR:   '#ef4444',
  PASS:    '#22c55e',
  FAIL:    '#ef4444',
  SKIP:    '#eab308',
  SUCCESS: '#22c55e',
}

const LEVEL_CLS: Record<string, string> = {
  INFO:    s.lvlInfo,
  WARN:    s.lvlWarn,
  ERROR:   s.lvlError,
  FAIL:    s.lvlError,
  PASS:    s.lvlPass,
  SUCCESS: s.lvlPass,
  SKIP:    s.lvlWarn,
}

interface Props {
  logs:    LogEntry[]
  onClear: () => void
  onViewAll?: () => void
}

export default function ActivityLog({ logs, onClear, onViewAll }: Props) {
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [logs])

  return (
    <div className={s.card}>
      <div className={s.header}>
        <div>
          <div className={s.title}>Actividad en Tiempo Real</div>
          <div className={s.subtitle}>Logs recientes de ejecuciones en curso</div>
        </div>
        <button className={s.viewAll} onClick={onViewAll}>Ver todos los logs</button>
      </div>

      <div className={s.body}>
        {logs.length === 0 ? (
          <div className={s.empty}>Sin actividad reciente…</div>
        ) : (
          logs.slice(-50).map(entry => (
            <div key={entry.id} className={s.line}>
              <span className={s.time}>{entry.time}</span>
              <span className={`${s.level} ${LEVEL_CLS[entry.level] ?? s.lvlInfo}`}>
                {entry.level}
              </span>
              <span className={s.msg} style={{ color: COLOR[entry.level] ?? COLOR.INFO }}>
                {entry.message}
              </span>
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
