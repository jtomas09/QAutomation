import { useEffect, useRef, useState } from 'react'
import type { TocItem } from '../../data/deviceSetupGuide'

/** Índice lateral con scroll-spy — resalta la sección visible y hace scroll suave al hacer click. */
export default function DocumentationToc({ items }: { items: TocItem[] }) {
  const flatIds = useRef(items.flatMap(it => [it.id, ...(it.children ?? []).map(c => c.id)]))
  const [activeId, setActiveId] = useState<string>(flatIds.current[0])

  useEffect(() => {
    const elements = flatIds.current
      .map(id => document.getElementById(id))
      .filter((el): el is HTMLElement => el !== null)

    if (elements.length === 0) return

    const observer = new IntersectionObserver(
      entries => {
        const visible = entries.filter(e => e.isIntersecting)
        if (visible.length > 0) {
          const topMost = visible.reduce((a, b) => (a.boundingClientRect.top < b.boundingClientRect.top ? a : b))
          setActiveId(topMost.target.id)
        }
      },
      { rootMargin: '-15% 0px -70% 0px', threshold: 0 },
    )

    elements.forEach(el => observer.observe(el))
    return () => observer.disconnect()
  }, [])

  const scrollTo = (id: string) => {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  return (
    <nav className="space-y-1">
      <div className="text-[11px] font-bold tracking-widest uppercase mb-2" style={{ color: 'var(--text-dim)' }}>
        En esta guía
      </div>
      <ol className="space-y-2.5">
        {items.map((item, i) => {
          const isActive = activeId === item.id || item.children?.some(c => c.id === activeId)
          return (
            <li key={item.id}>
              <button
                onClick={() => scrollTo(item.id)}
                className="text-left text-xs font-semibold flex items-start gap-1.5 w-full transition-colors"
                style={{ color: isActive ? '#818cf8' : 'var(--text-sec)' }}
              >
                <span className="flex-shrink-0">{i + 1}.</span>
                <span>{item.label}</span>
              </button>
              {item.children && (
                <ul className="mt-1.5 ml-4 space-y-1.5">
                  {item.children.map(child => (
                    <li key={child.id}>
                      <button
                        onClick={() => scrollTo(child.id)}
                        className="text-left text-[11px] transition-colors"
                        style={{ color: activeId === child.id ? '#818cf8' : 'var(--text-dim)' }}
                      >
                        {child.label}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
