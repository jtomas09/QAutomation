import { useState, useCallback, useRef, useEffect, useMemo } from 'react'
import type { PhysicalDevice, ReconciledDevice } from '../types'
import { getExecutionDeviceConfig, saveExecutionDeviceConfig } from '../api'
import { resolveDeviceDisplayName } from '../utils/displayNames'

export interface ConfiguredDevice {
  udid:            string
  name:            string
  platform:        string
  platformVersion: string | null
}

function toConfigured(device: PhysicalDevice): ConfiguredDevice {
  return {
    udid:            device.udid,
    name:            resolveDeviceDisplayName(device).title,
    platform:        device.platform   || 'ANDROID',
    platformVersion: device.platformVersion ?? null,
  }
}

/**
 * Reconciliación — capa 3 del modelo (Configuración persistida → Inventario →
 * Reconcile() → ReconciledDevice → readyDevices → ExecutionPlan). Función
 * pura, sin estado propio: se recalcula en cada poll de `liveDevices`, nunca
 * se persiste. `configured` NUNCA pierde un dispositivo por estar OFFLINE —
 * eso es exactamente el requisito "la configuración persiste, la
 * disponibilidad no".
 */
export function reconcile(
  configured: ConfiguredDevice[],
  liveDevices: PhysicalDevice[],
): ReconciledDevice[] {
  const liveMap = new Map(liveDevices.map(d => [d.udid, d]))
  return configured.map(cfg => {
    const live = liveMap.get(cfg.udid)
    const liveStatus = live?.status ?? 'UNKNOWN'
    return { ...cfg, liveStatus, isReady: liveStatus === 'AVAILABLE' }
  })
}

