import { motion } from 'framer-motion'
import { ArrowLeft, ArrowRight, ChevronRight } from 'lucide-react'
import { DOC_CATEGORIES, getArticlesByCategory, type DocumentationArticle } from '../../data/documentation'

interface Props {
  article:         DocumentationArticle
  onBack:          () => void
  onSelectArticle: (id: string) => void
}

/**
 * Vista de detalle reutilizable para cualquier DocumentationArticle — breadcrumb,
 * título, descripción, contenido (placeholder hasta que exista redacción real),
 * navegación anterior/siguiente dentro de la misma categoría, y botón de regreso.
 * Mismo lenguaje visual que el resto del Dashboard (var(--panel-*), rounded-2xl).
 */
export default function ArticleDetail({ article, onBack, onSelectArticle }: Props) {
  const category = DOC_CATEGORIES.find(c => c.id === article.category)
  const siblings  = getArticlesByCategory(article.category)
  const index     = siblings.findIndex(a => a.id === article.id)
  const prev      = index > 0 ? siblings[index - 1] : null
  const next      = index >= 0 && index < siblings.length - 1 ? siblings[index + 1] : null
  const Icon      = article.icon

  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
      className="p-6 space-y-5 max-w-3xl"
    >
      {/* Breadcrumb */}
      <div className="flex items-center gap-1.5 text-xs" style={{ color: 'var(--text-dim)' }}>
        <button onClick={onBack} className="hover:underline" style={{ color: 'var(--text-dim)' }}>
          Documentación
        </button>
        <ChevronRight size={12} />
        <span style={{ color: category?.color ?? 'var(--text-dim)' }}>{category?.title ?? article.category}</span>
        <ChevronRight size={12} />
        <span className="font-semibold" style={{ color: 'var(--text-sec)' }}>{article.title}</span>
      </div>

      {/* Back button */}
      <button
        onClick={onBack}
        className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors"
        style={{ background: 'var(--btn-bg)', border: '1px solid var(--btn-border)', color: 'var(--text-sec)' }}
      >
        <ArrowLeft size={13} />
        Volver a Documentación
      </button>

      {/* Header */}
      <div
        className="rounded-2xl p-6 relative overflow-hidden"
        style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}
      >
        <div className="absolute top-0 left-0 right-0 h-px"
          style={{ background: `linear-gradient(90deg, transparent, ${category?.color ?? '#6366f1'}, transparent)` }} />
        <div className="flex items-start gap-4">
          <div
            className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0"
            style={{ background: `${category?.color ?? '#6366f1'}22`, color: category?.color ?? '#6366f1' }}
          >
            <Icon size={22} />
          </div>
          <div>
            <h1 className="text-xl font-extrabold" style={{ color: 'var(--text-pri)' }}>{article.title}</h1>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>{article.description}</p>
          </div>
        </div>
      </div>

      {/* Content */}
      <div
        className="rounded-2xl p-6 space-y-3"
        style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}
      >
        {article.content.map((paragraph, i) => (
          <p key={i} className="text-sm leading-relaxed" style={{ color: 'var(--text-sec)' }}>{paragraph}</p>
        ))}
      </div>

      {/* Prev / Next */}
      {(prev || next) && (
        <div className="flex items-center justify-between gap-3">
          {prev ? (
            <button
              onClick={() => onSelectArticle(prev.id)}
              className="flex-1 flex items-center gap-2 px-4 py-3 rounded-xl text-left transition-colors"
              style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)' }}
            >
              <ArrowLeft size={14} style={{ color: 'var(--text-dim)' }} />
              <div className="min-w-0">
                <div className="text-[10px] uppercase tracking-wide" style={{ color: 'var(--text-dim)' }}>Anterior</div>
                <div className="text-xs font-semibold truncate" style={{ color: 'var(--text-sec)' }}>{prev.title}</div>
              </div>
            </button>
          ) : <div className="flex-1" />}
          {next ? (
            <button
              onClick={() => onSelectArticle(next.id)}
              className="flex-1 flex items-center justify-end gap-2 px-4 py-3 rounded-xl text-right transition-colors"
              style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)' }}
            >
              <div className="min-w-0">
                <div className="text-[10px] uppercase tracking-wide" style={{ color: 'var(--text-dim)' }}>Siguiente</div>
                <div className="text-xs font-semibold truncate" style={{ color: 'var(--text-sec)' }}>{next.title}</div>
              </div>
              <ArrowRight size={14} style={{ color: 'var(--text-dim)' }} />
            </button>
          ) : <div className="flex-1" />}
        </div>
      )}
    </motion.div>
  )
}
