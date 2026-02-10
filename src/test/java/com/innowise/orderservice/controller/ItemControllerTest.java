package com.innowise.orderservice.controller;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.orderservice.dto.ItemDto;
import com.innowise.orderservice.exception.ItemNotFoundException;
import com.innowise.orderservice.service.ItemService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ItemController.class)
class ItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ItemService itemService;

    @Autowired
    private ObjectMapper objectMapper;

    private ItemDto itemDto;

    @BeforeEach
    void setUp() {
        itemDto = new ItemDto();
        itemDto.setId(1L);
        itemDto.setName("Laptop");
        itemDto.setPrice(BigDecimal.valueOf(1500.00));
    }

    @Test
    @DisplayName("GET /api/v1/items - успешное получение списка товаров")
    void getAllItems_ShouldReturnItems() throws Exception {
        when(itemService.getAllItems()).thenReturn(List.of(itemDto));

        mockMvc.perform(get("/api/v1/items")
                        .with(jwt().jwt(jwt -> jwt.subject("user@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("Laptop"));
    }

    @Test
    @DisplayName("GET /api/v1/items/1 - успешное получение товара по ID")
    void getItemById_ShouldReturnItem_WhenExists() throws Exception {
        when(itemService.getItemById(1L)).thenReturn(itemDto);

        mockMvc.perform(get("/api/v1/items/{id}", 1L)
                        .with(jwt().jwt(jwt -> jwt.subject("user@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Laptop"));
    }

    @Test
    @DisplayName("GET /api/v1/items/999 - товар не найден")
    void getItemById_ShouldReturnNotFound_WhenNotExists() throws Exception {
        when(itemService.getItemById(999L)).thenThrow(new ItemNotFoundException("Item not found: 999"));

        mockMvc.perform(get("/api/v1/items/{id}", 999L)
                        .with(jwt().jwt(jwt -> jwt.subject("user@example.com").claim("role", "ROLE_USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/items - успешное создание товара (ADMIN)")
    void createItem_ShouldCreateItem_WhenAdmin() throws Exception {
        ItemDto createDto = new ItemDto();
        createDto.setName("Keyboard");
        createDto.setPrice(BigDecimal.valueOf(75.00));

        ItemDto createdDto = new ItemDto(2L, "Keyboard", null, BigDecimal.valueOf(75.00));
        when(itemService.createItem(any(ItemDto.class))).thenReturn(createdDto);

        mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto))
                        .with(jwt().jwt(jwt -> jwt.subject("admin@example.com").claim("role", "ROLE_ADMIN"))))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.name").value("Keyboard"));
    }

    @Test
    @DisplayName("PUT /api/v1/items/1 - успешное обновление товара (ADMIN)")
    void updateItem_ShouldUpdate_WhenAdmin() throws Exception {
        ItemDto updateDto = new ItemDto();
        updateDto.setName("Laptop Pro");
        updateDto.setPrice(BigDecimal.valueOf(2000.00));

        ItemDto updatedDto = new ItemDto(1L, "Laptop Pro", null, BigDecimal.valueOf(2000.00));
        when(itemService.updateItem(eq(1L), any(ItemDto.class))).thenReturn(updatedDto);

        mockMvc.perform(put("/api/v1/items/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto))
                        .with(jwt().jwt(jwt -> jwt.subject("admin@example.com").claim("role", "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.name").value("Laptop Pro"));
    }

    @Test
    @DisplayName("DELETE /api/v1/items/1 - успешное удаление товара (ADMIN)")
    void deleteItem_ShouldDelete_WhenAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/items/{id}", 1L)
                        .with(jwt().jwt(jwt -> jwt.subject("admin@example.com").claim("role", "ROLE_ADMIN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/items/999 - товар не найден (ADMIN)")
    void deleteItem_ShouldReturnNotFound_WhenNotExists() throws Exception {
        doThrow(new ItemNotFoundException("Item not found: 999"))
                .when(itemService).deleteItem(999L);

        mockMvc.perform(delete("/api/v1/items/{id}", 999L)
                        .with(jwt().jwt(jwt -> jwt.subject("admin@example.com").claim("role", "ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }
}


