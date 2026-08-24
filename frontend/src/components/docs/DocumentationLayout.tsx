import { useState } from 'react'
import { PanelRightOpen, X } from 'lucide-react'

interface Props {
  children: React.ReactNode
  sidebar:  React.ReactNode
}

/**
 * Shell de 2 columnas para artículos enriquecidos de Documentación (contenido + índice
 * lateral). En desktop el índice queda sticky a la derecha; en mobile se oculta detrás
 * de un botón/drawer para no partir el layout en pantallas angostas.
 */
export default function DocumentationLayout({ children, sidebar }: Props) {
  const [mobileTocOpen, setMobileTocOpen] = useState(false)

  return (
    <div className="relative">
      <button
        onClick={() => setMobileTocOpen(true)}
        className="md:hidden fixed bottom-5 right-5 z-20 flex items-center gap-2 px-4 py-2.5 rounded-full text-xs font-bold text-white"
        style={{ background: 'linear-gradient(135deg, #6366f1, #7c3aed)', boxShadow: '0 4px 16px rgba(99,102,241,0.4)' }}
      >
        <PanelRightOpen size={14} /> Ver índice
      </button>

      {mobileTocOpen && (
        <div className="md:hidden fixed inset-0 z-30 flex justify-end" style={{ background: 'rgba(0,0,0,0.5)' }} onClick={() => setMobileTocOpen(false)}>
          <div
            className="w-[85%] max-w-xs h-full overflow-y-auto p-5 space-y-5"
            style={{ background: 'var(--bg-panel)', borderLeft: '1px solid var(--panel-border)' }}
            onClick={e => e.stopPropagation()}
          >
            <button onClick={() => setMobileTocOpen(false)} className="flex items-center gap-1.5 text-xs mb-2" style={{ color: 'var(--text-dim)' }}>
              <X size={14} /> Cerrar
            </button>
            {sidebar}
          </div>
        </div>
      )}

      <div className="flex flex-col md:flex-row gap-6 items-start">
        <div className="flex-1 min-w-0 space-y-6">{children}</div>
        <aside className="hidden md:block w-[220px] lg:w-[260px] xl:w-[280px] flex-shrink-0 sticky space-y-5" style={{ top: 24 }}>
          {sidebar}
        </aside>
      </div>
    </div>
  )
}
