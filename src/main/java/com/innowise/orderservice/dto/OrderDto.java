package com.innowise.orderservice.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.innowise.orderservice.model.OrderStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    private Long id;
    private Long userId;
    private OrderStatus status;
    private LocalDateTime creationDate;
    private List<OrderItemDto> itemDtoList;
}
