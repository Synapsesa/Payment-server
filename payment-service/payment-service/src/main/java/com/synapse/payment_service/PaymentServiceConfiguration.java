package com.synapse.payment_service;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@ComponentScan
@EnableAutoConfiguration
@EnableJpaAuditing
public class PaymentServiceConfiguration {
    
}
