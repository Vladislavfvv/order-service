package com.innowise.orderservice.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Базовый класс для интеграционных тестов.
 * Настраивает PostgreSQL контейнер через Testcontainers и применяет миграции Liquibase.
 * 
 * ВАЖНО: Используется изолированный Docker контейнер с PostgreSQL, который:
 * - Создается автоматически при запуске тестов
 * - Существует только во время выполнения тестов
 * - Автоматически удаляется после завершения тестов
 * - НЕ влияет на реальную базу данных приложения
 * - Полностью изолирован от продакшн/разработки БД
 */
@SpringBootTest(properties = {
        "spring.cache.type=none"}) // Отключаем кеширование для интеграционных тестов
@org.springframework.test.context.ActiveProfiles("test") // Активируем профиль "test" для исключения SecurityConfig
// Примечание: application-test.properties НЕ используется, так как все настройки задаются через @DynamicPropertySource
// Примечание: НЕ используем @Transactional здесь, так как это может конфликтовать с MockMvc в контроллер-тестах
public abstract class BaseIntegrationTest {

    /**
     * Флаг для определения, использовать ли Testcontainers.
     * Если USE_TESTCONTAINERS=false, используются внешние сервисы (например, в CI/CD).
     * По умолчанию true — используем Testcontainers.
     */
    private static final boolean USE_TESTCONTAINERS =
            !"false".equalsIgnoreCase(System.getenv().getOrDefault("USE_TESTCONTAINERS", "true"));


    // Статические контейнеры — создаются один раз для всех тестов
    static PostgreSQLContainer<?> postgres;
    static GenericContainer<?> redis;

    static {
        // Создаём и стартуем контейнеры только если USE_TESTCONTAINERS=true
        if (USE_TESTCONTAINERS) {
            // Создаём контейнер PostgreSQL версии 16 для тестовой базы данных
            // Контейнер будет использоваться для всех интеграционных тестов
            postgres = new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("testdb") // Имя тестовой базы данных
                    .withUsername("test") // Имя пользователя для подключения
                    .withPassword("test") // Пароль для подключения
                    .withStartupAttempts(3); // Количество попыток запуска контейнера
            postgres.start(); // Запускаем контейнер PostgreSQL

            // Создаём контейнер Redis версии 7.2 для тестового кеша
            // Redis используется для кеширования данных в приложении
            redis = new GenericContainer<>("redis:7.2")
                    .withExposedPorts(6379) // Открываем стандартный порт Redis
                    .waitingFor(Wait.forListeningPort()); // Ждём, пока порт станет доступен
            redis.start(); // Запускаем контейнер Redis

            // Закрываем контейнеры при завершении JVM
            // Это гарантирует, что контейнеры будут остановлены даже при аварийном завершении
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (redis != null) {
                    redis.stop();
                }
                if (postgres != null) {
                    postgres.stop();
                }
            }));
        }
    }


    /**
     * PostgreSQL контейнер для интеграционных тестов.
     * Используется статический контейнер для переиспользования между тестами.
     *
     * Это изолированный Docker контейнер, который создается Testcontainers.
     * Он НЕ подключен к вашей реальной БД и автоматически удаляется после тестов.
     */

//    @Container
//    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
//            .withDatabaseName("testdb")
//            .withUsername("test")
//            .withPassword("test");

    /**
     * Настраивает динамические свойства для подключения к тестовой БД.
     * Используется вместо application.properties для интеграционных тестов.
     */
