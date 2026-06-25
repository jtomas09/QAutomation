import React, { useState } from 'react'
import { ENVIRONMENTS, SUITES, ALIMENTOS_TESTS, SUITE_TESTS, COUNTRY_SUITES, getRandomSmokeTests } from './data'
import { useTestRunner }        from './hooks/useTestRunner'
import { useExecutionDevices }  from './hooks/useExecutionDevices'
import { useBackendHealth } from './hooks/useBackendHealth'
import { useTheme }         from './hooks/useTheme'
import { useRunnerStatus }  from './hooks/useRunnerStatus'
import Sidebar, { type Page } from './components/Sidebar'
import TopBar                 from './components/TopBar'
import Dashboard              from './pages/Dashboard'
import DevicesPage            from './pages/DevicesPage'
import VideosPage             from './pages/VideosPage'
import ExecutionHistory       from './components/ExecutionHistory'
import TestCard               from './components/TestCard'
import SuiteDetailPage        from './components/SuiteDetailPage'
import SettingsPage           from './pages/SettingsPage'
import SchedulePage           from './pages/SchedulePage'
import ReportsPage            from './pages/ReportsPage'
import MetricsPage            from './pages/MetricsPage'
import RunnerManager          from './pages/RunnerManager'
import DeviceFarm             from './pages/DeviceFarm'
import RecordStudio           from './pages/RecordStudio'

