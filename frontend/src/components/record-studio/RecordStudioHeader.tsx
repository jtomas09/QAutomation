import React from 'react'
import { Video } from 'lucide-react'
import type { PhysicalDevice, DeviceAppConfig } from '../../types'
import { DeviceSelector } from './DeviceSelector'
import { ApplicationSelector } from './ApplicationSelector'
import { ExecutionModeSelector } from './ExecutionModeSelector'
import { RecordingStatus } from './RecordingStatus'

// ─── Shared card wrapper ───────────────────────────────────────────────────────

interface ConfigCardProps {
  step?: number
  title: string
  children: React.ReactNode
}

function ConfigCard({ step, title, children }: ConfigCardProps) {
  return (
    <div
      style={{
        background: '#111827',
        border: '1px solid #2A3144',
        borderRadius: 12,
        padding: '14px 16px',
        display: 'flex',
        flexDirection: 'column',
        gap: 10,
        flex: 1,
        minWidth: 0,
      }}
    >
      {/* Card heading: step badge + title */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        {step !== undefined && (
          <div
            style={{
              width: 22,
              height: 22,
              borderRadius: '50%',
              background: 'linear-gradient(135deg, #6366f1, #8b5cf6)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
            }}
          >
            <span style={{ color: '#fff', fontSize: 11, fontWeight: 700, lineHeight: 1 }}>
              {step}
            </span>
          </div>
        )}
        <span style={{ fontSize: 11, fontWeight: 600, color: '#94a3b8', letterSpacing: 0.3 }}>
          {title}
        </span>
      </div>

      {children}
    </div>
  )
}

// ─── Main export ──────────────────────────────────────────────────────────────

export interface RecordStudioHeaderProps {
  // Device
  devices: PhysicalDevice[]
  selectedDevice: PhysicalDevice | null
  onSelectDevice: (name: string) => void

  // Application
  appConfigs: Record<string, DeviceAppConfig>
  appConfig: DeviceAppConfig | null
  onSelectApp: (name: string) => void

  // Execution mode
  appMode: string
  onSelectMode: (mode: string) => void

  // Recording
  isRecording: boolean
  elapsed: number
  onToggleRecording: () => void
}

export function RecordStudioHeader({
  devices,
  selectedDevice,
  onSelectDevice,
  appConfigs,
  appConfig,
  onSelectApp,
  appMode,
  onSelectMode,
  isRecording,
  elapsed,
  onToggleRecording,
}: RecordStudioHeaderProps) {
  return (
    <div
      style={{
        borderBottom: '1px solid rgba(255,255,255,0.07)',
        backgroundColor: '#0d1117',
        flexShrink: 0,
        zIndex: 10,
      }}
    >
      {/* ── Title row ── */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          padding: '14px 20px 10px',
        }}
      >
        <div
          style={{
            width: 34,
            height: 34,
            borderRadius: 9,
            background: 'linear-gradient(135deg, rgba(99,102,241,0.22), rgba(129,140,248,0.12))',
            border: '1px solid rgba(99,102,241,0.3)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <Video size={16} color="#818cf8" />
        </div>
        <div>
          <h1
            style={{
              margin: 0,
              fontSize: 17,
              fontWeight: 700,
              color: '#f1f5f9',
              lineHeight: 1.2,
              letterSpacing: -0.3,
            }}
          >
            Record Studio
          </h1>
          <p style={{ margin: 0, fontSize: 11, color: '#64748b', lineHeight: 1.4 }}>
            Graba interacciones y genera pruebas automáticas
          </p>
        </div>
      </div>

      {/* ── Configuration cards row ── */}
      <div
        style={{
          display: 'flex',
          gap: 10,
          padding: '0 20px 16px',
          alignItems: 'stretch',
        }}
      >
        {/* Card 1 — Seleccionar Dispositivo */}
        <ConfigCard step={1} title="Seleccionar Dispositivo">
          <DeviceSelector
            devices={devices}
            selected={selectedDevice}
            onSelect={onSelectDevice}
          />
        </ConfigCard>

        {/* Card 2 — Seleccionar Aplicación */}
        <ConfigCard step={2} title="Seleccionar Aplicación">
          <ApplicationSelector
            appConfigs={appConfigs}
            selected={appConfig}
            onSelect={onSelectApp}
          />
        </ConfigCard>

        {/* Card 3 — Modo de Ejecución */}
        <ConfigCard step={3} title="Modo de Ejecución">
          <ExecutionModeSelector value={appMode} onChange={onSelectMode} />
        </ConfigCard>

        {/* Card 4 — Estado de Grabación (no step number) */}
        <RecordingStatus
          isRecording={isRecording}
          elapsed={elapsed}
          onToggle={onToggleRecording}
        />
      </div>
    </div>
  )
}
