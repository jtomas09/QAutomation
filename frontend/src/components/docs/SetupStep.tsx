import type { LucideIcon } from 'lucide-react'

interface Props {
  index:       number
  icon:        LucideIcon
  title:       string
  description: string
  accent:      string
}

/** Un paso individual dentro de un SetupStepFlow — círculo numerado + icono + texto. */
export default function SetupStep({ index, icon: Icon, title, description, accent }: Props) {
  return (
    <div className="flex flex-col items-start gap-2 flex-1 min-w-[150px]">
      <div className="relative">
        <div
          className="w-12 h-12 rounded-xl flex items-center justify-center"
          style={{ background: 'var(--btn-bg)', border: '1px solid var(--btn-border)', color: 'var(--text-sec)' }}
        >
          <Icon size={20} />
        </div>
        <div
          className="absolute -top-2 -left-2 w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold text-white"
          style={{ background: accent, border: '2px solid var(--bg-panel)' }}
        >
          {index}
        </div>
      </div>
      <div className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>{title}</div>
      <p className="text-xs leading-relaxed" style={{ color: 'var(--text-dim)' }}>{description}</p>
    </div>
  )
}
