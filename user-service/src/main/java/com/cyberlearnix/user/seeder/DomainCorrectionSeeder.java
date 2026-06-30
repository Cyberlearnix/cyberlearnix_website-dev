package com.cyberlearnix.user.seeder;

import com.cyberlearnix.shared.entity.identity.Member;
import com.cyberlearnix.shared.repository.identity.MemberRepository;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import com.cyberlearnix.user.service.IdentityService;
import com.cyberlearnix.user.service.EnrollmentCardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Startup seeder that detects and corrects any incorrect verification URLs 
 * and QR codes stored in the database.
 */
@Component
public class DomainCorrectionSeeder implements CommandLineRunner {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private IdentityService identityService;

    @Autowired
    private EnrollmentCardService enrollmentCardService;

    @Override
    public void run(String... args) {
        System.out.println("[DomainCorrectionSeeder] Starting database check for verify.cyberlearnix domain corrections...");

        // 1. Correct Member records
        try {
            List<Member> members = memberRepository.findAll();
            int correctedMembersCount = 0;
            for (Member member : members) {
                String verificationUrl = member.getVerificationUrl();
                boolean needsCorrection = false;
                
                if (verificationUrl == null || verificationUrl.isBlank()) {
                    needsCorrection = true;
                } else if (verificationUrl.contains("verify.cyberlearnix")) {
                    needsCorrection = true;
                } else if (!verificationUrl.contains("verify.html?enrollment=")) {
                    needsCorrection = true;
                }

                if (needsCorrection) {
                    System.out.println("[DomainCorrectionSeeder] Member " + member.getMemberId() + " (" + member.getFullName() + ") has incorrect/missing URL: " + verificationUrl);
                    identityService.regenerateQr(member.getId(), "System-DomainCorrection");
                    correctedMembersCount++;
                }
            }
            System.out.println("[DomainCorrectionSeeder] Checked " + members.size() + " members. Corrected " + correctedMembersCount + " records.");
        } catch (Exception e) {
            System.err.println("[DomainCorrectionSeeder] Error during Member URL corrections: " + e.getMessage());
            e.printStackTrace();
        }

        // 2. Correct UserProfile records (LMS Student cards)
        try {
            List<UserProfile> profiles = userProfileRepository.findAll();
            int correctedProfilesCount = 0;
            for (UserProfile profile : profiles) {
                if (profile.getEnrollmentNumber() != null && !profile.getEnrollmentNumber().isBlank()) {
                    // Since QR code is a base64 image and doesn't store verification URL as text,
                    // we re-issue the QR code for all existing profile cards to ensure they use the correct domain.
                    System.out.println("[DomainCorrectionSeeder] Updating QR code for UserProfile: " + profile.getEmail() + " (" + profile.getEnrollmentNumber() + ")");
                    enrollmentCardService.updateQrCode(profile);
                    correctedProfilesCount++;
                }
            }
            System.out.println("[DomainCorrectionSeeder] Checked " + profiles.size() + " user profiles. Updated QR codes for " + correctedProfilesCount + " records.");
        } catch (Exception e) {
            System.err.println("[DomainCorrectionSeeder] Error during UserProfile QR corrections: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[DomainCorrectionSeeder] Domain correction check finished successfully.");
    }
}
