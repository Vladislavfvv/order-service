package com.innowise.orderservice.producer;

import com.innowise.orderservice.dto.CreateOrderEvent;
import com.innowise.orderservice.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer for sending CREATE_ORDER events
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventProducer {

    private static final String CREATE_ORDER_TOPIC = "create-order-events";

    private final KafkaTemplate<String, CreateOrderEvent> kafkaTemplate;

    /**
     * Send CREATE_ORDER event to Kafka
     */
    public void sendCreateOrderEvent(Order order) {
        try {
            CreateOrderEvent event = buildCreateOrderEvent(order);
            
            log.info("Sending CREATE_ORDER event to Kafka for orderId: {}", order.getId());
            

            Long orderId = order.getId();
            if (orderId == null) {
                log.warn("Order id is null, skip sending CREATE_ORDER event");
                return;
            }

            CompletableFuture<SendResult<String, CreateOrderEvent>> future =
             kafkaTemplate.send(CREATE_ORDER_TOPIC, orderId.toString(), event);
            
           // CompletableFuture<SendResult<String, CreateOrderEvent>> future = 
           //         kafkaTemplate.send(CREATE_ORDER_TOPIC, String.valueOf(order.getId()), event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("CREATE_ORDER event sent successfully for orderId: {}, offset: {}", 
                            order.getId(), result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to send CREATE_ORDER event for orderId: {}", order.getId(), ex);
                }
            });
        } catch (Exception e) {
            log.error("Error sending CREATE_ORDER event for orderId: {}", order.getId(), e);
        }
    }

    private CreateOrderEvent buildCreateOrderEvent(Order order) {
        return new CreateOrderEvent(
                order.getId(),
                order.getUserId()
        );
    }   
}

