package com.synapse.payment_service_api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentRequestDto(
    @NotBlank(message = "구독 티어는 필수입니다") 
    String tier
) {

}
