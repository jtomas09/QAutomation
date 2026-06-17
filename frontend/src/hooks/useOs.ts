export type OsType = 'windows' | 'macos' | 'linux' | 'unknown'

/**
 * Detects the operating system from the browser user-agent.
 * Called once — no reactive state needed.
 */
export function detectOs(): OsType {
  const ua   = navigator.userAgent.toLowerCase()
  const plat = (navigator.platform ?? '').toLowerCase()
  if (plat.startsWith('win') || ua.includes('windows'))             return 'windows'
  if (plat.startsWith('mac') || ua.includes('mac os x'))            return 'macos'
  if (ua.includes('linux') || ua.includes('android'))               return 'linux'
  return 'unknown'
}

/** Returns a human-readable label. */
export function osLabel(os: OsType): string {
  switch (os) {
    case 'windows': return 'Windows'
    case 'macos':   return 'macOS'
    case 'linux':   return 'Linux'
    default:        return 'Unknown OS'
  }
}
