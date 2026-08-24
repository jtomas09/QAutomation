import { ArrowRight } from 'lucide-react'
import SetupStep from './SetupStep'
import type { SetupStepData } from '../../data/deviceSetupGuide'

/**
 * Fila horizontal de pasos numerados conectados por flechas — solo hay espacio real
 * para las 4 flechas en una sola fila en pantallas grandes (xl+, con Sidebar + índice
 * lateral ya ocupando espacio). Por debajo de xl se usa una grilla 2x2 sin flechas
 * para no forzar un ancho mínimo que genere scroll horizontal (tablet/mobile).
 */
export default function SetupStepFlow({ steps, accent }: { steps: SetupStepData[]; accent: string }) {
  return (
    <>
      <div className="hidden xl:flex items-start gap-3">
        {steps.map((step, i) => (
          <div key={i} className="flex items-start gap-3 flex-1 min-w-0">
            <SetupStep index={i + 1} icon={step.icon} title={step.title} description={step.description} accent={accent} />
            {i < steps.length - 1 && (
              <ArrowRight size={16} className="flex-shrink-0 mt-3" style={{ color: 'var(--text-dim)' }} />
            )}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-5 xl:hidden">
        {steps.map((step, i) => (
          <SetupStep key={i} index={i + 1} icon={step.icon} title={step.title} description={step.description} accent={accent} />
        ))}
      </div>
    </>
  )
}
