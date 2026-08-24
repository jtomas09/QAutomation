import { useMemo, useState } from 'react'
import { motion } from 'framer-motion'
import {
  Search, BookOpen, Rocket, Smartphone, Terminal, BarChart3,
  ArrowRight, Play, Code2, HelpCircle, Headset, Sparkles, MessageCircle,
} from 'lucide-react'
import {
  DOC_CATEGORIES, getArticlesByCategory, searchArticles,
  getArticleById, type DocumentationArticle,
} from '../data/documentation'
import ArticleDetail from '../components/docs/ArticleDetail'

interface Props {
  /** Opcional — permite que "Videos Tutoriales"/"Soporte Técnico" salten a esas
   *  páginas reales del Sidebar en vez de quedarse solo dentro de Documentación. */
  onNavigate?: (page: string) => void
}

// Mismos 4 colores que DOC_CATEGORIES (morado/verde/ámbar/azul) — Guías Rápidas
// y Recursos Adicionales no tienen categoría propia, pero cada tarjeta
// corresponde conceptualmente a una de las 4, así que reutiliza su color en
// vez de inventar una paleta paralela.
const COLOR_PRIMEROS  = '#8b5cf6'
const COLOR_CONFIG    = '#10b981'
const COLOR_EJECUCION = '#f59e0b'
const COLOR_REPORTES  = '#38bdf8'

const QUICK_GUIDES: { title: string; description: string; icon: typeof Rocket; color: string; articleId: string }[] = [
  { title: 'Guía de Inicio Rápido',      description: 'Comienza a ejecutar tus primeras pruebas en minutos.',            icon: Rocket,     color: COLOR_PRIMEROS,  articleId: 'primeros-pasos-guia' },
  { title: 'Configuración de Dispositivos', description: 'Aprende a conectar y configurar dispositivos Android e iOS.', icon: Smartphone, color: COLOR_CONFIG,    articleId: 'config-dispositivos' },
  { title: 'Ejecutar Pruebas',           description: 'Ejecuta casos, suites y pruebas en múltiples dispositivos.',      icon: Terminal,   color: COLOR_EJECUCION, articleId: 'ejecutar-suites' },
  { title: 'Reportes y Métricas',        description: 'Entiende los resultados y métricas de tus ejecuciones.',         icon: BarChart3,  color: COLOR_REPORTES,  articleId: 'entender-reportes' },
]

const RESOURCES: { title: string; description: string; icon: typeof Play; color: string; kind: 'page' | 'article'; target: string }[] = [
  { title: 'Videos Tutoriales',     description: 'Tutoriales paso a paso en video',   icon: Play,       color: COLOR_PRIMEROS,  kind: 'page',    target: 'videos' },
  { title: 'API Reference',         description: 'Documentación de API completa',     icon: Code2,      color: COLOR_CONFIG,    kind: 'article', target: 'api-reference' },
  { title: 'Preguntas Frecuentes',  description: 'Respuestas a dudas comunes',        icon: HelpCircle, color: COLOR_EJECUCION, kind: 'article', target: 'faq' },
  { title: 'Soporte Técnico',       description: 'Obtén ayuda del equipo',            icon: Headset,    color: COLOR_REPORTES,  kind: 'page',    target: 'support' },
]

