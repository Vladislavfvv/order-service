package com.innowise.orderservice.config;

import com.innowise.orderservice.dto.CreateOrderEvent;
import com.innowise.orderservice.dto.CreatePaymentEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for Order Service
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    // Producer Configuration for CREATE_ORDER events
    @Bean
    public ProducerFactory<String, CreateOrderEvent> orderEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // Создаем JsonSerializer без добавления информации о типе (@class) в JSON
        ObjectMapper objectMapper = new ObjectMapper();
        JsonSerializer<CreateOrderEvent> jsonSerializer = new JsonSerializer<>(objectMapper);
        jsonSerializer.setAddTypeInfo(false); // Отключаем добавление информации о типе
        
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        DefaultKafkaProducerFactory<String, CreateOrderEvent> factory = new DefaultKafkaProducerFactory<>(configProps);
        factory.setValueSerializer(jsonSerializer);
        return factory;
    }

    @Bean
    public KafkaTemplate<String, CreateOrderEvent> orderEventKafkaTemplate() {
        return new KafkaTemplate<>(orderEventProducerFactory());
    }

    // Consumer Configuration for CREATE_PAYMENT events
    @Bean
    public ConsumerFactory<String, CreatePaymentEvent> paymentEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "order-service-group");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // Используем latest для пропуска старых сообщений с неправильной информацией о типе
        // При первом запуске consumer group будет читать только новые сообщения
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Создаем ObjectMapper для игнорирования неизвестных свойств (включая @class из старых сообщений)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        
        // Создаем JsonDeserializer с типом и useTypeHeaders=false
        // Используем конструктор с тремя параметрами: тип, objectMapper, useTypeHeaders
        JsonDeserializer<CreatePaymentEvent> jsonDeserializer = new JsonDeserializer<>(CreatePaymentEvent.class, objectMapper, false);
        jsonDeserializer.setRemoveTypeHeaders(true);
        jsonDeserializer.addTrustedPackages("*");
        
        // Используем JsonDeserializer напрямую
        return new DefaultKafkaConsumerFactory<>(props, 
                new StringDeserializer(), 
                jsonDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CreatePaymentEvent> paymentEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CreatePaymentEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentEventConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        // Настраиваем DefaultErrorHandler для логирования ошибок без падения сервиса
        // ErrorHandlingDeserializer уже обрабатывает ошибки десериализации
        factory.setCommonErrorHandler(new DefaultErrorHandler());
        return factory;
    }
}

