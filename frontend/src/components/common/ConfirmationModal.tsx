import React, { useEffect, useRef } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Trash2, AlertTriangle, Info, CheckCircle2, Loader2 } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

// ── Types ─────────────────────────────────────────────────────────────────────

export type ConfirmationType = 'delete' | 'warning' | 'info' | 'success'

export interface ConfirmationModalProps {
  open: boolean
  title: string
  description: string
  confirmText?: string
  cancelText?: string
  type?: ConfirmationType
  loading?: boolean
  onConfirm(): void
  onCancel(): void
}

// ── Visual config per type ────────────────────────────────────────────────────

const TYPE_CONFIG: Record<ConfirmationType, {
  Icon: LucideIcon
  iconBg: string
  iconColor: string
  confirmBg: string
  confirmBgHover: string
  defaultConfirmText: string
}> = {
  delete: {
    Icon: Trash2,
    iconBg:          'rgba(239,68,68,0.12)',
    iconColor:       '#f87171',
    confirmBg:       '#dc2626',
    confirmBgHover:  '#ef4444',
    defaultConfirmText: 'Eliminar',
  },
  warning: {
    Icon: AlertTriangle,
    iconBg:          'rgba(245,158,11,0.12)',
    iconColor:       '#f59e0b',
    confirmBg:       '#d97706',
    confirmBgHover:  '#f59e0b',
    defaultConfirmText: 'Continuar',
  },
  info: {
    Icon: Info,
    iconBg:          'rgba(99,102,241,0.12)',
    iconColor:       '#818cf8',
    confirmBg:       '#6366f1',
    confirmBgHover:  '#818cf8',
    defaultConfirmText: 'Confirmar',
  },
  success: {
    Icon: CheckCircle2,
    iconBg:          'rgba(52,211,153,0.12)',
    iconColor:       '#34d399',
    confirmBg:       '#059669',
    confirmBgHover:  '#10b981',
    defaultConfirmText: 'Guardar',
  },
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function ConfirmationModal({
  open,
  title,
  description,
  confirmText,
  cancelText = 'Cancelar',
  type = 'delete',
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmationModalProps) {
  const cfg         = TYPE_CONFIG[type]
  const resolvedText = confirmText ?? cfg.defaultConfirmText
  const cancelRef   = useRef<HTMLButtonElement>(null)
  const confirmRef  = useRef<HTMLButtonElement>(null)

  // Keyboard: ESC → cancel, Enter → confirm, Tab navigation
  useEffect(() => {
    if (!open) return
    const handle = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { onCancel(); return }
      if (e.key === 'Enter' && !loading) { onConfirm(); return }
    }
    document.addEventListener('keydown', handle)
    return () => document.removeEventListener('keydown', handle)
  }, [open, loading, onConfirm, onCancel])

  // Focus the cancel button when modal opens (safer default for destructive actions)
  useEffect(() => {
    if (open) setTimeout(() => cancelRef.current?.focus(), 50)
  }, [open])

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          key="confirmation-overlay"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18 }}
          style={{
            position: 'fixed', inset: 0, zIndex: 9000,
            background: 'rgba(0,0,0,0.65)',
            backdropFilter: 'blur(4px)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            padding: 24,
          }}
          onClick={(e) => { if (e.target === e.currentTarget && !loading) onCancel() }}
        >
          <motion.div
            key="confirmation-card"
            initial={{ opacity: 0, scale: 0.94, y: 10 }}
            animate={{ opacity: 1, scale: 1,    y: 0  }}
            exit={{   opacity: 0, scale: 0.94, y: 10  }}
            transition={{ duration: 0.2, ease: [0.16, 1, 0.3, 1] }}
            role="dialog"
            aria-modal="true"
            aria-labelledby="confirm-title"
            aria-describedby="confirm-desc"
            style={{
              background: '#111827',
              border: '1px solid #2A3144',
              borderRadius: 16,
              padding: '28px 28px 24px',
              width: '100%',
              maxWidth: 420,
              display: 'flex',
              flexDirection: 'column',
              gap: 20,
              boxShadow: '0 24px 64px rgba(0,0,0,0.55), 0 0 0 1px rgba(255,255,255,0.04)',
            }}
          >
            {/* Icon + title */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{
                width: 42, height: 42, borderRadius: 11, flexShrink: 0,
                background: cfg.iconBg,
                border: `1px solid ${cfg.iconColor}33`,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <cfg.Icon size={18} color={cfg.iconColor} />
              </div>
              <h2
                id="confirm-title"
                style={{ margin: 0, fontSize: 15, fontWeight: 700, color: '#f1f5f9', lineHeight: 1.3 }}
              >
                {title}
              </h2>
            </div>

            {/* Description */}
            <p
              id="confirm-desc"
              style={{ margin: 0, fontSize: 13, color: '#94a3b8', lineHeight: 1.6 }}
            >
              {description}
            </p>

            {/* Buttons */}
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button
                ref={cancelRef}
                onClick={onCancel}
                disabled={loading}
                style={{
                  padding: '9px 18px',
                  borderRadius: 8,
                  fontSize: 13,
                  fontWeight: 600,
                  cursor: loading ? 'not-allowed' : 'pointer',
                  background: 'rgba(255,255,255,0.06)',
                  border: '1px solid rgba(255,255,255,0.1)',
                  color: loading ? '#334155' : '#94a3b8',
                  transition: 'all 0.15s',
                  outline: 'none',
                }}
                onMouseEnter={e => { if (!loading) (e.currentTarget.style.background = 'rgba(255,255,255,0.1)') }}
                onMouseLeave={e => (e.currentTarget.style.background = 'rgba(255,255,255,0.06)')}
              >
                {cancelText}
              </button>

              <button
                ref={confirmRef}
                onClick={onConfirm}
                disabled={loading}
                style={{
                  padding: '9px 18px',
                  borderRadius: 8,
                  fontSize: 13,
                  fontWeight: 700,
                  cursor: loading ? 'not-allowed' : 'pointer',
                  background: loading ? 'rgba(255,255,255,0.05)' : cfg.confirmBg,
                  border: 'none',
                  color: loading ? '#475569' : '#fff',
                  display: 'flex', alignItems: 'center', gap: 7,
                  transition: 'all 0.15s',
                  outline: 'none',
                  minWidth: 90, justifyContent: 'center',
                }}
                onMouseEnter={e => { if (!loading) (e.currentTarget.style.background = cfg.confirmBgHover) }}
                onMouseLeave={e => { if (!loading) (e.currentTarget.style.background = cfg.confirmBg) }}
              >
                {loading && <Loader2 size={13} style={{ animation: 'spin 0.8s linear infinite' }} />}
                {loading ? 'Procesando…' : resolvedText}
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

// ── CSS keyframe for spinner (injected once) ──────────────────────────────────

if (typeof document !== 'undefined') {
  const id = 'qa-confirm-spin'
  if (!document.getElementById(id)) {
    const s = document.createElement('style')
    s.id  = id
    s.textContent = '@keyframes spin { to { transform: rotate(360deg) } }'
    document.head.appendChild(s)
  }
}
