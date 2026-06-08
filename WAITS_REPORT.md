# Reporte de Waits Ocultos — CinepolisAutomation

Generado: 2026-06-08  
Alcance: `src/test/java/**/*.java`

---

## Resumen

| Tipo de Wait | Ocurrencias |
|---|---|
| `Thread.sleep()` | 25 |
| `implicitlyWait()` | 59 |
| `WebDriverWait` / `FluentWait` | 16 |
| `safeSleep()` | 12 |
| `Duration.ofSeconds()` | 44 |
| `Duration.ofMillis()` | 24 |
| `Pause` (PointerInput gestures) | 10 |
| **Total** | **190** |

---

## 1. base/BaseTest.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| BaseTest | relaunchAppSafe() | Thread.sleep | 300 ms | 1× por tearDown con error |
| BaseTest | quickSwipeUp() | Thread.sleep | 60 ms | 1× por swipe |
| BaseTest | isAlimentosScreenVisible() | Thread.sleep | 120 ms | N× polling (≤3 s) |
| BaseTest | autoScrollOnAppOpen() | Thread.sleep | 250 ms | 1× por test |
| BaseTest | setUp() | Thread.sleep | 800 ms | 2× por test (ensureCinema fallback) |

---

## 2. config/DriverFactory.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| DriverFactory | buildLocal() | Duration.ofSeconds | 300 s (connectionTimeout) | 1× por sesión |
| DriverFactory | buildLocal() | Duration.ofSeconds | 120 s (commandTimeout) | 1× por sesión |
| DriverFactory | validateAppiumServer() | Duration.ofSeconds | 5 s (connection check) | 1× por sesión |
| DriverFactory | attemptCreate() | implicitlyWait | 0 (inicial) | 1× por sesión |
| DriverFactory | buildBrowserStack() | Duration.ofSeconds | 300 s | 1× por sesión BS |
| DriverFactory | buildSauceLabs() | Duration.ofSeconds | 300 s | 1× por sesión SL |
| DriverFactory | sleep() | Thread.sleep | variable | 1× por retry |

---

## 3. pages/common/BasePage.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| BasePage | (const) DEFAULT_WAIT_TIMEOUT | Duration.ofSeconds | 15 s | referencia |
| BasePage | (const) SCROLL_WAIT_TIMEOUT | Duration.ofSeconds | 30 s | referencia |
| BasePage | (const) POLLING_INTERVAL | Duration.ofMillis | 500 ms | referencia |
| BasePage | constructor | WebDriverWait | 15 s | 1× por instancia |
| BasePage | waitForElementClickable() | WebDriverWait | variable (parámetro) | 1× por llamada |
| BasePage | scrollUntilElementClickable() | WebDriverWait | 15 s | N× por scroll |
| BasePage | waitSafelyForElement() | WebDriverWait | variable | 1× por llamada |
| BasePage | waitSafelyForClickable() | WebDriverWait | variable | 1× por llamada |
| BasePage | verifyElementPresence() | WebDriverWait | variable | 1× por llamada |
| BasePage | scrollAndClick() | WebDriverWait | variable | 1× por llamada |
| BasePage | findAndWaitForElement() | WebDriverWait | variable | 1× por llamada |
| BasePage | tap() | Pause (PointerInput) | 120 ms | 1× por tap |
| BasePage | swipe() | Pause (PointerInput) | variable | 1× por swipe |
| BasePage | sleep() | Thread.sleep | variable | N× según llamador |

---

