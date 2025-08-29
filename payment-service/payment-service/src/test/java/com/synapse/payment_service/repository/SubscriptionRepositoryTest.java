package com.synapse.payment_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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

@DisplayName("SubscriptionRepository 테스트")
class SubscriptionRepositoryTest extends TestConfig {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private LocalDate targetDate;
    private LocalDate pastDate;
    private LocalDate futureDate;

    @BeforeEach
    void setUp() {
        targetDate = LocalDate.of(2024, 1, 15);
        pastDate = targetDate.minusDays(1); // 2024-01-14
        futureDate = targetDate.plusDays(1); // 2024-01-16
    }

    @Test
    @DisplayName("정확히 해당 날짜에 만료되는 활성 구독만 조회한다")
    void findActiveSubscriptionsDueForRenewal_shouldReturnOnlyExactDateMatches() {
        // given
        // 정확히 targetDate에 만료되는 구독 (조회되어야 함)
        Subscription exactDateSubscription = createActiveSubscription(
                targetDate.atStartOfDay().atZone(ZonedDateTime.now().getZone()));

        // 과거 날짜에 만료된 구독 (조회되지 않아야 함)
        Subscription pastDateSubscription = createActiveSubscription(
                pastDate.atStartOfDay().atZone(ZonedDateTime.now().getZone()));

        // 미래 날짜에 만료되는 구독 (조회되지 않아야 함)
        Subscription futureDateSubscription = createActiveSubscription(
                futureDate.atStartOfDay().atZone(ZonedDateTime.now().getZone()));

        subscriptionRepository.saveAll(List.of(exactDateSubscription, pastDateSubscription, futureDateSubscription));

        // when
        ZonedDateTime startOfDay = targetDate.atStartOfDay(ZonedDateTime.now().getZone());
        ZonedDateTime endOfDay = targetDate.plusDays(1).atStartOfDay(ZonedDateTime.now().getZone());
        List<Subscription> result = subscriptionRepository.findActiveSubscriptionsDueForRenewal(startOfDay, endOfDay);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(exactDateSubscription.getId());
    }

