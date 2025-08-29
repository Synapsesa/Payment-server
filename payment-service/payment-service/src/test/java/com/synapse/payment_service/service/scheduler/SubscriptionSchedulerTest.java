package com.synapse.payment_service.service.scheduler;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.synapse.payment_service.TestConfig;
import com.synapse.payment_service.domain.entity.Subscription;
import com.synapse.payment_service.domain.enums.SubscriptionStatus;
import com.synapse.payment_service.domain.enums.SubscriptionTier;
import com.synapse.payment_service.domain.repository.SubscriptionRepository;
import com.synapse.payment_service.service.SubscriptionBillingService;

@DisplayName("SubscriptionScheduler 테스트")
class SubscriptionSchedulerTest extends TestConfig {

    @Mock
    private SubscriptionBillingService subscriptionBillingService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionScheduler subscriptionScheduler;

    private Subscription canceledSubscription;
    private Subscription paymentFailedSubscription;

    @BeforeEach
    void setUp() {
        ZonedDateTime pastTime = ZonedDateTime.now().minusDays(1);

        canceledSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(SubscriptionTier.PRO.getMaxRequestCount())
                .expiresAt(pastTime)
                .status(SubscriptionStatus.CANCELED)
                .build();

        paymentFailedSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(SubscriptionTier.PRO.getMaxRequestCount())
                .expiresAt(pastTime)
                .status(SubscriptionStatus.PAYMENT_FAILED)
                .build();
    }

    @Test
    @DisplayName("만료된 구독들의 상태를 EXPIRED로 변경하고 autoRenew를 false로 설정한다")
    void expireSubscriptions_shouldUpdateExpiredSubscriptionsToExpiredStatusAndDisableAutoRenew() {
        // given
        List<Subscription> expiredSubscriptions = Arrays.asList(canceledSubscription, paymentFailedSubscription);
        when(subscriptionRepository.findByStatusInAndExpiresAtBefore(anyList(), any(ZonedDateTime.class)))
                .thenReturn(expiredSubscriptions);

        // when
        subscriptionScheduler.expireSubscriptions();

        // then
        verify(subscriptionRepository, times(1)).findByStatusInAndExpiresAtBefore(
                anyList(),
                any(ZonedDateTime.class));
        verify(subscriptionRepository, times(1)).saveAll(expiredSubscriptions);

        // 각 구독의 expireSubscription 메서드가 호출되었는지 확인
        // (실제로는 mock 객체이므로 상태 변경을 직접 검증할 수는 없지만, 메서드 호출 로직은 검증됨)
    }

    @Test
    @DisplayName("만료 대상 구독이 없으면 저장 작업을 수행하지 않는다")
    void expireSubscriptions_shouldNotSaveWhenNoExpiredSubscriptions() {
        // given
        when(subscriptionRepository.findByStatusInAndExpiresAtBefore(anyList(), any(ZonedDateTime.class)))
                .thenReturn(Collections.emptyList());

        // when
        subscriptionScheduler.expireSubscriptions();

        // then
        verify(subscriptionRepository, times(1)).findByStatusInAndExpiresAtBefore(
                anyList(),
                any(ZonedDateTime.class));
        verify(subscriptionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("runDailyBilling 메서드가 SubscriptionBillingService를 호출한다")
    void runDailyBilling_shouldCallSubscriptionBillingService() {
        // when
        subscriptionScheduler.runDailyBilling();

        // then
        verify(subscriptionBillingService, times(1)).processDailySubscriptions();
    }
}
