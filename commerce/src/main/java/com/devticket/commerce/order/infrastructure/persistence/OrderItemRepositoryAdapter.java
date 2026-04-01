package com.devticket.commerce.order.infrastructure.persistence;

import com.devticket.commerce.order.domain.model.OrderItem;
import com.devticket.commerce.order.domain.repository.OrderItemRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderItemRepositoryAdapter implements OrderItemRepository {

    private final OrderItemJpaRepository orderItemJpaRepository;

    @Override
    public OrderItem save(OrderItem orderItem) {
        return orderItemJpaRepository.save(orderItem);
    }

    @Override
    public List<OrderItem> saveAll(List<OrderItem> orderItems) {
        return orderItemJpaRepository.saveAll(orderItems);
    }

    @Override
    public List<OrderItem> findAllByOrderId(Long orderId) {
        return orderItemJpaRepository.findAllByOrderId(orderId);
    }

    @Override
    public List<OrderItem> findSettlementItems(List<UUID> eventIds) {
        return orderItemJpaRepository.findSettlementItems(eventIds);
    }

    @Override
    public List<OrderItem> findAllByEventId(UUID eventId) {
        return orderItemJpaRepository.findAllByEventId(eventId);
    }

    @Override
    public Optional<OrderItem> findByOrderItemId(UUID orderItemId) {
        return orderItemJpaRepository.findByOrderItemId(orderItemId);
    }
}
