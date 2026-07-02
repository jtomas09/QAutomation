/**
 * ApplicationIconResolver — auto-resolves app icons and brand colors
 * from packageName / bundleId / appName for any mobile application.
 *
 * Strategy (in order):
 *   1. Match against the curated registry of 30+ known apps
 *   2. Try to derive the domain from the reverse-domain package name
 *   3. Return a fallback colored-initial icon
 *
 * Icons are loaded from Google's S2 favicon service so they are always
 * current — no asset bundling needed.
 */

interface AppDef {
  id:          string
  displayName: string
  patterns:    string[]   // lowercase substrings to test against package + appName
  domain:      string     // domain for Google s2/favicons
  color:       string     // primary brand color (hex)
  category:    string
  emoji:       string     // fallback emoji if image fails
}

export interface ResolvedApp {
  id:           string
  displayName:  string
  iconUrl:      string    // Google favicon URL (64px)
  fallbackEmoji: string
  color:        string
  category:     string
  isKnown:      boolean
}

// ── Registry ──────────────────────────────────────────────────────────────────

const REGISTRY: AppDef[] = [
  { id: 'cinepolis',    displayName: 'Cinépolis',        patterns: ['cinepolis', 'cinépolis'],       domain: 'cinepolis.com',       color: '#e31837', category: 'cinema',       emoji: '🎬' },
  { id: 'netflix',      displayName: 'Netflix',           patterns: ['netflix'],                      domain: 'netflix.com',         color: '#e50914', category: 'streaming',    emoji: '🎞️' },
  { id: 'spotify',      displayName: 'Spotify',           patterns: ['spotify'],                      domain: 'spotify.com',         color: '#1db954', category: 'music',        emoji: '🎵' },
  { id: 'youtube',      displayName: 'YouTube',           patterns: ['youtube', 'ytmusic'],           domain: 'youtube.com',         color: '#ff0000', category: 'video',        emoji: '▶️' },
  { id: 'amazon',       displayName: 'Amazon',            patterns: ['amazon', '.shopping'],          domain: 'amazon.com',          color: '#ff9900', category: 'ecommerce',    emoji: '🛒' },
  { id: 'mercadolibre', displayName: 'Mercado Libre',     patterns: ['mercadolibre', 'mercadopago', 'meli.'], domain: 'mercadolibre.com', color: '#ffe600', category: 'ecommerce', emoji: '🛒' },
  { id: 'rappi',        displayName: 'Rappi',             patterns: ['rappi'],                        domain: 'rappi.com',           color: '#ff441f', category: 'delivery',     emoji: '🍔' },
  { id: 'ubereats',     displayName: 'Uber Eats',         patterns: ['ubereats', 'uber_eats'],        domain: 'ubereats.com',        color: '#06c167', category: 'delivery',     emoji: '🍔' },
  { id: 'doordash',     displayName: 'DoorDash',          patterns: ['doordash'],                     domain: 'doordash.com',        color: '#ff3008', category: 'delivery',     emoji: '🍔' },
  { id: 'instagram',    displayName: 'Instagram',         patterns: ['instagram'],                    domain: 'instagram.com',       color: '#c13584', category: 'social',       emoji: '📸' },
  { id: 'tiktok',       displayName: 'TikTok',            patterns: ['tiktok'],                       domain: 'tiktok.com',          color: '#010101', category: 'social',       emoji: '🎶' },
  { id: 'facebook',     displayName: 'Facebook',          patterns: ['facebook', '.fb.'],             domain: 'facebook.com',        color: '#1877f2', category: 'social',       emoji: '📘' },
  { id: 'twitter',      displayName: 'X / Twitter',       patterns: ['twitter', 'x.android'],        domain: 'twitter.com',         color: '#1da1f2', category: 'social',       emoji: '🐦' },
  { id: 'whatsapp',     displayName: 'WhatsApp',          patterns: ['whatsapp'],                     domain: 'whatsapp.com',        color: '#25d366', category: 'messaging',    emoji: '💬' },
  { id: 'telegram',     displayName: 'Telegram',          patterns: ['telegram'],                     domain: 'telegram.org',        color: '#2ca5e0', category: 'messaging',    emoji: '✈️' },
  { id: 'zoom',         displayName: 'Zoom',              patterns: ['zoom'],                         domain: 'zoom.us',             color: '#2d8cff', category: 'productivity', emoji: '📹' },
  { id: 'slack',        displayName: 'Slack',             patterns: ['slack'],                        domain: 'slack.com',           color: '#4a154b', category: 'productivity', emoji: '🧩' },
  { id: 'linkedin',     displayName: 'LinkedIn',          patterns: ['linkedin'],                     domain: 'linkedin.com',        color: '#0a66c2', category: 'professional', emoji: '💼' },
  { id: 'gmail',        displayName: 'Gmail',             patterns: ['gmail', 'inbox.google'],        domain: 'gmail.com',           color: '#ea4335', category: 'email',        emoji: '📧' },
  { id: 'uber',         displayName: 'Uber',              patterns: ['com.ubercab'],                  domain: 'uber.com',            color: '#000000', category: 'transport',    emoji: '🚗' },
  { id: 'didi',         displayName: 'DiDi',              patterns: ['didi'],                         domain: 'didiglobal.com',      color: '#e97600', category: 'transport',    emoji: '🚗' },
  { id: 'cabify',       displayName: 'Cabify',            patterns: ['cabify'],                       domain: 'cabify.com',          color: '#6b0ac9', category: 'transport',    emoji: '🚗' },
  { id: 'bbva',         displayName: 'BBVA',              patterns: ['bbva'],                         domain: 'bbva.mx',             color: '#004481', category: 'finance',      emoji: '💳' },
  { id: 'santander',    displayName: 'Santander',         patterns: ['santander'],                    domain: 'santander.com',       color: '#ec0000', category: 'finance',      emoji: '💳' },
  { id: 'banamex',      displayName: 'Banamex',           patterns: ['banamex', 'citibanamex'],       domain: 'banamex.com',         color: '#d00000', category: 'finance',      emoji: '💳' },
  { id: 'airbnb',       displayName: 'Airbnb',            patterns: ['airbnb'],                       domain: 'airbnb.com',          color: '#ff5a5f', category: 'travel',       emoji: '🏠' },
  { id: 'waze',         displayName: 'Waze',              patterns: ['waze'],                         domain: 'waze.com',            color: '#33ccff', category: 'navigation',   emoji: '🗺️' },
  { id: 'googlemaps',   displayName: 'Google Maps',       patterns: ['maps.google', 'gmaps'],         domain: 'maps.google.com',     color: '#4285f4', category: 'navigation',   emoji: '🗺️' },
  { id: 'twitter',      displayName: 'Discord',           patterns: ['discord'],                      domain: 'discord.com',         color: '#5865f2', category: 'social',       emoji: '🎮' },
]