export default function App() {
  const [page,       setPage]       = useState<Page>('dashboard')
  const [country,    setCountry]    = useState('mexico')
  const [env,        setEnv]        = useState(ENVIRONMENTS[0])
  const [suite,      setSuite]      = useState(SUITES[0])
  const [drillSuite,    setDrillSuite]    = useState<string | null>(null)
  const [smokeTests,    setSmokeTests]    = useState(() => getRandomSmokeTests())
  const [videoEnabled,  setVideoEnabled]  = useState(false)

  const {
    configured, configuredUdids, toggleDevice: toggleConfigDevice,
    saveConfig, saving: savingConfig, isDirty: configDirty, syncWithLive,
  } = useExecutionDevices()

  const { state, runTest, stopTest, clearLog, attachToExecution } = useTestRunner()
  const backendHealth = useBackendHealth()
  const { isDark, toggle: toggleTheme } = useTheme()
  const runnerOnline  = useRunnerStatus()
  const runningCount  = state.status === 'running' ? 1 : 0

  function handleRun() {
    const labels = configured.map(d => d.name)
    runTest(suite, env, configuredUdids, country, videoEnabled, labels)
  }

  function handleCountryChange(c: string) {
    setCountry(c)
    setDrillSuite(null)
  }

  function handleSelectDevice(_deviceName: string) {
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
          isDark={isDark}
          onToggleTheme={toggleTheme}
          onNewExecution={handleRun}
        />

        {/* Scrollable page content */}
        <main className="flex-1 overflow-y-auto" style={{ background: 'var(--bg-main)' }}>

          {page === 'dashboard' && (
            <Dashboard
              state={state}
              suite={suite}              env={env}
              configured={configured}
              country={country}
              videoEnabled={videoEnabled}
              saving={savingConfig}
              isDirty={configDirty}
              onSuiteChange={setSuite}   onEnvChange={setEnv}
              onCountryChange={handleCountryChange}
              onVideoToggle={setVideoEnabled}
              onToggleDevice={toggleConfigDevice}
              onSaveConfig={saveConfig}
              onSyncLive={syncWithLive}
              onRun={handleRun}          onStop={stopTest}
              onClearLog={clearLog}      onViewAll={() => setPage('executions')}
              onManageDevices={() => setPage('devices')}
              onAttach={attachToExecution}
              onNavigate={(p) => setPage(p as import('./components/Sidebar').Page)}
            />
          )}

          {page === 'devices' && (
            <DevicesPage onSelectDevice={handleSelectDevice} />
          )}

          {page === 'videos' && <VideosPage videoEnabled={videoEnabled} />}

          {page === 'settings' && (
            <SettingsPage isDark={isDark} onToggleTheme={toggleTheme} />
          )}

          {page === 'schedule' && <SchedulePage />}

          {page === 'reports'  && <ReportsPage />}

          {page === 'metrics'        && <MetricsPage />}

          {page === 'runner-manager' && <RunnerManager />}

          {(page === 'device-farm' || page === 'download-agent') && (
            <DeviceFarm
              onNavigate={(p) => setPage(p as Page)}
              initialOpenDownload={page === 'download-agent'}
            />
          )}

          {page === 'record-studio' && (
            <RecordStudio onNavigateToExecute={() => setPage('execute')} />
          )}

          {page === 'execute' && (() => {
            // Merge static suites with custom suites saved from Record Studio
            const customSuites: Array<{ id: string; country: string; title: string; description: string; icon: string; accent: string }> =
              JSON.parse(localStorage.getItem('qa_custom_suites') ?? '[]')
            const customForCountry = customSuites.filter((s) => s.country === country)
            const countrySuites = [...(COUNTRY_SUITES[country] ?? []), ...customForCountry]
            // All known suite cards (needed for drill-down lookup)
            const allKnownCards = [...(COUNTRY_SUITES['mexico'] ?? []), ...ALIMENTOS_TESTS,
                                   ...(COUNTRY_SUITES['argentina'] ?? []), ...(COUNTRY_SUITES['chile'] ?? [])]

            // Drill-down: show individual tests for a suite
            if (drillSuite) {
              const suite = allKnownCards.find(s => s.id === drillSuite)!
              const tests = drillSuite === 'smoke' ? smokeTests : (SUITE_TESTS[drillSuite] ?? [])
              return (
                <SuiteDetailPage
                  suite={suite}
                  tests={tests}
                  disabled={state.status === 'running'}
                  activeId={state.activeSuite}
                  onBack={() => setDrillSuite(null)}
                  onRun={id => runTest(id, env, configuredUdids, country, videoEnabled, configured.map(d => d.name))}
                  onRunAll={() => runTest(drillSuite, env, configuredUdids, country, videoEnabled, configured.map(d => d.name))}
                />
              )
            }

            const handleCardRun = (id: string) => {
              if (id === 'smoke') {
                setSmokeTests(getRandomSmokeTests())
                setDrillSuite('smoke')
              } else if (SUITE_TESTS[id]) {
                setDrillSuite(id)
              } else {
                runTest(id, env, configuredUdids, country, videoEnabled, configured.map(d => d.name))
              }
            }

            // No suites defined for this country yet
            if (countrySuites.length === 0) {
              return (
                <div className="flex items-center justify-center h-64">
                  <div className="text-center">
                    <div className="text-4xl mb-4 opacity-30">🚧</div>
                    <div className="text-sm font-semibold" style={{ color: 'var(--text-dim)' }}>
                      No hay suites configuradas para este país
                    </div>
                  </div>
                </div>
              )
            }

            return (
              <div className="p-7">
                <div className="text-[11px] font-bold tracking-widest text-slate-500 uppercase mb-4">
                  Selecciona la prueba que deseas ejecutar
                </div>
                <div className="flex flex-wrap gap-5">
                  {countrySuites.map((s: { id: string; title: string; description: string; icon: string; accent: string }) => (
                    <TestCard
                      key={s.id} suite={s}
                      onRun={handleCardRun}
                      disabled={state.status === 'running'}
                      isActive={state.activeSuite === s.id}
                    />
                  ))}
                </div>
              </div>
            )
          })()}

          {(page === 'executions' || page === 'history') && (
            <div className="p-7">
              <ExecutionHistory />
            </div>
          )}

          {!['dashboard','execute','executions','history','devices','videos','settings','schedule','reports','metrics','runner-manager','device-farm','download-agent','record-studio'].includes(page) && (
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
