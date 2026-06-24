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

  // Ref keeps state readable synchronously (avoids stale closure in syncWithLive/saveConfig)
  const configuredRef = useRef<ConfiguredDevice[]>([])

  function setConfigured(next: ConfiguredDevice[]) {
    configuredRef.current = next
    setConfiguredState(next)
  }

  // Load saved config from backend on mount
  useEffect(() => {
    getExecutionDeviceConfig()
      .then(udids => {
        // Names will be resolved when ConnectedDevices calls syncWithLive
        const initial = udids.map(u => ({ udid: u, name: u.slice(0, 16), platform: 'UNKNOWN', platformVersion: null }))
        setConfigured(initial)
        setSavedUdids(udids)
      })
      .catch(() => {})
  }, [])

  /** Toggle a device in/out of the configured set. */
  const toggleDevice = useCallback((device: PhysicalDevice) => {
    const current = configuredRef.current
    const exists  = current.some(d => d.udid === device.udid)
    setConfigured(exists
      ? current.filter(d => d.udid !== device.udid)
      : [...current, toConfigured(device)]
    )
  }, [])

  /** Persist the current selection to the backend. */
  const saveConfig = useCallback(async () => {
    const toSave = configuredRef.current
    setSaving(true)
    try {
      await saveExecutionDeviceConfig(toSave.map(d => d.udid))
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
    return removed.map(d => d.name)
  }, [])

  const configuredUdids = configured.map(d => d.udid)
  const isDirty = [...configuredUdids].sort().join(',') !== [...savedUdids].sort().join(',')

  return { configured, configuredUdids, toggleDevice, saveConfig, saving, isDirty, syncWithLive }
}
