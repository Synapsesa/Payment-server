package com.synapse.payment_service.domain.enums;

public enum SubscriptionStatus {
    ACTIVE, // 활성
    CANCELED, // 사용자가 취소하여, 만료일에 종료 예정
    EXPIRED, // 만료됨
    PAYMENT_FAILED // 결제 실패
}
