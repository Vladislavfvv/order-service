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
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderWithUserDto> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {

        log.info("Creating order for user: {}", authentication.getName());
        
        // Передаем только Authentication - сервис сам извлечет email и токен из него
        // Сервис получит userId через UserServiceClient
        OrderWithUserDto order = orderService.createOrder(request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * Получает все заказы.
     * Доступно только администраторам.
     *
     * @param authentication аутентификация администратора
     * @return список всех заказов
     */
    @GetMapping
    public ResponseEntity<List<OrderWithUserDto>> getAllOrders(Authentication authentication) {
        log.info("Getting all orders by admin: {}", authentication != null ? authentication.getName() : "unknown");

        String authToken = getAuthToken(authentication);
        List<OrderWithUserDto> orders = orderService.getAllOrders(authToken);
        return ResponseEntity.ok(orders);
    }

    /**
     * Получает все заказы текущего пользователя.
     * Пользователь может видеть только свои заказы.
     * ВАЖНО: Этот метод должен быть объявлен ПЕРЕД @GetMapping("/{id}"),
     * чтобы Spring правильно обрабатывал маршрут /my, а не пытался интерпретировать "my" как ID.
     * 
     * @param authentication объект аутентификации, содержащий JWT токен
     * @return список заказов текущего пользователя
     */
    @GetMapping("/my")
    public ResponseEntity<List<OrderWithUserDto>> getMyOrders(Authentication authentication) {
        log.info("Getting orders for current user: {}", authentication.getName());
        
        List<OrderWithUserDto> orders = orderService.getMyOrders(authentication);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/ids")
    public ResponseEntity<List<OrderWithUserDto>> getOrdersByIds(
            @RequestParam List<Long> ids,
            Authentication authentication) {
        log.info("Getting orders by IDs: {}", ids);
        
        // Передаем Authentication для проверки прав доступа в сервисе
        List<OrderWithUserDto> orders = orderService.getOrdersByIds(ids, authentication);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/statuses")
    public ResponseEntity<List<OrderWithUserDto>> getOrdersByStatuses(
            @RequestParam List<OrderStatus> statuses,
            Authentication authentication) {
        log.info("Getting orders by statuses: {}", statuses);
        
        // Передаем Authentication для проверки прав доступа в сервисе
        List<OrderWithUserDto> orders = orderService.getOrdersByStatuses(statuses, authentication);
        return ResponseEntity.ok(orders);
    }

    /**
     * Получает заказ по ID.
     * ADMIN: может получить любой заказ.
     * USER: может получить только свой заказ.
     * 
     * @param id ID заказа
     * @param authentication объект аутентификации для проверки прав доступа
     * @return заказ с информацией о пользователе
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderWithUserDto> getOrderById(
            @PathVariable Long id,
            Authentication authentication) {
        log.info("Getting order by ID: {}", id);
        
        // Передаем Authentication для проверки прав доступа в сервисе
        OrderWithUserDto order = orderService.getOrderById(id, authentication);
        return ResponseEntity.ok(order);
    }

    /**
     * Обновляет статус заказа.
     * Пользователь может обновлять только свои заказы, ADMIN - любые заказы.
     * 
     * @param id ID заказа для обновления
     * @param request запрос с новым статусом заказа
     * @param authentication объект аутентификации для проверки прав доступа
     * @return обновленный заказ с информацией о пользователе
     */
    @PutMapping("/{id}")
    public ResponseEntity<OrderWithUserDto> updateOrder(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderRequest request,
            Authentication authentication) {
        log.info("Updating order ID: {} by user: {}", id, authentication != null ? authentication.getName() : "unknown");
        
        // Передаем Authentication для проверки прав доступа в сервисе
        OrderWithUserDto order = orderService.updateOrder(id, request, authentication);
        return ResponseEntity.ok(order);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(
            @PathVariable Long id,
            Authentication authentication) {
        String userName = authentication != null ? authentication.getName() : "unknown";
        log.info("Deleting order ID: {} by user: {}", id, userName);
        
        // Проверка прав доступа выполняется в SecurityConfig (только ADMIN) и в OrderService
        orderService.deleteOrder(id, authentication);
        return ResponseEntity.noContent().build();
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
}

