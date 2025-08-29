package com.synapse.payment_service.exception;

public class PaymentVerificationException extends PaymentException {
    public PaymentVerificationException(ExceptionCode exceptionCode) {
        super(exceptionCode);
    }
}
