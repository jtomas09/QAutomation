import React from 'react'
import s from './DailyChart.module.css'

const DAYS = ['16 May','17 May','18 May','19 May','20 May','21 May','22 May']
const DATA = {
  passed:  [80,  95,  88, 110, 145, 130, 120],
  failed:  [15,  12,  18,  20,  23,  17,  14],
  skipped: [8,   10,   6,  14,  12,   9,  10],
  total:   [103, 117, 112, 144, 180, 156, 144],
}

const SERIES = [
  { key: 'passed'  as const, color: '#22c55e', label: 'Passed'  },
  { key: 'failed'  as const, color: '#ef4444', label: 'Failed'  },
  { key: 'skipped' as const, color: '#eab308', label: 'Skipped' },
  { key: 'total'   as const, color: '#6366f1', label: 'Total'   },
]

const W = 400, H = 150, PAD_L = 30, PAD_B = 20, PAD_T = 10, PAD_R = 10

function scaleY(val: number, max: number) {
  return PAD_T + (H - PAD_T - PAD_B) * (1 - val / max)
}
function scaleX(i: number, n: number) {
  return PAD_L + (i / (n - 1)) * (W - PAD_L - PAD_R)
}

function polyline(values: number[], max: number): string {
  return values.map((v, i) => `${scaleX(i, values.length)},${scaleY(v, max)}`).join(' ')
}
function areaPath(values: number[], max: number): string {
  const pts = values.map((v, i) => `${scaleX(i, values.length)},${scaleY(v, max)}`).join(' L')
  const last = `${scaleX(values.length - 1, values.length)},${H - PAD_B}`
  const first = `${scaleX(0, values.length)},${H - PAD_B}`
  return `M ${pts} L ${last} L ${first} Z`
}

export default function DailyChart() {
  const maxVal = Math.max(...DATA.total) * 1.15

  return (
    <div className={s.card}>
      <div className={s.header}>
        <div>
          <div className={s.title}>Ejecuciones por Día</div>
          <div className={s.subtitle}>Últimos 7 días</div>
        </div>
        <div className={s.legend}>
          {SERIES.map(sr => (
            <span key={sr.key} className={s.legendItem}>
              <span className={s.legendDot} style={{ background: sr.color }} />
              {sr.label}
            </span>
          ))}
        </div>
      </div>

      <div className={s.chartWrap}>
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className={s.svg}>
          {/* Grid lines */}
          {[0,.25,.5,.75,1].map(t => {
            const y = PAD_T + (H - PAD_T - PAD_B) * t
            return (
              <g key={t}>
                <line x1={PAD_L} y1={y} x2={W - PAD_R} y2={y}
                  stroke="#1e2d55" strokeWidth=".5" />
                <text x={PAD_L - 4} y={y + 3.5} textAnchor="end"
                  fill="#7888b4" fontSize="8">
                  {Math.round(maxVal * (1 - t))}
                </text>
              </g>
            )
          })}

          {/* Area fills */}
          {SERIES.filter(sr => sr.key !== 'total').map(sr => (
            <path key={sr.key}
              d={areaPath(DATA[sr.key], maxVal)}
              fill={sr.color} fillOpacity=".06" />
          ))}

          {/* Lines */}
          {SERIES.map(sr => (
            <polyline key={sr.key}
              points={polyline(DATA[sr.key], maxVal)}
              fill="none"
              stroke={sr.color}
              strokeWidth={sr.key === 'total' ? 2 : 1.5}
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          ))}

          {/* Dots on last data point highlight */}
          {SERIES.map(sr => {
            const i = DATA[sr.key].length - 3
            const x = scaleX(i, DATA[sr.key].length)
            const y = scaleY(DATA[sr.key][i], maxVal)
            return (
              <circle key={sr.key} cx={x} cy={y} r="3"
                fill={sr.color} stroke="#0c1226" strokeWidth="1.5" />
            )
          })}

          {/* X axis labels */}
          {DAYS.map((d, i) => (
            <text key={d}
              x={scaleX(i, DAYS.length)} y={H - 4}
              textAnchor="middle" fill="#7888b4" fontSize="7.5">
              {d}
            </text>
          ))}
        </svg>
      </div>
    </div>
  )
}
