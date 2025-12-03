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
//поднимает значение из application.properties / application.yml. Если ключ не задан, используется значение по умолчанию http://user-service:8080
    @Value("${user.service.url:http://user-service:8080}")
    private String userServiceUrl;
//Аннотация подключает Resilience4j circuit breaker с именем userService. Параметры (когда открывается цепь, как долго держаться открытой и т.д.) настраиваются в application.yml/properties
    //fallbackMethod = "getUserByIdFallback" — если вызов закончился исключением или схема circuit breaker открыта (т.е. сервис считается недоступным), будет вызван метод getUserByIdFallback
    //Очень важно: подпись fallback-метода должна соответствовать оригинальной + иметь дополнительный параметр Throwable в конце. В коде это выполнено: getUserByIdFallback(long userId, String authToken, Throwable e) — правильно
    @CircuitBreaker(name = "userService", fallbackMethod = "getUserByIdFallback")
    public UserDto getUserById(long userId, String authToken) {
        try {
            String baseUrl = Objects.requireNonNullElse(userServiceUrl, "http://user-service:8080");
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build(); //получаем WebClient, у которого базовый URL выставлен.

            return webClient.get() //получаем WebClient, у которого базовый URL выставлен.
                    .uri(uriBuilder -> uriBuilder //строим конечный путь запроса и query-параметры.
                            .path("/api/v1/users/id")
                            .queryParam("id", userId)
                            .build()) //строим конечный путь запроса и query-параметры.
                    .header("Authorization", buildAuthorizationHeader(authToken)) //пробрасываем заголовок авторизации через buildAuthorizationHeader
                    .retrieve() //запускаем запрос, получаем Mono<UserDto> и блокируем текущий поток до получения ответа
                    .bodyToMono(UserDto.class) //преобразуем ответ от User Service в UserDto
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
            WebClient webClient = webClientBuilder.baseUrl(baseUrl).build();//получаем WebClient, у которого базовый URL выставлен.

            return webClient.get() //получаем WebClient, у которого базовый URL выставлен.
                    .uri(uriBuilder -> uriBuilder//строим конечный путь запроса и query-параметры.
                            .path("/api/v1/users/email") //путь запроса
                            .queryParam("email", email)
                            .build()) //строим конечный путь запроса и query-параметры.
                    .header("Authorization", buildAuthorizationHeader(authToken)) //пробрасываем заголовок авторизации через buildAuthorizationHeader
                    .retrieve()//запускаем запрос, получаем Mono<UserDto> и блокируем текущий поток до получения ответа
                    .bodyToMono(UserDto.class)
                    .block();
        } catch (WebClientResponseException e) { //Ошибки HTTP/сетевая ошибка: WebClientResponseException ловится отдельно (это исключение для ошибок ответа 4xx/5xx и др.). Любая другая ошибка ловится в общем Exception блоке
            log.error("Error calling User Service for email {}: {}", email, e.getMessage());
            throw new UserServiceException("Failed to get user by email: " + email, e);
        } catch (Exception e) {//В обоих случаях при ошибке бросается пользовательское UserServiceException (runtime exception)
            log.error("Unexpected error calling User Service for email {}: {}", email, e.getMessage());
            throw new UserServiceException("Unexpected error getting user by email: " + email, e);
        }
    }

    /**
     * Fallback method для getUserByEmail
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

    //Формирует корректный заголовок Authorization. Если authToken уже начинается с "Bearer", возвращает как есть; иначе добавляет префикс "Bearer "
    private String buildAuthorizationHeader(String authToken) {
        if (authToken == null || authToken.isBlank()) { //если authToken null или пустой, возвращаем пустую строку
            return "";
        }
        return authToken.startsWith("Bearer") ? authToken : "Bearer " + authToken;
    }

    //При ошибках сетевого вызова
    public static class UserServiceException extends RuntimeException {
        public UserServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

