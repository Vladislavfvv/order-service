package com.innowise.orderservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByIdIn(List<Long> ids);

    List<Order> findAllByStatusIn(List<OrderStatus> statuses);
    
    /**
     * Находит все заказы для указанного пользователя (по userId).
     * Используется для получения заказов текущего пользователя.
     */
    List<Order> findAllByUserId(Long userId);
    
    /**
     * Находит заказ по ID с загруженными товарами (items).
     * Использует EntityGraph для загрузки LAZY коллекции items и связанных item.
     */
    @EntityGraph(attributePaths = {"items", "items.item"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") Long id);
}
