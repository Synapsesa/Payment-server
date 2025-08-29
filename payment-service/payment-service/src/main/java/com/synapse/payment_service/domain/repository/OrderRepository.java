package com.synapse.payment_service.domain.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.payment_service.domain.entity.Order;
import com.synapse.payment_service.domain.entity.Subscription;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByPaymentId(String paymentId);

    Optional<Order> findBySubscription(Subscription subscription);
}
