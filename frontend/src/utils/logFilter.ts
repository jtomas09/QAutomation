import type { LogEntry } from '../types'

/**
 * Patterns that identify infrastructure / technical messages.
 * Messages matching any pattern are hidden from the QA functional view
 * and shown only in the "Log Técnico" panel.
 */
const TECH_PATTERNS: RegExp[] = [
  // ── Git operations ──────────────────────────────────────────────────────
  /^\[git\]/i,
  /\bgit\s+(clone|fetch|reset|pull|push)\b/i,
  /📥\s*(Repositorio no encontrado|Clonando)\.\.\./i,
  /📥\s*Clonaci[oó]n completada/i,
  /🔄\s*Actualizando repositorio/i,
  /✅\s*Repositorio (actualizado|clonado)/i,
  /⚠.*[uú]ltima versi[oó]n disponible/i,
  /❌.*sincronizar el repositorio/i,
  /Workspace incompleto/i,
  /Eliminando y re-clon/i,

  // ── Runner / backend internals (bracket-prefixed) ───────────────────────
  /^\[(Runner|BackendClient|ADB|Appium|JobExecutor|api|DriverFactory|Preflight|Diagnose)\]/i,

  // ── Runner config & workspace fetch ────────────────────────────────────
  /📥\s*Obteniendo configuraci[oó]n desde Backend/i,
  /✅\s*Configuraci[oó]n recibida\./i,
  /📱\s*DISPOSITIVO RECIBIDO DEL BACKEND/i,
  /No fue posible obtener la configuraci[oó]n del proyecto/i,
  /La configuraci[oó]n almacenada no coincide/i,

  // ── Gradle & build ──────────────────────────────────────────────────────
  /🔧\s*Verificando permisos de gradlew/i,
  /✅\s*Permisos aplicados correctamente/i,
  /⚠.*gradlew ya ten/i,
  /gradlew no tiene permisos/i,
  /✅\s*Proyecto Gradle v[aá]lido/i,
  /BUILD (SUCCESSFUL|FAILED)/i,
  /> Task /,
  /\bGradle\b/i,
  /\bgradlew\b/i,

  // ── Appium / ADB / WDA ──────────────────────────────────────────────────
  /\bAppium\b/i,
  /\bADB\b/i,
  /\bWDA\b/i,
  /\bWebDriverAgent\b/i,

  // ── SSE / Railway / network ─────────────────────────────────────────────
  /SSE connection/i,
  /Railway/i,

  // ── Java stack traces ───────────────────────────────────────────────────
  /^\s+at\s+[\w$.]+\(/,
  /^(com\.|org\.|java\.|javax\.)/,
  /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}/,   // ISO timestamp from Java logs
]

/**
 * Returns true when the log entry belongs in the functional QA view.
 *
 * PASS / FAIL / SKIP are always functional regardless of content.
 * INFO / WARN / ERROR are classified by their message text.
 */
export function isFunctionalLog(entry: LogEntry): boolean {
  if (entry.level === 'PASS' || entry.level === 'FAIL' || entry.level === 'SKIP') return true
  return !TECH_PATTERNS.some(p => p.test(entry.message))
}
