import React from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import * as Icons from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { ExecutionEvent } from '../../types'
import { EVENT_ICON, DEFAULT_EVENT_ICON, SEVERITY_COLOR, isVisibleInTimeline } from '../../utils/eventPresentation'

interface Props {
  events: ExecutionEvent[]
  /** Cuántas filas mostrar como máximo (igual criterio que ActivityLog: ventana reciente, no historial completo). */
  max?: number
}

function iconFor(type: string): LucideIcon {
  const name = EVENT_ICON[type] ?? DEFAULT_EVENT_ICON
  return (Icons as unknown as Record<string, LucideIcon>)[name] ?? Icons.Circle
}

/**
 * Reemplaza el modo "Extraer Log" de ActivityLog cuando hay eventos de dominio
 * disponibles — cada fila es un ExecutionEvent, el ícono/color se decide por
 * `type`/`severity`, nunca por texto (ver eventPresentation.ts). Estilo GitHub
 * Actions / Jenkins Blue Ocean: actividad principal limpia, una línea por hito.
 */
function ExecutionTimeline({ events, max = 80 }: Props) {
  const visible = React.useMemo(() => events.filter(isVisibleInTimeline), [events])
  const latestProgress = React.useMemo(() => {
    for (let i = visible.length - 1; i >= 0; i--) {
      if (visible[i].progress) return visible[i].progress!
    }
    return null
  }, [visible])

  if (visible.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-full gap-3 text-slate-600">
        <Icons.Terminal size={28} className="opacity-30" />
        <span className="text-xs text-center">Sin actividad reciente…</span>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex-1 min-h-0 overflow-y-auto">
        <AnimatePresence initial={false}>
          {visible.slice(-max).map((e, i) => {
            const Icon  = iconFor(e.type)
            const color = SEVERITY_COLOR[e.severity] ?? SEVERITY_COLOR.INFO
            return (
              <motion.div
                key={`${e.timestamp}-${e.type}-${i}`}
                initial={{ opacity: 0, x: -8 }}
                animate={{ opacity: 1, x: 0 }}
                transition={{ duration: 0.2 }}
                className="flex items-center gap-2.5 py-1 px-0.5 group"
              >
                <span className="text-slate-600 flex-shrink-0 tabular-nums text-[11px] w-14">
                  {e.timestamp?.slice(11, 19) || ''}
                </span>
                <span className="flex-shrink-0" style={{ color }}>
                  <Icon size={13} />
                </span>
                <span
                  className="flex-1 break-all text-[12px]"
                  style={{ color: e.type === 'SUITE_START' ? '#818cf8' : undefined, fontWeight: e.type === 'SUITE_START' ? 700 : 400 }}
                >
                  {e.message}
                </span>
                {e.progress && (
                  <span className="flex-shrink-0 text-[10px] text-slate-500 tabular-nums px-1.5 py-0.5 rounded"
                        style={{ background: 'rgba(255,255,255,0.05)' }}>
                    {e.progress.current}/{e.progress.total}
                  </span>
                )}
              </motion.div>
            )
          })}
        </AnimatePresence>
      </div>
      {latestProgress && latestProgress.total > 0 && (
        <div className="px-1 pt-2 pb-1 flex-shrink-0">
          <div className="h-1.5 rounded-full overflow-hidden" style={{ background: 'rgba(255,255,255,0.08)' }}>
            <div
              className="h-full rounded-full transition-all duration-300"
              style={{
                width: `${Math.min(100, (latestProgress.current / latestProgress.total) * 100)}%`,
                background: 'linear-gradient(90deg,#6366f1,#818cf8)',
              }}
            />
          </div>
        </div>
      )}
    </div>
  )
}

export default React.memo(ExecutionTimeline)
