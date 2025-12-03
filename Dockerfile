# === Этап 1: Сборка приложения ===
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Копирование всех файлов проекта (pom.xml, src и т.д.)
COPY . .

# Сборка проекта (пропускаем тесты для ускорения сборки образа)
RUN mvn -B package -DskipTests

# === Этап 2: Запуск приложения ===
FROM eclipse-temurin:21-jdk
WORKDIR /app

# Копирование JAR файла из этапа сборки
COPY --from=build /app/target/*.jar app.jar

# Порт для Order Service (8080 согласно application.properties)
EXPOSE 8080

# Переменная окружения для профиля
ENV SPRING_PROFILES_ACTIVE=docker

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]

