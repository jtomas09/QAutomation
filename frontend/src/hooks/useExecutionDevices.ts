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

  // Ref keeps state readable synchronously (avoids stale closure in syncWithLive/saveConfig)
  const configuredRef       = useRef<ConfiguredDevice[]>([])
  const activeDeviceUdidRef = useRef<string | null>(null)
  const videoEnabledRef     = useRef(false)

  function setConfigured(next: ConfiguredDevice[]) {
    configuredRef.current = next
    setConfiguredState(next)
  }

  function setActiveDeviceUdid(next: string | null) {
    activeDeviceUdidRef.current = next
    setActiveDeviceUdidState(next)
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
  }, [])

  /**
   * Hace que el Mirror siga al dispositivo de una ejecución recién lanzada
   * desde fuera del toggle de "Dispositivos Conectados" (p. ej. el picker
   * ad-hoc de Suites → Ejecutar caso). Si el dispositivo no está en
   * `configured` se agrega (igual que el camino "activar" de toggleDevice,
   * sin persistir — la config guardada del Dashboard no cambia); si ya está,
   * simplemente pasa a ser el activo.
   */
  const followExecutionDevice = useCallback((device: ConfiguredDevice) => {
    const current = configuredRef.current
    if (!current.some(d => d.udid === device.udid)) {
      setConfigured([...current, device])
    }
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
  const activeDevice = configured.find(d => d.udid === activeDeviceUdid) ?? null

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
