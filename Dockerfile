# ── Etapa 1: build del Runner JAR (Maven) ─────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS runner-build

WORKDIR /runner
COPY runner/pom.xml ./pom.xml
RUN mvn dependency:go-offline -q

COPY runner/src ./src
RUN mvn clean package -DskipTests -q

# ── Etapa 2: build del backend Spring Boot ────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copiar solo el pom.xml primero (cache de dependencias Maven)
COPY backend/pom.xml ./backend/pom.xml
RUN mvn -f backend/pom.xml dependency:go-offline -q

# Copiar el código fuente del backend
COPY backend/src ./backend/src

# Incluir el runner JAR como recurso del backend ANTES de compilar,
# para que quede empaquetado en el classpath y sea servible via /api/runner/download/jar
COPY --from=runner-build /runner/target/cinepolis-runner.jar \
     ./backend/src/main/resources/installers/cinepolis-runner.jar

RUN mvn -f backend/pom.xml clean package -DskipTests -q

# ── Etapa 3: imagen runtime mínima ────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copiar el JAR generado
COPY --from=build /app/backend/target/cinepolis-backend-*.jar app.jar

# Railway inyecta PORT automáticamente
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
