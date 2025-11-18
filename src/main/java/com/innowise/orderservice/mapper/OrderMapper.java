package com.innowise.orderservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.model.Order;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {
    @Mapping(source = "items", target = "itemDtoList")
    OrderDto toDto(Order order);

    @Mapping(source = "itemDtoList", target = "items")
    Order toEntity(OrderDto orderDto);
}
