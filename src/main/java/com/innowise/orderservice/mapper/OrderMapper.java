package com.innowise.orderservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.innowise.orderservice.dto.OrderDto;
import com.innowise.orderservice.model.Order;

@Mapper(componentModel = "spring", uses = {OrderItemMapper.class})
public interface OrderMapper {
    @Mapping(source = "items", target = "itemDtoList")
    @Mapping(source = "creation_date", target = "creationDate")
    OrderDto toDto(Order order);

    @Mapping(source = "itemDtoList", target = "items")
    @Mapping(source = "creationDate", target = "creation_date")
    Order toEntity(OrderDto orderDto);
}
