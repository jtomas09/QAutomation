import { MANAGEMENT_CARDS } from '../../data/deviceSetupGuide'

/** Grid de 4 tarjetas iguales — mismo tamaño/padding/radius/hover que el resto del Dashboard. */
export default function DeviceManagementCards() {
  return (
    <div className="grid gap-4" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
      {MANAGEMENT_CARDS.map((card, i) => (
        <div
          key={i}
          id={card.anchorId}
          className="rounded-2xl p-4 flex flex-col gap-3 transition-transform scroll-mt-24"
          style={{ background: 'var(--panel-bg)', border: '1px solid var(--panel-border)', boxShadow: 'var(--panel-shadow)' }}
          onMouseEnter={e => { (e.currentTarget as HTMLDivElement).style.transform = 'translateY(-3px)' }}
          onMouseLeave={e => { (e.currentTarget as HTMLDivElement).style.transform = 'translateY(0)' }}
        >
          <div
            className="w-10 h-10 rounded-xl flex items-center justify-center"
            style={{ background: `${card.color}22`, color: card.color, boxShadow: `0 0 12px ${card.color}33` }}
          >
            <card.icon size={18} />
          </div>
          <div>
            <div className="text-sm font-bold" style={{ color: 'var(--text-pri)' }}>{card.title}</div>
            <p className="text-xs mt-1 leading-relaxed" style={{ color: 'var(--text-dim)' }}>{card.description}</p>
          </div>
        </div>
      ))}
    </div>
  )
}
