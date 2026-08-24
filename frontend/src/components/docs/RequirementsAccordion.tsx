import { useState } from 'react'
import { ChevronDown, ListChecks } from 'lucide-react'

/** Bloque expandible reutilizable — mismo lenguaje visual que SuiteAccordion (Suites), en tokens CSS. */
export default function RequirementsAccordion({ id, title, items }: { id?: string; title: string; items: string[] }) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div id={id} className="rounded-xl overflow-hidden" style={{ background: 'var(--btn-bg)', border: '1px solid var(--btn-border)' }}>
      <button
        onClick={() => setExpanded(p => !p)}
        className="w-full flex items-center justify-between gap-2 px-4 py-3 text-left"
      >
        <span className="flex items-center gap-2 text-sm font-semibold" style={{ color: 'var(--text-sec)' }}>
          <ListChecks size={15} style={{ color: 'var(--text-dim)' }} />
          {title}
        </span>
        <ChevronDown
          size={16}
          style={{ color: 'var(--text-dim)', transition: 'transform 0.2s', transform: expanded ? 'rotate(180deg)' : 'rotate(0deg)' }}
        />
      </button>
      {expanded && (
        <ul className="px-4 pb-3 space-y-1.5" style={{ borderTop: '1px solid var(--panel-divide)', paddingTop: 10 }}>
          {items.map((item, i) => (
            <li key={i} className="text-xs flex items-start gap-2" style={{ color: 'var(--text-dim)' }}>
              <span className="mt-1.5 w-1 h-1 rounded-full flex-shrink-0" style={{ background: 'var(--text-dim)' }} />
              {item}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
