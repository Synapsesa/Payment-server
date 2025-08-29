package com.synapse.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;


import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.payment_service.TestConfig;
import com.synapse.payment_service.domain.entity.Order;
import com.synapse.payment_service.domain.entity.Subscription;
import com.synapse.payment_service.domain.enums.PaymentStatus;
import com.synapse.payment_service.domain.enums.SubscriptionStatus;
import com.synapse.payment_service.domain.enums.SubscriptionTier;
import com.synapse.payment_service.domain.repository.OrderRepository;
import com.synapse.payment_service.domain.repository.SubscriptionRepository;
import com.synapse.payment_service.service.convert.DelegatingPaymentStatusConverter;
import com.synapse.payment_service.service.convert.PaymentStatusConverter;
import com.synapse.payment_service_api.dto.request.PaymentRequestDto;
import com.synapse.payment_service_api.dto.request.PaymentVerificationRequest;
import com.synapse.payment_service_api.dto.response.PaymentPreparationResponse;

import io.portone.sdk.server.PortOneClient;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.Payment;
import io.portone.sdk.server.payment.PaymentAmount;
import io.portone.sdk.server.payment.PaymentClient;

public class PaymentServiceTest extends TestConfig {
    @InjectMocks
    private PaymentService paymentService;

    @Mock
    private PortOneClient portOneClient;
    @Mock
    private PaymentClient paymentClient;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentStatusConverter paymentStatusConverter;
    @Mock
    private ObjectMapper objectMapper;

    private UUID memberId;
    private String paymentId;
    private String iamPortTransactionId;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        paymentId = "order_" + UUID.randomUUID();
        iamPortTransactionId = "imp_" + UUID.randomUUID();
    }

    @Test
    @DisplayName("결제 사전 준비 성공: PENDING 상태의 주문이 생성되고, 프론트에 필요한 정보가 반환된다")
    void preparePayment_success() {
        // given
        PaymentRequestDto request = new PaymentRequestDto("PRO");
        String orderName = "pro_subscription";
        Subscription mockSubscription = Subscription.builder().id(UUID.randomUUID()).memberId(memberId).tier(SubscriptionTier.FREE).remainingChatCredits(10).expiresAt(ZonedDateTime.now().plusDays(30)).status(SubscriptionStatus.ACTIVE).build();

        given(subscriptionRepository.findByMemberId(memberId)).willReturn(Optional.of(mockSubscription));
        given(orderRepository.save(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        // when
        PaymentPreparationResponse response = paymentService.preparePayment(memberId, request);

        // then
        assertEquals(response.orderName(), orderName);
        assertEquals(response.amount(), new BigDecimal("100000"));
        verify(orderRepository).save(argThat(order -> order.getStatus() == PaymentStatus.PENDING));
        assertThat(mockSubscription.getTier()).isEqualTo(SubscriptionTier.FREE);
    }

    @Test
    @DisplayName("결제 검증 성공: 위변조가 없는 결제 건에 대해 구독 상태를 성공적으로 업데이트한다")
    void verifyAndProcess_success() {
        Subscription mockSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(memberId)
                .tier(SubscriptionTier.FREE)
                .remainingChatCredits(10)
                .expiresAt(ZonedDateTime.now().plusDays(30))
                .status(SubscriptionStatus.ACTIVE)
                .build();

        // given
        Order pendingOrder = Order.builder()
                .paymentId(paymentId)
                .amount(new BigDecimal("100000"))
                .status(PaymentStatus.PENDING)
                .subscription(mockSubscription)
                .build();

        // PortOne API의 응답을 모의 처리
        Payment.Recognized mockApiResponse = mock(Payment.Recognized.class);
        PaymentAmount mockAmount = mock(PaymentAmount.class);

        when(mockAmount.getTotal()).thenReturn(100000L);
        when(mockApiResponse.getAmount()).thenReturn(mockAmount);

        given(orderRepository.findByPaymentId(paymentId)).willReturn(Optional.of(pendingOrder));
        given(portOneClient.getPayment()).willReturn(paymentClient);

        // CompletableFuture Mock 설정
        CompletableFuture<Payment> mockFuture = CompletableFuture.completedFuture(mockApiResponse);
        given(paymentClient.getPayment(iamPortTransactionId)).willReturn(mockFuture);

        doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.updateStatus(PaymentStatus.PAID);
            order.getSubscription().renewSubscription(SubscriptionTier.PRO);
            return null;
        }).when(paymentStatusConverter).processPayment(any(Order.class), any(Payment.class));

        // when
        paymentService.verifyAndProcess(new PaymentVerificationRequest(paymentId, iamPortTransactionId), memberId);

        // then
        assertThat(pendingOrder.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pendingOrder.getIamPortTransactionId()).isEqualTo(iamPortTransactionId);
        assertThat(pendingOrder.getSubscription().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(pendingOrder.getSubscription().getTier()).isEqualTo(SubscriptionTier.PRO);
    }

    @Test
    @DisplayName("실제 DelegatingPaymentStatusConverter를 사용한 결제 검증 테스트")
    void verifyAndProcess_withRealDelegatingConverter() {
        // given
        Subscription mockSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(memberId)
                .tier(SubscriptionTier.FREE)
                .remainingChatCredits(10)
                .expiresAt(ZonedDateTime.now().plusDays(30))
                .status(SubscriptionStatus.ACTIVE)
                .build();

        Order pendingOrder = Order.builder()
                .paymentId(paymentId)
                .amount(new BigDecimal("100000"))
                .status(PaymentStatus.PENDING)
                .subscription(mockSubscription)
                .build();

        // 실제 DelegatingPaymentStatusConverter 사용 (내부에 PaidPaymentConverter 포함)
        PaymentStatusConverter realDelegatingConverter = new DelegatingPaymentStatusConverter();
        PaymentService paymentServiceWithRealConverter = new PaymentService(
                subscriptionRepository, orderRepository, portOneClient,
                realDelegatingConverter, objectMapper);

        // PaidPayment 타입으로 모킹 (실제 결제 완료 상태)
        PaidPayment mockPaidPayment = mock(PaidPayment.class);
        PaymentAmount mockAmount = mock(PaymentAmount.class);

        when(mockAmount.getTotal()).thenReturn(100000L);
        when(mockPaidPayment.getAmount()).thenReturn(mockAmount);

        given(orderRepository.findByPaymentId(paymentId)).willReturn(Optional.of(pendingOrder));
        given(portOneClient.getPayment()).willReturn(paymentClient);

        // CompletableFuture Mock 설정 여기서는 .join()으로 테스트 불가능 -> .join 메서드는 런타임시에 동기화를
        // 진행하는데 테스트중에 null이 들어가버린다.
        CompletableFuture<Payment> mockFuture = CompletableFuture.completedFuture(mockPaidPayment);
        given(paymentClient.getPayment(iamPortTransactionId)).willReturn(mockFuture);

        // when
        paymentServiceWithRealConverter.verifyAndProcess(
                new PaymentVerificationRequest(paymentId, iamPortTransactionId), memberId);

        // then - 실제 PaidPaymentConverter 로직에 의한 상태 변경 검증
        assertThat(pendingOrder.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(pendingOrder.getIamPortTransactionId()).isEqualTo(iamPortTransactionId);
        assertThat(pendingOrder.getSubscription().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(pendingOrder.getSubscription().getTier()).isEqualTo(SubscriptionTier.PRO);
    }
}
