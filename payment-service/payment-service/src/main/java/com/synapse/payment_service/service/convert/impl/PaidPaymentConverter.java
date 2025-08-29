package com.synapse.payment_service.service.convert.impl;

import org.springframework.transaction.annotation.Transactional;

import com.synapse.payment_service.domain.entity.Order;
import com.synapse.payment_service.domain.entity.Subscription;
import com.synapse.payment_service.domain.enums.PaymentStatus;
import com.synapse.payment_service.domain.enums.SubscriptionTier;
import com.synapse.payment_service.exception.ExceptionCode;
import com.synapse.payment_service.exception.PaymentVerificationException;
import com.synapse.payment_service.service.convert.PaymentStatusConverter;

import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.Payment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PaidPaymentConverter implements PaymentStatusConverter {

    @Override
    public boolean canHandle(Class<? extends Payment> paymentStatus) {
        return paymentStatus.equals(PaidPayment.class);
    }

    @Override
    @Transactional
    public void processPayment(Order order, Payment payment) {
        if (!(payment instanceof Payment.Recognized recognizedPayment)) {
            throw new PaymentVerificationException(ExceptionCode.PAYMENT_NOT_RECOGNIZED);
        }
        log.info("결제 완료 처리 시작. paymentId={}, amount={}",
                order.getPaymentId(), recognizedPayment.getAmount().getTotal());

        order.updateStatus(PaymentStatus.PAID);
        Subscription subscription = order.getSubscription();
        subscription.updateBillingKey(recognizedPayment.getBillingKey());
        subscription.renewSubscription(SubscriptionTier.PRO);

        log.info("결제 완료 처리 완료. paymentId={}", order.getPaymentId());
    }
}
