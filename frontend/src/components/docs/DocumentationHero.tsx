import type { LucideIcon } from 'lucide-react'
import { Clock, Signal, RefreshCw } from 'lucide-react'
import { AppleLogo, AndroidLogo } from '../icons/BrandIcons'

interface MetaItem {
  icon:  LucideIcon
  label: string
}

interface Props {
  icon:        LucideIcon
  color:       string
  title:       string
  subtitle:    string
  meta:        { tiempoEstimado: string; nivel: string; ultimaActualizacion: string }
}

/** Header horizontal de un artículo enriquecido — icono + título + meta + ilustración discreta. */
export default function DocumentationHero({ icon: Icon, color, title, subtitle, meta }: Props) {
  const metaItems: MetaItem[] = [
    { icon: Clock, label: `Tiempo estimado: ${meta.tiempoEstimado}` },
    { icon: Signal, label: meta.nivel },
    { icon: RefreshCw, label: `Última actualización: ${meta.ultimaActualizacion}` },
  ]

  return (
    <div
      className="relative overflow-hidden rounded-2xl p-6 flex items-center justify-between gap-6 flex-wrap"
      style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}
    >
      <div className="absolute top-0 left-0 right-0 h-px" style={{ background: `linear-gradient(90deg, transparent, ${color}, transparent)` }} />

      <div className="relative flex items-start gap-4 min-w-0">
        <div
          className="w-14 h-14 rounded-2xl flex items-center justify-center flex-shrink-0"
          style={{ background: `${color}22`, color, boxShadow: `0 0 20px ${color}33`, border: `1px solid ${color}44` }}
        >
          <Icon size={26} />
        </div>
        <div>
          <h1 className="text-xl font-extrabold" style={{ color: 'var(--text-pri)' }}>{title}</h1>
          <p className="text-sm mt-1 max-w-md" style={{ color: 'var(--text-dim)' }}>{subtitle}</p>
          <div className="flex items-center gap-4 mt-3 flex-wrap">
            {metaItems.map((m, i) => (
              <div key={i} className="flex items-center gap-1.5 text-xs" style={{ color: 'var(--text-dim)' }}>
                <m.icon size={13} />
                {m.label}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Ilustración discreta Android + iOS — logotipos oficiales reales (vectoriales), ver BrandIcons.tsx */}
      <div className="relative hidden md:flex items-center justify-center flex-shrink-0" style={{ width: 150, height: 110 }}>
        <div
          className="absolute w-20 h-28 rounded-2xl flex items-center justify-center"
          style={{ left: 8, background: '#0c1226', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}
        >
          <AppleLogo size={26} className="text-slate-300" />
        </div>
        <div
          className="absolute w-20 h-28 rounded-2xl flex items-center justify-center"
          style={{ right: 8, background: 'rgba(16,185,129,0.10)', border: '1px solid rgba(16,185,129,0.3)', boxShadow: '0 0 20px rgba(16,185,129,0.18)' }}
        >
          <AndroidLogo size={26} className="text-emerald-500" />
        </div>
      </div>
    </div>
  )
}
