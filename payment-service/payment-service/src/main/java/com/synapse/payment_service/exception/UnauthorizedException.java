package com.synapse.payment_service.exception;

public class UnauthorizedException extends PaymentException {
    public UnauthorizedException(ExceptionCode exceptionCode) {
        super(exceptionCode);
    }
}
