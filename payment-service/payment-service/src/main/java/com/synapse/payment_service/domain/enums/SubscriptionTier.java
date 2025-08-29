package com.synapse.payment_service.domain.enums;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriptionTier {
    PRO("pro", "subscription-pro", 100, new BigDecimal("100000")),
    FREE("free", "subscription-free", 10, BigDecimal.ZERO),
    UNKNOWN("unknown", "subscription-free", 10, BigDecimal.ZERO);

    private final String tierName;
    private final String policyName;
    private final int maxRequestCount;
    private final BigDecimal monthlyPrice;
}
