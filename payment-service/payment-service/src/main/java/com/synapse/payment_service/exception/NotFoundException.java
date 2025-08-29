package com.synapse.payment_service.exception;

public class NotFoundException extends PaymentException {
    public NotFoundException(ExceptionCode exceptionCode) {
        super(exceptionCode);
    }
}
