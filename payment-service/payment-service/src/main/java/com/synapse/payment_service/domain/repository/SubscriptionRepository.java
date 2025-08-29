package com.synapse.payment_service.domain.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.payment_service.domain.entity.Subscription;
import com.synapse.payment_service.domain.enums.SubscriptionStatus;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByMemberId(UUID memberId);

    Optional<Subscription> findByBillingKey(String billingKey);

    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.autoRenew = true AND s.billingKey IS NOT NULL AND s.expiresAt >= :startOfDay AND s.expiresAt < :endOfDay")
    List<Subscription> findActiveSubscriptionsDueForRenewal(@Param("startOfDay") ZonedDateTime startOfDay, @Param("endOfDay") ZonedDateTime endOfDay);

    /**
     * 만료 처리 대상 구독을 조회합니다.
     * CANCELED 또는 PAYMENT_FAILED 상태이고, 만료일이 지난 구독을 반환합니다.
     * 
     * @param statuses    조회할 구독 상태 목록 (CANCELED, PAYMENT_FAILED)
     * @param currentTime 현재 시간
     * @return 만료 처리 대상 구독 목록
     */
    List<Subscription> findByStatusInAndExpiresAtBefore(List<SubscriptionStatus> statuses, ZonedDateTime currentTime);
}
