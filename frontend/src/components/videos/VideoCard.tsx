import React, { useEffect, useRef, useState } from 'react'
import { Play, Download, Trash2 } from 'lucide-react'
import type { VideoRecord } from '../../types'
import { getVideoFileUrl } from '../../api'
import { statusColor, fmtSize, fmtDate, fmtDuration } from './videoVisuals'
import { cleanBonjourHostname } from '../../utils/displayNames'
import styles from './VideoCard.module.css'

interface Props {
  video:     VideoRecord
  playing:   boolean
  deleting:  boolean
  onPlay:    (id: string) => void
  onEnded:   (id: string) => void
  onDelete:  (id: string) => void
}

/** Observa si el card entró al viewport — evita cargar metadata de video para miles de cards ocultos. */
function useInView<T extends HTMLElement>() {
  const ref = useRef<T | null>(null)
  const [inView, setInView] = useState(false)
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const obs = new IntersectionObserver(
      entries => { if (entries[0]?.isIntersecting) { setInView(true); obs.disconnect() } },
      { rootMargin: '200px' },
    )
    obs.observe(el)
    return () => obs.disconnect()
  }, [])
  return { ref, inView }
}

export default function VideoCard({ video, playing, deleting, onPlay, onEnded, onDelete }: Props) {
  const { ref, inView } = useInView<HTMLDivElement>()
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const [duration, setDuration] = useState<number | null>(null)

  useEffect(() => {
    if (playing && videoRef.current) {
      videoRef.current.currentTime = 0
      videoRef.current.play().catch(() => { /* autoplay bloqueado — el usuario ya interactuó, reintenta con controles */ })
    }
  }, [playing])

  const fileUrl = getVideoFileUrl(video.id, false)
  const color   = statusColor(video.status)

  function handleDownload() {
    const a = document.createElement('a')
    a.href = getVideoFileUrl(video.id, true)
    a.download = video.originalName
    a.click()
  }

  return (
    <div className={styles.card} ref={ref}>
      <div className={styles.thumbWrap}>
        {inView && (
          <video
            ref={videoRef}
            className={styles.thumb}
            src={fileUrl}
            preload="metadata"
            muted={!playing}
            controls={playing}
            playsInline
            onLoadedMetadata={e => setDuration(e.currentTarget.duration)}
            onEnded={() => onEnded(video.id)}
          />
        )}
        <div className={styles.statusDot} style={{ background: color }} title={video.status ?? 'UNKNOWN'} />
        {!playing && (
          <button className={styles.playOverlay} onClick={() => onPlay(video.id)} aria-label="Reproducir">
            <span className={styles.playIcon}><Play size={16} fill="#fff" /></span>
          </button>
        )}
        {!playing && <span className={styles.duration}>{fmtDuration(duration)}</span>}
      </div>

      <div className={styles.body}>
        <div className={styles.name} title={video.testName || video.originalName}>
          {video.testName || video.originalName}
        </div>
        <div className={styles.meta}>
          <span className={styles.metaChip}>{fmtSize(video.sizeBytes)}</span>
          <span className={styles.metaChip}>{fmtDate(video.createdAt)}</span>
          {video.device && <span className={styles.metaChip}>{cleanBonjourHostname(video.device)}</span>}
          {video.env && <span className={styles.metaChip}>{video.env}</span>}
        </div>
        <div className={styles.actions}>
          <button className={styles.actionBtn} onClick={handleDownload}>
            <Download size={12} /> Descargar
          </button>
          <button
            className={`${styles.actionBtn} ${styles.deleteBtn}`}
            disabled={deleting}
            onClick={() => onDelete(video.id)}
          >
            <Trash2 size={12} /> {deleting ? '…' : 'Eliminar'}
          </button>
        </div>
      </div>
    </div>
  )
}
