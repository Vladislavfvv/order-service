package com.innowise.orderservice.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderItemDto;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private OrderMapper orderMapper;

    private Order order;
    private UserDto user;
    private Item item;
    private Item item2;
    private Item item3;
    private OrderItem orderItem;
    private OrderItem orderItem2;
    private OrderItem orderItem3;
    private List<OrderItem> orderItems;

    @BeforeEach
    void setUp() {
        // Инициализируем моки (важно вызвать перед использованием)
        MockitoAnnotations.openMocks(this);
        
        // Примечание: В юнит-тестах не нужно очищать БД, так как репозитории являются моками (@Mock)
        // и не хранят состояние между тестами. Очистка БД нужна только в интеграционных тестах.

        // Настройка UserDto
        user = new UserDto(1L, "testUserName", "testSurname",
                LocalDate.of(2000, 1, 1), "testEmail@email.com");

        // Настройка Item
        item = new Item(1L, "TV", BigDecimal.valueOf(5000.10));
        item2 = new Item(2L, "Phone", BigDecimal.valueOf(1000.2));
        item3 = new Item(3L, "Laptop", BigDecimal.valueOf(1500.3));

        // Настройка OrderItem
        orderItem = new OrderItem();
        orderItem.setId(1L);
        orderItem.setItem(item);
        orderItem.setQuantity(BigDecimal.valueOf(2));
        orderItem2 = new OrderItem();
        orderItem2.setId(2L);
        orderItem2.setItem(item2);
        orderItem2.setQuantity(BigDecimal.valueOf(1));
        orderItem3 = new OrderItem();
        orderItem3.setId(3L);
        orderItem3.setItem(item3);
        orderItem3.setQuantity(BigDecimal.valueOf(1));

        // Настройка списка OrderItem
        orderItems = new ArrayList<>();
        orderItems.add(orderItem);
        orderItems.add(orderItem2);
        orderItems.add(orderItem3);

        // Настройка Order
        order = new Order();
        order.setId(1L);
        order.setUserId(user.getId());
        order.setCreation_date(LocalDateTime.now());
        order.setStatus(OrderStatus.NEW);
        order.setItems(orderItems);

        // Связываем OrderItem с Order - обратная связь
        orderItem.setOrder(order);
        orderItem2.setOrder(order);
        orderItem3.setOrder(order);
    }

    //мок для аутентификации
    private JwtAuthenticationToken createMockAuthentication(String email, String role) {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "HS256")
                .claim("sub", email)
                .claim("email", email)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        return new JwtAuthenticationToken(
                jwt,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private void mockSecurityContext(Authentication authentication) {
        SecurityContext securityContext = org.mockito.Mockito.mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    /**
     * Тест успешного создания заказа.
     * Проверяет, что метод createOrder:
     * 1. Извлекает email из JWT токена
     * 2. Получает информацию о пользователе через UserServiceClient
     * 3. Находит все товары из запроса в репозитории
     * 4. Создает заказ со статусом NEW
     * 5. Сохраняет заказ в репозитории
     * 6. Возвращает OrderWithUserDto с информацией о заказе и пользователе
     */
    @DisplayName("createOrder_Success - Создание заказа успешно")
    @Test
    void createOrder_Success() {
        // given - Подготовка тестовых данных
        String userEmail = "test@example.com";
        String authToken = "Bearer mock-token";
        JwtAuthenticationToken authentication = createMockAuthentication(userEmail, "USER");
        
        // Создаем тестового пользователя
        UserDto testUser = new UserDto(1L, "Test", "User", 
                LocalDate.of(2000, 1, 1), userEmail);
        
        // Создаем запрос с двумя товарами
        OrderItemDto orderItemDto1 = new OrderItemDto();
        orderItemDto1.setItemId(item.getId());
        orderItemDto1.setQuantity(BigDecimal.valueOf(2));
        
        OrderItemDto orderItemDto2 = new OrderItemDto();
        orderItemDto2.setItemId(item2.getId());
        orderItemDto2.setQuantity(BigDecimal.valueOf(1));
        
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(orderItemDto1, orderItemDto2));
        
        // Ожидаемый сохраненный заказ
        Order savedOrder = new Order();
        savedOrder.setId(1L);
        savedOrder.setUserId(testUser.getId());
        savedOrder.setStatus(OrderStatus.NEW);
        savedOrder.setCreation_date(LocalDateTime.now());
        savedOrder.setItems(orderItems);
        
        // Ожидаемый DTO заказа
        OrderDto orderDto = new OrderDto();
        orderDto.setId(1L);
        orderDto.setUserId(testUser.getId());
        orderDto.setStatus(OrderStatus.NEW);
        
        // when - Настройка моков и выполнение метода
        // Мокируем получение пользователя по email
        when(userServiceClient.getUserByEmail(userEmail, authToken)).thenReturn(testUser);
        // Мокируем поиск товаров в репозитории
        when(itemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(itemRepository.findById(item2.getId())).thenReturn(Optional.of(item2));
        // Мокируем сохранение заказа
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
        // Мокируем преобразование Order в OrderDto
        when(orderMapper.toDto(any(Order.class))).thenReturn(orderDto);
        
        // Выполняем тестируемый метод
        OrderWithUserDto result = orderService.createOrder(request, authentication);
        
        // then - Проверка результатов
        // Проверяем, что результат не null
        assertNotNull(result); //Проверяем, что результат не null
        assertNotNull(result.getOrder()); //Проверяем, что заказ не null
        assertNotNull(result.getUser()); //Проверяем, что пользователь не null
        // Проверяем, что данные пользователя корректны
        assertEquals(testUser.getId(), result.getUser().getId()); //Проверяем, что ID пользователя совпадает с ID пользователя в заказе
        assertEquals(userEmail, result.getUser().getEmail()); //Проверяем, что email пользователя совпадает с email пользователя в заказе
        // Проверяем, что методы были вызваны нужное количество раз
        verify(orderRepository, times(1)).save(any(Order.class)); //Проверяем, что метод save был вызван 1 раз
        verify(userServiceClient, times(1)).getUserByEmail(userEmail, authToken); //Проверяем, что метод getUserByEmail был вызван 1 раз
    }
    
    /**
     * Тест обработки ошибки при создании заказа с несуществующим товаром.
     * Проверяет, что метод createOrder:
     * 1. Выбрасывает ItemNotFoundException, если товар не найден в репозитории
     * 2. Не сохраняет заказ в репозитории при ошибке
     * 3. Корректно обрабатывает ситуацию с несуществующим itemId
     */
    @DisplayName("createOrder_ItemNotFound_ThrowsException - Товар не найден")
    @Test
    void createOrder_ItemNotFound_ThrowsException() {
        // given - Подготовка тестовых данных с несуществующим товаром
        String userEmail = "test@example.com";
        JwtAuthenticationToken authentication = createMockAuthentication(userEmail, "USER");
        
        UserDto testUser = new UserDto(1L, "Test", "User", 
                LocalDate.of(2000, 1, 1), userEmail);
        
        // Создаем запрос с несуществующим товаром (ID = 999)
        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setItemId(999L); // Несуществующий ID
        orderItemDto.setQuantity(BigDecimal.valueOf(1));
        
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(orderItemDto));
        
        // when - Настройка моков
        when(userServiceClient.getUserByEmail(eq(userEmail), any())).thenReturn(testUser); //Мокируем получение пользователя из User Service
        // Мокируем ситуацию, когда товар не найден
        when(itemRepository.findById(999L)).thenReturn(Optional.empty()); //Мокируем ситуацию, когда товар не найден
        
        // then - Проверка, что выбрасывается исключение
        assertThrows(ItemNotFoundException.class, () -> {
            orderService.createOrder(request, authentication); //Вызываем метод createOrder и проверяем, что выбрасывается исключение ItemNotFoundException
        });
        
      
        verify(itemRepository, times(1)).findById(999L); //Проверяем, что метод findById был вызван 1 раз
        // Проверяем, что заказ НЕ был сохранен из-за ошибки, т.е. метод save не был вызван  
        verify(orderRepository, never()).save(any(Order.class)); 
    }

    /**
     * Тест успешного получения заказа по ID.
     * Проверяет, что метод getOrderById:
     * 1. Находит заказ в репозитории по ID
     * 2. Получает информацию о пользователе сначала по userId (для получения email)
     * 3. Затем получает полную информацию о пользователе по email
     * 4. Преобразует Order в OrderDto через mapper
     * 5. Возвращает OrderWithUserDto с информацией о заказе и пользователе
     */
    @DisplayName("findUserById_Positive - Все ок)")
    @Test
    void getOrderById_Exists_ReturnOrderWithUserDto() {
        //given - Подготовка тестовых данных
        Long orderId = 1L;
        String authToken = "Bearer mock-token";
        String userEmail = "testEmail@email.com";

        // Создаём тестового пользователя
        UserDto testUser = new UserDto(1L, "testUserName", "testSurname",
                LocalDate.of(2000, 1, 1), userEmail);

        // Создаём DTO для элементов заказа
        OrderItemDto orderItemDto1 = new OrderItemDto();
        orderItemDto1.setId(1L);
        orderItemDto1.setItemId(item.getId());
        orderItemDto1.setQuantity(BigDecimal.valueOf(2));
        orderItemDto1.setOrderId(orderId);

        OrderItemDto orderItemDto2 = new OrderItemDto();
        orderItemDto2.setId(2L);
        orderItemDto2.setItemId(item2.getId());
        orderItemDto2.setQuantity(BigDecimal.valueOf(1));
        orderItemDto2.setOrderId(orderId);

        OrderItemDto orderItemDto3 = new OrderItemDto();
        orderItemDto3.setId(3L);
        orderItemDto3.setItemId(item3.getId());
        orderItemDto3.setQuantity(BigDecimal.valueOf(1));
        orderItemDto3.setOrderId(orderId);

        List<OrderItemDto> orderItemDtos = new ArrayList<>();
        orderItemDtos.add(orderItemDto1);
        orderItemDtos.add(orderItemDto2);
        orderItemDtos.add(orderItemDto3);

        // Создаём ожидаемый OrderDto
        OrderDto orderDto = new OrderDto();
        orderDto.setId(orderId);
        orderDto.setUserId(testUser.getId());
        orderDto.setStatus(OrderStatus.NEW);
        orderDto.setCreationDate(LocalDateTime.now());
        orderDto.setItemDtoList(orderItemDtos);

        //when - Настройка моков и выполнение метода
        // Мокируем repository - возвращаем order
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        // Мокируем mapper - возвращаем orderDto
        when(orderMapper.toDto(order)).thenReturn(orderDto); 

        // Мокируем userServiceClient - сначала получаем пользователя по ID (для получения email)
        when(userServiceClient.getUserById(testUser.getId(), authToken)).thenReturn(testUser); 

        // Затем мокируем получение пользователя по email (основной вызов)
        when(userServiceClient.getUserByEmail(userEmail, authToken)).thenReturn(testUser); 

        // Вызываем тестируемый метод
        OrderWithUserDto result = orderService.getOrderById(orderId, authToken); 

        //then - Проверка результатов
        assertNotNull(result); //Проверяем, что результат не null
        assertNotNull(result.getOrder()); //Проверяем, что заказ не null
        assertNotNull(result.getUser()); //Проверяем, что пользователь не null
        // Проверяем, что ID заказа соответствует ожидаемому
        assertEquals(orderId, result.getOrder().getId()); 
        // Проверяем, что данные пользователя корректны
        assertEquals(testUser.getId(), result.getUser().getId()); //Проверяем, что ID пользователя совпадает с ID пользователя в заказе
        assertEquals(userEmail, result.getUser().getEmail()); //Проверяем, что email пользователя совпадает с email пользователя в заказе
    }

    /**
     * Тест обработки ошибки при получении несуществующего заказа.
     * Проверяет, что метод getOrderById:
     * 1. Выбрасывает OrderNotFoundException, если заказ не найден в репозитории
     * 2. Сообщение об ошибке содержит правильный ID заказа
     * 3. Метод findById вызывается один раз с правильным аргументом
     */
    @DisplayName("getOrderById_NotFound_ThrowsException - Нет такого заказа, исключение")
    @Test
    void getOrderById_NotFound_ThrowsException() {
        // given - Подготовка тестовых данных
        Long orderId = 1L;
        String authToken = "test-token";
        String expectedMessage = "Order not found: " + orderId;
        
        // Мокируем ситуацию, когда заказ не найден (пустой Optional)
        // Это имитирует ситуацию, когда order с таким ID не существует в базе данных
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty()); 

        // when & then - Проверка, что метод выбросит исключение
        OrderNotFoundException exception = assertThrows(
                OrderNotFoundException.class,
                () -> orderService.getOrderById(orderId, authToken) 
        );

        // Проверка: что текст сообщения исключения соответствует ожидаемому
        assertEquals(expectedMessage, exception.getMessage()); 
        // Проверка: что метод findById был вызван ровно 1 раз с нужным аргументом
        verify(orderRepository, times(1)).findById(orderId); 
    }

    /**
     * Тест успешного получения нескольких заказов по списку IDs.
     * Проверяет, что метод getOrdersByIds:
     * 1. Находит все заказы по списку IDs в репозитории
     * 2. Для каждого заказа получает информацию о пользователе (сначала по userId, затем по email)
     * 3. Преобразует каждый Order в OrderDto через mapper
     * 4. Возвращает список OrderWithUserDto с информацией о заказах и пользователях
     * 5. Возвращает правильное количество заказов
     */
    @DisplayName("getOrdersByIds_Success - Получение заказов по IDs успешно")
    @Test
    void getOrdersByIds_Success() {
        // given - Подготовка тестовых данных
        List<Long> orderIds = List.of(1L, 2L);
        String authToken = "Bearer mock-token";
        String userEmail = "testEmail@email.com";
        
        UserDto testUser = new UserDto(1L, "Test", "User", 
                LocalDate.of(2000, 1, 1), userEmail);
        
        // Создаём второй заказ для теста
        Order order2 = new Order();
        order2.setId(2L);
        order2.setUserId(testUser.getId());
        order2.setStatus(OrderStatus.PROCESSING);
        order2.setCreation_date(LocalDateTime.now());
        
        List<Order> orders = List.of(order, order2);
        
        // Создаём ожидаемые DTO для заказов
        OrderDto orderDto1 = new OrderDto();
        orderDto1.setId(1L);
        orderDto1.setUserId(testUser.getId());
        orderDto1.setStatus(OrderStatus.NEW);
        
        OrderDto orderDto2 = new OrderDto();
        orderDto2.setId(2L);
        orderDto2.setUserId(testUser.getId());
        orderDto2.setStatus(OrderStatus.PROCESSING);
        
        // when - Настройка моков и выполнение метода
        when(orderRepository.findAllByIdIn(orderIds)).thenReturn(orders); //Мокируем ситуацию, когда заказы найдены
        when(userServiceClient.getUserById(testUser.getId(), authToken)).thenReturn(testUser); //Мокируем ситуацию, когда пользователь найден по ID
        when(userServiceClient.getUserByEmail(userEmail, authToken)).thenReturn(testUser); //Мокируем ситуацию, когда пользователь найден по email
        when(orderMapper.toDto(order)).thenReturn(orderDto1); //Мокируем ситуацию, когда заказ преобразуется в OrderDto
        when(orderMapper.toDto(order2)).thenReturn(orderDto2); //Мокируем ситуацию, когда заказ преобразуется в OrderDto
        
        // Выполняем тестируемый метод
        List<OrderWithUserDto> result = orderService.getOrdersByIds(orderIds, authToken); //Вызываем метод getOrdersByIds и получаем результат
        
        // then - Проверка результатов
        assertNotNull(result); //Проверяем, что результат не null
        // Проверяем, что вернулось правильное количество заказов
        assertEquals(2, result.size()); //Проверяем, что в результате 2 заказа
        // Проверяем, что IDs заказов соответствуют ожидаемым
        assertEquals(1L, result.get(0).getOrder().getId()); //Проверяем, что ID первого заказа совпадает с ожидаемым
        assertEquals(2L, result.get(1).getOrder().getId()); //Проверяем, что ID второго заказа совпадает с ожидаемым
        // Проверяем, что метод был вызван один раз
        verify(orderRepository, times(1)).findAllByIdIn(orderIds); //Проверяем, что метод findAllByIdIn был вызван 1 раз
    }

    /**
     * Тест успешного получения заказов по статусам.
     * Проверяет, что метод getOrdersByStatuses:
     * 1. Находит все заказы с указанными статусами в репозитории
     * 2. Для каждого заказа получает информацию о пользователе (сначала по userId, затем по email)
     * 3. Преобразует каждый Order в OrderDto через mapper
     * 4. Возвращает список OrderWithUserDto с информацией о заказах и пользователях
     * 5. Возвращает заказы с правильными статусами
     */
    @DisplayName("getOrdersByStatuses_Success - Получение заказов по статусам успешно")
    @Test
    void getOrdersByStatuses_Success() {
        // given - Подготовка тестовых данных
        List<OrderStatus> statuses = List.of(OrderStatus.NEW, OrderStatus.PROCESSING);
        String authToken = "Bearer mock-token";
        String userEmail = "testEmail@email.com";
        
        UserDto testUser = new UserDto(1L, "Test", "User", 
                LocalDate.of(2000, 1, 1), userEmail);
        
        // Создаём второй заказ со статусом PROCESSING
        Order order2 = new Order();
        order2.setId(2L);
        order2.setUserId(testUser.getId());
        order2.setStatus(OrderStatus.PROCESSING);
        order2.setCreation_date(LocalDateTime.now());
        
        List<Order> orders = List.of(order, order2);
        
        // Создаём ожидаемые DTO для заказов
        OrderDto orderDto1 = new OrderDto();
        orderDto1.setId(1L);
        orderDto1.setUserId(testUser.getId());
        orderDto1.setStatus(OrderStatus.NEW);
        
        OrderDto orderDto2 = new OrderDto();
        orderDto2.setId(2L);
        orderDto2.setUserId(testUser.getId());
        orderDto2.setStatus(OrderStatus.PROCESSING);
        
        // when - Настройка моков и выполнение метода
        when(orderRepository.findAllByStatusIn(statuses)).thenReturn(orders); //Мокируем ситуацию, когда заказы найдены     
        when(userServiceClient.getUserById(testUser.getId(), authToken)).thenReturn(testUser); //Мокируем ситуацию, когда пользователь найден по ID
        when(userServiceClient.getUserByEmail(userEmail, authToken)).thenReturn(testUser); //Мокируем ситуацию, когда пользователь найден по email
        when(orderMapper.toDto(order)).thenReturn(orderDto1); //Мокируем ситуацию, когда заказ преобразуется в OrderDto
        when(orderMapper.toDto(order2)).thenReturn(orderDto2); //Мокируем ситуацию, когда заказ преобразуется в OrderDto
        
        // Выполняем тестируемый метод
        List<OrderWithUserDto> result = orderService.getOrdersByStatuses(statuses, authToken);
        
        // then - Проверка результатов
        assertNotNull(result);
        // Проверяем, что вернулось правильное количество заказов
        assertEquals(2, result.size());
        // Проверяем, что в результате есть заказ со статусом NEW
        assertTrue(result.stream().anyMatch(o -> o.getOrder().getStatus() == OrderStatus.NEW)); 
        // Проверяем, что в результате есть заказ со статусом PROCESSING
        assertTrue(result.stream().anyMatch(o -> o.getOrder().getStatus() == OrderStatus.PROCESSING)); 
        // Проверяем, что метод был вызван один раз
        verify(orderRepository, times(1)).findAllByStatusIn(statuses); 
    }

    /**
     * Тест успешного обновления заказа.
     * Проверяет, что метод updateOrder:
     * 1. Находит заказ в репозитории по ID
     * 2. Обновляет статус заказа согласно запросу
     * 3. Сохраняет обновленный заказ в репозитории
     * 4. Получает информацию о пользователе (сначала по userId, затем по email)
     * 5. Преобразует Order в OrderDto через mapper
     * 6. Возвращает OrderWithUserDto с обновленной информацией о заказе и пользователе
     */
    @DisplayName("updateOrder_Success - Обновление заказа успешно")
    @Test
    void updateOrder_Success() {
        // given - Подготовка тестовых данных
        Long orderId = 1L;
        String authToken = "Bearer mock-token";
        String userEmail = "testEmail@email.com";
        
        // Создаём запрос на обновление статуса заказа
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus(OrderStatus.PROCESSING);
        
        UserDto testUser = new UserDto(1L, "Test", "User", 
                LocalDate.of(2000, 1, 1), userEmail);
        
        // Ожидаемый обновленный заказ
        Order updatedOrder = new Order();
        updatedOrder.setId(orderId);
        updatedOrder.setUserId(testUser.getId());
        updatedOrder.setStatus(OrderStatus.PROCESSING);
        updatedOrder.setCreation_date(LocalDateTime.now());
        
        // Ожидаемый DTO обновленного заказа
        OrderDto updatedOrderDto = new OrderDto();
        updatedOrderDto.setId(orderId);
        updatedOrderDto.setUserId(testUser.getId());
        updatedOrderDto.setStatus(OrderStatus.PROCESSING);
        
        // when - Настройка моков и выполнение метода
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(updatedOrder);
        when(userServiceClient.getUserById(testUser.getId(), authToken)).thenReturn(testUser);
        when(userServiceClient.getUserByEmail(userEmail, authToken)).thenReturn(testUser);
        when(orderMapper.toDto(updatedOrder)).thenReturn(updatedOrderDto);
        
        // Выполняем тестируемый метод
        OrderWithUserDto result = orderService.updateOrder(orderId, request, authToken);
        
        // then - Проверка результатов
        assertNotNull(result);
        // Проверяем, что ID заказа соответствует ожидаемому
        assertEquals(orderId, result.getOrder().getId());
        // Проверяем, что статус заказа был обновлен
        assertEquals(OrderStatus.PROCESSING, result.getOrder().getStatus());
        // Проверяем, что методы были вызваны нужное количество раз
        verify(orderRepository, times(1)).findById(orderId);
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    /**
     * Тест обработки ошибки при обновлении несуществующего заказа.
     * Проверяет, что метод updateOrder:
     * 1. Выбрасывает OrderNotFoundException, если заказ не найден в репозитории
     * 2. Не сохраняет заказ в репозитории при ошибке
     * 3. Корректно обрабатывает ситуацию с несуществующим orderId
     */
    @DisplayName("updateOrder_NotFound_ThrowsException - Заказ не найден при обновлении")
    @Test
    void updateOrder_NotFound_ThrowsException() {
        // given - Подготовка тестовых данных с несуществующим заказом
        Long orderId = 999L;
        String authToken = "Bearer mock-token";
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus(OrderStatus.PROCESSING);
        
        // when - Мокируем ситуацию, когда заказ не найден
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        
        // then - Проверка, что выбрасывается исключение
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.updateOrder(orderId, request, authToken);
        });
        
        // Проверяем, что поиск заказа был выполнен
        verify(orderRepository, times(1)).findById(orderId);
        // Проверяем, что заказ НЕ был сохранен из-за ошибки
        verify(orderRepository, never()).save(any(Order.class));
    }

    /**
     * Тест успешного удаления заказа администратором.
     * Проверяет, что метод deleteOrder:
     * 1. Находит заказ в репозитории по ID
     * 2. Проверяет, что пользователь имеет роль ADMIN (через SecurityUtils.isAdmin)
     * 3. Удаляет заказ из репозитории
     * 4. Не выбрасывает исключение при успешном удалении
     */
    @DisplayName("deleteOrder_Success_Admin - Удаление заказа админом успешно")
    @Test
    void deleteOrder_Success_Admin() {
        // given - Подготовка тестовых данных с администратором
        Long orderId = 1L;
        JwtAuthenticationToken authentication = createMockAuthentication("admin@example.com", "ADMIN");
        
        // when - Настройка моков и выполнение метода
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        // Выполняем тестируемый метод
        orderService.deleteOrder(orderId, authentication);
        
        // then - Проверка результатов
        // Проверяем, что поиск заказа был выполнен
        verify(orderRepository, times(1)).findById(orderId);
        // Проверяем, что заказ был удален
        verify(orderRepository, times(1)).delete(order);
    }

    /**
     * Тест проверки прав доступа при удалении заказа обычным пользователем.
     * Проверяет, что метод deleteOrder:
     * 1. Находит заказ в репозитории по ID
     * 2. Проверяет роль пользователя (USER не является ADMIN)
     * 3. Выбрасывает AccessDeniedException, если пользователь не является администратором
     * 4. Не удаляет заказ из репозитории при отсутствии прав доступа
     * Это обеспечивает дополнительную защиту на уровне сервиса (defense in depth)
     */
    @DisplayName("deleteOrder_AccessDenied_User - Обычный пользователь не может удалять заказы")
    @Test
    void deleteOrder_AccessDenied_User() {
        // given - Подготовка тестовых данных с обычным пользователем (не админом)
        Long orderId = 1L;
        JwtAuthenticationToken authentication = createMockAuthentication("user@example.com", "USER");
        
        // when - Настройка моков
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        
        // then - Проверка, что выбрасывается исключение AccessDeniedException
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            orderService.deleteOrder(orderId, authentication);
        });
        
        // Проверяем, что поиск заказа был выполнен
        verify(orderRepository, times(1)).findById(orderId);
        // Проверяем, что заказ НЕ был удален из-за отсутствия прав доступа
        verify(orderRepository, never()).delete(any(Order.class));
    }

    /**
     * Тест обработки ошибки при удалении несуществующего заказа.
     * Проверяет, что метод deleteOrder:
     * 1. Выбрасывает OrderNotFoundException, если заказ не найден в репозитории
     * 2. Не удаляет заказ из репозитории при ошибке
     * 3. Корректно обрабатывает ситуацию с несуществующим orderId
     * Проверка прав доступа не выполняется, так как заказ не найден
     */
    @DisplayName("deleteOrder_NotFound_ThrowsException - Заказ не найден при удалении")
    @Test
    void deleteOrder_NotFound_ThrowsException() {
        // given - Подготовка тестовых данных с несуществующим заказом
        Long orderId = 999L;
        JwtAuthenticationToken authentication = createMockAuthentication("admin@example.com", "ADMIN");
        
        // when - Мокируем ситуацию, когда заказ не найден
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        
        // then - Проверка, что выбрасывается исключение
        assertThrows(OrderNotFoundException.class, () -> {
            orderService.deleteOrder(orderId, authentication);
        });
        
        // Проверяем, что поиск заказа был выполнен
        verify(orderRepository, times(1)).findById(orderId);
        // Проверяем, что заказ НЕ был удален из-за ошибки
        verify(orderRepository, never()).delete(any(Order.class));
    }

}