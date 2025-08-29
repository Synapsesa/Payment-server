package com.synapse.payment_service.service.scheduler;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.synapse.payment_service.domain.entity.Subscription;
import com.synapse.payment_service.domain.enums.SubscriptionStatus;
import com.synapse.payment_service.domain.repository.SubscriptionRepository;
import com.synapse.payment_service.service.SubscriptionBillingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {
    private final SubscriptionBillingService subscriptionBillingService;
    private final SubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "0 0 4 * * *")
    public void runDailyBilling() {
        log.info("일일 정기 결제 스케줄러를 시작합니다.");
        subscriptionBillingService.processDailySubscriptions();
        log.info("일일 정기 결제 스케줄러를 종료합니다.");
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void expireSubscriptions() {
        log.info("구독 만료 처리 스케줄러를 시작합니다.");

        ZonedDateTime currentTime = ZonedDateTime.now();

        // CANCELED 또는 PAYMENT_FAILED 상태이며 만료일이 지난 구독들을 조회
        List<Subscription> expiredSubscriptions = subscriptionRepository.findByStatusInAndExpiresAtBefore(
                List.of(SubscriptionStatus.CANCELED, SubscriptionStatus.PAYMENT_FAILED),
                currentTime);

        if (expiredSubscriptions.isEmpty()) {
            log.info("만료 처리할 구독이 없습니다.");
            return;
        }

        log.info("만료 처리 대상 구독 수: {}", expiredSubscriptions.size());

        // 각 구독의 상태를 EXPIRED로 변경하고 autoRenew를 false로 설정
        for (Subscription subscription : expiredSubscriptions) {
            subscription.expireSubscription();
            log.debug("구독 ID {} 상태를 EXPIRED로 변경하고 자동 갱신을 비활성화했습니다. (회원 ID: {})",
                    subscription.getId(), subscription.getMemberId());
        }

        // 변경사항을 데이터베이스에 저장
        subscriptionRepository.saveAll(expiredSubscriptions);

        log.info("구독 만료 처리 스케줄러를 종료합니다. 처리된 구독 수: {}", expiredSubscriptions.size());
    }
}
