import React, { useState } from 'react'
import { ENVIRONMENTS, DEVICES, SUITES, TEST_SUITES } from './data'
import { useTestRunner } from './hooks/useTestRunner'
import { useBackendHealth } from './hooks/useBackendHealth'
import Sidebar, { type Page }  from './components/Sidebar'
import TopBar                  from './components/TopBar'
import Dashboard               from './pages/Dashboard'
import ExecutionHistory        from './components/ExecutionHistory'
import TestCard                from './components/TestCard'

export default function App() {
  const [page,    setPage]    = useState<Page>('dashboard')
  const [country, setCountry] = useState('mexico')
  const [env,     setEnv]     = useState(ENVIRONMENTS[0])
  const [device,  setDevice]  = useState(DEVICES[0])
  const [suite,   setSuite]   = useState(SUITES[0])

  const { state, runTest, stopTest, clearLog } = useTestRunner()
  const backendHealth  = useBackendHealth()
  const runnerOnline   = state.status === 'running'
  const runningCount   = state.status === 'running' ? 1 : 0

  function handleRun() {
    runTest(suite, env, device, country)
  }

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      {/* Left sidebar — navigation */}
      <Sidebar page={page} onPageChange={setPage} runningCount={runningCount} />

      {/* Right: topbar + main content */}
      <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minWidth: 0, overflow: 'hidden' }}>
        <TopBar
          backendHealth={backendHealth}
          runnerOnline={runnerOnline}
          onNewExecution={handleRun}
        />

        {/* Scrollable content */}
        <main style={{ flex: 1, overflowY: 'auto', background: 'var(--bg-main)' }}>
          {page === 'dashboard' && (
            <Dashboard
              state={state}
              suite={suite}   env={env}   device={device}   country={country}
              onSuiteChange={setSuite}   onEnvChange={setEnv}
              onDeviceChange={setDevice} onCountryChange={setCountry}
              onRun={handleRun}          onStop={stopTest}
              onClearLog={clearLog}      onViewAll={() => setPage('executions')}
            />
          )}

          {page === 'execute' && (
            <div style={{ padding: '24px 28px' }}>
              <div style={{ marginBottom: 20, fontSize: 12, fontWeight: 700, letterSpacing: '.08em', color: 'var(--text-dim)' }}>
                ≡≡ SELECCIONA LA PRUEBA QUE DESEAS EJECUTAR
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18 }}>
                {TEST_SUITES.map(s => (
                  <TestCard
                    key={s.id} suite={s}
                    onRun={id => runTest(id, env, device, country)}
                    disabled={state.status === 'running'}
                    isActive={state.activeSuite === s.id}
                  />
                ))}
              </div>
            </div>
          )}

          {(page === 'executions' || page === 'history') && (
            <div style={{ padding: '24px 28px' }}>
              <ExecutionHistory />
            </div>
          )}

          {!['dashboard','execute','executions','history'].includes(page) && (
            <div style={{ padding: '48px 28px', color: 'var(--text-dim)', fontSize: 14 }}>
              Esta sección estará disponible próximamente.
            </div>
          )}
        </main>
      </div>
    </div>
  )
}
