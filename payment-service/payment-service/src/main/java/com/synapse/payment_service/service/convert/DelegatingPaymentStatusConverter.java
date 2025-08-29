package com.synapse.payment_service.service.convert;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.synapse.payment_service.domain.entity.Order;
import com.synapse.payment_service.exception.ExceptionCode;
import com.synapse.payment_service.exception.PaymentVerificationException;
import com.synapse.payment_service.service.convert.impl.CancelledPaymentConverter;
import com.synapse.payment_service.service.convert.impl.FailedPaymentConverter;
import com.synapse.payment_service.service.convert.impl.PaidPaymentConverter;
import com.synapse.payment_service.service.convert.impl.PartialCancelledPaymentConverter;
import com.synapse.payment_service.service.convert.impl.PayPendingPaymentConverter;
import com.synapse.payment_service.service.convert.impl.ReadyPaymentConverter;
import com.synapse.payment_service.service.convert.impl.VirtualAccountIssuedPaymentConverter;

import io.portone.sdk.server.payment.Payment;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DelegatingPaymentStatusConverter implements PaymentStatusConverter {

    private final List<PaymentStatusConverter> converters;

    public DelegatingPaymentStatusConverter() {
        List<PaymentStatusConverter> paymentConverters = Arrays.asList(
                new PaidPaymentConverter(),
                new CancelledPaymentConverter(),
                new PartialCancelledPaymentConverter(),
                new PayPendingPaymentConverter(),
                new ReadyPaymentConverter(),
                new VirtualAccountIssuedPaymentConverter(),
                new FailedPaymentConverter());
        this.converters = Collections.unmodifiableList(new LinkedList<>(paymentConverters));
    }

    @Override
    public boolean canHandle(Class<? extends Payment> paymentStatus) {
        return converters.stream()
                .anyMatch(converter -> converter.canHandle(paymentStatus));
    }

    @Override
    public void processPayment(Order order, Payment payment) {
        Assert.notNull(order, "order cannot be null");
        Assert.notNull(payment, "payment cannot be null");

        for (PaymentStatusConverter converter : this.converters) {
            if (converter.canHandle(payment.getClass())) {
                log.info("결제 상태 처리: {} -> {}", payment.getClass(), converter.getClass().getSimpleName());
                converter.processPayment(order, payment);
                return;
            }
        }

        // 처리할 수 있는 컨버터가 없는 경우
        log.error("지원하지 않는 결제 상태: {}", payment.getClass());
        throw new PaymentVerificationException(ExceptionCode.UNSUPPORTED_PAYMENT_STATUS);
    }
}
