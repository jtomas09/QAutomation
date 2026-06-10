import React, { useState } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Plus, Settings, Wifi, WifiOff, Activity, Zap,
  X, Save, Trash2, CheckCircle2, RefreshCw,
  Smartphone, Monitor, Cloud, Apple, Info,
} from 'lucide-react'
import type { DeviceConfig } from '../types'
import { useDeviceStore } from '../hooks/useDeviceStore'

import ip15  from '../assets/devices/iphone-15.svg'
import p8pro from '../assets/devices/pixel-8-pro.svg'
import s24   from '../assets/devices/galaxy-s24.svg'
import a56   from '../assets/devices/galaxy-a56.svg'
import rn13  from '../assets/devices/redmi-note13.svg'

const DEVICE_IMAGE: Record<string, string> = {
  'galaxy-a56': a56, 'pixel-8-pro': p8pro,
  'iphone-15':  ip15, 'galaxy-s24': s24, 'redmi-note13': rn13,
}

const STATUS_META = {
  available: { label: 'Disponible', color: '#10b981', bg: 'rgba(16,185,129,0.12)', Icon: Wifi     },
  inuse:     { label: 'En uso',     color: '#6366f1', bg: 'rgba(99,102,241,0.12)', Icon: Activity },
  offline:   { label: 'Offline',    color: '#f43f5e', bg: 'rgba(244,63,94,0.12)', Icon: WifiOff  },
}

const HUB_META = {
  'local':           { label: 'Local',            icon: Monitor, color: '#10b981' },
  'browserstack':    { label: 'BrowserStack',     icon: Cloud,   color: '#f59e0b' },
  'aws-device-farm': { label: 'AWS Device Farm',  icon: Cloud,   color: '#f97316' },
  'genymotion':      { label: 'Genymotion Cloud', icon: Cloud,   color: '#818cf8' },
}

const EMPTY: Omit<DeviceConfig, 'id'> = {
  name: '', platform: 'android', platformVersion: '',
  deviceName: '', udid: '', automationName: 'UiAutomator2',
  hub: 'local', appPackage: 'com.cinepolis.movil',
  appActivity: 'com.cinepolis.movil.MainActivity',
  status: 'available', isActive: false,
}

interface Props {
  onSelectDevice?: (deviceName: string) => void
}

export default function DevicesPage({ onSelectDevice }: Props) {
  const { devices, saveDevice, deleteDevice, setActive } = useDeviceStore()
  const [editing, setEditing] = useState<DeviceConfig | null>(null)
  const [testing, setTesting] = useState<string | null>(null)
  const [testResult, setTestResult] = useState<Record<string, 'ok' | 'fail'>>({})

  function openNew() {
    setEditing({ id: `device-${Date.now()}`, ...EMPTY })
  }

  function openEdit(d: DeviceConfig) {
    setEditing({ ...d })
  }

  function handleSave() {
    if (!editing) return
    saveDevice(editing)
    setEditing(null)
  }

  function handleDelete(id: string) {
    deleteDevice(id)
    if (editing?.id === id) setEditing(null)
  }

  async function handleTestConnection(d: DeviceConfig) {
    setTesting(d.id)
    await new Promise(r => setTimeout(r, 1800))
    setTestResult(prev => ({ ...prev, [d.id]: d.status !== 'offline' ? 'ok' : 'fail' }))
    setTesting(null)
  }

  function handleSetActive(d: DeviceConfig) {
    setActive(d.id)
    onSelectDevice?.(d.deviceName || d.name)
  }

  return (
    <div className="p-6 pb-10">
      {/* Page header */}
      <motion.div
        initial={{ opacity: 0, y: -8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3 }}
        className="flex items-start justify-between mb-6"
      >
        <div>
          <h1 className="text-2xl font-extrabold text-slate-100">Dispositivos</h1>
          <p className="text-sm text-slate-500 mt-1">
            Configura los capabilities de Appium para cada dispositivo
          </p>
        </div>
        <motion.button
          whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}
          onClick={openNew}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-bold text-white"
          style={{
            background: 'linear-gradient(135deg, #4f46e5, #6366f1)',
            boxShadow: '0 4px 14px rgba(99,102,241,0.4)',
          }}
        >
          <Plus size={15} />
          Agregar Dispositivo
        </motion.button>
      </motion.div>

      {/* Stats row */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        {[
          { label: 'Total',       value: devices.length,                                 color: '#818cf8' },
          { label: 'Disponibles', value: devices.filter(d => d.status === 'available').length, color: '#10b981' },
          { label: 'En uso',      value: devices.filter(d => d.status === 'inuse').length,     color: '#6366f1' },
        ].map((s, i) => (
          <motion.div
            key={s.label}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: i * 0.06 }}
            className="flex items-center gap-4 p-4 rounded-2xl"
            style={{
              background: 'linear-gradient(135deg, rgba(255,255,255,0.04), rgba(255,255,255,0.02))',
              border: '1px solid rgba(255,255,255,0.08)',
            }}
          >
            <div className="text-3xl font-black" style={{ color: s.color }}>{s.value}</div>
            <div className="text-sm text-slate-400 font-medium">{s.label}</div>
          </motion.div>
        ))}
      </div>

      {/* Device grid */}
      <div className="grid grid-cols-5 gap-4">
        {devices.map((device, i) => (
          <DeviceCard
            key={device.id}
            device={device}
            index={i}
            testResult={testResult[device.id]}
            isTesting={testing === device.id}
            onEdit={() => openEdit(device)}
            onSetActive={() => handleSetActive(device)}
            onTestConnection={() => handleTestConnection(device)}
          />
        ))}
      </div>

      {/* Edit/Add Modal */}
      <AnimatePresence>
        {editing && (
          <DeviceModal
            device={editing}
            onChange={setEditing}
            onSave={handleSave}
            onClose={() => setEditing(null)}
            onDelete={() => handleDelete(editing.id)}
          />
        )}
      </AnimatePresence>
    </div>
  )
}

