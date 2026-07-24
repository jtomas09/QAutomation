import { useState, useCallback, useRef, useEffect } from 'react'
import type { PhysicalDevice } from '../types'
import { getExecutionDeviceConfig, saveExecutionDeviceConfig } from '../api'

export interface ConfiguredDevice {
  udid:            string
  name:            string
  platform:        string
  platformVersion: string | null
}

function toConfigured(device: PhysicalDevice): ConfiguredDevice {
  return {
    udid:            device.udid,
    name:            device.deviceName || device.model || device.udid,
    platform:        device.platform   || 'ANDROID',
    platformVersion: device.platformVersion ?? null,
  }
}

export function useExecutionDevices() {
  const [configured, setConfiguredState] = useState<ConfiguredDevice[]>([])
  const [savedUdids,  setSavedUdids]     = useState<string[]>([])
  const [saving,      setSaving]         = useState(false)

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
    if (removed.some(d => d.udid === activeDeviceUdidRef.current)) {
      setActiveDeviceUdid(kept[0]?.udid ?? null)
    }
    return removed.map(d => d.name)
  }, [])

  const configuredUdids = configured.map(d => d.udid)
  const isDirty = [...configuredUdids].sort().join(',') !== [...savedUdids].sort().join(',')
  const activeDevice = configured.find(d => d.udid === activeDeviceUdid) ?? null

  return {
    configured, configuredUdids, toggleDevice, saveConfig, saving, isDirty, syncWithLive,
    activeDevice, videoEnabled, setVideoEnabled,
  }
}
