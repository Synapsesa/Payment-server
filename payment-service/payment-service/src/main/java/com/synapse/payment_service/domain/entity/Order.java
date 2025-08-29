package com.synapse.payment_service.domain.entity;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.synapse.payment_service.domain.common.BaseEntity;
import com.synapse.payment_service.domain.enums.PaymentStatus;
import com.synapse.payment_service.domain.enums.SubscriptionTier;
import com.synapse.payment_service.exception.ExceptionCode;
import com.synapse.payment_service.exception.PaymentVerificationException;

import io.portone.sdk.server.payment.Payment;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders")
public class Order extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;

    @Column(unique = true)
    private String iamPortTransactionId; // 아임포트에서 사용하는 결제 건별 고유 ID, 환불시 사용

    @Column(nullable = false, unique = true)
    private String paymentId; // 주문별 고유 ID. 중복 결제 방지

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private ZonedDateTime paidAt;

    @Builder
    public Order(
        Subscription subscription, String iamPortTransactionId, String paymentId, 
        BigDecimal amount, PaymentStatus status, ZonedDateTime paidAt
    ) {
        this.subscription = subscription;
        this.iamPortTransactionId = iamPortTransactionId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = status;
        this.paidAt = paidAt;
    }

    public void updateStatus(PaymentStatus status) {
        this.status = status;
    }

    public void updateIamPortTransactionId(String iamPortTransactionId) {
        this.iamPortTransactionId = iamPortTransactionId;
    }

    // 도메인 비즈니스 메서드들
    public boolean isAlreadyProcessed() {
        return this.status != PaymentStatus.PENDING;
    }

    public void validatePaymentAmount(Payment.Recognized recognizedPayment) {
        if (this.amount.compareTo(BigDecimal.valueOf(recognizedPayment.getAmount().getTotal())) != 0) {
            throw new PaymentVerificationException(ExceptionCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    public boolean hasBillingKey(Payment.Recognized recognizedPayment) {
        return this.status == PaymentStatus.PAID && recognizedPayment.getBillingKey() != null;
    }

    public void markAsPaid() {
        this.status = PaymentStatus.PAID;
        this.paidAt = ZonedDateTime.now();
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELED;
    }

    // 정적 팩토리 메서드
    public static Order createForSubscription(Subscription subscription, SubscriptionTier tier) {
        BigDecimal amount = tier.getMonthlyPrice();
        String orderName = tier.getTierName() + "_subscription";
        String paymentId = orderName + "_" + UUID.randomUUID();

        return Order.builder()
                .subscription(subscription)
                .paymentId(paymentId)
                .amount(amount)
                .status(PaymentStatus.PENDING)
                .build();
    }

    public String getOrderName() {
        String[] parts = this.paymentId.split("_");
        if (parts.length >= 2) {
            return parts[0] + "_" + parts[1]; // tier_subscription 형태
        }
        return this.paymentId;
    }
}
