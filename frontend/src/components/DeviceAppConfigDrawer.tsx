import React, { useState, useEffect, useCallback } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { X, Package, Store, Apple, CheckCircle2, Loader2, Settings2, Smartphone } from 'lucide-react'
import type { PhysicalDevice, DeviceAppConfig } from '../types'
import { getDeviceAppConfig, saveDeviceAppConfig } from '../api'

type AppMode = 'INSTALLED' | 'APK' | 'IPA'

interface Props {
  device:  PhysicalDevice | null
  isOpen:  boolean
  onClose: () => void
  onSaved: (udid: string, config: DeviceAppConfig) => void
}

const DEFAULT_ANDROID_PACKAGE = 'com.cinepolis.go'
const DEFAULT_IOS_BUNDLE      = 'mx.cinepolis.ios'

export default function DeviceAppConfigDrawer({ device, isOpen, onClose, onSaved }: Props) {
  const isIos     = device?.platform === 'IOS'
  const isAndroid = !isIos

  const [mode,       setMode]       = useState<AppMode>('INSTALLED')
  const [appPackage, setAppPackage] = useState('')
  const [bundleId,   setBundleId]   = useState('')
  const [appName,    setAppName]    = useState('')
  const [appVersion, setAppVersion] = useState('')
  const [saving,     setSaving]     = useState(false)
  const [saved,      setSaved]      = useState(false)
  const [loadError,  setLoadError]  = useState(false)

  useEffect(() => {
    if (!device || !isOpen) return
    setLoadError(false)
    setSaved(false)

    getDeviceAppConfig(device.udid).then(cfg => {
      if (cfg) {
        setMode((cfg.appMode as AppMode) || 'INSTALLED')
        setAppPackage(cfg.appPackage || '')
        setBundleId(cfg.bundleId || '')
        setAppName(cfg.appName || '')
        setAppVersion(cfg.appVersion || '')
      } else {
        setMode('INSTALLED')
        setAppPackage(isAndroid ? DEFAULT_ANDROID_PACKAGE : '')
        setBundleId(isIos     ? DEFAULT_IOS_BUNDLE      : '')
        setAppName('')
        setAppVersion('')
      }
    }).catch(() => setLoadError(true))
  }, [device?.udid, isOpen])

  const resolveSource = useCallback((): string => {
    if (mode === 'APK') return 'APK Interna'
    if (mode === 'IPA') return 'IPA Interna'
    return isAndroid ? 'Google Play Store' : 'Apple App Store'
  }, [mode, isAndroid])

  const handleSave = async () => {
    if (!device) return
    setSaving(true)
    try {
      const config: DeviceAppConfig = {
        deviceId:   device.udid,
        platform:   device.platform || '',
        appMode:    mode,
        appName:    appName.trim(),
        appPackage: appPackage.trim(),
        bundleId:   bundleId.trim(),
        appVersion: appVersion.trim(),
        source:     resolveSource(),
      }
      const saved = await saveDeviceAppConfig(device.udid, config)
      setSaved(true)
      onSaved(device.udid, saved)
      setTimeout(() => {
        setSaved(false)
        onClose()
      }, 1100)
    } catch {
      // leave saving=false, user can retry
    } finally {
      setSaving(false)
    }
  }

  const previewName   = appName.trim() || (isAndroid ? 'Cinépolis' : 'Cinépolis iOS')
  const previewSource = resolveSource()
  const previewIcon   = isIos && mode !== 'APK' ? '🍎' : mode === 'APK' || mode === 'IPA' ? '📦' : '📱'

  return (
    <AnimatePresence>
      {isOpen && device && (
        <>
          {/* Backdrop */}
          <motion.div
            key="backdrop"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.18 }}
            className="fixed inset-0 z-40"
            style={{ background: 'rgba(0,0,0,0.55)', backdropFilter: 'blur(2px)' }}
            onClick={onClose}
          />

          {/* Drawer panel */}
          <motion.div
            key="drawer"
            initial={{ x: 440 }}
            animate={{ x: 0 }}
            exit={{ x: 440 }}
            transition={{ type: 'spring', damping: 30, stiffness: 300 }}
            className="fixed right-0 top-0 h-full z-50 flex flex-col overflow-hidden"
            style={{
              width: 420,
              background: '#0d1117',
              borderLeft: '1px solid rgba(255,255,255,0.1)',
              boxShadow: '-24px 0 80px rgba(0,0,0,0.65)',
            }}
          >
            {/* ── Header ──────────────────────────────────────────────────── */}
            <div
              className="flex items-center justify-between px-6 py-5 flex-shrink-0"
              style={{ borderBottom: '1px solid rgba(255,255,255,0.07)' }}
            >
              <div className="flex items-center gap-3">
                <div
                  className="w-9 h-9 rounded-xl flex items-center justify-center"
                  style={{ background: 'rgba(99,102,241,0.15)', border: '1px solid rgba(99,102,241,0.25)' }}
                >
                  <Settings2 size={16} style={{ color: '#818cf8' }} />
                </div>
                <div>
                  <div className="text-[13px] font-bold text-slate-100">Configurar App</div>
                  <div className="text-[11px] text-slate-500 mt-0.5">
                    {device.deviceName || 'Dispositivo'} · {device.platform}
                  </div>
                </div>
              </div>
              <button
                onClick={onClose}
                className="w-7 h-7 flex items-center justify-center rounded-lg text-slate-500 hover:text-slate-300 transition-colors"
                style={{ background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.08)' }}
              >
                <X size={13} />
              </button>
            </div>

            {/* ── Body ────────────────────────────────────────────────────── */}
            <div className="flex-1 overflow-y-auto px-6 py-5 space-y-6">

              {loadError && (
                <div
                  className="flex items-center gap-2 px-4 py-3 rounded-xl text-[12px] text-amber-400"
                  style={{ background: 'rgba(245,158,11,0.08)', border: '1px solid rgba(245,158,11,0.2)' }}
                >
                  Error cargando config existente. Puedes configurarla de nuevo.
                </div>
              )}

              {/* Mode selector */}
              <div>
                <div className="text-[10px] font-black tracking-widest text-slate-500 mb-3">
                  MODO DE EJECUCIÓN
                </div>
                <div className="space-y-2">
                  {(['INSTALLED', 'APK', ...(isIos ? ['IPA'] : [])] as AppMode[]).map(m => {
                    const label = m === 'INSTALLED'
                      ? `Instalada (${isAndroid ? 'Google Play' : 'App Store'})`
                      : m
                    const active = mode === m
                    return (
                      <button
                        key={m}
                        onClick={() => setMode(m)}
                        className="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-left transition-all"
                        style={{
                          background: active ? 'rgba(99,102,241,0.12)' : 'rgba(255,255,255,0.03)',
                          border: `1px solid ${active ? 'rgba(99,102,241,0.4)' : 'rgba(255,255,255,0.07)'}`,
                        }}
                      >
                        <div
                          className="w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0"
                          style={{ borderColor: active ? '#818cf8' : '#475569' }}
                        >
                          {active && (
                            <div className="w-2 h-2 rounded-full" style={{ background: '#818cf8' }} />
                          )}
                        </div>
                        <span className="flex-shrink-0" style={{ color: active ? '#818cf8' : '#64748b' }}>
                          {m === 'INSTALLED'
                            ? (isAndroid ? <Store size={14} /> : <Apple size={14} />)
                            : <Package size={14} />
                          }
                        </span>
                        <span
                          className="text-[12px] font-semibold"
                          style={{ color: active ? '#c7d2fe' : '#64748b' }}
                        >
                          {label}
                        </span>
                      </button>
                    )
                  })}
                </div>
              </div>

              {/* Identifiers */}
              <div>
                <div className="text-[10px] font-black tracking-widest text-slate-500 mb-3">
                  IDENTIFICADOR DE APLICACIÓN
                </div>
                <div className="space-y-3">
                  {isAndroid && (
                    <div>
                      <label className="text-[11px] font-semibold text-slate-400 mb-1.5 block">
                        Package Name
                      </label>
                      <input
                        type="text"
                        value={appPackage}
                        onChange={e => setAppPackage(e.target.value)}
                        placeholder="com.example.app"
                        className="w-full px-3 py-2.5 rounded-xl text-[12px] font-mono outline-none transition-all"
                        style={{
                          background: 'rgba(255,255,255,0.05)',
                          border: '1px solid rgba(255,255,255,0.1)',
                          color: '#e2e8f0',
                        }}
                        onFocus={e => { e.currentTarget.style.borderColor = 'rgba(99,102,241,0.5)' }}
                        onBlur={e =>  { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)' }}
                      />
                    </div>
                  )}
                  {isIos && (
                    <div>
                      <label className="text-[11px] font-semibold text-slate-400 mb-1.5 block">
                        Bundle ID
                      </label>
                      <input
                        type="text"
                        value={bundleId}
                        onChange={e => setBundleId(e.target.value)}
                        placeholder="com.example.ios"
                        className="w-full px-3 py-2.5 rounded-xl text-[12px] font-mono outline-none"
                        style={{
                          background: 'rgba(255,255,255,0.05)',
                          border: '1px solid rgba(255,255,255,0.1)',
                          color: '#e2e8f0',
                        }}
                        onFocus={e => { e.currentTarget.style.borderColor = 'rgba(99,102,241,0.5)' }}
                        onBlur={e =>  { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)' }}
                      />
                    </div>
                  )}
                  {mode === 'INSTALLED' && (
                    <div
                      className="flex items-center gap-1.5 text-[11px] font-semibold"
                      style={{ color: '#64748b' }}
                    >
                      {isAndroid ? <Store size={12} /> : <Apple size={12} />}
                      <span>{isAndroid ? 'Google Play Store' : 'Apple App Store'}</span>
                    </div>
                  )}
                </div>
              </div>

              {/* App info */}
              <div>
                <div className="text-[10px] font-black tracking-widest text-slate-500 mb-3">
                  INFORMACIÓN DE LA APP
                </div>
                <div className="flex gap-3">
                  <div className="flex-1">
                    <label className="text-[11px] font-semibold text-slate-400 mb-1.5 block">
                      Nombre
                    </label>
                    <input
                      type="text"
                      value={appName}
                      onChange={e => setAppName(e.target.value)}
                      placeholder="Cinépolis"
                      className="w-full px-3 py-2.5 rounded-xl text-[12px] outline-none"
                      style={{
                        background: 'rgba(255,255,255,0.05)',
                        border: '1px solid rgba(255,255,255,0.1)',
                        color: '#e2e8f0',
                      }}
                      onFocus={e => { e.currentTarget.style.borderColor = 'rgba(99,102,241,0.5)' }}
                      onBlur={e =>  { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)' }}
                    />
                  </div>
                  <div className="w-28">
                    <label className="text-[11px] font-semibold text-slate-400 mb-1.5 block">
                      Versión
                    </label>
                    <input
                      type="text"
                      value={appVersion}
                      onChange={e => setAppVersion(e.target.value)}
                      placeholder="8.3.1"
                      className="w-full px-3 py-2.5 rounded-xl text-[12px] font-mono outline-none"
                      style={{
                        background: 'rgba(255,255,255,0.05)',
                        border: '1px solid rgba(255,255,255,0.1)',
                        color: '#e2e8f0',
                      }}
                      onFocus={e => { e.currentTarget.style.borderColor = 'rgba(99,102,241,0.5)' }}
                      onBlur={e =>  { e.currentTarget.style.borderColor = 'rgba(255,255,255,0.1)' }}
                    />
                  </div>
                </div>
              </div>

              {/* Preview */}
              <div>
                <div className="text-[10px] font-black tracking-widest text-slate-500 mb-3">
                  VISTA PREVIA EN CARD
                </div>
                <div
                  className="flex items-center gap-3 px-4 py-3.5 rounded-xl"
                  style={{ background: 'rgba(255,255,255,0.04)', border: '1px solid rgba(255,255,255,0.08)' }}
                >
                  <Smartphone size={20} style={{ color: '#475569', flexShrink: 0 }} />
                  <div className="min-w-0">
                    <div className="text-[12px] font-bold text-slate-200 truncate">
                      {previewIcon} {previewName}
                    </div>
                    <div className="text-[10px] text-slate-500 mt-0.5">
                      {mode === 'INSTALLED' ? (isAndroid ? '🏪' : '🍎') : '📦'} {previewSource}
                    </div>
                    {appVersion.trim() && (
                      <div className="text-[10px] font-mono text-slate-600 mt-0.5">v{appVersion.trim()}</div>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* ── Footer ──────────────────────────────────────────────────── */}
            <div
              className="px-6 py-5 flex-shrink-0"
              style={{ borderTop: '1px solid rgba(255,255,255,0.07)' }}
            >
              <button
                onClick={handleSave}
                disabled={saving || saved}
                className="w-full flex items-center justify-center gap-2 py-3 rounded-xl text-[13px] font-bold transition-all"
                style={{
                  background: saved
                    ? 'rgba(16,185,129,0.2)'
                    : 'rgba(99,102,241,0.85)',
                  border: `1px solid ${saved ? 'rgba(16,185,129,0.4)' : 'rgba(99,102,241,0.6)'}`,
                  color: saved ? '#10b981' : '#fff',
                  opacity: saving ? 0.75 : 1,
                }}
              >
                {saving ? (
                  <><Loader2 size={15} className="animate-spin" />Guardando...</>
                ) : saved ? (
                  <><CheckCircle2 size={15} />Guardado</>
                ) : (
                  'Guardar Configuración'
                )}
              </button>
            </div>
          </motion.div>
        </>
      )}
    </AnimatePresence>
  )
}
