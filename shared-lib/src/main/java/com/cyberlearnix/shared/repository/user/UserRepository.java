package com.cyberlearnix.shared.repository.user;

import com.cyberlearnix.shared.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);

    @Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.role) = LOWER(:role)")
    long countByRoleIgnoreCase(@Param("role") String role);

    @Query("SELECT COUNT(u) FROM User u WHERE LOWER(u.role) IN :roles")
    long countByRolesIgnoreCase(@Param("roles") java.util.List<String> roles);
}
