package com.cyberlearnix.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com.cyberlearnix.shop")
@EntityScan("com.cyberlearnix.shared.entity.shop")
@EnableJpaRepositories("com.cyberlearnix.shared.repository.shop")
public class ShopServiceApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ShopServiceApplication.class, args);
    }
}
