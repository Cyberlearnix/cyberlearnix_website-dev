package com.cyberlearnix.user.service;

import com.cyberlearnix.shared.entity.user.ContactSubmission;
import com.cyberlearnix.shared.entity.user.User;
import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.ContactSubmissionRepository;
import com.cyberlearnix.shared.repository.UserProfileRepository;
import com.cyberlearnix.shared.repository.UserRepository;
import com.cyberlearnix.user.dto.UserResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private ContactSubmissionRepository contactSubmissionRepository;

    public List<UserResponseDTO> getAllUsers() {
        // 1. Fetch system users and profiles
        List<User> users = userRepository.findAll();
        List<UserProfile> profiles = userProfileRepository.findAll();
        
        // 2. Map profiles for easy lookup
        Map<String, UserProfile> profileMap = profiles.stream()
                .collect(Collectors.toMap(UserProfile::getId, p -> p, (p1, p2) -> p1));

        Set<String> processedEmails = new HashSet<>();
        List<UserResponseDTO> result = new ArrayList<>();

        // 3. Add system users to the result
        for (User user : users) {
            String email = user.getEmail().toLowerCase();
            UserProfile profile = profileMap.get(user.getId());
            
            result.add(new UserResponseDTO(
                    user.getId(),
                    user.getEmail(),
                    profile != null ? profile.getFullName() : null,
                    profile != null ? profile.getPhone() : null,
                    user.getRole(),
                    profile != null ? profile.getIsActive() : true,
                    user.getCreatedAt(),
                    user.getLastLogin()
            ));
            processedEmails.add(email);
        }

        // 4. Fetch contact submissions and add as "Inquiry" if not already present
        List<ContactSubmission> submissions = contactSubmissionRepository.findAll();
        for (ContactSubmission sub : submissions) {
            String email = sub.getEmail().toLowerCase();
            if (!processedEmails.contains(email)) {
                result.add(new UserResponseDTO(
                        "sub-" + sub.getId(), // Prefix with 'sub-' to distinguish
                        sub.getEmail(),
                        sub.getName(),
                        sub.getPhone(),
                        "lead", // Identify them as a lead or inquiry
                        true,
                        sub.getCreatedAt(),
                        null // No login for inquiry leads
                ));
                processedEmails.add(email);
            }
        }

        return result;
    }
}
