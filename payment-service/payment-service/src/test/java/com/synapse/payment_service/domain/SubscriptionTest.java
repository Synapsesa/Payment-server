package com.synapse.payment_service.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.synapse.payment_service.domain.entity.Subscription;
import com.synapse.payment_service.domain.enums.SubscriptionStatus;
import com.synapse.payment_service.domain.enums.SubscriptionTier;

@DisplayName("Subscription 엔티티 테스트")
public class SubscriptionTest {

    private Subscription subscription;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        subscription = Subscription.builder()
                .memberId(memberId)
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(100)
                .expiresAt(ZonedDateTime.now().plusMonths(1))
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("handlePaymentFailure() 호출 시 상태가 PAYMENT_FAILED로 변경되고 retryCount가 1 증가한다")
    void handlePaymentFailure_shouldChangeStatusAndIncrementRetryCount() {
        // given
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getRetryCount()).isEqualTo(0);

        // when
        subscription.handlePaymentFailure();

        // then
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
        assertThat(subscription.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("handlePaymentFailure() 여러 번 호출 시 retryCount가 누적된다")
    void handlePaymentFailure_multipleCallsShouldAccumulateRetryCount() {
        // given
        assertThat(subscription.getRetryCount()).isEqualTo(0);

        // when
        subscription.handlePaymentFailure();
        subscription.handlePaymentFailure();
        subscription.handlePaymentFailure();

        // then
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
        assertThat(subscription.getRetryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("이미 PAYMENT_FAILED 상태인 구독에서 handlePaymentFailure() 호출 시 retryCount만 증가한다")
    void handlePaymentFailure_alreadyFailedStatus_shouldOnlyIncrementRetryCount() {
        // given
        subscription.handlePaymentFailure(); // 첫 번째 실패
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
        assertThat(subscription.getRetryCount()).isEqualTo(1);

        // when
        subscription.handlePaymentFailure(); // 두 번째 실패

        // then
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
        assertThat(subscription.getRetryCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("새로 생성된 구독의 retryCount 기본값은 0이다")
    void newSubscription_shouldHaveZeroRetryCount() {
        // given & when
        Subscription newSubscription = Subscription.builder()
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.FREE)
                .remainingChatCredits(10)
                .expiresAt(ZonedDateTime.now().plusMonths(1))
                .status(SubscriptionStatus.ACTIVE)
                .build();

        // then
        assertThat(newSubscription.getRetryCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("CANCELED 상태에서 handlePaymentFailure() 호출 시 상태가 PAYMENT_FAILED로 변경된다")
    void handlePaymentFailure_fromCanceledStatus_shouldChangeToPaymentFailed() {
        // given
        subscription.deactivate(); // CANCELED 상태로 변경
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);

        // when
        subscription.handlePaymentFailure();

        // then
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
        assertThat(subscription.getRetryCount()).isEqualTo(1);
    }
}
