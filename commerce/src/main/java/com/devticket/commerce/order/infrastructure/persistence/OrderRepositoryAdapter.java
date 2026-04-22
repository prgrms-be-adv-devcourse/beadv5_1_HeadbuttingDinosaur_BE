package com.devticket.commerce.order.infrastructure.persistence;

import com.devticket.commerce.common.enums.OrderStatus;
import com.devticket.commerce.order.domain.model.Order;
import com.devticket.commerce.order.domain.repository.OrderRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepository {

    public final OrderJpaRepository orderJpaRepository;

    @Override
    public Order save(Order order) {
        return orderJpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return orderJpaRepository.findById(id);
    }

    @Override
    public List<Order> findAllByIds(List<Long> ids) {
        return orderJpaRepository.findAllByIdIn(ids);
    }

    @Override
    public Optional<Order> findByOrderId(UUID orderId) {
        return orderJpaRepository.findByOrderId(orderId);
    }

    @Override
    public List<Order> findAllByOrderIds(List<UUID> orderIds) {
        return orderJpaRepository.findAllByOrderIdIn(orderIds);
    }

    @Override
    public Page<Order> findAllByUserId(UUID userId, OrderStatus status, Pageable pageable) {
        if (status == null) {
            return orderJpaRepository.findAllByUserId(userId, pageable);
        }
        return orderJpaRepository.findAllByUserIdAndStatus(userId, status, pageable);
    }

    @Override
    public List<Order> findExpiredOrders(OrderStatus status, int expirationMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(expirationMinutes);
        return orderJpaRepository.findExpiredOrders(status, threshold);
    }

    @Override
    public Optional<Order> findActiveOrder(UUID userId, String cartHash, List<OrderStatus> activeStatuses) {
        return orderJpaRepository.findActiveOrder(userId, cartHash, activeStatuses);
    }

    @Override
    public List<Order> findAllByEventIdAndStatus(UUID eventId, OrderStatus status) {
        return orderJpaRepository.findAllByEventIdAndStatus(eventId, status);
    }

}
