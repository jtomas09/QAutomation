/**
 * Resolvers de nombre amigable — único punto de verdad reutilizado por toda la
 * app para decidir qué mostrar como identificador de un dispositivo o de un
 * host/Runner. Nunca modifican ni descartan los datos originales (udid,
 * hostname, IP siguen disponibles tal cual en el objeto de origen para
 * debug/logs/tooltip) — solo calculan qué texto mostrar como título/subtítulo.
 */

/**
 * Hostname Bonjour embebido que el Runner concatena hoy al nombre real por un
 * bug de parseo de `devicectl` (ver IOSDeviceScanner.extractName() en el
 * Runner) — evidencia real de producción:
 *   deviceName = "iPhone de Tester iPhone-de-Tester.coredevice.local"
 * Esto es una limpieza de PRESENTACIÓN sobre un dato ya existente — no
 * requiere ningún cambio en Runner/Discovery/Appium.
 */
const BONJOUR_HOSTNAME_RE = /\S*\.(?:coredevice\.)?local\b/gi

/**
 * Limpia el hostname Bonjour embebido de un string plano (ej. el campo
 * `device` de una ExecutionSummary, guardado tal cual desde el `deviceName`
 * original). Reutilizada por resolveDeviceDisplayName() y por cualquier lugar
 * que solo tenga el nombre como texto (Historial, Reportes, Métricas).
 */
export function cleanBonjourHostname(raw: string | null | undefined): string {
  if (!raw) return ''
  const cleaned = raw.replace(BONJOUR_HOSTNAME_RE, '').replace(/\s+/g, ' ').trim()
  return cleaned || raw.trim()
}

export interface DeviceLike {
  deviceName?:      string | null
  model?:           string | null
  udid?:            string | null
  platform?:        string | null
  platformVersion?: string | null
}

export interface DeviceDisplayName {
  /** Nombre amigable para mostrar como título — nunca hostname/UDID. */
  title:    string
  /** Ej. "iOS 17.4" / "Android 13" — para mostrar como subtítulo. */
  subtitle: string
}

/** Resuelve el nombre amigable de un dispositivo (iOS/Android) para mostrar en UI. */
export function resolveDeviceDisplayName(device: DeviceLike | null | undefined): DeviceDisplayName {
  if (!device) return { title: 'Dispositivo', subtitle: '' }

  const raw = (device.deviceName || device.model || device.udid || 'Dispositivo').trim()
  const title = cleanBonjourHostname(raw)

  const isIOS = (device.platform ?? '').toUpperCase() === 'IOS'
  const platformLabel = isIOS ? 'iOS' : 'Android'
  const subtitle = device.platformVersion ? `${platformLabel} ${device.platformVersion}` : platformLabel

  return { title, subtitle }
}

const IP_RE = /^\d{1,3}(\.\d{1,3}){3}$/

export interface HostLike {
  hostname?:     string | null
  computerName?: string | null
  runnerId?:     string | null
  os?:           string | null
}

export interface HostDisplayName {
  /** Nombre amigable del equipo para mostrar como título — nunca una IP cruda. */
  title:    string
  /** Ej. "macOS" / "Windows" / "Linux" — para mostrar como subtítulo. */
  subtitle: string
  /** IP real, solo si `hostname` es efectivamente una IP — para mostrar como info secundaria/técnica. */
  ip:       string | null
}

/** Resuelve el nombre amigable de un host/Runner (Mac/PC) para mostrar en UI. */
export function resolveHostDisplayName(runner: HostLike | null | undefined): HostDisplayName {
  if (!runner) return { title: 'Runner', subtitle: '', ip: null }

  const hostname = runner.hostname?.trim() || ''
  const looksLikeIp = IP_RE.test(hostname)
  const computerName = runner.computerName?.trim() || ''

  const title = computerName || (!looksLikeIp && hostname) || runner.runnerId || 'Runner'
  const subtitle = runner.os === 'MACOS' ? 'macOS' : runner.os === 'WINDOWS' ? 'Windows' : runner.os === 'LINUX' ? 'Linux' : ''
  const ip = looksLikeIp ? hostname : null

  return { title, subtitle, ip }
}
