package com.innowise.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event DTO for CREATE_PAYMENT event received from Kafka
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentEvent {

    private String orderId;    
    private String status;// SUCCESS / FAILED   
}

