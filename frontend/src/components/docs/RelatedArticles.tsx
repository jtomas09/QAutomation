import { FileText, ChevronRight } from 'lucide-react'
import { getArticleById } from '../../data/documentation'

export default function RelatedArticles({ articleIds, onOpen }: { articleIds: string[]; onOpen: (id: string) => void }) {
  const articles = articleIds.map(getArticleById).filter((a): a is NonNullable<typeof a> => a != null)

  return (
    <div className="rounded-2xl p-4" style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}>
      <div className="text-sm font-bold mb-2" style={{ color: 'var(--text-pri)' }}>Artículos relacionados</div>
      <div className="space-y-0.5">
        {articles.map(a => (
          <button
            key={a.id}
            onClick={() => onOpen(a.id)}
            className="w-full flex items-center gap-2 py-2 px-2 -mx-2 rounded-lg text-left transition-colors"
            onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'rgba(255,255,255,0.04)' }}
            onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
          >
            <FileText size={13} className="flex-shrink-0" style={{ color: 'var(--text-dim)' }} />
            <span className="text-xs flex-1 min-w-0 truncate" style={{ color: 'var(--text-sec)' }}>{a.title}</span>
            <ChevronRight size={13} className="flex-shrink-0" style={{ color: 'var(--text-dim)' }} />
          </button>
        ))}
      </div>
    </div>
  )
}
