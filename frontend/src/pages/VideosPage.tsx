import React, { useEffect, useState } from 'react'
import { Video, Download, Trash2, RefreshCw, Film } from 'lucide-react'

interface VideoRecord {
  id:           string
  executionId:  string
  suiteName:    string
  testName:     string
  originalName: string
  sizeBytes:    number
  createdAt:    string
}

const API_URL = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? ''

export default function VideosPage({ videoEnabled = false }: { videoEnabled?: boolean }) {
  const [videos,     setVideos]     = useState<VideoRecord[]>([])
  const [loading,    setLoading]    = useState(true)
  const [deletingId, setDeletingId] = useState<string | null>(null)

  useEffect(() => { load() }, [])

  async function load() {
    setLoading(true)
    try {
      const res  = await fetch(`${API_URL}/api/videos`)
      const data = await res.json()
      setVideos(Array.isArray(data) ? data : [])
    } catch { /* backend offline */ } finally {
      setLoading(false)
    }
  }

  async function handleDelete(id: string) {
    setDeletingId(id)
    try {
      await fetch(`${API_URL}/api/videos/${id}`, { method: 'DELETE' })
      setVideos(prev => prev.filter(v => v.id !== id))
    } catch { /* ignore */ } finally {
      setDeletingId(null)
    }
  }

  function handleDownload(v: VideoRecord) {
    const a  = document.createElement('a')
    a.href   = `${API_URL}/api/videos/${v.id}/file?download=true`
    a.download = v.originalName
    a.click()
  }

  function fmtSize(bytes: number) {
    return bytes < 1_048_576
      ? `${(bytes / 1024).toFixed(0)} KB`
      : `${(bytes / 1_048_576).toFixed(1)} MB`
  }

  function fmtDate(iso: string) {
    return new Date(iso).toLocaleString('es-MX', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    })
  }

  return (
    <div className="p-7">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-lg font-bold" style={{ color: 'var(--text-pri)' }}>
            Videos de Ejecución
          </h1>
          <p className="text-xs mt-0.5" style={{ color: 'var(--text-dim)' }}>
            Grabaciones de pantalla generadas automáticamente durante las pruebas
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs font-mono" style={{ color: 'var(--text-dim)' }}>
            {videos.length} video{videos.length !== 1 ? 's' : ''}
          </span>
          <button
            onClick={load}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
            style={{ background: 'var(--btn-bg)', border: '1px solid var(--btn-border)', color: 'var(--text-sec)' }}
          >
            <RefreshCw size={13} />
            Actualizar
          </button>
        </div>
      </div>

      {/* Status hint */}
      <div className="mb-5 px-4 py-3 rounded-xl text-xs flex items-start gap-3"
        style={{
          background: videoEnabled ? 'rgba(16,185,129,0.08)' : 'rgba(99,102,241,0.08)',
          border: `1px solid ${videoEnabled ? 'rgba(16,185,129,0.22)' : 'rgba(99,102,241,0.18)'}`,
        }}>
        <Video size={14} style={{ color: videoEnabled ? '#10b981' : '#818cf8', flexShrink: 0, marginTop: 1 }} />
        {videoEnabled ? (
          <span style={{ color: 'var(--text-sec)' }}>
            El toggle <strong style={{ color: '#10b981' }}>Grabar Video</strong> está activado —
            los videos se subirán automáticamente al finalizar cada ejecución.
          </span>
        ) : (
          <span style={{ color: 'var(--text-sec)' }}>
            Activa el toggle <strong style={{ color: '#818cf8' }}>Grabar Video</strong> en el panel
            <strong style={{ color: '#818cf8' }}> Ejecutar Pruebas</strong> del Dashboard
            para grabar automáticamente la pantalla durante cada prueba.
          </span>
        )}
      </div>

      {/* Loading */}
      {loading && (
        <div className="flex items-center justify-center h-48">
          <div className="text-sm" style={{ color: 'var(--text-dim)' }}>Cargando videos…</div>
        </div>
      )}

      {/* Empty */}
      {!loading && videos.length === 0 && (
        <div className="flex flex-col items-center justify-center h-48 gap-3">
          <Film size={44} style={{ color: 'var(--text-dim)', opacity: 0.25 }} />
          <div className="text-sm font-semibold" style={{ color: 'var(--text-dim)' }}>
            No hay videos grabados todavía
          </div>
        </div>
      )}

      {/* List */}
      {!loading && videos.length > 0 && (
        <div className="space-y-2">
          {videos.map(v => (
            <div
              key={v.id}
              className="flex items-center gap-4 px-4 py-3 rounded-xl transition-all"
              style={{ background: 'var(--bg-card)', border: '1px solid var(--panel-border)' }}
            >
              {/* Icon */}
              <div className="w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0"
                style={{ background: 'rgba(99,102,241,0.12)' }}>
                <Video size={16} style={{ color: '#818cf8' }} />
              </div>

              {/* Info */}
              <div className="flex-1 min-w-0">
                <div className="text-sm font-medium truncate" style={{ color: 'var(--text-sec)' }}>
                  {v.testName || v.originalName}
                </div>
                <div className="flex flex-wrap items-center gap-2 mt-1">
                  <span className="text-[10px] font-mono px-1.5 py-0.5 rounded"
                    style={{ background: 'var(--terminal-bg)', color: 'var(--text-dim)' }}>
                    {v.executionId}
                  </span>
                  {v.suiteName && (
                    <span className="text-[10px] px-1.5 py-0.5 rounded-full font-medium"
                      style={{ background: 'rgba(99,102,241,0.12)', color: '#818cf8' }}>
                      {v.suiteName}
                    </span>
                  )}
                  <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
                    {fmtSize(v.sizeBytes)}
                  </span>
                  <span className="text-[10px]" style={{ color: 'var(--text-dim)' }}>
                    {fmtDate(v.createdAt)}
                  </span>
                </div>
              </div>

              {/* Actions */}
              <div className="flex items-center gap-2 flex-shrink-0">
                <button
                  onClick={() => handleDownload(v)}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all"
                  style={{ background: 'rgba(16,185,129,0.1)', border: '1px solid rgba(16,185,129,0.25)', color: '#10b981' }}
                  onMouseEnter={e => (e.currentTarget.style.background = 'rgba(16,185,129,0.2)')}
                  onMouseLeave={e => (e.currentTarget.style.background = 'rgba(16,185,129,0.1)')}
                >
                  <Download size={12} />
                  Descargar
                </button>
                <button
                  onClick={() => handleDelete(v.id)}
                  disabled={deletingId === v.id}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-all"
                  style={{
                    background: 'rgba(244,63,94,0.08)',
                    border: '1px solid rgba(244,63,94,0.2)',
                    color: '#f43f5e',
                    opacity: deletingId === v.id ? 0.5 : 1,
                  }}
                  onMouseEnter={e => { if (deletingId !== v.id) (e.currentTarget as HTMLButtonElement).style.background = 'rgba(244,63,94,0.16)' }}
                  onMouseLeave={e => (e.currentTarget as HTMLButtonElement).style.background = 'rgba(244,63,94,0.08)'}
                >
                  <Trash2 size={12} />
                  {deletingId === v.id ? '…' : 'Eliminar'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
