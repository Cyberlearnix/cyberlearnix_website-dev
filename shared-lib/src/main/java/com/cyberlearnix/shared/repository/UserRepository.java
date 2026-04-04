package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.role) = LOWER(:role)")
    long countByRoleIgnoreCase(String role);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.role) IN :roles")
    long countByRolesIgnoreCase(java.util.List<String> roles);
}
