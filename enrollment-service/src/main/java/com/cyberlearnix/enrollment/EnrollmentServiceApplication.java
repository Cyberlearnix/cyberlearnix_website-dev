package com.cyberlearnix.enrollment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com.cyberlearnix.enrollment")
@EntityScan({"com.cyberlearnix.shared.entity.enrollment", "com.cyberlearnix.shared.entity.form", "com.cyberlearnix.shared.entity.course"})
@EnableJpaRepositories({"com.cyberlearnix.shared.repository.enrollment", "com.cyberlearnix.shared.repository.form", "com.cyberlearnix.shared.repository.course"})
@EnableFeignClients
public class EnrollmentServiceApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(EnrollmentServiceApplication.class, args);
    }
}

