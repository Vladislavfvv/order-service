package com.innowise.orderservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
        // TODO: Реализовать получение userId из JWT токена
        // Пока используем заглушку
        if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            // Попытка получить userId из токена
            Object userIdObj = jwt.getClaim("userId");
            if (userIdObj instanceof Number) {
                return ((Number) userIdObj).longValue();
            }
        }
        // Заглушка - в реальном приложении нужно правильно извлекать
        return 1L;
    }

    /**
     * Извлекает токен из Authentication для передачи в User Service
     */
    private String getAuthToken(Authentication authentication) {
        // TODO: Реализовать извлечение токена из Authentication
        // Пока возвращаем null - в реальном приложении нужно извлечь из SecurityContext
        return null;
    }
}

