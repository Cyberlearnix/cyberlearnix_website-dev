package com.cyberlearnix.shared.entity.enrollment;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificates")
@Data
public class Certificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "student_name")
    private String studentName;
    
    @Column(name = "student_dob")
    private String studentDob;
    
    private String type;
    
    @Column(name = "course_name")
    private String courseName;
    
    @Column(name = "issue_date")
    private String issueDate;
    
    @Column(name = "certificate_id", unique = true)
    private String certificateId;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