## 4. pages/common/CinemasHelper.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| CinemasHelper | findInstant() | implicitlyWait | 0 → 10 s (restore) | N× por búsqueda instantánea |
| CinemasHelper | tapInstant() | implicitlyWait | 0 (via findInstant) | N× por dismiss genérico |
| CinemasHelper | isVisibleInstant() | implicitlyWait | 0 → 10 s (restore) | N× por check rápido |
| CinemasHelper | getCurrentCinemaName() | implicitlyWait | 0 → 10 s (restore) | 1× por ensureCinema |
| CinemasHelper | isMarioPromoVisible() | implicitlyWait | 0 → 10 s (restore) | ≤5× por PromosGuard |
| CinemasHelper | isLocationChangePopupVisible() | implicitlyWait | 0 → 10 s (restore) | ≤5× por PromosGuard |
| CinemasHelper | isSelectorOpen() | implicitlyWait | 0 (via isVisibleInstant) | N× polling |
| CinemasHelper | isChangeCinemaAlertOpen() | implicitlyWait | 0 (via isVisibleInstant) | N× polling |
| CinemasHelper | esperarMainNavRapido() | safeSleep | 200 ms | ≤25× (5 s / 200 ms) |
| CinemasHelper | safeSleep() | Thread.sleep | variable | N× utilidad interna |
| CinemasHelper | dismissClubLoginGuard() | safeSleep | 600 ms | 1× pre-check |
| CinemasHelper | dismissTransientPromosGuard() | safeSleep | 500 ms | ≤5× (fin de cada pass) |
| CinemasHelper | dismissLocationChangePopupIfPresent() | safeSleep | 600, 300 ms | ≤3× por dismiss zona |
| CinemasHelper | dismissClubLoginIfPresent() | sleep | 600, 400, 700 ms | ≤3× por dismiss Club |
| CinemasHelper | openSelectorFromAlimentosIfNeeded() | sleep | 900 ms | ≤3× por tap |
| CinemasHelper | openCinesIconWithRetries() | sleep | 450, 550 ms | ≤5× por retry |
| CinemasHelper | waitSelectorScreenOrThrow() | sleep | 150 ms | N× polling (9 s max) |
| CinemasHelper | acceptAlertsIfPresent() | sleep | 120 ms | N× polling (4.5 s max) |
| CinemasHelper | goToAlimentosTab() | sleep | 750, 650, 500, 900 ms | ≤6× por retry tab |
| CinemasHelper | typeInSearchBoxULTRA() | sleep | 200, 250, 550 ms | ≤6× por intento |
| CinemasHelper | w3cTap() | Pause | Math.max(0, holdMs) | 1× por tap |

---

## 5. pages/alimentos/SelectorPage.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| SelectorPage | buscarTeCaliente() | sleep | 600 + 1500 ms | 1× por llamada |
| SelectorPage | buscarAmericano() | sleep | 600 + 1500 ms | 1× por llamada |
| SelectorPage | buscarMokaObscuro() | sleep | 600 + 1500 ms | 1× por llamada |
| SelectorPage | buscarCapuccino() | sleep | 600 + 1500 ms | 1× por llamada |
| SelectorPage | buscarChocolate() | sleep | 600 + 1500 ms | 1× por llamada |
| SelectorPage | buscarPretzel() | sleep | 600 + 1500 ms | 1× por llamada |
| SelectorPage | buscarCheeseCake() | sleep | 600 + 1500 ms | 1× por llamada |
| SelectorPage | intentarAbrirCarritoInterno() | implicitlyWait | 0 → 10 s / 2 → 10 s / 5 → 10 s | 3× por abrirCarrito |
| SelectorPage | swipeUpInMainContent() | Thread.sleep | 350 ms | N× por scroll vertical |
| SelectorPage | swipeRightInAnchorY() | Thread.sleep | 80 ms | ≤20× por búsqueda carrusel |
| SelectorPage | tryClickIfAlreadyVisible() | WebDriverWait | variable (timeoutSeconds) | 1× por llamada |
| SelectorPage | tapCenterW3C() / w3cTap() | Pause | 100–150 ms | 1× por tap |
| SelectorPage | abrirCarrito() | sleep | 800 ms | ≤3× por retry |

---

## 6. pages/alimentos/SelectorsAlimentos.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| SelectorsAlimentos | seleccionarAlimentoAleatorio() | sleep | 2000 ms | 1× por intento |
| SelectorsAlimentos | seleccionarTabAleatorio() | sleep | 600 ms | ≤10× swipes recolección |
| SelectorsAlimentos | seleccionarTabAleatorio() | sleep | 1500 ms | 1× tras tap categoría |
| SelectorsAlimentos | seleccionarProductoEnSeccion() | sleep | 400, 300 ms | ≤20× por swipe |
| SelectorsAlimentos | seleccionarProductoEnSeccion() | sleep | 600 ms | ≤20× por swipe |
| SelectorsAlimentos | clickProductoEnCarrusel() | sleep | 600, 300, 500 ms | ≤15× por intento |
| SelectorsAlimentos | completarPersonalizacion() | sleep | 1000, 800, 1200 ms | ≤30× por paso |
| SelectorsAlimentos | manejarErrorCarritoSiPresente() | sleep | 500 ms | N× polling (3 s) |
| SelectorsAlimentos | seleccionarOpcionesAlAzar() | sleep | 300 ms | N× por tap opción |
| SelectorsAlimentos | clickSaltarAlimentos() | sleep | 500 ms | N× polling (15 s) |
| SelectorsAlimentos | vincularOrdenVIPSinSesion() | sleep | 1500 ms | 1× tras vincular |
| SelectorsAlimentos | saltarVinculacionEspaña() | sleep | 1500 ms | 1× por llamada |

---

