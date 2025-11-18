package com.innowise.orderservice.client;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.innowise.orderservice.dto.UserDto;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${user.service.url:http://user-service:8080}")
    private String userServiceUrl;

    @CircuitBreaker(name = "userService", fallbackMethod = "getUserByIdFallback")
    public UserDto getUserById(long userId, String authToken) {
        try {
            String baseUrl = Objects.requireNonNullElse(userServiceUrl, "http://user-service:8080");
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            // Используем REST API user-service (версия v1) для получения пользователя по id
                            .path("/api/v1/users/id")
                            .queryParam("id", userId)
                            .build())
                    // Прокидываем исходный Authorization заголовок, чтобы user-service мог валидировать токен
                    .header("Authorization", buildAuthorizationHeader(authToken))
                    .retrieve()
                    .bodyToMono(UserDto.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling User Service for userId {}: {}", userId, e.getMessage());
            throw new UserServiceException("Failed to get user by id: " + userId, e);
        } catch (Exception e) {
            log.error("Unexpected error calling User Service for userId {}: {}", userId, e.getMessage());
            throw new UserServiceException("Unexpected error getting user by id: " + userId, e);
        }
    }

    /**
     * Fallback method вызывается когда Circuit Breaker открыт или произошла ошибка
     */
    public UserDto getUserByIdFallback(long userId, String authToken, Throwable e) {
        log.warn("Circuit Breaker opened or error occurred. Returning fallback for userId: {}", userId, e);
        // Возвращаем минимальную информацию о пользователе
        return new UserDto(userId, "Unknown", "User", null, null);
    }

    private String buildAuthorizationHeader(String authToken) {
        if (authToken == null || authToken.isBlank()) {
            return "";
        }
        return authToken.startsWith("Bearer") ? authToken : "Bearer " + authToken;
    }

    public static class UserServiceException extends RuntimeException {
        public UserServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

