package com.innowise.orderservice.mapper;

import org.mapstruct.Mapper;
import com.innowise.orderservice.dto.ItemDto;
import com.innowise.orderservice.model.Item;

@Mapper(componentModel = "spring")
public interface ItemMapper {
    @org.mapstruct.Mapping(target = "description", ignore = true)
    ItemDto toDto(Item item);
    
    Item toEntity(ItemDto itemDto);
}
