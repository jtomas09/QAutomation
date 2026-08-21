/**
 * executableTestGenerator — puente de ejecución real (Suites → Runner).
 *
 * Genera un test JUnit5 mínimo y autocontenido (SIN Page Objects) a partir de
 * los `SuiteStep[]` ya persistidos por SuiteService — el mismo formato que
 * usa un TestCase guardado desde Record Studio. Este es un generador
 * DELIBERADAMENTE SEPARADO del generador de código que se muestra/copia en
 * la pestaña "Código" de Record Studio (RecordStudio.tsx → generateJava) —
 * ese sigue intacto, sin cambios, y sigue usando TestNG/JUnit4 + Page Objects
 * para lectura humana. ESTE genera lo que el Runner realmente compila y
 * ejecuta, así que sigue la convención EXACTA confirmada leyendo el repo de
 * tests real (src/test/java en este monorepo, idéntico al que clona el
 * Runner):
 *   - JUnit 5 (org.junit.jupiter.api), no TestNG.
 *   - package tests.QARecordStudio — carpeta dedicada; el Runner la escribe
 *     y la borra dentro del mismo job (nunca modifica el repo base).
 *   - extends base.BaseTest — mismo `driver` que ya gestiona todo el ciclo
 *     de vida (sesión Appium, launch de la app, etc.).
 *   - Cada paso envuelto en utils.TestSteps.run("N. Label", () -> {...}, driver)
 *     — único punto de instrumentación ya usado por todos los flujos reales
 *     (SeleccionAsientos, etc.): publica automáticamente a
 *     utils.TestFlowEventPublisher, que ya habla al mismo /api/events que
 *     alimenta "Actividad en Tiempo Real" en Dashboard. Ningún mecanismo de
 *     eventos nuevo.
 *   - SIN Page Objects: locators inline con WebDriverWait explícito — evita
 *     a propósito los bugs ya conocidos del otro generador con Page Objects
 *     activos (campos private inaccesibles, imports faltantes).
 */

import type { SuiteStep } from './SuiteService'

type ElLike = NonNullable<SuiteStep['el']>

interface LocatorResult {
  strategy: 'id' | 'accessibility_id' | 'uiautomator' | 'predicate_string' | 'class_chain' | 'xpath'
  value:    string
}

const GENERIC_RESOURCE_ID_RE = [
  /^android\.(view|widget|support|graphics|app)\./i,
  /^androidx\./i,
  /^com\.android\./i,
  /:id\/(container|wrapper|root|frame|layout|view|group|scroll|recycler|pager|page|content|inner|outer|main|body|header|footer|toolbar|nav|tab|cell|row|item|card|panel|box|surface)(_\w+)?$/i,
  /:id\/\d+$/,
]

function isGenericResourceId(id: string): boolean {
  return !id.trim() || GENERIC_RESOURCE_ID_RE.some(p => p.test(id))
}

function extractFromXPath(xpath: string, isIOS: boolean): LocatorResult | null {
  if (!isIOS) {
    const idM = xpath.match(/@resource-id=['"]([^'"]+)['"]/i)
    if (idM && !isGenericResourceId(idM[1])) return { strategy: 'id', value: idM[1] }
    const descM = xpath.match(/@content-desc=['"]([^'"]+)['"]/i)
    if (descM && descM[1].trim()) return { strategy: 'accessibility_id', value: descM[1] }
    const textM = xpath.match(/@text=['"]([^'"]+)['"]/i)
    if (textM && textM[1].trim()) return { strategy: 'uiautomator', value: `new UiSelector().text("${textM[1]}")` }
  } else {
    const labelM = xpath.match(/@label=['"]([^'"]+)['"]/i)
    if (labelM && labelM[1].trim()) return { strategy: 'predicate_string', value: `label == "${labelM[1]}"` }
    const nameM = xpath.match(/@name=['"]([^'"]+)['"]/i)
    if (nameM && nameM[1].trim()) return { strategy: 'accessibility_id', value: nameM[1] }
    const valueM = xpath.match(/@value=['"]([^'"]+)['"]/i)
    if (valueM && valueM[1].trim()) return { strategy: 'predicate_string', value: `value == "${valueM[1]}"` }
  }
  return null
}

