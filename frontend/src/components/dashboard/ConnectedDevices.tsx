import React from 'react'
import s from './ConnectedDevices.module.css'

interface Device {
  name:    string
  os:      string
  status:  'available' | 'inuse' | 'offline'
  emoji:   string
}

const DEVICES: Device[] = [
  { name: 'Galaxy A56 5G', os: 'Android 14', status: 'available', emoji: '📱' },
  { name: 'Pixel 8 Pro',   os: 'Android 14', status: 'inuse',     emoji: '📱' },
  { name: 'iPhone 15',     os: 'iOS 17.4',   status: 'available', emoji: '📱' },
  { name: 'Galaxy S24',    os: 'Android 14', status: 'available', emoji: '📱' },
  { name: 'Redmi Note 13', os: 'Android 13', status: 'inuse',     emoji: '📱' },
]

const STATUS_LABEL = { available: 'Disponible', inuse: 'En uso', offline: 'Offline' }
const STATUS_CLS   = { available: s.online, inuse: s.busy, offline: s.offline }

export default function ConnectedDevices() {
  return (
    <div className={s.card}>
      <div className={s.header}>
        <div>
          <div className={s.title}>Dispositivos Conectados</div>
          <div className={s.subtitle}>Dispositivos disponibles para pruebas</div>
        </div>
        <button className={s.manageBtn}>Gestionar Dispositivos</button>
      </div>

      <div className={s.grid}>
        {DEVICES.map(d => (
          <div key={d.name} className={s.device}>
            <div className={s.deviceHeader}>
              <span className={s.deviceEmoji}>{d.emoji}</span>
              <button className={s.moreBtn}>⋮</button>
            </div>
            <div className={s.deviceName}>{d.name}</div>
            <div className={s.deviceOs}>{d.os}</div>
            <div className={`${s.status} ${STATUS_CLS[d.status]}`}>
              <span className={s.statusDot} />
              {STATUS_LABEL[d.status]}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
