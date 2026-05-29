package com.cyberlearnix.form;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.cyberlearnix.form")
@EnableFeignClients(basePackages = "com.cyberlearnix.form.client")
@EntityScan({"com.cyberlearnix.shared.entity.form", "com.cyberlearnix.shared.entity.enrollment"})
@EnableJpaRepositories({"com.cyberlearnix.shared.repository.form", "com.cyberlearnix.shared.repository.enrollment"})
public class FormServiceApplication {

    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        SpringApplication.run(FormServiceApplication.class, args);
    }
}