/** Misma prioridad por plataforma que RecordStudio.tsx (resolveLocator) — no se duplica la decisión, solo el código puro. */
function resolveLocator(el: ElLike | null | undefined): LocatorResult | null {
  if (!el) return null
  const isIOS = el.platform === 'ios'
  if (!isIOS) {
    if (el.accessId?.trim()) return { strategy: 'accessibility_id', value: el.accessId }
    if (el.resourceId?.trim() && !isGenericResourceId(el.resourceId)) return { strategy: 'id', value: el.resourceId }
    if (el.accessibilityLabel?.trim()) return { strategy: 'accessibility_id', value: el.accessibilityLabel }
    if (el.text?.trim()) return { strategy: 'uiautomator', value: `new UiSelector().text("${el.text}")` }
    if (el.locatorStrategy === 'uiautomator' && el.locatorValue?.trim()) return { strategy: 'uiautomator', value: el.locatorValue }
    if (el.locatorValue?.trim()) return extractFromXPath(el.locatorValue, false) ?? { strategy: 'xpath', value: el.locatorValue }
  } else {
    if (el.accessId?.trim()) return { strategy: 'accessibility_id', value: el.accessId }
    if (el.accessibilityLabel?.trim()) return { strategy: 'accessibility_id', value: el.accessibilityLabel }
    if (el.text?.trim()) return { strategy: 'predicate_string', value: `label == "${el.text}"` }
    if (el.locatorStrategy === 'predicate_string' && el.locatorValue?.trim()) return { strategy: 'predicate_string', value: el.locatorValue }
    if (el.locatorStrategy === 'class_chain' && el.locatorValue?.trim()) return { strategy: 'class_chain', value: el.locatorValue }
    if (el.locatorValue?.trim()) return extractFromXPath(el.locatorValue, true) ?? { strategy: 'xpath', value: el.locatorValue }
  }
  return null
}

