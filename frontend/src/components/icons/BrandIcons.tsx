/**
 * BrandIcons.tsx — logos oficiales de Apple y Android como SVG vectorial real.
 *
 * lucide-react (única librería de iconos del proyecto) no tiene un logo de marca
 * de Android, y su icono "Apple" es una fruta genérica, no el logotipo de Apple.
 * Por eso se toma el path oficial directamente del paquete `simple-icons`
 * (la fuente de mayor prioridad recomendada tras la librería propia del
 * proyecto) y se renderiza como <svg><path/></svg> real — no hay imagen
 * rasterizada ni dangerouslySetInnerHTML de por medio.
 */
import appleRaw from 'simple-icons/icons/apple.svg?raw'
import androidRaw from 'simple-icons/icons/android.svg?raw'

function extractPath(raw: string): string {
  return raw.match(/<path d="([^"]+)"/)?.[1] ?? ''
}

const APPLE_PATH = extractPath(appleRaw)
const ANDROID_PATH = extractPath(androidRaw)

interface BrandIconProps {
  size?: number
  className?: string
}

/** Logotipo oficial de Apple (simple-icons "apple"), silueta y proporciones originales. */
export function AppleLogo({ size = 24, className }: BrandIconProps) {
  return (
    <svg role="img" aria-label="Apple" viewBox="0 0 24 24" width={size} height={size} className={className} fill="currentColor" xmlns="http://www.w3.org/2000/svg">
      <path d={APPLE_PATH} />
    </svg>
  )
}

/** Logotipo oficial de Android (simple-icons "android"), silueta y proporciones originales. */
export function AndroidLogo({ size = 24, className }: BrandIconProps) {
  return (
    <svg role="img" aria-label="Android" viewBox="0 0 24 24" width={size} height={size} className={className} fill="currentColor" xmlns="http://www.w3.org/2000/svg">
      <path d={ANDROID_PATH} />
    </svg>
  )
}
