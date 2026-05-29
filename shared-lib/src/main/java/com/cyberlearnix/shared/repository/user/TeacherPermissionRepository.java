package com.cyberlearnix.shared.repository.user;

import com.cyberlearnix.shared.entity.user.TeacherPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeacherPermissionRepository extends JpaRepository<TeacherPermission, String> {
}
