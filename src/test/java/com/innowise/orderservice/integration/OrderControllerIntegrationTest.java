package com.innowise.orderservice.integration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.dto.CreateOrderRequest;
import com.innowise.orderservice.dto.OrderItemDto;
import com.innowise.orderservice.dto.UpdateOrderRequest;
import com.innowise.orderservice.dto.UserDto;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

/**
 * Интеграционные тесты для OrderController.
 * Используют реальную PostgreSQL БД через Testcontainers и MockMvc для тестирования REST API.
 * Мокируют только внешние сервисы (UserServiceClient).
 * 
 * ВАЖНО: Используем TestSecurityConfig для упрощения тестов.
 * SecurityConfig не загружается благодаря @Profile("!test").
 */
@AutoConfigureMockMvc
@org.springframework.boot.test.context.SpringBootTest
@org.springframework.context.annotation.Import(com.innowise.orderservice.security.TestSecurityConfig.class) // Используем TestSecurityConfig для упрощения тестов
class OrderControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserServiceClient userServiceClient;

    // JwtDecoder настроен через @Primary бин в BaseIntegrationTest.TestJwtDecoderConfig
    // Не нужно настраивать его здесь

    private Item testItem1;
    private Item testItem2;
    private UserDto testUser;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        // JwtDecoder настроен через @Primary бин в BaseIntegrationTest.TestJwtDecoderConfig
        // Не нужно настраивать его здесь, так как он уже настроен в базовом классе
        
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

        // Создаём тестового пользователя (должен быть создан до настройки моков)
        testUser = new UserDto(1L, "Test", "User", LocalDate.of(2000, 1, 1), "test@example.com");
        
        // Настраиваем моки для UserServiceClient (после создания testUser)
        when(userServiceClient.getUserByEmail(eq("test@example.com"), any())).thenReturn(testUser);
        when(userServiceClient.getUserById(eq(testUser.getId()), any())).thenReturn(testUser);

        // Создаём тестовый заказ в БД
        testOrder = new Order();
        testOrder.setUserId(testUser.getId());
        testOrder.setStatus(OrderStatus.NEW);
        testOrder.setCreation_date(LocalDateTime.now());
        testOrder = orderRepository.save(testOrder);

        // Мокируем получение пользователя из User Service
        when(userServiceClient.getUserByEmail(eq("test@example.com"), any())).thenReturn(testUser);
        // eq("test@example.com") — Matcher: значит: первый аргумент должен быть точно "test@example.com"
        //any() — Matcher для второго аргумента, т.е второй аргумент может быть любой, нам не важно что там передали (например, токен)
        when(userServiceClient.getUserById(eq(testUser.getId()), any())).thenReturn(testUser);
    }


    /**
     * Интеграционный тест успешного создания заказа через REST API.
     * Проверяет полный стек: HTTP запрос -> контроллер -> сервис -> репозиторий -> БД.
     */
    @DisplayName("createOrder_Success - Интеграционный тест создания заказа через REST API")
    @Test
    void createOrder_Success() throws Exception {
        // given - Подготовка тестовых данных
        OrderItemDto orderItemDto = new OrderItemDto();
        orderItemDto.setItemId(testItem1.getId());
        orderItemDto.setQuantity(BigDecimal.valueOf(2));

        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(java.util.List.of(orderItemDto));

        // when & then - Выполнение HTTP запроса и проверка результатов
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)) //Преобразует объект в JSON строку
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com").claim("role", "ROLE_USER")))) //Авторизует пользователя через JWT токен  
                .andExpect(status().isCreated()) // Проверяем HTTP статус 201
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$.order").exists()) // Проверяем наличие заказа в ответе
                .andExpect(jsonPath("$.order.status").value("NEW")) // Проверяем статус заказа
                .andExpect(jsonPath("$.user").exists()) // Проверяем наличие пользователя в ответе
                .andExpect(jsonPath("$.user.email").value("test@example.com")); // Проверяем email пользователя

        // Проверяем, что заказ сохранен в БД
        assertEquals(2, orderRepository.count()); // Исходный заказ + новый заказ
    }

    /**
     * Интеграционный тест обработки невалидного запроса при создании заказа.
     */
    @DisplayName("createOrder_InvalidRequest - Невалидный запрос")
    @Test
    void createOrder_InvalidRequest() throws Exception {
        // given - Подготовка невалидного запроса (пустой список товаров)
        CreateOrderRequest request = new CreateOrderRequest();
        request.setItems(java.util.List.of());

        // when & then - Выполнение HTTP запроса и проверка ошибки валидации
        mockMvc.perform(post("/api/v1/orders") //Отправляет POST запрос на /api/v1/orders
                        .contentType(MediaType.APPLICATION_JSON) //Устанавливаем тип контента в JSON
                        .content(objectMapper.writeValueAsString(request)) //Преобразует объект в JSON строку
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com").claim("role", "ROLE_USER")))) //Авторизует пользователя через JWT токен  
                .andExpect(status().isBadRequest()); // Проверяем HTTP статус 400

        // Проверяем, что заказ НЕ был создан в БД
        assertEquals(1, orderRepository.count()); // Только исходный заказ
    }

    /**
     * Интеграционный тест успешного получения заказа по ID через REST API.
     */
    @DisplayName("getOrderById_Success - Интеграционный тест получения заказа по ID через REST API")
    @Test
    void getOrderById_Success() throws Exception {
        // given - Используем существующий заказ из setUp

        // when & then - Выполнение HTTP запроса и проверка результатов
        mockMvc.perform(get("/api/v1/orders/{id}", testOrder.getId()) //Отправляет GET запрос на /api/v1/orders/{id}
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com").claim("role", "ROLE_USER")))) //Авторизует пользователя через JWT токен  
                .andExpect(status().isOk()) // Проверяем HTTP статус 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$.order.id").value(testOrder.getId())) // Проверяем ID заказа
                .andExpect(jsonPath("$.order.status").value("NEW")) // Проверяем статус заказа
                .andExpect(jsonPath("$.user.id").value(testUser.getId())) // Проверяем ID пользователя
                .andExpect(jsonPath("$.user.email").value("test@example.com")); // Проверяем email пользователя
    }

    /**
     * Интеграционный тест успешного получения нескольких заказов по IDs через REST API.
     */
    @DisplayName("getOrdersByIds_Success - Интеграционный тест получения заказов по IDs через REST API")
    @Test
    void getOrdersByIds_Success() throws Exception {
        // given - Создаём второй заказ
        Order order2 = new Order(); 
        order2.setUserId(testUser.getId());
        order2.setStatus(OrderStatus.PROCESSING); 
        order2.setCreation_date(LocalDateTime.now());
        order2 = orderRepository.save(order2);

        // when & then - Выполнение HTTP запроса и проверка результатов
        mockMvc.perform(get("/api/v1/orders/ids") //Отправляет GET запрос на /api/v1/orders/ids
                        .param("ids", String.valueOf(testOrder.getId()), String.valueOf(order2.getId())) //Передаем IDs через query параметры
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com").claim("role", "ROLE_USER")))) //Авторизует пользователя через JWT токен  
                .andExpect(status().isOk()) // Проверяем HTTP статус 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$").isArray()) // Проверяем, что ответ - массив
                .andExpect(jsonPath("$.length()").value(2)) // Проверяем количество заказов
                .andExpect(jsonPath("$[0].order.id").exists()) // Проверяем наличие заказа в ответе
                .andExpect(jsonPath("$[1].order.id").exists()); // Проверяем наличие второго заказа
    }

    /**
     * Интеграционный тест успешного получения заказов по статусам через REST API.
     */
    @DisplayName("getOrdersByStatuses_Success - Интеграционный тест получения заказов по статусам через REST API")
    @Test
    void getOrdersByStatuses_Success() throws Exception {
        // given - Создаём заказ со статусом PROCESSING
        Order order2 = new Order();
        order2.setUserId(testUser.getId());
        order2.setStatus(OrderStatus.PROCESSING);
        order2.setCreation_date(LocalDateTime.now());
        order2 = orderRepository.save(order2);

        // when & then - Выполнение HTTP запроса и проверка результатов
        mockMvc.perform(get("/api/v1/orders/statuses") //Отправляет GET запрос на /api/v1/orders/statuses
                        .param("statuses", "NEW", "PROCESSING") //Передаем статусы через query параметры
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com").claim("role", "ROLE_USER")))) //Авторизует пользователя через JWT токен  
                .andExpect(status().isOk()) // Проверяем HTTP статус 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$").isArray()) // Проверяем, что ответ - массив
                .andExpect(jsonPath("$.length()").value(2)); // Проверяем количество заказов
    }

    /**
     * Интеграционный тест успешного обновления заказа через REST API.
     */
    @DisplayName("updateOrder_Success - Интеграционный тест обновления заказа через REST API")
    @Test
    void updateOrder_Success() throws Exception {
        // given - Подготовка запроса на обновление
        UpdateOrderRequest request = new UpdateOrderRequest();
        request.setStatus(OrderStatus.PROCESSING);

        // when & then - Выполнение HTTP запроса и проверка результатов
        mockMvc.perform(put("/api/v1/orders/{id}", testOrder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isOk()) // Проверяем HTTP статус 200
                .andExpect(content().contentType(MediaType.APPLICATION_JSON)) // Проверяем тип контента
                .andExpect(jsonPath("$.order.status").value("PROCESSING")); // Проверяем обновленный статус

        // Проверяем, что заказ обновлен в БД
        Order updatedOrder = orderRepository.findById(testOrder.getId()).orElseThrow();
        assertEquals(OrderStatus.PROCESSING, updatedOrder.getStatus()); //Проверяем, что статус заказа обновлен
    }

    /**
     * Интеграционный тест успешного удаления заказа администратором через REST API.
     */
    @DisplayName("deleteOrder_Success_Admin - Интеграционный тест удаления заказа админом через REST API")
    @Test
    void deleteOrder_Success_Admin() throws Exception {
        // given - Используем существующий заказ из setUp

        // when & then - Выполнение HTTP запроса и проверка результатов
        mockMvc.perform(delete("/api/v1/orders/{id}", testOrder.getId())
                        .with(jwt().jwt(jwt -> jwt.subject("admin@example.com").claim("role", "ROLE_ADMIN"))))
                .andExpect(status().isNoContent()); // Проверяем HTTP статус 204

        // Проверяем, что заказ удален из БД
        assertTrue(orderRepository.findById(testOrder.getId()).isEmpty()); //Проверяем, что заказ нет в БД
    }

    /**
     * Интеграционный тест проверки прав доступа при удалении заказа обычным пользователем.
     */
    @DisplayName("deleteOrder_Forbidden_User - Обычный пользователь не может удалять заказы")
    @Test
    void deleteOrder_Forbidden_User() throws Exception {
        // given - Используем существующий заказ из setUp

        // when & then - Выполнение HTTP запроса и проверка ошибки доступа
        mockMvc.perform(delete("/api/v1/orders/{id}", testOrder.getId())
                        .with(jwt().jwt(jwt -> jwt.subject("test@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isForbidden()); // Проверяем HTTP статус 403

        // Проверяем, что заказ НЕ был удален из БД
        assertTrue(orderRepository.findById(testOrder.getId()).isPresent()); //Проверяем, что заказ есть в БД
    }

    /**
     * Интеграционный тест обработки неавторизованного запроса на удаление заказа.
     */
    @DisplayName("deleteOrder_Unauthorized - Неавторизованный запрос")
    @Test
    void deleteOrder_Unauthorized() throws Exception {
        // given - Используем существующий заказ из setUp

        // when & then - Выполнение HTTP запроса без авторизации и проверка ошибки
        mockMvc.perform(delete("/api/v1/orders/{id}", testOrder.getId())) //Отправляет DELETE запрос на /api/v1/orders/{id}
                .andExpect(status().isForbidden()); // Проверяем HTTP статус 403 (Spring Security проверяет роль до авторизации)

        // Проверяем, что заказ НЕ был удален из БД
        assertTrue(orderRepository.findById(testOrder.getId()).isPresent()); //Проверяем, что заказ есть в БД
    }
    
}