export default function DocsPage({ onNavigate }: Props) {
  const [query, setQuery]                     = useState('')
  const [selectedArticleId, setSelectedArticleId] = useState<string | null>(null)

  const results = useMemo(() => searchArticles(query), [query])
  const isSearching = query.trim().length > 0

  const selectedArticle = selectedArticleId ? getArticleById(selectedArticleId) : null

  if (selectedArticle) {
    return (
      <ArticleDetail
        article={selectedArticle}
        onBack={() => setSelectedArticleId(null)}
        onSelectArticle={setSelectedArticleId}
      />
    )
  }

  return (
    <div className="p-6 space-y-6 pb-10">

      {/* Header + search */}
      <motion.div
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.35 }}
        className="flex items-start justify-between gap-4 flex-wrap"
      >
        <div>
          <h1 className="text-2xl font-extrabold text-slate-100 leading-tight">Documentación</h1>
          <p className="text-sm text-slate-500 mt-1">
            Guías, referencias y recursos para usar Automation QA
          </p>
        </div>

        <div className="relative w-full max-w-xs">
          <Search size={14} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none" />
          <input
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="Buscar en documentación..."
            className="w-full pl-9 pr-3 py-2.5 rounded-xl text-sm outline-none transition-colors"
            style={{ background: 'var(--input-bg)', border: '1px solid var(--input-border)', color: 'var(--text-pri)' }}
          />
        </div>
      </motion.div>

      {isSearching ? (
        <SearchResults query={query} results={results} onOpen={setSelectedArticleId} />
      ) : (
        <>
          {/* Banner principal */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35, delay: 0.05 }}
            className="relative overflow-hidden rounded-2xl p-7 flex items-center justify-between gap-6"
            style={{
              background: 'linear-gradient(135deg, rgba(99,102,241,0.14) 0%, rgba(124,58,237,0.08) 100%)',
              border: '1px solid rgba(99,102,241,0.25)',
              boxShadow: 'var(--panel-shadow)',
            }}
          >
            <div className="absolute inset-0 pointer-events-none"
              style={{ background: 'radial-gradient(ellipse at top right, rgba(99,102,241,0.2) 0%, transparent 60%)' }} />

            <div className="relative flex items-start gap-4 min-w-0">
              <div
                className="w-14 h-14 rounded-2xl flex items-center justify-center flex-shrink-0"
                style={{ background: 'rgba(99,102,241,0.2)', border: '1px solid rgba(99,102,241,0.35)', boxShadow: '0 0 24px rgba(99,102,241,0.25)' }}
              >
                <BookOpen size={26} className="text-indigo-300" />
              </div>
              <div>
                <h2 className="text-lg font-extrabold" style={{ color: 'var(--text-pri)' }}>
                  Todo lo que necesitas para comenzar
                </h2>
                <p className="text-sm mt-1.5 max-w-md" style={{ color: 'var(--text-dim)' }}>
                  Encuentra guías, tutoriales y referencias para aprovechar al máximo Automation QA.
                </p>
              </div>
            </div>

            {/* Ilustración abstracta */}
            <div className="relative hidden md:flex items-center justify-center flex-shrink-0 w-32 h-32">
              <motion.div
                className="absolute w-24 h-24 rounded-3xl"
                style={{ background: 'linear-gradient(135deg, rgba(99,102,241,0.35), rgba(124,58,237,0.15))', border: '1px solid rgba(99,102,241,0.3)' }}
                animate={{ rotate: [0, 8, 0] }}
                transition={{ duration: 6, repeat: Infinity, ease: 'easeInOut' }}
              />
              <Sparkles size={40} className="relative text-indigo-300" style={{ filter: 'drop-shadow(0 0 12px rgba(129,140,248,0.6))' }} />
            </div>
          </motion.div>

          {/* Guías Rápidas */}
          <section>
            <SectionTitle>Guías Rápidas</SectionTitle>
            <div className="grid gap-4" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
              {QUICK_GUIDES.map((g, i) => (
                <QuickGuideCard key={g.articleId} guide={g} index={i} onOpen={() => setSelectedArticleId(g.articleId)} />
              ))}
            </div>
          </section>

          {/* Documentación por Categoría */}
          <section>
            <SectionTitle>Documentación por Categoría</SectionTitle>
            <div className="grid gap-4" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
              {DOC_CATEGORIES.map((cat, i) => (
                <CategoryCard key={cat.id} categoryId={cat.id} index={i} onOpenArticle={setSelectedArticleId} />
              ))}
            </div>
          </section>

          {/* Recursos Adicionales */}
          <section>
            <SectionTitle>Recursos Adicionales</SectionTitle>
            <div className="grid gap-4" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}>
              {RESOURCES.map((r, i) => (
                <ResourceCard
                  key={r.target}
                  resource={r}
                  index={i}
                  onOpen={() => r.kind === 'page' ? onNavigate?.(r.target) : setSelectedArticleId(r.target)}
                />
              ))}
            </div>
          </section>

          {/* Mensaje final */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.35, delay: 0.3 }}
            className="flex flex-col items-center justify-center gap-2 py-8 text-center rounded-2xl"
            style={{ background: 'var(--panel-bg)', border: '1px dashed var(--panel-border)' }}
          >
            <MessageCircle size={22} className="text-slate-500" />
            <div className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>¿No encuentras lo que buscas?</div>
            <button
              onClick={() => onNavigate?.('support')}
              className="text-sm font-semibold hover:underline"
              style={{ color: '#818cf8' }}
            >
              Contacta a nuestro equipo de soporte
            </button>
          </motion.div>
        </>
      )}
    </div>
  )
}

// ── Sub-components ───────────────────────────────────────────────────────────

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <div className="text-[11px] font-bold tracking-widest text-slate-500 uppercase mb-3">
      {children}
    </div>
  )
}

function QuickGuideCard({ guide, index, onOpen }: {
  guide: { title: string; description: string; icon: typeof Rocket; color: string }
  index: number
  onOpen: () => void
}) {
  const Icon = guide.icon
  return (
    <motion.button
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.05, duration: 0.35 }}
      whileHover={{ y: -3 }}
      onClick={onOpen}
      className="text-left rounded-2xl p-4 flex flex-col gap-3 transition-shadow"
      style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}
    >
      <div
        className="w-10 h-10 rounded-xl flex items-center justify-center"
        style={{ background: `${guide.color}22`, color: guide.color, boxShadow: `0 0 12px ${guide.color}33` }}
      >
        <Icon size={18} />
      </div>
      <div>
        <div className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>{guide.title}</div>
        <p className="text-xs mt-1 leading-relaxed" style={{ color: 'var(--text-dim)' }}>{guide.description}</p>
      </div>
      <div className="text-xs font-semibold flex items-center gap-1" style={{ color: guide.color }}>
        Ver guía <ArrowRight size={12} />
      </div>
    </motion.button>
  )
}

