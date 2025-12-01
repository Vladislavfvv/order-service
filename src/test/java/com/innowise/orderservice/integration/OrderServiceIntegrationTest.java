package com.innowise.orderservice.integration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderItemDto;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.dto.UserDto;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.exception.OrderNotFoundException;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import com.innowise.orderservice.service.OrderService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Интеграционные тесты для OrderService.
 * Используют реальную PostgreSQL БД через Testcontainers и мокируют только внешние сервисы (UserServiceClient).
 * 
 * ВАЖНО: Используем @Transactional для обеспечения доступа к ленивым коллекциям Hibernate
 * в рамках активной сессии.
 */
@org.springframework.transaction.annotation.Transactional
class OrderServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    @MockitoBean
    private UserServiceClient userServiceClient;

    private Item testItem1;
    private Item testItem2;
    private UserDto testUser;
    private JwtAuthenticationToken userAuthentication;
    private JwtAuthenticationToken adminAuthentication;

    @BeforeEach
    void setUp() {
        // Очищаем тестовую БД перед каждым тестом
        // ВАЖНО: Это очищает только изолированный PostgreSQL контейнер из Testcontainers,
        // а НЕ вашу реальную базу данных. Контейнер создается автоматически при запуске тестов
        // и удаляется после их завершения.
        orderRepository.deleteAll();
        itemRepository.deleteAll();

        // Создаём тестовые товары в БД
        testItem1 = new Item(null, "Laptop", BigDecimal.valueOf(1500.00));
        testItem1 = itemRepository.save(testItem1);

        testItem2 = new Item(null, "Mouse", BigDecimal.valueOf(25.50));
        testItem2 = itemRepository.save(testItem2);

        // Создаём тестового пользователя
        testUser = new UserDto(1L, "Test", "User", LocalDate.of(2000, 1, 1), "test@example.com");

        // Создаём JWT токен для обычного пользователя
        Jwt userJwt = Jwt.withTokenValue("user-token")
                .header("alg", "HS256")
                .claim("sub", "test@example.com")
                .issuedAt(Instant.now()) //Токен выдан в текущий момент
                .expiresAt(Instant.now().plusSeconds(3600)) //Токен истекает через 1 час
                .build();
        userAuthentication = new JwtAuthenticationToken(
                userJwt,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")) 
        );

        // Создаём JWT токен для администратора
        Jwt adminJwt = Jwt.withTokenValue("admin-token")
                .header("alg", "HS256")
                .claim("sub", "admin@example.com")
                .issuedAt(Instant.now()) //Токен выдан в текущий момент
                .expiresAt(Instant.now().plusSeconds(3600)) //Токен истекает через 1 час
                .build();
        adminAuthentication = new JwtAuthenticationToken(
                adminJwt,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    /**
     * Интеграционный тест успешного создания заказа.
     * Проверяет полный стек: извлечение email из токена -> получение пользователя -> создание заказа -> сохранение в БД.
     */
    @DisplayName("createOrder_Success - Интеграционный тест создания заказа")
    @Test
    void createOrder_Success() {
        // given - Подготовка тестовых данных
        OrderItemDto orderItemDto1 = new OrderItemDto();
        orderItemDto1.setItemId(testItem1.getId());
        orderItemDto1.setQuantity(BigDecimal.valueOf(2));

        OrderItemDto orderItemDto2 = new OrderItemDto();
        orderItemDto2.setItemId(testItem2.getId());
        orderItemDto2.setQuantity(BigDecimal.valueOf(1));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(orderItemDto1, orderItemDto2));

        // Мокируем получение пользователя из User Service
        when(userServiceClient.getUserByEmail(eq("test@example.com"), any())).thenReturn(testUser);

        // when - Выполнение метода
        OrderWithUserDto result = orderService.createOrder(request, userAuthentication);

        // then - Проверка результатов
        assertNotNull(result); //Проверяем, что результат не null
        assertNotNull(result.getOrder()); //Проверяем, что заказ не null
        assertNotNull(result.getUser()); //Проверяем, что пользователь не null
        
        // Проверяем, что заказ сохранен в БД
        Order savedOrder = orderRepository.findById(result.getOrder().getId()).orElseThrow(); //Получаем заказ из БД
        assertEquals(OrderStatus.NEW, savedOrder.getStatus()); //Проверяем, что статус заказа NEW
        assertEquals(testUser.getId(), savedOrder.getUserId()); //Проверяем, что ID пользователя совпадает с ID пользователя в заказе
        assertEquals(2, savedOrder.getItems().size()); //Проверяем, что в заказе 2 элемента
        
        // Проверяем, что элементы заказа сохранены корректно
        assertTrue(savedOrder.getItems().stream() //Проверяем, что в заказе есть элемент с ID testItem1
                .anyMatch(item -> item.getItem().getId().equals(testItem1.getId()) 
                        && item.getQuantity().equals(BigDecimal.valueOf(2))));
        assertTrue(savedOrder.getItems().stream() //Проверяем, что в заказе есть элемент с ID testItem2
                .anyMatch(item -> item.getItem().getId().equals(testItem2.getId()) 
                        && item.getQuantity().equals(BigDecimal.valueOf(1))));
        
        // Проверяем вызов UserServiceClient
        verify(userServiceClient, times(1)).getUserByEmail(eq("test@example.com"), any()); //Проверяем, что UserServiceClient был вызван 1 раз
    }

    /**
     * Интеграционный тест обработки ошибки при создании заказа с несуществующим товаром.
     * Проверяет, что заказ не создается, если товар не найден в БД.
     */
    @DisplayName("createOrder_ItemNotFound_ThrowsException - Товар не найден в БД")
    @Test
    void createOrder_ItemNotFound_ThrowsException() {
        // given - Подготовка тестовых данных с несуществующим товаром
        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setItemId(999L); // Несуществующий ID
        orderItemDto.setQuantity(BigDecimal.valueOf(1));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(orderItemDto));

        when(userServiceClient.getUserByEmail(eq("test@example.com"), any())).thenReturn(testUser); //Мокируем получение пользователя из User Service

        // when & then - Проверка, что выбрасывается исключение
        assertThrows(ItemNotFoundException.class, () -> {
            orderService.createOrder(request, userAuthentication); //Вызываем метод createOrder и проверяем, что выбрасывается исключение ItemNotFoundException
        });

        // Проверяем, что заказ НЕ был сохранен в БД
        assertEquals(0, orderRepository.count()); //Проверяем, что в БД нет заказов
        verify(userServiceClient, times(1)).getUserByEmail(eq("test@example.com"), any()); //Проверяем, что UserServiceClient был вызван 1 раз
    }

    /**
     * Интеграционный тест успешного получения заказа по ID.
     * Проверяет полный стек: поиск в БД -> получение пользователя -> преобразование в DTO.
     */
    @DisplayName("getOrderById_Success - Интеграционный тест получения заказа по ID")
    @Test
    void getOrderById_Success() {
        // given - Создаём заказ в БД
        Order order = new Order();
        order.setUserId(testUser.getId());
        order.setStatus(OrderStatus.NEW);
        order.setCreation_date(LocalDateTime.now());
        order = orderRepository.save(order);

        String authToken = "Bearer test-token";

        // Мокируем получение пользователя из User Service
        when(userServiceClient.getUserById(eq(testUser.getId()), eq(authToken))).thenReturn(testUser); //Мокируем получение пользователя из User Service
        when(userServiceClient.getUserByEmail(eq("test@example.com"), eq(authToken))).thenReturn(testUser); //Мокируем получение пользователя из User Service

        // when - Выполнение метода
        OrderWithUserDto result = orderService.getOrderById(order.getId(), authToken); //Вызываем метод getOrderById и получаем результат

        // then - Проверка результатов
        assertNotNull(result);
        assertNotNull(result.getOrder());
        assertNotNull(result.getUser());
        assertEquals(order.getId(), result.getOrder().getId());
        assertEquals(testUser.getId(), result.getUser().getId());
        assertEquals("test@example.com", result.getUser().getEmail());
        
        verify(userServiceClient, times(1)).getUserById(eq(testUser.getId()), eq(authToken)); //Проверяем, что UserServiceClient был вызван 1 раз
        verify(userServiceClient, times(1)).getUserByEmail(eq("test@example.com"), eq(authToken)); //Проверяем, что UserServiceClient был вызван 1 раз
    }

    /**
     * Интеграционный тест обработки ошибки при получении несуществующего заказа.
     */
    @DisplayName("getOrderById_NotFound_ThrowsException - Заказ не найден в БД")
    @Test
    void getOrderById_NotFound_ThrowsException() {
        // given
        Long nonExistentOrderId = 999L;
        String authToken = "Bearer test-token";

        // when & then - Проверка, что выбрасывается исключение
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.getOrderById(nonExistentOrderId, authToken);
        });

        // Проверяем, что UserServiceClient не вызывался
        verify(userServiceClient, never()).getUserById(anyLong(), anyString()); //Проверяем, что UserServiceClient не был вызван
        verify(userServiceClient, never()).getUserByEmail(anyString(), anyString()); //Проверяем, что UserServiceClient не был вызван
    }

    /**
     * Интеграционный тест успешного получения нескольких заказов по IDs.
     */
    @DisplayName("getOrdersByIds_Success - Интеграционный тест получения заказов по IDs")
    @Test
    void getOrdersByIds_Success() {
        // given - Создаём несколько заказов в БД
        Order order1 = new Order();
        order1.setUserId(testUser.getId());
        order1.setStatus(OrderStatus.NEW);
        order1.setCreation_date(LocalDateTime.now());
        final Order savedOrder1 = orderRepository.save(order1);

        Order order2 = new Order();
        order2.setUserId(testUser.getId());
        order2.setStatus(OrderStatus.PROCESSING);
        order2.setCreation_date(LocalDateTime.now());
        final Order savedOrder2 = orderRepository.save(order2);

        List<Long> orderIds = List.of(savedOrder1.getId(), savedOrder2.getId());
        String authToken = "Bearer test-token";

        // Мокируем получение пользователя из User Service
        when(userServiceClient.getUserById(eq(testUser.getId()), eq(authToken))).thenReturn(testUser);
        when(userServiceClient.getUserByEmail(eq("test@example.com"), eq(authToken))).thenReturn(testUser);

        // when - Выполнение метода
        List<OrderWithUserDto> result = orderService.getOrdersByIds(orderIds, authToken);

        // then - Проверка результатов
        assertNotNull(result); //Проверяем, что результат не null
        assertEquals(2, result.size()); //Проверяем, что в результате 2 заказа
        assertTrue(result.stream().anyMatch(o -> o.getOrder().getId().equals(savedOrder1.getId()))); //Проверяем, что в результате есть заказ с ID savedOrder1
        assertTrue(result.stream().anyMatch(o -> o.getOrder().getId().equals(savedOrder2.getId()))); //Проверяем, что в результате есть заказ с ID savedOrder2
    }

    /**
     * Интеграционный тест успешного получения заказов по статусам.
     */
    @DisplayName("getOrdersByStatuses_Success - Интеграционный тест получения заказов по статусам")
    @Test
    void getOrdersByStatuses_Success() {
        // given - Создаём заказы с разными статусами в БД
        Order order1 = new Order();
        order1.setUserId(testUser.getId());
        order1.setStatus(OrderStatus.NEW);
        order1.setCreation_date(LocalDateTime.now());
        order1 = orderRepository.save(order1);

        Order order2 = new Order();
        order2.setUserId(testUser.getId());
        order2.setStatus(OrderStatus.PROCESSING);
        order2.setCreation_date(LocalDateTime.now());
        order2 = orderRepository.save(order2);

        Order order3 = new Order();
        order3.setUserId(testUser.getId());
        order3.setStatus(OrderStatus.COMPLETED);
        order3.setCreation_date(LocalDateTime.now());
        order3 = orderRepository.save(order3);

        List<OrderStatus> statuses = List.of(OrderStatus.NEW, OrderStatus.PROCESSING);
        String authToken = "Bearer test-token";

        // Мокируем получение пользователя из User Service
        when(userServiceClient.getUserById(eq(testUser.getId()), eq(authToken))).thenReturn(testUser);
        when(userServiceClient.getUserByEmail(eq("test@example.com"), eq(authToken))).thenReturn(testUser);

        // when - Выполнение метода
        List<OrderWithUserDto> result = orderService.getOrdersByStatuses(statuses, authToken);

        // then - Проверка результатов
        assertNotNull(result); //Проверяем, что результат не null
        assertEquals(2, result.size()); // Только NEW и PROCESSING
        assertTrue(result.stream().anyMatch(o -> o.getOrder().getStatus() == OrderStatus.NEW)); //Проверяем, что в результате есть заказ с статусом NEW
        assertTrue(result.stream().anyMatch(o -> o.getOrder().getStatus() == OrderStatus.PROCESSING)); //Проверяем, что в результате есть заказ с статусом PROCESSING
        assertTrue(result.stream().noneMatch(o -> o.getOrder().getStatus() == OrderStatus.COMPLETED)); //Проверяем, что в результате нет заказа с статусом COMPLETED
    }

    /**
     * Интеграционный тест успешного обновления заказа.
     */
    @DisplayName("updateOrder_Success - Интеграционный тест обновления заказа")
    @Test
    void updateOrder_Success() {
        // given - Создаём заказ в БД
        Order order = new Order();
        order.setUserId(testUser.getId());
        order.setStatus(OrderStatus.NEW);
        order.setCreation_date(LocalDateTime.now());
        order = orderRepository.save(order);

        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus(OrderStatus.PROCESSING);

        // Токен из userAuthentication будет "Bearer user-token" (SecurityUtils.getTokenString добавляет "Bearer ")
        String authToken = "Bearer user-token";

        // Мокируем получение пользователя из User Service
        // Важно: используем правильный токен, который будет извлечен из userAuthentication
        when(userServiceClient.getUserById(eq(testUser.getId()), eq(authToken))).thenReturn(testUser);
        when(userServiceClient.getUserByEmail(eq("test@example.com"), eq(authToken))).thenReturn(testUser);

        // when - Выполнение метода с Authentication объектом
        OrderWithUserDto result = orderService.updateOrder(order.getId(), request, userAuthentication);

        // then - Проверка результатов
        assertNotNull(result); //Проверяем, что результат не null
        assertEquals(OrderStatus.PROCESSING, result.getOrder().getStatus()); //Проверяем, что статус заказа PROCESSING
        
        // Проверяем, что заказ обновлен в БД
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PROCESSING, updatedOrder.getStatus());
    }

    /**
     * Интеграционный тест обработки ошибки при обновлении несуществующего заказа.
     */
    @DisplayName("updateOrder_NotFound_ThrowsException - Заказ не найден при обновлении")
    @Test
    void updateOrder_NotFound_ThrowsException() {
        // given
        Long nonExistentOrderId = 999L;
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus(OrderStatus.PROCESSING);

        // when & then - Проверка, что выбрасывается исключение
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.updateOrder(nonExistentOrderId, request, userAuthentication);
        });

        // Проверяем, что UserServiceClient не вызывался
        verify(userServiceClient, never()).getUserById(anyLong(), anyString());
        verify(userServiceClient, never()).getUserByEmail(anyString(), anyString());
    }

    /**
     * Интеграционный тест успешного удаления заказа администратором.
     */
    @DisplayName("deleteOrder_Success_Admin - Интеграционный тест удаления заказа админом")
    @Test
    void deleteOrder_Success_Admin() {
        // given - Создаём заказ в БД
        Order order = new Order();
        order.setUserId(testUser.getId());
        order.setStatus(OrderStatus.NEW);
        order.setCreation_date(LocalDateTime.now());
        order = orderRepository.save(order);

        Long orderId = order.getId();

        // when - Выполнение метода
        orderService.deleteOrder(orderId, adminAuthentication); //Вызываем метод deleteOrder и проверяем, что заказ удален из БД

        // then - Проверяем, что заказ удален из БД
        assertTrue(orderRepository.findById(orderId).isEmpty()); //Проверяем, что заказ нет в БД
    }

    /**
     * Интеграционный тест проверки прав доступа при удалении заказа обычным пользователем.
     */
    @DisplayName("deleteOrder_AccessDenied_User - Обычный пользователь не может удалять заказы")
    @Test
    void deleteOrder_AccessDenied_User() {
        // given - Создаём заказ в БД
        Order order = new Order();
        order.setUserId(testUser.getId());
        order.setStatus(OrderStatus.NEW);
        order.setCreation_date(LocalDateTime.now());
        order = orderRepository.save(order);

        Long orderId = order.getId();

        // when & then - Проверка, что выбрасывается исключение
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            orderService.deleteOrder(orderId, userAuthentication);
        });

        // Проверяем, что заказ НЕ был удален из БД
        assertTrue(orderRepository.findById(orderId).isPresent()); //Проверяем, что заказ есть в БД
    }

    /**
     * Интеграционный тест обработки ошибки при удалении несуществующего заказа.
     */
    @DisplayName("deleteOrder_NotFound_ThrowsException - Заказ не найден при удалении")
    @Test
    void deleteOrder_NotFound_ThrowsException() {
        // given
        Long nonExistentOrderId = 999L;

        // when & then - Проверка, что выбрасывается исключение
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.deleteOrder(nonExistentOrderId, adminAuthentication); //Вызываем метод deleteOrder и проверяем, что выбрасывается исключение OrderNotFoundException
        });
    }
}

