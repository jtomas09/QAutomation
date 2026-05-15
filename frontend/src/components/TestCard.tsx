import React, { useState } from 'react'
import type { TestSuite } from '../types'
import s from './TestCard.module.css'

interface Props {
  suite: TestSuite
  onRun: (id: string) => void
  disabled: boolean
  isActive: boolean
}

function hexRgb(hex: string) {
  const n = parseInt(hex.replace('#', ''), 16)
  return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
}

export default function TestCard({ suite, onRun, disabled, isActive }: Props) {
  const [hov, setHov] = useState(false)
  const { r, g, b } = hexRgb(suite.accent)

  const a1 = hov ? 0.38 : 0.26
  const a2 = hov ? 0.58 : 0.44
  const a3 = hov ? 0.80 : 0.64

  return (
    <div
      className={`${s.card} ${hov ? s.cardHov : ''} ${isActive ? s.cardActive : ''}`}
      style={{
        '--r': r, '--g': g, '--b': b,
        boxShadow: hov
          ? `0 0 0 2px rgba(${r},${g},${b},.55), 0 8px 28px rgba(0,0,0,.45)`
          : '4px 5px 0 rgba(0,0,0,.22), 2px 3px 0 rgba(0,0,0,.14)',
      } as React.CSSProperties}
      onMouseEnter={() => setHov(true)}
      onMouseLeave={() => setHov(false)}
    >
      {/* Radial glow bottom-left */}
      <div
        className={s.glow}
        style={{ background: `radial-gradient(ellipse at 28% 80%, rgba(${r},${g},${b},${hov ? .33 : .2}) 0%, transparent 65%)` }}
      />
      {hov && (
        <div
          className={s.glowTop}
          style={{ background: `radial-gradient(ellipse at 10% 10%, rgba(${r},${g},${b},.25) 0%, transparent 60%)` }}
        />
      )}

      {/* Content */}
      <div className={s.main}>
        {/* Circle icon */}
        <div className={s.iconWrap}>
          <div className={s.iconHalo} style={{ background: `rgba(${r},${g},${b},.18)` }} />
          <div className={s.icon} style={{ background: suite.accent }}>
            {suite.icon}
          </div>
        </div>

        {/* Text */}
        <div className={s.text}>
          <div className={s.title}>{suite.title}</div>
          <div className={s.desc}>{suite.description}</div>
        </div>
      </div>

      {/* Wave SVG (matches Java Bezier curves exactly) */}
      <div className={s.waveWrap}>
        <svg viewBox="0 0 100 42" preserveAspectRatio="none" className={s.wave}
          style={{ transition: 'opacity .2s' }}>
          <path
            d="M0,12.6 C25,0 65,17.6 100,5.9 L100,42 L0,42 Z"
            fill={`rgba(${r},${g},${b},${a1})`}
          />
          <path
            d="M0,21.8 C30,8.4 60,23.5 100,16 L100,42 L0,42 Z"
            fill={`rgba(${r},${g},${b},${a2})`}
          />
          <path
            d="M0,29.4 C22,18.5 68,31.1 100,25.2 L100,42 L0,42 Z"
            fill={`rgba(${r},${g},${b},${a3})`}
          />
        </svg>
      </div>

      {/* Button row */}
      <div className={s.footer}>
        <button
          className={s.execBtn}
          style={{ background: suite.accent }}
          onClick={() => !disabled && onRun(suite.id)}
          disabled={disabled}
        >
          ▶ Ejecutar
        </button>
      </div>
    </div>
  )
}
