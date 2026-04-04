package com.cyberlearnix.shared.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "certificate_templates")
@Data
public class CertificateTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String type;
    
    @Column(name = "background_url")
    private String backgroundUrl;
}