//    @DynamicPropertySource //Аннотация для настройки динамических свойств для подключения к тестовой БД
//    static void configureProperties(DynamicPropertyRegistry registry) {
//        // Настройки подключения к PostgreSQL контейнеру
//        registry.add("spring.datasource.url", postgres::getJdbcUrl);
//        registry.add("spring.datasource.username", postgres::getUsername);
//        registry.add("spring.datasource.password", postgres::getPassword);
//
//        // Включаем Liquibase для применения миграций
//        registry.add("spring.liquibase.enabled", () -> true);
//        registry.add("spring.liquibase.change-log",
//                () -> "classpath:/db/changelog/db.changelog-master.yaml");
//
//        // Отключаем Redis для тестов
//        registry.add("spring.data.redis.repositories.enabled", () -> false);
//
//        // Настройки JWT для тестов
//        registry.add("jwt.secret", () -> "test-secret-key-for-jwt-generation-in-order-service-integration-tests");
//
//        // Мок URL для User Service (будет замокирован через @MockBean)
//        registry.add("user.service.url", () -> "http://localhost:9999");
//
//        // Упрощенные настройки Circuit Breaker для тестов
//        registry.add("resilience4j.circuitbreaker.instances.userService.slidingWindowSize", () -> 5);
//        registry.add("resilience4j.circuitbreaker.instances.userService.minimumNumberOfCalls", () -> 2);
//    }

    /**
     * Настраивает свойства для интеграционных тестов.
     * Использует схему public по умолчанию (стандартная схема PostgreSQL).
     * Динамически устанавливает параметры подключения к базе данных и Redis.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (USE_TESTCONTAINERS) {
            // Конфигурация для локальных тестов с использованием Testcontainers
            // Схема public существует по умолчанию в PostgreSQL, создание не требуется

            // Настраиваем подключение к PostgreSQL из контейнера Testcontainers
            registry.add("spring.datasource.url",
                    () -> postgres.getJdbcUrl() + "?currentSchema=public"); // URL подключения к БД с указанием схемы
            registry.add("spring.datasource.username", postgres::getUsername); // Имя пользователя из контейнера
            registry.add("spring.datasource.password", postgres::getPassword); // Пароль из контейнера

            // Отключаем Liquibase — используем Hibernate для создания схемы в тестах
            registry.add("spring.liquibase.enabled", () -> "false");
            // Hibernate автоматически создаст и удалит схему при старте/завершении тестов
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            // Используем схему public по умолчанию
            registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");

            // Настройки пула соединений Hikari для тестов
            // Более короткий lifecycle для тестов, чтобы не было WARN на shutdown
            registry.add("spring.datasource.hikari.max-lifetime", () -> "30000"); // Максимальное время жизни соединения (30 сек)
            registry.add("spring.datasource.hikari.idle-timeout", () -> "10000"); // Таймаут простоя соединения (10 сек)
            registry.add("spring.datasource.hikari.minimum-idle", () -> "0"); // Минимальное количество простаивающих соединений
            registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5"); // Максимальный размер пула соединений

            // Снижаем уровень логов Hikari в тестах, чтобы скрыть шумные WARN на shutdown
            registry.add("logging.level.com.zaxxer.hikari", () -> "ERROR");

            // Настраиваем подключение к Redis из контейнера Testcontainers
            registry.add("spring.data.redis.host", redis::getHost); // Хост Redis из контейнера
            registry.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379))); // Порт Redis (маппинг порта контейнера)
            
            // Настройки JWT для тестов
            registry.add("jwt.secret", () -> "test-secret-key-for-jwt-generation-in-order-service-integration-tests");
            
            // Мок URL для User Service (будет замокирован через @MockBean)
            registry.add("user.service.url", () -> "http://localhost:9999");
            
            // Упрощенные настройки Circuit Breaker для тестов
            registry.add("resilience4j.circuitbreaker.instances.userService.slidingWindowSize", () -> 5);
            registry.add("resilience4j.circuitbreaker.instances.userService.minimumNumberOfCalls", () -> 2);
            
            // Отключаем Redis репозитории для тестов
            registry.add("spring.data.redis.repositories.enabled", () -> false);
        } else {
            // Конфигурация для CI/CD окружения (GitHub Actions и т.д.)
            // Используем внешние сервисы postgres/redis вместо Testcontainers
            // Читаем параметры подключения из переменных окружения
            String pgHost = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
            String pgPort = System.getenv().getOrDefault("POSTGRES_PORT", "5432");
            String pgDb = System.getenv().getOrDefault("POSTGRES_DB", "os_db");
            String pgUser = System.getenv().getOrDefault("POSTGRES_USER", "postgres");
            String pgPass = System.getenv().getOrDefault("POSTGRES_PASSWORD", "postgres");

            // Формируем URL подключения к PostgreSQL
            String baseUrl = "jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDb;

            // Схема public существует по умолчанию в PostgreSQL, создание не требуется

            // Настраиваем подключение к внешнему PostgreSQL
            registry.add("spring.datasource.url", () -> baseUrl + "?currentSchema=public");
            registry.add("spring.datasource.username", () -> pgUser);
            registry.add("spring.datasource.password", () -> pgPass);

            // Отключаем Liquibase — используем Hibernate для создания схемы в тестах
            registry.add("spring.liquibase.enabled", () -> "false");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("spring.jpa.properties.hibernate.default_schema", () -> "public");

            // Настройки пула соединений Hikari для CI окружения
            registry.add("spring.datasource.hikari.max-lifetime", () -> "30000");
            registry.add("spring.datasource.hikari.idle-timeout", () -> "10000");
            registry.add("spring.datasource.hikari.minimum-idle", () -> "0");
            registry.add("spring.datasource.hikari.maximum-pool-size", () -> "5");

            // Настраиваем подключение к внешнему Redis
            String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
            String redisPort = System.getenv().getOrDefault("REDIS_PORT", "6379");
            registry.add("spring.data.redis.host", () -> redisHost);
            registry.add("spring.data.redis.port", () -> redisPort);
            
            // Настройки JWT для тестов
            registry.add("jwt.secret", () -> "test-secret-key-for-jwt-generation-in-order-service-integration-tests");
            
            // Мок URL для User Service (будет замокирован через @MockBean)
            registry.add("user.service.url", () -> "http://localhost:9999");
            
            // Упрощенные настройки Circuit Breaker для тестов
            registry.add("resilience4j.circuitbreaker.instances.userService.slidingWindowSize", () -> 5);
            registry.add("resilience4j.circuitbreaker.instances.userService.minimumNumberOfCalls", () -> 2);
            
            // Отключаем Redis репозитории для тестов
            registry.add("spring.data.redis.repositories.enabled", () -> false);
        }
    }
}

