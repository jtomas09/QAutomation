import React from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { Check, X, RotateCw, MinusCircle } from 'lucide-react'
import type { ExecutionEvent } from '../../types'
import { deriveTestCaseFlow, type FlowStep, type FlowStepStatus } from '../../utils/testFlow'

interface Props {
  events: ExecutionEvent[]
}

const STATUS_LABEL: Record<FlowStepStatus, string> = {
  RUNNING:   'EN EJECUCIÓN',
  COMPLETED: 'COMPLETADO',
  ERROR:     'ERROR',
  RETRY:     'REINTENTO',
  SKIPPED:   'OMITIDO',
}

const STATUS_COLOR: Record<FlowStepStatus, { fg: string; bg: string }> = {
  RUNNING:   { fg: '#a5b4fc', bg: 'rgba(99,102,241,0.16)' },
  COMPLETED: { fg: '#34d399', bg: 'rgba(16,185,129,0.14)' },
  ERROR:     { fg: '#fca5a5', bg: 'rgba(244,63,94,0.14)' },
  RETRY:     { fg: '#fcd34d', bg: 'rgba(245,158,11,0.14)' },
  SKIPPED:   { fg: '#94a3b8', bg: 'rgba(255,255,255,0.06)' },
}

function StepDot({ status }: { status: FlowStepStatus }) {
  const c = STATUS_COLOR[status]
  if (status === 'RUNNING') {
    return (
      <span className="flow-dot-running" style={{ background: c.fg }} />
    )
  }
  const Icon = status === 'COMPLETED' ? Check : status === 'ERROR' ? X : status === 'RETRY' ? RotateCw : MinusCircle
  return (
    <span className="w-5 h-5 rounded-full flex items-center justify-center flex-shrink-0"
          style={{ background: c.bg, color: c.fg }}>
      <Icon size={11} strokeWidth={3} />
    </span>
  )
}

function FlowStepRow({ step, isLast }: { step: FlowStep; isLast: boolean }) {
  const c = STATUS_COLOR[step.status]
  return (
    <motion.div
      layout
      initial={{ opacity: 0, x: -6 }}
      animate={{ opacity: 1, x: 0 }}
      transition={{ duration: 0.18 }}
      className="relative grid gap-2.5 py-1.5"
      style={{ gridTemplateColumns: '20px 1fr auto' }}
    >
      {!isLast && (
        <span className="absolute" style={{ left: 9, top: 24, bottom: -6, width: 1.5, background: 'var(--panel-divide)' }} />
      )}
      <StepDot status={step.status} />
      <div className="min-w-0">
        <div className="text-[12.5px] font-semibold truncate" style={{ color: 'var(--text-pri)' }}>{step.name}</div>
        {step.description && (
          <div className="text-[10.5px] mt-0.5 truncate" style={{ color: 'var(--text-dim)' }}>{step.description}</div>
        )}
      </div>
      <span className="self-start text-[8.5px] font-bold px-1.5 py-1 rounded-full font-mono whitespace-nowrap"
            style={{ color: c.fg, background: c.bg, letterSpacing: '.02em' }}>
        {STATUS_LABEL[step.status]}
      </span>
    </motion.div>
  )
}

/**
 * Flujo funcional del caso de prueba en ejecución — consume ÚNICAMENTE
 * ExecutionEvent (TEST_STARTED, TEST_STEP_STARTED/COMPLETED/FAILED, TEST_FINISHED),
 * publicados explícitamente desde TestSteps.run() (ver utils/TestFlowEventPublisher.java
 * en el módulo de tests). Cero parseo de logs — si `events` no trae ninguno de
 * estos tipos (flujo aún no instrumentado), esta sección simplemente no
 * renderiza nada, sin romper el resto de la tarjeta.
 */
function TestCaseFlowTimeline({ events }: Props) {
  const flow = React.useMemo(() => deriveTestCaseFlow(events), [events])

  if (flow.steps.length === 0) return null

  return (
    <div>
      <div className="text-[10.5px] font-bold uppercase tracking-wide mb-1 mt-1" style={{ color: '#a5b4fc', letterSpacing: '.04em' }}>
        Flujo del Caso de Prueba
      </div>
      <AnimatePresence initial={false}>
        {flow.steps.map((step, i) => (
          <FlowStepRow key={step.name} step={step} isLast={i === flow.steps.length - 1} />
        ))}
      </AnimatePresence>
      <style>{`
        .flow-dot-running{
          width:14px; height:14px; border-radius:50%; display:inline-block; margin:3px;
          animation: flowPulse 1.6s ease-in-out infinite;
        }
        @keyframes flowPulse{
          0%,100%{ box-shadow: 0 0 0 4px rgba(99,102,241,0.18); }
          50%{ box-shadow: 0 0 0 7px rgba(99,102,241,0.06); }
        }
        @media (prefers-reduced-motion: reduce){ .flow-dot-running{ animation: none; } }
      `}</style>
    </div>
  )
}

export default React.memo(TestCaseFlowTimeline)
