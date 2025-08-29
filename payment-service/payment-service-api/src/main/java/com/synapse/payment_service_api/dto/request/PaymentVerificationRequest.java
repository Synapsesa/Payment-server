package com.synapse.payment_service_api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentVerificationRequest(
    @NotBlank(message = "paymentId는 필수입니다") 
    String paymentId,

    @NotBlank(message = "iamPortTransactionId는 필수입니다") 
    String iamPortTransactionId
) {

}
