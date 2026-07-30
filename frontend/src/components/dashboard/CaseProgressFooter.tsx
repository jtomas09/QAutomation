import React from 'react'
import type { ExecutionEvent, ExecutionEventProgress, RunStatus } from '../../types'

interface Props {
  events: ExecutionEvent[]
  status: RunStatus
}

function formatElapsed(ms: number): string {
  const totalSec = Math.max(0, Math.floor(ms / 1000))
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  return `${m}:${String(s).padStart(2, '0')}`
}

/**
 * Resumen compacto de pie de tarjeta — progreso del caso (N de M, ya viene en
 * `progress` de los eventos CASE_PASSED/FAILED/SKIPPED vía caseProgress()) +
 * tiempo transcurrido desde el primer evento de esta ejecución. Todo derivado
 * de `events`, ningún estado propio nuevo salvo el tick del reloj.
 */
function CaseProgressFooter({ events, status }: Props) {
  const [now, setNow] = React.useState(() => Date.now())

  // FIX real (evidencia en vivo — el cronómetro seguía corriendo minutos
  // después de que la ejecución ya había terminado): este intervalo no
  // dependía del estado de la ejecución, así que nunca se detenía por sí
  // solo. Ahora solo corre mientras status === 'running'; al salir de ese
  // estado (finished/idle) se limpia el intervalo y `now` queda congelado en
  // su último valor — el tiempo transcurrido deja de avanzar. Al iniciar una
  // ejecución nueva (status vuelve a 'running'), se resincroniza de inmediato.
  React.useEffect(() => {
    if (status !== 'running') return
    setNow(Date.now())
    const id = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(id)
  }, [status])

  const progress: ExecutionEventProgress | null = React.useMemo(() => {
    for (let i = events.length - 1; i >= 0; i--) {
      if (events[i].progress) return events[i].progress!
    }
    return null
  }, [events])

  const startedAtMs = React.useMemo(() => {
    if (events.length === 0) return null
    const t = Date.parse(events[0].timestamp)
    return Number.isNaN(t) ? null : t
  }, [events])

  if (!progress && startedAtMs === null) return null

  const pct = progress && progress.total > 0
    ? Math.min(100, Math.round((progress.current / progress.total) * 100))
    : 0

  return (
    <div className="flex-shrink-0 px-3.5 pt-2 pb-2.5" style={{ borderTop: '1px solid var(--panel-divide)' }}>
      <div className="flex items-center justify-between text-[10.5px] mb-1.5" style={{ color: 'var(--text-dim)' }}>
        <span>
          {progress
            ? <>Caso <b style={{ color: 'var(--text-sec)' }}>{progress.current} de {progress.total}</b> en ejecución</>
            : 'En ejecución'}
        </span>
        <span>
          {progress && <b style={{ color: 'var(--text-sec)' }}>{pct}%</b>}
          {progress && startedAtMs !== null && ' · '}
          {startedAtMs !== null && formatElapsed(now - startedAtMs) + ' transcurrido'}
        </span>
      </div>
      {progress && (
        <div className="h-[5px] rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.06)' }}>
          <div
            className="h-full rounded-full transition-all duration-300"
            style={{ width: `${pct}%`, background: 'linear-gradient(90deg,#6366f1,#9333ea)' }}
          />
        </div>
      )}
    </div>
  )
}

export default React.memo(CaseProgressFooter)
