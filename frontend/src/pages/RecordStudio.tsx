import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { useMirrorStream } from '../hooks/useMirrorStream'
import { useRecordingSession } from '../hooks/useRecordingSession'
import type { RecordingAction } from '../services/recordingService'
import type { StreamState } from '../services/deviceStream'
import { motion, AnimatePresence } from 'framer-motion'
import {
  Video, Square, ChevronDown, ChevronRight, ChevronUp,
  Camera, Settings2, Maximize2, RotateCcw, RotateCw, Smartphone,
  Copy, Download, Check, X, MousePointer2, Type,
  MoveHorizontal, ChevronsDown, Keyboard, Clock, Code2,
  FileCode2, Layers3, Plus, Trash2, Play, Circle,
  Hand, Zap, Search, Wifi, Eye, AlertCircle, Link2,
  Pencil, CheckCircle, Package, PlayCircle,
} from 'lucide-react'
import { getDevices, getAllDeviceAppConfigs } from '../api'
import type { PhysicalDevice, DeviceAppConfig } from '../types'
import { RecordStudioHeader } from '../components/record-studio/RecordStudioHeader'

// ─── Local Types ──────────────────────────────────────────────────────────────

type RecState = 'idle' | 'recording'
type StepType = 'tap' | 'double_tap' | 'long_press' | 'input' | 'swipe' | 'scroll' | 'hide_keyboard' | 'assertion' | 'screenshot'
type StepFilter = 'all' | StepType
type AppScreen = 'home' | 'login'
type Lang = 'java-testng' | 'java-junit' | 'python' | 'javascript' | 'csharp' | 'kotlin'
type ViewTab = 'code' | 'xml' | 'inspector' | 'locators'

interface AppEl {
  shortId:    string
  resourceId: string
  accessId:   string
  text:       string
  elType:     'btn' | 'input' | 'text' | 'list' | 'image'
  bounds?:    string
  className?: string
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
  pageObjects:     boolean
  assertions:      boolean
  smartWaits:      boolean
  screenshots:     boolean
  allureLogs:      boolean
  reusableMethods: boolean
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
    className: 'android.widget.Button',
    bounds: '[140,8][252,30]',
  },
  iniciarSesion: {
    shortId: 'btn_iniciar_sesion',
    resourceId: `${ANDROID_PKG}:id/btn_iniciar_sesion`,
    accessId: 'Iniciar Sesión',
    text: 'Iniciar Sesión',
    elType: 'btn',
    className: 'android.widget.Button',
    bounds: '[10,8][138,30]',
  },
  buscar: {
    shortId: 'txt_buscar',
    resourceId: `${ANDROID_PKG}:id/txt_buscar`,
    accessId: 'Buscar',
    text: 'Buscar película...',
    elType: 'input',
    className: 'android.widget.EditText',
    bounds: '[10,44][252,68]',
  },
  tabCartelera: {
    shortId: 'tab_cartelera',
    resourceId: `${ANDROID_PKG}:id/tab_cartelera`,
    accessId: 'En cartelera',
    text: 'En cartelera',
    elType: 'btn',
    className: 'android.widget.TextView',
    bounds: '[0,72][131,98]',
  },
  tabProximos: {
    shortId: 'tab_proximos',
    resourceId: `${ANDROID_PKG}:id/tab_proximos`,
    accessId: 'Próximos estrenos',
    text: 'Próximos estrenos',
    elType: 'btn',
    className: 'android.widget.TextView',
    bounds: '[131,72][262,98]',
  },
  pelicula_duna: {
    shortId: 'rv_pelicula_duna',
    resourceId: `${ANDROID_PKG}:id/rv_pelicula_duna`,
    accessId: 'Duna',
    text: 'Duna: Parte Dos',
    elType: 'list',
    className: 'android.widget.FrameLayout',
    bounds: '[10,104][84,192]',
  },
  pelicula_garfield: {
    shortId: 'rv_pelicula_garfield',
    resourceId: `${ANDROID_PKG}:id/rv_pelicula_garfield`,
    accessId: 'Garfield',
    text: 'Garfield',
    elType: 'list',
    className: 'android.widget.FrameLayout',
    bounds: '[90,104][164,192]',
  },
  navInicio: {
    shortId: 'btn_nav_inicio',
    resourceId: `${ANDROID_PKG}:id/btn_nav_inicio`,
    accessId: 'Inicio',
    text: 'Inicio',
    elType: 'btn',
    className: 'android.widget.LinearLayout',
    bounds: '[0,400][52,452]',
  },
  navMisCompras: {
    shortId: 'btn_nav_mis_compras',
    resourceId: `${ANDROID_PKG}:id/btn_nav_mis_compras`,
    accessId: 'Mis compras',
    text: 'Mis compras',
    elType: 'btn',
    className: 'android.widget.LinearLayout',
    bounds: '[157,400][209,452]',
  },
  navCines: {
    shortId: 'btn_nav_cines',
    resourceId: `${ANDROID_PKG}:id/btn_nav_cines`,
    accessId: 'Cines',
    text: 'Cines',
    elType: 'btn',
    className: 'android.widget.LinearLayout',
    bounds: '[52,400][105,452]',
  },
  navAlimentos: {
    shortId: 'btn_nav_alimentos',
    resourceId: `${ANDROID_PKG}:id/btn_nav_alimentos`,
    accessId: 'Alimentos',
    text: 'Alimentos',
    elType: 'btn',
    className: 'android.widget.LinearLayout',
    bounds: '[105,400][157,452]',
  },
  navMas: {
    shortId: 'btn_nav_mas',
    resourceId: `${ANDROID_PKG}:id/btn_nav_mas`,
    accessId: 'Más',
    text: 'Más',
    elType: 'btn',
    className: 'android.widget.LinearLayout',
    bounds: '[209,400][262,452]',
  },
}

const LOGIN_ELS: Record<string, AppEl> = {
  correo: {
    shortId: 'txt_correo',
    resourceId: `${ANDROID_PKG}:id/txt_correo`,
    accessId: 'Correo',
    text: 'Correo electrónico',
    elType: 'input',
    className: 'android.widget.EditText',
    bounds: '[16,100][246,135]',
  },
  password: {
    shortId: 'txt_password',
    resourceId: `${ANDROID_PKG}:id/txt_password`,
    accessId: 'Contraseña',
    text: 'Contraseña',
    elType: 'input',
    className: 'android.widget.EditText',
    bounds: '[16,147][246,182]',
  },
  entrar: {
    shortId: 'btn_entrar',
    resourceId: `${ANDROID_PKG}:id/btn_entrar`,
    accessId: 'Iniciar sesión',
    text: 'Iniciar Sesión',
    elType: 'btn',
    className: 'android.widget.Button',
    bounds: '[16,194][246,224]',
  },
}

// ─── Inspector helpers ────────────────────────────────────────────────────────

function getElById(shortId: string): AppEl | null {
  const all = { ...HOME_ELS, ...LOGIN_ELS }
  return Object.values(all).find(e => e.shortId === shortId) ?? null
}

function deriveXPath(el: AppEl): string {
  return `//*[@resource-id="${el.resourceId}"]`
}

interface XmlNode {
  tag: string
  attrs: Record<string, string>
  children?: XmlNode[]
  elId?: string
}

function buildXmlTree(screen: AppScreen): XmlNode {
  const els = screen === 'home' ? HOME_ELS : LOGIN_ELS
  const leaves: XmlNode[] = Object.values(els).map(el => ({
    tag: el.className ?? 'android.view.View',
    elId: el.shortId,
    attrs: {
      'resource-id': el.resourceId,
      'content-desc': el.accessId,
      text: el.text,
      bounds: el.bounds ?? '[0,0][0,0]',
      clickable: (el.elType === 'btn' || el.elType === 'input') ? 'true' : 'false',
      enabled: 'true',
      displayed: 'true',
    },
  }))

  const container: XmlNode = {
    tag: 'android.widget.FrameLayout',
    attrs: {
      'resource-id': `${ANDROID_PKG}:id/content`,
      bounds: '[0,0][262,452]',
      clickable: 'false',
      enabled: 'true',
    },
    children: leaves,
  }

  return {
    tag: 'hierarchy',
    attrs: { rotation: '0' },
    children: [container],
  }
}

// ─── Step type helpers ────────────────────────────────────────────────────────

const STEP_COLORS: Record<StepType, string> = {
  tap:           '#818cf8',
  double_tap:    '#a78bfa',
  long_press:    '#c084fc',
  input:         '#34d399',
  swipe:         '#f59e0b',
  scroll:        '#60a5fa',
  hide_keyboard: '#f43f5e',
  assertion:     '#14b8a6',
  screenshot:    '#eab308',
}

function stepTypeLabel(type: StepType): string {
  switch (type) {
    case 'tap':           return 'Tap'
    case 'double_tap':    return 'Double Tap'
    case 'long_press':    return 'Long Press'
    case 'input':         return 'Input Text'
    case 'swipe':         return 'Swipe'
    case 'scroll':        return 'Scroll'
    case 'hide_keyboard': return 'Hide Keyboard'
    case 'assertion':     return 'Assertion'
    case 'screenshot':    return 'Screenshot'
  }
}

function getStepIcon(type: StepType, size = 13): React.ReactNode {
  const c = STEP_COLORS[type]
  switch (type) {
    case 'tap':           return <MousePointer2 size={size} color={c} />
    case 'double_tap':    return <Zap size={size} color={c} />
    case 'long_press':    return <Hand size={size} color={c} />
    case 'input':         return <Type size={size} color={c} />
    case 'swipe':         return <MoveHorizontal size={size} color={c} />
    case 'scroll':        return <ChevronsDown size={size} color={c} />
    case 'hide_keyboard': return <Keyboard size={size} color={c} />
    case 'assertion':     return <CheckCircle size={size} color={c} />
    case 'screenshot':    return <Camera size={size} color={c} />
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
  if (opts.allureLogs) {
    lines.push('import io.qameta.allure.Allure;')
    lines.push('import io.qameta.allure.Description;')
    lines.push('import io.qameta.allure.Feature;')
    lines.push('import io.qameta.allure.Story;')
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
  if (opts.allureLogs) {
    lines.push(`    @Feature("${effectiveClassName}")`)
    lines.push(`    @Story("${effectiveTestName}")`)
    lines.push(`    @Description("Auto-generated by QAutomation Record Studio")`)
  }
  lines.push(`    @Test`)
  lines.push(`    public void ${effectiveTestName}() {`)

  for (const step of steps) {
    lines.push('')
    const label = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`        // ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)
    if (opts.allureLogs) {
      lines.push(`        Allure.step("${step.n}. ${label}${elText ? ` — ${elText}` : ''}");`)
    }

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

function getLangFileExt(lang: Lang): string {
  switch (lang) {
    case 'java-testng': case 'java-junit': return 'java'
    case 'python': return 'py'
    case 'javascript': return 'js'
    case 'csharp': return 'cs'
    case 'kotlin': return 'kt'
  }
}

function toPascalCase(s: string): string {
  return s
    .replace(/[^a-zA-Z0-9]/g, ' ')
    .split(' ')
    .filter(Boolean)
    .map(w => w.charAt(0).toUpperCase() + w.slice(1))
    .join('')
}

function toSnakeCase(s: string): string {
  return s
    .replace(/[^a-zA-Z0-9]/g, '_')
    .replace(/_+/g, '_')
    .toLowerCase()
    .replace(/^_|_$/g, '')
}

function generatePython(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
): string {
  const isAndroid = platform.toUpperCase() !== 'IOS'
  const effectiveClass = toPascalCase(className.trim() || 'GeneratedTest')
  const effectiveTest = toSnakeCase(testName.trim() || 'my_test')
  const lines: string[] = []

  lines.push('import pytest')
  lines.push('from appium import webdriver')
  lines.push('from appium.webdriver.common.appiumby import AppiumBy')
  if (opts.smartWaits) {
    lines.push('from selenium.webdriver.support.ui import WebDriverWait')
    lines.push('from selenium.webdriver.support import expected_conditions as EC')
  }
  if (opts.assertions) lines.push('import pytest')
  lines.push('')
  lines.push('')
  lines.push(`class Test${effectiveClass}:`)
  lines.push('')
  lines.push(`    def test_${effectiveTest}(self, driver):`)

  for (const step of steps) {
    lines.push('')
    const label = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`        # ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)

    const sel = step.el
      ? isAndroid
        ? `AppiumBy.ID, "${step.el.resourceId}"`
        : `AppiumBy.ACCESSIBILITY_ID, "${step.el.accessId}"`
      : null

    if (opts.smartWaits && sel) {
      lines.push(`        WebDriverWait(driver, 10).until(EC.presence_of_element_located((${sel})))`)
    }

    switch (step.type) {
      case 'tap':
        if (sel) lines.push(`        driver.find_element(${sel}).click()`)
        break
      case 'double_tap':
        if (sel) {
          lines.push(`        el = driver.find_element(${sel})`)
          lines.push(`        from appium.webdriver.common.touch_action import TouchAction`)
          lines.push(`        TouchAction(driver).tap(el).tap(el).perform()`)
        }
        break
      case 'long_press':
        if (sel) {
          lines.push(`        el = driver.find_element(${sel})`)
          lines.push(`        from appium.webdriver.common.touch_action import TouchAction`)
          lines.push(`        TouchAction(driver).long_press(el, duration=1000).perform()`)
        }
        break
      case 'input':
        if (sel) {
          lines.push(`        el = driver.find_element(${sel})`)
          lines.push(`        el.clear()`)
          lines.push(`        el.send_keys("${step.inputVal ?? ''}")`)
        }
        if (opts.assertions && sel) {
          lines.push(`        assert driver.find_element(${sel}).get_attribute("text") == "${step.inputVal ?? ''}"`)
        }
        break
      case 'swipe':
        lines.push(`        driver.swipe(540, 1200, 540, ${step.dir === 'up' || step.dir === 'left' ? 400 : 1800}, 800)`)
        break
      case 'scroll':
        lines.push(`        driver.execute_script("mobile: scroll", {"direction": "down"})`)
        break
      case 'hide_keyboard':
        lines.push(`        driver.hide_keyboard()`)
        break
      case 'assertion':
        if (sel) lines.push(`        assert driver.find_element(${sel}).is_displayed()`)
        break
      case 'screenshot':
        lines.push(`        driver.save_screenshot(f"screenshot_step_${step.n}.png")`)
        break
    }
    if (opts.screenshots && step.type !== 'screenshot') {
      lines.push(`        driver.save_screenshot(f"step_${step.n}.png")`)
    }
  }

  return lines.join('\n')
}

function generateJavaScript(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
): string {
  const isAndroid = platform.toUpperCase() !== 'IOS'
  const effectiveClass = className.trim() || 'GeneratedTest'
  const effectiveTest = testName.trim() || 'myTest'
  const lines: string[] = []

  lines.push(`const { remote } = require('webdriverio')`)
  lines.push('')
  lines.push(`describe('${effectiveClass}', () => {`)
  lines.push(`  it('${effectiveTest}', async () => {`)

  for (const step of steps) {
    lines.push('')
    const label = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`    // ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)

    const sel = step.el
      ? isAndroid
        ? `$('android=new UiSelector().resourceId("${step.el.resourceId}")')`
        : `$('~${step.el.accessId}')`
      : null

    if (opts.smartWaits && sel) {
      lines.push(`    await ${sel}.waitForDisplayed({ timeout: 10000 })`)
    }

    switch (step.type) {
      case 'tap':
        if (sel) lines.push(`    await ${sel}.click()`)
        break
      case 'double_tap':
        if (sel) lines.push(`    await ${sel}.doubleClick()`)
        break
      case 'long_press':
        if (sel) {
          lines.push(`    await browser.touchAction([`)
          lines.push(`      { action: 'longPress', element: await ${sel} },`)
          lines.push(`      { action: 'release' }`)
          lines.push(`    ])`)
        }
        break
      case 'input':
        if (sel) {
          lines.push(`    await ${sel}.clearValue()`)
          lines.push(`    await ${sel}.setValue('${step.inputVal ?? ''}')`)
        }
        if (opts.assertions && sel) {
          lines.push(`    expect(await ${sel}.getValue()).toBe('${step.inputVal ?? ''}')`)
        }
        break
      case 'swipe':
        lines.push(`    await browser.execute('mobile: swipe', { direction: '${step.dir ?? 'up'}' })`)
        break
      case 'scroll':
        lines.push(`    await browser.execute('mobile: scroll', { direction: 'down' })`)
        break
      case 'hide_keyboard':
        lines.push(`    await driver.hideKeyboard()`)
        break
      case 'assertion':
        if (sel) lines.push(`    await expect(${sel}).toBeDisplayed()`)
        break
      case 'screenshot':
        lines.push(`    await browser.saveScreenshot('./step_${step.n}.png')`)
        break
    }
    if (opts.screenshots && step.type !== 'screenshot') {
      lines.push(`    await browser.saveScreenshot('./step_${step.n}.png')`)
    }
  }

  lines.push(`  })`)
  lines.push(`})`)

  return lines.join('\n')
}

function generateCSharp(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
): string {
  const isAndroid = platform.toUpperCase() !== 'IOS'
  const effectiveClass = toPascalCase(className.trim() || 'GeneratedTest')
  const effectiveTest = toPascalCase(testName.trim() || 'MyTest')
  const lines: string[] = []

  lines.push('using NUnit.Framework;')
  lines.push('using OpenQA.Selenium;')
  lines.push('using OpenQA.Selenium.Appium;')
  lines.push('using OpenQA.Selenium.Appium.Android;')
  if (opts.smartWaits) {
    lines.push('using OpenQA.Selenium.Support.UI;')
    lines.push('using SeleniumExtras.WaitHelpers;')
  }
  lines.push('')
  lines.push(`namespace ${effectiveClass}Tests`)
  lines.push('{')
  lines.push('    [TestFixture]')
  lines.push(`    public class ${effectiveClass} : BaseTest`)
  lines.push('    {')
  lines.push('        [Test]')
  lines.push(`        public void ${effectiveTest}()`)
  lines.push('        {')

  for (const step of steps) {
    lines.push('')
    const label = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`            // ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)

    const byStr = step.el
      ? isAndroid
        ? `By.Id("${step.el.resourceId}")`
        : `MobileBy.AccessibilityId("${step.el.accessId}")`
      : null

    if (opts.smartWaits && byStr) {
      lines.push(`            new WebDriverWait(_driver, TimeSpan.FromSeconds(10))`)
      lines.push(`                .Until(ExpectedConditions.ElementExists(${byStr}));`)
    }

    switch (step.type) {
      case 'tap':
        if (byStr) lines.push(`            _driver.FindElement(${byStr}).Click();`)
        break
      case 'double_tap':
        if (byStr) {
          lines.push(`            var el${step.n} = _driver.FindElement(${byStr});`)
          lines.push(`            new Actions(_driver).DoubleClick(el${step.n}).Perform();`)
        }
        break
      case 'long_press':
        if (byStr) {
          lines.push(`            var el${step.n} = _driver.FindElement(${byStr});`)
          lines.push(`            new Actions(_driver).ClickAndHold(el${step.n}).Pause(TimeSpan.FromSeconds(1)).Release().Perform();`)
        }
        break
      case 'input':
        if (byStr) {
          lines.push(`            var el${step.n} = _driver.FindElement(${byStr});`)
          lines.push(`            el${step.n}.Clear();`)
          lines.push(`            el${step.n}.SendKeys("${step.inputVal ?? ''}");`)
        }
        if (opts.assertions && byStr) {
          lines.push(`            Assert.AreEqual("${step.inputVal ?? ''}", _driver.FindElement(${byStr}).GetAttribute("text"));`)
        }
        break
      case 'swipe':
        lines.push(`            _driver.ExecuteScript("mobile: swipe", new Dictionary<string, string> { { "direction", "${step.dir ?? 'up'}" } });`)
        break
      case 'scroll':
        lines.push(`            _driver.ExecuteScript("mobile: scroll", new Dictionary<string, string> { { "direction", "down" } });`)
        break
      case 'hide_keyboard':
        lines.push(`            _driver.HideKeyboard();`)
        break
      case 'assertion':
        if (byStr) lines.push(`            Assert.IsTrue(_driver.FindElement(${byStr}).Displayed);`)
        break
      case 'screenshot':
        lines.push(`            ((ITakesScreenshot)_driver).GetScreenshot().SaveAsFile($"step_${step.n}.png");`)
        break
    }
    if (opts.screenshots && step.type !== 'screenshot') {
      lines.push(`            ((ITakesScreenshot)_driver).GetScreenshot().SaveAsFile($"step_${step.n}.png");`)
    }
  }

  lines.push(`        }`)
  lines.push(`    }`)
  lines.push(`}`)

  return lines.join('\n')
}

