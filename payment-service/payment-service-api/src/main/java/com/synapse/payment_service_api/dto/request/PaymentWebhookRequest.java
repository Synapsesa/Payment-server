package com.synapse.payment_service_api.dto.request;

import java.io.IOException;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record PaymentWebhookRequest(
    @JsonProperty("type") String type,
    @JsonProperty("timestamp") String timestamp,
    @JsonProperty("data") JsonNode data
) {
    public static PaymentWebhookRequest from(String requestBody, ObjectMapper objectMapper) throws IOException {
        return objectMapper.readValue(requestBody, PaymentWebhookRequest.class);
    }

    public String getPaymentId() {
        if (data == null)
            return null;
        return extractText(data, "paymentId");
    }

    public String getTransactionId() {
        if (data == null)
            return null;
        return extractText(data, "transactionId");
    }

    public String getBillingKey() {
        if (data == null)
            return null;
        return extractText(data, "billingKey");
    }

    private String extractText(JsonNode node, String fieldName) {
        return Optional.ofNullable(node.get(fieldName))
                .map(JsonNode::asText)
                .orElse(null);
    }

    public boolean isTransactionWebhook() {
        return type != null && type.startsWith("Transaction.");
    }

    public boolean isBillingKeyWebhook() {
        return type != null && type.startsWith("BillingKey.");
    }
}
