# ── Etapa 1: build del backend Spring Boot ────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

# Copiar solo el pom.xml primero (cache de dependencias Maven)
COPY backend/pom.xml ./backend/pom.xml
RUN mvn -f backend/pom.xml dependency:go-offline -q

# Copiar el código fuente y compilar
COPY backend/src ./backend/src
RUN mvn -f backend/pom.xml clean package -DskipTests -q

# ── Etapa 2: imagen runtime mínima ────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copiar el JAR generado
COPY --from=build /app/backend/target/cinepolis-backend-*.jar app.jar

# Railway inyecta PORT automáticamente
ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
