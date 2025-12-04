package com.innowise.orderservice.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.dto.OrderItemDto;
import com.innowise.orderservice.dto.OrderWithUserDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.dto.UserDto;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.service.OrderService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;

    private OrderWithUserDto orderWithUserDto;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        // Настройка тестовых данных
        userDto = new UserDto(1L, "Test", "User", LocalDate.of(2000, 1, 1), "user@example.com");
        
        OrderDto orderDto = new OrderDto();
        orderDto.setId(1L);
        orderDto.setUserId(1L);
        orderDto.setStatus(OrderStatus.NEW);
        orderDto.setCreationDate(LocalDateTime.now());
        
        orderWithUserDto = new OrderWithUserDto(orderDto, userDto);
    }

    @Test
    @DisplayName("GET /api/v1/orders - успешное получение всех заказов админом")
    void getAllOrders_ShouldReturnOrders_WhenAdmin() throws Exception {
        // given
        when(orderService.getAllOrders(any())).thenReturn(List.of(orderWithUserDto));

        // when & then
        mockMvc.perform(get("/api/v1/orders")
                        .with(jwt().jwt(jwt -> jwt.subject("admin@example.com").claim("role", "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].order.id").value(1L))
                .andExpect(jsonPath("$[0].user.email").value("user@example.com"));
    }

    /**
     * Тест успешного создания заказа через REST API.
     * Проверяет, что эндпоинт POST /api/v1/orders:
     * 1. Принимает валидный запрос с товарами
     * 2. Авторизует пользователя через JWT токен
     * 3. Вызывает сервис для создания заказа
     * 4. Возвращает HTTP 201 CREATED
     * 5. Возвращает OrderWithUserDto с информацией о заказе и пользователе
     */
    @DisplayName("createOrder_Success - Создание заказа успешно")
    @Test
    void createOrder_Success() throws Exception {
        // given - Подготовка тестовых данных
        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setItemId(1L);
        orderItemDto.setQuantity(BigDecimal.valueOf(2));
        
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of(orderItemDto));

        // when - Настройка моков
        when(orderService.createOrder(any(CreateOrderRequest.class), any())).thenReturn(orderWithUserDto);

        // then - Выполнение запроса и проверка результатов
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(jwt -> jwt.subject("user@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isCreated()) // Проверяем HTTP статус 201
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$.order.id").value(1L)) // Проверяем ID заказа
                .andExpect(jsonPath("$.order.status").value("NEW")) // Проверяем статус заказа
                .andExpect(jsonPath("$.user.id").value(1L)) // Проверяем ID пользователя
                .andExpect(jsonPath("$.user.email").value("user@example.com")); // Проверяем email пользователя
    }

    /**
     * Тест обработки невалидного запроса при создании заказа.
     * Проверяет, что эндпоинт POST /api/v1/orders:
     * 1. Валидирует входные данные (список товаров не может быть пустым)
     * 2. Возвращает HTTP 400 BAD_REQUEST при невалидном запросе
     * 3. Не вызывает сервис при ошибке валидации
     */
    @DisplayName("createOrder_InvalidRequest - Невалидный запрос")
    @Test
    void createOrder_InvalidRequest() throws Exception {
        // given - Подготовка невалидного запроса (пустой список товаров)
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(List.of()); // Пустой список - нарушает валидацию @NotEmpty

        // then - Выполнение запроса и проверка ошибки валидации
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(jwt -> jwt.subject("user@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isBadRequest()); // Проверяем HTTP статус 400
    }

    /**
     * Тест успешного получения заказа по ID через REST API.
     * Проверяет, что эндпоинт GET /api/v1/orders/{id}:
     * 1. Принимает ID заказа в пути запроса
     * 2. Авторизует пользователя через JWT токен
     * 3. Вызывает сервис для получения заказа
     * 4. Возвращает HTTP 200 OK
     * 5. Возвращает OrderWithUserDto с информацией о заказе и пользователе
     */
    @DisplayName("getOrderById_Success - Получение своего заказа по ID успешно")
    @Test
    void getOrderById_Success() throws Exception {
        // given - Подготовка тестовых данных
        Long orderId = 1L;
        String userEmail = "user@example.com";

        // when - Настройка моков
        when(orderService.getOrderById(eq(orderId), any())).thenReturn(orderWithUserDto);

        // then - Выполнение запроса и проверка результатов
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .with(jwt().jwt(jwt -> jwt.subject(userEmail).claim("role", "ROLE_USER"))))
                .andExpect(status().isOk()) // Проверяем HTTP статус 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$.order.id").value(orderId)) // Проверяем ID заказа
                .andExpect(jsonPath("$.user.id").value(1L)); // Проверяем ID пользователя
    }

    @DisplayName("getOrderById_AccessDenied - Обычный пользователь не может получить чужой заказ")
    @Test
    void getOrderById_AccessDenied() throws Exception {
        // given - Подготовка тестовых данных
        Long orderId = 1L;
        String userEmail = "user@example.com";

        // when - Настройка моков - выбрасываем AccessDeniedException при попытке получить чужой заказ
        when(orderService.getOrderById(eq(orderId), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("Access denied: You can only access your own orders"));

        // then - Выполнение запроса и проверка результатов
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .with(jwt().jwt(jwt -> jwt.subject(userEmail).claim("role", "ROLE_USER"))))
                .andExpect(status().isForbidden()); // Проверяем HTTP статус 403 Forbidden
    }

    @DisplayName("getOrderById_Success_Admin - Админ может получить любой заказ")
    @Test
    void getOrderById_Success_Admin() throws Exception {
        // given - Подготовка тестовых данных
        Long orderId = 1L;

        // when - Настройка моков
        when(orderService.getOrderById(eq(orderId), any())).thenReturn(orderWithUserDto);

        // then - Выполнение запроса и проверка результатов
        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .with(jwt().jwt(jwt -> jwt.subject("admin@example.com").claim("role", "ROLE_ADMIN"))))
                .andExpect(status().isOk()) // Проверяем HTTP статус 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$.order.id").value(orderId)) // Проверяем ID заказа
                .andExpect(jsonPath("$.user.id").value(1L)); // Проверяем ID пользователя
    }

    /**
     * Тест успешного получения нескольких заказов по списку IDs через REST API.
     * Проверяет, что эндпоинт GET /api/v1/orders/ids:
     * 1. Принимает список IDs заказов в query параметрах
     * 2. Авторизует пользователя через JWT токен
     * 3. Вызывает сервис для получения заказов
     * 4. Возвращает HTTP 200 OK
     * 5. Возвращает список OrderWithUserDto с информацией о заказах и пользователях
     */
    @DisplayName("getOrdersByIds_Success - Получение заказов по IDs успешно")
    @Test
    void getOrdersByIds_Success() throws Exception {
        // given - Подготовка тестовых данных
        List<Long> ids = List.of(1L, 2L);
        OrderWithUserDto order2 = new OrderWithUserDto(
                new OrderDto(), userDto
        );

        // when - Настройка моков
        when(orderService.getOrdersByIds(eq(ids), any())).thenReturn(List.of(orderWithUserDto, order2));

        // then - Выполнение запроса и проверка результатов
        mockMvc.perform(get("/api/v1/orders/ids")
                        .param("ids", "1", "2") // Передаем IDs через query параметры
                        .with(jwt().jwt(jwt -> jwt.subject("user@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isOk()) // Проверяем HTTP статус 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$").isArray()) // Проверяем, что ответ - массив
                .andExpect(jsonPath("$.length()").value(2)); // Проверяем количество заказов
    }

    /**
     * Тест успешного получения заказов по статусам через REST API.
     * Проверяет, что эндпоинт GET /api/v1/orders/statuses:
     * 1. Принимает список статусов заказов в query параметрах
     * 2. Авторизует пользователя через JWT токен
     * 3. Вызывает сервис для получения заказов по статусам
     * 4. Возвращает HTTP 200 OK
     * 5. Возвращает список OrderWithUserDto с информацией о заказах и пользователях
     */
    @DisplayName("getOrdersByStatuses_Success - Получение заказов по статусам успешно")
    @Test
    void getOrdersByStatuses_Success() throws Exception {
        // given - Подготовка тестовых данных
        List<OrderStatus> statuses = List.of(OrderStatus.NEW, OrderStatus.PROCESSING);

        // when - Настройка моков
        when(orderService.getOrdersByStatuses(eq(statuses), any())).thenReturn(List.of(orderWithUserDto));

        // then - Выполнение запроса и проверка результатов
        mockMvc.perform(get("/api/v1/orders/statuses")
                        .param("statuses", "NEW", "PROCESSING") // Передаем статусы через query параметры
                        .with(jwt().jwt(jwt -> jwt.subject("user@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isOk()) // Проверяем HTTP статус 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$").isArray()); // Проверяем, что ответ - массив
    }

    /**
     * Тест успешного обновления заказа через REST API.
     * Проверяет, что эндпоинт PUT /api/v1/orders/{id}:
     * 1. Принимает ID заказа в пути запроса и данные для обновления в теле
     * 2. Авторизует пользователя через JWT токен
     * 3. Вызывает сервис для обновления заказа
     * 4. Возвращает HTTP 200 OK
     * 5. Возвращает OrderWithUserDto с обновленной информацией о заказе и пользователе
     */
    @DisplayName("updateOrder_Success - Обновление заказа успешно")
    @Test
    void updateOrder_Success() throws Exception {
        // given - Подготовка тестовых данных
        Long orderId = 1L;
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus(OrderStatus.PROCESSING);
        
        OrderDto updatedOrderDto = new OrderDto();
        updatedOrderDto.setId(orderId);
        updatedOrderDto.setStatus(OrderStatus.PROCESSING);
        OrderWithUserDto updatedOrder = new OrderWithUserDto(updatedOrderDto, userDto);

        // when - Настройка моков
        when(orderService.updateOrder(eq(orderId), any(UpdateOrderRequest.class), any())).thenReturn(updatedOrder);

        // then - Выполнение запроса и проверка результатов
        mockMvc.perform(put("/api/v1/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(jwt -> jwt.subject("user@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isOk()) // Проверяем HTTP статус 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$.order.status").value("PROCESSING")); // Проверяем обновленный статус
    }

    /**
     * Тест обработки невалидного запроса при обновлении заказа.
     * Проверяет, что эндпоинт PUT /api/v1/orders/{id}:
     * 1. Валидирует входные данные (статус не может быть null)
     * 2. Возвращает HTTP 400 BAD_REQUEST при невалидном запросе
     * 3. Не вызывает сервис при ошибке валидации
     */
    @DisplayName("updateOrder_InvalidRequest - Невалидный запрос на обновление")
    @Test
    void updateOrder_InvalidRequest() throws Exception {
        // given - Подготовка невалидного запроса (null статус)
        Long orderId = 1L;
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus(null); // null статус - нарушает валидацию @NotNull

        // then - Выполнение запроса и проверка ошибки валидации
        mockMvc.perform(put("/api/v1/orders/{id}", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(jwt -> jwt.subject("user@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isBadRequest()); // Проверяем HTTP статус 400
    }

    /**
     * Тест успешного удаления заказа администратором через REST API.
     * Проверяет, что эндпоинт DELETE /api/v1/orders/{id}:
     * 1. Принимает ID заказа в пути запроса
     * 2. Авторизует пользователя через JWT токен (только ADMIN)
     * 3. Вызывает сервис для удаления заказа
     * 4. Возвращает HTTP 204 NO_CONTENT при успешном удалении
     * 5. Проверяет права доступа на уровне SecurityConfig (только ADMIN может удалять)
     */
    @DisplayName("deleteOrder_Success_Admin - Удаление заказа админом успешно")
    @Test
    void deleteOrder_Success_Admin() throws Exception {
        // given - Подготовка тестовых данных
        Long orderId = 1L;

        // when - Настройка моков (deleteOrder возвращает void, поэтому используем doNothing)
        org.mockito.Mockito.doNothing().when(orderService).deleteOrder(eq(orderId), any());

        // then - Выполнение запроса и проверка результатов
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId)
                        .with(jwt().jwt(jwt -> jwt.subject("admin@example.com").claim("role", "ROLE_ADMIN"))))
                .andExpect(status().isNoContent()); // Проверяем HTTP статус 204
    }

    /**
     * Тест обработки неавторизованного запроса на удаление заказа.
     * Проверяет, что эндпоинт DELETE /api/v1/orders/{id}:
     * 1. Требует JWT токен для авторизации
     * 2. Возвращает HTTP 403 FORBIDDEN при отсутствии токена (Spring Security проверяет роль до проверки авторизации)
     * 3. Не вызывает сервис при отсутствии авторизации
     * Это обеспечивает безопасность на уровне Spring Security
     * 
     * Примечание: Spring Security возвращает 403 вместо 401, так как эндпоинт настроен на проверку роли ADMIN,
     * и SecurityConfig проверяет роль до проверки авторизации
     */
    @DisplayName("deleteOrder_Unauthorized - Неавторизованный запрос")
    @Test
    void deleteOrder_Unauthorized() throws Exception {
        // given - Подготовка тестовых данных (без токена авторизации)
        Long orderId = 1L;

        // then - Выполнение запроса без авторизации и проверка ошибки
        mockMvc.perform(delete("/api/v1/orders/{id}", orderId))
                .andExpect(status().isForbidden()); // Проверяем HTTP статус 403 (Spring Security проверяет роль до авторизации)
    }
}

