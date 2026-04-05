package com.cyberlearnix.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.cyberlearnix")
@EntityScan({
        "com.cyberlearnix.shared.entity.user",
        "com.cyberlearnix.shared.entity.course",
        "com.cyberlearnix.shared.entity.enrollment",
        "com.cyberlearnix.shared.entity.form",
        "com.cyberlearnix.shared.entity.cms",
        "com.cyberlearnix.shared.entity.shop"
})
@EnableJpaRepositories("com.cyberlearnix.shared.repository")
public class GatewayApplication {
    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
