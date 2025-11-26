#Запуск через докер(автоматом распакует):

# === Этап 1: Сборка приложения ===
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
#COPY pom.xml .
#RUN mvn dependency:go-offline
# Повторные попытки скачивания зависимостей с увеличенным таймаутом
###########################################
#RUN mvn -B dependency:go-offline  \
#    -Dmaven.wagon.http.retryHandler.count=5  \
#    -Dmaven.wagon.http.connectionTimeout=60000  \
#    -Dmaven.wagon.http.readTimeout=60000
#COPY src ./src
#RUN mvn clean package -DskipTests
COPY . .
RUN mvn -B package -DskipTests

# === Этап 2: Запуск приложения ===
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=docker
ENTRYPOINT ["java", "-jar", "app.jar"]

