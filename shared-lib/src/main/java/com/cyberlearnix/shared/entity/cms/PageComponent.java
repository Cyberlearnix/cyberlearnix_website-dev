package com.cyberlearnix.shared.entity.cms;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Entity
@Table(name = "page_components")
@ToString(exclude = "section")
public class PageComponent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    @JsonIgnore
    private PageSection section;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type")
    private ComponentType componentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "component_data", columnDefinition = "jsonb")
    private Map<String, Object> componentData;

    @Column(name = "order_index")
    private Integer orderIndex = 0;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
