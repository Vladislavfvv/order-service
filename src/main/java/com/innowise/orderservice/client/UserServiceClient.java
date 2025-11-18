package com.innowise.orderservice.client;

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

    @Value("${user.service.api-key:}")
    private String apiKey;

    @CircuitBreaker(name = "userService", fallbackMethod = "getUserByEmailFallback")
    public UserDto getUserByEmail(String email, String authToken) {
        try {
            WebClient webClient = webClientBuilder.baseUrl(userServiceUrl).build();
            
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/users/email")
                            .queryParam("email", email)
                            .build())
                    .header("Authorization", authToken != null ? authToken : "Bearer ")
                    .retrieve()
                    .bodyToMono(UserDto.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Error calling User Service for email {}: {}", email, e.getMessage());
            throw new UserServiceException("Failed to get user by email: " + email, e);
        } catch (Exception e) {
            log.error("Unexpected error calling User Service for email {}: {}", email, e.getMessage());
            throw new UserServiceException("Unexpected error getting user by email: " + email, e);
        }
    }

    /**
     * Fallback method вызывается когда Circuit Breaker открыт или произошла ошибка
     */
    public UserDto getUserByEmailFallback(String email, String authToken, Exception e) {
        log.warn("Circuit Breaker opened or error occurred. Returning fallback for email: {}", email);
        // Возвращаем минимальную информацию о пользователе
        return new UserDto(null, "Unknown", "User", null, email);
    }

    public static class UserServiceException extends RuntimeException {
        public UserServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

