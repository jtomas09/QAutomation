import type { ExecutionEvent } from '../types'

export type FlowStepStatus = 'RUNNING' | 'COMPLETED' | 'ERROR' | 'RETRY' | 'SKIPPED'

export interface FlowStep {
  name: string
  status: FlowStepStatus
  description?: string
}

export interface TestCaseFlow {
  testName: string | null
  suite: string | null
  steps: FlowStep[]
}

const STEP_TYPES = new Set([
  'TEST_STEP_STARTED', 'TEST_STEP_COMPLETED', 'TEST_STEP_FAILED',
  'TEST_STEP_RETRY', 'TEST_STEP_SKIPPED',
])

/** ¿Este evento pertenece al flujo del CASO (no a la infraestructura del Launcher)? */
export function isTestFlowEvent(e: ExecutionEvent): boolean {
  return e.type === 'TEST_STARTED' || e.type === 'TEST_FINISHED' || STEP_TYPES.has(e.type)
}

/**
 * Deriva el estado actual del flujo del caso a partir de la secuencia de
 * eventos — sin regex, sin parseo de texto: solo mira `type`/`message`/
 * `details` ya estructurados (ver utils/TestFlowEventPublisher.java).
 *
 * Reconstruye linealmente: TEST_STARTED reinicia la lista de pasos (nuevo
 * caso); cada TEST_STEP_* actualiza (o agrega, si es la primera vez que se ve
 * ese nombre) el paso correspondiente, preservando el orden de PRIMERA
 * aparición. TEST_FINISHED no borra la lista — el último caso se queda visible
 * con su resultado final hasta que llegue un TEST_STARTED nuevo.
 */
export function deriveTestCaseFlow(events: ExecutionEvent[]): TestCaseFlow {
  let testName: string | null = null
  let suite: string | null = null
  const order: string[] = []
  const byName = new Map<string, FlowStep>()

  for (const e of events) {
    if (e.type === 'TEST_STARTED') {
      testName = e.message
      suite = e.suite ?? suite
      order.length = 0
      byName.clear()
      continue
    }
    if (!STEP_TYPES.has(e.type)) continue

    const name = e.message
    if (!byName.has(name)) order.push(name)

    const status: FlowStepStatus =
      e.type === 'TEST_STEP_COMPLETED' ? 'COMPLETED' :
      e.type === 'TEST_STEP_FAILED'    ? 'ERROR' :
      e.type === 'TEST_STEP_RETRY'     ? 'RETRY' :
      e.type === 'TEST_STEP_SKIPPED'   ? 'SKIPPED' :
      'RUNNING' // TEST_STEP_STARTED

    byName.set(name, { name, status, description: e.details ?? undefined })
  }

  return { testName, suite, steps: order.map(n => byName.get(n)!) }
}