// ── Device Card ────────────────────────────────────────────────────────────────

function DeviceCard({
  device, index, testResult, isTesting,
  onEdit, onSetActive, onTestConnection,
}: {
  device: DeviceConfig
  index: number
  testResult?: 'ok' | 'fail'
  isTesting: boolean
  onEdit: () => void
  onSetActive: () => void
  onTestConnection: () => void
}) {
  const sm   = STATUS_META[device.status]
  const hub  = HUB_META[device.hub]
  const img  = DEVICE_IMAGE[device.id]

  const accent = device.platform === 'ios'
    ? 'rgba(229,229,234,0.25)'
    : device.isActive ? 'rgba(99,102,241,0.4)' : 'rgba(129,140,248,0.2)'

  return (
    <motion.div
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.06, duration: 0.4 }}
      whileHover={{ y: -4, transition: { duration: 0.2 } }}
      className="relative flex flex-col rounded-2xl overflow-hidden"
      style={{
        background: device.isActive
          ? 'linear-gradient(135deg, rgba(99,102,241,0.1), rgba(99,102,241,0.05))'
          : 'linear-gradient(135deg, rgba(255,255,255,0.04), rgba(255,255,255,0.02))',
        border: device.isActive
          ? '1px solid rgba(99,102,241,0.4)'
          : '1px solid rgba(255,255,255,0.08)',
        boxShadow: device.isActive
          ? '0 0 24px rgba(99,102,241,0.2), 0 4px 24px rgba(0,0,0,0.4)'
          : '0 4px 24px rgba(0,0,0,0.3)',
      }}
    >
      {/* Active badge */}
      {device.isActive && (
        <div
          className="absolute top-2.5 left-2.5 flex items-center gap-1 px-2 py-0.5 rounded-full text-[9px] font-bold"
          style={{ background: 'rgba(99,102,241,0.3)', color: '#a5b4fc', border: '1px solid rgba(99,102,241,0.4)' }}
        >
          <CheckCircle2 size={9} />
          ACTIVO
        </div>
      )}

      {/* Settings button */}
      <button
        onClick={onEdit}
        className="absolute top-2.5 right-2.5 w-6 h-6 rounded-lg flex items-center justify-center text-slate-500 hover:text-slate-300 transition-colors"
        style={{ background: 'rgba(255,255,255,0.06)' }}
      >
        <Settings size={11} />
      </button>

      {/* Device image */}
      <div className="flex justify-center items-end pt-8 pb-2" style={{ height: 130 }}>
        {img ? (
          <img
            src={img} alt={device.name}
            className="h-full w-auto object-contain"
            style={{
              filter: `drop-shadow(0 0 12px ${accent}) drop-shadow(0 4px 8px rgba(0,0,0,0.5))`,
            }}
          />
        ) : (
          <Smartphone size={56} className="text-slate-700" />
        )}
      </div>

      {/* Info */}
      <div className="flex flex-col gap-2 px-3 pb-3">
        <div className="text-center">
          <div className="text-[11px] font-bold text-slate-200 leading-tight">{device.name}</div>
          <div className="text-[9px] text-slate-600 mt-0.5">{device.platform === 'ios' ? 'iOS' : 'Android'} {device.platformVersion}</div>
        </div>

        {/* Status + Hub */}
        <div className="flex items-center justify-between gap-1">
          <span
            className="flex items-center gap-1 px-1.5 py-0.5 rounded-full text-[9px] font-bold"
            style={{ color: sm.color, background: sm.bg }}
          >
            <sm.Icon size={8} />
            {sm.label}
          </span>
          <span className="text-[9px] text-slate-600 font-medium">{hub.label}</span>
        </div>

        {/* UDID / deviceName */}
        <div className="text-[9px] text-slate-700 font-mono truncate" title={device.udid || device.deviceName}>
          {device.udid || device.deviceName || '—'}
        </div>

        {/* Connection test result */}
        {testResult && (
          <div
            className="text-center text-[9px] font-bold py-0.5 rounded-lg"
            style={{
              color: testResult === 'ok' ? '#10b981' : '#f43f5e',
              background: testResult === 'ok' ? 'rgba(16,185,129,0.1)' : 'rgba(244,63,94,0.1)',
            }}
          >
            {testResult === 'ok' ? '✓ Conexión OK' : '✗ Sin conexión'}
          </div>
        )}

        {/* Action buttons */}
        <div className="grid grid-cols-2 gap-1.5 mt-1">
          <button
            onClick={onTestConnection}
            disabled={isTesting}
            className="flex items-center justify-center gap-1 py-1.5 rounded-lg text-[9px] font-semibold text-slate-400 hover:text-slate-200 transition-colors"
            style={{ background: 'rgba(255,255,255,0.05)', border: '1px solid rgba(255,255,255,0.08)' }}
          >
            {isTesting
              ? <RefreshCw size={9} className="animate-spin" />
              : <Wifi size={9} />
            }
            {isTesting ? 'Probando…' : 'Probar'}
          </button>

          <button
            onClick={onSetActive}
            className="flex items-center justify-center gap-1 py-1.5 rounded-lg text-[9px] font-bold transition-colors"
            style={
              device.isActive
                ? { background: 'rgba(99,102,241,0.2)', color: '#a5b4fc', border: '1px solid rgba(99,102,241,0.3)' }
                : { background: 'rgba(255,255,255,0.04)', color: '#64748b', border: '1px solid rgba(255,255,255,0.07)' }
            }
          >
            <Zap size={9} />
            {device.isActive ? 'En uso' : 'Usar'}
          </button>
        </div>
      </div>
    </motion.div>
  )
}

