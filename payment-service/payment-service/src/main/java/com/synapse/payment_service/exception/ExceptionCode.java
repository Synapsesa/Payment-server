package com.synapse.payment_service.exception;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExceptionCode {
    PORT_ONE_CLIENT_ERROR(INTERNAL_SERVER_ERROR, "P001", "포트원 클라이언트 오류"),

    SUBSCRIPTION_NOT_FOUND(NOT_FOUND, "P002", "구독 정보를 찾을 수 없습니다"),
    ORDER_NOT_FOUND(NOT_FOUND, "P003", "주문 정보를 찾을 수 없습니다"),
    BILLING_KEY_NOT_FOUND(NOT_FOUND, "P008", "빌링키 정보를 찾을 수 없습니다"),

    PAYMENT_VERIFICATION_FAILED(BAD_REQUEST, "P004", "존재하지 않는 거래입니다"),
    PAYMENT_NOT_RECOGNIZED(INTERNAL_SERVER_ERROR, "P005", "결제 정보를 인식할 수 없습니다"),
    PAYMENT_AMOUNT_MISMATCH(CONFLICT, "P006", "결제 금액이 불일치합니다"),
    UNSUPPORTED_PAYMENT_STATUS(INTERNAL_SERVER_ERROR, "P007", "지원하지 않는 결제 상태입니다"),

    UNAUTHORIZED_USER(UNAUTHORIZED, "P009", "권한이 없습니다"),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
