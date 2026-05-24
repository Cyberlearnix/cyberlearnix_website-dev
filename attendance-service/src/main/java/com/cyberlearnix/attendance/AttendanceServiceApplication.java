package com.cyberlearnix.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = "com.cyberlearnix.attendance")
@EntityScan("com.cyberlearnix.attendance.entity")
@EnableJpaRepositories("com.cyberlearnix.attendance.repository")
@EnableJpaAuditing
@EnableScheduling
public class AttendanceServiceApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AttendanceServiceApplication.class, args);
    }
}