function CategoryCard({ categoryId, index, onOpenArticle }: {
  categoryId: DocumentationArticle['category']
  index: number
  onOpenArticle: (id: string) => void
}) {
  const category = DOC_CATEGORIES.find(c => c.id === categoryId)!
  const articles = getArticlesByCategory(categoryId).slice(0, 4)
  const Icon = category.icon

  return (
    <motion.div
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.06, duration: 0.35 }}
      className="relative overflow-hidden rounded-2xl flex flex-col"
      style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}
    >
      <div className="absolute top-0 left-0 right-0 h-px" style={{ background: `linear-gradient(90deg, transparent, ${category.color}, transparent)` }} />

      <div className="p-4 pb-2 flex items-center gap-3">
        <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
          style={{ background: `${category.color}22`, color: category.color, boxShadow: `0 0 12px ${category.color}33` }}>
          <Icon size={17} />
        </div>
        <span className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>{category.title}</span>
      </div>

      <div className="px-4 py-2 flex-1 space-y-0.5">
        {articles.map(a => (
          <button
            key={a.id}
            onClick={() => onOpenArticle(a.id)}
            className="w-full text-left text-xs py-1.5 px-2 rounded-lg transition-colors truncate block"
            style={{ color: 'var(--text-sec)' }}
            onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.background = 'rgba(255,255,255,0.04)' }}
            onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
          >
            {a.title}
          </button>
        ))}
      </div>

      <button
        onClick={() => onOpenArticle(articles[0]?.id)}
        className="text-xs font-semibold flex items-center gap-1 px-4 py-3 mt-1"
        style={{ color: category.color, borderTop: '1px solid var(--panel-divide)' }}
      >
        Ver {articles.length} artículos <ArrowRight size={12} />
      </button>
    </motion.div>
  )
}

function ResourceCard({ resource, index, onOpen }: {
  resource: { title: string; description: string; icon: typeof Play; color: string }
  index: number
  onOpen: () => void
}) {
  const Icon = resource.icon
  return (
    <motion.button
      initial={{ opacity: 0, y: 14 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.05, duration: 0.35 }}
      whileHover={{ y: -3 }}
      onClick={onOpen}
      className="text-left rounded-2xl p-4 flex items-center gap-3"
      style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}
    >
      <div className="w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0"
        style={{ background: `${resource.color}22`, color: resource.color, boxShadow: `0 0 12px ${resource.color}33` }}>
        <Icon size={18} />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-sm font-bold truncate" style={{ color: 'var(--text-pri)' }}>{resource.title}</div>
        <div className="text-xs mt-0.5 truncate" style={{ color: 'var(--text-dim)' }}>{resource.description}</div>
      </div>
      <ArrowRight size={14} className="flex-shrink-0" style={{ color: 'var(--text-dim)' }} />
    </motion.button>
  )
}

function SearchResults({ query, results, onOpen }: {
  query: string; results: DocumentationArticle[]; onOpen: (id: string) => void
}) {
  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} transition={{ duration: 0.25 }}>
      <SectionTitle>
        {results.length} resultado{results.length !== 1 ? 's' : ''} para "{query}"
      </SectionTitle>
      {results.length === 0 ? (
        <div
          className="rounded-2xl p-8 text-center text-sm"
          style={{ background: 'var(--panel-bg)', border: '1px dashed var(--panel-border)', color: 'var(--text-dim)' }}
        >
          No encontramos artículos que coincidan con tu búsqueda.
        </div>
      ) : (
        <div className="grid gap-4" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))' }}>
          {results.map((a, i) => {
            const category = DOC_CATEGORIES.find(c => c.id === a.category)
            const Icon = a.icon
            return (
              <motion.button
                key={a.id}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.03, duration: 0.3 }}
                onClick={() => onOpen(a.id)}
                className="text-left rounded-2xl p-4 flex flex-col gap-2"
                style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}
              >
                <div className="flex items-center gap-2">
                  <div className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
                    style={{
                      background: `${category?.color ?? '#6366f1'}22`, color: category?.color ?? '#6366f1',
                      boxShadow: `0 0 10px ${category?.color ?? '#6366f1'}33`,
                    }}>
                    <Icon size={15} />
                  </div>
                  <span className="text-[10px] font-bold uppercase tracking-wide" style={{ color: category?.color ?? 'var(--text-dim)' }}>
                    {category?.title ?? a.category}
                  </span>
                </div>
                <div className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>{a.title}</div>
                <p className="text-xs leading-relaxed" style={{ color: 'var(--text-dim)' }}>{a.description}</p>
              </motion.button>
            )
          })}
        </div>
      )}
    </motion.div>
  )
}
