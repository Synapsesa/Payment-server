package com.synapse.payment_service.controller;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.payment_service.service.PaymentService;

import io.portone.sdk.server.errors.WebhookVerificationException;
import io.portone.sdk.server.webhook.WebhookVerifier;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebHookController {
    private final WebhookVerifier webhookVerifier;
    private final PaymentService paymentService;

    @PostMapping("/portone")
    public ResponseEntity<String> handlePortOneWebhook(
        @RequestBody String requestBody,
        @RequestHeader(WebhookVerifier.HEADER_ID) String webhookId,
        @RequestHeader(WebhookVerifier.HEADER_SIGNATURE) String webhookSignature,
        @RequestHeader(WebhookVerifier.HEADER_TIMESTAMP) String webhookTimestamp
    ) throws WebhookVerificationException, IOException {
        webhookVerifier.verify(requestBody, webhookId, webhookSignature, webhookTimestamp);
        paymentService.verifyAndProcessWebhook(requestBody);
        return ResponseEntity.ok().build();
    }
}
