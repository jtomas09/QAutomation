import React from 'react'
import { Film } from 'lucide-react'
import type { VideoRecord } from '../../types'
import VideoCard from './VideoCard'
import styles from './VideoGallery.module.css'

interface Props {
  videos:       VideoRecord[]
  loading:      boolean
  total:        number
  page:         number
  pageSize:     number
  onPageChange: (page: number) => void
  playingId:    string | null
  onPlay:       (id: string) => void
  onEnded:      (id: string) => void
  deletingId:   string | null
  onDelete:     (id: string) => void
}

export default function VideoGallery({
  videos, loading, total, page, pageSize, onPageChange,
  playingId, onPlay, onEnded, deletingId, onDelete,
}: Props) {
  const totalPages = Math.max(1, Math.ceil(total / pageSize))

  if (loading) {
    return <div className={styles.loading}>Cargando videos…</div>
  }

  if (videos.length === 0) {
    return (
      <div className={styles.empty}>
        <Film size={40} style={{ opacity: 0.3 }} />
        <span>No hay videos que coincidan con los filtros actuales</span>
      </div>
    )
  }

  return (
    <div>
      <div className={styles.grid}>
        {videos.map(v => (
          <VideoCard
            key={v.id}
            video={v}
            playing={playingId === v.id}
            deleting={deletingId === v.id}
            onPlay={onPlay}
            onEnded={onEnded}
            onDelete={onDelete}
          />
        ))}
      </div>

      {totalPages > 1 && (
        <div className={styles.pagination}>
          <button className={styles.pageBtn} disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
            ← Anterior
          </button>
          <span className={styles.pageInfo}>Página {page + 1} de {totalPages} · {total} video{total !== 1 ? 's' : ''}</span>
          <button className={styles.pageBtn} disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
            Siguiente →
          </button>
        </div>
      )}
    </div>
  )
}
