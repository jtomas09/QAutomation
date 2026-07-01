import React, { createContext, useContext, useState, useCallback, ReactNode } from 'react'
import ConfirmationModal from '../components/common/ConfirmationModal'
import type { ConfirmationType } from '../components/common/ConfirmationModal'

// ── Public options type ───────────────────────────────────────────────────────

export interface ConfirmOptions {
  title: string
  description: string
  confirmText?: string
  cancelText?: string
  type?: ConfirmationType
}

// ── Internal state ────────────────────────────────────────────────────────────

interface PendingConfirm extends ConfirmOptions {
  resolve: (confirmed: boolean) => void
}

// ── Context ───────────────────────────────────────────────────────────────────

const ConfirmationContext = createContext<((opts: ConfirmOptions) => Promise<boolean>) | null>(null)

// ── Provider ──────────────────────────────────────────────────────────────────

export function ConfirmationProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<PendingConfirm | null>(null)
  const [loading, setLoading] = useState(false)

  const ask = useCallback((opts: ConfirmOptions): Promise<boolean> => {
    return new Promise<boolean>((resolve) => {
      setLoading(false)
      setPending({ ...opts, resolve })
    })
  }, [])

  const handleConfirm = useCallback(() => {
    if (!pending) return
    setLoading(true)
    pending.resolve(true)
    // Let the caller handle async work; close after a tick so the modal
    // doesn't flash a loading state before the caller dismisses it
    setTimeout(() => {
      setPending(null)
      setLoading(false)
    }, 80)
  }, [pending])

  const handleCancel = useCallback(() => {
    if (!pending) return
    pending.resolve(false)
    setPending(null)
    setLoading(false)
  }, [pending])

  return (
    <ConfirmationContext.Provider value={ask}>
      {children}
      <ConfirmationModal
        open={!!pending}
        title={pending?.title ?? ''}
        description={pending?.description ?? ''}
        confirmText={pending?.confirmText}
        cancelText={pending?.cancelText}
        type={pending?.type ?? 'delete'}
        loading={loading}
        onConfirm={handleConfirm}
        onCancel={handleCancel}
      />
    </ConfirmationContext.Provider>
  )
}

// ── Hook ──────────────────────────────────────────────────────────────────────

/**
 * Returns an async function that shows a confirmation modal and resolves to
 * true (confirmed) or false (cancelled / dismissed).
 *
 * Must be used inside a <ConfirmationProvider>.
 *
 * @example
 *   const confirm = useConfirmation()
 *   const ok = await confirm({
 *     title: 'Eliminar Suite',
 *     description: '¿Estás seguro? Esta acción no se puede deshacer.',
 *     type: 'delete',
 *   })
 *   if (ok) deleteTheThing()
 */
export function useConfirmation(): (opts: ConfirmOptions) => Promise<boolean> {
  const ctx = useContext(ConfirmationContext)
  if (!ctx) throw new Error('useConfirmation must be used inside <ConfirmationProvider>')
  return ctx
}
