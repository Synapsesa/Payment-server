package com.synapse.payment_service.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.synapse.payment_service.TestConfig;
import com.synapse.payment_service.domain.entity.Subscription;
import com.synapse.payment_service.domain.enums.SubscriptionStatus;
import com.synapse.payment_service.domain.enums.SubscriptionTier;
import com.synapse.payment_service.domain.repository.SubscriptionRepository;

@DisplayName("SubscriptionScheduler 통합 테스트")
class SubscriptionSchedulerIntegrationTest extends TestConfig {

    @Autowired
    private SubscriptionScheduler subscriptionScheduler;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private Subscription canceledSubscription;
    private Subscription paymentFailedSubscription;
    private Subscription activeSubscription;

    @BeforeEach
    void setUp() {
        // 테스트 데이터 정리
        subscriptionRepository.deleteAll();

        ZonedDateTime pastTime = ZonedDateTime.now().minusDays(1);
        ZonedDateTime futureTime = ZonedDateTime.now().plusDays(30);

        // 만료된 CANCELED 구독 생성
        canceledSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(100)
                .expiresAt(pastTime)
                .status(SubscriptionStatus.CANCELED)
                .build();
        canceledSubscription.deactivate(); // autoRenew를 false로 설정하지만 테스트를 위해 다시 true로 설정

        // 만료된 PAYMENT_FAILED 구독 생성
        paymentFailedSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(200)
                .expiresAt(pastTime)
                .status(SubscriptionStatus.PAYMENT_FAILED)
                .build();

        // 만료되지 않은 ACTIVE 구독 생성 (비교 대상)
        activeSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.FREE)
                .remainingChatCredits(100)
                .expiresAt(futureTime)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        subscriptionRepository.saveAll(List.of(canceledSubscription, paymentFailedSubscription, activeSubscription));
    }

    @Test
    @DisplayName("만료된 구독들의 상태를 EXPIRED로 변경하고 autoRenew를 false로 설정한다")
    void expireSubscriptions_shouldUpdateExpiredSubscriptionsInDatabase() {
        // given
        // setUp에서 만료된 구독들이 준비됨

        // when
        subscriptionScheduler.expireSubscriptions();

        // then
        // 만료된 구독들이 EXPIRED 상태로 변경되고 autoRenew가 false가 되었는지 확인
        Subscription updatedCanceledSubscription = subscriptionRepository.findById(canceledSubscription.getId())
                .orElseThrow();
        Subscription updatedPaymentFailedSubscription = subscriptionRepository
                .findById(paymentFailedSubscription.getId()).orElseThrow();
        Subscription updatedActiveSubscription = subscriptionRepository.findById(activeSubscription.getId())
                .orElseThrow();

        // 만료된 구독들은 EXPIRED 상태가 되고 autoRenew가 false가 되어야 함
        assertThat(updatedCanceledSubscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(updatedCanceledSubscription.isAutoRenew()).isFalse();

        assertThat(updatedPaymentFailedSubscription.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(updatedPaymentFailedSubscription.isAutoRenew()).isFalse();

        // ACTIVE 구독은 변경되지 않아야 함
        assertThat(updatedActiveSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(updatedActiveSubscription.isAutoRenew()).isTrue();
    }

    @Test
    @DisplayName("만료 대상 구독이 없으면 아무것도 변경하지 않는다")
    void expireSubscriptions_shouldNotChangeAnythingWhenNoExpiredSubscriptions() {
        // given
        subscriptionRepository.deleteAll();

        // 만료되지 않은 구독만 생성
        Subscription futureSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.FREE)
                .remainingChatCredits(100)
                .expiresAt(ZonedDateTime.now().plusDays(30))
                .status(SubscriptionStatus.CANCELED)
                .build();
        subscriptionRepository.save(futureSubscription);

        // when
        subscriptionScheduler.expireSubscriptions();

        // then
        Subscription unchangedSubscription = subscriptionRepository.findById(futureSubscription.getId()).orElseThrow();
        assertThat(unchangedSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(unchangedSubscription.isAutoRenew()).isTrue();
    }

    @Test
    @DisplayName("ACTIVE 상태의 만료된 구독은 처리하지 않는다")
    void expireSubscriptions_shouldNotProcessActiveExpiredSubscriptions() {
        // given
        subscriptionRepository.deleteAll();

        // 만료된 ACTIVE 구독 생성 (이는 처리 대상이 아님)
        Subscription expiredActiveSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(100)
                .expiresAt(ZonedDateTime.now().minusDays(1))
                .status(SubscriptionStatus.ACTIVE)
                .build();
        subscriptionRepository.save(expiredActiveSubscription);

        // when
        subscriptionScheduler.expireSubscriptions();

        // then
        Subscription unchangedSubscription = subscriptionRepository.findById(expiredActiveSubscription.getId())
                .orElseThrow();
        assertThat(unchangedSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(unchangedSubscription.isAutoRenew()).isTrue();
    }
}
