package com.synapse.payment_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
public class InternalApiController {

    @GetMapping("/payments/id")
    public ResponseEntity<String> getPaymentInfo(Authentication authentication) {
        // 인증된 주체(클라이언트 ID)와 요청된 ID를 로깅합니다.
        System.out.println("Client '" + authentication.getName() + "' requested info for payment: ");
        return ResponseEntity.ok("Payment info for ");
    }
}
