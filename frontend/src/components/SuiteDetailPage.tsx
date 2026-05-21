import type { TestSuite, IndividualTest } from '../types'

interface Props {
  suite: TestSuite
  tests: IndividualTest[]
  disabled: boolean
  activeId: string | null
  onBack: () => void
  onRun: (id: string) => void
  onRunAll: () => void
}

export default function SuiteDetailPage({
  suite, tests, disabled, activeId, onBack, onRun, onRunAll,
}: Props) {
  const { r, g, b } = hexRgb(suite.accent)

  return (
    <div className="p-7">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-4">
          <button
            onClick={onBack}
            className="flex items-center gap-2 px-3 py-1.5 rounded-md text-sm font-medium transition-colors"
            style={{
              background: 'var(--bg-card)',
              color: 'var(--text-secondary)',
              border: '1px solid var(--border)',
            }}
          >
            ← Volver
          </button>
          <div className="flex items-center gap-2">
            <span className="text-xl">{suite.icon}</span>
            <span
              className="text-[11px] font-bold tracking-widest uppercase"
              style={{ color: suite.accent }}
            >
              {suite.title} — Tests Individuales
            </span>
          </div>
        </div>

        <button
          onClick={() => !disabled && onRunAll()}
          disabled={disabled}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold transition-opacity"
          style={{
            background: suite.accent,
            color: '#fff',
            opacity: disabled ? 0.45 : 1,
            cursor: disabled ? 'not-allowed' : 'pointer',
          }}
        >
          ▶ Ejecutar Todos
        </button>
      </div>

      {/* Test list */}
      <div
        className="rounded-xl overflow-hidden"
        style={{ border: '1px solid var(--border)' }}
      >
        {tests.map((test, idx) => {
          const isActive = activeId === test.id
          return (
            <div
              key={test.id}
              className="flex items-center gap-4 px-5 py-4 transition-colors"
              style={{
                background: isActive
                  ? `rgba(${r},${g},${b},.12)`
                  : idx % 2 === 0
                  ? 'var(--bg-card)'
                  : 'var(--bg-card-alt, var(--bg-card))',
                borderBottom: idx < tests.length - 1 ? '1px solid var(--border)' : 'none',
              }}
            >
              {/* Number badge */}
              <div
                className="flex-none w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold"
                style={{
                  background: isActive ? suite.accent : `rgba(${r},${g},${b},.18)`,
                  color: isActive ? '#fff' : suite.accent,
                }}
              >
                {idx + 1}
              </div>

              {/* Title + description */}
              <div className="flex-1 min-w-0">
                <div
                  className="text-sm font-semibold truncate"
                  style={{ color: 'var(--text-primary)' }}
                >
                  {test.title}
                </div>
                <div
                  className="text-xs mt-0.5 truncate"
                  style={{ color: 'var(--text-secondary)' }}
                >
                  {test.description}
                </div>
              </div>

              {/* Run button */}
              <button
                onClick={() => !disabled && onRun(test.id)}
                disabled={disabled}
                className="flex-none flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-xs font-semibold transition-opacity"
                style={{
                  background: isActive ? suite.accent : `rgba(${r},${g},${b},.18)`,
                  color: isActive ? '#fff' : suite.accent,
                  opacity: disabled ? 0.45 : 1,
                  cursor: disabled ? 'not-allowed' : 'pointer',
                }}
              >
                ▶ Ejecutar
              </button>
            </div>
          )
        })}
      </div>
    </div>
  )
}

function hexRgb(hex: string) {
  const n = parseInt(hex.replace('#', ''), 16)
  return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 }
}
