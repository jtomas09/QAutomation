import { Info } from 'lucide-react'

/** Alerta informativa Enterprise — no un <div role="alert"> genérico de HTML. */
export default function ImportantNotice({ title = 'Importante', children }: { title?: string; children: React.ReactNode }) {
  return (
    <div
      className="rounded-2xl p-4 flex items-start gap-3"
      style={{ background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.25)' }}
    >
      <div
        className="w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0"
        style={{ background: 'rgba(99,102,241,0.18)', color: '#818cf8' }}
      >
        <Info size={16} />
      </div>
      <div>
        <div className="text-sm font-bold" style={{ color: '#a5b4fc' }}>{title}</div>
        <p className="text-sm mt-0.5 leading-relaxed" style={{ color: 'var(--text-sec)' }}>{children}</p>
      </div>
    </div>
  )
}
