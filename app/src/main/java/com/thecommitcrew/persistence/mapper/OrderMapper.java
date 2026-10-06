package com.thecommitcrew.persistence.mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.thecommitcrew.domain.enums.OrderStatus;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.messaging.OrderEvent;

@Mapper
public interface OrderMapper {
    Optional<Order> findById(UUID orderId);
    void save(Order order);
    List<Order> findByAccountId(String accountId);
    List<Order> findByAccountIdAndStatus(String accountId, OrderStatus status);
    List<OrderEvent> findPendingOlderThan(@Param("seconds") long seconds, @Param("limit") int limit);
}
