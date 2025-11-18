package com.innowise.orderservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderWithUserDto> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {
        log.info("Creating order for user: {}", authentication.getName());
        
        // Получаем userId из токена (предполагается, что в токене есть userId)
        Long userId = getUserIdFromAuthentication(authentication);
        String authToken = getAuthToken(authentication);
        
        OrderWithUserDto order = orderService.createOrder(userId, request, authToken);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderWithUserDto> getOrderById(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("Getting order by ID: {}", id);
        
        String authToken = getAuthToken(authentication);
        OrderWithUserDto order = orderService.getOrderById(id, authToken);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/ids")
    public ResponseEntity<List<OrderWithUserDto>> getOrdersByIds(
            @RequestParam List<Long> ids,
            Authentication authentication) {
        log.info("Getting orders by IDs: {}", ids);
        
        String authToken = getAuthToken(authentication);
        List<OrderWithUserDto> orders = orderService.getOrdersByIds(ids, authToken);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/statuses")
    public ResponseEntity<List<OrderWithUserDto>> getOrdersByStatuses(
            @RequestParam List<OrderStatus> statuses,
            Authentication authentication) {
        log.info("Getting orders by statuses: {}", statuses);
        
        String authToken = getAuthToken(authentication);
        List<OrderWithUserDto> orders = orderService.getOrdersByStatuses(statuses, authToken);
        return ResponseEntity.ok(orders);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderWithUserDto> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request,
            Authentication authentication) {
        log.info("Updating order ID: {}", id);
        
        String authToken = getAuthToken(authentication);
        OrderWithUserDto order = orderService.updateOrder(id, request, authToken);
        return ResponseEntity.ok(order);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        log.info("Deleting order ID: {}", id);
        
        // Delete не возвращает user info согласно требованиям
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Извлекает userId из Authentication объекта
     * В реальном приложении нужно получить из JWT токена
     */
    private Long getUserIdFromAuthentication(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new IllegalStateException("JWT token is required to extract userId");
        }

        Object userIdClaim = jwt.getClaims().get("userId");
        if (userIdClaim == null) {
            userIdClaim = jwt.getClaims().get("user_id");
        }
        if (userIdClaim == null) {
            userIdClaim = jwt.getSubject();
        }

        Long userId = convertClaimToLong(userIdClaim);
        if (userId == null) {
            throw new IllegalStateException("Cannot extract userId from JWT claims");
        }
        return userId;
    }

    /**
     * Извлекает токен из Authentication для передачи в User Service
     */
    private String getAuthToken(Authentication authentication) {
        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new IllegalStateException("JWT token is required to propagate Authorization header");
        }
        return "Bearer " + jwt.getTokenValue();
    }

    private Jwt extractJwt(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken();
        }
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        return null;
    }

    private Long convertClaimToLong(Object claimValue) {
        if (claimValue instanceof Number number) {
            return number.longValue();
        }
        if (claimValue instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

