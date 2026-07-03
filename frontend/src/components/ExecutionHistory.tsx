import { useEffect, useState, useCallback } from 'react'
import type { ExecutionSummary, ExecutionStatus } from '../types'
import { getExecutions } from '../api'
import s from './ExecutionHistory.module.css'

const STATUS_LABEL: Record<ExecutionStatus, string> = {
  PENDING:             'Pendiente',
  QUEUED:              'En Cola',
  STARTING:            'Iniciando',
  RUNNING:             'Ejecutando',
  FINALIZING:          'Finalizando',
  ABORTING:            'Abortando',
  PASSED:              'Passed',
  FAILED:              'Failed',
  FAILED_FINALIZATION: 'Error al cerrar',
  SKIPPED:             'Skipped',
  COMPLETED:           'Completado',
  ABORTED:             'Abortado',
}

const STATUS_CLASS: Record<ExecutionStatus, string> = {
  PENDING:             s.queued,
  QUEUED:              s.queued,
  STARTING:            s.running,
  RUNNING:             s.running,
  FINALIZING:          s.running,
  ABORTING:            s.running,
  PASSED:              s.passed,
  FAILED:              s.failed,
  FAILED_FINALIZATION: s.failed,
  SKIPPED:             s.skipped,
  COMPLETED:           s.completed,
  ABORTED:             s.aborted,
}

function duration(start: string, end: string | null): string {
  if (!end) return '—'
  const ms = new Date(end).getTime() - new Date(start).getTime()
  if (ms < 60_000) return `${Math.round(ms / 1000)}s`
  return `${Math.round(ms / 60_000)}m ${Math.round((ms % 60_000) / 1000)}s`
}

export default function ExecutionHistory() {
  const [executions, setExecutions] = useState<ExecutionSummary[]>([])
  const [loading,    setLoading]    = useState(true)
  const [error,      setError]      = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const data = await getExecutions()
      setExecutions(data)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Error al cargar historial')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
    const id = setInterval(load, 10_000) // refresh every 10s
    return () => clearInterval(id)
  }, [load])

  if (loading) return <div className={s.empty}>Cargando historial…</div>
  if (error)   return <div className={s.empty} style={{ color: 'var(--color-fail)' }}>{error}</div>

  return (
    <div className={s.wrap}>
      <div className={s.toolbar}>
        <span className={s.title}>≡ HISTORIAL DE EJECUCIONES</span>
        <button className={s.refreshBtn} onClick={load} title="Actualizar">↺ Actualizar</button>
      </div>

      {executions.length === 0 ? (
        <div className={s.empty}>Sin ejecuciones registradas aún.<br />Presiona <strong>EJECUTAR PRUEBAS</strong> para comenzar.</div>
      ) : (
        <div className={s.table}>
          <div className={s.thead}>
            <span>ID</span>
            <span>Suite</span>
            <span>Env</span>
            <span>Device</span>
            <span>Estado</span>
            <span>Passed</span>
            <span>Failed</span>
            <span>Duración</span>
            <span>Reporte</span>
          </div>
          {executions.map(ex => (
            <div key={ex.executionId} className={`${s.row} ${ex.status === 'RUNNING' ? s.rowActive : ''}`}>
              <span className={s.execId}>{ex.executionId}</span>
              <span>{ex.suite}</span>
              <span className={s.dim}>{ex.env}</span>
              <span className={s.dim}>{ex.device}</span>
              <span>
                <span className={`${s.badge} ${STATUS_CLASS[ex.status]}`}>
                  {ex.status === 'RUNNING' && <span className={s.pulse} />}
                  {STATUS_LABEL[ex.status]}
                </span>
              </span>
              <span className={s.passed}>{ex.passed}</span>
              <span className={ex.failed > 0 ? s.failed : s.dim}>{ex.failed}</span>
              <span className={s.dim}>{duration(ex.startTime, ex.endTime)}</span>
              <span>
                {ex.allureUrl
                  ? <a className={s.allureLink} href={ex.allureUrl} target="_blank" rel="noreferrer">📊 Allure</a>
                  : <span className={s.dim}>—</span>
                }
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
