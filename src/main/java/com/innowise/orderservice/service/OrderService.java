package com.innowise.orderservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.dto.UserDto;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.mapper.OrderItemMapper;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.util.SecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserServiceClient userServiceClient;

    @Transactional
    public OrderWithUserDto createOrder(CreateOrderRequest request, Authentication authentication) {
        // Шаг 1: Извлекаем email из Authentication объекта (из JWT токена)
        String email = SecurityUtils.getEmailFromToken(authentication);
        log.info("Creating order for user email: {}", email);

        // Шаг 2: Извлекаем токен для передачи в User Service
        String authToken = SecurityUtils.getTokenString(authentication);

        // Шаг 3: Получаем информацию о пользователе из user-service по email
        // Это необходимо, чтобы получить userId, который нужен для создания заказа
        UserDto user = getUserInfoByEmail(email, authToken);
        Long userId = user.getId();
        if (userId == null) {
            throw new IllegalStateException("User ID not found for email: " + email);
        }

        // Шаг 4: userId теперь известен, можно создавать заказ

        // Создаем заказ
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.NEW);
        order.setCreation_date(LocalDateTime.now());

        // Создаем элементы заказа
        List<OrderItem> orderItems = request.getItems().stream()
                .map(itemDto -> {
                    Item item = itemRepository.findById(itemDto.getItemId())
                            .orElseThrow(() -> new ItemNotFoundException("Item not found: " + itemDto.getItemId()));

                    OrderItem orderItem = new OrderItem();
                    orderItem.setOrder(order);
                    orderItem.setItem(item);
                    orderItem.setQuantity(itemDto.getQuantity());
                    return orderItem;
                })
                .collect(Collectors.toList());

        order.setItems(orderItems);
        
        // Сохраняем заказ (cascade сохранит orderItems)
        final Order savedOrder = orderRepository.save(order);

        // Возвращаем заказ с информацией о пользователе (уже полученной выше)
        OrderDto orderDto = orderMapper.toDto(savedOrder);
        return new OrderWithUserDto(orderDto, user);
    }

    /**
     * Получает заказ по ID.
     * ADMIN: может получить любой заказ.
     * USER: может получить только свой заказ.
     * 
     * @param id ID заказа
     * @param authentication объект аутентификации для проверки прав доступа
     * @return заказ с информацией о пользователе
     * @throws AccessDeniedException если пользователь пытается получить чужой заказ
     */
    @Transactional(readOnly = true)
    public OrderWithUserDto getOrderById(Long id, Authentication authentication) {
        log.info("Getting order by ID: {}", id);

        if (authentication == null) {
            throw new IllegalStateException("Authentication is required");
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        // Проверка прав доступа: пользователь может получить только свой заказ, ADMIN - любой
        if (!SecurityUtils.isAdmin(authentication)) {
            // Извлекаем email текущего пользователя из токена
            String userEmail = SecurityUtils.getEmailFromToken(authentication);
            
            // Извлекаем токен для получения информации о владельце заказа
            String authToken = SecurityUtils.getTokenString(authentication);
            
            // Получаем информацию о владельце заказа
            Long orderOwnerId = Objects.requireNonNull(order.getUserId(), "Order userId cannot be null");
            UserDto orderOwner = getUserInfo(orderOwnerId, authToken);
            String orderOwnerEmail = orderOwner.getEmail();
            
            if (orderOwnerEmail == null || orderOwnerEmail.isBlank()) {
                log.warn("Email not found for order owner userId: {}", orderOwnerId);
                throw new IllegalStateException("Cannot verify order ownership: email not found for order owner");
            }
            
            // Проверяем, что текущий пользователь является владельцем заказа
            if (!userEmail.equals(orderOwnerEmail)) {
                log.warn("User {} attempted to get order {} owned by {}", userEmail, id, orderOwnerEmail);
                throw new AccessDeniedException("Access denied: You can only access your own orders");
            }
            
            log.info("User {} is accessing their own order {}", userEmail, id);
        } else {
            log.info("ADMIN user {} is accessing order {}", authentication.getName(), id);
        }

        // Извлекаем токен для получения информации о пользователе
        String authToken = SecurityUtils.getTokenString(authentication);
        
        // Получаем email владельца заказа через userId, затем используем email для получения user info
        return getOrderWithUserDto(authToken, order);
    }

    @NotNull
    private OrderWithUserDto getOrderWithUserDto(String authToken, Order order) {
        long orderOwnerId = Objects.requireNonNull(order.getUserId(), "Order userId cannot be null");
        UserDto tempUser = getUserInfo(orderOwnerId, authToken);
        String email = tempUser.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Email not found for userId: " + orderOwnerId);
        }
        UserDto user = getUserInfoByEmail(email, authToken);

        OrderDto orderDto = orderMapper.toDto(order);
        return new OrderWithUserDto(orderDto, user);
    }

    @Transactional(readOnly = true)
    public List<OrderWithUserDto> getOrdersByIds(List<Long> ids, String authToken) {
        log.info("Getting orders by IDs: {}", ids);

        List<Order> orders = orderRepository.findAllByIdIn(ids);
        
        return orders.stream()
                .map(order -> {
                    // Получаем email владельца заказа через userId, затем используем email для получения user info
                    long orderOwnerId = Objects.requireNonNull(order.getUserId(), "Order userId cannot be null");
                    UserDto tempUser = getUserInfo(orderOwnerId, authToken);
                    String email = tempUser.getEmail();
                    UserDto user;
                    if (email == null || email.isBlank()) {
                        log.warn("Email not found for userId: {}, using tempUser directly", orderOwnerId);
                        user = tempUser;
                    } else {
                        user = getUserInfoByEmail(email, authToken);
                    }
                    OrderDto orderDto = orderMapper.toDto(order);
                    return new OrderWithUserDto(orderDto, user);
                })
                .collect(Collectors.toList());
    }

    /**
     * Получает все заказы в системе.
     * Доступно только для администраторов (проверяется в SecurityConfig).
     *
     * @param authToken токен для вызова user-service
     * @return список всех заказов с информацией о пользователях
     */
    @Transactional(readOnly = true)
    public List<OrderWithUserDto> getAllOrders(String authToken) {
        log.info("Getting all orders");

        List<Order> orders = orderRepository.findAll();

        return orders.stream()
                .map(order -> {
                    long orderOwnerId = Objects.requireNonNull(order.getUserId(), "Order userId cannot be null");
                    UserDto tempUser = getUserInfo(orderOwnerId, authToken);
                    String email = tempUser.getEmail();
                    UserDto user;
                    if (email == null || email.isBlank()) {
                        log.warn("Email not found for userId: {}, using tempUser directly", orderOwnerId);
                        user = tempUser;
                    } else {
                        user = getUserInfoByEmail(email, authToken);
                    }
                    OrderDto orderDto = orderMapper.toDto(order);
                    return new OrderWithUserDto(orderDto, user);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderWithUserDto> getOrdersByStatuses(List<OrderStatus> statuses, String authToken) {
        log.info("Getting orders by statuses: {}", statuses);

        List<Order> orders = orderRepository.findAllByStatusIn(statuses);
        
        return orders.stream()
                .map(order -> {
                    // Получаем email владельца заказа через userId, затем используем email для получения user info
                    long orderOwnerId = Objects.requireNonNull(order.getUserId(), "Order userId cannot be null");
                    UserDto tempUser = getUserInfo(orderOwnerId, authToken);
                    String email = tempUser.getEmail();
                    UserDto user;
                    if (email == null || email.isBlank()) {
                        log.warn("Email not found for userId: {}, using tempUser directly", orderOwnerId);
                        user = tempUser;
                    } else {
                        user = getUserInfoByEmail(email, authToken);
                    }
                    OrderDto orderDto = orderMapper.toDto(order);
                    return new OrderWithUserDto(orderDto, user);
                })
                .collect(Collectors.toList());
    }

    /**
     * Получает все заказы текущего пользователя.
     * Пользователь может видеть только свои заказы.
     * 
     * @param authentication объект аутентификации, содержащий JWT токен
     * @return список заказов текущего пользователя
     */
    @Transactional(readOnly = true)
    public List<OrderWithUserDto> getMyOrders(Authentication authentication) {
        // Извлекаем email из токена
        String email = SecurityUtils.getEmailFromToken(authentication);
        log.info("Getting orders for user email: {}", email);

        // Извлекаем токен для передачи в User Service
        String authToken = SecurityUtils.getTokenString(authentication);

        // Получаем информацию о пользователе из user-service по email
        UserDto user = getUserInfoByEmail(email, authToken);
        Long userId = user.getId();
        if (userId == null) {
            throw new IllegalStateException("User ID not found for email: " + email);
        }

        // Получаем все заказы пользователя
        List<Order> orders = orderRepository.findAllByUserId(userId);
        log.info("Found {} orders for user {}", orders.size(), email);

        // Преобразуем заказы в DTO
        return orders.stream()
                .map(order -> {
                    OrderDto orderDto = orderMapper.toDto(order);
                    return new OrderWithUserDto(orderDto, user);
                })
                .collect(Collectors.toList());
    }

    /**
     * Обновляет статус заказа.
     * Пользователь может обновлять только свои заказы, ADMIN - любые заказы.
     * 
     * @param id ID заказа для обновления
     * @param request запрос с новым статусом заказа
     * @param authentication объект аутентификации для проверки прав доступа
     * @return обновленный заказ с информацией о пользователе
     * @throws AccessDeniedException если пользователь пытается обновить чужой заказ
     */
    @Transactional
    public OrderWithUserDto updateOrder(Long id, UpdateOrderRequest request, Authentication authentication) {
        log.info("Updating order ID: {} with status: {}", id, request.getStatus());

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        // Проверка прав доступа: пользователь может обновлять только свои заказы, ADMIN - любые
        if (authentication == null) {
            throw new IllegalStateException("Authentication is required");
        }

        // Если пользователь не ADMIN, проверяем, что заказ принадлежит ему
        if (!SecurityUtils.isAdmin(authentication)) {
            // Извлекаем email текущего пользователя из токена
            String userEmail = SecurityUtils.getEmailFromToken(authentication);
            
            // Извлекаем токен для получения информации о владельце заказа
            String authToken = SecurityUtils.getTokenString(authentication);
            
            // Получаем информацию о владельце заказа
            Long orderOwnerId = Objects.requireNonNull(order.getUserId(), "Order userId cannot be null");
            UserDto orderOwner = getUserInfo(orderOwnerId, authToken);
            String orderOwnerEmail = orderOwner.getEmail();
            
            if (orderOwnerEmail == null || orderOwnerEmail.isBlank()) {
                log.warn("Email not found for order owner userId: {}", orderOwnerId);
                throw new IllegalStateException("Cannot verify order ownership: email not found for order owner");
            }
            
            // Проверяем, что текущий пользователь является владельцем заказа
            if (!userEmail.equals(orderOwnerEmail)) {
                log.warn("User {} attempted to update order {} owned by {}", userEmail, id, orderOwnerEmail);
                throw new AccessDeniedException("Access denied: You can only update your own orders");
            }
            
            log.info("User {} is updating their own order {}", userEmail, id);
        } else {
            log.info("ADMIN user {} is updating order {}", authentication.getName(), id);
        }

        // Обновляем статус заказа
        order.setStatus(request.getStatus());
        final Order savedOrder = orderRepository.save(order);

        // Извлекаем токен для получения информации о пользователе
        String authToken = SecurityUtils.getTokenString(authentication);
        
        // Получаем email владельца заказа через userId, затем используем email для получения user info
        return getOrderWithUserDto(authToken, savedOrder);
    }

    /**
     * Удаляет заказ по ID.
     * Доступ разрешен только пользователям с ролью ADMIN.     
     */
    @Transactional
    public void deleteOrder(Long id, Authentication authentication) {
        log.info("Deleting order ID: {} by user: {}", id, authentication != null ? authentication.getName() : "unknown");

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        // Проверяем, является ли пользователь ADMIN
        // Дополнительная проверка на уровне сервиса для безопасности (defense in depth)
        // Основная проверка уже выполнена в SecurityConfig, но это обеспечивает дополнительную защиту
        if (authentication == null || !SecurityUtils.isAdmin(authentication)) {
            String userName = authentication != null ? authentication.getName() : "unknown";
            log.warn("User {} attempted to delete order {} but is not ADMIN", userName, id);
            throw new AccessDeniedException("Access denied: Only ADMIN users can delete orders");
        }

        // Cascade удалит orderItems автоматически благодаря настройкам JPA
        orderRepository.delete(order);
        log.info("Order {} successfully deleted by ADMIN user: {}", id, authentication.getName());
    }

   
    private UserDto getUserInfo(long userId, String authToken) {
        try {
            return userServiceClient.getUserById(userId, authToken);
        } catch (Exception e) {
            log.warn("Failed to get user info for userId: {}, using fallback", userId, e);
            return new UserDto(userId, "Unknown", "User", null, null);
        }
    }

    
    private UserDto getUserInfoByEmail(String email, String authToken) {
        try {
            return userServiceClient.getUserByEmail(email, authToken);
        } catch (Exception e) {
            log.warn("Failed to get user info for email: {}, using fallback", email, e);
            return new UserDto(null, "Unknown", "User", null, email);
        }
    }
}

