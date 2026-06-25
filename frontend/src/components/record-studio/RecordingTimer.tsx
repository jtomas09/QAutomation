import React from 'react'

interface RecordingTimerProps {
  elapsed: number
}

function fmt(secs: number): string {
  const h = Math.floor(secs / 3600)
  const m = Math.floor((secs % 3600) / 60)
  const s = secs % 60
  return [h, m, s].map((n) => String(n).padStart(2, '0')).join(':')
}

export function RecordingTimer({ elapsed }: RecordingTimerProps) {
  return (
    <span
      style={{
        fontFamily: '"JetBrains Mono", "Fira Code", "Courier New", monospace',
        fontSize: 22,
        fontWeight: 700,
        color: '#e2e8f0',
        letterSpacing: 3,
        lineHeight: 1,
        display: 'block',
      }}
    >
      {fmt(elapsed)}
    </span>
  )
}
