package com.innowise.orderservice.util;

import java.util.Base64;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Утилитный класс для работы с Spring Security и проверки прав доступа.
 */
public class SecurityUtils {

    /**
     * Проверяет, является ли пользователь администратором.
     * Возвращает true, если пользователь имеет роль ADMIN.
     */
    public static boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN"));
    }

    /**
     * Извлекает email пользователя из JWT токена (claim "sub").
     * Выбрасывает IllegalStateException, если токен не содержит email.
     */
    public static String getEmailFromToken(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("Authentication is required");
        }

        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new IllegalStateException("JWT token is required");
        }

        String email = jwt.getSubject();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Email (sub claim) not found in JWT token");
        }

        return email;
    }

    /**
     * Извлекает email пользователя из строки JWT токена (claim "sub").
     * Принимает токен в формате "Bearer {token}" или просто "{token}".
     * Выбрасывает IllegalStateException, если токен не содержит email.
     */
    public static String getEmailFromTokenString(String tokenString) {
        if (tokenString == null || tokenString.isBlank()) {
            throw new IllegalStateException("Token string is required");
        }

        // Убираем префикс "Bearer " если есть
        String token = tokenString.startsWith("Bearer ") 
                ? tokenString.substring(7) 
                : tokenString;

        try {
            // JWT токен состоит из трех частей: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalStateException("Invalid JWT token format");
            }

            // Декодируем payload (вторая часть)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            
            // Парсим JSON payload
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(payload);
            
            // Извлекаем email из claim "sub"
            String email = jsonNode.get("sub").asText();
            if (email == null || email.isBlank()) {
                throw new IllegalStateException("Email (sub claim) not found in JWT token");
            }

            return email;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract email from token: " + e.getMessage(), e);
        }
    }

    /**
     * Извлекает строку токена из Authentication объекта для передачи в другие сервисы.
     * Возвращает токен в формате "Bearer {token}".
     * Выбрасывает IllegalStateException, если токен не найден.
     */
    public static String getTokenString(Authentication authentication) {
        if (authentication == null) {
            throw new IllegalStateException("Authentication is required");
        }

        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new IllegalStateException("JWT token is required");
        }

        return "Bearer " + jwt.getTokenValue();
    }

    /**
     * Извлекает JWT из Authentication объекта.
     * Возвращает JWT токен или null, если не удалось извлечь.
     */
    private static Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    /**
     * Проверяет доступ пользователя к ресурсу.
     * ADMIN имеет доступ ко всем ресурсам, USER - только к своим (проверка по email).
     * Возвращает true, если пользователь имеет доступ к ресурсу.
     */
    public static boolean hasAccess(Authentication authentication, String resourceOwnerEmail) {
        if (authentication == null) {
            return false;
        }

        if (isAdmin(authentication)) {
            return true;
        }

        try {
            String userEmail = getEmailFromToken(authentication);
            return userEmail.equals(resourceOwnerEmail);
        } catch (IllegalStateException e) {
            return false;
        }
    }
}

