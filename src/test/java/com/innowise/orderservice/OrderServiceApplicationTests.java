package com.innowise.orderservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import com.innowise.orderservice.client.UserServiceClient;
import com.innowise.orderservice.mapper.OrderItemMapper;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.repository.ItemRepository;
import com.innowise.orderservice.repository.OrderItemRepository;
import com.innowise.orderservice.repository.OrderRepository;
import org.mockito.Mockito;
/*
 * Тесты для OrderServiceApplication.
 * Проверяют, что Spring контекст загружается без ошибок.
 * Репозитории и зависимости, требующие БД, замокированы через @TestConfiguration.
 * запускает Spring Boot контекст для тестов.

webEnvironment = NONE означает:
    Не стартовать встроенный веб-сервер (Tomcat, Netty и т.д.).
    Контекст создаётся только для компонентов, бинов, конфигураций.
    Используется для unit/практически integration тестов без реального HTTP сервера.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)//Запускает Spring Boot контекст для тестов.
@ActiveProfiles("test")//Использует профиль "test" для загрузки конфигурации тестов.
class OrderServiceApplicationTests {

    @TestConfiguration //Создаёт все моки (репозитории, клиенты, мапперы)
    static class MockConfiguration {

        /*создаёт фейковый объект, который:
            не обращается к БД
            позволяет настраивать поведение (when(...).thenReturn(...))
            фиксирует вызовы методов для проверки (verify(...))        
        */
        @Bean //Создаёт мок для OrderRepository
        public OrderRepository orderRepository() {
            return Mockito.mock(OrderRepository.class);
        }

        @Bean
        public ItemRepository itemRepository() {
            return Mockito.mock(ItemRepository.class);
        }

        @Bean
        public OrderItemRepository orderItemRepository() {
            return Mockito.mock(OrderItemRepository.class);
        }

        @Bean
        public UserServiceClient userServiceClient() {
            return Mockito.mock(UserServiceClient.class);
        }

        @Bean
        public OrderMapper orderMapper() {
            return Mockito.mock(OrderMapper.class);
        }

        @Bean
        public OrderItemMapper orderItemMapper() {
            return Mockito.mock(OrderItemMapper.class);
        }
    }

    //ИТОГО: контекст загружается, но без реальной БД и внешних сервисов.
    @Test
    void contextLoads() {
        // Тест проверяет, что Spring контекст загружается без ошибок
        // Репозитории и зависимости, требующие БД, замокированы через @TestConfiguration
    }

}
