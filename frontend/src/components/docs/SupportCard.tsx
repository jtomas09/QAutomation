import { LifeBuoy } from 'lucide-react'

/** CTA de soporte — mismo botón primario morado que "Descargar Agent" en el Sidebar. */
export default function SupportCard({ onGoToSupport }: { onGoToSupport: () => void }) {
  return (
    <div
      className="rounded-2xl p-4 relative overflow-hidden"
      style={{ background: 'linear-gradient(135deg, rgba(99,102,241,0.12) 0%, rgba(124,58,237,0.08) 100%)', border: '1px solid rgba(99,102,241,0.22)' }}
    >
      <div className="absolute inset-0 pointer-events-none" style={{ background: 'radial-gradient(ellipse at top right, rgba(99,102,241,0.18) 0%, transparent 60%)' }} />
      <div className="relative">
        <div className="flex items-center gap-2 mb-2">
          <div className="w-6 h-6 rounded-lg flex items-center justify-center flex-shrink-0" style={{ background: 'rgba(99,102,241,0.2)', border: '1px solid rgba(99,102,241,0.3)' }}>
            <LifeBuoy size={12} className="text-indigo-400" />
          </div>
          <span className="text-xs font-bold" style={{ color: 'var(--text-pri)' }}>¿Necesitas ayuda?</span>
        </div>
        <p className="text-[11px] leading-relaxed mb-3" style={{ color: 'var(--text-dim)' }}>
          Nuestro equipo de soporte está listo para ayudarte.
        </p>
        <button
          onClick={onGoToSupport}
          className="w-full py-2 rounded-lg text-[11px] font-bold text-white flex items-center justify-center gap-1.5 transition-opacity"
          style={{ background: 'linear-gradient(135deg, #6366f1, #7c3aed)', boxShadow: '0 4px 12px rgba(99,102,241,0.35)' }}
          onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.opacity = '0.88' }}
          onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.opacity = '1' }}
        >
          Ir a Soporte
        </button>
      </div>
    </div>
  )
}
