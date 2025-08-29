package com.synapse.payment_service.exception;

import lombok.Getter;

@Getter
public abstract class PaymentException extends RuntimeException {
    private final ExceptionCode exceptionCode;

    protected PaymentException(ExceptionCode exceptionCode) {
        super(exceptionCode.getMessage());
        this.exceptionCode = exceptionCode;
    }
}
