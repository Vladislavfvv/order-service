package com.innowise.orderservice.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.innowise.orderservice.dto.ItemDto;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.mapper.ItemMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.repository.ItemRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;

    /**
     * Получает все товары.     * 
     * список всех товаров
     */
    @Transactional(readOnly = true)
    public List<ItemDto> getAllItems() {
        log.info("Getting all items");
        return itemRepository.findAll().stream()
                .map(itemMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Получает товар по ID.
     * 
     * @param id ID товара
     * @return товар
     * @throws ItemNotFoundException если товар не найден
     */
    @Transactional(readOnly = true)
    public ItemDto getItemById(Long id) {
        log.info("Getting item by ID: {}", id);
        Item item = itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException("Item not found: " + id));
        return itemMapper.toDto(item);
    }

    /**
     * Создает новый товар.
     *
     * @param itemDto данные товара
     * @return созданный товар
     */
    @Transactional
    public ItemDto createItem(ItemDto itemDto) {
        log.info("Creating item: {}", itemDto.getName());
        Item item = itemMapper.toEntity(itemDto);
        Item savedItem = itemRepository.save(item);
        return itemMapper.toDto(savedItem);
    }

    /**
     * Обновляет существующий товар.
     *
     * @param id      ID товара для обновления
     * @param itemDto новые данные товара
     * @return обновленный товар
     * @throws ItemNotFoundException если товар не найден
     */
    @Transactional
    public ItemDto updateItem(Long id, ItemDto itemDto) {
        log.info("Updating item with ID: {}", id);

        Item existingItem = itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException("Item not found: " + id));

        existingItem.setName(itemDto.getName());
        existingItem.setPrice(itemDto.getPrice());

        Item updatedItem = itemRepository.save(existingItem);
        return itemMapper.toDto(updatedItem);
    }

    /**
     * Удаляет товар по ID.
     *
     * @param id ID товара
     * @throws ItemNotFoundException если товар не найден
     */
    @Transactional
    public void deleteItem(Long id) {
        log.info("Deleting item with ID: {}", id);

        Item existingItem = itemRepository.findById(id)
                .orElseThrow(() -> new ItemNotFoundException("Item not found: " + id));

        itemRepository.delete(existingItem);
        log.info("Item with ID {} successfully deleted", id);
    }
}

