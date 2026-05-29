package com.cyberlearnix.enrollment.dto;

import lombok.Data;
import java.util.List;

@Data
public class BulkAssignRequest {
    private String userId;
    private List<Long> courseIds;
}
