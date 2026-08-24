import { ChevronRight } from 'lucide-react'

export interface Crumb {
  label:    string
  onClick?: () => void
}

/** Breadcrumb de texto pequeño reutilizable — mismo patrón que ArticleDetail.tsx. */
export default function DocumentationBreadcrumb({ crumbs }: { crumbs: Crumb[] }) {
  return (
    <div className="flex items-center gap-1.5 text-xs flex-wrap" style={{ color: 'var(--text-dim)' }}>
      {crumbs.map((c, i) => {
        const isLast = i === crumbs.length - 1
        return (
          <span key={i} className="flex items-center gap-1.5">
            {c.onClick && !isLast ? (
              <button onClick={c.onClick} className="hover:underline" style={{ color: 'var(--text-dim)' }}>
                {c.label}
              </button>
            ) : (
              <span
                className={isLast ? 'font-semibold' : undefined}
                style={{ color: isLast ? 'var(--text-sec)' : 'var(--text-dim)' }}
              >
                {c.label}
              </span>
            )}
            {!isLast && <ChevronRight size={12} />}
          </span>
        )
      })}
    </div>
  )
}
