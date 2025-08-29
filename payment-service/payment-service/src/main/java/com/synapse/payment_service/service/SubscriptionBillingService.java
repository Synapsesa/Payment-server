package com.synapse.payment_service.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.payment_service.configuration.PortOneClientProperties;
import com.synapse.payment_service.domain.entity.Order;
import com.synapse.payment_service.domain.entity.Subscription;
import com.synapse.payment_service.domain.enums.PaymentStatus;
import com.synapse.payment_service.domain.repository.OrderRepository;
import com.synapse.payment_service.domain.repository.SubscriptionRepository;

import io.portone.sdk.server.PortOneClient;
import io.portone.sdk.server.common.PaymentAmountInput;
import io.portone.sdk.server.payment.PayWithBillingKeyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubscriptionBillingService {
    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;
    private final PortOneClient portOneClient;
    private final PortOneClientProperties portOneClientProperties;

    @Transactional
    public void processDailySubscriptions() {
        // 오늘이 다음 결제일인 모든 활성 구독을 찾는다.
        LocalDate today = LocalDate.now();
        ZonedDateTime startOfDay = today.atStartOfDay(ZoneId.systemDefault());
        ZonedDateTime endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault());

        List<Subscription> targets = subscriptionRepository.findActiveSubscriptionsDueForRenewal(startOfDay, endOfDay);
        for (Subscription subscription : targets) {
            chargeWithBillingKey(subscription);
        }
    }

    private void chargeWithBillingKey(Subscription subscription) {
        // Order 객체를 먼저 생성하여 일관된 로직 사용
        Order order = Order.createForSubscription(subscription, subscription.getTier());
        orderRepository.save(order); // PENDING 상태로 저장

        String billingKey = subscription.getBillingKey();
        PaymentAmountInput amount = new PaymentAmountInput(subscription.getTier().getMonthlyPrice().longValue(), 0L, 0L);

        // 빌링키 결제 요청
        try {
            PayWithBillingKeyResponse response = portOneClient.getPayment().payWithBillingKey(
                    order.getPaymentId(),
                    billingKey,
                    portOneClientProperties.channelKey(),
                    order.getOrderName(),
                    null, null, amount, null, null, null, null, null, null, null, null, null, null, null, null, null,
                    null).join();
            successHandler(response, order, subscription);
            log.info("구독 결제 성공. paymentId={}, orderName={}, subscriptionId={}", order.getPaymentId(), order.getOrderName(), subscription.getId());
        } catch (Exception e) {
            failureHandler(order, subscription);
            log.error("구독 결제 실패. paymentId={}, orderName={}, subscriptionId={}", order.getPaymentId(), order.getOrderName(), subscription.getId());
        }
    }

    private void successHandler(PayWithBillingKeyResponse response, Order order, Subscription subscription) {
        // 기존 Order 객체 업데이트
        order.updateIamPortTransactionId(response.getPayment().getPgTxId());
        order.markAsPaid();

        subscription.renewSubscription(subscription.getTier());

        orderRepository.save(order);
    }

    private void failureHandler(Order order, Subscription subscription) {
        // 기존 Order 객체 상태를 FAILED로 업데이트
        order.updateStatus(PaymentStatus.FAILED);
        orderRepository.save(order);

        // 구독 상태를 PAYMENT_FAILED로 변경하고 retryCount 증가
        subscription.handlePaymentFailure();
        subscriptionRepository.save(subscription);
    }
}
