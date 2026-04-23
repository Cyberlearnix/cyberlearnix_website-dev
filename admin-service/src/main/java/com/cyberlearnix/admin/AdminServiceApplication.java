package com.cyberlearnix.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com.cyberlearnix.admin")
@EntityScan("com.cyberlearnix.admin.entity")
@EnableJpaRepositories("com.cyberlearnix.admin.repository")
@EnableJpaAuditing
@EnableFeignClients(basePackages = "com.cyberlearnix.admin.client")
public class AdminServiceApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
