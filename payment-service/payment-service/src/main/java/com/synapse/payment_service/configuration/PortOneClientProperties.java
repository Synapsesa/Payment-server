package com.synapse.payment_service.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "iamport")
public record PortOneClientProperties(
    String apiSecret,
    String baseUrl,
    String midKey,
    String webhookSecret,
    String channelKey
) {

}
