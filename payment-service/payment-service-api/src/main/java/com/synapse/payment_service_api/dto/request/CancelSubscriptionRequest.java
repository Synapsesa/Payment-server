package com.synapse.payment_service_api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CancelSubscriptionRequest(
    @NotBlank(message = "취소 사유는 필수입니다.") 
    String reason
) {

}
