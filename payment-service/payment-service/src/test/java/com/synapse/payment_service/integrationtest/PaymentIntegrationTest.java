package com.synapse.payment_service.integrationtest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.payment_service.TestConfig;
import com.synapse.payment_service.domain.entity.Order;
import com.synapse.payment_service.domain.entity.Subscription;
import com.synapse.payment_service.domain.enums.PaymentStatus;
import com.synapse.payment_service.domain.enums.SubscriptionStatus;
import com.synapse.payment_service.domain.enums.SubscriptionTier;
import com.synapse.payment_service.domain.repository.OrderRepository;
import com.synapse.payment_service.domain.repository.SubscriptionRepository;
import com.synapse.payment_service_api.dto.request.PaymentRequestDto;
import com.synapse.payment_service_api.dto.response.PaymentPreparationResponse;

import io.portone.sdk.server.PortOneClient;
import io.portone.sdk.server.payment.PaidPayment;
import io.portone.sdk.server.payment.Payment;
import io.portone.sdk.server.payment.PaymentAmount;
import io.portone.sdk.server.payment.PaymentClient;
import io.portone.sdk.server.webhook.WebhookVerifier;

public class PaymentIntegrationTest extends TestConfig {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
    @Autowired
    private OrderRepository orderRepository;

    @MockitoBean
    private PortOneClient portOneClient;
    @MockitoBean
    private WebhookVerifier webhookVerifier;
    @MockitoBean
    private PaymentClient paymentClient;

    private UUID memberId;

    @BeforeEach
    void setUp() {
        this.memberId = UUID.randomUUID();
        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .memberId(memberId)
                .tier(SubscriptionTier.FREE)
                .remainingChatCredits(10)
                .expiresAt(ZonedDateTime.now().plusDays(30))
                .status(SubscriptionStatus.CANCELED)
                .build();
        subscriptionRepository.save(subscription);
    }

    @Test
    @DisplayName("결제 준비 API 성공: /api/payments/request 호출 시, PENDING 상태의 주문이 생성된다")
    void requestPayment_success() throws Exception {
        // given
        PaymentRequestDto request = new PaymentRequestDto("PRO");

        // when
        ResultActions resultActions = mockMvc.perform(post("/api/payments/request")
                .header("X-Authenticated-Member-Id", memberId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // then
        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").exists())
                .andExpect(jsonPath("$.amount").exists());

        // DB 검증
        assertThat(orderRepository.findAll()).hasSize(1);
        assertThat(orderRepository.findAll().get(0).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("웹훅 처리 성공: /api/webhooks/portone 호출 시, 결제 상태가 성공적으로 업데이트된다")
    void handleWebhook_success() throws Exception {
        // given
        // 1. 먼저 /request API를 호출하여 PENDING 상태의 주문을 생성
        PaymentRequestDto request = new PaymentRequestDto("PRO");

        ResultActions prepareResult = mockMvc.perform(post("/api/payments/request")
                .header("X-Authenticated-Member-Id", memberId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));

        // 2. 첫 번째 API 결과에서 paymentId와 amount 추출
        String responseJson = prepareResult
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").exists())
                .andReturn()
                .getResponse().getContentAsString();

        PaymentPreparationResponse prepResponse = objectMapper.readValue(responseJson,
                PaymentPreparationResponse.class);
        String paymentId = prepResponse.paymentId();
        BigDecimal amount = prepResponse.amount();
        String iamPortTransactionId = "imp_test_12345";

        // 3. Mock PortOneClient가 위변조 없는 정상 데이터를 반환하도록 설정
        PaidPayment mockPaidPayment = mock(PaidPayment.class);
        PaymentAmount mockAmount = mock(PaymentAmount.class);

        when(mockAmount.getTotal()).thenReturn(amount.longValue());
        when(mockPaidPayment.getAmount()).thenReturn(mockAmount);

        CompletableFuture<Payment> mockFuture = CompletableFuture.completedFuture(mockPaidPayment);
        given(portOneClient.getPayment()).willReturn(paymentClient);
        given(paymentClient.getPayment(iamPortTransactionId)).willReturn(mockFuture);

        // 4. WebhookVerifier가 항상 검증에 성공하도록 설정

        // 5. 포트원 웹훅 페이로드 생성 (실제 포트원 SDK 사용)
        String webhookJson = """
                {
                    "type": "Transaction.Paid",
                    "data": {
                        "paymentId": "%s",
                        "transactionId": "%s"
                    }
                }
                """.formatted(paymentId, iamPortTransactionId);

        // when - 웹훅 API 호출
        ResultActions webhookResult = mockMvc.perform(post("/api/webhooks/portone")
                .header(WebhookVerifier.HEADER_ID, "wh_test_id")
                .header(WebhookVerifier.HEADER_SIGNATURE, "test_signature")
                .header(WebhookVerifier.HEADER_TIMESTAMP, String.valueOf(Instant.now().getEpochSecond()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookJson));

        // then
        webhookResult.andExpect(status().isOk());

        // DB 검증 - 트랜잭션이 커밋된 후 검증
        Order completedOrder = orderRepository.findByPaymentId(paymentId).get();
        assertThat(completedOrder.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(completedOrder.getSubscription().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        Subscription updatedSubscription = subscriptionRepository.findByMemberId(memberId).get();
        assertThat(updatedSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(updatedSubscription.getTier()).isEqualTo(SubscriptionTier.PRO);
    }
}
