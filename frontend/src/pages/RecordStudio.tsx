import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Video, Square, ChevronDown, ChevronRight, ChevronUp,
  Camera, Settings2, Maximize2, RotateCcw, Smartphone,
  Copy, Download, Check, X, MousePointer2, Type,
  MoveHorizontal, ChevronsDown, Keyboard, Clock, Code2,
  FileCode2, Layers3, Plus, Trash2, Play, Circle,
  Hand, Zap, Search, Wifi, Eye, AlertCircle,
} from 'lucide-react'
import { getDevices, getAllDeviceAppConfigs } from '../api'
import type { PhysicalDevice, DeviceAppConfig } from '../types'

// ─── Local Types ──────────────────────────────────────────────────────────────

type RecState = 'idle' | 'recording'
type StepType = 'tap' | 'double_tap' | 'long_press' | 'input' | 'swipe' | 'scroll' | 'hide_keyboard'
type AppScreen = 'home' | 'login'
type Lang = 'java-testng' | 'java-junit'
type ViewTab = 'code' | 'xml'

interface AppEl {
  shortId:    string
  resourceId: string
  accessId:   string
  text:       string
  elType:     'btn' | 'input' | 'text' | 'list' | 'image'
}

interface RecStep {
  id:        string
  n:         number
  type:      StepType
  el:        AppEl | null
  inputVal?: string
  dir?:      'up' | 'down' | 'left' | 'right'
  timeStr:   string
}

interface GenOpts {
  pageObjects:  boolean
  assertions:   boolean
  smartWaits:   boolean
  screenshots:  boolean
}

// ─── Cinépolis App Data ───────────────────────────────────────────────────────

const ANDROID_PKG = 'com.cinepolis.go'

const HOME_ELS: Record<string, AppEl> = {
  misCompras: {
    shortId: 'btn_mis_compras',
    resourceId: `${ANDROID_PKG}:id/btn_mis_compras`,
    accessId: 'Mis Compras',
    text: 'Mis compras',
    elType: 'btn',
  },
  iniciarSesion: {
    shortId: 'btn_iniciar_sesion',
    resourceId: `${ANDROID_PKG}:id/btn_iniciar_sesion`,
    accessId: 'Iniciar Sesión',
    text: 'Iniciar Sesión',
    elType: 'btn',
  },
  buscar: {
    shortId: 'txt_buscar',
    resourceId: `${ANDROID_PKG}:id/txt_buscar`,
    accessId: 'Buscar',
    text: 'Buscar película...',
    elType: 'input',
  },
  tabCartelera: {
    shortId: 'tab_cartelera',
    resourceId: `${ANDROID_PKG}:id/tab_cartelera`,
    accessId: 'En cartelera',
    text: 'En cartelera',
    elType: 'btn',
  },
  tabProximos: {
    shortId: 'tab_proximos',
    resourceId: `${ANDROID_PKG}:id/tab_proximos`,
    accessId: 'Próximos estrenos',
    text: 'Próximos estrenos',
    elType: 'btn',
  },
  pelicula_duna: {
    shortId: 'rv_pelicula_duna',
    resourceId: `${ANDROID_PKG}:id/rv_pelicula_duna`,
    accessId: 'Duna',
    text: 'Duna: Parte Dos',
    elType: 'list',
  },
  pelicula_garfield: {
    shortId: 'rv_pelicula_garfield',
    resourceId: `${ANDROID_PKG}:id/rv_pelicula_garfield`,
    accessId: 'Garfield',
    text: 'Garfield',
    elType: 'list',
  },
  navInicio: {
    shortId: 'btn_nav_inicio',
    resourceId: `${ANDROID_PKG}:id/btn_nav_inicio`,
    accessId: 'Inicio',
    text: 'Inicio',
    elType: 'btn',
  },
  navMisCompras: {
    shortId: 'btn_nav_mis_compras',
    resourceId: `${ANDROID_PKG}:id/btn_nav_mis_compras`,
    accessId: 'Mis compras',
    text: 'Mis compras',
    elType: 'btn',
  },
  navCines: {
    shortId: 'btn_nav_cines',
    resourceId: `${ANDROID_PKG}:id/btn_nav_cines`,
    accessId: 'Cines',
    text: 'Cines',
    elType: 'btn',
  },
  navAlimentos: {
    shortId: 'btn_nav_alimentos',
    resourceId: `${ANDROID_PKG}:id/btn_nav_alimentos`,
    accessId: 'Alimentos',
    text: 'Alimentos',
    elType: 'btn',
  },
  navMas: {
    shortId: 'btn_nav_mas',
    resourceId: `${ANDROID_PKG}:id/btn_nav_mas`,
    accessId: 'Más',
    text: 'Más',
    elType: 'btn',
  },
}

const LOGIN_ELS: Record<string, AppEl> = {
  correo: {
    shortId: 'txt_correo',
    resourceId: `${ANDROID_PKG}:id/txt_correo`,
    accessId: 'Correo',
    text: 'Correo electrónico',
    elType: 'input',
  },
  password: {
    shortId: 'txt_password',
    resourceId: `${ANDROID_PKG}:id/txt_password`,
    accessId: 'Contraseña',
    text: 'Contraseña',
    elType: 'input',
  },
  entrar: {
    shortId: 'btn_entrar',
    resourceId: `${ANDROID_PKG}:id/btn_entrar`,
    accessId: 'Iniciar sesión',
    text: 'Iniciar Sesión',
    elType: 'btn',
  },
}

// ─── Step type helpers ────────────────────────────────────────────────────────

const STEP_COLORS: Record<StepType, string> = {
  tap:          '#818cf8',
  double_tap:   '#a78bfa',
  long_press:   '#c084fc',
  input:        '#34d399',
  swipe:        '#f59e0b',
  scroll:       '#60a5fa',
  hide_keyboard:'#f43f5e',
}

function stepTypeLabel(type: StepType): string {
  switch (type) {
    case 'tap':           return 'Tap'
    case 'double_tap':    return 'Double Tap'
    case 'long_press':    return 'Long Press'
    case 'input':         return 'Escribir texto'
    case 'swipe':         return 'Swipe'
    case 'scroll':        return 'Scroll'
    case 'hide_keyboard': return 'Ocultar teclado'
  }
}

// ─── Code generation helpers ──────────────────────────────────────────────────

function cap(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1)
}

function toMethodName(shortId: string): string {
  return shortId.replace(/^(btn|txt|rv|tab|iv|cb)_/, '').split('_').map(cap).join('')
}

function selectorStr(el: AppEl, isAndroid: boolean): string {
  if (isAndroid) return `By.id("${el.resourceId}")`
  return `AppiumBy.accessibilityId("${el.accessId}")`
}

