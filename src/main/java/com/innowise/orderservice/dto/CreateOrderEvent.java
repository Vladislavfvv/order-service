package com.innowise.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * Event DTO for CREATE_ORDER event sent to Kafka
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderEvent {
    private Long orderId;
    private Long userId;    
}

