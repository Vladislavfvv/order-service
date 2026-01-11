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

    Optional<Order> findById(Long id);
    
    /**
     * Находит все заказы с загруженными товарами (items).
     * Использует EntityGraph для загрузки LAZY коллекции items и связанных item.
     * Сортирует заказы по ID в порядке возрастания.
     */
    @EntityGraph(attributePaths = {"items", "items.item"})
    @Query("SELECT o FROM Order o ORDER BY o.id ASC")
    List<Order> findAllWithItems();
    
    /**
     * Находит заказы по списку ID с загруженными товарами (items).
     * Использует EntityGraph для загрузки LAZY коллекции items и связанных item.
     */
    @EntityGraph(attributePaths = {"items", "items.item"})
    @Query("SELECT o FROM Order o WHERE o.id IN :ids")
    List<Order> findAllByIdInWithItems(@Param("ids") List<Long> ids);
    
    /**
     * Находит заказы по списку статусов с загруженными товарами (items).
     * Использует EntityGraph для загрузки LAZY коллекции items и связанных item.
     */
    @EntityGraph(attributePaths = {"items", "items.item"})
    @Query("SELECT o FROM Order o WHERE o.status IN :statuses")
    List<Order> findAllByStatusInWithItems(@Param("statuses") List<OrderStatus> statuses);
    
    /**
     * Находит все заказы пользователя с загруженными товарами (items).
     * Использует EntityGraph для загрузки LAZY коллекции items и связанных item.
     */
    @EntityGraph(attributePaths = {"items", "items.item"})
    @Query("SELECT o FROM Order o WHERE o.userId = :userId")
    List<Order> findAllByUserIdWithItems(@Param("userId") Long userId);
}