    @Test
    @DisplayName("과거 날짜에 만료된 구독은 조회되지 않는다")
    void findActiveSubscriptionsDueForRenewal_shouldNotReturnPastDueSubscriptions() {
        // given
        // 과거 여러 날짜에 만료된 구독들
        Subscription pastSubscription1 = createActiveSubscription(
                pastDate.minusDays(5).atStartOfDay().atZone(ZonedDateTime.now().getZone()));
        Subscription pastSubscription2 = createActiveSubscription(
                pastDate.minusDays(10).atStartOfDay().atZone(ZonedDateTime.now().getZone()));
        Subscription pastSubscription3 = createActiveSubscription(
                pastDate.atStartOfDay().atZone(ZonedDateTime.now().getZone()));

        subscriptionRepository.saveAll(List.of(pastSubscription1, pastSubscription2, pastSubscription3));

        // when
        ZonedDateTime startOfDay = targetDate.atStartOfDay(ZonedDateTime.now().getZone());
        ZonedDateTime endOfDay = targetDate.plusDays(1).atStartOfDay(ZonedDateTime.now().getZone());
        List<Subscription> result = subscriptionRepository.findActiveSubscriptionsDueForRenewal(startOfDay, endOfDay);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("미래 날짜에 만료되는 구독은 조회되지 않는다")
    void findActiveSubscriptionsDueForRenewal_shouldNotReturnFutureSubscriptions() {
        // given
        // 미래 여러 날짜에 만료되는 구독들
        Subscription futureSubscription1 = createActiveSubscription(
                futureDate.plusDays(1).atStartOfDay().atZone(ZonedDateTime.now().getZone()));
        Subscription futureSubscription2 = createActiveSubscription(
                futureDate.plusDays(10).atStartOfDay().atZone(ZonedDateTime.now().getZone()));
        Subscription futureSubscription3 = createActiveSubscription(
                futureDate.atStartOfDay().atZone(ZonedDateTime.now().getZone()));

        subscriptionRepository.saveAll(List.of(futureSubscription1, futureSubscription2, futureSubscription3));

        // when
        ZonedDateTime startOfDay = targetDate.atStartOfDay(ZonedDateTime.now().getZone());
        ZonedDateTime endOfDay = targetDate.plusDays(1).atStartOfDay(ZonedDateTime.now().getZone());
        List<Subscription> result = subscriptionRepository.findActiveSubscriptionsDueForRenewal(startOfDay, endOfDay);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ACTIVE 상태가 아닌 구독은 조회되지 않는다")
    void findActiveSubscriptionsDueForRenewal_shouldNotReturnInactiveSubscriptions() {
        // given
        Subscription canceledSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(SubscriptionTier.PRO.getMaxRequestCount())
                .expiresAt(targetDate.atStartOfDay().atZone(ZonedDateTime.now().getZone()))
                .status(SubscriptionStatus.CANCELED)
                .build();
        canceledSubscription.updateBillingKey("test-billing-key");

        Subscription paymentFailedSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(SubscriptionTier.PRO.getMaxRequestCount())
                .expiresAt(targetDate.atStartOfDay().atZone(ZonedDateTime.now().getZone()))
                .status(SubscriptionStatus.PAYMENT_FAILED)
                .build();
        paymentFailedSubscription.updateBillingKey("test-billing-key-2");

        subscriptionRepository.saveAll(List.of(canceledSubscription, paymentFailedSubscription));

        // when
        ZonedDateTime startOfDay = targetDate.atStartOfDay(ZonedDateTime.now().getZone());
        ZonedDateTime endOfDay = targetDate.plusDays(1).atStartOfDay(ZonedDateTime.now().getZone());
        List<Subscription> result = subscriptionRepository.findActiveSubscriptionsDueForRenewal(startOfDay, endOfDay);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("autoRenew가 false인 구독은 조회되지 않는다")
    void findActiveSubscriptionsDueForRenewal_shouldNotReturnNonAutoRenewSubscriptions() {
        // given
        // autoRenew가 false인 구독 생성 (CANCELED 상태)
        Subscription subscription = createActiveSubscription(
                targetDate.atStartOfDay().atZone(ZonedDateTime.now().getZone()));
        subscription.deactivate(); // autoRenew를 false로 설정하고 상태를 CANCELED로 변경

        subscriptionRepository.save(subscription);

        // when
        ZonedDateTime startOfDay = targetDate.atStartOfDay(ZonedDateTime.now().getZone());
        ZonedDateTime endOfDay = targetDate.plusDays(1).atStartOfDay(ZonedDateTime.now().getZone());
        List<Subscription> result = subscriptionRepository.findActiveSubscriptionsDueForRenewal(startOfDay, endOfDay);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("billingKey가 null인 구독은 조회되지 않는다")
    void findActiveSubscriptionsDueForRenewal_shouldNotReturnSubscriptionsWithoutBillingKey() {
        // given
        Subscription subscriptionWithoutBillingKey = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(SubscriptionTier.PRO.getMaxRequestCount())
                .expiresAt(targetDate.atStartOfDay().atZone(ZonedDateTime.now().getZone()))
                .status(SubscriptionStatus.ACTIVE)
                .build();
        // billingKey를 설정하지 않음 (null 상태)

        subscriptionRepository.save(subscriptionWithoutBillingKey);

        // when
        ZonedDateTime startOfDay = targetDate.atStartOfDay(ZonedDateTime.now().getZone());
        ZonedDateTime endOfDay = targetDate.plusDays(1).atStartOfDay(ZonedDateTime.now().getZone());
        List<Subscription> result = subscriptionRepository.findActiveSubscriptionsDueForRenewal(startOfDay, endOfDay);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("모든 조건을 만족하는 여러 구독이 모두 조회된다")
    void findActiveSubscriptionsDueForRenewal_shouldReturnAllValidSubscriptions() {
        // given
        Subscription subscription1 = createActiveSubscription(
                targetDate.atStartOfDay().atZone(ZonedDateTime.now().getZone()));
        Subscription subscription2 = createActiveSubscription(
                targetDate.atTime(12, 30).atZone(ZonedDateTime.now().getZone()));
        Subscription subscription3 = createActiveSubscription(
                targetDate.atTime(23, 59).atZone(ZonedDateTime.now().getZone()));

        subscriptionRepository.saveAll(List.of(subscription1, subscription2, subscription3));

        // when
        ZonedDateTime startOfDay = targetDate.atStartOfDay(ZonedDateTime.now().getZone());
        ZonedDateTime endOfDay = targetDate.plusDays(1).atStartOfDay(ZonedDateTime.now().getZone());
        List<Subscription> result = subscriptionRepository.findActiveSubscriptionsDueForRenewal(startOfDay, endOfDay);

        // then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(Subscription::getId)
                .containsExactlyInAnyOrder(subscription1.getId(), subscription2.getId(), subscription3.getId());
    }

    @Test
    @DisplayName("CANCELED 상태이고 만료일이 지난 구독을 조회한다")
    void findByStatusInAndExpiresAtBefore_shouldReturnCanceledExpiredSubscriptions() {
        // given
        ZonedDateTime currentTime = ZonedDateTime.now();
        ZonedDateTime pastTime = currentTime.minusDays(1);
        ZonedDateTime futureTime = currentTime.plusDays(1);

        // CANCELED 상태이고 만료일이 지난 구독 (조회되어야 함)
        Subscription canceledExpiredSubscription = createSubscriptionWithStatus(SubscriptionStatus.CANCELED, pastTime);

        // CANCELED 상태이지만 아직 만료되지 않은 구독 (조회되지 않아야 함)
        Subscription canceledNotExpiredSubscription = createSubscriptionWithStatus(SubscriptionStatus.CANCELED,
                futureTime);

        // ACTIVE 상태이고 만료일이 지난 구독 (조회되지 않아야 함)
        Subscription activeExpiredSubscription = createSubscriptionWithStatus(SubscriptionStatus.ACTIVE, pastTime);

        subscriptionRepository.saveAll(
                List.of(canceledExpiredSubscription, canceledNotExpiredSubscription, activeExpiredSubscription));

        // when
        List<Subscription> result = subscriptionRepository.findByStatusInAndExpiresAtBefore(
                List.of(SubscriptionStatus.CANCELED, SubscriptionStatus.PAYMENT_FAILED),
                currentTime);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(canceledExpiredSubscription.getId());
        assertThat(result.get(0).getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    @DisplayName("PAYMENT_FAILED 상태이고 만료일이 지난 구독을 조회한다")
    void findByStatusInAndExpiresAtBefore_shouldReturnPaymentFailedExpiredSubscriptions() {
        // given
        ZonedDateTime currentTime = ZonedDateTime.now();
        ZonedDateTime pastTime = currentTime.minusDays(1);
        ZonedDateTime futureTime = currentTime.plusDays(1);

        // PAYMENT_FAILED 상태이고 만료일이 지난 구독 (조회되어야 함)
        Subscription paymentFailedExpiredSubscription = createSubscriptionWithStatus(SubscriptionStatus.PAYMENT_FAILED,
                pastTime);

        // PAYMENT_FAILED 상태이지만 아직 만료되지 않은 구독 (조회되지 않아야 함)
        Subscription paymentFailedNotExpiredSubscription = createSubscriptionWithStatus(
                SubscriptionStatus.PAYMENT_FAILED, futureTime);

        subscriptionRepository.saveAll(List.of(paymentFailedExpiredSubscription, paymentFailedNotExpiredSubscription));

        // when
        List<Subscription> result = subscriptionRepository.findByStatusInAndExpiresAtBefore(
                List.of(SubscriptionStatus.CANCELED, SubscriptionStatus.PAYMENT_FAILED),
                currentTime);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(paymentFailedExpiredSubscription.getId());
        assertThat(result.get(0).getStatus()).isEqualTo(SubscriptionStatus.PAYMENT_FAILED);
    }

    @Test
    @DisplayName("CANCELED와 PAYMENT_FAILED 상태 모두에서 만료일이 지난 구독들을 조회한다")
    void findByStatusInAndExpiresAtBefore_shouldReturnBothCanceledAndPaymentFailedExpiredSubscriptions() {
        // given
        ZonedDateTime currentTime = ZonedDateTime.now();
        ZonedDateTime pastTime1 = currentTime.minusDays(1);
        ZonedDateTime pastTime2 = currentTime.minusDays(2);
        ZonedDateTime pastTime3 = currentTime.minusDays(3);

        // 다양한 상태와 만료일을 가진 구독들
        Subscription canceledExpired1 = createSubscriptionWithStatus(SubscriptionStatus.CANCELED, pastTime1);
        Subscription canceledExpired2 = createSubscriptionWithStatus(SubscriptionStatus.CANCELED, pastTime2);
        Subscription paymentFailedExpired1 = createSubscriptionWithStatus(SubscriptionStatus.PAYMENT_FAILED, pastTime1);
        Subscription paymentFailedExpired2 = createSubscriptionWithStatus(SubscriptionStatus.PAYMENT_FAILED, pastTime3);

        subscriptionRepository
                .saveAll(List.of(canceledExpired1, canceledExpired2, paymentFailedExpired1, paymentFailedExpired2));

        // when
        List<Subscription> result = subscriptionRepository.findByStatusInAndExpiresAtBefore(
                List.of(SubscriptionStatus.CANCELED, SubscriptionStatus.PAYMENT_FAILED),
                currentTime);

        // then
        assertThat(result).hasSize(4);
        assertThat(result).extracting(Subscription::getId)
                .containsExactlyInAnyOrder(
                        canceledExpired1.getId(),
                        canceledExpired2.getId(),
                        paymentFailedExpired1.getId(),
                        paymentFailedExpired2.getId());
    }

    @Test
    @DisplayName("다른 상태의 구독은 조회되지 않는다")
    void findByStatusInAndExpiresAtBefore_shouldNotReturnOtherStatusSubscriptions() {
        // given
        ZonedDateTime currentTime = ZonedDateTime.now();
        ZonedDateTime pastTime = currentTime.minusDays(1);

        // 다른 상태의 만료된 구독들 (조회되지 않아야 함)
        Subscription activeExpiredSubscription = createSubscriptionWithStatus(SubscriptionStatus.ACTIVE, pastTime);
        Subscription expiredExpiredSubscription = createSubscriptionWithStatus(SubscriptionStatus.EXPIRED, pastTime);

        subscriptionRepository.saveAll(List.of(activeExpiredSubscription, expiredExpiredSubscription));

        // when
        List<Subscription> result = subscriptionRepository.findByStatusInAndExpiresAtBefore(
                List.of(SubscriptionStatus.CANCELED, SubscriptionStatus.PAYMENT_FAILED),
                currentTime);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("만료일이 현재 시간과 같거나 이후인 구독은 조회되지 않는다")
    void findByStatusInAndExpiresAtBefore_shouldNotReturnCurrentOrFutureSubscriptions() {
        // given
        ZonedDateTime currentTime = ZonedDateTime.now();
        ZonedDateTime currentTimeExact = currentTime.plusSeconds(1); // 시간 정밀도 문제 해결을 위해 1초 후 시간 사용
        ZonedDateTime futureTime = currentTime.plusDays(1);

        // 현재 시간과 같은 만료일을 가진 구독
        Subscription canceledCurrentSubscription = createSubscriptionWithStatus(SubscriptionStatus.CANCELED,
                currentTimeExact);

        // 미래 만료일을 가진 구독
        Subscription canceledFutureSubscription = createSubscriptionWithStatus(SubscriptionStatus.CANCELED, futureTime);

        subscriptionRepository.saveAll(List.of(canceledCurrentSubscription, canceledFutureSubscription));

        // when
        List<Subscription> result = subscriptionRepository.findByStatusInAndExpiresAtBefore(
                List.of(SubscriptionStatus.CANCELED, SubscriptionStatus.PAYMENT_FAILED),
                currentTime);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("빈 상태 목록으로 조회하면 빈 결과를 반환한다")
    void findByStatusInAndExpiresAtBefore_shouldReturnEmptyForEmptyStatusList() {
        // given
        ZonedDateTime currentTime = ZonedDateTime.now();
        ZonedDateTime pastTime = currentTime.minusDays(1);

        Subscription canceledExpiredSubscription = createSubscriptionWithStatus(SubscriptionStatus.CANCELED, pastTime);
        subscriptionRepository.save(canceledExpiredSubscription);

        // when
        List<Subscription> result = subscriptionRepository.findByStatusInAndExpiresAtBefore(
                List.of(),
                currentTime);

        // then
        assertThat(result).isEmpty();
    }

    private Subscription createActiveSubscription(ZonedDateTime expiresAt) {
        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(SubscriptionTier.PRO.getMaxRequestCount())
                .expiresAt(expiresAt)
                .status(SubscriptionStatus.ACTIVE)
                .build();
        subscription.updateBillingKey("test-billing-key-" + UUID.randomUUID());
        return subscription;
    }

    private Subscription createSubscriptionWithStatus(SubscriptionStatus status, ZonedDateTime expiresAt) {
        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(UUID.randomUUID())
                .tier(SubscriptionTier.PRO)
                .remainingChatCredits(SubscriptionTier.PRO.getMaxRequestCount())
                .expiresAt(expiresAt)
                .status(status)
                .build();
        subscription.updateBillingKey("test-billing-key-" + UUID.randomUUID());
        return subscription;
    }
}
