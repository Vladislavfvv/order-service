package com.innowise.orderservice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.dto.UserDto;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final UserServiceClient userServiceClient;

    @Transactional
    public OrderWithUserDto createOrder(Long userId, CreateOrderRequest request, String authToken) {
        log.info("Creating order for user ID: {}", userId);

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
                    orderItem.setItem(item);
                    orderItem.setQuantity(itemDto.getQuantity());
                    return orderItem;
                })
                .collect(Collectors.toList());

        order.setItems(orderItems);
        
        // Сохраняем заказ (cascade сохранит orderItems)
        final Order savedOrder = orderRepository.save(order);

        // Получаем информацию о пользователе
        UserDto user = getUserInfo(userId, authToken);

        OrderDto orderDto = orderMapper.toDto(savedOrder);
        return new OrderWithUserDto(orderDto, user);
    }

    @Transactional(readOnly = true)
    public OrderWithUserDto getOrderById(Long id, String authToken) {
        log.info("Getting order by ID: {}", id);

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + id));

        UserDto user = getUserInfo(order.getUserId(), authToken);

        OrderDto orderDto = orderMapper.toDto(order);
        return new OrderWithUserDto(orderDto, user);
    }

    @Transactional(readOnly = true)
    public List<OrderWithUserDto> getOrdersByIds(List<Long> ids, String authToken) {
        log.info("Getting orders by IDs: {}", ids);

        List<Order> orders = orderRepository.findAllByIdIn(ids);
        
        return orders.stream()
                .map(order -> {
                    UserDto user = getUserInfo(order.getUserId(), authToken);
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
                    UserDto user = getUserInfo(order.getUserId(), authToken);
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

        UserDto user = getUserInfo(savedOrder.getUserId(), authToken);

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
     * В реальном приложении нужно получить email из токена или из другого источника
     */
    private UserDto getUserInfo(Long userId, String authToken) {
        try {
            // TODO: Получить email пользователя по userId
            // Пока используем заглушку - в реальном приложении нужно получить email из токена
            // или из другого источника данных
            String email = "user@example.com"; // Заглушка
            
            return userServiceClient.getUserByEmail(email, authToken);
        } catch (Exception e) {
            log.warn("Failed to get user info for userId: {}, using fallback", userId, e);
            // Возвращаем fallback из Circuit Breaker
            return new UserDto(userId, "Unknown", "User", null, null);
        }
    }

    // Exception classes
    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String message) {
            super(message);
        }
    }

    public static class ItemNotFoundException extends RuntimeException {
        public ItemNotFoundException(String message) {
            super(message);
        }
    }
}