function esc(s: string): string { return (s ?? '').replace(/\\/g, '\\\\').replace(/"/g, '\\"') }

function javaByStr(el: ElLike | null | undefined): string {
  const loc = resolveLocator(el)
  if (!loc) return `By.id("REPLACE_ME")`
  switch (loc.strategy) {
    case 'id':               return `By.id("${esc(loc.value)}")`
    case 'accessibility_id': return `AppiumBy.accessibilityId("${esc(loc.value)}")`
    case 'uiautomator':      return `AppiumBy.androidUIAutomator("${esc(loc.value)}")`
    case 'predicate_string': return `AppiumBy.iOSNsPredicateString("${esc(loc.value)}")`
    case 'class_chain':      return `AppiumBy.iOSClassChain("${esc(loc.value)}")`
    case 'xpath':            return `By.xpath("${esc(loc.value)}")`
    default:                 return `By.xpath("${esc(loc.value)}")`
  }
}

// ── Pasos grabados sobre pantallas transitorias conocidas (TAREA 3) ─────────
//
// "o crea una cuenta para comenzar a disfrutar los beneficios de ser socio" es
// el CTA secundario de la pantalla "Club Cinépolis" (login/alta de socio) —
// BaseTest.beforeEach() (ClubGuard/PromosGuard, repo de tests) YA la cierra
// automáticamente para TODOS los tests antes de que cualquier paso grabado se
// ejecute, porque es transitoria (solo aparece en ciertos arranques). Si el
// caso se grabó mientras esa pantalla estaba visible, el tap queda grabado
// igual — pero para cuando el test corre de verdad, la pantalla casi siempre
// ya no está. Confirmado con evidencia real (pageSource + screenshot del
// dispositivo): la app queda en Cartelera, sin ese texto en pantalla.
//
// Lista cerrada y explícita — SOLO estos textos exactos activan la tolerancia;
// cualquier otro tap grabado sigue siendo estrictamente obligatorio (falla si
// no encuentra el elemento), sin cambios.
const TRANSIENT_SCREEN_TAP_TEXTS = new Set([
  'o crea una cuenta para comenzar a disfrutar los beneficios de ser socio',
])

function isTransientScreenTap(step: SuiteStep): boolean {
  if (step.type !== 'tap') return false
  const text = step.el?.text?.trim()
  return !!text && TRANSIENT_SCREEN_TAP_TEXTS.has(text)
}

const SPANISH_STOP_WORDS = new Set(['el', 'la', 'los', 'las', 'de', 'del', 'un', 'una', 'y', 'en', 'a'])

function removeAccents(s: string): string {
  return s.normalize('NFD').replace(/[̀-ͯ]/g, '')
}

function normalizeToIdentifier(text: string): string {
  const noAcc = removeAccents(text)
  const words = noAcc.split(/[^a-zA-Z0-9]+/).filter(w => w.length > 0)
  const useful = words.filter(w => !SPANISH_STOP_WORDS.has(w.toLowerCase()))
  const src = useful.length > 0 ? useful : words.slice(0, 1)
  if (src.length === 0) return ''
  return src.map((w, i) => i === 0
    ? w.charAt(0).toLowerCase() + w.slice(1).toLowerCase()
    : w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()
  ).join('')
}

function sanitizeJavaClassName(rawName: string): string {
  const id = normalizeToIdentifier(rawName || 'CasoGrabado')
  const capitalized = id ? id.charAt(0).toUpperCase() + id.slice(1) : 'CasoGrabado'
  const safe = capitalized.replace(/[^a-zA-Z0-9_]/g, '') || 'CasoGrabado'
  return 'RS_' + (/^[0-9]/.test(safe) ? '_' + safe : safe)
}

function escJava(text: string): string {
  return (text ?? '').replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n')
}

function stepLabelFor(step: SuiteStep, n: number): string {
  const target = step.el?.text?.trim() || step.el?.varName?.trim() || step.el?.accessId?.trim() || ''
  switch (step.type) {
    case 'tap':           return `${n}. Tap${target ? ' — ' + target : ''}`
    case 'double_tap':    return `${n}. Doble tap${target ? ' — ' + target : ''}`
    case 'long_press':    return `${n}. Presión larga${target ? ' — ' + target : ''}`
    case 'input':         return `${n}. Escribir "${step.inputVal ?? ''}"${target ? ' en ' + target : ''}`
    case 'swipe':         return `${n}. Swipe ${step.dir ?? 'up'}`
    case 'scroll':        return `${n}. Scroll ${step.dir ?? 'down'}`
    case 'hide_keyboard': return `${n}. Ocultar teclado`
    case 'back':          return `${n}. Atrás`
    case 'home':          return `${n}. Ir a Home`
    case 'assertion':     return `${n}. Validar${target ? ' — ' + target : ' resultado'}`
    case 'screenshot':    return `${n}. Captura de pantalla`
    default:              return `${n}. Paso`
  }
}

export interface GeneratedExecutableTest {
  className: string
  source:    string
}

/** Único punto real usado por SuitesPage para convertir un TestCase grabado en un archivo ejecutable por el Runner. */
export function generateExecutableJava(steps: SuiteStep[], caseName: string): GeneratedExecutableTest {
  const className = sanitizeJavaClassName(caseName)
  const body: string[] = []
  let n = 0

  for (const step of steps) {
    n++
    const label = escJava(stepLabelFor(step, n))
    switch (step.type) {
      case 'tap':
      case 'double_tap':
      case 'long_press': {
        const by = javaByStr(step.el)
        if (isTransientScreenTap(step)) {
          const transientText = escJava(step.el?.text?.trim() ?? '')
          body.push(`        TestSteps.run("${label}", () -> {`)
          body.push(`            try {`)
          body.push(`                WebElement el = new WebDriverWait(driver, Duration.ofSeconds(15))`)
          body.push(`                        .until(ExpectedConditions.elementToBeClickable(${by}));`)
          body.push(`                el.click();`)
          body.push(`                System.out.println("[RecordStudio][TransientStep] texto=${transientText} action=TAPPED");`)
          body.push(`            } catch (org.openqa.selenium.TimeoutException notPresent) {`)
          body.push(`                System.out.println("[RecordStudio][TransientStep] texto=${transientText} action=SKIPPED reason=Club Cinépolis transient screen");`)
          body.push(`            }`)
          body.push(`        }, driver);`)
        } else {
          body.push(`        TestSteps.run("${label}", () -> {`)
          body.push(`            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(15))`)
          body.push(`                    .until(ExpectedConditions.elementToBeClickable(${by}));`)
          body.push(step.type === 'double_tap' ? `            el.click();\n            el.click();` : `            el.click();`)
          body.push(`        }, driver);`)
        }
        break
      }
      case 'input': {
        const by = javaByStr(step.el)
        const val = escJava(step.inputVal ?? '')
        body.push(`        TestSteps.run("${label}", () -> {`)
        body.push(`            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(15))`)
        body.push(`                    .until(ExpectedConditions.visibilityOfElementLocated(${by}));`)
        body.push(`            el.clear();`)
        body.push(`            el.sendKeys("${val}");`)
        body.push(`        }, driver);`)
        break
      }
      case 'swipe':
      case 'scroll': {
        const dir = step.dir ?? (step.type === 'swipe' ? 'up' : 'down')
        body.push(`        TestSteps.run("${label}", () -> rsSwipe(driver, "${dir}"), driver);`)
        break
      }
      case 'hide_keyboard':
        body.push(`        TestSteps.run("${label}", () -> driver.hideKeyboard(), driver);`)
        break
      case 'back':
        body.push(`        TestSteps.run("${label}", () -> driver.navigate().back(), driver);`)
        break
      case 'home':
        body.push(`        TestSteps.run("${label}", () -> rsGoHome(driver), driver);`)
        break
      case 'assertion': {
        const by = javaByStr(step.el)
        const expected = escJava(step.el?.text ?? '')
        body.push(`        TestSteps.run("${label}", () -> {`)
        body.push(`            WebElement el = new WebDriverWait(driver, Duration.ofSeconds(15))`)
        body.push(`                    .until(ExpectedConditions.visibilityOfElementLocated(${by}));`)
        body.push(`            Assertions.assertTrue(el.isDisplayed(), "Elemento esperado no visible: ${expected}");`)
        body.push(`        }, driver);`)
        break
      }
      case 'screenshot':
        body.push(`        TestSteps.run("${label}", () -> {}, driver);`)
        break
    }
  }

  const source = `package tests.QARecordStudio;

import base.BaseTest;
import io.appium.java_client.AppiumBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.PointerInput;
import org.openqa.selenium.interactions.Sequence;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import utils.TestSteps;

import java.time.Duration;
import java.util.Collections;

/**
 * Generado automáticamente por Record Studio — caso: "${escJava(caseName)}".
 * NO editar a mano: se regenera y reemplaza en cada ejecución.
 */
public class ${className} extends BaseTest {

    @BeforeEach
    void rsStartScenario() {
        TestSteps.startScenario("${escJava(caseName)}");
    }

    @Test
    @DisplayName("${escJava(caseName)}")
    void ejecutarCasoGrabado() {
${body.join('\n')}
    }

    private static void rsSwipe(io.appium.java_client.AppiumDriver driver, String dir) {
        Dimension size = driver.manage().window().getSize();
        int cx = size.getWidth() / 2, cy = size.getHeight() / 2;
        int startX = cx, startY = cy, endX = cx, endY = cy;
        switch (dir) {
            case "up":    startY = (int) (size.getHeight() * 0.75); endY = (int) (size.getHeight() * 0.25); break;
            case "down":  startY = (int) (size.getHeight() * 0.25); endY = (int) (size.getHeight() * 0.75); break;
            case "left":  startX = (int) (size.getWidth()  * 0.75); endX = (int) (size.getWidth()  * 0.25); break;
            case "right": startX = (int) (size.getWidth()  * 0.25); endX = (int) (size.getWidth()  * 0.75); break;
        }
        PointerInput finger = new PointerInput(PointerInput.Kind.TOUCH, "finger");
        Sequence swipe = new Sequence(finger, 1);
        swipe.addAction(finger.createPointerMove(Duration.ZERO, PointerInput.Origin.viewport(), startX, startY));
        swipe.addAction(finger.createPointerDown(PointerInput.MouseButton.LEFT.asArg()));
        swipe.addAction(finger.createPointerMove(Duration.ofMillis(300), PointerInput.Origin.viewport(), endX, endY));
        swipe.addAction(finger.createPointerUp(PointerInput.MouseButton.LEFT.asArg()));
        driver.perform(Collections.singletonList(swipe));
    }

    private static void rsGoHome(io.appium.java_client.AppiumDriver driver) {
        try {
            ((io.appium.java_client.android.AndroidDriver) driver)
                    .pressKey(new io.appium.java_client.android.nativekey.KeyEvent(
                            io.appium.java_client.android.nativekey.AndroidKey.HOME));
        } catch (ClassCastException notAndroid) {
            driver.navigate().back();
        }
    }
}
`

  return { className, source }
}
