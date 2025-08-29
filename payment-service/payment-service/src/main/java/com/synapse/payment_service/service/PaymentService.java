package com.synapse.payment_service.service;

import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.payment_service.domain.entity.Order;
import com.synapse.payment_service.domain.entity.Subscription;
import com.synapse.payment_service.domain.enums.SubscriptionTier;
import com.synapse.payment_service.domain.repository.OrderRepository;
import com.synapse.payment_service.domain.repository.SubscriptionRepository;
import com.synapse.payment_service.exception.ExceptionCode;
import com.synapse.payment_service.exception.NotFoundException;
import com.synapse.payment_service.exception.PaymentVerificationException;
import com.synapse.payment_service.exception.UnauthorizedException;
import com.synapse.payment_service.service.convert.PaymentStatusConverter;
import com.synapse.payment_service_api.dto.request.CancelSubscriptionRequest;
import com.synapse.payment_service_api.dto.request.PaymentRequestDto;
import com.synapse.payment_service_api.dto.request.PaymentVerificationRequest;
import com.synapse.payment_service_api.dto.request.PaymentWebhookRequest;
import com.synapse.payment_service_api.dto.response.PaymentPreparationResponse;

import io.portone.sdk.server.PortOneClient;
import io.portone.sdk.server.payment.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final SubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;
    private final PortOneClient portOneClient;
    private final PaymentStatusConverter paymentStatusConverter;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentPreparationResponse preparePayment(UUID memberId, PaymentRequestDto request) {
        SubscriptionTier tier = SubscriptionTier.valueOf(request.tier().toUpperCase());

        Subscription subscription = subscriptionRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NotFoundException(ExceptionCode.SUBSCRIPTION_NOT_FOUND)); // 현재 인증 서버와 연동이 안되어있기
                                                                                                 // 때문에 테스트로 검증

        // 도메인 객체의 팩토리 메서드 사용
        Order order = Order.createForSubscription(subscription, tier);
        orderRepository.save(order);

        return new PaymentPreparationResponse(order.getPaymentId(), order.getOrderName(), order.getAmount());
    }

    /**
     * 결제 후 검증 api 요청
     */
    @Transactional
    public void verifyAndProcess(PaymentVerificationRequest request, UUID memberId) {
        processPaymentVerification(request.paymentId(), request.iamPortTransactionId(), memberId);
    }

    // 웹 훅 용입니다.
    @Transactional
    public void verifyAndProcessWebhook(String requestBody) throws IOException {
        PaymentWebhookRequest webhookRequest = PaymentWebhookRequest.from(requestBody, objectMapper);
        if (webhookRequest.isTransactionWebhook()) {
            String paymentId = webhookRequest.getPaymentId();
            String transactionId = webhookRequest.getTransactionId();
            processPaymentVerification(paymentId, transactionId, null); // 웹훅은 memberId null로 전달
        }
    }

    // 결제 검증 (memberId가 null이면 웹훅용, 아니면 클라이언트용)
    private void processPaymentVerification(String paymentId, String iamPortTransactionId, UUID memberId) {
        Order order = orderRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new NotFoundException(ExceptionCode.ORDER_NOT_FOUND));

        // 클라이언트 요청인 경우에만 권한 검증 (웹훅은 memberId가 null)
        if (memberId != null && !order.getSubscription().getMemberId().equals(memberId)) {
            throw new UnauthorizedException(ExceptionCode.UNAUTHORIZED_USER);
        }

        // 도메인 객체를 통한 중복 처리 검증
        if (order.isAlreadyProcessed()) {
            log.info("이미 처리된 결제입니다. paymentId={}", order.getPaymentId());
            return;
        }

        Payment payment = portOneClient.getPayment().getPayment(iamPortTransactionId).join();

        if (payment == null) {
            throw new PaymentVerificationException(ExceptionCode.PAYMENT_VERIFICATION_FAILED);
        }

        // 아임포트 결제 ID 설정
        order.updateIamPortTransactionId(iamPortTransactionId);

        if (!(payment instanceof Payment.Recognized recognizedPayment)) {
            throw new PaymentVerificationException(ExceptionCode.PAYMENT_NOT_RECOGNIZED);
        }

        // 도메인 객체를 통한 결제 금액 검증
        try {
            order.validatePaymentAmount(recognizedPayment);
        } catch (PaymentVerificationException e) {
            log.error("결제 금액 불일치. 주문금액={}, 실제결제금액={}, paymentId={}",
                    order.getAmount(), recognizedPayment.getAmount().getTotal(), order.getPaymentId());
            throw e;
        }

        paymentStatusConverter.processPayment(order, payment);

        // 도메인 객체를 통한 빌링키 처리
        if (order.hasBillingKey(recognizedPayment)) {
            Subscription subscription = order.getSubscription();
            String billingKey = recognizedPayment.getBillingKey();
            subscription.updateBillingKey(billingKey);
            subscriptionRepository.save(subscription);
        }
    }

    @Transactional
    public void cancelSubscription(UUID memberId, CancelSubscriptionRequest request) {
        Subscription subscription = subscriptionRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NotFoundException(ExceptionCode.SUBSCRIPTION_NOT_FOUND));

        Order order = orderRepository.findBySubscription(subscription)
                .orElseThrow(() -> new NotFoundException(ExceptionCode.ORDER_NOT_FOUND));

        // String paymentId = order.getPaymentId();

        // portOneClient.getPayment().cancelPayment(paymentId, null, null, null,
        // request.reason(), null, null, null, null).join();

        // 도메인 객체의 비즈니스 메서드 사용
        order.cancel();
        subscription.deactivate();

        orderRepository.save(order);
        subscriptionRepository.save(subscription);
    }
}
