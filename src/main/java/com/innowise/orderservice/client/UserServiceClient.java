package com.innowise.orderservice.client;

import java.util.Objects;

import com.innowise.orderservice.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.innowise.orderservice.dto.UserDto;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Клиент для вызова User Service.
 * <p>
 * Circuit Breaker (Resilience4j, инстанс {@code userService}) защищает от каскадных сбоев,
 * когда user-service недоступен или часто падает с ошибками:
 * <ul>
 *   <li><b>CLOSED</b> — вызовы идут в user-service. При накоплении ошибок (см. конфиг) цепь открывается.</li>
 *   <li><b>OPEN</b> — вызовы в user-service не выполняются; сразу вызывается fallback-метод.</li>
 *   <li><b>HALF_OPEN</b> — после {@code waitDurationInOpenState} делаются пробные вызовы;
 *   при успехе цепь закрывается, при ошибках — снова OPEN.</li>
 * </ul>
 * Конфигурация: {@code resilience4j.circuitbreaker.instances.userService.*} в
 * {@code application.properties} / {@code application-kubernetes.properties} / тестах.
 * Fallback-методы возвращают «заглушку» {@link UserDto} (Unknown User), чтобы order-service
 * продолжал работать без user-service (например, отображение заказов с минимальными данными о пользователе).
 */
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

            return webClient.get() //получаем WebClient, у которого базовый URL выставлен.
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/users/id")
                            .queryParam("id", userId)
                            .build()) //строим конечный путь запроса и query-параметры.
                    .header("Authorization", buildAuthorizationHeader(authToken)) //пробрасываем заголовок авторизации через buildAuthorizationHeader
                    .retrieve() //запускаем запрос, до этого было только формирование запроса, а после, получаем Mono<UserDto>, есть FLUX
                    .bodyToMono(UserDto.class) // Читает тело HTTP ответа и конвертирует JSON в объект UserDto
                    // Возвращает Mono<UserDto> (обещание UserDto в будущем)
                    // Для коллекций
                    //.bodyToFlux(UserDto.class)       // List<UserDto> → Flux<UserDto>
                    // Для строки
                    //.bodyToMono(String.class)        // Просто текст ответа
                    // Для массива байтов
                    //.bodyToMono(byte[].class)
                    // С кастомным десериализатором
                    //.bodyToMono(new ParameterizedTypeReference<List<UserDto>>() {})
                    .block(); //блокируем текущий поток до получения ответа
        } catch (WebClientResponseException e) {
            log.error("Error calling User Service for userId {}: {}", userId, e.getMessage()); //логируем ошибку
            throw new UserServiceException("Failed to get user by id: " + userId, e);
        } catch (Exception e) {
            log.error("Unexpected error calling User Service for userId {}: {}", userId, e.getMessage());
            throw new UserServiceException("Unexpected error getting user by id: " + userId, e);
        }
    }


    @CircuitBreaker(name = "userService", fallbackMethod = "getUserByEmailFallback")
    public UserDto getUserByEmail(String email, String authToken) {
        try {
            String baseUrl = Objects.requireNonNullElse(userServiceUrl, "http://user-service:8080"); //если поле userServiceUrl null, используется запасной URL
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();

            return webClient.get() //получаем WebClient, у которого базовый URL выставлен.
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/v1/users/email") //путь запроса
                            .queryParam("email", email)
                            .build()) //строим конечный путь запроса и query-параметры.
                    .header("Authorization", buildAuthorizationHeader(authToken)) //пробрасываем заголовок авторизации через buildAuthorizationHeader
                    .retrieve()//запускаем запрос, получаем Mono<UserDto> и блокируем текущий поток до получения ответа
                    .bodyToMono(UserDto.class)
                    .block();
        } catch (
                WebClientResponseException e) { //Ошибки HTTP/сетевая ошибка: WebClientResponseException ловится отдельно (это исключение для ошибок ответа 4xx/5xx и др.). Любая другая ошибка ловится в общем Exception блоке
            log.error("Error calling User Service for email {}: {}", email, e.getMessage());
            throw new UserServiceException("Failed to get user by email: " + email, e);
        } catch (Exception e) {
            log.error("Unexpected error calling User Service for email {}: {}", email, e.getMessage());
            throw new UserServiceException("Unexpected error getting user by email: " + email, e);
        }
    }

    /**
     * ======================================Fallback methods для getUserBy smth=======================
     */
    public UserDto getUserByEmailFallback(String email, String authToken, Throwable e) {
        log.warn("Circuit Breaker opened or error occurred. Returning fallback for email: {}", email, e);
        // Возвращаем минимальную информацию о пользователе
        return new UserDto(null, "Unknown", "User", null, email);
    }

    /**
     * Fallback method вызывается когда Circuit Breaker открыт или произошла ошибка
     */
    //Fallback-методы в коде возвращают «минимально информативного» UserDto и логируют предупреждение:
    public UserDto getUserByIdFallback(long userId, String authToken, Throwable e) {
        log.warn("Circuit Breaker opened or error occurred. Returning fallback for userId: {}", userId, e);
        // Возвращаем минимальную информацию о пользователе
        return new UserDto(userId, "Unknown", "User", null, null);
    }

    //Формирует корректный заголовок Authorization.
    // Если authToken уже начинается с "Bearer", возвращает как есть; иначе добавляет префикс "Bearer "
    private String buildAuthorizationHeader(String authToken) {
        if (authToken == null || authToken.isBlank()) { //если authToken null или пустой, возвращаем пустую строку
            return "";
        }
        return authToken.startsWith("Bearer") ? authToken : "Bearer " + authToken;
    }
}

