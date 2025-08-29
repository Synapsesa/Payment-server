package com.synapse.payment_service.service.convert.impl;

import org.springframework.transaction.annotation.Transactional;

import com.synapse.payment_service.domain.entity.Order;
import com.synapse.payment_service.domain.enums.PaymentStatus;
import com.synapse.payment_service.service.convert.PaymentStatusConverter;

import io.portone.sdk.server.payment.PartialCancelledPayment;
import io.portone.sdk.server.payment.Payment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PartialCancelledPaymentConverter implements PaymentStatusConverter {

    @Override
    public boolean canHandle(Class<? extends Payment> paymentStatus) {
        return paymentStatus.equals(PartialCancelledPayment.class);
    }

    @Override
    @Transactional
    public void processPayment(Order order, Payment payment) {
        log.info("부분 취소 처리 시작. paymentId={}", order.getPaymentId());

        // 주문 상태를 부분 취소로 업데이트
        order.updateStatus(PaymentStatus.PARTIAL_CANCELLED);

        // 부분 취소 시에는 구독은 유지하되 크레딧 조정 등의 로직이 필요할 수 있음
        log.info("부분 취소 처리 완료. paymentId={}", order.getPaymentId());
    }
}