// ── Device Modal ───────────────────────────────────────────────────────────────

function DeviceModal({
  device, onChange, onSave, onClose, onDelete,
}: {
  device: DeviceConfig
  onChange: (d: DeviceConfig) => void
  onSave: () => void
  onClose: () => void
  onDelete: () => void
}) {
  const isIOS = device.platform === 'ios'

  const set = (key: keyof DeviceConfig, val: string) =>
    onChange({ ...device, [key]: val })

  function handlePlatformChange(platform: string) {
    onChange({
      ...device,
      platform: platform as DeviceConfig['platform'],
      automationName: platform === 'ios' ? 'XCUITest' : 'UiAutomator2',
    })
  }

  const iosBorderColor = isIOS ? 'rgba(229,229,234,0.2)' : 'rgba(255,255,255,0.1)'
  const iosGlow        = isIOS ? '0 0 40px rgba(200,200,220,0.06)' : 'none'

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ background: 'rgba(0,0,0,0.7)', backdropFilter: 'blur(8px)' }}
      onClick={e => { if (e.target === e.currentTarget) onClose() }}
    >
      <motion.div
        initial={{ opacity: 0, scale: 0.93, y: 20 }}
        animate={{ opacity: 1, scale: 1,    y: 0  }}
        exit={{   opacity: 0, scale: 0.93, y: 20  }}
        transition={{ duration: 0.25, ease: 'easeOut' }}
        className="w-full max-w-2xl rounded-2xl overflow-hidden"
        style={{
          background: isIOS
            ? 'linear-gradient(135deg, #0e1220, #0a0f1b)'
            : 'linear-gradient(135deg, #0d1226, #080e1c)',
          border: `1px solid ${iosBorderColor}`,
          boxShadow: `0 24px 80px rgba(0,0,0,0.7), ${iosGlow}`,
        }}
      >
        {/* Modal header */}
        <div
          className="flex items-center justify-between px-6 py-4"
          style={{ borderBottom: `1px solid ${iosBorderColor}` }}
        >
          <div className="flex items-center gap-3">
            {isIOS ? (
              <div
                className="w-8 h-8 rounded-xl flex items-center justify-center"
                style={{ background: 'rgba(229,229,234,0.1)', border: '1px solid rgba(229,229,234,0.15)' }}
              >
                <Apple size={16} style={{ color: '#e5e5ea' }} />
              </div>
            ) : (
              <div
                className="w-8 h-8 rounded-xl flex items-center justify-center"
                style={{ background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.25)' }}
              >
                <Smartphone size={16} className="text-indigo-400" />
              </div>
            )}
            <div>
              <div className="text-sm font-bold text-slate-100">Configurar Dispositivo</div>
              <div className="text-xs text-slate-500 mt-0.5">
                {isIOS ? 'Capabilities XCUITest · iOS' : 'Capabilities de Appium · Android'}
              </div>
            </div>
          </div>
          <button onClick={onClose} className="w-7 h-7 rounded-lg flex items-center justify-center text-slate-500 hover:text-slate-300 transition-colors"
            style={{ background: 'rgba(255,255,255,0.05)' }}>
            <X size={14} />
          </button>
        </div>

        {/* Modal body */}
        <div className="p-6 grid grid-cols-2 gap-4 max-h-[70vh] overflow-y-auto">

          {/* Nombre */}
          <Field label="Nombre del Dispositivo" span>
            <Input value={device.name} onChange={v => set('name', v)}
              placeholder={isIOS ? 'iPhone 15 Pro' : 'Galaxy A56 5G'} />
          </Field>

          {/* Platform */}
          <Field label="Plataforma">
            <Select value={device.platform} onChange={handlePlatformChange}
              options={[{ value: 'android', label: 'Android' }, { value: 'ios', label: 'iOS' }]} />
          </Field>

          {/* Platform Version */}
          <Field label="Versión de Plataforma">
            <Input value={device.platformVersion} onChange={v => set('platformVersion', v)}
              placeholder={isIOS ? '17.4' : '14'} />
          </Field>

          {/* Device Name (Appium cap) */}
          <Field label="deviceName (Appium)">
            <Input value={device.deviceName} onChange={v => set('deviceName', v)}
              placeholder={isIOS ? 'iPhone 15' : 'Galaxy A56 5G'} />
          </Field>

          {/* UDID */}
          <Field label={isIOS ? 'UDID del dispositivo' : 'UDID / Serial'} span>
            <Input value={device.udid} onChange={v => set('udid', v)}
              placeholder={isIOS ? '00008110-001A34C13E02401E' : 'emulator-5554 ó R3CT203YHVA'} mono />
          </Field>

          {/* Automation Name */}
          <Field label="automationName">
            <Select value={device.automationName}
              onChange={v => set('automationName', v as DeviceConfig['automationName'])}
              options={isIOS
                ? [{ value: 'XCUITest', label: 'XCUITest (iOS)' }]
                : [
                    { value: 'UiAutomator2', label: 'UiAutomator2' },
                    { value: 'Espresso',     label: 'Espresso'     },
                  ]
              } />
          </Field>

          {/* Hub */}
          <Field label="Hub de Ejecución">
            <Select value={device.hub}
              onChange={v => set('hub', v as DeviceConfig['hub'])}
              options={[
                { value: 'local',           label: 'Local'            },
                { value: 'browserstack',    label: 'BrowserStack'     },
                { value: 'aws-device-farm', label: 'AWS Device Farm'  },
                { value: 'genymotion',      label: 'Genymotion Cloud' },
              ]} />
          </Field>

          {/* Status */}
          <Field label="Estado">
            <Select value={device.status}
              onChange={v => set('status', v as DeviceConfig['status'])}
              options={[
                { value: 'available', label: 'Disponible' },
                { value: 'inuse',     label: 'En uso'     },
                { value: 'offline',   label: 'Offline'    },
              ]} />
          </Field>

          {/* ── Android fields ── */}
          {!isIOS && (
            <>
              <Field label="appPackage" span>
                <Input value={device.appPackage} onChange={v => set('appPackage', v)}
                  placeholder="com.cinepolis.movil" mono />
              </Field>
              <Field label="appActivity" span>
                <Input value={device.appActivity} onChange={v => set('appActivity', v)}
                  placeholder="com.cinepolis.movil.MainActivity" mono />
              </Field>
            </>
          )}

          {/* ── iOS fields ── */}
          {isIOS && (
            <>
              {/* Separator */}
              <div className="col-span-2 flex items-center gap-3 mt-1">
                <Apple size={11} style={{ color: '#e5e5ea', flexShrink: 0 }} />
                <div className="text-[10px] font-bold uppercase tracking-wider" style={{ color: '#e5e5ea99' }}>
                  Configuración iOS
                </div>
                <div className="flex-1" style={{ height: 1, background: 'rgba(229,229,234,0.1)' }} />
              </div>

              {/* Bundle ID */}
              <Field label="bundleId *" span>
                <Input value={device.appPackage} onChange={v => set('appPackage', v)}
                  placeholder="com.cinepolis.ios" mono />
              </Field>

              {/* Xcode Org ID */}
              <Field label="xcodeOrgId (dispositivo real)">
                <Input value={device.xcodeOrgId ?? ''} onChange={v => set('xcodeOrgId', v)}
                  placeholder="A1B2C3D4E5" mono />
              </Field>

              {/* Signing Identity */}
              <Field label="xcodeSigningId (dispositivo real)">
                <Input value={device.xcodeSigningId ?? ''} onChange={v => set('xcodeSigningId', v)}
                  placeholder="iPhone Developer" />
              </Field>

              {/* WDA Port */}
              <Field label="wdaLocalPort (opcional)">
                <Input value={device.wdaLocalPort ?? ''} onChange={v => set('wdaLocalPort', v)}
                  placeholder="8100" mono />
              </Field>

              {/* IPA path */}
              <Field label="Ruta IPA · app (opcional)" span>
                <Input value={device.ipaPath ?? ''} onChange={v => set('ipaPath', v)}
                  placeholder="/builds/CinePolis.ipa" mono />
              </Field>

              {/* Info banner */}
              <div
                className="col-span-2 flex items-start gap-2 px-3 py-2.5 rounded-xl"
                style={{ background: 'rgba(229,229,234,0.05)', border: '1px solid rgba(229,229,234,0.1)' }}
              >
                <Info size={12} style={{ color: '#e5e5ea88', marginTop: 1, flexShrink: 0 }} />
                <p className="text-[10px] leading-relaxed" style={{ color: '#e5e5ea77' }}>
                  Para <strong style={{ color: '#e5e5eaaa' }}>dispositivos físicos</strong>, completa
                  {' '}xcodeOrgId y xcodeSigningId (Ajustes › Apple Developer).
                  Para el <strong style={{ color: '#e5e5eaaa' }}>simulador</strong>, solo se necesita udid y bundleId.
                </p>
              </div>
            </>
          )}

          {/* Capabilities JSON preview */}
          <Field label="Preview JSON capabilities" span>
            <div
              className="p-3 rounded-xl text-[10px] font-mono text-slate-400 leading-relaxed overflow-auto"
              style={{ background: 'rgba(0,0,0,0.3)', border: `1px solid ${iosBorderColor}`, maxHeight: 150 }}
            >
              <pre>{JSON.stringify({
                platformName:    isIOS ? 'iOS' : 'Android',
                platformVersion: device.platformVersion,
                deviceName:      device.deviceName,
                udid:            device.udid || undefined,
                automationName:  device.automationName,
                ...(isIOS ? {
                  bundleId: device.appPackage,
                  ...(device.xcodeOrgId     && { xcodeOrgId:     device.xcodeOrgId     }),
                  ...(device.xcodeSigningId && { xcodeSigningId: device.xcodeSigningId }),
                  ...(device.wdaLocalPort   && { wdaLocalPort:   Number(device.wdaLocalPort) }),
                  ...(device.ipaPath        && { app:            device.ipaPath }),
                } : {
                  appPackage:  device.appPackage,
                  appActivity: device.appActivity,
                }),
              }, null, 2)}</pre>
            </div>
          </Field>
        </div>

        {/* Modal footer */}
        <div
          className="flex items-center justify-between px-6 py-4 gap-3"
          style={{ borderTop: `1px solid ${iosBorderColor}` }}
        >
          <button
            onClick={onDelete}
            className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold text-red-400 hover:text-red-300 transition-colors"
            style={{ background: 'rgba(244,63,94,0.08)', border: '1px solid rgba(244,63,94,0.15)' }}
          >
            <Trash2 size={13} />
            Eliminar
          </button>

          <div className="flex items-center gap-2">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-400 hover:text-slate-200 transition-colors"
              style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)' }}
            >
              Cancelar
            </button>
            <motion.button
              whileHover={{ scale: 1.02 }} whileTap={{ scale: 0.97 }}
              onClick={onSave}
              className="flex items-center gap-2 px-5 py-2 rounded-xl text-xs font-bold text-white"
              style={isIOS
                ? { background: 'linear-gradient(135deg, #3a3a4a, #4a4a5a)', boxShadow: '0 4px 14px rgba(200,200,220,0.15)' }
                : { background: 'linear-gradient(135deg, #4f46e5, #6366f1)', boxShadow: '0 4px 14px rgba(99,102,241,0.35)' }
              }
            >
              <Save size={13} />
              Guardar
            </motion.button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  )
}

