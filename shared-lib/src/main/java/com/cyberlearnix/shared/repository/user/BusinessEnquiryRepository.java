package com.cyberlearnix.shared.repository.user;

import com.cyberlearnix.shared.entity.user.BusinessEnquiry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface BusinessEnquiryRepository extends JpaRepository<BusinessEnquiry, Long>, JpaSpecificationExecutor<BusinessEnquiry> {

    Optional<BusinessEnquiry> findByEnquiryReference(String enquiryReference);

    Optional<BusinessEnquiry> findTopBySubmissionHashAndCreatedAtAfterOrderByCreatedAtDesc(
            String submissionHash, LocalDateTime after);

    @Query("SELECT MAX(e.id) FROM BusinessEnquiry e")
    Long findMaxId();

    @Query("SELECT COUNT(e) FROM BusinessEnquiry e WHERE e.enquiryReference LIKE CONCAT(:prefix, '%')")
    long countByReferencePrefix(@Param("prefix") String prefix);

    long countByLeadStatus(String leadStatus);

    @Query("SELECT COUNT(e) FROM BusinessEnquiry e WHERE e.leadStatus IN ('New', 'Contacted', 'Meeting Scheduled', 'Proposal Sent', 'Negotiation')")
    long countPendingFollowUps();
}
