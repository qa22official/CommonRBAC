package com.example.rbac.audit.internal;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditRecord;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.support.ApiResponse;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditMapper mapper;

    public AuditController(AuditMapper mapper) {
        this.mapper = mapper;
    }

    /** 审计只读查询（游标分页）。 */
    @RequiresPermission("sys:audit:read")
    @GetMapping
    public ApiResponse<List<AuditRecord>> query(
            @RequestParam(required = false) AuditCategory category,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(mapper.query(category, eventType, operatorId, targetType, from, to, cursor, Math.min(limit, 200)));
    }
}
