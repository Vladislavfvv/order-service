package com.innowise.orderservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.innowise.orderservice.model.OrderItem;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
