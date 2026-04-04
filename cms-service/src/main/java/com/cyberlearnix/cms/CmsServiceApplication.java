package com.cyberlearnix.cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com.cyberlearnix")
@EntityScan("com.cyberlearnix.shared.entity")
@EnableJpaRepositories("com.cyberlearnix.shared.repository")
public class CmsServiceApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CmsServiceApplication.class, args);
    }
}
