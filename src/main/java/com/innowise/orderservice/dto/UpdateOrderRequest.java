package com.innowise.orderservice.dto;

import com.innowise.orderservice.model.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderRequest {
    private OrderStatus status;
}
