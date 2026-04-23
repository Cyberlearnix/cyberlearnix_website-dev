package com.cyberlearnix.cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com.cyberlearnix.cms")
@EntityScan("com.cyberlearnix.shared.entity.cms")
@EnableJpaRepositories("com.cyberlearnix.shared.repository.cms")
public class CmsServiceApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CmsServiceApplication.class, args);
    }
}
