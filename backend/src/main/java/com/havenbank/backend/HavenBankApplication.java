package com.havenbank.backend;

import com.havenbank.backend.iam.bootstrap.AdminBootstrapProperties;
import com.havenbank.backend.notification.service.EmailProperties;
import com.havenbank.backend.shared.ratelimit.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({EmailProperties.class, RateLimitProperties.class,
        AdminBootstrapProperties.class})
public class HavenBankApplication {

    public static void main(String[] args) {
        SpringApplication.run(HavenBankApplication.class, args);
    }

}