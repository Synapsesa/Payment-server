package com.synapse.payment_service.domain.enums;

public enum PaymentStatus {
    PAID, // 결제 완료
    FAILED, // 결제 실패
    CANCELED, // 결제 취소 (환불)
    PENDING, // 결제 대기
    PARTIAL_CANCELLED, // 부분 취소
    PAY_PENDING, // 결제 완료 대기
    READY, // 준비 상태
    VIRTUAL_ACCOUNT_ISSUED // 가상계좌 발급 완료
}
