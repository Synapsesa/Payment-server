package com.synapse.payment_service.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.portone.sdk.server.PortOneClient;
import io.portone.sdk.server.webhook.WebhookVerifier;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableConfigurationProperties(PortOneClientProperties.class)
@RequiredArgsConstructor
public class PortOneClientConfig {
    private final PortOneClientProperties properties;

    @Bean
    public PortOneClient portOneClient() {
        return new PortOneClient(properties.apiSecret(), properties.baseUrl(), properties.midKey());
    }

    @Bean
    public WebhookVerifier webhookVerifier() {
        return new WebhookVerifier(properties.webhookSecret());
    }
}
