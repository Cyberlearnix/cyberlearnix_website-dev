package com.cyberlearnix.shared.repository;

import com.cyberlearnix.shared.entity.TeacherPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TeacherPermissionRepository extends JpaRepository<TeacherPermission, String> {
}
