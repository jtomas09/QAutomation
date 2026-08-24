import { useState } from 'react'
import { ThumbsUp, ThumbsDown } from 'lucide-react'

/** Encuesta rápida de utilidad — reutiliza los tokens ok/fail ya usados en Reportes (PASSED/FAILED). */
export default function DocumentationFeedback() {
  const [choice, setChoice] = useState<'yes' | 'no' | null>(null)

  return (
    <div className="rounded-2xl p-4" style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}>
      <div className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>¿Te resultó útil esta guía?</div>
      <p className="text-xs mt-1" style={{ color: 'var(--text-dim)' }}>Tu opinión nos ayuda a mejorar.</p>

      {choice ? (
        <p className="text-xs mt-3 font-semibold" style={{ color: choice === 'yes' ? 'var(--color-ok)' : 'var(--color-fail)' }}>
          {choice === 'yes' ? 'Gracias por tu feedback.' : 'Gracias, revisaremos esta guía.'}
        </p>
      ) : (
        <div className="flex items-center gap-2 mt-3">
          <button
            onClick={() => setChoice('yes')}
            className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-semibold transition-colors"
            style={{ background: 'rgba(16,185,129,0.12)', border: '1px solid rgba(16,185,129,0.3)', color: 'var(--color-ok)' }}
          >
            <ThumbsUp size={13} /> Sí, fue útil
          </button>
          <button
            onClick={() => setChoice('no')}
            className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg text-xs font-semibold transition-colors"
            style={{ background: 'rgba(244,63,94,0.10)', border: '1px solid rgba(244,63,94,0.28)', color: 'var(--color-fail)' }}
          >
            <ThumbsDown size={13} /> No, necesito ayuda
          </button>
        </div>
      )}
    </div>
  )
}