function generateKotlin(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
): string {
  const isAndroid = platform.toUpperCase() !== 'IOS'
  const effectiveClass = toPascalCase(className.trim() || 'GeneratedTest')
  const effectiveTest = testName.trim() || 'myTest'
  const lines: string[] = []

  lines.push('import io.appium.java_client.AppiumBy')
  lines.push('import io.appium.java_client.android.AndroidDriver')
  lines.push('import org.junit.jupiter.api.Test')
  if (opts.smartWaits) {
    lines.push('import org.openqa.selenium.support.ui.WebDriverWait')
    lines.push('import org.openqa.selenium.support.ui.ExpectedConditions')
  }
  if (opts.assertions) lines.push('import org.junit.jupiter.api.Assertions.*')
  lines.push('')
  lines.push(`class ${effectiveClass} : BaseTest() {`)
  lines.push('')
  lines.push('    @Test')
  lines.push(`    fun \`${effectiveTest}\`() {`)

  for (const step of steps) {
    lines.push('')
    const label = stepTypeLabel(step.type)
    const elText = step.el?.text ?? ''
    lines.push(`        // ${step.n}. ${label}${elText ? ` — ${elText}` : ''}`)

    const byExpr = step.el
      ? isAndroid
        ? `AppiumBy.id("${step.el.resourceId}")`
        : `AppiumBy.accessibilityId("${step.el.accessId}")`
      : null

    if (opts.smartWaits && byExpr) {
      lines.push(`        WebDriverWait(driver, 10).until(ExpectedConditions.presenceOfElementLocated(${byExpr}))`)
    }

    switch (step.type) {
      case 'tap':
        if (byExpr) lines.push(`        driver.findElement(${byExpr}).click()`)
        break
      case 'double_tap':
        if (byExpr) {
          lines.push(`        val el${step.n} = driver.findElement(${byExpr})`)
          lines.push(`        // TODO: double tap via TouchAction`)
        }
        break
      case 'long_press':
        if (byExpr) {
          lines.push(`        val el${step.n} = driver.findElement(${byExpr})`)
          lines.push(`        // TODO: long press via TouchAction`)
        }
        break
      case 'input':
        if (byExpr) {
          lines.push(`        val el${step.n} = driver.findElement(${byExpr})`)
          lines.push(`        el${step.n}.clear()`)
          lines.push(`        el${step.n}.sendKeys("${step.inputVal ?? ''}")`)
        }
        if (opts.assertions && byExpr) {
          lines.push(`        assertEquals("${step.inputVal ?? ''}", driver.findElement(${byExpr}).getAttribute("text"))`)
        }
        break
      case 'swipe':
        lines.push(`        driver.executeScript("mobile: swipe", mapOf("direction" to "${step.dir ?? 'up'}"))`)
        break
      case 'scroll':
        lines.push(`        driver.executeScript("mobile: scroll", mapOf("direction" to "down"))`)
        break
      case 'hide_keyboard':
        lines.push(`        driver.hideKeyboard()`)
        break
      case 'assertion':
        if (byExpr) lines.push(`        assertTrue(driver.findElement(${byExpr}).isDisplayed)`)
        break
      case 'screenshot':
        lines.push(`        (driver as TakesScreenshot).getScreenshotAs(OutputType.FILE).copyTo(File("step_${step.n}.png"))`)
        break
    }
    if (opts.screenshots && step.type !== 'screenshot') {
      lines.push(`        (driver as TakesScreenshot).getScreenshotAs(OutputType.FILE).copyTo(File("step_${step.n}.png"))`)
    }
  }

  lines.push(`    }`)
  lines.push(`}`)

  return lines.join('\n')
}

