package com.innowise.orderservice.mapper;

import org.mapstruct.Mapper;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.model.Order;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {
    OrderDto toDto(Order order);

    Order toEntity(OrderDto orderDto);
}
