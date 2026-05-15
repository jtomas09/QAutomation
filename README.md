# Automatización de Alimentos Cinépolis con Appium

## Descripción del Proyecto

Este proyecto contiene una suite de pruebas automatizadas para la sección de **Alimentos y Bebidas** de la aplicación de Cinépolis. Está construido sobre una arquitectura robusta que utiliza **Appium** para la automatización móvil, **Java** como lenguaje de programación y **Gradle** como sistema de compilación.

La estructura sigue el patrón de diseño **Page Object Model (POM)** para garantizar que el código sea limpio, mantenible y escalable. Además, integra el framework **Allure** para la generación de reportes de prueba detallados e interactivos y un sistema personalizado para consolidar los resultados en **reportes PDF**.

---

## Tecnologías Utilizadas

- **Lenguaje:** Java 17+
- **Framework de Automatización:** Appium 2.x (con driver UiAutomator2)
- **Sistema de Compilación:** Gradle 8+
- **Framework de Pruebas:** JUnit 5
- **Framework de Reportes:** Allure Framework
- **Diseño de Código:** Page Object Model (POM)
- **CI/CD:** GitHub Actions (configurado en `.github/workflows`)

---

## Características Principales

- **Ejecución Optimizada:** El tiempo de ejecución de la suite se ha reducido drásticamente gracias a un sistema de captura de evidencia configurable. Por defecto, solo se toman capturas en los pasos que fallan, pero se puede activar la captura completa para debugging.
- **Estructura Robusta y Escalable:** El uso del patrón POM y la refactorización de las clases de prueba (`MenuCoffeTree`, `MenuAtmosfera`, etc.) aseguran un código fácil de mantener y extender.
- **Esperas Dinámicas:** Se ha eliminado el uso de esperas fijas (`Thread.sleep`) en favor de esperas explícitas y fluídas de Appium/Selenium, lo que hace las pruebas más rápidas y estables.
- **Reportes Avanzados:** Generación automática de reportes interactivos con Allure y un reporte consolidado en formato PDF al finalizar la suite, ideal para compartir resultados.
- **Configuración Centralizada:** La creación del driver y las capacidades se gestionan desde una única factoría, facilitando la configuración para diferentes entornos y dispositivos.

---

## Estructura del Proyecto

```
appium-gradle-pom/
├── .github/workflows/         # Flujos de trabajo para Integración Continua.
├── build.gradle               # Fichero principal de configuración de Gradle.
├── .gitignore                 # Ficheros y carpetas ignorados por Git.
└── src/test/java/
    ├── base/                  # Clases base para las pruebas (BaseTest).
    ├── config/                # Configuración y creación del driver (DriverFactory).
    ├── pages/                 # Clases Page Object que modelan las pantallas de la app.
    ├── tests/                 # Clases que contienen los casos de prueba (JUnit 5).
    └── utils/                 # Clases de utilidad (TestSteps, extensiones de reportes, etc.).
```

---

## Configuración y Ejecución

#### 1. Requisitos Previos
- Tener un servidor de **Appium 2.x** corriendo.
- Un dispositivo o emulador Android conectado y visible a través de `adb`.

#### 2. Configuración del Dispositivo
Crea un fichero `local.properties` en la raíz del proyecto para especificar las capacidades del dispositivo. Puedes usar el siguiente template:

```properties
# local.properties (este fichero no se sube al repositorio)
deviceName=nombre_de_tu_emulador
udid=emulator-5554
platformVersion=14.0
```

#### 3. Ejecutar Pruebas

Utiliza el wrapper de Gradle para ejecutar la suite de pruebas. Puedes pasar parámetros adicionales por línea de comandos si es necesario.

**Ejecución Rápida (Recomendada):**
Esta es la opción por defecto. Solo se generan capturas de pantalla si un test falla.

```bash
./gradlew test
```

**Ejecución con Evidencia Completa:**
Este modo captura una imagen en cada paso del test, lo que es útil para debugging pero mucho más lento.

```bash
./gradlew test -DcaptureEvidence=true
```

---

## Sistema de Reportes

#### Reportes Allure

Una vez finalizada la ejecución, puedes generar y visualizar el reporte de Allure con el siguiente comando. Esto abrirá un servidor web local con el reporte interactivo.

```bash
./gradlew allureServe
```

Los resultados brutos de Allure se encuentran en `build/allure-results`.

#### Reporte PDF

Al finalizar la ejecución completa de la suite, se genera automáticamente un reporte consolidado en `build/reports/suite/TestReport.pdf`, el cual incluye los resultados de todas las pruebas y las evidencias de los fallos.