export function useExecutionDevices() {
  const [configured, setConfiguredState] = useState<ConfiguredDevice[]>([])
  const [savedUdids,  setSavedUdids]     = useState<string[]>([])
  const [saving,      setSaving]         = useState(false)

  // Último snapshot del inventario real (poblado por syncWithLive en cada poll de
  // 15s) — insumo de reconcile(). Nunca se persiste; es puro estado derivado.
  const [lastLiveDevices, setLastLiveDevices] = useState<PhysicalDevice[]>([])

  // Persistido en el backend (no localStorage): ver ExecutionDeviceConfig en
  // api.ts — localStorage es por navegador y Safari lo purga mucho más
  // agresivo que Chrome, lo que hacía que el toggle pareciera "encendido" en
  // un navegador y "apagado" en otro.
  const [videoEnabled, setVideoEnabledState] = useState(false)

  // Dispositivo que el panel Mirror debe reflejar. Se sincroniza con la MISMA
  // interacción de selección que ya existe (el toggle "Usar dispositivo" en
  // ConnectedDevices) — no agrega ningún control nuevo en la UI.
  const [activeDeviceUdid, setActiveDeviceUdidState] = useState<string | null>(null)

  // Device Target de la ejecución (Suite/caso) más reciente — EXCLUSIVO y
  // completamente separado de `configured` (esa lista es del toggle manual
  // del usuario en "Dispositivos Conectados", persistida al backend). Nunca
  // se "agrega" — cada nueva ejecución REEMPLAZA por completo al anterior, así
  // que jamás pueden coexistir dos devices de ejecución al mismo tiempo (ver
  // followExecutionDevice más abajo — causa raíz del Device A que "seguía
  // configurado" tras seleccionar Device B).
  const [executionDevice, setExecutionDeviceState] = useState<ConfiguredDevice | null>(null)

  // Ref keeps state readable synchronously (avoids stale closure in syncWithLive/saveConfig)
  const configuredRef       = useRef<ConfiguredDevice[]>([])
  const activeDeviceUdidRef = useRef<string | null>(null)
  const executionDeviceRef  = useRef<ConfiguredDevice | null>(null)
  const videoEnabledRef     = useRef(false)

  function setConfigured(next: ConfiguredDevice[]) {
    configuredRef.current = next
    setConfiguredState(next)
  }

  function setActiveDeviceUdid(next: string | null) {
    activeDeviceUdidRef.current = next
    setActiveDeviceUdidState(next)
  }

  function setExecutionDevice(next: ConfiguredDevice | null) {
    executionDeviceRef.current = next
    setExecutionDeviceState(next)
  }

  // Load saved config from backend on mount
  useEffect(() => {
    getExecutionDeviceConfig()
      .then(({ devices: udids, videoEnabled: savedVideoEnabled }) => {
        // Names will be resolved when ConnectedDevices calls syncWithLive
        const initial = udids.map(u => ({ udid: u, name: u.slice(0, 16), platform: 'UNKNOWN', platformVersion: null }))
        setConfigured(initial)
        setSavedUdids(udids)
        videoEnabledRef.current = savedVideoEnabled
        setVideoEnabledState(savedVideoEnabled)
      })
      .catch(() => {})
  }, [])

  /** Toggle video recording — persiste al backend de inmediato (mismo momento en que antes se escribía a localStorage). */
  const setVideoEnabled = useCallback((next: boolean) => {
    videoEnabledRef.current = next
    setVideoEnabledState(next)
    saveExecutionDeviceConfig(configuredRef.current.map(d => d.udid), next)
      .catch(e => console.warn('[useExecutionDevices] setVideoEnabled persist error:', e))
  }, [])

  /**
   * Toggle a device in/out of the configured set.
   * Doubles as the Mirror selection signal: activating a device makes it the
   * one the Mirror panel follows; deactivating the active one falls back to
   * another still-configured device (or null if none remain).
   */
  const toggleDevice = useCallback((device: PhysicalDevice) => {
    const current = configuredRef.current
    const exists  = current.some(d => d.udid === device.udid)

    if (exists) {
      const next = current.filter(d => d.udid !== device.udid)
      setConfigured(next)
      if (activeDeviceUdidRef.current === device.udid) {
        setActiveDeviceUdid(next[0]?.udid ?? null)
      }
    } else {
      setConfigured([...current, toConfigured(device)])
      setActiveDeviceUdid(device.udid)
    }
    // La selección manual siempre puede tomar el control del Mirror — ver
    // followExecutionDevice: mientras exista un executionDevice, gana sobre
    // activeDeviceUdid en el cómputo de `activeDevice` más abajo, así que hay
    // que soltarlo aquí para que el toggle manual tenga efecto inmediato.
    setExecutionDevice(null)
  }, [])

  /**
   * Fija el Device Target de una ejecución (Suite/caso) recién lanzada desde
   * fuera del toggle de "Dispositivos Conectados" (p. ej. el picker ad-hoc de
   * Suites → Ejecutar). SIEMPRE reemplaza — nunca agrega — al anterior, y
   * NUNCA toca `configured` (esa lista es exclusiva del toggle manual del
   * usuario, persistida al backend): antes, esta función agregaba el device a
   * `configured` sin quitar el de una ejecución previa, así que un Device A
   * de una ejecución anterior podía quedar "configurado" para siempre — este
   * es exactamente el bug que corrige este cambio.
   */
  const followExecutionDevice = useCallback((device: ConfiguredDevice) => {
    const previous = executionDeviceRef.current
    if (previous && previous.udid !== device.udid) {
      console.log('[SuiteExecution] Previous device cleared:', { name: previous.name, udid: previous.udid })
    }
    console.log('[SuiteExecution] Final target device:',
      { name: device.name, platform: device.platform, udid: device.udid })
    setExecutionDevice(device)
    setActiveDeviceUdid(device.udid)
  }, [])

  /** Persist the current selection to the backend. */
  const saveConfig = useCallback(async () => {
    const toSave = configuredRef.current
    setSaving(true)
    try {
      await saveExecutionDeviceConfig(toSave.map(d => d.udid), videoEnabledRef.current)
      setSavedUdids(toSave.map(d => d.udid))
    } catch (e) {
      console.warn('[useExecutionDevices] saveConfig error:', e)
    } finally {
      setSaving(false)
    }
  }, [])

  /**
   * Called after each live device refresh.
   * - Removes devices no longer present in the live list.
   * - Updates names / versions from live data.
   * Returns the NAMES of removed devices so the caller can show a notification.
   */
  const syncWithLive = useCallback((liveDevices: PhysicalDevice[]): string[] => {
    const liveMap = new Map(liveDevices.map(d => [d.udid, d]))
    const current = configuredRef.current

    const removed = current.filter(d => !liveMap.has(d.udid))
    const kept    = current
      .filter(d => liveMap.has(d.udid))
      .map(d => {
        const live = liveMap.get(d.udid)!
        return { ...d, ...toConfigured(live) }
      })

    setConfigured(kept)
    setLastLiveDevices(liveDevices)
    if (removed.some(d => d.udid === activeDeviceUdidRef.current)) {
      setActiveDeviceUdid(kept[0]?.udid ?? null)
    }
    return removed.map(d => d.name)
  }, [])

  const configuredUdids = configured.map(d => d.udid)
  const isDirty = [...configuredUdids].sort().join(',') !== [...savedUdids].sort().join(',')
  // executionDevice (Suite/caso en ejecución) tiene prioridad — es exclusivo
  // por construcción (followExecutionDevice siempre reemplaza), así que nunca
  // hay ambigüedad entre "el device de la ejecución" y "el device tocado a
  // mano en Dashboard". Sin ejecución activa, cae al toggle manual de siempre.
  const activeDevice = executionDevice ?? configured.find(d => d.udid === activeDeviceUdid) ?? null

  // Capa 3 (Plan de Ejecución): reconciled se recalcula automáticamente en cada
  // poll — un dispositivo que vuelve a AVAILABLE pasa a isReady=true sin que el
  // usuario reconfigure nada. readyUdids es lo único que debe alimentar el
  // ExecutionPlan — nunca configuredUdids directamente.
  const reconciled = useMemo(
    () => reconcile(configured, lastLiveDevices),
    [configured, lastLiveDevices],
  )
  const readyUdids = useMemo(
    () => reconciled.filter(d => d.isReady).map(d => d.udid),
    [reconciled],
  )

  return {
    configured, configuredUdids, toggleDevice, saveConfig, saving, isDirty, syncWithLive,
    activeDevice, videoEnabled, setVideoEnabled, followExecutionDevice,
    reconciled, readyUdids,
  }
}
