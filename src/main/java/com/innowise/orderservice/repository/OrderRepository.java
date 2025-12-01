package com.innowise.orderservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
