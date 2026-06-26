import React, { useState, useCallback, useRef } from 'react'
import { useMirrorStream } from '../../hooks/useMirrorStream'

interface DeviceMirrorProps {
  udid:    string | null
  width?:  number | string
  height?: number | string
}

const STATE_ICON: Record<string, string> = {
  idle:                '📱',
  connecting:          '🔄',
  runner_offline:      '⚡',
  device_disconnected: '📵',
  error:               '⚠️',
}

const STATE_LABEL: Record<string, string> = {
  idle:                'Sin dispositivo',
  connecting:          'Conectando al Runner…',
  runner_offline:      'Runner no disponible',
  device_disconnected: 'Dispositivo desconectado',
  error:               'Error de conexión',
  available:           'Iniciando stream…',
}

export function DeviceMirror({ udid, width = '100%', height = '100%' }: DeviceMirrorProps) {
  const { url, state } = useMirrorStream(udid)
  const [imgError, setImgError]   = useState(false)
  const retryKeyRef               = useRef(0)
  const retryTimerRef             = useRef<ReturnType<typeof setTimeout> | null>(null)

  const handleError = useCallback(() => {
    setImgError(true)
    // Auto-retry after 2 s — the MJPEG connection may have dropped briefly
    if (retryTimerRef.current) clearTimeout(retryTimerRef.current)
    retryTimerRef.current = setTimeout(() => {
      retryKeyRef.current += 1
      setImgError(false)
    }, 2_000)
  }, [])

  const handleLoad = useCallback(() => setImgError(false), [])

  const containerStyle: React.CSSProperties = {
    width,
    height,
    display:        'flex',
    flexDirection:  'column',
    alignItems:     'center',
    justifyContent: 'center',
    background:     '#0d1117',
    gap:            8,
    flexShrink:     0,
  }

  if (!url || imgError) {
    const icon  = imgError ? '⚠️' : (STATE_ICON[state]  ?? '📱')
    const label = imgError ? 'Error de stream — reintentando…' : (STATE_LABEL[state] ?? 'Esperando…')

    return (
      <div style={containerStyle}>
        <span style={{ fontSize: 22, opacity: 0.3, lineHeight: 1 }}>{icon}</span>
        <span
          style={{
            fontSize:   10,
            color:      '#475569',
            fontWeight: 600,
            textAlign:  'center',
            maxWidth:   160,
            lineHeight: 1.5,
          }}
        >
          {label}
        </span>
        {state === 'runner_offline' && !imgError && (
          <span
            style={{
              fontSize:   9,
              color:      '#374151',
              textAlign:  'center',
              maxWidth:   180,
              lineHeight: 1.5,
            }}
          >
            Verifica que el Runner esté activo (puerto 8082)
          </span>
        )}
      </div>
    )
  }

  return (
    <img
      key={retryKeyRef.current}
      src={url}
      style={{ width, height, objectFit: 'contain', display: 'block' }}
      onError={handleError}
      onLoad={handleLoad}
      draggable={false}
      alt="Dispositivo en vivo"
    />
  )
}
