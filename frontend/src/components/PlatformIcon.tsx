import React from 'react'

// ─── Canonical brand palette ──────────────────────────────────────────────────
// Each platform gets its real identity color, not the status color.
// Status is communicated via the container ring (OsAvatar), not the icon fill.
export const PLATFORM_COLORS: Record<string, string> = {
  MACOS:   '#c9cdd5',  // Mac aluminum silver — matches the chassis aesthetic
  IOS:     '#a8b8d8',  // iOS blue-silver — glass & polish, distinct from macOS
  ANDROID: '#3DDC84',  // Official Android Green (Google brand guideline)
  WINDOWS: '#0078D4',  // Official Windows Blue (Microsoft brand guideline)
  LINUX:   '#e4a83c',  // Terminal amber — universally associated with Linux CLI
}

export const PLATFORM_LABELS: Record<string, string> = {
  MACOS: 'macOS', IOS: 'iOS', ANDROID: 'Android', WINDOWS: 'Windows', LINUX: 'Linux',
}

export const PLATFORM_TOOLTIPS: Record<string, string> = {
  MACOS:   'macOS Sonoma / Apple Silicon',
  IOS:     'iOS Device (iPhone / iPad)',
  ANDROID: 'Android Device',
  WINDOWS: 'Windows Host',
  LINUX:   'Linux Host',
}

// ─── Normalizer ───────────────────────────────────────────────────────────────
export function normalizeOs(os?: string | null): string {
  const s = (os ?? '').trim().toUpperCase()
  if (s === 'IOS' || s === 'IPHONE' || s === 'IPAD') return 'IOS'
  if (s === 'MACOS' || s === 'MAC' || s === 'DARWIN') return 'MACOS'
  if (s === 'ANDROID') return 'ANDROID'
  if (s === 'WINDOWS' || s === 'WIN' || s === 'WIN32' || s === 'WIN64') return 'WINDOWS'
  if (s === 'LINUX' || s === 'UBUNTU' || s === 'DEBIAN') return 'LINUX'
  return s || 'UNKNOWN'
}

// ─── Status ring (glow around avatar container) ───────────────────────────────
function statusRing(status?: string) {
  const s = (status ?? '').toUpperCase()
  if (s === 'ONLINE' || s === 'STARTING')
    return { border: 'rgba(16,185,129,0.5)', shadow: '0 0 0 1px rgba(16,185,129,0.18), 0 0 14px rgba(16,185,129,0.22)' }
  if (s === 'BUSY')
    return { border: 'rgba(245,158,11,0.55)', shadow: '0 0 0 1px rgba(245,158,11,0.18), 0 0 14px rgba(245,158,11,0.22)' }
  if (s === 'DEGRADED' || s === 'STOPPING')
    return { border: 'rgba(245,158,11,0.35)', shadow: '0 0 8px rgba(245,158,11,0.15)' }
  if (s === 'OFFLINE' || s === 'MAINTENANCE')
    return { border: 'rgba(107,114,128,0.28)', shadow: 'none' }
  return { border: 'rgba(255,255,255,0.08)', shadow: 'none' }
}

// ─── SVG primitives ───────────────────────────────────────────────────────────

// Apple Inc. corporate logo — shared by macOS hosts and iOS devices
function AppleSvg({ size, color }: { size: number; color: string }) {
  return (
    <svg
      width={size} height={size} viewBox="0 0 24 24" fill={color}
      xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false"
    >
      <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83zM13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z" />
    </svg>
  )
}

// Android Bugdroid robot head — dome arc + rectangular face + eyes + antennas
function AndroidSvg({ size, color }: { size: number; color: string }) {
  return (
    <svg
      width={size} height={size} viewBox="0 0 24 24" fill="none"
      xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false"
    >
      {/* Left antenna */}
      <line x1="8.5" y1="8.4" x2="6.1" y2="5.1" stroke={color} strokeWidth="1.8" strokeLinecap="round" />
      {/* Right antenna */}
      <line x1="15.5" y1="8.4" x2="17.9" y2="5.1" stroke={color} strokeWidth="1.8" strokeLinecap="round" />
      {/* Head: semicircle dome (A = arc radius 7, exact semicircle when chord = diameter)
          + rectangular face with rounded bottom corners */}
      <path
        d="M5 14A7 7 0 0 1 19 14V19.5C19 20.6 18.1 21.5 17 21.5H7C5.9 21.5 5 20.6 5 19.5V14Z"
        fill={color}
      />
      {/* Eyes — white circles on the green face */}
      <circle cx="9.5" cy="14.6" r="1.25" fill="white" />
      <circle cx="14.5" cy="14.6" r="1.25" fill="white" />
    </svg>
  )
}

