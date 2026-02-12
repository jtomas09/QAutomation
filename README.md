# Appium + Gradle + Java + JUnit 5 + Allure (POM)

Proyecto base listo para ejecutar que automatiza Android con **Appium** y estructura **Page Object Model** para mantener los flujos limpios.
Incluye **Allure** para reportes y configuración por propiedades.

## Requisitos
- Java 17+
- Gradle 8+ (o usa `gradle wrapper` para generar `./gradlew`)
- Appium Server 2.x en `http://127.0.0.1:4723/wd/hub` con **UiAutomator2** driver
- Dispositivo Android (real o emulador)

## Estructura
```
appium-gradle-pom/
  build.gradle
  settings.gradle
  src/test/java/
    base/BaseTest.java
    config/DriverFactory.java
    pages/CinemaSelectorPage.java
    tests/SelectCinemaTest.java
    utils/Waits.java
  src/test/resources/
    appium.properties
    locators.properties
```

## Configuración
1) Edita `src/test/resources/appium.properties` con tu `appPackage` y `appActivity` (o `apkPath`).
2) Ajusta `src/test/resources/locators.properties` con los IDs/textos reales del campo de búsqueda y del item de resultado.

## Ejecutar pruebas
```bash
# Si usas gradle del sistema
gradle test -Dappium.hub=http://127.0.0.1:4723/wd/hub             -DdeviceName="Android Device"             -DplatformVersion=14             -Dudid=auto             -DcinemaName="Cinépolis Universidad"

# Si generas wrapper primero:
gradle wrapper
./gradlew test -Dappium.hub=http://127.0.0.1:4723/wd/hub -DcinemaName="Cinépolis Universidad"
```

## Reportes Allure
Los resultados quedan en `build/allure-results`. Puedes verlos de dos formas:
- Con el plugin de Gradle: `./gradlew allureServe` (descarga y abre Allure UI localmente).
- O si tienes Allure CLI: `allure serve build/allure-results`.

## Notas
- `DriverFactory` usa `UiAutomator2Options`. Puedes pasar flags por `-D` o dejar valores en `appium.properties`.
- El Page Object `CinemaSelectorPage` mantiene limpio el flujo de **buscar y tocar** un cine por nombre.
