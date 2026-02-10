package com.innowise.orderservice.integration;

import com.github.tomakehurst.wiremock.junit5.*;
import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.UserDto;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Интеграционный тест, демонстрирующий использование WireMock + Testcontainers.
 * <p>
 * Здесь мы НЕ мокируем UserServiceClient, а поднимаем настоящий HTTP-сервер WireMock
 * на http://localhost:9999 и настраиваем ответы для запросов к user-service.
 * <p>
 * Схема:
 * OrderService -> UserServiceClient (WebClient) -> WireMock (эмулирует user-service)
 */
// Используем JUnit5-расширение WireMock 3 (jetty12) для подъёма mock-сервера на 9999 порту
@WireMockTest(httpPort = 9999)
class UserServiceClientWireMockIT extends BaseIntegrationTest {

    @Autowired
    private UserServiceClient userServiceClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void resetCircuitBreaker() {
        circuitBreakerRegistry.circuitBreaker("userService").reset();
    }

    @Test
    @DisplayName("UserServiceClient.getUserById интегрируется с WireMock")
    void getUserById_UsesWireMock() {
        // given
        long userId = 1L;
        String authToken = "Bearer test-token";

        // Настраиваем WireMock: если придет GET /api/v1/users/id?id=1 -> вернуть JSON пользователя
        stubFor(get(urlPathEqualTo("/api/v1/users/id"))
                .withQueryParam("id", equalTo(String.valueOf(userId)))
                .willReturn(okJson("""
                        {
                          "id": 1,
                          "firstName": "Test",
                          "lastName": "User",
                          "email": "test@example.com",
                          "birthDate": "2000-01-01"
                        }
                        """)));

        // when
        UserDto result = userServiceClient.getUserById(userId, authToken);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test", result.getFirstName());
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    @DisplayName("UserServiceClient.getUserByEmail интегрируется с WireMock")
    void getUserByEmail_UsesWireMock() {
        // given
        String email = "test@example.com";
        String authToken = "Bearer test-token";

        // Настраиваем WireMock: если придет GET /api/v1/users/email?email=test@example.com -> вернуть JSON пользователя
        stubFor(get(urlPathEqualTo("/api/v1/users/email"))
                .withQueryParam("email", equalTo(email))
                .willReturn(okJson("""
                        {
                          "id": 1,
                          "firstName": "Test",
                          "lastName": "User",
                          "email": "test@example.com",
                          "birthDate": "2000-01-01"
                        }
                        """)));

        // when
        UserDto result = userServiceClient.getUserByEmail(email, authToken);

        // then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test", result.getFirstName());
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    @DisplayName("CircuitBreaker для userService открывается после ошибок и больше не дергает user-service")
    void circuitBreaker_opens_after_errors_and_uses_fallback_without_http_call() {
        long userId = 1L;
        String authToken = "Bearer test-token";
        // Сбрасываем состояние CircuitBreaker перед тестом, чтобы предыдущие тесты не влияли
//        circuitBreakerRegistry.circuitBreaker("userService").reset();
        // В тестовом профиле minimumNumberOfCalls=2 и failureRateThreshold=50,
        // поэтому двух последовательных неудачных вызовов достаточно, чтобы открыть CircuitBreaker.
        // WireMock всегда возвращает 500 для /api/v1/users/id?id=1
        stubFor(get(urlPathEqualTo("/api/v1/users/id"))
                .withQueryParam("id", equalTo(String.valueOf(userId)))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"message\":\"Internal error\"}")));

        // 1-й вызов — ошибка -> fallback
        UserDto r1 = userServiceClient.getUserById(userId, authToken);
        // 2-й вызов — снова ошибка -> fallback, после него CircuitBreaker уже может перейти в OPEN
        UserDto r2 = userServiceClient.getUserById(userId, authToken);
        // 3-й вызов — CircuitBreaker должен быть OPEN и сразу пойти в fallback, НЕ обращаясь к WireMock
        UserDto r3 = userServiceClient.getUserById(userId, authToken);

        // Проверяем, что все три вызова вернули fallback-объект
        assertEquals("Unknown", r1.getFirstName());
        assertEquals("Unknown", r2.getFirstName());
        assertEquals("Unknown", r3.getFirstName());

        // Проверяем, что HTTP-запрос реально ушёл только 2 раза, а третий вызов уже не ходил в WireMock
        verify(2, getRequestedFor(urlPathEqualTo("/api/v1/users/id"))
                .withQueryParam("id", equalTo(String.valueOf(userId))));
    }

    @Test
    @DisplayName("CircuitBreaker для userService по email открывается после ошибок и использует fallback без HTTP вызова")
    void circuitBreaker_opens_after_errors_and_uses_fallback_for_getUserByEmail() {
        String email = "test@example.com";
        String authToken = "Bearer test-token";
        // Сбрасываем состояние CircuitBreaker перед тестом, чтобы предыдущие тесты не влияли
//        circuitBreakerRegistry.circuitBreaker("userService").reset();
        // В тестовом профиле minimumNumberOfCalls=2 и failureRateThreshold=50,
        // поэтому двух последовательных неудачных вызовов достаточно, чтобы открыть CircuitBreaker.

        // WireMock всегда возвращает 500 для /api/v1/users/email?email=test@example.com
        stubFor(get(urlPathEqualTo("/api/v1/users/email"))
                .withQueryParam("email", equalTo(email))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("{\"message\":\"Internal error\"}")));

        // 1-й вызов — ошибка -> fallback
        UserDto r1 = userServiceClient.getUserByEmail(email, authToken);
        // 2-й вызов — снова ошибка -> fallback, после него CircuitBreaker уже может перейти в OPEN
        UserDto r2 = userServiceClient.getUserByEmail(email, authToken);
        // 3-й вызов — CircuitBreaker должен быть OPEN и сразу пойти в fallback, НЕ обращаясь к WireMock
        UserDto r3 = userServiceClient.getUserByEmail(email, authToken);

        // Проверяем, что все три вызова вернули fallback-объект (Unknown User с тем же email)
        assertEquals("Unknown", r1.getFirstName());
        assertEquals(email, r1.getEmail());
        assertEquals("Unknown", r2.getFirstName());
        assertEquals(email, r2.getEmail());
        assertEquals("Unknown", r3.getFirstName());
        assertEquals(email, r3.getEmail());

        // Проверяем, что HTTP-запрос реально ушёл только 2 раза, а третий вызов уже не ходил в WireMock
        verify(2, getRequestedFor(urlPathEqualTo("/api/v1/users/email"))
                .withQueryParam("email", equalTo(email)));
    }
}


