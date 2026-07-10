import React from 'react'
import { ChevronRight } from 'lucide-react'
import type { VideoSuiteSummary } from '../../types'
import { suiteIconFor, statusColor, statusLabel, fmtSize, fmtDateShort } from './videoVisuals'
import styles from './SuiteCard.module.css'

interface Props {
  suite:    VideoSuiteSummary
  selected: boolean
  onSelect: (suiteName: string) => void
}

export default function SuiteCard({ suite, selected, onSelect }: Props) {
  const color = statusColor(suite.overallStatus)
  return (
    <button
      className={`${styles.card} ${selected ? styles.selected : ''}`}
      onClick={() => onSelect(suite.suiteName)}
    >
      <div className={styles.icon}>{suiteIconFor(suite.suiteName)}</div>
      <div className={styles.body}>
        <div className={styles.name}>{suite.suiteName}</div>
        <div className={styles.meta}>
          <span>{suite.videoCount} video{suite.videoCount !== 1 ? 's' : ''}</span>
          <span className={styles.dot}>•</span>
          <span>{fmtSize(suite.totalSizeBytes)}</span>
          <span className={styles.dot}>•</span>
          <span>{fmtDateShort(suite.lastExecutionAt)}</span>
        </div>
        <span className={styles.badge} style={{ background: `${color}22`, color }}>
          {statusLabel(suite.overallStatus)}
        </span>
      </div>
      <ChevronRight size={16} className={styles.chevron} />
    </button>
  )
}
