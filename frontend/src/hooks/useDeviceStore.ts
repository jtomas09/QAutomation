import { useState, useCallback } from 'react'
import type { DeviceConfig } from '../types'

const STORAGE_KEY = 'qa_device_configs'

const DEFAULTS: DeviceConfig[] = [
  {
    id: 'galaxy-a56', name: 'Galaxy A56 5G',
    platform: 'android', platformVersion: '14',
    deviceName: 'Galaxy A56 5G', udid: 'emulator-5554',
    automationName: 'UiAutomator2', hub: 'local',
    appPackage: 'com.cinepolis.movil', appActivity: 'com.cinepolis.movil.MainActivity',
    status: 'available', isActive: true,
  },
  {
    id: 'pixel-8-pro', name: 'Pixel 8 Pro',
    platform: 'android', platformVersion: '14',
    deviceName: 'Pixel 8 Pro', udid: 'emulator-5556',
    automationName: 'UiAutomator2', hub: 'local',
    appPackage: 'com.cinepolis.movil', appActivity: 'com.cinepolis.movil.MainActivity',
    status: 'inuse', isActive: false,
  },
  {
    id: 'iphone-15', name: 'iPhone 15',
    platform: 'ios', platformVersion: '17.4',
    deviceName: 'iPhone 15', udid: '00008110-001A34C13E02401E',
    automationName: 'XCUITest', hub: 'local',
    appPackage: 'com.cinepolis.ios', appActivity: '',
    status: 'available', isActive: false,
  },
  {
    id: 'galaxy-s24', name: 'Galaxy S24',
    platform: 'android', platformVersion: '14',
    deviceName: 'Galaxy S24', udid: 'R3CT203YHVA',
    automationName: 'UiAutomator2', hub: 'local',
    appPackage: 'com.cinepolis.movil', appActivity: 'com.cinepolis.movil.MainActivity',
    status: 'available', isActive: false,
  },
  {
    id: 'redmi-note13', name: 'Redmi Note 13',
    platform: 'android', platformVersion: '13',
    deviceName: 'Redmi Note 13', udid: 'emulator-5558',
    automationName: 'UiAutomator2', hub: 'local',
    appPackage: 'com.cinepolis.movil', appActivity: 'com.cinepolis.movil.MainActivity',
    status: 'inuse', isActive: false,
  },
]

function load(): DeviceConfig[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : DEFAULTS
  } catch {
    return DEFAULTS
  }
}

function save(devices: DeviceConfig[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(devices))
}

export function useDeviceStore() {
  const [devices, setDevices] = useState<DeviceConfig[]>(load)

  const update = useCallback((updated: DeviceConfig[]) => {
    setDevices(updated)
    save(updated)
  }, [])

  const saveDevice = useCallback((device: DeviceConfig) => {
    update(
      devices.some(d => d.id === device.id)
        ? devices.map(d => d.id === device.id ? device : d)
        : [...devices, device]
    )
  }, [devices, update])

  const deleteDevice = useCallback((id: string) => {
    update(devices.filter(d => d.id !== id))
  }, [devices, update])

  const setActive = useCallback((id: string) => {
    update(devices.map(d => ({ ...d, isActive: d.id === id })))
  }, [devices, update])

  const activeDevice = devices.find(d => d.isActive) ?? devices[0] ?? null

  return { devices, saveDevice, deleteDevice, setActive, activeDevice }
}
