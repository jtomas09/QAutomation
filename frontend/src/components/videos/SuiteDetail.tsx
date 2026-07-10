import React, { useEffect, useState } from 'react'
import { Download, Play, Trash2, Search } from 'lucide-react'
import type { VideoSuiteSummary, VideoRecord, VideoQueryResult } from '../../types'
import { getVideos, deleteVideo, deleteVideoSuite, getVideoFileUrl } from '../../api'
import { suiteIconFor, fmtDate } from './videoVisuals'
import VideoGallery from './VideoGallery'
import styles from './SuiteDetail.module.css'

const PAGE_SIZE = 24

interface Props {
  suite:           VideoSuiteSummary
  onSuiteDeleted:  () => void
}

export default function SuiteDetail({ suite, onSuiteDeleted }: Props) {
  const [q, setQ]           = useState('')
  const [status, setStatus] = useState('')
  const [device, setDevice] = useState('')
  const [env, setEnv]       = useState('')
  const [page, setPage]     = useState(0)

  const [data, setData]       = useState<VideoQueryResult | null>(null)
  const [loading, setLoading] = useState(true)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [deletingSuite, setDeletingSuite] = useState(false)
  const [playingId, setPlayingId] = useState<string | null>(null)
  const [downloadingAll, setDownloadingAll] = useState(false)

  useEffect(() => { setPage(0) }, [suite.suiteName, q, status, device, env])

  useEffect(() => { load() }, [suite.suiteName, q, status, device, env, page])

  async function load() {
    setLoading(true)
    try {
      const res = await getVideos({ suite: suite.suiteName, q, status, device, env, page, pageSize: PAGE_SIZE })
      setData(res)
    } catch {
      setData({ items: [], total: 0, page: 0, pageSize: PAGE_SIZE })
    } finally {
      setLoading(false)
    }
  }

  async function handleDelete(id: string) {
    setDeletingId(id)
    try {
      await deleteVideo(id)
      await load()
    } finally {
      setDeletingId(null)
    }
  }

  async function handleDeleteSuite() {
    if (!window.confirm(`¿Eliminar TODOS los videos de "${suite.suiteName}"? Esta acción no se puede deshacer.`)) return
    setDeletingSuite(true)
    try {
      await deleteVideoSuite(suite.suiteName)
      onSuiteDeleted()
    } finally {
      setDeletingSuite(false)
    }
  }

  function handlePlayAll() {
    const items = data?.items ?? []
    if (items.length > 0) setPlayingId(items[0].id)
  }

  function handleEnded(id: string) {
    const items = data?.items ?? []
    const idx = items.findIndex(v => v.id === id)
    const next = idx >= 0 ? items[idx + 1] : undefined
    setPlayingId(next ? next.id : null)
  }

  async function handleDownloadAll() {
    setDownloadingAll(true)
    try {
      const all = await getVideos({ suite: suite.suiteName, page: 0, pageSize: Math.max(suite.videoCount, 1) })
      all.items.forEach((v: VideoRecord, i: number) => {
        setTimeout(() => {
          const a = document.createElement('a')
          a.href = getVideoFileUrl(v.id, true)
          a.download = v.originalName
          a.click()
        }, i * 400)
      })
    } finally {
      setDownloadingAll(false)
    }
  }

  return (
    <div className={styles.panel}>
      <div className={styles.header}>
        <div className={styles.headerIcon}>{suiteIconFor(suite.suiteName)}</div>
        <div className={styles.headerInfo}>
          <div className={styles.headerName}>{suite.suiteName}</div>
          <div className={styles.headerMeta}>
            {suite.videoCount} video{suite.videoCount !== 1 ? 's' : ''} · última ejecución {fmtDate(suite.lastExecutionAt)}
          </div>
        </div>
        <div className={styles.headerActions}>
          <button className={styles.actionBtn} onClick={handleDownloadAll} disabled={downloadingAll}>
            <Download size={13} /> {downloadingAll ? 'Preparando…' : 'Descargar todos'}
          </button>
          <button className={styles.actionBtn} onClick={handlePlayAll}>
            <Play size={13} /> Reproducir todo
          </button>
          <button className={`${styles.actionBtn} ${styles.dangerBtn}`} onClick={handleDeleteSuite} disabled={deletingSuite}>
            <Trash2 size={13} /> {deletingSuite ? 'Eliminando…' : 'Eliminar Suite'}
          </button>
        </div>
      </div>

      <div className={styles.filters}>
        <div style={{ position: 'relative', flex: 1, minWidth: 160 }}>
          <Search size={13} style={{ position: 'absolute', left: 9, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-dim)' }} />
          <input
            className={styles.searchInput}
            style={{ paddingLeft: 28, width: '100%' }}
            placeholder="Buscar caso…"
            value={q}
            onChange={e => setQ(e.target.value)}
          />
        </div>
        <select className={styles.select} value={status} onChange={e => setStatus(e.target.value)}>
          <option value="">Todos los estados</option>
          <option value="PASS">Aprobado</option>
          <option value="FAIL">Falló</option>
          <option value="SKIP">Omitido</option>
          <option value="UNKNOWN">Desconocido</option>
        </select>
        <input className={styles.select} placeholder="Dispositivo" value={device} onChange={e => setDevice(e.target.value)} style={{ width: 130 }} />
        <input className={styles.select} placeholder="Ambiente" value={env} onChange={e => setEnv(e.target.value)} style={{ width: 110 }} />
      </div>

      <div className={styles.body}>
        <VideoGallery
          videos={data?.items ?? []}
          loading={loading}
          total={data?.total ?? 0}
          page={page}
          pageSize={PAGE_SIZE}
          onPageChange={setPage}
          playingId={playingId}
          onPlay={setPlayingId}
          onEnded={handleEnded}
          deletingId={deletingId}
          onDelete={handleDelete}
        />
      </div>
    </div>
  )
}
