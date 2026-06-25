/**
 * useDeviceStream — React hook for live device preview.
 *
 * Wraps DeviceStreamService so components stay declarative:
 *
 *   const { url, state } = useDeviceStream(selectedDevice?.udid)
 *
 * - url: revocable blob URL to the latest PNG frame (null when unavailable)
 * - state: StreamState for status indicators
 * - lastUpdated: timestamp of the last successful frame (ms since epoch)
 *
 * Passing null/undefined unsubscribes and resets to idle state.
 */

import { useState, useEffect } from 'react'
import { deviceStreamService, type StreamState } from '../services/deviceStream'

export interface DeviceStreamData {
  url:         string | null
  state:       StreamState
  lastUpdated: number
}

const IDLE: DeviceStreamData = { url: null, state: 'idle', lastUpdated: 0 }

export function useDeviceStream(udid: string | null | undefined): DeviceStreamData {
  const [data, setData] = useState<DeviceStreamData>(IDLE)

  useEffect(() => {
    if (!udid) {
      setData(IDLE)
      return
    }
    setData(prev => ({ ...prev, state: 'connecting' }))
    let lastUpdated = 0
    return deviceStreamService.subscribe(udid, (state, url) => {
      if (state === 'available') lastUpdated = Date.now()
      setData({ url, state, lastUpdated })
    })
  }, [udid])

  return data
}
