package com.cyberlearnix.shared.repository.identity;

import com.cyberlearnix.shared.entity.identity.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, String> {
    Optional<Member> findByMemberId(String memberId);
    Optional<Member> findByEmail(String email);
    
    long countByMemberType(String memberType);
    long countByStatus(String status);
    
    List<Member> findTop5ByOrderByCreatedAtDesc();

    @Query("SELECT m FROM Member m WHERE " +
           "(:query IS NULL OR LOWER(m.fullName) LIKE CONCAT('%', CAST(:query AS string), '%') OR " +
           " LOWER(m.email) LIKE CONCAT('%', CAST(:query AS string), '%') OR " +
           " LOWER(m.phone) LIKE CONCAT('%', CAST(:query AS string), '%') OR " +
           " LOWER(m.memberId) LIKE CONCAT('%', CAST(:query AS string), '%')) AND " +
           "(:memberType IS NULL OR LOWER(m.memberType) = CAST(:memberType AS string)) AND " +
           "(:department IS NULL OR LOWER(m.department) = CAST(:department AS string)) AND " +
           "(:status IS NULL OR LOWER(m.status) = CAST(:status AS string))")
    Page<Member> searchMembers(
            @Param("query") String query,
            @Param("memberType") String memberType,
            @Param("department") String department,
            @Param("status") String status,
            Pageable pageable
    );

    @Query("SELECT DISTINCT m.department FROM Member m WHERE m.department IS NOT NULL AND m.department != ''")
    List<String> findDistinctDepartments();
    
    @Query("SELECT COUNT(m) FROM Member m WHERE LOWER(m.memberType) = 'employee' OR LOWER(m.memberType) = 'hr' OR LOWER(m.memberType) = 'manager' OR LOWER(m.memberType) = 'director' OR LOWER(m.memberType) = 'ceo'")
    long countEmployees();

    @Query("SELECT m FROM Member m WHERE m.status = 'Approved' AND m.isActive = true AND LOWER(m.memberType) NOT IN ('student', 'intern') ORDER BY m.fullName ASC")
    List<Member> findPublicDirectoryMembers();
}