function faviconUrl(domain: string): string {
  return `https://www.google.com/s2/favicons?domain=${encodeURIComponent(domain)}&sz=64`
}

// Try to derive a domain from a Java-style reverse-domain package name.
// "com.cinepolis.go" → "cinepolis.com"
// "mx.com.bbva.bbvamobile" → try "bbva.com"
function deriveDomain(pkg: string): string | null {
  const parts = pkg.toLowerCase().split('.').filter(p => p && p.length > 1 && !['com', 'org', 'net', 'io', 'mx', 'app', 'dev', 'go', 'android', 'ios'].includes(p))
  if (parts.length === 0) return null
  // Take the first meaningful segment as the root domain
  const root = parts[0]
  if (root.length < 3) return null
  return `${root}.com`
}

const FALLBACK_COLORS = ['#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#3b82f6', '#14b8a6']

function fallbackColor(seed: string): string {
  let h = 0
  for (let i = 0; i < seed.length; i++) h = (h * 31 + seed.charCodeAt(i)) >>> 0
  return FALLBACK_COLORS[h % FALLBACK_COLORS.length]
}

// ── Service ───────────────────────────────────────────────────────────────────

class ApplicationIconResolverImpl {

  private match(hay: string): AppDef | null {
    for (const app of REGISTRY) {
      if (app.patterns.some(p => hay.includes(p))) return app
    }
    return null
  }

  resolveApplication(
    packageName = '',
    bundleId    = '',
    appName     = '',
  ): ResolvedApp {
    const hay = `${packageName} ${bundleId} ${appName}`.toLowerCase()

    // 1. Registry match
    const known = this.match(hay)
    if (known) {
      return {
        id: known.id, displayName: known.displayName,
        iconUrl: faviconUrl(known.domain),
        fallbackEmoji: known.emoji,
        color: known.color, category: known.category, isKnown: true,
      }
    }

    // 2. Derive domain from package name
    const derived = deriveDomain(packageName) ?? deriveDomain(bundleId)
    const displayName = appName || (packageName.split('.').pop() ?? 'App')
    const color = fallbackColor(packageName || bundleId || appName)

    if (derived) {
      return {
        id: derived, displayName,
        iconUrl: faviconUrl(derived),
        fallbackEmoji: '📱',
        color, category: 'other', isKnown: false,
      }
    }

    // 3. Full fallback
    return {
      id:            'unknown',
      displayName:   displayName || 'App',
      iconUrl:       '',
      fallbackEmoji: '📱',
      color,
      category:      'other',
      isKnown:       false,
    }
  }

  resolveIcon(packageName = '', bundleId = '', appName = ''): string {
    return this.resolveApplication(packageName, bundleId, appName).iconUrl
  }

  resolveColor(packageName = '', bundleId = '', appName = ''): string {
    return this.resolveApplication(packageName, bundleId, appName).color
  }
}

export const appIconResolver = new ApplicationIconResolverImpl()
