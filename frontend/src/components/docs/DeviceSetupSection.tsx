import SetupStepFlow from './SetupStepFlow'
import RequirementsAccordion from './RequirementsAccordion'
import { STEP_ACCENT, type OsSetupConfig } from '../../data/deviceSetupGuide'

/** Sección completa "Conectar Dispositivo X" — Android e iOS reutilizan exactamente este componente. */
export default function DeviceSetupSection({ config }: { config: OsSetupConfig }) {
  return (
    <section id={config.sectionId} className="space-y-4 scroll-mt-24">
      <div>
        <h2 className="text-base font-extrabold" style={{ color: 'var(--text-pri)' }}>{config.heading}</h2>
        <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>{config.subtitle}</p>
      </div>

      <div id={config.stepsId} className="scroll-mt-24">
        <SetupStepFlow steps={config.steps} accent={STEP_ACCENT} />
      </div>

      <RequirementsAccordion id={config.requirementsId} title={config.requirementsTitle} items={config.requirements} />
    </section>
  )
}
