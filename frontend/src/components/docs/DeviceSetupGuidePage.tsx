import { motion } from 'framer-motion'
import { ArrowLeft, Smartphone, Wrench, AlertTriangle, ShieldCheck, ArrowRight } from 'lucide-react'
import DocumentationBreadcrumb from './DocumentationBreadcrumb'
import DocumentationHero from './DocumentationHero'
import ImportantNotice from './ImportantNotice'
import DeviceSetupSection from './DeviceSetupSection'
import DeviceManagementCards from './DeviceManagementCards'
import DocumentationLayout from './DocumentationLayout'
import DocumentationToc from './DocumentationToc'
import DocumentationFeedback from './DocumentationFeedback'
import RelatedArticles from './RelatedArticles'
import SupportCard from './SupportCard'
import {
  ANDROID_SETUP, IOS_SETUP, GUIDE_TOC, GUIDE_META, RELATED_ARTICLE_IDS,
} from '../../data/deviceSetupGuide'

interface Props {
  onBack:          () => void
  onSelectArticle: (id: string) => void
  onGoToSupport:   () => void
}

/**
 * Guía enriquecida "Configuración de Dispositivos" — el único artículo de Documentación
 * con layout propio (hero + pasos Android/iOS + gestión + índice lateral), reproduciendo
 * el diseño de referencia entregado por el usuario. El resto de artículos siguen usando
 * el ArticleDetail genérico sin cambios.
 */
export default function DeviceSetupGuidePage({ onBack, onSelectArticle, onGoToSupport }: Props) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.25 }}
      className="p-6 space-y-5"
    >
      <DocumentationBreadcrumb
        crumbs={[
          { label: 'Documentación', onClick: onBack },
          { label: 'Guías Rápidas', onClick: onBack },
          { label: 'Configuración de Dispositivos' },
        ]}
      />

      <button
        onClick={onBack}
        className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors"
        style={{ background: 'var(--btn-bg)', border: '1px solid var(--btn-border)', color: 'var(--text-sec)' }}
      >
        <ArrowLeft size={13} />
        Volver a Documentación
      </button>

      <DocumentationLayout
        sidebar={
          <>
            <DocumentationToc items={GUIDE_TOC} />
            <DocumentationFeedback />
            <RelatedArticles articleIds={RELATED_ARTICLE_IDS} onOpen={onSelectArticle} />
            <SupportCard onGoToSupport={onGoToSupport} />
          </>
        }
      >
        <DocumentationHero
          icon={Smartphone}
          color="#10b981"
          title="Configuración de Dispositivos"
          subtitle="Aprende a conectar y configurar dispositivos Android e iOS."
          meta={GUIDE_META}
        />

        <ImportantNotice>
          Para ejecutar pruebas correctamente, el dispositivo debe estar conectado, reconocido por el Runner y configurado en Automation QA.
        </ImportantNotice>

        <DeviceSetupSection config={ANDROID_SETUP} />
        <DeviceSetupSection config={IOS_SETUP} />

        <section id="gestion" className="space-y-4 scroll-mt-24">
          <div>
            <h2 className="text-base font-extrabold" style={{ color: 'var(--text-pri)' }}>3. Gestionar Dispositivos Configurados</h2>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
              Una vez conectado, puedes gestionar los dispositivos disponibles para ejecución.
            </p>
          </div>
          <DeviceManagementCards />
        </section>

        <section id="problemas" className="space-y-4 scroll-mt-24">
          <div>
            <h2 className="text-base font-extrabold" style={{ color: 'var(--text-pri)' }}>4. Solución de Problemas</h2>
            <p className="text-sm mt-1" style={{ color: 'var(--text-dim)' }}>
              Qué revisar cuando un dispositivo no se conecta o no aparece disponible.
            </p>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div id="problemas-comunes" className="rounded-2xl p-4 flex items-start gap-3 scroll-mt-24"
              style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}>
              <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: 'rgba(245,158,11,0.15)', color: '#f59e0b' }}>
                <AlertTriangle size={16} />
              </div>
              <div>
                <div className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>Problemas Comunes</div>
                <p className="text-xs mt-1 leading-relaxed" style={{ color: 'var(--text-dim)' }}>
                  El dispositivo no aparece, se desconecta o el Runner no lo reconoce. Revisa el cable, la confianza/depuración USB y que el Runner Agent esté corriendo.
                </p>
              </div>
            </div>
            <div id="problemas-verificaciones" className="rounded-2xl p-4 flex items-start gap-3 scroll-mt-24"
              style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}>
              <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: 'rgba(56,189,248,0.15)', color: '#38bdf8' }}>
                <ShieldCheck size={16} />
              </div>
              <div>
                <div className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>Verificaciones</div>
                <p className="text-xs mt-1 leading-relaxed" style={{ color: 'var(--text-dim)' }}>
                  Confirma el estado del dispositivo en Dispositivos Conectados y que el Backend/Runner muestren estado Online antes de ejecutar.
                </p>
              </div>
            </div>
          </div>
        </section>

        <section id="proximos" className="rounded-2xl p-5 flex items-center justify-between gap-4 flex-wrap scroll-mt-24"
          style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: 'rgba(139,92,246,0.15)', color: '#8b5cf6' }}>
              <Wrench size={16} />
            </div>
            <div>
              <div className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>Próximos Pasos</div>
              <p className="text-xs mt-1" style={{ color: 'var(--text-dim)' }}>Con tu dispositivo configurado, continúa ejecutando tus primeras pruebas.</p>
            </div>
          </div>
          <button
            onClick={() => onSelectArticle('ejecutar-suites')}
            className="text-xs font-semibold flex items-center gap-1 flex-shrink-0"
            style={{ color: '#8b5cf6' }}
          >
            Ejecutar Pruebas <ArrowRight size={12} />
          </button>
        </section>
      </DocumentationLayout>
    </motion.div>
  )
}
