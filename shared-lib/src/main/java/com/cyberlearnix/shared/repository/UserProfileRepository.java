package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.user.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {
    List<UserProfile> findByRole(String role);
}
