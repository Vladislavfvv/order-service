package com.innowise.orderservice.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Тестовая конфигурация безопасности для интеграционных тестов.
 * Отключает проверку безопасности, чтобы упростить тестирование REST API.
 * ВАЖНО: Это используется только для интеграционных тестов контроллера,
 * чтобы избежать проблем с мокированием JwtDecoder в Spring Security Test.
 * 
 * Используем @Primary и @Order(1) для обеспечения приоритета над SecurityConfig.
 */
@TestConfiguration
public class TestSecurityConfig {
    @Bean
    @Primary
    @Order(1)
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
