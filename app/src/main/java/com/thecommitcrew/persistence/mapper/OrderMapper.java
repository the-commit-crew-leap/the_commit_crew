package com.thecommitcrew.persistence.mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;

import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.model.Order;

@Mapper
public interface OrderMapper {
    Optional<Order> findById(UUID orderId);
    void save(Order order);
    List<Order> findByAccountId(Long accountId);
    List<Order> findByAccountIdAndStatus(Long accountId, OrderStatus status);
}
