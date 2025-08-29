package com.synapse.payment_service.service.convert.impl;

import org.springframework.transaction.annotation.Transactional;

import com.synapse.payment_service.domain.entity.Order;
import com.synapse.payment_service.domain.enums.PaymentStatus;
import com.synapse.payment_service.service.convert.PaymentStatusConverter;

import io.portone.sdk.server.payment.Payment;
import io.portone.sdk.server.payment.VirtualAccountIssuedPayment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VirtualAccountIssuedPaymentConverter implements PaymentStatusConverter {

    @Override
    public boolean canHandle(Class<? extends Payment> paymentStatus) {
        return paymentStatus.equals(VirtualAccountIssuedPayment.class);
    }

    @Override
    @Transactional
    public void processPayment(Order order, Payment payment) {
        log.info("가상계좌 발급 완료 처리 시작. paymentId={}", order.getPaymentId());

        // 주문 상태를 가상계좌 발급 완료로 업데이트
        order.updateStatus(PaymentStatus.VIRTUAL_ACCOUNT_ISSUED);

        log.info("가상계좌 발급 완료 처리 완료. paymentId={}", order.getPaymentId());
    }
}