// Microsoft Windows 11 logo — four rounded squares in a 2×2 grid
function WindowsSvg({ size, color }: { size: number; color: string }) {
  return (
    <svg
      width={size} height={size} viewBox="0 0 22 22" fill={color}
      xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false"
    >
      <rect x="1"  y="1"  width="9" height="9" rx="1.5" />
      <rect x="12" y="1"  width="9" height="9" rx="1.5" />
      <rect x="1"  y="12" width="9" height="9" rx="1.5" />
      <rect x="12" y="12" width="9" height="9" rx="1.5" />
    </svg>
  )
}

// Linux terminal prompt — monitor frame + "> _" prompt
function LinuxSvg({ size, color }: { size: number; color: string }) {
  return (
    <svg
      width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke={color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
      xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false"
    >
      <rect x="2" y="3" width="20" height="16" rx="2" />
      {/* > chevron prompt */}
      <polyline points="7 8.5 11 12 7 15.5" />
      {/* _ cursor bar */}
      <line x1="13" y1="15.5" x2="17" y2="15.5" />
    </svg>
  )
}

// Fallback for unknown/unrecognized platforms
function UnknownSvg({ size, color }: { size: number; color: string }) {
  return (
    <svg
      width={size} height={size} viewBox="0 0 24 24" fill="none"
      stroke={color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"
      xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M9 9c0-1.66 1.34-3 3-3s3 1.34 3 3c0 1.5-1.5 2.5-3 2.5" />
      <circle cx="12" cy="17" r="0.6" fill={color} />
    </svg>
  )
}

// ─── Public components ────────────────────────────────────────────────────────

interface PlatformIconProps {
  platform: string
  size?: number
  color?: string  // override brand color
  className?: string
}

/** Raw SVG icon for a platform. No container, no tooltip — just the graphic. */
export function PlatformIcon({ platform, size = 18, color, className }: PlatformIconProps) {
  const key = normalizeOs(platform)
  const c = color ?? PLATFORM_COLORS[key] ?? '#94a3b8'
  const props = { size, color: c }

  let icon: React.ReactNode
  if (key === 'MACOS' || key === 'IOS') icon = <AppleSvg {...props} />
  else if (key === 'ANDROID')            icon = <AndroidSvg {...props} />
  else if (key === 'WINDOWS')            icon = <WindowsSvg {...props} />
  else if (key === 'LINUX')              icon = <LinuxSvg {...props} />
  else                                   icon = <UnknownSvg {...props} />

  if (!className) return <>{icon}</>
  return <span className={className}>{icon}</span>
}

interface OsAvatarProps {
  os: string
  size?: number
  status?: string
  className?: string
}

/**
 * Rounded avatar container with official platform icon + status ring glow.
 *
 * Design intent: the icon color encodes PLATFORM (brand color).
 * The ring/glow encodes STATUS (green=online, amber=busy, gray=offline).
 * Separating these two signals makes both unambiguous at a glance.
 */
export function OsAvatar({ os, size = 36, status, className }: OsAvatarProps) {
  const key = normalizeOs(os)
  const platformColor = PLATFORM_COLORS[key] ?? '#94a3b8'
  const ring = statusRing(status)
  const iconSize = Math.round(size * 0.48)
  const radius = Math.round(size * 0.3)
  const tooltip = PLATFORM_TOOLTIPS[key] ?? key

  return (
    <div
      className={className}
      title={tooltip}
      aria-label={tooltip}
      role="img"
      style={{
        width: size,
        height: size,
        borderRadius: radius,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        background: `${platformColor}13`,
        border: `1.5px solid ${ring.border}`,
        boxShadow: ring.shadow,
        transition: 'box-shadow 0.4s ease, border-color 0.4s ease',
      }}
    >
      <PlatformIcon platform={key} size={iconSize} />
    </div>
  )
}

interface PlatformBadgeProps {
  platform: string
  version?: string | null
  size?: 'xs' | 'sm' | 'md'
}

/** Inline badge: icon + platform label + optional OS version. */
export function PlatformBadge({ platform, version, size = 'sm' }: PlatformBadgeProps) {
  const key = normalizeOs(platform)
  const color = PLATFORM_COLORS[key] ?? '#94a3b8'
  const label = PLATFORM_LABELS[key] ?? key
  const iconSize = size === 'xs' ? 9 : size === 'sm' ? 11 : 13
  const fontSize = size === 'xs' ? '9px' : size === 'sm' ? '10px' : '11px'
  const tooltip = version && version !== '—' ? `${label} ${version}` : label

  return (
    <div
      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-lg"
      style={{
        background: `${color}12`,
        border: `1px solid ${color}28`,
        lineHeight: 1,
      }}
      title={tooltip}
      aria-label={tooltip}
    >
      <PlatformIcon platform={key} size={iconSize} />
      <span style={{ color, fontSize, fontWeight: 700, letterSpacing: '0.01em' }}>
        {label}
      </span>
      {version && version !== '—' && version !== 'null' && (
        <span style={{ color: `${color}bb`, fontSize: '9px', fontWeight: 500 }}>
          {version}
        </span>
      )}
    </div>
  )
}
