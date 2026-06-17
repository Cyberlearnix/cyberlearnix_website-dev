package com.cyberlearnix.user.repository;

import com.cyberlearnix.shared.entity.identity.Member;
import com.cyberlearnix.shared.repository.identity.MemberRepository;
import com.cyberlearnix.shared.repository.user.UserRepository;
import com.cyberlearnix.user.service.IdentityService;
import com.cyberlearnix.user.service.OtpService;
import com.cyberlearnix.user.service.EmailNotificationService;
import org.springframework.mail.javamail.JavaMailSender;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5999/cyberlearnix_users?options=-c%20timezone=UTC&timezone=UTC&prepareThreshold=0",
    "spring.datasource.username=postgres",
    "spring.datasource.password=Cyb3rL3arnix#2026!DB",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=update",
    "admin.seeder.enabled=false"
})
@Transactional
public class DiagnosticIT {

    @MockBean private OtpService otpService;
    @MockBean private EmailNotificationService emailNotificationService;
    @MockBean private JavaMailSender mailSender;

    @Autowired private IdentityService identityService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DataSource dataSource;

    @Test
    public void runDiagnostics() {
        System.out.println("=== DIAGNOSTICS START ===");
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            System.out.println("DB Product Name: " + metaData.getDatabaseProductName());
            System.out.println("DB Product Version: " + metaData.getDatabaseProductVersion());
            
            // Check tables
            try (ResultSet rs = metaData.getTables(null, null, "members", null)) {
                if (rs.next()) {
                    System.out.println("Table 'members' exists.");
                } else {
                    System.out.println("Table 'members' does NOT exist.");
                }
            }
            
            try (ResultSet rs = metaData.getTables(null, null, "identity_audit_logs", null)) {
                if (rs.next()) {
                    System.out.println("Table 'identity_audit_logs' exists.");
                } else {
                    System.out.println("Table 'identity_audit_logs' does NOT exist.");
                }
            }
            
            // Check columns in members table
            System.out.println("Columns in 'members':");
            try (ResultSet rs = metaData.getColumns(null, null, "members", null)) {
                while (rs.next()) {
                    System.out.println("  " + rs.getString("COLUMN_NAME") + " (" + rs.getString("TYPE_NAME") + ")");
                }
            }

            // Check columns in identity_audit_logs table
            System.out.println("Columns in 'identity_audit_logs':");
            try (ResultSet rs = metaData.getColumns(null, null, "identity_audit_logs", null)) {
                while (rs.next()) {
                    System.out.println("  " + rs.getString("COLUMN_NAME") + " (" + rs.getString("TYPE_NAME") + ")");
                }
            }

            // Check columns in users table
            System.out.println("Columns in 'users':");
            try (ResultSet rs = metaData.getColumns(null, null, "users", null)) {
                while (rs.next()) {
                    System.out.println("  " + rs.getString("COLUMN_NAME") + " (" + rs.getString("TYPE_NAME") + ")");
                }
            }
            
        } catch (Exception e) {
            System.out.println("Database connection failed: " + e.getMessage());
            e.printStackTrace();
        }

        // Try to load the admin user
        try {
            System.out.println("Loading admin user from repository...");
            java.util.Optional<com.cyberlearnix.shared.entity.user.User> adminUser = userRepository.findByEmail("shivakumar@cyberlearnix.com");
            if (adminUser.isPresent()) {
                System.out.println("Admin user loaded successfully: " + adminUser.get().getEmail() + " (isFirstLogin=" + adminUser.get().getIsFirstLogin() + ")");
            } else {
                System.out.println("Admin user NOT found in database.");
            }
        } catch (Exception e) {
            System.out.println("Loading admin user FAILED:");
            e.printStackTrace();
        }

        // Try to add member manually to see the exact stack trace of the error
        try {
            Member m = new Member();
            m.setFullName("Diagnostic Test Member");
            m.setEmail("diagtest@example.com");
            m.setPhone("+919999999999");
            m.setMemberType("Employee");
            m.setDepartment("Engineering");
            m.setDesignation("Software Engineer");
            m.setProfilePhoto("");
            
            System.out.println("Calling addMemberManually...");
            Member saved = identityService.addMemberManually(m, "admin@example.com");
            System.out.println("Member saved successfully: " + saved.getMemberId());
        } catch (Exception e) {
            System.out.println("addMemberManually FAILED:");
            e.printStackTrace();
        }
        System.out.println("=== DIAGNOSTICS END ===");
    }
}
