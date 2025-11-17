package com.innowise.orderservice.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import com.innowise.orderservice.model.Item;

public interface ItemRepository extends JpaRepository<Item, Long> {
}
