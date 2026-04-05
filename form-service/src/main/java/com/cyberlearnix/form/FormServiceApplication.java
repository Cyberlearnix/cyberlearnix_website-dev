package com.cyberlearnix.form;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@ComponentScan(basePackages = {
    "com.cyberlearnix.form",
    "com.cyberlearnix.shared.repository",
    "com.cyberlearnix.shared.entity.form",
    "com.cyberlearnix.shared.service"
})
@EntityScan({
        "com.cyberlearnix.shared.entity.user",
        "com.cyberlearnix.shared.entity.course",
        "com.cyberlearnix.shared.entity.enrollment",
        "com.cyberlearnix.shared.entity.form",
        "com.cyberlearnix.shared.entity.cms",
        "com.cyberlearnix.shared.entity.shop"
})
@EnableJpaRepositories(basePackages = {"com.cyberlearnix.shared.repository"})
public class
FormServiceApplication {
    @Value("${jwt.secret:MISSING}")
    private String jwtSecret;

    public static void main(String[] args) {
        System.setProperty("user.timezone", "UTC");
        SpringApplication.run(FormServiceApplication.class, args);
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        System.out.println("FormService started with TimeZone: " + java.util.TimeZone.getDefault().getID());
        System.out.println("FormService JWT Secret check (first 5 chars): " + 
            (jwtSecret != null && jwtSecret.length() > 5 ? jwtSecret.substring(0, 5) : "INVALID"));
    }
}
