package com.synapse.payment_service.service.convert;

import com.synapse.payment_service.domain.entity.Order;

import io.portone.sdk.server.payment.Payment;

public interface PaymentStatusConverter {

    /**
     * 해당 컨버터가 주어진 결제 상태를 처리할 수 있는지 확인
     */
    boolean canHandle(Class<? extends Payment> paymentStatus);

    /**
     * 결제 상태에 따른 주문 처리 로직 실행
     */
    void processPayment(Order order, Payment payment);
}
