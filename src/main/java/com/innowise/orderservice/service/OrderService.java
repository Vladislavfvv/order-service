package com.innowise.orderservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

    @Transactional(readOnly = true)
    public OrderWithUserDto getOrderById(Long id, String authToken) {
        log.info("Getting order by ID: {}", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        // Получаем email владельца заказа через userId, затем используем email для получения user info
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

    @Transactional
    public OrderWithUserDto updateOrder(Long id, UpdateOrderRequest request, String authToken) {
        log.info("Updating order ID: {} with status: {}", id, request.getStatus());

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        order.setStatus(request.getStatus());
        final Order savedOrder = orderRepository.save(order);

        // Получаем email владельца заказа через userId, затем используем email для получения user info
        long orderOwnerId = Objects.requireNonNull(savedOrder.getUserId(), "Order userId cannot be null");
        UserDto tempUser = getUserInfo(orderOwnerId, authToken);
        String email = tempUser.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("Email not found for userId: " + orderOwnerId);
        }
        UserDto user = getUserInfoByEmail(email, authToken);

        OrderDto orderDto = orderMapper.toDto(savedOrder);
        return new OrderWithUserDto(orderDto, user);
    }

    @Transactional
    public void deleteOrder(Long id) {
        log.info("Deleting order ID: {}", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        // Cascade удалит orderItems автоматически
        orderRepository.delete(order);
    }

    /**
     * Получает информацию о пользователе по userId
     */
    private UserDto getUserInfo(long userId, String authToken) {
        try {
            return userServiceClient.getUserById(userId, authToken);
        } catch (Exception e) {
            log.warn("Failed to get user info for userId: {}, using fallback", userId, e);
            return new UserDto(userId, "Unknown", "User", null, null);
        }
    }

    /**
     * Получает информацию о пользователе по email
     */
    private UserDto getUserInfoByEmail(String email, String authToken) {
        try {
            return userServiceClient.getUserByEmail(email, authToken);
        } catch (Exception e) {
            log.warn("Failed to get user info for email: {}, using fallback", email, e);
            return new UserDto(null, "Unknown", "User", null, email);
        }
    }
}