## 7. pages/asientos/SelectorPage.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| SelectorPage (asientos) | abrirPrimerAsiento() | implicitlyWait | 3 s | 1× |
| SelectorPage (asientos) | (helpers varios) | implicitlyWait | 0 → 10 s | N× por búsqueda |
| SelectorPage (asientos) | waitForElement() | Thread.sleep | variable | N× polling |
| SelectorPage (asientos) | tapElement() | Pause | 50–80 ms | 1× por tap |
| SelectorPage (asientos) | (context) | Thread.sleep | 250 ms | 1× |

---

## 8. pages/mapaAsientos/SelectorsMapaAsientos.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| SelectorsMapaAsientos | (helpers visibilidad) | implicitlyWait | 0 → 10 s, 2 s, 3 s | N× por check |

---

## 9. pages/homeCartelera/SelectorsHome.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| SelectorsHome | cambiarPaisArgentina() | Thread.sleep | **5000 ms** ⚠️ | 1× por cambio país |
| SelectorsHome | runAppInBackground() | Duration.ofSeconds | -1 s (indefinido) | 1× |

---

## 10. utils/Waits.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| Waits | (const) defaultTimeout | Duration.ofSeconds | 6 s | referencia |
| Waits | (const) fastTimeout | Duration.ofSeconds | 6 s | referencia |
| Waits | waitClickable() | WebDriverWait | 6 s | 1× por llamada |
| Waits | waitVisible() | WebDriverWait | 6 s | 1× por llamada |
| Waits | waitPresent() | WebDriverWait | 6 s | 1× por llamada |
| Waits | waitGone() | WebDriverWait | 6 s | 1× por llamada |

---

## 11. utils/Reintento.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| Reintento | pausar() | Thread.sleep | variable (backoff exponencial) | N× por reintento |

---

## 12. tests/México/alimentos/MenuAtmosfera.java

| Clase | Método | Tipo | Tiempo | Veces aprox. |
|---|---|---|---|---|
| MenuAtmosfera | (test method) | Thread.sleep | 2000 ms | 1× |
| MenuAtmosfera | (test method) | Thread.sleep | 2500 ms | 1× |
| MenuAtmosfera | (test method) | WebDriverWait | **40 s** ⚠️ | 1× |

---

## Hallazgos Críticos

### ⚠️ Waits de alto impacto (>1 segundo fijo)

| Ubicación | Wait | Impacto |
|---|---|---|
| MenuAtmosfera (test) | `WebDriverWait 40 s` | Bloquea 40 s si elemento no aparece |
| SelectorsHome.cambiarPaisArgentina | `Thread.sleep(5000)` | 5 s fijo, siempre |
| SelectorsAlimentos.seleccionarAlimentoAleatorio | `sleep(2000)` | 2 s fijo antes de buscar barra |
| SelectorsAlimentos.completarPersonalizacion | `sleep(1000)` por paso × 30 pasos máx | Hasta 30 s solo en sleeps |
| SelectorPage (alimentos) buscar* | `sleep(1500)` | 1.5 s fijo por cada buscar*() |

### ✅ Waits correctamente optimizados (post este PR)

| Ubicación | Antes | Después |
|---|---|---|
| CinemasHelper.isMarioPromoVisible | 10 s (implicitWait default) | 0 ms (implicitlyWait=0 temporal) |
| CinemasHelper.isLocationChangePopupVisible | 10 s | 0 ms |
| CinemasHelper.tryGenericOverlayDismiss (4 locators) | 4×10 s = 40 s | 4×0 ms ≈ 0 ms |
| CinemasHelper.isSelectorOpen | 2×10 s = 20 s | 0 ms (isVisibleInstant) |
| CinemasHelper.isChangeCinemaAlertOpen | 10 s | 0 ms |
| CinemasHelper.acceptAlertsIfPresent (loop) | desbordaba 4.5 s limit | ≤4.5 s correctamente acotado |
| CinemasHelper.getCurrentCinemaName (3 estrategias) | hasta 30 s | ≈ 0 ms si elemento no existe |
| ensureCinemaSelectedFromAlimentos (check inicial) | isVisibleNow = 10 s | isVisibleInstant = 0 ms |

### Reducción estimada PromosGuard

```
ANTES (sin overlays, main nav no visible inmediatamente):
  Pass 1: Mario(10s) + Zona(10s) + MainNav(false) + GenericDismiss(40s) + sleep(0.5s) = 60.5s
  Pass 2: Mario(10s) + Zona(10s) + MainNav(true→exit) = 20s
  Total estimado: ~80s

DESPUÉS (sin overlays):
  Pass 1: Mario(≈0ms) + Zona(≈0ms) + MainNav(true→exit) = <200ms
  Total estimado: <1s cuando nav es visible
  Con retrasos de app: <10s en el peor caso (5 passes × sleep 500ms + polling nav)
```
