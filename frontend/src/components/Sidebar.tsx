import React from 'react'
import type { Country } from '../types'
import { COUNTRIES } from '../data'
import s from './Sidebar.module.css'

interface Props {
  selected: string
  onSelect: (id: string) => void
}

export default function Sidebar({ selected, onSelect }: Props) {
  return (
    <aside className={s.sidebar}>
      {/* Brand */}
      <div className={s.brand}>
        <div className={s.brandIcon}>C</div>
        <div>
          <div className={s.brandName}>AUTOMATION QA</div>
          <div className={s.brandSub}>Test Launcher</div>
        </div>
      </div>

      <div className={s.divider} />

      {/* Country list */}
      <div className={s.section}>
        <div className={s.sectionLabel}>SELECCIONA UN PAÍS</div>
        <ul className={s.list}>
          {COUNTRIES.map((c: Country) => (
            <li key={c.id}>
              <button
                className={`${s.item} ${selected === c.id ? s.itemActive : ''}`}
                onClick={() => onSelect(c.id)}
              >
                <span className={s.flag}>{c.flag}</span>
                <span className={s.itemName}>{c.name}</span>
                {c.hasSubMenu && <span className={s.chevron}>›</span>}
              </button>
            </li>
          ))}
        </ul>
      </div>

      {/* Bottom */}
      <div className={s.bottom}>
        <div className={s.divider} />
        <div className={s.infoRow}>
          <span className={s.infoIcon}>⚡</span>
          <div>
            <div className={s.infoTitle}>Ejecución inteligente</div>
            <div className={s.infoSub}>Automatiza, valida y entrega mejores experiencias.</div>
          </div>
        </div>
        <div className={s.divider} />
        <div className={s.verLabel}>INFORMACIÓN DEL ENTORNO</div>
        <div className={s.version}>Versión: 1.0.0</div>
      </div>
    </aside>
  )
}
