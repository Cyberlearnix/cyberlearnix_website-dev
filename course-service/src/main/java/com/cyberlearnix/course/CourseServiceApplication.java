package com.cyberlearnix.course;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = {"com.cyberlearnix.course", "com.cyberlearnix.shared.service"})
@EntityScan("com.cyberlearnix.shared.entity.course")
@EnableJpaRepositories("com.cyberlearnix.shared.repository.course")
@EnableFeignClients(basePackages = "com.cyberlearnix.course.client")
public class CourseServiceApplication {
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CourseServiceApplication.class, args);
    }
}