function generateJava(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
  lang: Lang,
): string {
  const isAndroid = platform.toUpperCase() !== 'IOS'
  const effectiveTestName = testName.trim() || 'myTest'
  const effectiveClassName = className.trim() || 'GeneratedTest'

  const lines: string[] = []

  // Imports
  lines.push('import io.appium.java_client.AppiumBy;')
  lines.push('import io.appium.java_client.AppiumDriver;')
  lines.push('import io.appium.java_client.android.AndroidDriver;')
  lines.push('import io.appium.java_client.ios.IOSDriver;')
  lines.push('import org.openqa.selenium.By;')
  lines.push('import org.openqa.selenium.WebElement;')
  lines.push('import org.openqa.selenium.support.ui.ExpectedConditions;')
  lines.push('import org.openqa.selenium.support.ui.WebDriverWait;')
  if (lang === 'java-testng') {
    lines.push('import org.testng.Assert;')
    lines.push('import org.testng.annotations.Test;')
  } else {
    lines.push('import org.junit.Assert;')
    lines.push('import org.junit.Test;')
  }
  if (opts.pageObjects) {
    lines.push('import io.appium.java_client.pagefactory.AndroidFindBy;')
    lines.push('import io.appium.java_client.pagefactory.iOSXCUITFindBy;')
    lines.push('import io.appium.java_client.pagefactory.AppiumFieldDecorator;')
    lines.push('import org.openqa.selenium.support.PageFactory;')
  }
  lines.push('')

  // Page Objects class
  if (opts.pageObjects) {
    const uniqueEls = new Map<string, AppEl>()
    for (const step of steps) {
      if (step.el) uniqueEls.set(step.el.shortId, step.el)
    }

    lines.push(`public class CinepolisPage extends BaseMobilePage {`)
    lines.push('')
    for (const [, el] of uniqueEls) {
      const methodName = toMethodName(el.shortId)
      const fieldName = methodName.charAt(0).toLowerCase() + methodName.slice(1)
      lines.push(`    @AndroidFindBy(id = "${el.resourceId}")`)
      lines.push(`    @iOSXCUITFindBy(accessibility = "${el.accessId}")`)
      lines.push(`    private WebElement ${fieldName};`)
      lines.push('')
    }
    lines.push(`    public CinepolisPage(AppiumDriver driver) {`)
    lines.push(`        super(driver);`)
    lines.push(`        PageFactory.initElements(new AppiumFieldDecorator(driver), this);`)
    lines.push(`    }`)
    lines.push('')
    for (const [, el] of uniqueEls) {
      const methodName = toMethodName(el.shortId)
      const fieldName = methodName.charAt(0).toLowerCase() + methodName.slice(1)
      lines.push(`    public void tap${methodName}() {`)
      lines.push(`        ${fieldName}.click();`)
      lines.push(`    }`)
      lines.push('')
      if (el.elType === 'input') {
        lines.push(`    public void type${methodName}(String value) {`)
        lines.push(`        ${fieldName}.clear();`)
        lines.push(`        ${fieldName}.sendKeys(value);`)
        lines.push(`    }`)
        lines.push('')
      }
    }
    lines.push(`}`)
    lines.push('')
  }

  // Test class
  lines.push(`public class ${effectiveClassName} extends BaseTest {`)
  lines.push('')
  lines.push(`    @Test`)
  lines.push(`    public void ${effectiveTestName}() {`)

  for (const step of steps) {
    lines.push('')
    const label = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`        // ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)

    const sel = step.el ? selectorStr(step.el, isAndroid) : null

    if (opts.smartWaits && sel) {
      lines.push(`        waitForElement(${sel});`)
    }

    switch (step.type) {
      case 'tap':
        if (opts.pageObjects && step.el) {
          lines.push(`        page.tap${toMethodName(step.el.shortId)}();`)
        } else if (sel) {
          lines.push(`        click(${sel});`)
        }
        break
      case 'double_tap':
        if (sel) lines.push(`        doubleTap(${sel});`)
        break
      case 'long_press':
        if (sel) lines.push(`        longPress(${sel});`)
        break
      case 'input':
        if (opts.pageObjects && step.el) {
          lines.push(`        page.type${toMethodName(step.el.shortId)}("${step.inputVal ?? ''}");`)
        } else if (sel) {
          lines.push(`        clear(${sel});`)
          lines.push(`        type(${sel}, "${step.inputVal ?? ''}");`)
        }
        if (opts.assertions && sel) {
          lines.push(`        Assert.assertEquals(getValue(${sel}), "${step.inputVal ?? ''}");`)
        }
        break
      case 'swipe':
        lines.push(`        swipe(Direction.${(step.dir ?? 'up').toUpperCase()});`)
        break
      case 'scroll':
        lines.push(`        scrollDown();`)
        break
      case 'hide_keyboard':
        lines.push(`        driver.hideKeyboard();`)
        break
    }

    if (opts.screenshots) {
      lines.push(`        captureScreenshot("step_${step.n}");`)
    }
  }

  lines.push(`    }`)
  lines.push(`}`)

  return lines.join('\n')
}

function generateXML(steps: RecStep[], platform: string): string {
  const lines: string[] = []
  lines.push('<?xml version="1.0" encoding="UTF-8"?>')
  lines.push(`<recording platform="${platform.toUpperCase()}" steps="${steps.length}">`)
  for (const step of steps) {
    lines.push(`  <step type="${step.type}" timestamp="${step.timeStr}" n="${step.n}">`)
    if (step.el) {
      lines.push(`    <element`)
      lines.push(`      resource-id="${step.el.resourceId}"`)
      lines.push(`      content-desc="${step.el.accessId}"`)
      lines.push(`      text="${step.el.text}"`)
      lines.push(`    />`)
    }
    if (step.inputVal) {
      lines.push(`    <value>${step.inputVal}</value>`)
    }
    if (step.dir) {
      lines.push(`    <direction>${step.dir}</direction>`)
    }
    lines.push(`  </step>`)
  }
  lines.push('</recording>')
  return lines.join('\n')
}

// ─── Syntax highlighter ───────────────────────────────────────────────────────

function SyntaxLine({ line }: { line: string }) {
  const trimmed = line.trimStart()

  if (trimmed.startsWith('//')) {
    return <span style={{ color: '#6a9955' }}>{line}</span>
  }

  if (trimmed.startsWith('@')) {
    return <span style={{ color: '#c586c0' }}>{line}</span>
  }

  if (trimmed.startsWith('import ')) {
    return <span style={{ color: '#4fc1ff' }}>{line}</span>
  }

  if (trimmed.includes('By.id(') || trimmed.includes('AppiumBy')) {
    return <span style={{ color: '#9cdcfe' }}>{line}</span>
  }

  // General line: split by double-quoted strings, then color keywords
  const KEYWORDS = /\b(public|void|class|extends|static|private|new|return|if|else|for|while|this)\b/g
  const parts = line.split(/(\"[^\"]*\")/g)

  return (
    <span>
      {parts.map((part, i) => {
        if (part.startsWith('"') && part.endsWith('"') && part.length >= 2) {
          return <span key={i} style={{ color: '#ce9178' }}>{part}</span>
        }
        // Split by keywords
        const subparts = part.split(KEYWORDS)
        const kwMatches = part.match(KEYWORDS) ?? []
        if (kwMatches.length === 0) {
          return <span key={i} style={{ color: '#d4d4d4' }}>{part}</span>
        }
        let kwIdx = 0
        return (
          <span key={i}>
            {subparts.map((sp, j) => {
              if (j % 2 === 1) {
                const kw = kwMatches[kwIdx++]
                return <span key={j} style={{ color: '#569cd6' }}>{kw}</span>
              }
              return <span key={j} style={{ color: '#d4d4d4' }}>{sp}</span>
            })}
          </span>
        )
      })}
    </span>
  )
}

// ─── RecordableEl ─────────────────────────────────────────────────────────────

interface RecordableElProps {
  el: AppEl
  recording: boolean
  onRecord: (el: AppEl) => void
  children: React.ReactNode
  style?: React.CSSProperties
  className?: string
}

const RecordableEl = React.memo(function RecordableEl({
  el,
  recording,
  onRecord,
  children,
  style,
  className,
}: RecordableElProps) {
  const [hovered, setHovered] = useState(false)

  const handleClick = useCallback(
    (e: React.MouseEvent) => {
      if (!recording) return
      e.stopPropagation()
      onRecord(el)
    },
    [recording, onRecord, el],
  )

  return (
    <div
      style={{
        position: 'relative',
        cursor: recording ? 'crosshair' : 'default',
        outline: recording && hovered ? '2px solid #3b82f6' : 'none',
        outlineOffset: '-1px',
        borderRadius: 4,
        ...style,
      }}
      className={className}
      onMouseEnter={() => recording && setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={handleClick}
    >
      {children}
      {recording && hovered && (
        <div
          style={{
            position: 'absolute',
            top: -22,
            left: 0,
            background: '#1e40af',
            color: '#fff',
            fontSize: 9,
            padding: '2px 5px',
            borderRadius: 3,
            whiteSpace: 'nowrap',
            zIndex: 100,
            pointerEvents: 'none',
            fontFamily: 'monospace',
          }}
        >
          {el.shortId}
        </div>
      )}
    </div>
  )
})

// ─── Cinépolis Home Screen ────────────────────────────────────────────────────

interface HomeScreenProps {
  recording: boolean
  onRecord: (el: AppEl) => void
  pkg: string
  onScreenChange: (screen: AppScreen) => void
}

const CinepolisHomeScreen = React.memo(function CinepolisHomeScreen({
  recording,
  onRecord,
  onScreenChange,
}: HomeScreenProps) {
  const handleRecord = useCallback(
    (el: AppEl) => {
      onRecord(el)
      if (
        el.shortId === HOME_ELS.navMisCompras.shortId ||
        el.shortId === HOME_ELS.iniciarSesion.shortId
      ) {
        setTimeout(() => onScreenChange('login'), 150)
      }
    },
    [onRecord, onScreenChange],
  )

  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        backgroundColor: '#ffffff',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        fontFamily: 'system-ui, sans-serif',
        fontSize: 13,
      }}
    >
      {/* App Header */}
      <div
        style={{
          backgroundColor: '#003087',
          padding: '10px 12px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <span
          style={{
            color: '#ffffff',
            fontStyle: 'italic',
            fontWeight: 700,
            fontSize: 16,
            letterSpacing: 0.5,
          }}
        >
          cinépolis
        </span>
        <div style={{ display: 'flex', gap: 8 }}>
          <Search size={14} color="#ffffff" />
          <div
            style={{
              width: 14,
              height: 14,
              borderRadius: '50%',
              backgroundColor: 'rgba(255,255,255,0.25)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <div
              style={{
                width: 8,
                height: 8,
                borderRadius: '50%',
                backgroundColor: 'rgba(255,255,255,0.5)',
              }}
            />
          </div>
        </div>
      </div>

      {/* Body */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '10px 10px 0' }}>
        <p style={{ fontSize: 10, color: '#333', marginBottom: 8, fontWeight: 500 }}>
          ¡Bienvenido! ¿Qué vamos a ver hoy?
        </p>

        {/* Search bar */}
        <RecordableEl el={HOME_ELS.buscar} recording={recording} onRecord={handleRecord}>
          <div
            style={{
              backgroundColor: '#f5f5f5',
              border: '1px solid #e0e0e0',
              borderRadius: 20,
              padding: '5px 10px',
              fontSize: 10,
              color: '#888',
              marginBottom: 10,
              display: 'flex',
              alignItems: 'center',
              gap: 4,
            }}
          >
            <Search size={9} color="#888" />
            <span>Buscar película...</span>
          </div>
        </RecordableEl>

        {/* Tabs */}
        <div style={{ display: 'flex', marginBottom: 10, borderBottom: '1px solid #e0e0e0' }}>
          <RecordableEl el={HOME_ELS.tabCartelera} recording={recording} onRecord={handleRecord}>
            <div
              style={{
                padding: '4px 8px',
                fontSize: 9,
                fontWeight: 600,
                color: '#003087',
                borderBottom: '2px solid #003087',
                marginBottom: -1,
              }}
            >
              En cartelera
            </div>
          </RecordableEl>
          <RecordableEl el={HOME_ELS.tabProximos} recording={recording} onRecord={handleRecord}>
            <div
              style={{
                padding: '4px 8px',
                fontSize: 9,
                color: '#888',
              }}
            >
              Próximos estrenos
            </div>
          </RecordableEl>
        </div>

        {/* Movie cards */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
          <RecordableEl el={HOME_ELS.pelicula_duna} recording={recording} onRecord={handleRecord}>
            <div
              style={{
                width: 64,
                height: 88,
                backgroundColor: '#8B6914',
                borderRadius: 6,
                display: 'flex',
                alignItems: 'flex-end',
                padding: 4,
                cursor: 'pointer',
              }}
            >
              <span style={{ color: '#fff', fontSize: 7, fontWeight: 600, lineHeight: 1.2 }}>
                Duna: Parte Dos
              </span>
            </div>
          </RecordableEl>
          <RecordableEl
            el={HOME_ELS.pelicula_garfield}
            recording={recording}
            onRecord={handleRecord}
          >
            <div
              style={{
                width: 64,
                height: 88,
                backgroundColor: '#d97706',
                borderRadius: 6,
                display: 'flex',
                alignItems: 'flex-end',
                padding: 4,
                cursor: 'pointer',
              }}
            >
              <span style={{ color: '#fff', fontSize: 7, fontWeight: 600, lineHeight: 1.2 }}>
                Garfield
              </span>
            </div>
          </RecordableEl>
          <div
            style={{
              width: 64,
              height: 88,
              backgroundColor: '#7c3aed',
              borderRadius: 6,
              display: 'flex',
              alignItems: 'flex-end',
              padding: 4,
            }}
          >
            <span style={{ color: '#fff', fontSize: 7, fontWeight: 600, lineHeight: 1.2 }}>
              Intensamente 2
            </span>
          </div>
        </div>
      </div>

      {/* Bottom Nav */}
      <div
        style={{
          display: 'flex',
          borderTop: '1px solid #e0e0e0',
          backgroundColor: '#ffffff',
        }}
      >
        {[
          { el: HOME_ELS.navInicio, label: 'Inicio', active: true },
          { el: HOME_ELS.navCines, label: 'Cines', active: false },
          { el: HOME_ELS.navAlimentos, label: 'Alimentos', active: false },
          { el: HOME_ELS.navMisCompras, label: 'Mis compras', active: false },
          { el: HOME_ELS.navMas, label: 'Más', active: false },
        ].map(({ el, label, active }) => (
          <RecordableEl
            key={el.shortId}
            el={el}
            recording={recording}
            onRecord={handleRecord}
            style={{ flex: 1 }}
          >
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                padding: '5px 2px',
                gap: 2,
              }}
            >
              <div
                style={{
                  width: 16,
                  height: 16,
                  borderRadius: 3,
                  backgroundColor: active ? '#003087' : '#ccc',
                }}
              />
              <span
                style={{
                  fontSize: 7,
                  color: active ? '#003087' : '#888',
                  textAlign: 'center',
                  lineHeight: 1.2,
                }}
              >
                {label}
              </span>
            </div>
          </RecordableEl>
        ))}
      </div>
    </div>
  )
})

// ─── Cinépolis Login Screen ───────────────────────────────────────────────────

interface LoginScreenProps {
  recording: boolean
  onRecord: (el: AppEl) => void
  onScreenChange: (screen: AppScreen) => void
}

const CinepolisLoginScreen = React.memo(function CinepolisLoginScreen({
  recording,
  onRecord,
  onScreenChange,
}: LoginScreenProps) {
  const handleRecord = useCallback(
    (el: AppEl) => {
      onRecord(el)
      if (el.shortId === LOGIN_ELS.entrar.shortId) {
        setTimeout(() => onScreenChange('home'), 150)
      }
    },
    [onRecord, onScreenChange],
  )

  return (
    <div
      style={{
        width: '100%',
        height: '100%',
        backgroundColor: '#ffffff',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: 'system-ui, sans-serif',
      }}
    >
      {/* Header */}
      <div
        style={{
          backgroundColor: '#003087',
          padding: '10px 12px',
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        <ChevronRight
          size={12}
          color="#fff"
          style={{ transform: 'rotate(180deg)', flexShrink: 0 }}
        />
        <span style={{ color: '#fff', fontWeight: 600, fontSize: 13 }}>Iniciar Sesión</span>
      </div>

      {/* Form */}
      <div style={{ padding: '16px 14px', display: 'flex', flexDirection: 'column', gap: 12 }}>
        <p style={{ fontSize: 11, color: '#333', fontWeight: 600, margin: 0 }}>Hola de nuevo</p>
        <p style={{ fontSize: 9, color: '#888', margin: 0 }}>
          Ingresa tus credenciales para continuar
        </p>

        <RecordableEl el={LOGIN_ELS.correo} recording={recording} onRecord={handleRecord}>
          <div
            style={{
              border: '1px solid #e0e0e0',
              borderRadius: 6,
              padding: '7px 10px',
              fontSize: 9,
              color: '#aaa',
            }}
          >
            Correo electrónico
          </div>
        </RecordableEl>

        <RecordableEl el={LOGIN_ELS.password} recording={recording} onRecord={handleRecord}>
          <div
            style={{
              border: '1px solid #e0e0e0',
              borderRadius: 6,
              padding: '7px 10px',
              fontSize: 9,
              color: '#aaa',
              letterSpacing: 3,
            }}
          >
            Contraseña
          </div>
        </RecordableEl>

        <RecordableEl el={LOGIN_ELS.entrar} recording={recording} onRecord={handleRecord}>
          <div
            style={{
              backgroundColor: '#003087',
              borderRadius: 6,
              padding: '8px 10px',
              textAlign: 'center',
              fontSize: 10,
              color: '#fff',
              fontWeight: 600,
            }}
          >
            Iniciar Sesión
          </div>
        </RecordableEl>

        <p style={{ fontSize: 8, color: '#3b82f6', textAlign: 'center', margin: 0 }}>
          ¿Olvidaste tu contraseña?
        </p>
      </div>
    </div>
  )
})

// ─── Phone Frame ──────────────────────────────────────────────────────────────

interface PhoneFrameProps {
  recording: boolean
  screen: AppScreen
  onRecord: (el: AppEl) => void
  onScreenChange: (s: AppScreen) => void
}

const PhoneFrame = React.memo(function PhoneFrame({
  recording,
  screen,
  onRecord,
  onScreenChange,
}: PhoneFrameProps) {
  const PHONE_W = 220
  const SCREEN_W = 190
  const SCREEN_H = 340
  const SCALE = 0.9

  return (
    <div
      style={{
        width: PHONE_W,
        background: '#1a1a1a',
        borderRadius: 32,
        padding: '12px 15px',
        boxShadow: '0 0 0 1px rgba(255,255,255,0.08), 0 8px 40px rgba(0,0,0,0.6)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 0,
        position: 'relative',
      }}
    >
      {/* Notch */}
      <div
        style={{
          width: 70,
          height: 8,
          backgroundColor: '#000',
          borderRadius: 10,
          marginBottom: 8,
        }}
      />

      {/* Status bar */}
      <div
        style={{
          width: SCREEN_W,
          backgroundColor: '#003087',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '3px 8px',
          fontSize: 8,
          color: '#ffffff',
          borderTopLeftRadius: 4,
          borderTopRightRadius: 4,
        }}
      >
        <span style={{ fontWeight: 600 }}>12:30</span>
        <div style={{ display: 'flex', gap: 3, alignItems: 'center' }}>
          <Wifi size={7} color="#fff" />
          <div
            style={{
              width: 14,
              height: 7,
              border: '1px solid #fff',
              borderRadius: 2,
              position: 'relative',
            }}
          >
            <div
              style={{
                position: 'absolute',
                left: 1,
                top: 1,
                width: '75%',
                height: 'calc(100% - 2px)',
                backgroundColor: '#fff',
                borderRadius: 1,
              }}
            />
          </div>
        </div>
      </div>

      {/* Screen */}
      <div
        style={{
          width: SCREEN_W,
          height: SCREEN_H,
          overflow: 'hidden',
          backgroundColor: '#fff',
          transform: `scale(${SCALE})`,
          transformOrigin: 'top center',
          marginBottom: -(SCREEN_H * (1 - SCALE)),
        }}
      >
        <AnimatePresence mode="wait">
          {screen === 'home' ? (
            <motion.div
              key="home"
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 10 }}
              transition={{ duration: 0.15 }}
              style={{ width: '100%', height: '100%' }}
            >
              <CinepolisHomeScreen
                recording={recording}
                onRecord={onRecord}
                pkg={ANDROID_PKG}
                onScreenChange={onScreenChange}
              />
            </motion.div>
          ) : (
            <motion.div
              key="login"
              initial={{ opacity: 0, x: 10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -10 }}
              transition={{ duration: 0.15 }}
              style={{ width: '100%', height: '100%' }}
            >
              <CinepolisLoginScreen
                recording={recording}
                onRecord={onRecord}
                onScreenChange={onScreenChange}
              />
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* Home indicator */}
      <div
        style={{
          width: 60,
          height: 4,
          backgroundColor: 'rgba(255,255,255,0.3)',
          borderRadius: 3,
          marginTop: 8,
        }}
      />
    </div>
  )
})

// ─── Manual Action Bar ────────────────────────────────────────────────────────

interface ManualActionBarProps {
  onManualAdd: (
    type: StepType,
    elementId: string,
    inputVal?: string,
    dir?: 'up' | 'down' | 'left' | 'right',
  ) => void
}

interface ManualDialog {
  type: StepType
  elementId: string
  inputVal: string
  dir: 'up' | 'down' | 'left' | 'right'
}

const ManualActionBar = React.memo(function ManualActionBar({ onManualAdd }: ManualActionBarProps) {
  const [dialog, setDialog] = useState<ManualDialog | null>(null)

  const openDialog = (type: StepType) => {
    setDialog({ type, elementId: '', inputVal: '', dir: 'down' })
  }

  const confirm = () => {
    if (!dialog) return
    if (dialog.type === 'scroll' || dialog.type === 'hide_keyboard') {
      onManualAdd(dialog.type, '', undefined, undefined)
    } else if (dialog.type === 'swipe') {
      onManualAdd(dialog.type, dialog.elementId, undefined, dialog.dir)
    } else if (dialog.type === 'input') {
      onManualAdd(dialog.type, dialog.elementId, dialog.inputVal, undefined)
    } else {
      onManualAdd(dialog.type, dialog.elementId, undefined, undefined)
    }
    setDialog(null)
  }

  const actions: Array<{ type: StepType; label: string; icon: React.ReactNode }> = [
    { type: 'tap', label: 'Tap', icon: <MousePointer2 size={11} /> },
    { type: 'input', label: 'Input', icon: <Type size={11} /> },
    { type: 'double_tap', label: 'D.Tap', icon: <Zap size={11} /> },
    { type: 'long_press', label: 'Long', icon: <Hand size={11} /> },
    { type: 'swipe', label: 'Swipe', icon: <MoveHorizontal size={11} /> },
    { type: 'scroll', label: 'Scroll', icon: <ChevronsDown size={11} /> },
    { type: 'hide_keyboard', label: 'KB', icon: <Keyboard size={11} /> },
  ]

  return (
    <>
      <div
        style={{
          display: 'flex',
          gap: 4,
          flexWrap: 'wrap',
          justifyContent: 'center',
        }}
      >
        {actions.map((a) => (
          <button
            key={a.type}
            onClick={() => {
              if (a.type === 'scroll' || a.type === 'hide_keyboard') {
                onManualAdd(a.type, '', undefined, undefined)
              } else {
                openDialog(a.type)
              }
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 3,
              padding: '4px 7px',
              backgroundColor: 'rgba(255,255,255,0.06)',
              border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 5,
              color: '#d4d4d4',
              fontSize: 10,
              cursor: 'pointer',
            }}
          >
            {a.icon}
            {a.label}
          </button>
        ))}
      </div>

      <AnimatePresence>
        {dialog && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            style={{
              position: 'fixed',
              inset: 0,
              backgroundColor: 'rgba(0,0,0,0.6)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 200,
            }}
            onClick={() => setDialog(null)}
          >
            <motion.div
              initial={{ scale: 0.92 }}
              animate={{ scale: 1 }}
              exit={{ scale: 0.92 }}
              onClick={(e) => e.stopPropagation()}
              style={{
                backgroundColor: '#1e2027',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 10,
                padding: 20,
                width: 300,
                display: 'flex',
                flexDirection: 'column',
                gap: 12,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                }}
              >
                <span style={{ color: '#fff', fontWeight: 600, fontSize: 13 }}>
                  Acción: {stepTypeLabel(dialog.type)}
                </span>
                <button
                  onClick={() => setDialog(null)}
                  style={{
                    background: 'none',
                    border: 'none',
                    cursor: 'pointer',
                    color: '#888',
                  }}
                >
                  <X size={14} />
                </button>
              </div>

              {dialog.type !== 'scroll' &&
                dialog.type !== 'hide_keyboard' &&
                dialog.type !== 'swipe' && (
                  <div>
                    <label
                      style={{
                        fontSize: 11,
                        color: '#888',
                        display: 'block',
                        marginBottom: 4,
                      }}
                    >
                      ID del elemento
                    </label>
                    <input
                      value={dialog.elementId}
                      onChange={(e) => setDialog({ ...dialog, elementId: e.target.value })}
                      placeholder="btn_iniciar_sesion"
                      style={{
                        width: '100%',
                        backgroundColor: '#141519',
                        border: '1px solid rgba(255,255,255,0.12)',
                        borderRadius: 6,
                        color: '#d4d4d4',
                        padding: '6px 10px',
                        fontSize: 12,
                        fontFamily: 'monospace',
                        boxSizing: 'border-box',
                      }}
                    />
                  </div>
                )}

              {dialog.type === 'swipe' && (
                <div>
                  <label
                    style={{ fontSize: 11, color: '#888', display: 'block', marginBottom: 4 }}
                  >
                    Dirección
                  </label>
                  <div style={{ display: 'flex', gap: 6 }}>
                    {(['up', 'down', 'left', 'right'] as const).map((d) => (
                      <button
                        key={d}
                        onClick={() => setDialog({ ...dialog, dir: d })}
                        style={{
                          flex: 1,
                          padding: '5px 0',
                          borderRadius: 5,
                          fontSize: 10,
                          cursor: 'pointer',
                          backgroundColor: dialog.dir === d ? '#6366f1' : 'rgba(255,255,255,0.06)',
                          border: `1px solid ${
                            dialog.dir === d ? '#818cf8' : 'rgba(255,255,255,0.1)'
                          }`,
                          color: '#fff',
                        }}
                      >
                        {d}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {dialog.type === 'input' && (
                <div>
                  <label
                    style={{ fontSize: 11, color: '#888', display: 'block', marginBottom: 4 }}
                  >
                    Valor a escribir
                  </label>
                  <input
                    value={dialog.inputVal}
                    onChange={(e) => setDialog({ ...dialog, inputVal: e.target.value })}
                    placeholder="usuario@email.com"
                    style={{
                      width: '100%',
                      backgroundColor: '#141519',
                      border: '1px solid rgba(255,255,255,0.12)',
                      borderRadius: 6,
                      color: '#d4d4d4',
                      padding: '6px 10px',
                      fontSize: 12,
                      boxSizing: 'border-box',
                    }}
                  />
                </div>
              )}

              <button
                onClick={confirm}
                style={{
                  backgroundColor: '#6366f1',
                  border: 'none',
                  borderRadius: 6,
                  color: '#fff',
                  padding: '8px 0',
                  fontSize: 12,
                  fontWeight: 600,
                  cursor: 'pointer',
                  marginTop: 4,
                }}
              >
                Agregar paso
              </button>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  )
})

// ─── Step Row ─────────────────────────────────────────────────────────────────

interface StepRowProps {
  step: RecStep
  onDelete: (id: string) => void
}

function StepRow({ step, onDelete }: StepRowProps) {
  const [hovered, setHovered] = useState(false)
  const color = STEP_COLORS[step.type]

  return (
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ duration: 0.15 }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        display: 'flex',
        alignItems: 'center',
        padding: '7px 14px',
        gap: 8,
        backgroundColor: hovered ? 'rgba(255,255,255,0.04)' : 'transparent',
        transition: 'background-color 0.1s',
        cursor: 'default',
      }}
    >
      {/* Step number */}
      <span
        style={{
          color: 'rgba(255,255,255,0.3)',
          fontSize: 10,
          width: 18,
          textAlign: 'right',
          flexShrink: 0,
        }}
      >
        {step.n}
      </span>

      {/* Type dot */}
      <div
        style={{
          width: 8,
          height: 8,
          borderRadius: '50%',
          backgroundColor: color,
          flexShrink: 0,
        }}
      />

      {/* Main info */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
          <span
            style={{
              color: '#e0e0e0',
              fontWeight: 600,
              fontSize: 11,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              maxWidth: 90,
            }}
          >
            {step.el?.text ?? stepTypeLabel(step.type)}
          </span>
        </div>
        {step.el && (
          <span
            style={{
              color: 'rgba(255,255,255,0.3)',
              fontSize: 9,
              fontFamily: 'monospace',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              display: 'block',
              maxWidth: 120,
            }}
          >
            {step.el.shortId}
          </span>
        )}
        {step.inputVal && (
          <span
            style={{
              color: '#34d399',
              fontSize: 9,
              fontStyle: 'italic',
              display: 'block',
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              maxWidth: 120,
            }}
          >
            "{step.inputVal}"
          </span>
        )}
      </div>

      {/* Timestamp */}
      <span
        style={{
          color: 'rgba(255,255,255,0.25)',
          fontSize: 9,
          fontFamily: 'monospace',
          flexShrink: 0,
        }}
      >
        {step.timeStr}
      </span>

      {/* Delete */}
      <button
        onClick={() => onDelete(step.id)}
        style={{
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          color: hovered ? '#ef4444' : 'transparent',
          padding: 0,
          display: 'flex',
          alignItems: 'center',
          transition: 'color 0.1s',
          flexShrink: 0,
        }}
      >
        <Trash2 size={11} />
      </button>
    </motion.div>
  )
}

// ─── Steps Panel ──────────────────────────────────────────────────────────────

interface StepsPanelProps {
  steps: RecStep[]
  recording: boolean
  onDeleteStep: (id: string) => void
  onManualAdd: (
    type: StepType,
    elementId: string,
    inputVal?: string,
    dir?: 'up' | 'down' | 'left' | 'right',
  ) => void
}

const StepsPanel = React.memo(function StepsPanel({
  steps,
  recording,
  onDeleteStep,
  onManualAdd,
}: StepsPanelProps) {
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [steps.length])

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: 'rgba(255,255,255,0.03)',
        borderLeft: '1px solid rgba(255,255,255,0.06)',
        borderRight: '1px solid rgba(255,255,255,0.06)',
      }}
    >
      {/* Panel header */}
      <div
        style={{
          padding: '12px 16px',
          borderBottom: '1px solid rgba(255,255,255,0.06)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexShrink: 0,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Layers3 size={14} color="#818cf8" />
          <span style={{ color: '#e0e0e0', fontWeight: 600, fontSize: 13 }}>Pasos Grabados</span>
        </div>
        <span
          style={{
            backgroundColor: '#6366f1',
            color: '#fff',
            fontSize: 10,
            fontWeight: 700,
            padding: '2px 7px',
            borderRadius: 20,
            minWidth: 20,
            textAlign: 'center',
          }}
        >
          {steps.length}
        </span>
      </div>

      {/* Steps list */}
      <div
        ref={scrollRef}
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: '8px 0',
        }}
      >
        {steps.length === 0 ? (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              padding: 24,
              gap: 10,
            }}
          >
            <Circle size={32} color="rgba(255,255,255,0.1)" />
            <p
              style={{
                color: 'rgba(255,255,255,0.3)',
                fontSize: 12,
                textAlign: 'center',
                margin: 0,
              }}
            >
              Inicia la grabación para capturar pasos
            </p>
          </div>
        ) : (
          <AnimatePresence initial={false}>
            {steps.map((step) => (
              <StepRow key={step.id} step={step} onDelete={onDeleteStep} />
            ))}
          </AnimatePresence>
        )}
      </div>

      {/* Bottom actions when recording */}
      {recording && (
        <div
          style={{
            padding: '8px 10px',
            borderTop: '1px solid rgba(255,255,255,0.06)',
            flexShrink: 0,
          }}
        >
          <p
            style={{
              color: '#555',
              fontSize: 9,
              fontWeight: 600,
              margin: '0 0 5px',
              letterSpacing: 0.5,
            }}
          >
            AGREGAR ACCIÓN
          </p>
          <ManualActionBar onManualAdd={onManualAdd} />
        </div>
      )}
    </div>
  )
})

// ─── Code Panel ───────────────────────────────────────────────────────────────

interface CodePanelProps {
  steps: RecStep[]
  lang: Lang
  viewTab: ViewTab
  opts: GenOpts
  testName: string
  className: string
  generatedCode: string
  generatedXML: string
  onLangChange: (l: Lang) => void
  onViewTabChange: (t: ViewTab) => void
  onOptsChange: (o: GenOpts) => void
  onTestNameChange: (s: string) => void
  onClassNameChange: (s: string) => void
  onCopy: () => void
  onDownload: () => void
  onSave: () => void
  copied: boolean
}

const CodePanel = React.memo(function CodePanel({
  steps,
  lang,
  viewTab,
  opts,
  testName,
  className,
  generatedCode,
  generatedXML,
  onLangChange,
  onViewTabChange,
  onOptsChange,
  onTestNameChange,
  onClassNameChange,
  onCopy,
  onDownload,
  onSave,
  copied,
}: CodePanelProps) {
  const code = viewTab === 'code' ? generatedCode : generatedXML
  const lines = code.split('\n')

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        backgroundColor: 'rgba(255,255,255,0.02)',
      }}
    >
      {/* Tabs + controls */}
      <div
        style={{
          padding: '10px 14px',
          borderBottom: '1px solid rgba(255,255,255,0.06)',
          display: 'flex',
          flexDirection: 'column',
          gap: 8,
          flexShrink: 0,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          {/* View tabs */}
          <div style={{ display: 'flex', gap: 2 }}>
            {(['code', 'xml'] as ViewTab[]).map((t) => (
              <button
                key={t}
                onClick={() => onViewTabChange(t)}
                style={{
                  padding: '4px 10px',
                  borderRadius: 5,
                  fontSize: 11,
                  fontWeight: 500,
                  cursor: 'pointer',
                  border: 'none',
                  backgroundColor: viewTab === t ? '#6366f1' : 'transparent',
                  color: viewTab === t ? '#fff' : '#888',
                  transition: 'all 0.15s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                {t === 'code' ? (
                  <>
                    <Code2 size={10} />
                    Código Generado
                  </>
                ) : (
                  <>
                    <FileCode2 size={10} />
                    Vista XML
                  </>
                )}
              </button>
            ))}
          </div>

          {/* Action buttons */}
          <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
            {viewTab === 'code' && (
              <select
                value={lang}
                onChange={(e) => onLangChange(e.target.value as Lang)}
                style={{
                  backgroundColor: 'rgba(255,255,255,0.06)',
                  border: '1px solid rgba(255,255,255,0.1)',
                  borderRadius: 5,
                  color: '#d4d4d4',
                  fontSize: 10,
                  padding: '3px 6px',
                  cursor: 'pointer',
                }}
              >
                <option value="java-testng">Java + TestNG</option>
                <option value="java-junit">Java + JUnit</option>
              </select>
            )}

            <button
              onClick={onCopy}
              title="Copiar código"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 3,
                padding: '4px 8px',
                backgroundColor: copied ? 'rgba(52,211,153,0.15)' : 'rgba(255,255,255,0.06)',
                border: `1px solid ${copied ? '#34d399' : 'rgba(255,255,255,0.1)'}`,
                borderRadius: 5,
                color: copied ? '#34d399' : '#d4d4d4',
                fontSize: 10,
                cursor: 'pointer',
                transition: 'all 0.15s',
              }}
            >
              {copied ? <Check size={10} /> : <Copy size={10} />}
              {copied ? 'Copiado' : 'Copiar'}
            </button>

            <button
              onClick={onDownload}
              title="Descargar archivo"
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 3,
                padding: '4px 8px',
                backgroundColor: 'rgba(255,255,255,0.06)',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 5,
                color: '#d4d4d4',
                fontSize: 10,
                cursor: 'pointer',
              }}
            >
              <Download size={10} />
              Descargar
            </button>
          </div>
        </div>
      </div>

      {/* Code area */}
      <div style={{ flex: 1, overflowY: 'auto', position: 'relative', minHeight: 0 }}>
        {steps.length === 0 ? (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              height: '100%',
              gap: 10,
              padding: 24,
            }}
          >
            <FileCode2 size={32} color="rgba(255,255,255,0.1)" />
            <p
              style={{
                color: 'rgba(255,255,255,0.25)',
                fontSize: 12,
                textAlign: 'center',
                margin: 0,
              }}
            >
              Graba pasos para generar código automáticamente
            </p>
          </div>
        ) : (
          <pre
            style={{
              margin: 0,
              padding: '12px 0',
              fontSize: 11,
              lineHeight: 1.6,
              fontFamily: '"Fira Code", "Consolas", monospace',
              overflowX: 'auto',
            }}
          >
            {lines.map((line, i) => (
              <div
                key={i}
                style={{ display: 'flex', padding: '0 8px' }}
              >
                <span
                  style={{
                    color: 'rgba(255,255,255,0.2)',
                    userSelect: 'none',
                    width: 28,
                    textAlign: 'right',
                    marginRight: 12,
                    flexShrink: 0,
                    fontSize: 10,
                  }}
                >
                  {i + 1}
                </span>
                <span style={{ flex: 1 }}>
                  <SyntaxLine line={line} />
                </span>
              </div>
            ))}
          </pre>
        )}
      </div>

      {/* Divider */}
      <div
        style={{ height: 1, backgroundColor: 'rgba(255,255,255,0.06)', flexShrink: 0 }}
      />

      {/* Options section */}
      <div
        style={{
          padding: '12px 14px',
          display: 'flex',
          flexDirection: 'column',
          gap: 10,
          flexShrink: 0,
          overflowY: 'auto',
          maxHeight: 220,
        }}
      >
        <div style={{ display: 'flex', gap: 12 }}>
          {/* Gen options */}
          <div style={{ flex: 1 }}>
            <p
              style={{
                color: '#888',
                fontSize: 10,
                margin: '0 0 6px',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              <Settings2 size={10} />
              Opciones de Generación
            </p>
            {(
              [
                { key: 'pageObjects', label: 'Usar Page Objects' },
                { key: 'assertions', label: 'Generar Assertions' },
                { key: 'smartWaits', label: 'Agregar Esperas Inteligentes' },
                { key: 'screenshots', label: 'Incluir Toma de Screenshots' },
              ] as Array<{ key: keyof GenOpts; label: string }>
            ).map(({ key, label }) => (
              <label
                key={key}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6,
                  color: '#c0c0c0',
                  fontSize: 10,
                  cursor: 'pointer',
                  marginBottom: 4,
                }}
              >
                <input
                  type="checkbox"
                  checked={opts[key]}
                  onChange={(e) => onOptsChange({ ...opts, [key]: e.target.checked })}
                  style={{ accentColor: '#6366f1', cursor: 'pointer' }}
                />
                {label}
              </label>
            ))}
          </div>

          {/* Names */}
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6 }}>
            <p
              style={{
                color: '#888',
                fontSize: 10,
                margin: '0 0 2px',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: 4,
              }}
            >
              <Type size={10} />
              Nombre del Test
            </p>
            <input
              value={testName}
              onChange={(e) => onTestNameChange(e.target.value)}
              placeholder="myTest"
              style={{
                backgroundColor: '#141519',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 5,
                color: '#d4d4d4',
                padding: '5px 8px',
                fontSize: 11,
                fontFamily: 'monospace',
              }}
            />
            <input
              value={className}
              onChange={(e) => onClassNameChange(e.target.value)}
              placeholder="GeneratedTest"
              style={{
                backgroundColor: '#141519',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 5,
                color: '#d4d4d4',
                padding: '5px 8px',
                fontSize: 11,
                fontFamily: 'monospace',
              }}
            />
          </div>
        </div>

        {/* Save button */}
        <button
          onClick={onSave}
          style={{
            width: '100%',
            padding: '8px 0',
            background: 'linear-gradient(90deg, #6366f1, #818cf8)',
            border: 'none',
            borderRadius: 7,
            color: '#fff',
            fontSize: 12,
            fontWeight: 600,
            cursor: 'pointer',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 6,
          }}
        >
          <Layers3 size={12} />
          Guardar como Suite
          <ChevronDown size={12} />
        </button>
      </div>
    </div>
  )
})

// ─── Session Info Bar ─────────────────────────────────────────────────────────

interface SessionInfoBarProps {
  sessionStart: Date | null
  device: PhysicalDevice | null
  appConfig: DeviceAppConfig | null
  appMode: string
  elapsed: number
  stepCount: number
  expanded: boolean
  onToggle: () => void
}

function formatElapsed(secs: number): string {
  const h = Math.floor(secs / 3600)
  const m = Math.floor((secs % 3600) / 60)
  const s = secs % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

const SessionInfoBar = React.memo(function SessionInfoBar({
  sessionStart,
  device,
  appConfig,
  appMode,
  elapsed,
  stepCount,
  expanded,
  onToggle,
}: SessionInfoBarProps) {
  const items = [
    {
      label: 'Inicio',
      value: sessionStart
        ? sessionStart.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
        : '—',
    },
    {
      label: 'Dispositivo',
      value: device ? `${device.deviceName} (${device.udid.slice(0, 8)}...)` : '—',
    },
    {
      label: 'Aplicación',
      value: appConfig
        ? `${appConfig.appName} (${appConfig.appPackage || appConfig.bundleId})`
        : '—',
    },
    { label: 'Modo', value: appMode || '—' },
    { label: 'Duración', value: formatElapsed(elapsed) },
    { label: 'Pasos', value: String(stepCount) },
  ]

  return (
    <div
      style={{
        borderTop: '1px solid rgba(255,255,255,0.06)',
        backgroundColor: '#0d1117',
        flexShrink: 0,
      }}
    >
      <button
        onClick={onToggle}
        style={{
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '5px 16px',
          background: 'none',
          border: 'none',
          cursor: 'pointer',
          color: '#666',
        }}
      >
        <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: 0.5 }}>SESIÓN INFO</span>
        {expanded ? <ChevronDown size={12} /> : <ChevronUp size={12} />}
      </button>

      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            style={{ overflow: 'hidden' }}
          >
            <div
              style={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: '4px 24px',
                padding: '6px 16px 10px',
              }}
            >
              {items.map(({ label, value }) => (
                <span key={label} style={{ fontSize: 10, color: '#888' }}>
                  <span style={{ color: '#555', marginRight: 3 }}>{label}:</span>
                  <span style={{ color: '#aaa' }}>{value}</span>
                </span>
              ))}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
})

// ─── Save Modal ───────────────────────────────────────────────────────────────

interface SaveModalProps {
  onClose: () => void
  onConfirm: (name: string, mode: string) => void
}

type SaveType = 'Suite' | 'Caso de Prueba' | 'Plantilla'

function SaveModal({ onClose, onConfirm }: SaveModalProps) {
  const [name, setName] = useState('')
  const [type, setType] = useState<SaveType>('Suite')
  const [saved, setSaved] = useState(false)

  const handleSave = () => {
    if (!name.trim()) return
    onConfirm(name.trim(), type)
    setSaved(true)
    setTimeout(onClose, 1200)
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0,0,0,0.65)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 300,
      }}
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.92, y: 10 }}
        animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.92, y: 10 }}
        onClick={(e) => e.stopPropagation()}
        style={{
          backgroundColor: '#1a1d24',
          border: '1px solid rgba(255,255,255,0.1)',
          borderRadius: 12,
          padding: 24,
          width: 360,
          display: 'flex',
          flexDirection: 'column',
          gap: 14,
        }}
      >
        {saved ? (
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 10,
              padding: '10px 0',
            }}
          >
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 300 }}
            >
              <Check size={36} color="#34d399" />
            </motion.div>
            <p style={{ color: '#34d399', fontWeight: 600, fontSize: 14, margin: 0 }}>
              ¡Guardado exitosamente!
            </p>
          </div>
        ) : (
          <>
            <div
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}
            >
              <span style={{ color: '#fff', fontWeight: 700, fontSize: 14 }}>
                Guardar grabación
              </span>
              <button
                onClick={onClose}
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#666' }}
              >
                <X size={16} />
              </button>
            </div>

            <div>
              <label style={{ display: 'block', color: '#888', fontSize: 11, marginBottom: 5 }}>
                Nombre
              </label>
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Mi test de Cinépolis"
                autoFocus
                style={{
                  width: '100%',
                  backgroundColor: '#141519',
                  border: '1px solid rgba(255,255,255,0.12)',
                  borderRadius: 6,
                  color: '#d4d4d4',
                  padding: '7px 10px',
                  fontSize: 12,
                  boxSizing: 'border-box',
                }}
              />
            </div>

            <div>
              <label style={{ display: 'block', color: '#888', fontSize: 11, marginBottom: 7 }}>
                Tipo
              </label>
              <div style={{ display: 'flex', gap: 8 }}>
                {(['Suite', 'Caso de Prueba', 'Plantilla'] as SaveType[]).map((t) => (
                  <label
                    key={t}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 4,
                      color: '#c0c0c0',
                      fontSize: 11,
                      cursor: 'pointer',
                    }}
                  >
                    <input
                      type="radio"
                      checked={type === t}
                      onChange={() => setType(t)}
                      style={{ accentColor: '#6366f1' }}
                    />
                    {t}
                  </label>
                ))}
              </div>
            </div>

            <button
              onClick={handleSave}
              disabled={!name.trim()}
              style={{
                width: '100%',
                padding: '9px 0',
                background: name.trim()
                  ? 'linear-gradient(90deg, #6366f1, #818cf8)'
                  : 'rgba(255,255,255,0.06)',
                border: 'none',
                borderRadius: 7,
                color: name.trim() ? '#fff' : '#555',
                fontSize: 13,
                fontWeight: 600,
                cursor: name.trim() ? 'pointer' : 'not-allowed',
                marginTop: 4,
              }}
            >
              Guardar
            </button>
          </>
        )}
      </motion.div>
    </motion.div>
  )
}

// ─── Header Step Pill ─────────────────────────────────────────────────────────

interface HeaderStepProps {
  n: number
  label: string
  value: string | null
  active: boolean
  options: string[]
  onSelect: (val: string) => void
  placeholder?: string
}

function HeaderStepPill({ n, label, value, active, options, onSelect }: HeaderStepProps) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        onClick={() => options.length > 0 && setOpen((p) => !p)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 7,
          padding: '6px 10px',
          borderRadius: 7,
          backgroundColor: active ? 'rgba(99,102,241,0.15)' : 'rgba(255,255,255,0.04)',
          border: `1px solid ${active ? 'rgba(99,102,241,0.4)' : 'rgba(255,255,255,0.08)'}`,
          cursor: options.length > 0 ? 'pointer' : 'default',
          color: '#d4d4d4',
          transition: 'all 0.15s',
        }}
      >
        {/* Number */}
        <div
          style={{
            width: 20,
            height: 20,
            borderRadius: '50%',
            backgroundColor: active ? '#6366f1' : 'rgba(255,255,255,0.08)',
            border: active ? 'none' : '1px solid rgba(255,255,255,0.15)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: active ? '#fff' : '#666',
            fontSize: 10,
            fontWeight: 700,
            flexShrink: 0,
          }}
        >
          {n}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
          <span style={{ fontSize: 9, color: '#666', lineHeight: 1 }}>{label}</span>
          <span style={{ fontSize: 11, color: value ? '#e0e0e0' : '#555', lineHeight: 1.3 }}>
            {value ?? 'Seleccionar'}
          </span>
        </div>

        {options.length > 0 && <ChevronDown size={11} color="#666" />}
      </button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: 4 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 4 }}
            transition={{ duration: 0.12 }}
            style={{
              position: 'absolute',
              top: '100%',
              left: 0,
              marginTop: 4,
              backgroundColor: '#1e2027',
              border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 7,
              minWidth: 180,
              zIndex: 100,
              boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
              overflow: 'hidden',
            }}
          >
            {options.map((opt) => (
              <button
                key={opt}
                onClick={() => {
                  onSelect(opt)
                  setOpen(false)
                }}
                style={{
                  display: 'block',
                  width: '100%',
                  textAlign: 'left',
                  padding: '7px 12px',
                  fontSize: 11,
                  color: '#d4d4d4',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  borderBottom: '1px solid rgba(255,255,255,0.04)',
                }}
                onMouseEnter={(e) => {
                  ;(e.currentTarget as HTMLButtonElement).style.backgroundColor =
                    'rgba(255,255,255,0.06)'
                }}
                onMouseLeave={(e) => {
                  ;(e.currentTarget as HTMLButtonElement).style.backgroundColor = 'transparent'
                }}
              >
                {opt}
              </button>
            ))}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

// ─── Main Component ───────────────────────────────────────────────────────────

let _stepCounter = 0

export default function RecordStudio() {
  // ── State ──────────────────────────────────────────────────────────────────
  const [recState, setRecState] = useState<RecState>('idle')
  const [elapsed, setElapsed] = useState(0)
  const [steps, setSteps] = useState<RecStep[]>([])
  const [screen, setScreen] = useState<AppScreen>('home')
  const [selectedDevice, setSelectedDevice] = useState<PhysicalDevice | null>(null)
  const [appConfig, setAppConfig] = useState<DeviceAppConfig | null>(null)
  const [devices, setDevices] = useState<PhysicalDevice[]>([])
  const [appConfigs, setAppConfigs] = useState<Record<string, DeviceAppConfig>>({})
  const [appMode, setAppMode] = useState('INSTALLED')
  const [lang, setLang] = useState<Lang>('java-testng')
  const [viewTab, setViewTab] = useState<ViewTab>('code')
  const [opts, setOpts] = useState<GenOpts>({
    pageObjects: true,
    assertions: false,
    smartWaits: true,
    screenshots: false,
  })
  const [testName, setTestName] = useState('testLoginFlow')
  const [className, setClassName] = useState('CinepolisTest')
  const [showSave, setShowSave] = useState(false)
  const [copied, setCopied] = useState(false)
  const [sessionStart, setSessionStart] = useState<Date | null>(null)
  const [infoExpanded, setInfoExpanded] = useState(true)

  // ── Fetch devices + configs ────────────────────────────────────────────────
  useEffect(() => {
    getDevices()
      .then((d) => setDevices(d))
      .catch(() => {})
    getAllDeviceAppConfigs()
      .then((c) => setAppConfigs(c))
      .catch(() => {})
  }, [])

  // ── Timer ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    if (recState !== 'recording') return
    const id = setInterval(() => setElapsed((s) => s + 1), 1000)
    return () => clearInterval(id)
  }, [recState])

  const elapsedStr = useMemo(() => formatElapsed(elapsed), [elapsed])

  // ── Device selection ───────────────────────────────────────────────────────
  const handleSelectDevice = useCallback(
    (name: string) => {
      const d = devices.find((dev) => dev.deviceName === name) ?? null
      setSelectedDevice(d)
      if (d) {
        setAppConfig(appConfigs[d.udid] ?? null)
        setAppMode(appConfigs[d.udid]?.appMode ?? 'INSTALLED')
      } else {
        setAppConfig(null)
      }
    },
    [devices, appConfigs],
  )

  const handleSelectApp = useCallback(
    (name: string) => {
      if (!selectedDevice) return
      const cfg = Object.values(appConfigs).find((c) => c.appName === name) ?? null
      setAppConfig(cfg)
    },
    [appConfigs, selectedDevice],
  )

  const handleSelectMode = useCallback((mode: string) => {
    setAppMode(mode)
  }, [])

  // ── Recording ──────────────────────────────────────────────────────────────
  const handleToggleRecording = useCallback(() => {
    if (recState === 'idle') {
      setRecState('recording')
      setSessionStart(new Date())
      setElapsed(0)
    } else {
      setRecState('idle')
    }
  }, [recState])

  const handleRecordEl = useCallback(
    (el: AppEl) => {
      if (recState !== 'recording') return
      _stepCounter++
      const newStep: RecStep = {
        id: `step_${Date.now()}_${Math.random().toString(36).slice(2)}`,
        n: _stepCounter,
        type: 'tap',
        el,
        timeStr: formatElapsed(elapsed),
      }
      setSteps((prev) => [...prev, newStep])
    },
    [recState, elapsed],
  )

  const handleManualAdd = useCallback(
    (
      type: StepType,
      elementId: string,
      inputVal?: string,
      dir?: 'up' | 'down' | 'left' | 'right',
    ) => {
      if (recState !== 'recording') return
      _stepCounter++

      let el: AppEl | null = null
      if (elementId.trim()) {
        el = {
          shortId: elementId.trim(),
          resourceId: `${ANDROID_PKG}:id/${elementId.trim()}`,
          accessId: elementId.trim(),
          text: elementId.trim(),
          elType: 'btn',
        }
      }

      const newStep: RecStep = {
        id: `step_${Date.now()}_${Math.random().toString(36).slice(2)}`,
        n: _stepCounter,
        type,
        el,
        inputVal: inputVal && inputVal.trim() ? inputVal.trim() : undefined,
        dir,
        timeStr: formatElapsed(elapsed),
      }
      setSteps((prev) => [...prev, newStep])
    },
    [recState, elapsed],
  )

  const handleDeleteStep = useCallback((id: string) => {
    setSteps((prev) => prev.filter((s) => s.id !== id))
  }, [])

  // ── Code generation ────────────────────────────────────────────────────────
  const generatedCode = useMemo(
    () =>
      generateJava(
        steps,
        opts,
        selectedDevice?.platform ?? 'ANDROID',
        testName,
        className,
        lang,
      ),
    [steps, opts, selectedDevice, testName, className, lang],
  )

  const generatedXML = useMemo(
    () => generateXML(steps, selectedDevice?.platform ?? 'ANDROID'),
    [steps, selectedDevice],
  )

  // ── Copy / Download ────────────────────────────────────────────────────────
  const handleCopy = useCallback(async () => {
    const code = viewTab === 'code' ? generatedCode : generatedXML
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* ignore */
    }
  }, [viewTab, generatedCode, generatedXML])

  const handleDownload = useCallback(() => {
    const code = viewTab === 'code' ? generatedCode : generatedXML
    const blob = new Blob([code], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = viewTab === 'code' ? `${className || 'GeneratedTest'}.java` : 'recording.xml'
    a.click()
    URL.revokeObjectURL(url)
  }, [viewTab, generatedCode, generatedXML, className])

  // ── Save ───────────────────────────────────────────────────────────────────
  const handleSave = useCallback(
    (name: string, mode: string) => {
      const sessions = JSON.parse(
        localStorage.getItem('qa_record_sessions') ?? '[]',
      ) as unknown[]
      sessions.push({
        id: `session_${Date.now()}`,
        name,
        mode,
        savedAt: new Date().toISOString(),
        stepCount: steps.length,
        code: generatedCode,
        xml: generatedXML,
      })
      localStorage.setItem('qa_record_sessions', JSON.stringify(sessions))
    },
    [steps.length, generatedCode, generatedXML],
  )

  // ── Dropdown data ──────────────────────────────────────────────────────────
  const deviceNames = useMemo(() => devices.map((d) => d.deviceName), [devices])
  const appNames = useMemo(
    () => [...new Set(Object.values(appConfigs).map((c) => c.appName))],
    [appConfigs],
  )
  const modes = ['INSTALLED', 'APK', 'IPA']

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        flexDirection: 'column',
        backgroundColor: '#0d1117',
        color: '#d4d4d4',
        fontFamily: 'system-ui, -apple-system, sans-serif',
      }}
    >
      {/* ── Header ── */}
      <header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 20px',
          borderBottom: '1px solid rgba(255,255,255,0.07)',
          flexShrink: 0,
          gap: 12,
          backgroundColor: '#0d1117',
          zIndex: 10,
        }}
      >
        {/* Steps pills */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
          <Video size={16} color="#6366f1" style={{ flexShrink: 0 }} />
          <span
            style={{ color: '#e0e0e0', fontWeight: 700, fontSize: 14, marginRight: 12 }}
          >
            Record Studio
          </span>

          <HeaderStepPill
            n={1}
            label="Seleccionar Dispositivo"
            value={selectedDevice?.deviceName ?? null}
            active={!!selectedDevice}
            options={deviceNames}
            onSelect={handleSelectDevice}
          />

          <ChevronRight size={12} color="rgba(255,255,255,0.2)" />

          <HeaderStepPill
            n={2}
            label="Seleccionar Aplicación"
            value={appConfig?.appName ?? null}
            active={!!appConfig}
            options={appNames}
            onSelect={handleSelectApp}
          />

          <ChevronRight size={12} color="rgba(255,255,255,0.2)" />

          <HeaderStepPill
            n={3}
            label="Modo de Ejecución"
            value={appMode}
            active={true}
            options={modes}
            onSelect={handleSelectMode}
          />
        </div>

        {/* Recording controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0 }}>
          {recState === 'recording' && (
            <>
              <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                <motion.div
                  animate={{ opacity: [1, 0.3, 1] }}
                  transition={{ repeat: Infinity, duration: 1 }}
                  style={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    backgroundColor: '#ef4444',
                  }}
                />
                <span style={{ color: '#ef4444', fontWeight: 700, fontSize: 12 }}>GRABANDO</span>
              </div>
              <span
                style={{
                  color: '#888',
                  fontFamily: 'monospace',
                  fontSize: 13,
                  minWidth: 55,
                }}
              >
                {elapsedStr}
              </span>
            </>
          )}

          <button
            onClick={handleToggleRecording}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              padding: '7px 14px',
              borderRadius: 7,
              fontSize: 12,
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.15s',
              ...(recState === 'idle'
                ? {
                    backgroundColor: 'rgba(34,197,94,0.15)',
                    border: '1px solid rgba(34,197,94,0.4)',
                    color: '#4ade80',
                  }
                : {
                    backgroundColor: 'rgba(239,68,68,0.1)',
                    border: '1px solid rgba(239,68,68,0.4)',
                    color: '#ef4444',
                  }),
            }}
          >
            {recState === 'idle' ? (
              <>
                <Circle size={11} />
                Iniciar Grabación
              </>
            ) : (
              <>
                <Square size={11} />
                Detener Grabación
              </>
            )}
          </button>
        </div>
      </header>

      {/* ── Main body ── */}
      <div
        style={{
          flex: 1,
          display: 'grid',
          gridTemplateColumns: '280px minmax(280px,1fr) 420px',
          minHeight: 0,
          overflow: 'hidden',
        }}
      >
        {/* ── Left column: Phone simulation ── */}
        <div
          style={{
            borderRight: '1px solid rgba(255,255,255,0.07)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            padding: '20px 14px',
            gap: 16,
            overflowY: 'auto',
          }}
        >
          {/* Panel label */}
          <div
            style={{
              width: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <Smartphone size={13} color="#6366f1" />
              <span style={{ color: '#888', fontSize: 11, fontWeight: 600 }}>
                Simulador de App
              </span>
            </div>
            <div style={{ display: 'flex', gap: 4 }}>
              <button
                onClick={() => setScreen('home')}
                title="Ir a Home"
                style={{
                  background: 'none',
                  border: '1px solid rgba(255,255,255,0.08)',
                  borderRadius: 4,
                  padding: '2px 4px',
                  cursor: 'pointer',
                  color: '#666',
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                <RotateCcw size={10} />
              </button>
              <button
                title="Expandir"
                style={{
                  background: 'none',
                  border: '1px solid rgba(255,255,255,0.08)',
                  borderRadius: 4,
                  padding: '2px 4px',
                  cursor: 'pointer',
                  color: '#666',
                  display: 'flex',
                  alignItems: 'center',
                }}
              >
                <Maximize2 size={10} />
              </button>
            </div>
          </div>

          {/* Phone */}
          <PhoneFrame
            recording={recState === 'recording'}
            screen={screen}
            onRecord={handleRecordEl}
            onScreenChange={setScreen}
          />

          {/* Status info */}
          <div
            style={{
              width: '100%',
              backgroundColor: 'rgba(255,255,255,0.03)',
              border: '1px solid rgba(255,255,255,0.06)',
              borderRadius: 7,
              padding: '8px 10px',
              fontSize: 10,
              color: '#666',
              display: 'flex',
              flexDirection: 'column',
              gap: 3,
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span>Pantalla actual</span>
              <span style={{ color: '#818cf8', fontWeight: 600 }}>
                {screen === 'home' ? 'Home' : 'Login'}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span>Paquete</span>
              <span style={{ color: '#d4d4d4', fontFamily: 'monospace', fontSize: 9 }}>
                {appConfig?.appPackage || ANDROID_PKG}
              </span>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span>Plataforma</span>
              <span style={{ color: '#34d399' }}>{selectedDevice?.platform ?? 'ANDROID'}</span>
            </div>
          </div>

          {/* Manual action bar */}
          {recState === 'recording' && (
            <div
              style={{
                width: '100%',
                backgroundColor: 'rgba(255,255,255,0.03)',
                border: '1px solid rgba(255,255,255,0.06)',
                borderRadius: 7,
                padding: '8px 6px',
              }}
            >
              <p
                style={{
                  color: '#555',
                  fontSize: 9,
                  fontWeight: 600,
                  margin: '0 0 6px 4px',
                  letterSpacing: 0.5,
                }}
              >
                AGREGAR ACCIÓN MANUAL
              </p>
              <ManualActionBar onManualAdd={handleManualAdd} />
            </div>
          )}
        </div>

        {/* ── Middle column: Steps ── */}
        <div
          style={{ minHeight: 0, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
        >
          <StepsPanel
            steps={steps}
            recording={recState === 'recording'}
            onDeleteStep={handleDeleteStep}
            onManualAdd={handleManualAdd}
          />
        </div>

        {/* ── Right column: Code ── */}
        <div
          style={{
            borderLeft: '1px solid rgba(255,255,255,0.07)',
            minHeight: 0,
            overflow: 'hidden',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <CodePanel
            steps={steps}
            lang={lang}
            viewTab={viewTab}
            opts={opts}
            testName={testName}
            className={className}
            generatedCode={generatedCode}
            generatedXML={generatedXML}
            onLangChange={setLang}
            onViewTabChange={setViewTab}
            onOptsChange={setOpts}
            onTestNameChange={setTestName}
            onClassNameChange={setClassName}
            onCopy={handleCopy}
            onDownload={handleDownload}
            onSave={() => setShowSave(true)}
            copied={copied}
          />
        </div>
      </div>

      {/* ── Session info bar ── */}
      <SessionInfoBar
        sessionStart={sessionStart}
        device={selectedDevice}
        appConfig={appConfig}
        appMode={appMode}
        elapsed={elapsed}
        stepCount={steps.length}
        expanded={infoExpanded}
        onToggle={() => setInfoExpanded((p) => !p)}
      />

      {/* ── Save Modal ── */}
      <AnimatePresence>
        {showSave && (
          <SaveModal
            onClose={() => setShowSave(false)}
            onConfirm={(name, mode) => {
              handleSave(name, mode)
            }}
          />
        )}
      </AnimatePresence>
    </div>
  )
}
