package com.cyberlearnix.user.seeder;

import com.cyberlearnix.shared.entity.User;
import com.cyberlearnix.shared.entity.UserProfile;
import com.cyberlearnix.shared.repository.UserProfileRepository;
import com.cyberlearnix.shared.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Seeds the default admin user on startup if not already present.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserProfileRepository userProfileRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public void run(String... args) {
        String adminEmail = "shivakumar@cyberlearnix.com";

        Optional<User> existingAdmin = userRepository.findByEmail(adminEmail);
        if (existingAdmin.isEmpty()) {
            User admin = new User();
            admin.setEmail(adminEmail);
            admin.setPasswordHash(passwordEncoder.encode("Shivam$179"));
            admin.setRole("admin");
            userRepository.save(admin);
            System.out.println("[AdminSeeder] Created default admin user: " + adminEmail);
        } else {
            User admin = existingAdmin.get();
            admin.setPasswordHash(passwordEncoder.encode("Shivam$179"));
            admin.setRole("admin"); // Ensure role is still admin
            userRepository.save(admin);
            System.out.println("[AdminSeeder] Updated password for existing admin: " + adminEmail);
        }

        // Ensure UserProfile exists
        String userId = userRepository.findByEmail(adminEmail).get().getId();
        UserProfile profile = userProfileRepository.findById(userId).orElse(new UserProfile());
        profile.setId(userId);
        profile.setEmail(adminEmail);
        profile.setRole("admin");
        if (profile.getFullName() == null) {
            profile.setFullName("Shivakumar (Admin)");
        }
        profile.setIsActive(true);
        userProfileRepository.save(profile);
        System.out.println("[AdminSeeder] UserProfile verified for: " + adminEmail);
    }
}
