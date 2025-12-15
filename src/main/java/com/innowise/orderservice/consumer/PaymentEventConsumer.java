package com.innowise.orderservice.consumer;

import com.innowise.orderservice.dto.CreatePaymentEvent;
import com.innowise.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer for handling CREATE_PAYMENT events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private static final String CREATE_PAYMENT_TOPIC = "create-payment-events";
    private static final String GROUP_ID = "order-service-group";

    private final OrderService orderService;

    /**
     * Handle CREATE_PAYMENT event from Kafka
     */
    @KafkaListener(topics = CREATE_PAYMENT_TOPIC, groupId = GROUP_ID)
    public void handleCreatePaymentEvent(
            @Payload CreatePaymentEvent event,            
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        try {
            log.info("Received CREATE_PAYMENT event: orderId={}, status={}, offset={}", 
                    event.getOrderId(), event.getStatus(), offset);
            
            // Process payment event
            processPaymentEvent(event);
            
            // Acknowledge message processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            
            log.info("Successfully processed CREATE_PAYMENT event for orderId: {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Error processing CREATE_PAYMENT event for orderId: {}", event.getOrderId(), e);
            throw e;
        }
    }

    private void processPaymentEvent(CreatePaymentEvent event) {
        log.info("Processing payment event: orderId={}, status={}",
                event.getOrderId(), event.getStatus());
        orderService.updateOrderStatus(event.getOrderId(), event.getStatus());
    }
}

