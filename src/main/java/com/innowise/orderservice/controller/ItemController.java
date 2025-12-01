package com.innowise.orderservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.innowise.orderservice.dto.ItemDto;
import com.innowise.orderservice.service.ItemService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    /**
     * Получает все товары.
     * Доступно всем аутентифицированным пользователям.
     * 
     * @return список всех товаров
     */
    @GetMapping
    public ResponseEntity<List<ItemDto>> getAllItems() {
        log.info("Getting all items");
        List<ItemDto> items = itemService.getAllItems();
        return ResponseEntity.ok(items);
    }

    /**
     * Получает товар по ID.
     * Доступно всем аутентифицированным пользователям.
     * 
     * @param id ID товара
     * @return товар
     */
    @GetMapping("/{id}")
    public ResponseEntity<ItemDto> getItemById(@PathVariable Long id) {
        log.info("Getting item by ID: {}", id);
        ItemDto item = itemService.getItemById(id);
        return ResponseEntity.ok(item);
    }

    /**
     * Создает новый товар.
     * Доступно только администраторам.
     * 
     * @param itemDto данные товара
     * @return созданный товар
     */
    @PostMapping
    public ResponseEntity<ItemDto> createItem(@Valid @RequestBody ItemDto itemDto) {
        log.info("Creating item: {}", itemDto.getName());
        ItemDto createdItem = itemService.createItem(itemDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdItem);
    }

    /**
     * Обновляет существующий товар.
     * Доступно только администраторам.
     *
     * @param id      ID товара
     * @param itemDto новые данные товара
     * @return обновленный товар
     */
    @PutMapping("/{id}")
    public ResponseEntity<ItemDto> updateItem(
            @PathVariable Long id,
            @Valid @RequestBody ItemDto itemDto) {
        log.info("Updating item with ID: {}", id);
        ItemDto updatedItem = itemService.updateItem(id, itemDto);
        return ResponseEntity.ok(updatedItem);
    }

    /**
     * Удаляет товар.
     * Доступно только администраторам.
     *
     * @param id ID товара
     * @return 204 No Content при успешном удалении
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        log.info("Deleting item with ID: {}", id);
        itemService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}

