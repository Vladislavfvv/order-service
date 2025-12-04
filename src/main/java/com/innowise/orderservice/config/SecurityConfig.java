package com.innowise.orderservice.config;

import java.util.Collections;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import io.jsonwebtoken.security.Keys;

/**
 * Конфигурация Spring Security для user-service.
 * Настраивает OAuth2 Resource Server для валидации JWT токенов от auth-service.
 */
@Configuration
@EnableWebSecurity
@org.springframework.context.annotation.Profile("!test") // Не загружается в тестах, используется TestSecurityConfig
public class SecurityConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Настраивает Security Filter Chain для работы с JWT токенами.
     * 
     * Правила доступа:
     * - ADMIN: доступ ко всем эндпоинтам, включая удаление заказов
     * - USER: доступ только к своим ресурсам (проверка в контроллерах), НЕ может удалять заказы
     * - Публичные эндпоинты: /actuator/health, /actuator/info
     */
    @Bean
    @org.springframework.core.annotation.Order(2) // Низкий приоритет, чтобы тестовая конфигурация могла переопределить
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Публичные эндпоинты - доступны без аутентификации для мониторинга
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Эндпоинты только для ADMIN
                        .requestMatchers("/api/cache/**").hasRole("ADMIN")
                        
                        // Эндпоинты для работы с товарами
                        .requestMatchers(HttpMethod.GET, "/api/v1/items", "/api/v1/items/**").hasAnyRole("ADMIN", "USER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/items").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/items/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/items/**").hasRole("ADMIN")
                        
                        // Эндпоинты для получения списка всех заказов - только ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders").hasRole("ADMIN")
                        
                        // Эндпоинт для получения заказов текущего пользователя - доступен USER и ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/my").hasAnyRole("ADMIN", "USER")
                        
                        // Эндпоинты для получения заказов по ID и статусам - доступны USER и ADMIN
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/ids", "/api/v1/orders/statuses").hasAnyRole("ADMIN", "USER")
                        
                        // Эндпоинт для получения заказа по ID - USER может получить только свой заказ, ADMIN - любой
                        // Проверка доступа выполняется в OrderService
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/{id}").hasAnyRole("ADMIN", "USER")
                        
                        // Эндпоинт для создания заказов из токена - требует аутентификации
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").hasAnyRole("ADMIN", "USER")
                        
                        // Эндпоинт для обновления заказов - USER может обновлять только свои заказы, ADMIN - любые
                        // Проверка доступа выполняется в OrderService
                        .requestMatchers(HttpMethod.PUT, "/api/v1/orders/**").hasAnyRole("ADMIN", "USER")
                        
                        // Эндпоинт для удаления заказов - только ADMIN(USER не может удалять заказы - получит 403 Forbidden)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/orders/**").hasRole("ADMIN")
                        
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * Создает JWT Decoder для валидации токенов от auth-service.
     * Использует тот же секрет, что и auth-service (HMAC SHA-256).
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    /**
     * Создает JWT Authentication Converter для правильной обработки ролей из токена.
     * Извлекает роль из claim "role" и преобразует её в GrantedAuthority.
     * Поддерживает как "ROLE_USER"/"ROLE_ADMIN", так и "USER"/"ADMIN".
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Убираем префикс ROLE_, если он есть (Spring Security добавит его автоматически)
            String authority = role.startsWith("ROLE_") ? role.substring(5) : role;
            
            return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + authority));
        });
        return converter;
    }
}

