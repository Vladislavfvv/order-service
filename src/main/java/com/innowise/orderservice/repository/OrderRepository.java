package com.innowise.orderservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.innowise.orderservice.model.Order;
import com.innowise.orderservice.model.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

      List<Order> findAllByStatusIn(List<OrderStatus> statuses);
}
