import React, { useEffect, useState } from 'react'
import type { ExecutionSummary, ExecutionStatus } from '../../types'
import { getExecutions } from '../../api'
import s from './RecentExecutions.module.css'

const STATUS_LABEL: Record<ExecutionStatus, string> = {
  PENDING: 'PENDING', RUNNING: 'RUNNING',
  COMPLETED: 'PASSED', FAILED: 'FAILED', ABORTED: 'ABORTED',
}
const STATUS_CLS: Record<ExecutionStatus, string> = {
  PENDING: s.pending, RUNNING: s.running,
  COMPLETED: s.passed, FAILED: s.failed, ABORTED: s.aborted,
}

function fmt(iso: string) {
  const d = new Date(iso)
  return d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
}
function dur(start: string, end: string | null) {
  if (!end) return '—'
  const ms = new Date(end).getTime() - new Date(start).getTime()
  if (ms < 60000) return `${Math.round(ms/1000)}s`
  return `${Math.round(ms/60000)}m ${Math.round((ms%60000)/1000)}s`
}

const MOCK: ExecutionSummary[] = [
  { executionId:'RUN-1247', suite:'Smoke Tests',        env:'QA',   device:'Galaxy A56 5G', country:'mexico',    status:'COMPLETED', startTime: new Date(Date.now()-165000).toISOString(), endTime: new Date(Date.now()-300).toISOString(), passed:12,failed:0,skipped:0,total:12,allureUrl:null },
  { executionId:'RUN-1246', suite:'Flujo Completo',     env:'QA',   device:'Pixel 8 Pro',  country:'mexico',    status:'FAILED',    startTime: new Date(Date.now()-3900000).toISOString(), endTime: new Date(Date.now()-3900000+192000).toISOString(), passed:8,failed:3,skipped:0,total:11,allureUrl:null },
  { executionId:'RUN-1245', suite:'Carrito de Compras', env:'PROD', device:'iPhone 15',    country:'argentina', status:'COMPLETED', startTime: new Date(Date.now()-7200000).toISOString(), endTime: new Date(Date.now()-7200000+118000).toISOString(), passed:10,failed:0,skipped:0,total:10,allureUrl:null },
  { executionId:'RUN-1244', suite:'Checkout',           env:'STG',  device:'Galaxy S24',   country:'chile',     status:'ABORTED',   startTime: new Date(Date.now()-86400000).toISOString(), endTime: new Date(Date.now()-86400000+45000).toISOString(),  passed:0,failed:0,skipped:0,total:0,allureUrl:null },
  { executionId:'RUN-1243', suite:'Alimentos',          env:'QA',   device:'Redmi Note 13',country:'mexico',    status:'COMPLETED', startTime: new Date(Date.now()-90000000).toISOString(), endTime: new Date(Date.now()-90000000+150000).toISOString(), passed:9,failed:0,skipped:1,total:10,allureUrl:null },
]

interface Props { onViewAll?: () => void }

export default function RecentExecutions({ onViewAll }: Props) {
  const [rows, setRows] = useState<ExecutionSummary[]>(MOCK)

  useEffect(() => {
    getExecutions()
      .then(data => { if (data.length > 0) setRows(data.slice(0, 5)) })
      .catch(() => {})
    const id = setInterval(() => {
      getExecutions()
        .then(data => { if (data.length > 0) setRows(data.slice(0, 5)) })
        .catch(() => {})
    }, 10_000)
    return () => clearInterval(id)
  }, [])

  return (
    <div className={s.card}>
      <div className={s.header}>
        <div>
          <div className={s.title}>Ejecuciones Recientes</div>
        </div>
        <button className={s.viewAll} onClick={onViewAll}>Ver todas</button>
      </div>

      <div className={s.tableWrap}>
        <table className={s.table}>
          <thead>
            <tr>
              <th>EJECUCIÓN</th>
              <th>SUITE</th>
              <th>DISPOSITIVO</th>
              <th>ESTADO</th>
              <th>INICIADO</th>
              <th>DURACIÓN</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rows.map(row => (
              <tr key={row.executionId} className={row.status === 'RUNNING' ? s.rowActive : ''}>
                <td className={s.execId}>{row.executionId}</td>
                <td>{row.suite}</td>
                <td className={s.dim}>{row.device}</td>
                <td>
                  <span className={`${s.badge} ${STATUS_CLS[row.status]}`}>
                    {row.status === 'RUNNING' && <span className={s.pulse}/>}
                    {STATUS_LABEL[row.status]}
                  </span>
                </td>
                <td className={s.dim}>{fmt(row.startTime)}</td>
                <td className={s.dim}>{dur(row.startTime, row.endTime)}</td>
                <td>
                  <button className={s.playBtn} title="Ver logs">▶</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
