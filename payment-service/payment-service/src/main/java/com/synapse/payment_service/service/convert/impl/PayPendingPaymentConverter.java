package com.synapse.payment_service.service.convert.impl;

import org.springframework.transaction.annotation.Transactional;

import com.synapse.payment_service.domain.entity.Order;
import com.synapse.payment_service.domain.enums.PaymentStatus;
import com.synapse.payment_service.service.convert.PaymentStatusConverter;

import io.portone.sdk.server.payment.PayPendingPayment;
import io.portone.sdk.server.payment.Payment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PayPendingPaymentConverter implements PaymentStatusConverter {

    @Override
    public boolean canHandle(Class<? extends Payment> paymentStatus) {
        return paymentStatus.equals(PayPendingPayment.class);
    }

    @Override
    @Transactional
    public void processPayment(Order order, Payment payment) {
        log.info("결제 완료 대기 처리 시작. paymentId={}", order.getPaymentId());

        // 주문 상태를 결제 완료 대기로 업데이트
        order.updateStatus(PaymentStatus.PAY_PENDING);

        log.info("결제 완료 대기 처리 완료. paymentId={}", order.getPaymentId());
    }
}
