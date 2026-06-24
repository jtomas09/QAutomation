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
  /Repositorio no encontrado/i,
  /📥\s*Clonaci[oó]n completada/i,
  /🔄\s*Actualizando repositorio/i,
  /✅\s*Repositorio (actualizado|clonado)/i,
  /⚠.*[uú]ltima versi[oó]n disponible/i,
  /❌.*sincronizar el repositorio/i,
  /Workspace incompleto/i,
  /Eliminando y re-clon/i,
  /\bClonando\b/i,

  // ── Runner / backend internals (bracket-prefixed) ───────────────────────
  /^\[(Runner|BackendClient|ADB|Appium|JobExecutor|api|DriverFactory|Preflight|Diagnose|AllureMailListener|AllureAutoPublish|AllureSummary|PdfReport|Suite|Test|EMAIL)\]/i,

  // ── Runner config & workspace fetch ────────────────────────────────────
  /📥\s*Obteniendo configuraci[oó]n desde Backend/i,
  /✅\s*Configuraci[oó]n recibida\./i,
  /📱\s*DISPOSITIVO RECIBIDO DEL BACKEND/i,
  /No fue posible obtener la configuraci[oó]n del proyecto/i,
  /La configuraci[oó]n almacenada no coincide/i,

  // ── Runner execution metadata (counts, summary) ─────────────────────────
  /TOTAL_ESPERADO/,
  /Casos seleccionados/i,
  /Suite completada/i,   // Runner's summary; frontend generates "Suite finalizada"

  // ── Android SDK / environment variables ────────────────────────────────
  /\bANDROID_HOME\b/,
  /\bANDROID_SDK_ROOT\b/,
  /\bAndroid SDK\b/i,
  /\bplatform-tools\b/i,
  /\bbuild-tools\b/i,

  // ── Capabilities / session config ──────────────────────────────────────
  /\bcapabilit/i,
  /\bLogcat\b/i,

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
  /\bDaemon\b/i,
  /actionable task/i,

  // ── Appium / ADB / WDA ──────────────────────────────────────────────────
  /\bAppium\b/i,
  /\bADB\b/i,
  /\bWDA\b/i,
  /\bWebDriverAgent\b/i,

  // ── Deploy / email / SMTP ───────────────────────────────────────────────
  /\bNetlify\b/i,
  /\bSMTP\b/i,
  /SSE connection/i,
  /Railway/i,

  // ── Java stack traces & log timestamps ──────────────────────────────────
  /^\s+at\s+[\w$.]+\(/,
  /^(com\.|org\.|java\.|javax\.)/,
  /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}/,   // ISO date-time from Java logs
  /^\d{2}:\d{2}:\d{2}\.\d{3}/,                  // HH:MM:SS.mmm from SLF4J/Logback
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