// ── Primitives ─────────────────────────────────────────────────────────────────

function Field({ label, children, span }: { label: string; children: React.ReactNode; span?: boolean }) {
  return (
    <div className={span ? 'col-span-2' : 'col-span-1'}>
      <label className="block text-[10px] font-bold uppercase tracking-wider text-slate-500 mb-1.5">
        {label}
      </label>
      {children}
    </div>
  )
}

function Input({ value, onChange, placeholder, mono }: {
  value: string; onChange: (v: string) => void; placeholder?: string; mono?: boolean
}) {
  return (
    <input
      value={value}
      onChange={e => onChange(e.target.value)}
      placeholder={placeholder}
      className={`w-full px-3 py-2 rounded-xl text-xs text-slate-200 outline-none transition-all ${mono ? 'font-mono' : ''}`}
      style={{
        background: 'rgba(255,255,255,0.05)',
        border: '1px solid rgba(255,255,255,0.1)',
      }}
      onFocus={e  => (e.currentTarget.style.borderColor = 'rgba(99,102,241,0.5)')}
      onBlur={e   => (e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)')}
    />
  )
}

function Select({ value, onChange, options }: {
  value: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
}) {
  return (
    <select
      value={value}
      onChange={e => onChange(e.target.value)}
      className="w-full appearance-none px-3 py-2 rounded-xl text-xs font-semibold text-slate-200 outline-none"
      style={{
        background: 'rgba(255,255,255,0.05)',
        border: '1px solid rgba(255,255,255,0.1)',
        backgroundImage: "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 0l5 6 5-6z' fill='%2364748b'/%3E%3C/svg%3E\")",
        backgroundRepeat: 'no-repeat',
        backgroundPosition: 'right 8px center',
      }}
    >
      {options.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
    </select>
  )
}
