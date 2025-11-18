package com.innowise.orderservice.dto;

import com.innowise.orderservice.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrderRequest {
    @NotNull(message = "Status is required")
    private OrderStatus status;
}
