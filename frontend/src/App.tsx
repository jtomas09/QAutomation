import React, { useState } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { AlertTriangle } from 'lucide-react'
import { ConfirmationProvider } from './hooks/useConfirmation'
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
import SuitesPage            from './pages/SuitesPage'
import type { RecordedCasePayload } from './api'
import type { PhysicalDevice } from './types'
import { resolveDeviceDisplayName } from './utils/displayNames'

export default function App() {
  const [page,       setPage]       = useState<Page>('dashboard')
  const [country,    setCountry]    = useState('mexico')
  const [env,        setEnv]        = useState(ENVIRONMENTS[0])
  const [suite,      setSuite]      = useState(SUITES[0])
  const [drillSuite,    setDrillSuite]    = useState<string | null>(null)
  const [smokeTests,    setSmokeTests]    = useState(() => getRandomSmokeTests())

  const {
    configured, reconciled, readyUdids, toggleDevice: toggleConfigDevice,
    saveConfig, saving: savingConfig, isDirty: configDirty, syncWithLive,
    activeDevice, videoEnabled, setVideoEnabled, followExecutionDevice,
  } = useExecutionDevices()

  const { state, runTest, stopTest, clearLog, attachToExecution } = useTestRunner()
  const backendHealth = useBackendHealth()
  const { isDark, toggle: toggleTheme } = useTheme()
  const runnerOnline  = useRunnerStatus()
  const runningCount  = state.status === 'running' ? 1 : 0

  // Toast de exclusión (Problema 4) — mismo patrón visual ya usado en SuitesPage.tsx.
  const [deviceToast, setDeviceToast] = useState<string | null>(null)
  function showDeviceToast(msg: string) {
    setDeviceToast(msg)
    setTimeout(() => setDeviceToast(null), 4500)
  }

  /**
   * Plan de Ejecución (capa 3): construye la lista de dispositivos a partir de
   * readyUdids/reconciled — NUNCA de configuredUdids crudo. Único punto usado
   * por los 4 lugares de la app que disparan una ejecución (evita duplicar esta
   * validación en cada uno). Bloquea solo si NINGÚN dispositivo está listo;
   * si algunos sí y otros no, avisa con un toast pero ejecuta igual.
   */
  function runReady(suiteId: string) {
    if (configured.length === 0) return // sin dispositivos configurados — nada que avisar aquí
    const notReadyCount = reconciled.length - readyUdids.length
    if (readyUdids.length === 0) {
      showDeviceToast('Ningún dispositivo configurado está disponible ahora mismo.')
      return
    }
    if (notReadyCount > 0) {
      showDeviceToast(
        notReadyCount === 1
          ? '1 dispositivo fue excluido automáticamente por no estar disponible.'
          : `${notReadyCount} dispositivos fueron excluidos automáticamente por no estar disponibles.`
      )
    }
    const readyLabels = reconciled.filter(d => d.isReady).map(d => d.name)
    runTest(suiteId, env, readyUdids, country, videoEnabled, readyLabels)
  }

  function handleRun() {
    runReady(suite)
  }

  /**
   * Ejecuta un caso grabado en Record Studio (Suites) — MISMO pipeline que
   * runReady()/runTest() (un solo POST /api/run, mismo RUN-XXXX, mismo SSE,
   * mismo Mirror/Actividad en Tiempo Real). La única diferencia es el campo
   * `recordedCase`, que el Runner usa para escribir y compilar el test
   * generado en vez de resolver un nombre de suite ya existente (ver
   * JobExecutor). No crea un segundo mecanismo de ejecución.
   *
   * `device` viene del selector de dispositivo propio de SuitesPage (no pasa
   * por la reconciliación configured/readyUdids de Dashboard, pensada para
   * ejecuciones multi-dispositivo curadas) — la validación real de "¿está
   * disponible AHORA?" la hace el backend (RunController → 409 si no está
   * listo, ver ExecuteCaseModal que ya solo ofrece dispositivos con
   * readyForExecution=true).
   *
   * Fase 6 (Mirror sigue a la ejecución activa): este dispositivo puede no
   * estar en `configured` (el multi-selector persistido del Dashboard) —
   * followExecutionDevice() lo agrega/activa para que el panel Mirror lo
   * muestre de inmediato, sin tocar la config guardada.
   */
  function runReadyRecordedCase(recordedCase: RecordedCasePayload, device: PhysicalDevice, executionEnv: string) {
    const deviceLabel = resolveDeviceDisplayName(device).title
    followExecutionDevice({
      udid: device.udid, name: deviceLabel,
      platform: device.platform || 'ANDROID', platformVersion: device.platformVersion ?? null,
    })
    runTest(`QARecordStudio:${recordedCase.className}`, executionEnv, [device.udid], country, videoEnabled, [deviceLabel], recordedCase)
  }

  function handleCountryChange(c: string) {
    setCountry(c)
    setDrillSuite(null)
  }

  function handleSelectDevice(_deviceName: string) {
    setPage('dashboard')
  }

  return (
    <ConfirmationProvider>
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
              reconciled={reconciled}
              activeDevice={activeDevice}
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

          {page === 'suites' && (
            <SuitesPage
              onNavigate={p => setPage(p as import('./components/Sidebar').Page)}
              onExecuteRecordedCase={runReadyRecordedCase}
            />
          )}

          {page === 'execute' && (() => {
            const countrySuites = COUNTRY_SUITES[country] ?? []
            // All known suite cards (needed for drill-down lookup)
            const allKnownCards = [...(COUNTRY_SUITES['mexico'] ?? []), ...ALIMENTOS_TESTS,
                                   ...(COUNTRY_SUITES['argentina'] ?? []), ...(COUNTRY_SUITES['chile'] ?? [])]

            // Runner stopped — block executions
            if (!runnerOnline) {
              return (
                <div className="flex flex-col items-center justify-center h-64 gap-4">
                  <div className="text-4xl opacity-20">⏹</div>
                  <div className="text-center">
                    <div className="text-sm font-bold text-slate-400">Runner detenido</div>
                    <div className="text-xs text-slate-600 mt-1">
                      No es posible ejecutar pruebas porque el Runner está detenido.
                    </div>
                  </div>
                  <button
                    onClick={() => setPage('runner-manager' as import('./components/Sidebar').Page)}
                    className="text-xs font-semibold px-4 py-2 rounded-xl"
                    style={{ background: 'rgba(99,102,241,0.12)', color: '#818cf8', border: '1px solid rgba(99,102,241,0.25)' }}
                  >
                    Ir a Runner Manager
                  </button>
                </div>
              )
            }

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
                  onRun={id => runReady(id)}
                  onRunAll={() => runReady(drillSuite)}
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
                runReady(id)
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

          {!['dashboard','execute','executions','history','devices','videos','settings','schedule','reports','metrics','runner-manager','device-farm','download-agent','record-studio','suites'].includes(page) && (
            <div className="flex items-center justify-center h-64">
              <div className="text-center">
                <div className="text-4xl mb-4 opacity-30">🚧</div>
                <div className="text-sm font-semibold text-slate-500">Esta sección estará disponible próximamente</div>
              </div>
            </div>
          )}
        </main>
      </div>

      {/* Toast de exclusión de dispositivos (Problema 4) */}
      <AnimatePresence>
        {deviceToast && (
          <motion.div
            key="device-toast"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 20 }}
            style={{
              position: 'fixed', bottom: 28, right: 28, zIndex: 999,
              background: '#1e293b', border: '1px solid rgba(245,158,11,0.35)',
              borderRadius: 10, padding: '10px 18px',
              fontSize: 12, fontWeight: 600, color: '#fbbf24',
              boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
              display: 'flex', alignItems: 'center', gap: 8, maxWidth: 360,
            }}
          >
            <AlertTriangle size={13} style={{ flexShrink: 0 }} /> {deviceToast}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
    </ConfirmationProvider>
  )
}
