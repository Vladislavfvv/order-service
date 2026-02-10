package com.innowise.orderservice.integration;

import com.github.tomakehurst.wiremock.junit5.*;
import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.UserDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
}


