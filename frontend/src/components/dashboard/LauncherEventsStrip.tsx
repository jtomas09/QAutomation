import React from 'react'
import * as Icons from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { ExecutionEvent } from '../../types'
import { EVENT_ICON, DEFAULT_EVENT_ICON, isVisibleInTimeline } from '../../utils/eventPresentation'
import { isTestFlowEvent } from '../../utils/testFlow'

interface Props {
  events: ExecutionEvent[]
}

function iconFor(type: string): LucideIcon {
  const name = EVENT_ICON[type] ?? DEFAULT_EVENT_ICON
  return (Icons as unknown as Record<string, LucideIcon>)[name] ?? Icons.Circle
}

/**
 * Sección pequeña y colapsable — eventos de INFRAESTRUCTURA del Launcher
 * (clonar repo, preparar dispositivo, Appium, driver, reporte, correo). Cambia
 * poco durante la ejecución, así que ocupa poco espacio por diseño: un solo
 * riel horizontal de íconos, no una lista de líneas como el flujo del caso.
 */
function LauncherEventsStrip({ events }: Props) {
  const launcherEvents = React.useMemo(
    () => events.filter(e => isVisibleInTimeline(e) && !isTestFlowEvent(e)),
    [events]
  )

  if (launcherEvents.length === 0) return null

  return (
    <details className="rounded-lg overflow-hidden mb-2" open
      style={{ border: '1px solid var(--panel-divide)', background: 'rgba(255,255,255,0.015)' }}>
      <summary
        className="flex items-center justify-between px-2.5 py-1.5 text-[10.5px] font-semibold cursor-pointer select-none"
        style={{ color: 'var(--text-lbl)' }}
      >
        Eventos del Launcher
        <Icons.ChevronRight size={12} className="chevron-toggle" style={{ color: 'var(--text-dim)' }} />
      </summary>
      <div className="flex gap-3.5 px-2.5 pb-2 pt-0.5 overflow-x-auto">
        {launcherEvents.map((e, i) => {
          const Icon = iconFor(e.type)
          const isTerminal = e.type === 'EXECUTION_FINISHED' || e.severity === 'SUCCESS'
          return (
            <div key={`${e.timestamp}-${i}`} className="flex flex-col items-center gap-1 flex-shrink-0" style={{ minWidth: 46 }}>
              <div
                className="w-5 h-5 rounded-md flex items-center justify-center"
                style={{
                  background: isTerminal ? 'rgba(16,185,129,0.14)' : 'rgba(99,102,241,0.12)',
                  color: isTerminal ? '#34d399' : '#a5b4fc',
                }}
              >
                <Icon size={11} />
              </div>
              <span className="text-[8.5px] text-center leading-tight" style={{ color: 'var(--text-dim)', maxWidth: 52 }}>
                {e.message}
              </span>
            </div>
          )
        })}
      </div>
      <style>{`details[open] .chevron-toggle{ transform: rotate(90deg); } .chevron-toggle{ transition: transform .15s; }`}</style>
    </details>
  )
}

export default React.memo(LauncherEventsStrip)
