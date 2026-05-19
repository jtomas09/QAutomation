import React, { useState } from 'react'
import { ENVIRONMENTS, SUITES, TEST_SUITES } from './data'
import { useTestRunner }    from './hooks/useTestRunner'
import { useBackendHealth } from './hooks/useBackendHealth'
import { useDeviceStore }   from './hooks/useDeviceStore'
import Sidebar, { type Page } from './components/Sidebar'
import TopBar                 from './components/TopBar'
import Dashboard              from './pages/Dashboard'
import DevicesPage            from './pages/DevicesPage'
import ExecutionHistory       from './components/ExecutionHistory'
import TestCard               from './components/TestCard'

export default function App() {
  const [page,    setPage]    = useState<Page>('dashboard')
  const [country, setCountry] = useState('mexico')
  const [env,     setEnv]     = useState(ENVIRONMENTS[0])
  const [suite,   setSuite]   = useState(SUITES[0])

  // Device comes from the store (persisted, configurable)
  const { devices, activeDevice, setActive } = useDeviceStore()
  const [deviceOverride, setDeviceOverride] = useState<string | null>(null)

  // The effective device name sent to the backend
  const effectiveDevice = deviceOverride ?? activeDevice?.deviceName ?? activeDevice?.name ?? 'Galaxy A56 5G'

  const { state, runTest, stopTest, clearLog } = useTestRunner()
  const backendHealth = useBackendHealth()
  const runnerOnline  = state.status === 'running'
  const runningCount  = state.status === 'running' ? 1 : 0

  function handleRun() {
    runTest(suite, env, effectiveDevice, country)
  }

  function handleSelectDevice(deviceName: string) {
    setDeviceOverride(deviceName)
    // Also navigate back to dashboard for convenience
    setPage('dashboard')
  }

  return (
    <div className="flex h-screen overflow-hidden" style={{ background: 'var(--bg-main)' }}>
      {/* Sidebar */}
      <Sidebar page={page} onPageChange={setPage} runningCount={runningCount} />

      {/* Main column */}
      <div className="flex flex-col flex-1 min-w-0 overflow-hidden">
        <TopBar
          backendHealth={backendHealth}
          runnerOnline={runnerOnline}
          onNewExecution={handleRun}
        />

        {/* Scrollable page content */}
        <main className="flex-1 overflow-y-auto" style={{ background: 'var(--bg-main)' }}>

          {page === 'dashboard' && (
            <Dashboard
              state={state}
              suite={suite}     env={env}
              device={effectiveDevice}
              country={country}
              onSuiteChange={setSuite}     onEnvChange={setEnv}
              onDeviceChange={setDeviceOverride}
              onCountryChange={setCountry}
              onRun={handleRun}            onStop={stopTest}
              onClearLog={clearLog}        onViewAll={() => setPage('executions')}
            />
          )}

          {page === 'devices' && (
            <DevicesPage onSelectDevice={handleSelectDevice} />
          )}

          {page === 'execute' && (
            <div className="p-7">
              <div className="text-[11px] font-bold tracking-widest text-slate-600 uppercase mb-5">
                Selecciona la prueba que deseas ejecutar
              </div>
              <div className="flex flex-wrap gap-5">
                {TEST_SUITES.map(s => (
                  <TestCard
                    key={s.id} suite={s}
                    onRun={id => runTest(id, env, effectiveDevice, country)}
                    disabled={state.status === 'running'}
                    isActive={state.activeSuite === s.id}
                  />
                ))}
              </div>
            </div>
          )}

          {(page === 'executions' || page === 'history') && (
            <div className="p-7">
              <ExecutionHistory />
            </div>
          )}

          {!['dashboard','execute','executions','history','devices'].includes(page) && (
            <div className="flex items-center justify-center h-64">
              <div className="text-center">
                <div className="text-4xl mb-4 opacity-30">🚧</div>
                <div className="text-sm font-semibold text-slate-500">Esta sección estará disponible próximamente</div>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  )
}
