import React, { useState } from 'react'
import { ENVIRONMENTS, DEVICES, SUITES, TEST_SUITES } from './data'
import { useTestRunner } from './hooks/useTestRunner'
import Sidebar          from './components/Sidebar'
import Header           from './components/Header'
import TestCard         from './components/TestCard'
import SummaryBar       from './components/SummaryBar'
import LogPanel         from './components/LogPanel'
import ExecutionHistory from './components/ExecutionHistory'

export default function App() {
  const [country,     setCountry]     = useState('mexico')
  const [env,         setEnv]         = useState(ENVIRONMENTS[0])
  const [device,      setDevice]      = useState(DEVICES[0])
  const [suite,       setSuite]       = useState(SUITES[0])
  const [showHistory, setShowHistory] = useState(false)

  const { state, runTest, stopTest, clearLog } = useTestRunner()

  function handleRunSuite(suiteId: string) {
    runTest(suiteId, env, device, country)
  }

  function handleRunAll() {
    runTest(TEST_SUITES[0].id, env, device, country)
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
      <Header
        env={env}       onEnvChange={setEnv}
        device={device} onDeviceChange={setDevice}
        suite={suite}   onSuiteChange={setSuite}
        status={state.status}
        onRun={handleRunAll}
        onStop={stopTest}
      />

      <div style={{ display: 'flex', flex: 1, minHeight: 0 }}>
        <Sidebar selected={country} onSelect={setCountry} />

        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0, overflow: 'hidden' }}>
          {/* Main area — cards or history */}
          <div style={{ flex: 1, overflowY: 'auto', background: 'var(--bg-main)' }}>
            {/* Section header with view toggle */}
            <div style={{
              display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              padding: '18px 28px 14px',
            }}>
              <div style={{
                display: 'flex', alignItems: 'center', gap: 10,
                fontSize: 12, fontWeight: 700, letterSpacing: '.08em', color: 'var(--text-dim)',
              }}>
                <span style={{ fontSize: 14 }}>≡≡</span>
                {showHistory ? 'HISTORIAL DE EJECUCIONES' : 'SELECCIONA LA PRUEBA QUE DESEAS EJECUTAR'}
              </div>

              <div style={{ display: 'flex', gap: 8 }}>
                <ViewBtn active={!showHistory} onClick={() => setShowHistory(false)}>⊞ SUITES</ViewBtn>
                <ViewBtn active={showHistory}  onClick={() => setShowHistory(true)}>≡ HISTORIAL</ViewBtn>
              </div>
            </div>

            {showHistory ? (
              <ExecutionHistory />
            ) : (
              <div style={{ padding: '0 28px 24px', display: 'flex', flexWrap: 'wrap', gap: 18 }}>
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
            )}
          </div>

          {/* Bottom: summary + log (fixed height) */}
          <div style={{ display: 'flex', flexDirection: 'column', height: '310px', flexShrink: 0, borderTop: '1px solid #1c2a4b' }}>
            <SummaryBar state={state} />
            <LogPanel logs={state.logs} onClear={clearLog} />
          </div>
        </div>
      </div>
    </div>
  )
}

function ViewBtn({ active, onClick, children }: {
  active: boolean; onClick: () => void; children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      style={{
        fontSize: 11, fontWeight: 700, letterSpacing: '.04em',
        padding: '5px 14px', borderRadius: 8,
        background: active ? 'var(--accent)' : '#0c1226',
        color: active ? '#fff' : 'var(--text-dim)',
        border: `1px solid ${active ? 'var(--accent)' : '#1e2d55'}`,
        cursor: 'pointer', transition: 'all .15s',
      }}
    >
      {children}
    </button>
  )
}
