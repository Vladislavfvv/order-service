package com.innowise.orderservice.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.innowise.orderservice.dto.ItemDto;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.mapper.ItemMapper;
import com.innowise.orderservice.model.Item;
import com.innowise.orderservice.repository.ItemRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItemServiceTest {

    @InjectMocks
    private ItemService itemService;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ItemMapper itemMapper;

    private Item item;
    private ItemDto itemDto;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        item = new Item();
        item.setId(1L);
        item.setName("Laptop");
        item.setPrice(BigDecimal.valueOf(1500.00));

        itemDto = new ItemDto();
        itemDto.setId(1L);
        itemDto.setName("Laptop");
        itemDto.setPrice(BigDecimal.valueOf(1500.00));
    }

    @Test
    @DisplayName("getAllItems - успешно возвращает список товаров")
    void getAllItems_ShouldReturnListOfItems() {
        when(itemRepository.findAll()).thenReturn(List.of(item));
        when(itemMapper.toDto(item)).thenReturn(itemDto);

        List<ItemDto> result = itemService.getAllItems();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Laptop", result.get(0).getName());
        verify(itemRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("getItemById - товар существует, возвращает DTO")
    void getItemById_ShouldReturnItem_WhenExists() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemMapper.toDto(item)).thenReturn(itemDto);

        ItemDto result = itemService.getItemById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Laptop", result.getName());
        verify(itemRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("getItemById - товар не найден, выбрасывает ItemNotFoundException")
    void getItemById_ShouldThrow_WhenNotFound() {
        when(itemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> itemService.getItemById(999L));
        verify(itemRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("createItem - успешно создает товар")
    void createItem_ShouldSaveAndReturnItem() {
        when(itemMapper.toEntity(itemDto)).thenReturn(item);
        when(itemRepository.save(any(Item.class))).thenReturn(item);
        when(itemMapper.toDto(item)).thenReturn(itemDto);

        ItemDto result = itemService.createItem(itemDto);

        assertNotNull(result);
        assertEquals("Laptop", result.getName());
        verify(itemRepository, times(1)).save(any(Item.class));
    }

    @Test
    @DisplayName("updateItem - товар существует, успешно обновляется")
    void updateItem_ShouldUpdate_WhenExists() {
        ItemDto updateDto = new ItemDto();
        updateDto.setName("Laptop Pro");
        updateDto.setPrice(BigDecimal.valueOf(2000.00));

        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class))).thenReturn(item);
        when(itemMapper.toDto(any(Item.class))).thenAnswer(invocation -> {
            Item saved = invocation.getArgument(0);
            ItemDto dto = new ItemDto();
            dto.setId(saved.getId());
            dto.setName(saved.getName());
            dto.setPrice(saved.getPrice());
            return dto;
        });

        ItemDto result = itemService.updateItem(1L, updateDto);

        assertNotNull(result);
        assertEquals("Laptop Pro", result.getName());
        assertEquals(BigDecimal.valueOf(2000.00), result.getPrice());
        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository, times(1)).save(any(Item.class));
    }

    @Test
    @DisplayName("updateItem - товар не найден, выбрасывает ItemNotFoundException")
    void updateItem_ShouldThrow_WhenNotFound() {
        ItemDto updateDto = new ItemDto();
        updateDto.setName("Laptop Pro");
        updateDto.setPrice(BigDecimal.valueOf(2000.00));

        when(itemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> itemService.updateItem(999L, updateDto));
        verify(itemRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("deleteItem - товар существует, успешно удаляется")
    void deleteItem_ShouldDelete_WhenExists() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

        itemService.deleteItem(1L);

        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository, times(1)).delete(item);
    }

    @Test
    @DisplayName("deleteItem - товар не найден, выбрасывает ItemNotFoundException")
    void deleteItem_ShouldThrow_WhenNotFound() {
        when(itemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ItemNotFoundException.class, () -> itemService.deleteItem(999L));
        verify(itemRepository, times(1)).findById(999L);
    }
}


