import type { VideoStatus } from '../../types'

/** Emoji de suite derivado del nombre — heurística simple por palabra clave. */
export function suiteIconFor(suiteName: string): string {
  const n = suiteName.toLowerCase()
  if (n.includes('atmosfera')) return '🛋️'
  if (n.includes('vip'))       return '⭐'
  if (n.includes('coffee') || n.includes('café') || n.includes('cafe')) return '☕'
  if (n.includes('micine') || n.includes('mi cine')) return '🍿'
  if (n.includes('tradicional')) return '🎬'
  if (n.includes('asiento'))    return '💺'
  if (n.includes('checkout'))   return '💳'
  if (n.includes('flujo'))      return '🔄'
  return '🎥'
}

export function statusColor(status: string | null | undefined): string {
  switch ((status ?? '').toUpperCase()) {
    case 'PASS':   case 'PASSED': return 'var(--color-ok)'
    case 'FAIL':   case 'FAILED': return 'var(--color-fail)'
    case 'SKIP':   case 'SKIPPED': return 'var(--color-skip)'
    case 'MIXED':  return '#a78bfa'
    default:       return 'var(--text-dim)'
  }
}

export function statusLabel(status: string | null | undefined): string {
  switch ((status ?? '').toUpperCase()) {
    case 'PASS':   case 'PASSED': return 'Aprobado'
    case 'FAIL':   case 'FAILED': return 'Falló'
    case 'SKIP':   case 'SKIPPED': return 'Omitido'
    case 'MIXED':  return 'Mixto'
    default:       return 'Desconocido'
  }
}

export function fmtSize(bytes: number): string {
  return bytes < 1_048_576
    ? `${(bytes / 1024).toFixed(0)} KB`
    : `${(bytes / 1_048_576).toFixed(1)} MB`
}

/** Fecha compacta para espacios angostos (tarjetas de suite en el panel izquierdo). */
export function fmtDateShort(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('es-MX', { day: '2-digit', month: 'short' })
}

export function fmtDate(iso: string | null): string {
  if (!iso) return '—'
  return new Date(iso).toLocaleString('es-MX', {
    day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

export function fmtDuration(seconds: number | null): string {
  if (seconds == null || !isFinite(seconds) || seconds < 0) return '--:--'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

export type { VideoStatus }
