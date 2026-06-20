package com.cyberlearnix.user.repository;

import com.cyberlearnix.shared.entity.identity.Member;
import com.cyberlearnix.shared.repository.identity.MemberRepository;
import com.cyberlearnix.shared.repository.user.UserRepository;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.user.service.IdentityService;
import com.cyberlearnix.user.service.OtpService;
import com.cyberlearnix.user.service.EmailNotificationService;
import org.springframework.mail.javamail.JavaMailSender;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;

@Disabled("Local diagnostic tool — requires PostgreSQL on localhost:5432. Remove @Disabled to run locally.")
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
    "spring.datasource.url=jdbc:postgresql://127.0.0.1:5432/cyberlearnix_users?options=-c%20timezone=UTC&timezone=UTC&prepareThreshold=0",
    "spring.datasource.username=postgres",
    "spring.datasource.password=CyberLearnix@DB2026!SecurePass",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=update",
    "admin.seeder.enabled=false"
})
//@Transactional
public class DiagnosticIT {

    @MockBean private OtpService otpService;
    @MockBean private EmailNotificationService emailNotificationService;
    @MockBean private JavaMailSender mailSender;

    @Autowired private IdentityService identityService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private com.cyberlearnix.shared.repository.user.UserProfileRepository userProfileRepository;
    @Autowired private com.cyberlearnix.user.seeder.DomainCorrectionSeeder domainCorrectionSeeder;
    @Autowired private DataSource dataSource;

    @Test
    public void runDiagnostics() {
        System.out.println("=== DIAGNOSTICS START ===");
        
        // Setup a dummy Member with an incorrect domain
        Member badMember = new Member();
        badMember.setMemberId("CLX-EMP-9999");
        badMember.setFullName("Bad Member Domain Test");
        badMember.setEmail("badmember@example.com");
        badMember.setPhone("+919876543210");
        badMember.setMemberType("Employee");
        badMember.setVerificationUrl("https://verify.cyberlearnix.com/verify/CLX-EMP-9999");
        badMember.setIsActive(true);
        badMember.setStatus("Approved");
        badMember = memberRepository.save(badMember);
        System.out.println("Saved dummy bad member: " + badMember.getId());

        // Setup a UserProfile with an enrollment number
        UserProfile profile = new UserProfile();
        profile.setId("test-profile-id");
        profile.setFullName("Student Domain Test");
        profile.setEmail("studenttest@example.com");
        profile.setRole("student");
        profile.setEnrollmentNumber("CLX-20260620-9999");
        profile.setQrCodeData("data:image/png;base64,dummy"); // old/invalid QR code
        profile = userProfileRepository.save(profile);
        System.out.println("Saved dummy user profile: " + profile.getId());

        // Execute Domain Correction Seeder manually to correct them
        System.out.println("Manually running DomainCorrectionSeeder...");
        domainCorrectionSeeder.run();

        // Verify Member domain correction
        Member correctedMember = memberRepository.findById(badMember.getId()).get();
        System.out.println("Corrected Member URL: " + correctedMember.getVerificationUrl());
        org.junit.jupiter.api.Assertions.assertFalse(correctedMember.getVerificationUrl().contains("verify.cyberlearnix.com"));
        org.junit.jupiter.api.Assertions.assertTrue(correctedMember.getVerificationUrl().contains("www.cyberlearnix.com"));
        org.junit.jupiter.api.Assertions.assertTrue(correctedMember.getVerificationUrl().contains("verify.html?enrollment="));
        org.junit.jupiter.api.Assertions.assertTrue(correctedMember.getQrCodeUrl().startsWith("data:image/png;base64,"));

        // Verify UserProfile QR code correction
        UserProfile correctedProfile = userProfileRepository.findById(profile.getId()).get();
        System.out.println("Corrected Profile QR Code Data starts with: " + correctedProfile.getQrCodeData().substring(0, Math.min(50, correctedProfile.getQrCodeData().length())));
        org.junit.jupiter.api.Assertions.assertNotEquals("data:image/png;base64,dummy", correctedProfile.getQrCodeData());
        org.junit.jupiter.api.Assertions.assertTrue(correctedProfile.getQrCodeData().startsWith("data:image/png;base64,"));
        
        // Clean up test records
        memberRepository.delete(correctedMember);
        userProfileRepository.delete(correctedProfile);
        System.out.println("Cleaned up dummy test records.");
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

        // Print existing members verification URLs
        try {
            System.out.println("Querying member verification URLs...");
            Iterable<Member> members = memberRepository.findAll();
            for (Member member : members) {
                System.out.println("Member ID: " + member.getMemberId() + ", Name: " + member.getFullName() + ", Verification URL: " + member.getVerificationUrl());
            }
        } catch (Exception e) {
            System.out.println("Querying members FAILED:");
            e.printStackTrace();
        }

        // Print existing user profiles
        try {
            System.out.println("Querying user profiles...");
            Iterable<UserProfile> profiles = userProfileRepository.findAll();
            for (UserProfile p : profiles) {
                System.out.println("Profile ID: " + p.getId() + ", Name: " + p.getFullName() + ", Email: " + p.getEmail() + ", Enrollment: " + p.getEnrollmentNumber() + ", Has QR: " + (p.getQrCodeData() != null && !p.getQrCodeData().isEmpty()));
            }
        } catch (Exception e) {
            System.out.println("Querying user profiles FAILED:");
            e.printStackTrace();
        }

        // Search all tables and columns for 'verify.cyberlearnix'
        try {
            System.out.println("=== DB SEARCH FOR 'verify.cyberlearnix' ===");
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                String[] types = {"TABLE"};
                try (ResultSet tables = meta.getTables(null, null, "%", types)) {
                    while (tables.next()) {
                        String tableName = tables.getString("TABLE_NAME");
                        try (ResultSet cols = meta.getColumns(null, null, tableName, "%")) {
                            while (cols.next()) {
                                String colName = cols.getString("COLUMN_NAME");
                                String typeName = cols.getString("TYPE_NAME");
                                if ("varchar".equalsIgnoreCase(typeName) || "text".equalsIgnoreCase(typeName)) {
                                    String sql = "SELECT COUNT(*) FROM \"" + tableName + "\" WHERE \"" + colName + "\" LIKE '%verify.cyberlearnix%'";
                                    try (Statement stmt = conn.createStatement();
                                         ResultSet countRs = stmt.executeQuery(sql)) {
                                        if (countRs.next()) {
                                            int count = countRs.getInt(1);
                                            if (count > 0) {
                                                System.out.println("FOUND in table '" + tableName + "', column '" + colName + "': " + count + " rows match.");
                                                String selectSql = "SELECT \"" + colName + "\" FROM \"" + tableName + "\" WHERE \"" + colName + "\" LIKE '%verify.cyberlearnix%' LIMIT 5";
                                                try (Statement selectStmt = conn.createStatement();
                                                     ResultSet selectRs = selectStmt.executeQuery(selectSql)) {
                                                    while (selectRs.next()) {
                                                        System.out.println("  Value: " + selectRs.getString(1));
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception colEx) {
                                        // Ignore errors on specific column queries (e.g. system tables)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            System.out.println("=== DB SEARCH END ===");
        } catch (Exception e) {
            System.out.println("DB search failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("=== DIAGNOSTICS END ===");
    }
}
