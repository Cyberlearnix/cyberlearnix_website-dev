package com.cyberlearnix.instructor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com.cyberlearnix.instructor")
@EntityScan("com.cyberlearnix.instructor.entity")
@EnableJpaRepositories("com.cyberlearnix.instructor.repository")
@EnableJpaAuditing
@EnableFeignClients(basePackages = "com.cyberlearnix.instructor.client")
public class InstructorServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(InstructorServiceApplication.class, args);
    }
}
