package com.cyberlearnix.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = {"com.cyberlearnix.user", "com.cyberlearnix.shared.service"})
@EntityScan({"com.cyberlearnix.shared.entity.user", "com.cyberlearnix.shared.entity.identity"})
@EnableJpaRepositories({"com.cyberlearnix.shared.repository.user", "com.cyberlearnix.shared.repository.identity"})
@EnableScheduling
@EnableAsync
@EnableJpaAuditing
public class UserServiceApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