function generateCode(
  steps: RecStep[],
  opts: GenOpts,
  platform: string,
  testName: string,
  className: string,
  lang: Lang,
): string {
  switch (lang) {
    case 'java-testng':
    case 'java-junit':
      return generateJava(steps, opts, platform, testName, className, lang)
    case 'python':
      return generatePython(steps, opts, platform, testName, className)
    case 'javascript':
      return generateJavaScript(steps, opts, platform, testName, className)
    case 'csharp':
      return generateCSharp(steps, opts, platform, testName, className)
    case 'kotlin':
      return generateKotlin(steps, opts, platform, testName, className)
  }
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

const KW_JAVA = /\b(public|void|class|extends|static|private|new|return|if|else|for|while|this|import|package|final|boolean|int|String|true|false|null)\b/g
const KW_PYTHON = /\b(import|from|def|class|self|return|if|else|elif|for|while|True|False|None|async|await|with|as|assert|not|and|or|in|is|lambda|pass|yield)\b/g
const KW_JS = /\b(const|let|var|function|class|async|await|return|if|else|for|while|new|import|require|export|default|true|false|null|undefined|this|of|in)\b/g
const KW_CS = /\b(using|namespace|public|private|protected|class|void|string|var|new|return|if|else|for|while|foreach|true|false|null|async|await|static|override|virtual|readonly)\b/g
const KW_KT = /\b(import|fun|class|val|var|return|if|else|for|while|when|true|false|null|object|companion|override|private|public|protected|by|is|as|in|this|it)\b/g

function SyntaxLine({ line, lang = 'java-testng' }: { line: string; lang?: Lang }) {
  const trimmed = line.trimStart()

  // Comments — all languages
  if (trimmed.startsWith('//') || trimmed.startsWith('#')) {
    return <span style={{ color: '#6a9955' }}>{line}</span>
  }

  // Decorators/annotations
  if (trimmed.startsWith('@')) {
    return <span style={{ color: '#c586c0' }}>{line}</span>
  }

  // Import lines
  if (
    trimmed.startsWith('import ') ||
    trimmed.startsWith('from ') ||
    trimmed.startsWith('using ') ||
    trimmed.startsWith('require(')
  ) {
    return <span style={{ color: '#4fc1ff' }}>{line}</span>
  }

  const kwPattern =
    lang === 'python' ? KW_PYTHON :
    lang === 'javascript' ? KW_JS :
    lang === 'csharp' ? KW_CS :
    lang === 'kotlin' ? KW_KT :
    KW_JAVA

  // Split by string literals (single or double quoted)
  const parts = line.split(/(\"[^\"]*\"|'[^']*')/g)

  return (
    <span>
      {parts.map((part, i) => {
        if (
          ((part.startsWith('"') && part.endsWith('"')) ||
            (part.startsWith("'") && part.endsWith("'"))) &&
          part.length >= 2
        ) {
          return <span key={i} style={{ color: '#ce9178' }}>{part}</span>
        }
        const kwRe = new RegExp(kwPattern.source, 'g')
        const subparts = part.split(kwRe)
        const kwMatches = part.match(kwRe) ?? []
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
  inspectedElId?: string
}

const RecordableEl = React.memo(function RecordableEl({
  el,
  recording,
  onRecord,
  children,
  style,
  className,
  inspectedElId,
}: RecordableElProps) {
  const [hovered, setHovered] = useState(false)
  const isInspected = inspectedElId === el.shortId

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
        outline: isInspected
          ? '2px solid #14b8a6'
          : recording && hovered
            ? '2px solid #3b82f6'
            : 'none',
        outlineOffset: '-1px',
        borderRadius: 4,
        boxShadow: isInspected ? '0 0 0 3px rgba(20,184,166,0.18)' : 'none',
        zIndex: isInspected ? 2 : 'auto',
        transition: 'outline 0.15s, box-shadow 0.15s',
        ...style,
      }}
      className={className}
      onMouseEnter={() => recording && setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={handleClick}
    >
      {children}
      {isInspected && (
        <div
          style={{
            position: 'absolute',
            top: -18,
            left: 0,
            background: '#0f766e',
            color: '#fff',
            fontSize: 8,
            padding: '1px 5px',
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
      {recording && hovered && !isInspected && (
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
  inspectedElId?: string
}

const CinepolisHomeScreen = React.memo(function CinepolisHomeScreen({
  recording,
  onRecord,
  onScreenChange,
  inspectedElId,
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
        <RecordableEl el={HOME_ELS.buscar} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
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
          <RecordableEl el={HOME_ELS.tabCartelera} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
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
          <RecordableEl el={HOME_ELS.tabProximos} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
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
          <RecordableEl el={HOME_ELS.pelicula_duna} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
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
            inspectedElId={inspectedElId}
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
            inspectedElId={inspectedElId}
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
  inspectedElId?: string
}

const CinepolisLoginScreen = React.memo(function CinepolisLoginScreen({
  recording,
  onRecord,
  onScreenChange,
  inspectedElId,
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

        <RecordableEl el={LOGIN_ELS.correo} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
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

        <RecordableEl el={LOGIN_ELS.password} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
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

        <RecordableEl el={LOGIN_ELS.entrar} recording={recording} onRecord={handleRecord} inspectedElId={inspectedElId}>
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

// ─── Recording Overlay ────────────────────────────────────────────────────────

interface RecordingOverlayProps {
  onInteract: (
    nx: number, ny: number,
    gesture: 'tap' | 'swipe' | 'long_press',
    nx2?: number, ny2?: number,
  ) => void
}

function RecordingOverlay({ onInteract }: RecordingOverlayProps) {
  const dragRef = useRef<{
    startNx:   number
    startNy:   number
    timer:     ReturnType<typeof setTimeout> | null
    fired:     boolean
  } | null>(null)

  return (
    <div
      style={{
        position: 'absolute',
        inset: 0,
        zIndex: 25,
        cursor: 'crosshair',
        userSelect: 'none',
        WebkitUserSelect: 'none',
      } as React.CSSProperties}
      onMouseDown={(e) => {
        e.preventDefault()
        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
        const startNx = Math.max(0, Math.min(1, (e.clientX - rect.left)  / rect.width))
        const startNy = Math.max(0, Math.min(1, (e.clientY - rect.top)   / rect.height))
        const timer = setTimeout(() => {
          if (dragRef.current && !dragRef.current.fired) {
            dragRef.current.fired = true
            onInteract(startNx, startNy, 'long_press')
          }
        }, 600)
        dragRef.current = { startNx, startNy, timer, fired: false }
      }}
      onMouseUp={(e) => {
        const drag = dragRef.current
        if (!drag) return
        if (drag.timer) clearTimeout(drag.timer)
        if (drag.fired) { dragRef.current = null; return }
        dragRef.current = null

        const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
        const endNx = Math.max(0, Math.min(1, (e.clientX - rect.left)  / rect.width))
        const endNy = Math.max(0, Math.min(1, (e.clientY - rect.top)   / rect.height))
        const dx = endNx - drag.startNx
        const dy = endNy - drag.startNy
        const dist = Math.sqrt(dx * dx + dy * dy)

        if (dist < 0.03) {
          onInteract(drag.startNx, drag.startNy, 'tap')
        } else {
          onInteract(drag.startNx, drag.startNy, 'swipe', endNx, endNy)
        }
      }}
      onMouseLeave={() => {
        if (dragRef.current?.timer) clearTimeout(dragRef.current.timer)
        dragRef.current = null
      }}
    />
  )
}

// ─── Phone Frame ──────────────────────────────────────────────────────────────

interface PhoneFrameProps {
  recording: boolean
  screen: AppScreen
  onRecord: (el: AppEl) => void
  onScreenChange: (s: AppScreen) => void
  isLandscape?: boolean
  inspectedElId?: string
  previewUrl?: string | null
  previewState?: StreamState
  /** When set and recording with a live preview, renders an interactive overlay. */
  onScreenInteract?: (
    nx: number, ny: number,
    gesture: 'tap' | 'swipe' | 'long_press',
    nx2?: number, ny2?: number,
  ) => void
}

const PhoneFrame = React.memo(function PhoneFrame({
  recording,
  screen,
  onRecord,
  onScreenChange,
  isLandscape = false,
  inspectedElId,
  previewUrl,
  previewState,
  onScreenInteract,
}: PhoneFrameProps) {
  const PHONE_W = 296
  const SCREEN_W = 262
  const SCREEN_H = 452
  const SCALE = 1.0

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
          backgroundColor: '#000',
          transform: `scale(${SCALE})`,
          transformOrigin: 'top center',
          marginBottom: -(SCREEN_H * (1 - SCALE)),
          position: 'relative',
        }}
      >
        {/* ── Live Preview layer (DeviceStreamProvider) ── */}
        {previewUrl ? (
          <>
            {/* Real device screenshot */}
            <img
              src={previewUrl}
              draggable={false}
              style={{
                position: 'absolute',
                inset: 0,
                width: '100%',
                height: '100%',
                objectFit: 'cover',
                display: 'block',
              }}
              alt="Device screen"
            />
            {/* Subtle "Updating" indicator — top-right dot */}
            {previewState === 'updating' && (
              <div
                style={{
                  position: 'absolute',
                  top: 6,
                  right: 6,
                  width: 6,
                  height: 6,
                  borderRadius: '50%',
                  backgroundColor: '#6366f1',
                  opacity: 0.85,
                  animation: 'pulse 1s ease-in-out infinite',
                  zIndex: 10,
                }}
              />
            )}
            {/* Interactive recording overlay — captures taps/swipes on the live mirror */}
            {recording && onScreenInteract && (
              <RecordingOverlay onInteract={onScreenInteract} />
            )}
          </>
        ) : (
          /* ── Static mockup fallback (no device / no preview) ── */
          <>
            {(previewState === 'loading' || previewState === 'connecting') && (
              /* Loading state overlay */
              <div
                style={{
                  position: 'absolute',
                  inset: 0,
                  backgroundColor: '#0d1117',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 10,
                  zIndex: 20,
                }}
              >
                <div
                  style={{
                    width: 32,
                    height: 32,
                    borderRadius: '50%',
                    border: '2px solid rgba(99,102,241,0.2)',
                    borderTopColor: '#6366f1',
                    animation: 'spin 0.8s linear infinite',
                  }}
                />
                <span style={{ color: '#475569', fontSize: 10, fontWeight: 600 }}>
                  {previewState === 'connecting' ? 'Conectando...' : 'Cargando pantalla...'}
                </span>
              </div>
            )}
            {(previewState === 'device_disconnected' || previewState === 'runner_offline') && (
              /* Error state */
              <div
                style={{
                  position: 'absolute',
                  inset: 0,
                  backgroundColor: '#0d1117',
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 8,
                  zIndex: 20,
                }}
              >
                <div style={{ fontSize: 22, opacity: 0.4 }}>
                  {previewState === 'device_disconnected' ? '📵' : '⚡'}
                </div>
                <span style={{ color: '#475569', fontSize: 10, fontWeight: 600, textAlign: 'center', padding: '0 16px' }}>
                  {previewState === 'device_disconnected'
                    ? 'Dispositivo desconectado'
                    : 'Runner no disponible'}
                </span>
              </div>
            )}
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
                    inspectedElId={inspectedElId}
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
                    inspectedElId={inspectedElId}
                  />
                </motion.div>
              )}
            </AnimatePresence>
          </>
        )}
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

// ─── Step Card ────────────────────────────────────────────────────────────────

const DIR_LABELS: Record<string, string> = {
  up: '↑ Arriba', down: '↓ Abajo', left: '← Izquierda', right: '→ Derecha',
}

interface StepCardProps {
  step: RecStep
  index: number
  total: number
  isSelected: boolean
  onDelete: (id: string) => void
  onDuplicate: (id: string) => void
  onMoveUp: (id: string) => void
  onMoveDown: (id: string) => void
  onEdit: (step: RecStep) => void
  onCardClick: () => void
}

function StepCard({ step, index, total, isSelected, onDelete, onDuplicate, onMoveUp, onMoveDown, onEdit, onCardClick }: StepCardProps) {
  const [hovered, setHovered] = useState(false)
  const color = STEP_COLORS[step.type]
  const isAndroid = true // demo defaults to Android

  const locatorValue = step.el
    ? isAndroid ? step.el.resourceId : step.el.accessId
    : null

  return (
    <motion.div
      layout
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0, x: -16, height: 0, marginBottom: 0, paddingTop: 0 }}
      transition={{ duration: 0.18 }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={onCardClick}
      style={{
        borderRadius: 10,
        border: isSelected
          ? `1px solid ${color}60`
          : `1px solid ${hovered ? color + '30' : 'rgba(255,255,255,0.07)'}`,
        background: isSelected
          ? `linear-gradient(135deg, ${color}12, rgba(255,255,255,0.03))`
          : hovered
            ? `linear-gradient(135deg, ${color}07, rgba(255,255,255,0.02))`
            : 'rgba(255,255,255,0.025)',
        borderLeft: `3px solid ${color}`,
        marginBottom: 8,
        overflow: 'hidden',
        transition: 'border-color 0.15s, background 0.15s',
        cursor: 'pointer',
        boxShadow: isSelected ? `0 0 0 1px ${color}20` : 'none',
      }}
    >
      {/* ── Header row ── */}
      <div style={{ display: 'flex', alignItems: 'center', padding: '10px 12px 8px', gap: 10 }}>
        {/* Icon chip */}
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: 8,
            background: color + '18',
            border: `1px solid ${color}30`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          {getStepIcon(step.type, 14)}
        </div>

        {/* Step number + type label */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <span
              style={{
                fontSize: 9,
                color: 'rgba(255,255,255,0.25)',
                background: 'rgba(255,255,255,0.06)',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 4,
                padding: '1px 5px',
                fontWeight: 600,
                flexShrink: 0,
              }}
            >
              #{step.n}
            </span>
            <span style={{ fontSize: 12, color: '#e2e8f0', fontWeight: 700, letterSpacing: 0.3 }}>
              {stepTypeLabel(step.type).toUpperCase()}
            </span>
          </div>
          {step.el && (
            <span
              style={{
                fontSize: 10,
                color: '#64748b',
                marginTop: 1,
                display: 'block',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                maxWidth: 200,
              }}
            >
              {step.el.text}
            </span>
          )}
        </div>

        {/* Timestamp */}
        <span style={{ fontSize: 10, color: '#334155', fontFamily: 'monospace', flexShrink: 0 }}>
          {step.timeStr}
        </span>

        {/* Action buttons (appear on hover) */}
        <AnimatePresence>
          {hovered && (
            <motion.div
              initial={{ opacity: 0, x: 6 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 6 }}
              transition={{ duration: 0.1 }}
              style={{ display: 'flex', gap: 3, flexShrink: 0 }}
            >
              {[
                {
                  icon: <ChevronUp size={11} />,
                  title: 'Mover arriba',
                  disabled: index === 0,
                  onClick: () => onMoveUp(step.id),
                  danger: false,
                },
                {
                  icon: <ChevronDown size={11} />,
                  title: 'Mover abajo',
                  disabled: index === total - 1,
                  onClick: () => onMoveDown(step.id),
                  danger: false,
                },
                {
                  icon: <Copy size={11} />,
                  title: 'Duplicar',
                  disabled: false,
                  onClick: () => onDuplicate(step.id),
                  danger: false,
                },
                {
                  icon: <Pencil size={11} />,
                  title: 'Editar',
                  disabled: false,
                  onClick: () => onEdit(step),
                  danger: false,
                },
                {
                  icon: <Trash2 size={11} />,
                  title: 'Eliminar',
                  disabled: false,
                  onClick: () => onDelete(step.id),
                  danger: true,
                },
              ].map((btn, i) => (
                <button
                  key={i}
                  title={btn.title}
                  disabled={btn.disabled}
                  onClick={btn.onClick}
                  style={{
                    width: 24,
                    height: 24,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: btn.danger
                      ? 'rgba(239,68,68,0.08)'
                      : 'rgba(255,255,255,0.05)',
                    border: `1px solid ${btn.danger ? 'rgba(239,68,68,0.2)' : 'rgba(255,255,255,0.1)'}`,
                    borderRadius: 5,
                    cursor: btn.disabled ? 'not-allowed' : 'pointer',
                    color: btn.disabled ? '#1e293b' : btn.danger ? '#ef4444' : '#64748b',
                    transition: 'all 0.1s',
                    opacity: btn.disabled ? 0.4 : 1,
                  }}
                >
                  {btn.icon}
                </button>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>

      {/* ── Details row ── */}
      <div style={{ padding: '0 12px 10px 54px', display: 'flex', flexDirection: 'column', gap: 4 }}>
        {/* Element + locator (tap/long/double) */}
        {(step.type === 'tap' || step.type === 'double_tap' || step.type === 'long_press') && step.el && (
          <>
            <DetailRow label="Elemento" value={step.el.shortId} mono />
            <DetailRow label="Locator" value={isAndroid ? 'resource-id' : 'accessibilityId'} />
            {locatorValue && (
              <DetailRow label="ID" value={locatorValue} mono truncate />
            )}
          </>
        )}

        {/* Input */}
        {step.type === 'input' && (
          <>
            {step.el && <DetailRow label="Elemento" value={step.el.shortId} mono />}
            {step.inputVal && (
              <DetailRow label="Valor" value={`"${step.inputVal}"`} color="#34d399" />
            )}
            {locatorValue && <DetailRow label="Locator" value={locatorValue} mono truncate />}
          </>
        )}

        {/* Swipe */}
        {step.type === 'swipe' && (
          <DetailRow label="Dirección" value={DIR_LABELS[step.dir ?? 'right'] ?? step.dir ?? '—'} />
        )}

        {/* Scroll */}
        {step.type === 'scroll' && (
          <DetailRow label="Tipo" value="Vertical" />
        )}

        {/* Hide keyboard */}
        {step.type === 'hide_keyboard' && (
          <DetailRow label="Acción" value="Ocultar teclado del sistema" />
        )}

        {/* Assertion/Screenshot */}
        {(step.type === 'assertion' || step.type === 'screenshot') && step.el && (
          <DetailRow label="Elemento" value={step.el.shortId} mono />
        )}
      </div>
    </motion.div>
  )
}

function DetailRow({
  label,
  value,
  mono = false,
  truncate = false,
  color = '#64748b',
}: {
  label: string
  value: string
  mono?: boolean
  truncate?: boolean
  color?: string
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
      <span
        style={{
          fontSize: 9,
          color: '#334155',
          fontWeight: 600,
          textTransform: 'uppercase',
          letterSpacing: 0.4,
          minWidth: 56,
          flexShrink: 0,
        }}
      >
        {label}
      </span>
      <span
        style={{
          fontSize: 10,
          color,
          fontFamily: mono ? 'monospace' : 'inherit',
          overflow: truncate ? 'hidden' : 'visible',
          textOverflow: 'ellipsis',
          whiteSpace: truncate ? 'nowrap' : 'normal',
          maxWidth: truncate ? 280 : 'none',
        }}
      >
        {value}
      </span>
    </div>
  )
}

// ─── Edit Step Modal ───────────────────────────────────────────────────────────

interface EditStepModalProps {
  step: RecStep
  onClose: () => void
  onSave: (id: string, updates: { elementId?: string; inputVal?: string; dir?: 'up' | 'down' | 'left' | 'right' }) => void
}

function EditStepModal({ step, onClose, onSave }: EditStepModalProps) {
  const [elementId, setElementId] = useState(step.el?.shortId ?? '')
  const [inputVal, setInputVal] = useState(step.inputVal ?? '')
  const [dir, setDir] = useState<'up' | 'down' | 'left' | 'right'>(step.dir ?? 'down')

  const handleSave = () => {
    onSave(step.id, {
      elementId: elementId.trim() || undefined,
      inputVal: inputVal.trim() || undefined,
      dir,
    })
    onClose()
  }

  const color = STEP_COLORS[step.type]

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(0,0,0,0.7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 400,
        backdropFilter: 'blur(4px)',
      }}
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.93, y: 12 }}
        animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.93, y: 12 }}
        transition={{ type: 'spring', stiffness: 300, damping: 28 }}
        onClick={e => e.stopPropagation()}
        style={{
          background: '#111827',
          border: '1px solid rgba(255,255,255,0.1)',
          borderTop: `3px solid ${color}`,
          borderRadius: 12,
          padding: 24,
          width: 400,
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <div
              style={{
                width: 28,
                height: 28,
                borderRadius: 7,
                background: color + '18',
                border: `1px solid ${color}30`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              {getStepIcon(step.type, 13)}
            </div>
            <div>
              <p style={{ margin: 0, color: '#e2e8f0', fontSize: 13, fontWeight: 700 }}>
                Editar paso #{step.n}
              </p>
              <p style={{ margin: 0, color: '#475569', fontSize: 10 }}>
                {stepTypeLabel(step.type)}
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#475569', padding: 4 }}
          >
            <X size={15} />
          </button>
        </div>

        {/* Element ID field */}
        {step.type !== 'scroll' && step.type !== 'hide_keyboard' && step.type !== 'swipe' && (
          <div>
            <label style={{ display: 'block', fontSize: 11, color: '#475569', marginBottom: 5, fontWeight: 600 }}>
              ID DEL ELEMENTO
            </label>
            <input
              value={elementId}
              onChange={e => setElementId(e.target.value)}
              placeholder="btn_iniciar_sesion"
              style={{
                width: '100%',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 7,
                color: '#e2e8f0',
                padding: '8px 11px',
                fontSize: 12,
                fontFamily: 'monospace',
                boxSizing: 'border-box',
                outline: 'none',
              }}
            />
          </div>
        )}

        {/* Input value */}
        {step.type === 'input' && (
          <div>
            <label style={{ display: 'block', fontSize: 11, color: '#475569', marginBottom: 5, fontWeight: 600 }}>
              VALOR A ESCRIBIR
            </label>
            <input
              value={inputVal}
              onChange={e => setInputVal(e.target.value)}
              placeholder="usuario@empresa.com"
              style={{
                width: '100%',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.1)',
                borderRadius: 7,
                color: '#34d399',
                padding: '8px 11px',
                fontSize: 12,
                boxSizing: 'border-box',
                outline: 'none',
              }}
            />
          </div>
        )}

        {/* Direction (swipe) */}
        {step.type === 'swipe' && (
          <div>
            <label style={{ display: 'block', fontSize: 11, color: '#475569', marginBottom: 7, fontWeight: 600 }}>
              DIRECCIÓN
            </label>
            <div style={{ display: 'flex', gap: 6 }}>
              {(['up', 'down', 'left', 'right'] as const).map(d => (
                <button
                  key={d}
                  onClick={() => setDir(d)}
                  style={{
                    flex: 1,
                    padding: '7px 0',
                    borderRadius: 7,
                    fontSize: 11,
                    fontWeight: 600,
                    cursor: 'pointer',
                    background: dir === d ? color + '20' : 'rgba(255,255,255,0.04)',
                    border: `1px solid ${dir === d ? color + '50' : 'rgba(255,255,255,0.08)'}`,
                    color: dir === d ? color : '#475569',
                    transition: 'all 0.12s',
                  }}
                >
                  {DIR_LABELS[d]}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Save button */}
        <button
          onClick={handleSave}
          style={{
            width: '100%',
            padding: '10px 0',
            background: `linear-gradient(135deg, ${color}30, ${color}20)`,
            border: `1px solid ${color}40`,
            borderRadius: 8,
            color,
            fontSize: 12,
            fontWeight: 700,
            cursor: 'pointer',
            marginTop: 4,
            transition: 'all 0.15s',
          }}
        >
          Guardar cambios
        </button>
      </motion.div>
    </motion.div>
  )
}

// ─── Steps Panel ──────────────────────────────────────────────────────────────

interface StepsPanelProps {
  steps: RecStep[]
  recording: boolean
  selectedStepId: string | null
  onDeleteStep: (id: string) => void
  onDuplicateStep: (id: string) => void
  onMoveStep: (id: string, dir: 'up' | 'down') => void
  onEditStep: (id: string, updates: { elementId?: string; inputVal?: string; dir?: 'up' | 'down' | 'left' | 'right' }) => void
  onSelectStep: (step: RecStep) => void
  onManualAdd: (
    type: StepType,
    elementId: string,
    inputVal?: string,
    dir?: 'up' | 'down' | 'left' | 'right',
  ) => void
}

const FILTER_CHIPS: { label: string; value: StepFilter; color: string }[] = [
  { label: 'Todos', value: 'all', color: '#6366f1' },
  { label: 'Tap', value: 'tap', color: '#818cf8' },
  { label: 'Input', value: 'input', color: '#34d399' },
  { label: 'Swipe', value: 'swipe', color: '#f59e0b' },
  { label: 'Scroll', value: 'scroll', color: '#60a5fa' },
  { label: 'Assertion', value: 'assertion', color: '#14b8a6' },
  { label: 'Screenshot', value: 'screenshot', color: '#eab308' },
]

const StepsPanel = React.memo(function StepsPanel({
  steps,
  recording,
  selectedStepId,
  onDeleteStep,
  onDuplicateStep,
  onMoveStep,
  onEditStep,
  onSelectStep,
  onManualAdd,
}: StepsPanelProps) {
  const scrollRef = useRef<HTMLDivElement>(null)
  const [search, setSearch] = useState('')
  const [activeFilter, setActiveFilter] = useState<StepFilter>('all')
  const [editingStep, setEditingStep] = useState<RecStep | null>(null)

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight
    }
  }, [steps.length])

  const filteredSteps = useMemo(() => {
    let result = steps
    if (activeFilter !== 'all') {
      result = result.filter(s => s.type === activeFilter)
    }
    if (search.trim()) {
      const q = search.toLowerCase()
      result = result.filter(s =>
        stepTypeLabel(s.type).toLowerCase().includes(q) ||
        s.el?.text?.toLowerCase().includes(q) ||
        s.el?.shortId?.toLowerCase().includes(q) ||
        s.inputVal?.toLowerCase().includes(q)
      )
    }
    return result
  }, [steps, activeFilter, search])

  return (
    <>
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
        {/* ── Panel header ── */}
        <div
          style={{
            padding: '12px 16px 10px',
            borderBottom: '1px solid rgba(255,255,255,0.06)',
            flexShrink: 0,
          }}
        >
          {/* Title row */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 10,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div
                style={{
                  width: 26,
                  height: 26,
                  borderRadius: 7,
                  background: 'rgba(99,102,241,0.12)',
                  border: '1px solid rgba(99,102,241,0.2)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Layers3 size={13} color="#818cf8" />
              </div>
              <span style={{ color: '#e2e8f0', fontWeight: 700, fontSize: 13 }}>Pasos Grabados</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              {steps.length > 0 && (
                <span
                  style={{
                    background: 'linear-gradient(135deg, #6366f1, #818cf8)',
                    color: '#fff',
                    fontSize: 10,
                    fontWeight: 700,
                    padding: '2px 8px',
                    borderRadius: 20,
                  }}
                >
                  {filteredSteps.length !== steps.length
                    ? `${filteredSteps.length}/${steps.length}`
                    : steps.length}
                </span>
              )}
            </div>
          </div>

          {/* Search bar */}
          <div style={{ position: 'relative', marginBottom: 8 }}>
            <Search
              size={12}
              color="#475569"
              style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)' }}
            />
            <input
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Buscar paso..."
              style={{
                width: '100%',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 7,
                padding: '6px 10px 6px 28px',
                fontSize: 11,
                color: '#94a3b8',
                outline: 'none',
                boxSizing: 'border-box',
              }}
            />
          </div>

          {/* Filter chips */}
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
            {FILTER_CHIPS.map(({ label, value, color }) => {
              const isActive = activeFilter === value
              return (
                <button
                  key={value}
                  onClick={() => setActiveFilter(value)}
                  style={{
                    padding: '3px 9px',
                    borderRadius: 20,
                    fontSize: 10,
                    fontWeight: 500,
                    cursor: 'pointer',
                    border: `1px solid ${isActive ? color : 'rgba(255,255,255,0.08)'}`,
                    background: isActive ? `${color}22` : 'rgba(255,255,255,0.03)',
                    color: isActive ? color : '#475569',
                    transition: 'all 0.15s',
                  }}
                >
                  {label}
                </button>
              )
            })}
          </div>
        </div>

        {/* ── Steps list ── */}
        <div
          ref={scrollRef}
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '10px 12px',
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
                padding: '32px 24px',
                gap: 14,
                textAlign: 'center',
              }}
            >
              <div
                style={{
                  width: 56,
                  height: 56,
                  borderRadius: 16,
                  background: 'rgba(99,102,241,0.08)',
                  border: '1px solid rgba(99,102,241,0.15)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Layers3 size={24} color="rgba(99,102,241,0.4)" />
              </div>
              <div>
                <p style={{ color: '#475569', fontSize: 13, fontWeight: 600, margin: '0 0 4px' }}>
                  No existen pasos grabados
                </p>
                <p style={{ color: '#334155', fontSize: 11, margin: 0, lineHeight: 1.5 }}>
                  Inicia la grabación e interactúa con el dispositivo para capturar acciones.
                </p>
              </div>
              <div
                style={{
                  display: 'flex',
                  flexWrap: 'wrap',
                  gap: '6px 12px',
                  justifyContent: 'center',
                  marginTop: 4,
                }}
              >
                {[
                  { label: 'Tap', color: '#818cf8' },
                  { label: 'Input', color: '#34d399' },
                  { label: 'Swipe', color: '#f59e0b' },
                  { label: 'Scroll', color: '#60a5fa' },
                ].map(({ label, color }) => (
                  <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                    <div style={{ width: 6, height: 6, borderRadius: '50%', backgroundColor: color }} />
                    <span style={{ fontSize: 10, color: '#334155' }}>{label}</span>
                  </div>
                ))}
              </div>
            </div>
          ) : filteredSteps.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '40px 24px',
                gap: 10,
                textAlign: 'center',
              }}
            >
              <Search size={22} color="#334155" />
              <p style={{ color: '#475569', fontSize: 12, fontWeight: 600, margin: 0 }}>
                Sin resultados
              </p>
              <p style={{ color: '#334155', fontSize: 11, margin: 0 }}>
                Prueba con otro término o cambia el filtro.
              </p>
            </div>
          ) : (
            <AnimatePresence initial={false}>
              {filteredSteps.map((step, idx) => (
                <StepCard
                  key={step.id}
                  step={step}
                  index={steps.indexOf(step)}
                  total={steps.length}
                  isSelected={selectedStepId === step.id}
                  onDelete={onDeleteStep}
                  onDuplicate={onDuplicateStep}
                  onMoveUp={id => onMoveStep(id, 'up')}
                  onMoveDown={id => onMoveStep(id, 'down')}
                  onEdit={setEditingStep}
                  onCardClick={() => onSelectStep(step)}
                />
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

      {/* Edit modal — portal-like via AnimatePresence */}
      <AnimatePresence>
        {editingStep && (
          <EditStepModal
            step={editingStep}
            onClose={() => setEditingStep(null)}
            onSave={(id, updates) => {
              onEditStep(id, updates)
              setEditingStep(null)
            }}
          />
        )}
      </AnimatePresence>
    </>
  )
})

const LANG_OPTIONS: { value: Lang; label: string; color: string; ext: string }[] = [
  { value: 'java-testng', label: 'Java · TestNG', color: '#f97316', ext: 'java' },
  { value: 'java-junit',  label: 'Java · JUnit',  color: '#f59e0b', ext: 'java' },
  { value: 'python',      label: 'Python',         color: '#3b82f6', ext: 'py'   },
  { value: 'javascript',  label: 'JavaScript',     color: '#eab308', ext: 'js'   },
  { value: 'csharp',      label: 'C#',             color: '#a855f7', ext: 'cs'   },
  { value: 'kotlin',      label: 'Kotlin',         color: '#818cf8', ext: 'kt'   },
]

// ─── XML Tree View ────────────────────────────────────────────────────────────

interface XmlTreeViewProps {
  node: XmlNode
  expanded: Set<string>
  onToggle: (key: string) => void
  inspectedElId: string | null
  onInspect: (id: string) => void
  depth: number
  parentKey?: string
  nodeIndex?: number
}

function XmlTreeView({ node, expanded, onToggle, inspectedElId, onInspect, depth, parentKey = '', nodeIndex = 0 }: XmlTreeViewProps) {
  const nodeKey = `${parentKey}/${node.tag}[${nodeIndex}]`
  const hasChildren = node.children && node.children.length > 0
  const isExpanded = expanded.has(nodeKey) || depth < 2
  const isInspected = node.elId === inspectedElId
  const indent = depth * 16

  const tagColor = depth === 0 ? '#64748b' : depth === 1 ? '#818cf8' : '#93c5fd'
  const attrNameColor = '#14b8a6'
  const attrValColor = '#34d399'

  const priorityAttrs = ['resource-id', 'text', 'content-desc', 'bounds']
  const shownAttrs = Object.entries(node.attrs).filter(([k]) => priorityAttrs.includes(k) || !node.elId)

  return (
    <div
      style={{
        fontFamily: 'monospace',
        fontSize: 10,
        lineHeight: 1.7,
        userSelect: 'none',
        background: isInspected ? 'rgba(20,184,166,0.06)' : 'transparent',
        borderLeft: isInspected ? '2px solid #14b8a6' : '2px solid transparent',
        transition: 'background 0.15s',
      }}
    >
      {/* Opening tag line */}
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-start',
          paddingLeft: indent + 10,
          paddingRight: 10,
          paddingTop: 1,
          paddingBottom: 1,
          cursor: node.elId ? 'pointer' : hasChildren ? 'pointer' : 'default',
          borderRadius: 4,
        }}
        onClick={e => {
          e.stopPropagation()
          if (node.elId) { onInspect(node.elId); return }
          if (hasChildren) onToggle(nodeKey)
        }}
      >
        {hasChildren && (
          <span
            style={{ color: '#475569', marginRight: 4, fontSize: 9, lineHeight: 1.8, flexShrink: 0 }}
            onClick={e => { e.stopPropagation(); onToggle(nodeKey) }}
          >
            {isExpanded ? '▾' : '▸'}
          </span>
        )}
        {!hasChildren && <span style={{ width: 13, flexShrink: 0 }} />}
        <span style={{ flex: 1, flexWrap: 'wrap', display: 'flex', alignItems: 'baseline', gap: 2 }}>
          <span style={{ color: '#475569' }}>&lt;</span>
          <span style={{ color: tagColor, fontWeight: depth < 2 ? 600 : 400 }}>{node.tag}</span>
          {shownAttrs.map(([k, v]) => v && v !== '—' && v !== '' ? (
            <span key={k} style={{ display: 'inline-flex', gap: 1 }}>
              {' '}
              <span style={{ color: attrNameColor }}>{k}</span>
              <span style={{ color: '#475569' }}>="</span>
              <span
                style={{
                  color: attrValColor,
                  maxWidth: 160,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  display: 'inline-block',
                  verticalAlign: 'bottom',
                }}
                title={v}
              >
                {v}
              </span>
              <span style={{ color: '#475569' }}>"</span>
            </span>
          ) : null)}
          {!hasChildren && <><span style={{ color: '#475569' }}> /&gt;</span></>}
          {hasChildren && !isExpanded && (
            <span style={{ color: '#475569' }}>&gt;…&lt;/{node.tag}&gt;</span>
          )}
          {hasChildren && isExpanded && (
            <span style={{ color: '#475569' }}>&gt;</span>
          )}
        </span>
      </div>

      {/* Children */}
      {hasChildren && isExpanded && (
        <div>
          {node.children!.map((child, i) => (
            <XmlTreeView
              key={`${nodeKey}/${child.tag}[${i}]`}
              node={child}
              expanded={expanded}
              onToggle={onToggle}
              inspectedElId={inspectedElId}
              onInspect={onInspect}
              depth={depth + 1}
              parentKey={nodeKey}
              nodeIndex={i}
            />
          ))}
          {/* Closing tag */}
          <div style={{ paddingLeft: indent + 10, fontSize: 10, color: '#475569', lineHeight: 1.7 }}>
            &lt;/{node.tag}&gt;
          </div>
        </div>
      )}
    </div>
  )
}

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
  currentScreen: AppScreen
  inspectedElId: string | null
  onLangChange: (l: Lang) => void
  onViewTabChange: (t: ViewTab) => void
  onOptsChange: (o: GenOpts) => void
  onTestNameChange: (s: string) => void
  onClassNameChange: (s: string) => void
  onInspectEl: (shortId: string) => void
  onCopy: () => void
  onDownload: () => void
  onSaveCase: () => void
  onSaveSuite: () => void
  onExecute: () => void
  onExport: () => void
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
  currentScreen,
  inspectedElId,
  onLangChange,
  onViewTabChange,
  onOptsChange,
  onTestNameChange,
  onClassNameChange,
  onInspectEl,
  onCopy,
  onDownload,
  onSaveCase,
  onSaveSuite,
  onExecute,
  onExport,
  copied,
}: CodePanelProps) {
  const [copiedLocator, setCopiedLocator] = useState<string | null>(null)
  const [xmlExpanded, setXmlExpanded] = useState<Set<string>>(new Set(['hierarchy', 'android.widget.FrameLayout']))

  const inspectedEl = inspectedElId ? getElById(inspectedElId) : null
  const xmlTree = useMemo(() => buildXmlTree(currentScreen), [currentScreen])

  const copyLocator = useCallback((value: string, key: string) => {
    navigator.clipboard.writeText(value).catch(() => {})
    setCopiedLocator(key)
    setTimeout(() => setCopiedLocator(null), 1500)
  }, [])

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
        {/* ── Row 1: Tabs + action buttons ── */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          {/* View tabs */}
          <div style={{ display: 'flex', gap: 1 }}>
            {([
              { id: 'code' as ViewTab, label: 'Código', icon: <Code2 size={10} /> },
              { id: 'xml' as ViewTab, label: 'XML', icon: <FileCode2 size={10} /> },
              { id: 'inspector' as ViewTab, label: 'Inspector', icon: <Eye size={10} /> },
              { id: 'locators' as ViewTab, label: 'Locators', icon: <Link2 size={10} /> },
            ]).map((tab) => (
              <button
                key={tab.id}
                onClick={() => onViewTabChange(tab.id)}
                style={{
                  padding: '5px 9px',
                  fontSize: 11,
                  fontWeight: 500,
                  cursor: 'pointer',
                  border: 'none',
                  borderBottom: viewTab === tab.id
                    ? '2px solid #6366f1'
                    : '2px solid transparent',
                  borderRadius: viewTab === tab.id ? '6px 6px 0 0' : 6,
                  background: viewTab === tab.id
                    ? 'linear-gradient(135deg, rgba(99,102,241,0.18), rgba(129,140,248,0.12))'
                    : 'transparent',
                  color: viewTab === tab.id ? '#818cf8' : '#475569',
                  transition: 'all 0.15s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 4,
                }}
              >
                {tab.icon}
                {tab.label}
              </button>
            ))}
          </div>

          {/* Copy / Download action buttons */}
          <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
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

        {/* ── Row 2: Language chips (only on Código tab) ── */}
        {viewTab === 'code' && (
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
            {LANG_OPTIONS.map(({ value, label, color }) => {
              const isActive = lang === value
              return (
                <button
                  key={value}
                  onClick={() => onLangChange(value)}
                  style={{
                    padding: '3px 10px',
                    borderRadius: 20,
                    fontSize: 10,
                    fontWeight: isActive ? 700 : 500,
                    cursor: 'pointer',
                    border: `1px solid ${isActive ? color : 'rgba(255,255,255,0.08)'}`,
                    background: isActive ? `${color}22` : 'rgba(255,255,255,0.03)',
                    color: isActive ? color : '#475569',
                    transition: 'all 0.15s',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {label}
                </button>
              )
            })}
          </div>
        )}
      </div>

      {/* ── Content area ── */}
      <div style={{ flex: 1, overflowY: 'auto', position: 'relative', minHeight: 0 }}>

        {/* ── Inspector tab ── */}
        {viewTab === 'inspector' && (
          <div style={{ padding: '14px 14px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            {!inspectedEl ? (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '40px 24px',
                  gap: 12,
                  textAlign: 'center',
                }}
              >
                <div
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: 14,
                    background: 'rgba(20,184,166,0.08)',
                    border: '1px solid rgba(20,184,166,0.18)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Eye size={22} color="rgba(20,184,166,0.5)" />
                </div>
                <p style={{ color: '#475569', fontSize: 12, fontWeight: 600, margin: 0 }}>
                  Inspector de Elementos
                </p>
                <p style={{ color: '#334155', fontSize: 11, margin: 0, lineHeight: 1.5 }}>
                  Haz clic en un paso grabado para inspeccionar el elemento.
                </p>
              </div>
            ) : (
              <>
                {/* Element header */}
                <div
                  style={{
                    background: 'rgba(20,184,166,0.07)',
                    border: '1px solid rgba(20,184,166,0.18)',
                    borderRadius: 10,
                    padding: '10px 12px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                  }}
                >
                  <div
                    style={{
                      width: 32,
                      height: 32,
                      borderRadius: 8,
                      background: 'rgba(20,184,166,0.12)',
                      border: '1px solid rgba(20,184,166,0.25)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                    }}
                  >
                    <Eye size={14} color="#14b8a6" />
                  </div>
                  <div style={{ minWidth: 0 }}>
                    <p style={{ margin: 0, color: '#e2e8f0', fontSize: 12, fontWeight: 700 }}>
                      {inspectedEl.shortId}
                    </p>
                    <p style={{ margin: 0, color: '#14b8a6', fontSize: 10, fontFamily: 'monospace' }}>
                      {inspectedEl.className ?? 'android.view.View'}
                    </p>
                  </div>
                </div>

                {/* Properties table */}
                <div
                  style={{
                    borderRadius: 9,
                    border: '1px solid rgba(255,255,255,0.06)',
                    overflow: 'hidden',
                  }}
                >
                  {[
                    { key: 'resource-id', value: inspectedEl.resourceId, mono: true },
                    { key: 'content-desc', value: inspectedEl.accessId, mono: false },
                    { key: 'text', value: inspectedEl.text, mono: false },
                    { key: 'bounds', value: inspectedEl.bounds ?? '—', mono: true },
                    { key: 'enabled', value: 'true', mono: false, bool: true, positive: true },
                    { key: 'clickable', value: (inspectedEl.elType === 'btn' || inspectedEl.elType === 'input') ? 'true' : 'false', mono: false, bool: true, positive: inspectedEl.elType === 'btn' || inspectedEl.elType === 'input' },
                    { key: 'displayed', value: 'true', mono: false, bool: true, positive: true },
                    { key: 'XPath', value: deriveXPath(inspectedEl), mono: true },
                  ].map(({ key, value, mono, bool, positive }, i, arr) => (
                    <div
                      key={key}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '7px 12px',
                        borderBottom: i < arr.length - 1 ? '1px solid rgba(255,255,255,0.04)' : 'none',
                        background: i % 2 === 0 ? 'rgba(255,255,255,0.01)' : 'transparent',
                        gap: 8,
                      }}
                    >
                      <span
                        style={{
                          fontSize: 10,
                          color: '#475569',
                          fontFamily: 'monospace',
                          flexShrink: 0,
                          minWidth: 72,
                        }}
                      >
                        {key}
                      </span>
                      <span
                        style={{
                          fontSize: 10,
                          color: bool
                            ? (positive ? '#34d399' : '#f43f5e')
                            : mono ? '#93c5fd' : '#e2e8f0',
                          fontFamily: mono ? 'monospace' : 'inherit',
                          textAlign: 'right',
                          wordBreak: 'break-all',
                          flex: 1,
                        }}
                      >
                        {value}
                      </span>
                    </div>
                  ))}
                </div>

                {/* Copy Locator button */}
                <button
                  onClick={() => copyLocator(inspectedEl.resourceId, 'inspector-main')}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: 6,
                    width: '100%',
                    padding: '9px 0',
                    background: copiedLocator === 'inspector-main'
                      ? 'rgba(52,211,153,0.12)'
                      : 'rgba(20,184,166,0.08)',
                    border: `1px solid ${copiedLocator === 'inspector-main' ? '#34d399' : 'rgba(20,184,166,0.25)'}`,
                    borderRadius: 8,
                    color: copiedLocator === 'inspector-main' ? '#34d399' : '#14b8a6',
                    fontSize: 11,
                    fontWeight: 600,
                    cursor: 'pointer',
                    transition: 'all 0.15s',
                  }}
                >
                  {copiedLocator === 'inspector-main' ? <Check size={12} /> : <Copy size={12} />}
                  {copiedLocator === 'inspector-main' ? 'Copiado' : 'Copiar Locator'}
                </button>
              </>
            )}
          </div>
        )}

        {/* ── Locators tab ── */}
        {viewTab === 'locators' && (
          <div style={{ padding: '14px 14px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            {!inspectedEl ? (
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '40px 24px',
                  gap: 12,
                  textAlign: 'center',
                }}
              >
                <div
                  style={{
                    width: 48,
                    height: 48,
                    borderRadius: 14,
                    background: 'rgba(99,102,241,0.08)',
                    border: '1px solid rgba(99,102,241,0.18)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                >
                  <Link2 size={22} color="rgba(99,102,241,0.5)" />
                </div>
                <p style={{ color: '#475569', fontSize: 12, fontWeight: 600, margin: 0 }}>
                  Estrategia de Locators
                </p>
                <p style={{ color: '#334155', fontSize: 11, margin: 0, lineHeight: 1.5 }}>
                  Haz clic en un paso para ver los locators disponibles.
                </p>
              </div>
            ) : (() => {
              const locatorRows = [
                {
                  strategy: 'accessibilityId',
                  value: inspectedEl.accessId,
                  recommended: true,
                  warn: false,
                  appium: `MobileBy.accessibilityId("${inspectedEl.accessId}")`,
                },
                {
                  strategy: 'resource-id',
                  value: inspectedEl.resourceId,
                  recommended: true,
                  warn: false,
                  appium: `By.id("${inspectedEl.resourceId}")`,
                },
                {
                  strategy: 'content-desc',
                  value: inspectedEl.accessId,
                  recommended: false,
                  warn: false,
                  appium: `By.description("${inspectedEl.accessId}")`,
                },
                {
                  strategy: 'text',
                  value: inspectedEl.text,
                  recommended: false,
                  warn: false,
                  appium: `By.text("${inspectedEl.text}")`,
                },
                {
                  strategy: 'xpath',
                  value: deriveXPath(inspectedEl),
                  recommended: false,
                  warn: true,
                  appium: `By.xpath("${deriveXPath(inspectedEl)}")`,
                },
              ]
              return (
                <>
                  <p style={{ margin: 0, color: '#475569', fontSize: 10, fontWeight: 600, letterSpacing: 0.5 }}>
                    LOCATORS — {inspectedEl.shortId}
                  </p>
                  {locatorRows.map(({ strategy, value, recommended, warn, appium }, i) => (
                    <div
                      key={strategy}
                      style={{
                        borderRadius: 9,
                        border: `1px solid ${recommended ? 'rgba(52,211,153,0.15)' : warn ? 'rgba(245,158,11,0.15)' : 'rgba(255,255,255,0.06)'}`,
                        background: recommended ? 'rgba(52,211,153,0.04)' : 'rgba(255,255,255,0.02)',
                        padding: '10px 12px',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 5,
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                        <span style={{ fontSize: 10, color: warn ? '#f59e0b' : '#64748b', fontFamily: 'monospace', fontWeight: 600 }}>
                          {strategy}
                        </span>
                        <div style={{ display: 'flex', gap: 5, alignItems: 'center' }}>
                          {recommended && (
                            <span style={{ fontSize: 9, color: '#34d399', background: 'rgba(52,211,153,0.1)', border: '1px solid rgba(52,211,153,0.2)', padding: '1px 6px', borderRadius: 20 }}>
                              recomendado
                            </span>
                          )}
                          {warn && (
                            <span style={{ fontSize: 9, color: '#f59e0b', background: 'rgba(245,158,11,0.1)', border: '1px solid rgba(245,158,11,0.2)', padding: '1px 6px', borderRadius: 20 }}>
                              evitar
                            </span>
                          )}
                          <button
                            onClick={() => copyLocator(appium, `loc-${strategy}`)}
                            style={{
                              display: 'flex',
                              alignItems: 'center',
                              gap: 3,
                              padding: '3px 7px',
                              background: copiedLocator === `loc-${strategy}` ? 'rgba(52,211,153,0.12)' : 'rgba(255,255,255,0.06)',
                              border: `1px solid ${copiedLocator === `loc-${strategy}` ? '#34d399' : 'rgba(255,255,255,0.1)'}`,
                              borderRadius: 5,
                              color: copiedLocator === `loc-${strategy}` ? '#34d399' : '#64748b',
                              fontSize: 9,
                              cursor: 'pointer',
                              transition: 'all 0.12s',
                            }}
                          >
                            {copiedLocator === `loc-${strategy}` ? <Check size={9} /> : <Copy size={9} />}
                            Copiar
                          </button>
                        </div>
                      </div>
                      <span style={{ fontSize: 9, color: '#334155', fontFamily: 'monospace', wordBreak: 'break-all' }}>
                        {value}
                      </span>
                      <span style={{ fontSize: 9, color: '#1e3a5f', fontFamily: 'monospace', wordBreak: 'break-all', borderTop: '1px solid rgba(255,255,255,0.04)', paddingTop: 4, marginTop: 1 }}>
                        {appium}
                      </span>
                    </div>
                  ))}
                </>
              )
            })()}
          </div>
        )}

        {/* ── XML tab — page source tree ── */}
        {viewTab === 'xml' && (
          <XmlTreeView
            node={xmlTree}
            expanded={xmlExpanded}
            onToggle={key => setXmlExpanded(prev => {
              const next = new Set(prev)
              if (next.has(key)) next.delete(key); else next.add(key)
              return next
            })}
            inspectedElId={inspectedElId}
            onInspect={id => { onInspectEl(id); onViewTabChange('inspector') }}
            depth={0}
          />
        )}

        {/* ── Código tab ── */}
        {viewTab === 'code' && (
          steps.length === 0 ? (
            <div
              style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                gap: 14,
                padding: '32px 24px',
                textAlign: 'center',
              }}
            >
              <div
                style={{
                  width: 56,
                  height: 56,
                  borderRadius: 16,
                  background: 'rgba(99,102,241,0.08)',
                  border: '1px solid rgba(99,102,241,0.15)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <FileCode2 size={24} color="rgba(99,102,241,0.4)" />
              </div>
              <div>
                <p style={{ color: '#475569', fontSize: 13, fontWeight: 600, margin: '0 0 4px' }}>
                  Sin código generado
                </p>
                <p style={{ color: '#334155', fontSize: 11, margin: 0, lineHeight: 1.5 }}>
                  Graba pasos para generar código automáticamente.
                </p>
              </div>
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
                <div key={i} style={{ display: 'flex', padding: '0 8px' }}>
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
                    <SyntaxLine line={line} lang={lang} />
                  </span>
                </div>
              ))}
            </pre>
          )
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
                { key: 'pageObjects',     label: 'Usar Page Objects' },
                { key: 'assertions',      label: 'Generar Assertions' },
                { key: 'smartWaits',      label: 'Agregar Esperas Inteligentes' },
                { key: 'screenshots',     label: 'Incluir Toma de Screenshots' },
                { key: 'allureLogs',      label: 'Generar Logs Allure' },
                { key: 'reusableMethods', label: 'Métodos Reutilizables' },
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

        {/* Action buttons */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6 }}>
          <button
            onClick={onSaveCase}
            title="Guardar como caso de prueba"
            style={{
              padding: '7px 0',
              background: 'rgba(99,102,241,0.12)',
              border: '1px solid rgba(99,102,241,0.3)',
              borderRadius: 6,
              color: '#818cf8',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 5,
              transition: 'all 0.15s',
            }}
          >
            <CheckCircle size={11} />
            Guardar Caso
          </button>
          <button
            onClick={onSaveSuite}
            title="Guardar como suite"
            style={{
              padding: '7px 0',
              background: 'linear-gradient(90deg, rgba(99,102,241,0.2), rgba(129,140,248,0.15))',
              border: '1px solid rgba(99,102,241,0.4)',
              borderRadius: 6,
              color: '#a5b4fc',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 5,
              transition: 'all 0.15s',
            }}
          >
            <Layers3 size={11} />
            Guardar Suite
          </button>
          <button
            onClick={onExecute}
            title="Navegar a Ejecutar Pruebas"
            style={{
              padding: '7px 0',
              background: 'rgba(52,211,153,0.1)',
              border: '1px solid rgba(52,211,153,0.3)',
              borderRadius: 6,
              color: '#34d399',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 5,
              transition: 'all 0.15s',
            }}
          >
            <Play size={11} />
            Ejecutar
          </button>
          <button
            onClick={onExport}
            title="Exportar archivos de prueba"
            style={{
              padding: '7px 0',
              background: 'rgba(234,179,8,0.1)',
              border: '1px solid rgba(234,179,8,0.3)',
              borderRadius: 6,
              color: '#eab308',
              fontSize: 11,
              fontWeight: 600,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              gap: 5,
              transition: 'all 0.15s',
            }}
          >
            <Download size={11} />
            Exportar
          </button>
        </div>
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

interface SaveSuiteModalProps {
  mode: 'caso' | 'suite'
  onClose: () => void
  onConfirm: (data: {
    name: string
    description: string
    country: string
    mode: 'caso' | 'suite'
  }) => void
}

const SAVE_COUNTRIES = [
  { id: 'mexico',    label: 'México',    flag: '🇲🇽' },
  { id: 'argentina', label: 'Argentina', flag: '🇦🇷' },
  { id: 'chile',     label: 'Chile',     flag: '🇨🇱' },
]

function SaveSuiteModal({ mode, onClose, onConfirm }: SaveSuiteModalProps) {
  const [name,        setName]        = useState('')
  const [description, setDescription] = useState('')
  const [country,     setCountry]     = useState('mexico')
  const [saved,       setSaved]       = useState(false)

  const isSuite = mode === 'suite'
  const title   = isSuite ? 'Guardar como Suite' : 'Guardar como Caso de Prueba'
  const accent  = isSuite ? '#818cf8' : '#6366f1'

  const handleSave = () => {
    if (!name.trim()) return
    onConfirm({ name: name.trim(), description: description.trim(), country, mode })
    setSaved(true)
    setTimeout(onClose, 1400)
  }

  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: 'rgba(0,0,0,0.7)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        zIndex: 300,
      }}
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.92, y: 12 }}
        animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.92, y: 12 }}
        onClick={(e) => e.stopPropagation()}
        style={{
          backgroundColor: '#111827',
          border: `1px solid ${accent}44`,
          borderRadius: 14,
          padding: 28,
          width: 420,
          display: 'flex',
          flexDirection: 'column',
          gap: 16,
          boxShadow: `0 20px 60px rgba(0,0,0,0.6), 0 0 0 1px ${accent}22`,
        }}
      >
        {saved ? (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 12, padding: '12px 0' }}>
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 320 }}
            >
              <Check size={40} color="#34d399" />
            </motion.div>
            <p style={{ color: '#34d399', fontWeight: 700, fontSize: 15, margin: 0 }}>
              ¡{isSuite ? 'Suite guardada' : 'Caso guardado'} exitosamente!
            </p>
            {isSuite && (
              <p style={{ color: '#64748b', fontSize: 11, margin: 0, textAlign: 'center' }}>
                Aparecerá automáticamente en Suites, Dashboard y Ejecutar Pruebas.
              </p>
            )}
          </div>
        ) : (
          <>
            {/* Header */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <div style={{
                  width: 30, height: 30, borderRadius: 8,
                  background: `${accent}22`, border: `1px solid ${accent}44`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                }}>
                  {isSuite ? <Layers3 size={14} color={accent} /> : <CheckCircle size={14} color={accent} />}
                </div>
                <span style={{ color: '#f1f5f9', fontWeight: 700, fontSize: 14 }}>{title}</span>
              </div>
              <button onClick={onClose} style={{ color: '#475569', background: 'none', border: 'none', cursor: 'pointer', padding: 4 }}>
                <X size={16} />
              </button>
            </div>

            {/* Name */}
            <div>
              <label style={{ display: 'block', color: '#94a3b8', fontSize: 11, marginBottom: 5, fontWeight: 600 }}>
                Nombre *
              </label>
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder={isSuite ? 'Flujo de Compra Cinépolis' : 'test_login_exitoso'}
                autoFocus
                style={{
                  width: '100%',
                  backgroundColor: '#0d1117',
                  border: `1px solid ${name.trim() ? accent + '55' : 'rgba(255,255,255,0.1)'}`,
                  borderRadius: 7,
                  color: '#e2e8f0',
                  padding: '8px 11px',
                  fontSize: 12,
                  boxSizing: 'border-box',
                  outline: 'none',
                  transition: 'border-color 0.15s',
                }}
              />
            </div>

            {/* Description */}
            <div>
              <label style={{ display: 'block', color: '#94a3b8', fontSize: 11, marginBottom: 5, fontWeight: 600 }}>
                Descripción
              </label>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Descripción breve del flujo grabado..."
                rows={2}
                style={{
                  width: '100%',
                  backgroundColor: '#0d1117',
                  border: '1px solid rgba(255,255,255,0.1)',
                  borderRadius: 7,
                  color: '#e2e8f0',
                  padding: '8px 11px',
                  fontSize: 12,
                  boxSizing: 'border-box',
                  resize: 'none',
                  fontFamily: 'inherit',
                  outline: 'none',
                }}
              />
            </div>

            {/* Country (only for suites — to know which Execute tab it appears under) */}
            {isSuite && (
              <div>
                <label style={{ display: 'block', color: '#94a3b8', fontSize: 11, marginBottom: 7, fontWeight: 600 }}>
                  País / Región
                </label>
                <div style={{ display: 'flex', gap: 8 }}>
                  {SAVE_COUNTRIES.map((c) => (
                    <button
                      key={c.id}
                      onClick={() => setCountry(c.id)}
                      style={{
                        flex: 1,
                        padding: '7px 0',
                        borderRadius: 7,
                        fontSize: 11,
                        fontWeight: country === c.id ? 700 : 500,
                        cursor: 'pointer',
                        border: `1px solid ${country === c.id ? accent : 'rgba(255,255,255,0.1)'}`,
                        background: country === c.id ? `${accent}18` : 'rgba(255,255,255,0.03)',
                        color: country === c.id ? accent : '#64748b',
                        transition: 'all 0.15s',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: 4,
                      }}
                    >
                      <span>{c.flag}</span>
                      <span>{c.label}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* Save button */}
            <button
              onClick={handleSave}
              disabled={!name.trim()}
              style={{
                width: '100%',
                padding: '10px 0',
                background: name.trim()
                  ? `linear-gradient(90deg, ${accent}, #a5b4fc)`
                  : 'rgba(255,255,255,0.05)',
                border: 'none',
                borderRadius: 8,
                color: name.trim() ? '#fff' : '#475569',
                fontSize: 13,
                fontWeight: 700,
                cursor: name.trim() ? 'pointer' : 'not-allowed',
                marginTop: 2,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 7,
                transition: 'all 0.15s',
              }}
            >
              {isSuite ? <Layers3 size={13} /> : <CheckCircle size={13} />}
              {isSuite ? 'Guardar Suite' : 'Guardar Caso de Prueba'}
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
  sub?: string | null
  statusBadge?: 'available' | 'busy' | 'offline' | null
  active: boolean
  options: string[]
  onSelect: (val: string) => void
  placeholder?: string
  icon?: React.ReactNode
}

const STATUS_BADGE: Record<string, { label: string; color: string; bg: string }> = {
  available: { label: 'Disponible', color: '#4ade80', bg: 'rgba(74,222,128,0.12)' },
  busy:      { label: 'Ocupado',    color: '#f59e0b', bg: 'rgba(245,158,11,0.12)' },
  offline:   { label: 'Offline',   color: '#64748b', bg: 'rgba(100,116,139,0.12)' },
}

function HeaderStepPill({
  n, label, value, sub, statusBadge, active, options, onSelect, icon,
}: HeaderStepProps) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [])

  const badge = statusBadge ? STATUS_BADGE[statusBadge] : null

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        onClick={() => options.length > 0 && setOpen((p) => !p)}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          padding: '8px 12px',
          borderRadius: 8,
          backgroundColor: active ? 'rgba(99,102,241,0.1)' : 'rgba(255,255,255,0.04)',
          border: `1px solid ${active ? 'rgba(99,102,241,0.35)' : 'rgba(255,255,255,0.08)'}`,
          cursor: options.length > 0 ? 'pointer' : 'default',
          color: '#d4d4d4',
          transition: 'all 0.15s',
          minWidth: 170,
          textAlign: 'left',
        }}
      >
        {/* Step number badge */}
        <div
          style={{
            width: 22,
            height: 22,
            borderRadius: '50%',
            backgroundColor: active ? '#6366f1' : 'rgba(255,255,255,0.08)',
            border: active ? 'none' : '1px solid rgba(255,255,255,0.12)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: active ? '#fff' : '#555',
            fontSize: 11,
            fontWeight: 700,
            flexShrink: 0,
          }}
        >
          {n}
        </div>

        {/* Optional icon */}
        {icon && (
          <div style={{ flexShrink: 0, opacity: active ? 1 : 0.4 }}>{icon}</div>
        )}

        {/* Text block */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 9, color: '#5c6370', lineHeight: 1, marginBottom: 3 }}>
            {label}
          </div>
          <div
            style={{
              fontSize: 12,
              fontWeight: 600,
              color: value ? '#e2e8f0' : '#475569',
              lineHeight: 1,
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: 140,
            }}
          >
            {value ?? 'Seleccionar'}
          </div>
          {sub && (
            <div
              style={{
                fontSize: 9,
                color: '#475569',
                lineHeight: 1,
                marginTop: 3,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
                maxWidth: 140,
              }}
            >
              {sub}
            </div>
          )}
        </div>

        {/* Status badge */}
        {badge && (
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              padding: '2px 7px',
              borderRadius: 10,
              backgroundColor: badge.bg,
              flexShrink: 0,
            }}
          >
            <div
              style={{
                width: 5,
                height: 5,
                borderRadius: '50%',
                backgroundColor: badge.color,
              }}
            />
            <span style={{ fontSize: 9, color: badge.color, fontWeight: 600 }}>
              {badge.label}
            </span>
          </div>
        )}

        {options.length > 0 && (
          <ChevronDown size={11} color="#4b5563" style={{ flexShrink: 0 }} />
        )}
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
              backgroundColor: '#161b22',
              border: '1px solid rgba(255,255,255,0.1)',
              borderRadius: 8,
              minWidth: 200,
              zIndex: 200,
              boxShadow: '0 12px 32px rgba(0,0,0,0.5)',
              overflow: 'hidden',
            }}
          >
            {options.map((opt) => (
              <button
                key={opt}
                onClick={() => { onSelect(opt); setOpen(false) }}
                style={{
                  display: 'block',
                  width: '100%',
                  textAlign: 'left',
                  padding: '8px 14px',
                  fontSize: 12,
                  color: '#d4d4d4',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  borderBottom: '1px solid rgba(255,255,255,0.04)',
                }}
                onMouseEnter={(e) => {
                  ;(e.currentTarget as HTMLButtonElement).style.backgroundColor = 'rgba(99,102,241,0.12)'
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

// ─── Device Info Row ──────────────────────────────────────────────────────────

function DeviceInfoRow({
  label,
  value,
  valueColor = '#94a3b8',
  mono = false,
}: {
  label: string
  value: string
  valueColor?: string
  mono?: boolean
}) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '5px 12px',
      }}
    >
      <span style={{ fontSize: 10, color: '#475569' }}>{label}</span>
      <span
        style={{
          fontSize: 10,
          color: valueColor,
          fontWeight: 500,
          fontFamily: mono ? 'monospace' : 'inherit',
        }}
      >
        {value}
      </span>
    </div>
  )
}

// ─── Main Component ───────────────────────────────────────────────────────────

let _stepCounter = 0

interface RecordStudioProps {
  onNavigateToExecute?: () => void
}

export default function RecordStudio({ onNavigateToExecute }: RecordStudioProps = {}) {
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
    pageObjects:     true,
    assertions:      false,
    smartWaits:      true,
    screenshots:     false,
    allureLogs:      false,
    reusableMethods: false,
  })
  const [testName, setTestName] = useState('testLoginFlow')
  const [className, setClassName] = useState('CinepolisTest')
  const [showSave, setShowSave] = useState<'caso' | 'suite' | null>(null)
  const [copied, setCopied] = useState(false)
  const [sessionStart, setSessionStart] = useState<Date | null>(null)
  const [infoExpanded, setInfoExpanded] = useState(true)
  // ── Inspector state ───────────────────────────────────────────────────────
  const [selectedStepId, setSelectedStepId] = useState<string | null>(null)
  const [inspectedElId, setInspectedElId] = useState<string | null>(null)
  // ── Live device mirror — direct MJPEG from Runner (port 8082) ────────────
  const { url: previewUrl, state: previewState } = useMirrorStream(selectedDevice?.udid ?? null)
  // ── Recording session (Runner recording engine on port 8082) ──────────────
  const { sessionId, deviceWidth, deviceHeight, start: startSession, stop: stopSession, send: sendStep, onPhysicalStep } = useRecordingSession()
  // ── Device viewer state ────────────────────────────────────────────────────
  const [isLandscape, setIsLandscape] = useState(false)
  const [isVideoRecording, setIsVideoRecording] = useState(false)
  const [captureFlash, setCaptureFlash] = useState(false)
  const [deviceFps] = useState(60)
  const [deviceBattery] = useState(87)

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

  // ── Step type counts ───────────────────────────────────────────────────────
  const tapCount = useMemo(
    () => steps.filter(s => s.type === 'tap' || s.type === 'double_tap' || s.type === 'long_press').length,
    [steps],
  )
  const scrollCount = useMemo(
    () => steps.filter(s => s.type === 'scroll').length,
    [steps],
  )
  const inputCount = useMemo(
    () => steps.filter(s => s.type === 'input').length,
    [steps],
  )

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

  /** Convert a raw step object from the Runner API into a RecStep. */
  const mapApiStep = useCallback((raw: unknown): RecStep => {
    const s = raw as {
      id: string; n: number; type: string
      el: AppEl | null; inputVal?: string
      dir?: string; timeStr: string
    }
    _stepCounter = Math.max(_stepCounter, s.n)
    return {
      id:       s.id,
      n:        s.n,
      type:     s.type as StepType,
      el:       s.el ?? null,
      inputVal: s.inputVal,
      dir:      s.dir as RecStep['dir'],
      timeStr:  s.timeStr,
    }
  }, [])

  // Wire SSE physical-device events → steps panel
  useEffect(() => {
    onPhysicalStep((raw) => {
      setSteps(prev => [...prev, mapApiStep(raw)])
    })
  }, [onPhysicalStep, mapApiStep])

  const handleToggleRecording = useCallback(async () => {
    if (recState === 'idle') {
      setRecState('recording')
      setSessionStart(new Date())
      setElapsed(0)
      if (selectedDevice?.udid) {
        try {
          await startSession(selectedDevice.udid)
        } catch (e) {
          // Runner not reachable — recording works in local-only mode (manual steps)
          console.warn('[RecordStudio] Runner recording session unavailable:', e)
        }
      }
    } else {
      stopSession()
      setRecState('idle')
    }
  }, [recState, selectedDevice, startSession, stopSession])

  /**
   * Handles taps/swipes from the interactive overlay on the live device mirror.
   * Converts normalized container coords → device pixel coords using the
   * objectFit:cover mapping (scale to width for portrait devices).
   */
  const handleScreenInteract = useCallback(
    async (
      nx: number, ny: number,
      gesture: 'tap' | 'swipe' | 'long_press',
      nx2?: number, ny2?: number,
    ) => {
      if (!sessionId || recState !== 'recording') return

      const CONTAINER_W = 262, CONTAINER_H = 452
      const scaleX = CONTAINER_W / deviceWidth
      const scaleY = CONTAINER_H / deviceHeight
      const scale  = Math.max(scaleX, scaleY)                    // objectFit: cover
      const offsetX = (CONTAINER_W - deviceWidth  * scale) / 2  // 0 for portrait
      const offsetY = (CONTAINER_H - deviceHeight * scale) / 2  // negative for tall phones

      const toDevice = (normX: number, normY: number) => ({
        x: Math.round(Math.max(0, Math.min(deviceWidth,  (normX * CONTAINER_W - offsetX) / scale))),
        y: Math.round(Math.max(0, Math.min(deviceHeight, (normY * CONTAINER_H - offsetY) / scale))),
      })

      const { x, y } = toDevice(nx, ny)

      let action: RecordingAction
      if (gesture === 'swipe' && nx2 !== undefined && ny2 !== undefined) {
        const end = toDevice(nx2, ny2)
        action = { action: 'swipe', x1: x, y1: y, x2: end.x, y2: end.y }
      } else if (gesture === 'long_press') {
        action = { action: 'long_press', x, y }
      } else {
        action = { action: 'tap', x, y }
      }

      const raw = await sendStep(action)
      if (raw) setSteps(prev => [...prev, mapApiStep(raw)])
    },
    [sessionId, recState, deviceWidth, deviceHeight, sendStep, mapApiStep],
  )

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

  const handleDuplicateStep = useCallback((id: string) => {
    setSteps((prev) => {
      const idx = prev.findIndex(s => s.id === id)
      if (idx === -1) return prev
      const original = prev[idx]
      const clone: RecStep = {
        ...original,
        id: `step_${Date.now()}_dup`,
        n: 0,
      }
      const next = [...prev.slice(0, idx + 1), clone, ...prev.slice(idx + 1)]
      return next.map((s, i) => ({ ...s, n: i + 1 }))
    })
  }, [])

  const handleMoveStep = useCallback((id: string, dir: 'up' | 'down') => {
    setSteps((prev) => {
      const idx = prev.findIndex(s => s.id === id)
      if (idx === -1) return prev
      const target = dir === 'up' ? idx - 1 : idx + 1
      if (target < 0 || target >= prev.length) return prev
      const next = [...prev]
      ;[next[idx], next[target]] = [next[target], next[idx]]
      return next.map((s, i) => ({ ...s, n: i + 1 }))
    })
  }, [])

  const handleSelectStep = useCallback((step: RecStep) => {
    setSelectedStepId(step.id)
    setInspectedElId(step.el?.shortId ?? null)
    setViewTab('inspector')
  }, [])

  const handleEditStep = useCallback((id: string, updates: { elementId?: string; inputVal?: string; dir?: 'up' | 'down' | 'left' | 'right' }) => {
    setSteps((prev) =>
      prev.map(s => {
        if (s.id !== id) return s
        return {
          ...s,
          inputVal: updates.inputVal ?? s.inputVal,
          dir: updates.dir ?? s.dir,
          el: updates.elementId && s.el
            ? { ...s.el, shortId: updates.elementId, resourceId: updates.elementId, accessId: updates.elementId }
            : s.el,
        }
      })
    )
  }, [])

  // ── Code generation ────────────────────────────────────────────────────────
  const generatedCode = useMemo(
    () =>
      generateCode(
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
    a.download = viewTab === 'code'
      ? `${className || 'GeneratedTest'}.${getLangFileExt(lang)}`
      : 'recording.xml'
    a.click()
    URL.revokeObjectURL(url)
  }, [viewTab, generatedCode, generatedXML, className])

  // ── Save ───────────────────────────────────────────────────────────────────
  const handleSave = useCallback(
    (data: { name: string; description: string; country: string; mode: 'caso' | 'suite' }) => {
      const sessionId = `session_${Date.now()}`

      // Always save to session history
      const sessions = JSON.parse(localStorage.getItem('qa_record_sessions') ?? '[]') as unknown[]
      sessions.push({
        id: sessionId,
        name: data.name,
        description: data.description,
        mode: data.mode,
        country: data.country,
        savedAt: new Date().toISOString(),
        stepCount: steps.length,
        lang,
        code: generatedCode,
        xml: generatedXML,
      })
      localStorage.setItem('qa_record_sessions', JSON.stringify(sessions))

      // When saving as suite, also write to qa_custom_suites so it appears in Execute/Dashboard
      if (data.mode === 'suite') {
        const suites = JSON.parse(localStorage.getItem('qa_custom_suites') ?? '[]') as unknown[]
        const SUITE_ICONS = ['🎬', '🎭', '🎪', '🎨', '🎯', '🎮', '🎲', '🎰', '🎳', '🎻']
        const SUITE_ACCENTS = ['#6366f1', '#8b5cf6', '#ec4899', '#f59e0b', '#10b981', '#3b82f6', '#14b8a6', '#f43f5e']
        const iconIdx = (suites.length) % SUITE_ICONS.length
        const accentIdx = (suites.length) % SUITE_ACCENTS.length
        suites.push({
          id: sessionId,
          country: data.country,
          title: data.name,
          description: data.description || `Suite generada con ${steps.length} paso${steps.length !== 1 ? 's' : ''}`,
          icon: SUITE_ICONS[iconIdx],
          accent: SUITE_ACCENTS[accentIdx],
          stepCount: steps.length,
          savedAt: new Date().toISOString(),
        })
        localStorage.setItem('qa_custom_suites', JSON.stringify(suites))
      }
    },
    [steps.length, generatedCode, generatedXML, lang],
  )

  // ── Export ─────────────────────────────────────────────────────────────────
  const handleExport = useCallback(() => {
    // Download test file
    const testBlob = new Blob([generatedCode], { type: 'text/plain' })
    const testUrl  = URL.createObjectURL(testBlob)
    const testA    = document.createElement('a')
    testA.href     = testUrl
    testA.download = `${className || 'GeneratedTest'}.${getLangFileExt(lang)}`
    testA.click()
    URL.revokeObjectURL(testUrl)

    // Download XML recording
    const xmlBlob = new Blob([generatedXML], { type: 'application/xml' })
    const xmlUrl  = URL.createObjectURL(xmlBlob)
    const xmlA    = document.createElement('a')
    xmlA.href     = xmlUrl
    xmlA.download = `${className || 'GeneratedTest'}_recording.xml`
    xmlA.click()
    URL.revokeObjectURL(xmlUrl)
  }, [generatedCode, generatedXML, className, lang])

  // ── Dropdown data ──────────────────────────────────────────────────────────
  const deviceNames = useMemo(() => devices.map((d) => d.deviceName), [devices])
  const appNames = useMemo(
    () => [...new Set(Object.values(appConfigs).map((c) => c.appName))],
    [appConfigs],
  )
  const modes = ['INSTALLED', 'APK', 'IPA']

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <>
    <style>{`
      @keyframes pulse { 0%,100% { opacity: 0.85; transform: scale(1); } 50% { opacity: 0.4; transform: scale(1.4); } }
      @keyframes spin  { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
    `}</style>
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
      {/* ── Configuration header (title + 4 cards) ── */}
      <RecordStudioHeader
        devices={devices}
        selectedDevice={selectedDevice}
        onSelectDevice={handleSelectDevice}
        appConfigs={appConfigs}
        appConfig={appConfig}
        onSelectApp={handleSelectApp}
        appMode={appMode}
        onSelectMode={handleSelectMode}
        isRecording={recState === 'recording'}
        elapsed={elapsed}
        onToggleRecording={handleToggleRecording}
      />

      {/* ── Recording Bar ── */}
      <AnimatePresence>
        {recState === 'recording' && (
          <motion.div
            key="recording-bar"
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.22, ease: 'easeOut' }}
            style={{ overflow: 'hidden', flexShrink: 0, zIndex: 9 }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                padding: '0 20px',
                height: 48,
                gap: 0,
                background: 'linear-gradient(90deg, rgba(239,68,68,0.08) 0%, rgba(13,17,23,0.95) 60%)',
                borderBottom: '1px solid rgba(239,68,68,0.2)',
                borderLeft: '3px solid #ef4444',
                position: 'relative',
                overflow: 'hidden',
              }}
            >
              {/* Subtle scan line */}
              <motion.div
                animate={{ x: ['-100%', '200%'] }}
                transition={{ repeat: Infinity, duration: 3, ease: 'linear' }}
                style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  width: '30%',
                  height: '100%',
                  background: 'linear-gradient(90deg, transparent, rgba(239,68,68,0.04), transparent)',
                  pointerEvents: 'none',
                }}
              />

              {/* 🔴 Indicator + label */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginRight: 20 }}>
                <motion.div
                  animate={{ scale: [1, 1.3, 1], opacity: [1, 0.6, 1] }}
                  transition={{ repeat: Infinity, duration: 1.1, ease: 'easeInOut' }}
                  style={{
                    width: 10,
                    height: 10,
                    borderRadius: '50%',
                    backgroundColor: '#ef4444',
                    boxShadow: '0 0 8px rgba(239,68,68,0.8)',
                  }}
                />
                <span
                  style={{
                    color: '#ef4444',
                    fontWeight: 800,
                    fontSize: 12,
                    letterSpacing: 1.5,
                  }}
                >
                  GRABANDO
                </span>
              </div>

              {/* Separator */}
              <div style={{ width: 1, height: 24, background: 'rgba(255,255,255,0.06)', marginRight: 20 }} />

              {/* ⏱ Timer */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 5, marginRight: 20 }}>
                <Clock size={12} color="#64748b" />
                <span
                  style={{
                    fontFamily: '"JetBrains Mono", "Fira Code", monospace',
                    fontSize: 14,
                    fontWeight: 700,
                    color: '#e2e8f0',
                    minWidth: 58,
                    letterSpacing: 1,
                  }}
                >
                  {elapsedStr}
                </span>
              </div>

              {/* Separator */}
              <div style={{ width: 1, height: 24, background: 'rgba(255,255,255,0.06)', marginRight: 20 }} />

              {/* Metrics */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 16, flex: 1 }}>
                {[
                  {
                    label: 'Pasos',
                    value: steps.length,
                    color: '#818cf8',
                    bg: 'rgba(129,140,248,0.1)',
                    border: 'rgba(129,140,248,0.2)',
                  },
                  {
                    label: 'Taps',
                    value: tapCount,
                    color: '#818cf8',
                    bg: 'rgba(129,140,248,0.08)',
                    border: 'rgba(129,140,248,0.15)',
                  },
                  {
                    label: 'Scroll',
                    value: scrollCount,
                    color: '#60a5fa',
                    bg: 'rgba(96,165,250,0.08)',
                    border: 'rgba(96,165,250,0.15)',
                  },
                  {
                    label: 'Inputs',
                    value: inputCount,
                    color: '#34d399',
                    bg: 'rgba(52,211,153,0.08)',
                    border: 'rgba(52,211,153,0.15)',
                  },
                ].map(({ label, value, color, bg, border }) => (
                  <div key={label} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span style={{ fontSize: 11, color: '#475569' }}>{label}</span>
                    <motion.span
                      key={value}
                      initial={{ scale: 1.3, opacity: 0.6 }}
                      animate={{ scale: 1, opacity: 1 }}
                      transition={{ duration: 0.2 }}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        minWidth: 26,
                        height: 20,
                        borderRadius: 5,
                        background: bg,
                        border: `1px solid ${border}`,
                        color,
                        fontSize: 11,
                        fontWeight: 700,
                        fontFamily: 'monospace',
                        padding: '0 5px',
                      }}
                    >
                      {value}
                    </motion.span>
                  </div>
                ))}
              </div>

              {/* ⏹ Stop button */}
              <button
                onClick={handleToggleRecording}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 7,
                  padding: '8px 18px',
                  background: 'linear-gradient(135deg, rgba(239,68,68,0.2), rgba(220,38,38,0.15))',
                  border: '1px solid rgba(239,68,68,0.45)',
                  borderRadius: 8,
                  color: '#f87171',
                  fontSize: 12,
                  fontWeight: 700,
                  cursor: 'pointer',
                  transition: 'all 0.15s',
                  flexShrink: 0,
                  boxShadow: '0 0 14px rgba(239,68,68,0.1)',
                }}
                onMouseEnter={e => {
                  const b = e.currentTarget as HTMLButtonElement
                  b.style.background = 'linear-gradient(135deg, rgba(239,68,68,0.3), rgba(220,38,38,0.25))'
                  b.style.boxShadow = '0 0 20px rgba(239,68,68,0.2)'
                  b.style.color = '#fca5a5'
                }}
                onMouseLeave={e => {
                  const b = e.currentTarget as HTMLButtonElement
                  b.style.background = 'linear-gradient(135deg, rgba(239,68,68,0.2), rgba(220,38,38,0.15))'
                  b.style.boxShadow = '0 0 14px rgba(239,68,68,0.1)'
                  b.style.color = '#f87171'
                }}
              >
                <Square size={11} />
                Detener Grabación
              </button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* ── Main body ── */}
      <div
        style={{
          flex: 1,
          display: 'grid',
          gridTemplateColumns: '380px 1fr 460px',
          minHeight: 0,
          overflow: 'hidden',
        }}
      >
        {/* ── Left column: Professional Device Viewer ── */}
        <div
          style={{
            borderRight: '1px solid rgba(255,255,255,0.07)',
            display: 'flex',
            flexDirection: 'column',
            overflowY: 'auto',
            background: 'linear-gradient(180deg, rgba(7,12,28,0) 0%, rgba(4,8,22,0.4) 100%)',
          }}
        >
          {/* ── Header: title + status ── */}
          <div
            style={{
              padding: '12px 16px 10px',
              borderBottom: '1px solid rgba(255,255,255,0.06)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexShrink: 0,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div
                style={{
                  width: 30,
                  height: 30,
                  borderRadius: 8,
                  background: 'linear-gradient(135deg, rgba(99,102,241,0.2), rgba(129,140,248,0.1))',
                  border: '1px solid rgba(99,102,241,0.3)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                }}
              >
                <Smartphone size={15} color="#818cf8" />
              </div>
              <div>
                <p style={{ margin: 0, color: '#e2e8f0', fontSize: 12, fontWeight: 700, lineHeight: 1.2 }}>
                  Dispositivo en Vivo
                </p>
                <p style={{ margin: 0, color: '#475569', fontSize: 10, lineHeight: 1.4 }}>
                  {selectedDevice ? selectedDevice.deviceName : 'Sin dispositivo seleccionado'}
                </p>
              </div>
            </div>
            {/* Connection status indicator */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <motion.div
                animate={{ opacity: [1, 0.3, 1] }}
                transition={{ repeat: Infinity, duration: 2.2, ease: 'easeInOut' }}
                style={{
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  backgroundColor: selectedDevice ? '#4ade80' : '#475569',
                }}
              />
              <span style={{ fontSize: 10, color: selectedDevice ? '#4ade80' : '#475569', fontWeight: 500 }}>
                {selectedDevice ? 'Conectado' : 'Sin conexión'}
              </span>
            </div>
          </div>

          {/* ── Professional toolbar: 5 action buttons ── */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 4,
              padding: '8px 14px',
              borderBottom: '1px solid rgba(255,255,255,0.05)',
              flexShrink: 0,
              background: 'rgba(0,0,0,0.15)',
            }}
          >
            {/* Capture */}
            <button
              title="Capturar pantalla"
              onClick={() => {
                setCaptureFlash(true)
                setTimeout(() => setCaptureFlash(false), 300)
              }}
              style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 3,
                padding: '6px 4px',
                background: captureFlash
                  ? 'rgba(99,102,241,0.2)'
                  : 'rgba(255,255,255,0.04)',
                border: `1px solid ${captureFlash ? 'rgba(99,102,241,0.5)' : 'rgba(255,255,255,0.07)'}`,
                borderRadius: 7,
                cursor: 'pointer',
                color: captureFlash ? '#818cf8' : '#64748b',
                transition: 'all 0.15s',
              }}
            >
              <Camera size={13} />
              <span style={{ fontSize: 9, fontWeight: 500 }}>Captura</span>
            </button>

            {/* Video */}
            <button
              title={isVideoRecording ? 'Detener video' : 'Grabar video'}
              onClick={() => setIsVideoRecording(v => !v)}
              style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 3,
                padding: '6px 4px',
                background: isVideoRecording
                  ? 'rgba(239,68,68,0.15)'
                  : 'rgba(255,255,255,0.04)',
                border: `1px solid ${isVideoRecording ? 'rgba(239,68,68,0.5)' : 'rgba(255,255,255,0.07)'}`,
                borderRadius: 7,
                cursor: 'pointer',
                color: isVideoRecording ? '#ef4444' : '#64748b',
                transition: 'all 0.2s',
              }}
            >
              {isVideoRecording ? (
                <motion.div animate={{ opacity: [1, 0.4, 1] }} transition={{ repeat: Infinity, duration: 1 }}>
                  <Square size={13} />
                </motion.div>
              ) : (
                <Video size={13} />
              )}
              <span style={{ fontSize: 9, fontWeight: 500 }}>
                {isVideoRecording ? 'Detener' : 'Video'}
              </span>
            </button>

            {/* Rotate */}
            <button
              title="Rotar dispositivo"
              onClick={() => setIsLandscape(l => !l)}
              style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 3,
                padding: '6px 4px',
                background: isLandscape
                  ? 'rgba(99,102,241,0.15)'
                  : 'rgba(255,255,255,0.04)',
                border: `1px solid ${isLandscape ? 'rgba(99,102,241,0.4)' : 'rgba(255,255,255,0.07)'}`,
                borderRadius: 7,
                cursor: 'pointer',
                color: isLandscape ? '#818cf8' : '#64748b',
                transition: 'all 0.2s',
              }}
            >
              <RotateCw size={13} />
              <span style={{ fontSize: 9, fontWeight: 500 }}>Rotar</span>
            </button>

            {/* Refresh */}
            <button
              title="Actualizar pantalla"
              onClick={() => setScreen('home')}
              style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 3,
                padding: '6px 4px',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.07)',
                borderRadius: 7,
                cursor: 'pointer',
                color: '#64748b',
                transition: 'all 0.15s',
              }}
              onMouseEnter={e => {
                const b = e.currentTarget as HTMLButtonElement
                b.style.background = 'rgba(255,255,255,0.08)'
                b.style.color = '#94a3b8'
              }}
              onMouseLeave={e => {
                const b = e.currentTarget as HTMLButtonElement
                b.style.background = 'rgba(255,255,255,0.04)'
                b.style.color = '#64748b'
              }}
            >
              <RotateCcw size={13} />
              <span style={{ fontSize: 9, fontWeight: 500 }}>Actualizar</span>
            </button>

            {/* Fullscreen */}
            <button
              title="Pantalla completa"
              style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: 3,
                padding: '6px 4px',
                background: 'rgba(255,255,255,0.04)',
                border: '1px solid rgba(255,255,255,0.07)',
                borderRadius: 7,
                cursor: 'pointer',
                color: '#64748b',
                transition: 'all 0.15s',
              }}
              onMouseEnter={e => {
                const b = e.currentTarget as HTMLButtonElement
                b.style.background = 'rgba(255,255,255,0.08)'
                b.style.color = '#94a3b8'
              }}
              onMouseLeave={e => {
                const b = e.currentTarget as HTMLButtonElement
                b.style.background = 'rgba(255,255,255,0.04)'
                b.style.color = '#64748b'
              }}
            >
              <Maximize2 size={13} />
              <span style={{ fontSize: 9, fontWeight: 500 }}>Pantalla completa</span>
            </button>
          </div>

          {/* ── Phone frame area ── */}
          <div
            style={{
              flex: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              padding: isLandscape ? '10px 16px' : '16px',
              minHeight: isLandscape ? 220 : 360,
              overflow: 'hidden',
              position: 'relative',
            }}
          >
            {/* Capture flash overlay */}
            <AnimatePresence>
              {captureFlash && (
                <motion.div
                  initial={{ opacity: 0.6 }}
                  animate={{ opacity: 0 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.3 }}
                  style={{
                    position: 'absolute',
                    inset: 0,
                    background: '#fff',
                    zIndex: 10,
                    pointerEvents: 'none',
                    borderRadius: 8,
                  }}
                />
              )}
            </AnimatePresence>

            {/* Phone with rotation animation */}
            <motion.div
              animate={{
                rotate: isLandscape ? -90 : 0,
                scale: isLandscape ? 0.58 : 1,
              }}
              transition={{ type: 'spring', stiffness: 200, damping: 24 }}
              style={{ transformOrigin: 'center center' }}
            >
              <PhoneFrame
                recording={recState === 'recording'}
                screen={screen}
                onRecord={handleRecordEl}
                onScreenChange={setScreen}
                isLandscape={isLandscape}
                inspectedElId={inspectedElId ?? undefined}
                previewUrl={previewUrl}
                previewState={previewState}
                onScreenInteract={sessionId ? handleScreenInteract : undefined}
              />
            </motion.div>
          </div>

          {/* ── Device info panel ── */}
          <div
            style={{
              margin: '0 14px 14px',
              borderRadius: 10,
              background: 'rgba(255,255,255,0.025)',
              border: '1px solid rgba(255,255,255,0.07)',
              overflow: 'hidden',
              flexShrink: 0,
            }}
          >
            <div
              style={{
                padding: '7px 12px',
                borderBottom: '1px solid rgba(255,255,255,0.06)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <Wifi size={10} color="#6366f1" />
                <span style={{ fontSize: 10, color: '#475569', fontWeight: 600, letterSpacing: 0.5 }}>
                  INFO DEL DISPOSITIVO
                </span>
              </div>
              <span style={{ fontSize: 9, color: '#334155' }}>
                {selectedDevice?.platform ?? 'ANDROID'}
              </span>
            </div>

            <div style={{ padding: '6px 0' }}>
              {/* Name */}
              <DeviceInfoRow
                label="Nombre"
                value={selectedDevice?.deviceName ?? 'Samsung Galaxy A52'}
                valueColor="#e2e8f0"
              />
              {/* Platform chip */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '5px 12px' }}>
                <span style={{ fontSize: 10, color: '#475569' }}>Plataforma</span>
                <span
                  style={{
                    fontSize: 9,
                    fontWeight: 600,
                    color: (selectedDevice?.platform ?? 'ANDROID') === 'IOS' ? '#a78bfa' : '#34d399',
                    background: (selectedDevice?.platform ?? 'ANDROID') === 'IOS'
                      ? 'rgba(167,139,250,0.1)'
                      : 'rgba(52,211,153,0.1)',
                    border: `1px solid ${(selectedDevice?.platform ?? 'ANDROID') === 'IOS' ? 'rgba(167,139,250,0.25)' : 'rgba(52,211,153,0.25)'}`,
                    padding: '1px 7px',
                    borderRadius: 20,
                  }}
                >
                  {selectedDevice?.platform ?? 'ANDROID'}
                </span>
              </div>
              {/* Version */}
              <DeviceInfoRow
                label="Versión"
                value={
                  (selectedDevice?.platform ?? 'ANDROID') === 'IOS'
                    ? 'iOS 17.4'
                    : 'Android 13'
                }
                valueColor="#94a3b8"
              />
              {/* Resolution */}
              <DeviceInfoRow label="Resolución" value="1080 × 2400" valueColor="#94a3b8" mono />
              {/* FPS */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '5px 12px' }}>
                <span style={{ fontSize: 10, color: '#475569' }}>FPS</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                  <span style={{ fontSize: 10, color: '#60a5fa', fontWeight: 600, fontFamily: 'monospace' }}>
                    {deviceFps}
                  </span>
                  <div style={{ display: 'flex', gap: 2 }}>
                    {[...Array(5)].map((_, i) => (
                      <div
                        key={i}
                        style={{
                          width: 3,
                          height: 4 + i * 2,
                          borderRadius: 1,
                          backgroundColor: i < 4 ? '#60a5fa' : 'rgba(96,165,250,0.3)',
                        }}
                      />
                    ))}
                  </div>
                </div>
              </div>
              {/* Battery */}
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '5px 12px' }}>
                <span style={{ fontSize: 10, color: '#475569' }}>Batería</span>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <div
                    style={{
                      width: 28,
                      height: 12,
                      borderRadius: 3,
                      border: '1px solid rgba(255,255,255,0.15)',
                      position: 'relative',
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        position: 'absolute',
                        left: 1,
                        top: 1,
                        width: `${deviceBattery - 4}%`,
                        height: 'calc(100% - 2px)',
                        background: deviceBattery > 20
                          ? 'linear-gradient(90deg, #34d399, #4ade80)'
                          : '#ef4444',
                        borderRadius: 2,
                        transition: 'width 0.3s',
                      }}
                    />
                  </div>
                  <span style={{ fontSize: 10, color: '#94a3b8', fontFamily: 'monospace' }}>
                    {deviceBattery}%
                  </span>
                </div>
              </div>
              {/* Orientation */}
              <DeviceInfoRow
                label="Orientación"
                value={isLandscape ? 'Landscape' : 'Portrait'}
                valueColor="#94a3b8"
              />
              {/* Pantalla actual */}
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  padding: '5px 12px',
                  borderTop: '1px solid rgba(255,255,255,0.05)',
                  marginTop: 2,
                }}
              >
                <span style={{ fontSize: 10, color: '#475569' }}>Pantalla</span>
                <span style={{ fontSize: 10, color: '#818cf8', fontWeight: 600 }}>
                  {screen === 'home' ? 'Home' : 'Login'}
                </span>
              </div>
            </div>
          </div>

          {/* ── Manual action bar when recording ── */}
          {recState === 'recording' && (
            <div
              style={{
                margin: '0 14px 14px',
                borderRadius: 10,
                background: 'rgba(255,255,255,0.025)',
                border: '1px solid rgba(255,255,255,0.07)',
                padding: '10px 10px 8px',
                flexShrink: 0,
              }}
            >
              <p
                style={{
                  color: '#334155',
                  fontSize: 9,
                  fontWeight: 700,
                  margin: '0 0 7px 2px',
                  letterSpacing: 0.6,
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
            selectedStepId={selectedStepId}
            onDeleteStep={handleDeleteStep}
            onDuplicateStep={handleDuplicateStep}
            onMoveStep={handleMoveStep}
            onEditStep={handleEditStep}
            onSelectStep={handleSelectStep}
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
            currentScreen={screen}
            inspectedElId={inspectedElId}
            onLangChange={setLang}
            onViewTabChange={setViewTab}
            onOptsChange={setOpts}
            onTestNameChange={setTestName}
            onClassNameChange={setClassName}
            onInspectEl={id => { setInspectedElId(id); setViewTab('inspector') }}
            onCopy={handleCopy}
            onDownload={handleDownload}
            onSaveCase={() => setShowSave('caso')}
            onSaveSuite={() => setShowSave('suite')}
            onExecute={() => onNavigateToExecute?.()}
            onExport={handleExport}
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
          <SaveSuiteModal
            mode={showSave}
            onClose={() => setShowSave(null)}
            onConfirm={(data) => {
              handleSave(data)
            }}
          />
        )}
      </AnimatePresence>
    </div>
    </>
  )
}
