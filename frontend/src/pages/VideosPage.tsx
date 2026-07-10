import React, { useEffect, useState } from 'react'
import { Video, RefreshCw, Film } from 'lucide-react'
import type { VideoSuiteSummary } from '../types'
import { getVideoSuites } from '../api'
import SuiteCard from '../components/videos/SuiteCard'
import SuiteDetail from '../components/videos/SuiteDetail'
import styles from './VideosPage.module.css'

export default function VideosPage({ videoEnabled = false }: { videoEnabled?: boolean }) {
  const [suites,     setSuites]     = useState<VideoSuiteSummary[]>([])
  const [loading,    setLoading]    = useState(true)
  const [search,     setSearch]     = useState('')
  const [selected,   setSelected]   = useState<string | null>(null)

  useEffect(() => { load() }, [])

  async function load() {
    setLoading(true)
    try {
      const data = await getVideoSuites()
      setSuites(data)
      // Si la suite seleccionada ya no existe (p.ej. se eliminó), limpiar selección.
      setSelected(prev => (prev && data.some(s => s.suiteName === prev)) ? prev : (data[0]?.suiteName ?? null))
    } catch { /* backend offline */ } finally {
      setLoading(false)
    }
  }

  function handleSuiteDeleted() {
    setSelected(null)
    load()
  }

  const filtered = suites.filter(s => s.suiteName.toLowerCase().includes(search.toLowerCase()))
  const selectedSuite = suites.find(s => s.suiteName === selected) ?? null

  return (
    <div className="p-7" style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-lg font-bold" style={{ color: 'var(--text-pri)' }}>
            Videos de Ejecución
          </h1>
          <p className="text-xs mt-0.5" style={{ color: 'var(--text-dim)' }}>
            Grabaciones de pantalla generadas automáticamente durante las pruebas, agrupadas por suite
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="text-xs font-mono" style={{ color: 'var(--text-dim)' }}>
            {suites.reduce((n, s) => n + s.videoCount, 0)} video{suites.reduce((n, s) => n + s.videoCount, 0) !== 1 ? 's' : ''} · {suites.length} suite{suites.length !== 1 ? 's' : ''}
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
      {loading && suites.length === 0 && (
        <div className="flex items-center justify-center h-48">
          <div className="text-sm" style={{ color: 'var(--text-dim)' }}>Cargando videos…</div>
        </div>
      )}

      {/* Empty (no suites at all) */}
      {!loading && suites.length === 0 && (
        <div className="flex flex-col items-center justify-center h-48 gap-3">
          <Film size={44} style={{ color: 'var(--text-dim)', opacity: 0.25 }} />
          <div className="text-sm font-semibold" style={{ color: 'var(--text-dim)' }}>
            No hay videos grabados todavía
          </div>
        </div>
      )}

      {/* Master-detail: suites ↔ videos */}
      {!loading && suites.length > 0 && (
        <div className={styles.layout}>
          <div className={styles.suiteList}>
            <div className={styles.suiteListHeader}>
              <input
                className={styles.suiteSearch}
                placeholder="Buscar suite…"
                value={search}
                onChange={e => setSearch(e.target.value)}
              />
            </div>
            <div className={styles.suiteListBody}>
              {filtered.map(s => (
                <SuiteCard
                  key={s.suiteName}
                  suite={s}
                  selected={s.suiteName === selected}
                  onSelect={setSelected}
                />
              ))}
            </div>
          </div>

          {selectedSuite ? (
            <SuiteDetail suite={selectedSuite} onSuiteDeleted={handleSuiteDeleted} />
          ) : (
            <div className={styles.emptySelection}>
              <Film size={40} style={{ opacity: 0.3 }} />
              <span>Selecciona una suite para ver sus videos</span>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
