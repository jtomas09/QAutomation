import React, { useState } from 'react'
import { ENVIRONMENTS, DEVICES, SUITES, TEST_SUITES } from './data'
import { useTestRunner } from './hooks/useTestRunner'
import Sidebar     from './components/Sidebar'
import Header      from './components/Header'
import TestCard    from './components/TestCard'
import SummaryBar  from './components/SummaryBar'
import LogPanel    from './components/LogPanel'

export default function App() {
  const [country, setCountry]  = useState('mexico')
  const [env,     setEnv]      = useState(ENVIRONMENTS[0])
  const [device,  setDevice]   = useState(DEVICES[0])
  const [suite,   setSuite]    = useState(SUITES[0])

  const { state, runTest, stopTest, clearLog } = useTestRunner()

  function handleRunSuite(suiteId: string) {
    runTest(suiteId, env, device)
  }

  function handleRunAll() {
    const first = TEST_SUITES[0]
    runTest(first.id, env, device)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
      {/* Header — full width */}
      <Header
        env={env}       onEnvChange={setEnv}
        device={device} onDeviceChange={setDevice}
        suite={suite}   onSuiteChange={setSuite}
        status={state.status}
        onRun={handleRunAll}
        onStop={stopTest}
      />

      {/* Body: sidebar + main */}
      <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <Sidebar selected={country} onSelect={setCountry} />

        {/* Main content */}
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, overflow: 'hidden' }}>
          {/* Card area */}
          <div style={{ flex: 1, overflowY: 'auto', padding: '24px 28px', background: 'var(--bg-main)' }}>
            {/* Section header */}
            <div style={{
              display: 'flex', alignItems: 'center', gap: 10,
              marginBottom: 22,
              fontSize: 12, fontWeight: 700, letterSpacing: '.08em',
              color: 'var(--text-dim)',
            }}>
              <span style={{ fontSize: 14 }}>≡≡</span>
              SELECCIONA LA PRUEBA QUE DESEAS EJECUTAR
            </div>

            {/* Cards grid — wrap layout */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18 }}>
              {TEST_SUITES.map(s => (
                <TestCard
                  key={s.id}
                  suite={s}
                  onRun={handleRunSuite}
                  disabled={state.status === 'running'}
                  isActive={state.activeSuite === s.id}
                />
              ))}
            </div>
          </div>

          {/* Bottom: summary + log (fixed height area) */}
          <div style={{ display: 'flex', flexDirection: 'column', height: '310px', flexShrink: 0, borderTop: '1px solid #1c2a4b' }}>
            <SummaryBar state={state} />
            <LogPanel logs={state.logs} onClear={clearLog} />
          </div>
        </div>
      </div>
    </div>
  )
}
