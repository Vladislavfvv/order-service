package com.innowise.orderservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import com.innowise.orderservice.dto.OrderItemDto;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderItem;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {

    @Mapping(source = "order.id", target = "orderId")
    @Mapping(source = "item.id", target = "itemId")
    OrderItemDto toDto(OrderItem orderItem);

    @Mapping(source = "orderId", target = "order.id")
    @Mapping(source = "itemId", target = "item.id")
    OrderItem toEntity(OrderItemDto orderItemDto);

    // А это — реализация по умолчанию
    default Order mapOrder(Long id) {
        if (id == null) return null;
        Order order = new Order();
        order.setId(id);
        return order;
    }

    default Item mapItem(Long id) {
        if (id == null) return null;
        Item item = new Item();
        item.setId(id);
        return item;
    }
}
