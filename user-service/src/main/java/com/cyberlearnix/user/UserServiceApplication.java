package com.cyberlearnix.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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
        // Raw JDBC unlock query executed BEFORE Spring Boot bean initialization / Hibernate updates.
        // This resolves any database lock blocks (deadlocks) on startup.
        try {
            String dbHost = System.getenv("DB_HOST");
            if (dbHost == null) dbHost = "127.0.0.1";
            String dbPort = System.getenv("DB_PORT");
            if (dbPort == null) dbPort = "5999";
            String dbUser = System.getenv("DB_USER");
            if (dbUser == null) dbUser = "postgres";
            String dbPass = System.getenv("DB_PASS");
            if (dbPass == null) dbPass = "Cyb3rL3arnix#2026!DB";
            String dbName = System.getenv("DB_NAME");
            if (dbName == null) dbName = "cyberlearnix_user";

            String url = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName + "?prepareThreshold=0";
            System.out.println("[DB-Unlocker] Pre-startup check connecting to " + url);
            
            try (Connection conn = DriverManager.getConnection(url, dbUser, dbPass);
                 Statement stmt = conn.createStatement()) {
                
                System.out.println("[DB-Unlocker] Connection successful. Terminating locking connections to cms & user databases...");
                stmt.execute(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity " +
                    "WHERE (datname = 'cyberlearnix_cms' OR datname = 'cyberlearnix_user') " +
                    "AND pid != pg_backend_pid();"
                );
                System.out.println("[DB-Unlocker] Databases unlocked successfully.");
            }
        } catch (Exception e) {
            System.err.println("[DB-Unlocker] Non-fatal error during pre-startup database unlock: " + e.getMessage());
        }

        SpringApplication.run(UserServiceApplication.class, args);
    }
}
