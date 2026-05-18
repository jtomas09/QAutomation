import { useState, useEffect } from 'react'
import { getHealth } from '../api'

export type BackendStatus = 'checking' | 'online' | 'offline'

export interface BackendHealth {
  status: BackendStatus
  message: string
}

/**
 * Verifica periódicamente la conexión con el backend Railway.
 * Reintenta cada 30 s.
 */
export function useBackendHealth(): BackendHealth {
  const [status,  setStatus]  = useState<BackendStatus>('checking')
  const [message, setMessage] = useState('Verificando conexión…')

  useEffect(() => {
    let mounted = true

    async function check() {
      try {
        const msg = await getHealth()
        if (mounted) {
          setStatus('online')
          setMessage(msg)
        }
      } catch {
        if (mounted) {
          setStatus('offline')
          setMessage('Backend no disponible')
        }
      }
    }

    check()
    const id = setInterval(check, 30_000)
    return () => { mounted = false; clearInterval(id) }
  }, [])

  return { status, message }
}
