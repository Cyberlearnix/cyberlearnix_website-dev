package com.cyberlearnix.shared.entity.course;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificates")
@Data
public class Certificate {
    
    public enum CertificateType {
        CERTIFICATE,
        EXAM,
        COURSE_BADGE
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "student_id")
    private String studentId;
    
    @Column(name = "course_id")
    private Long courseId;
    
    @Column(name = "course_title")
    private String courseTitle;

    @Column(name = "student_name")
    private String studentName;

    @Column(name = "instructor_name")
    private String instructorName;

    @Column(name = "certificate_image_url", length = 1000)
    private String certificateImageUrl;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private CertificateType type = CertificateType.CERTIFICATE;
    
    @Column(name = "issued_at")
    private LocalDateTime issuedAt = LocalDateTime.now();
    
    @Column(name = "badge_image_url")
    private String badgeImageUrl;
    
    @Column(name = "certificate_id", unique = true)
    private String certificateId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
