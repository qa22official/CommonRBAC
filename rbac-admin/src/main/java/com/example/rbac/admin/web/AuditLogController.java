package com.example.rbac.admin.web;

import com.example.rbac.api.annotation.NoAudit;
import com.example.rbac.api.annotation.RequiresPermission;
import com.example.rbac.api.audit.AuditCategory;
import com.example.rbac.api.audit.AuditLogPage;
import com.example.rbac.api.audit.AuditLogQuery;
import com.example.rbac.api.audit.AuditQueryService;
import com.example.rbac.api.constant.Permissions;
import com.example.rbac.api.support.ApiResponse;
import com.example.rbac.api.support.BusinessException;
import com.example.rbac.api.support.ErrorCodes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogController {

    private final AuditQueryService auditQueryService;

    public AuditLogController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @RequiresPermission(Permissions.SYS_AUDIT_READ)
    @NoAudit("纯读，不改变数据")
    public ApiResponse<AuditLogPage> query(
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) String auditCategory,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) Integer result,
            @RequestParam(required = false) Long lastId,
            @RequestParam(defaultValue = "20") int size) {
        try {
            AuditCategory category = auditCategory == null || auditCategory.isBlank()
                    ? null : AuditCategory.valueOf(auditCategory);
            Boolean success = result == null ? null : result == 1;
            if (result != null && result != 0 && result != 1) {
                throw new BusinessException(ErrorCodes.PARAM_INVALID, "result 只能是 0 或 1");
            }
            AuditLogQuery query = new AuditLogQuery(
                    parseTime(startTime),
                    parseTime(endTime),
                    category,
                    operatorId,
                    blankToNull(action),
                    blankToNull(targetType),
                    targetId,
                    success,
                    lastId,
                    size);
            return ApiResponse.success(auditQueryService.query(query), ControllerSupport.traceId());
        } catch (BusinessException ex) {
            throw ex;
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new BusinessException(ErrorCodes.PARAM_INVALID, "查询参数非法：" + ex.getMessage());
        }
    }

    private Instant parseTime(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return OffsetDateTime.parse(value).toInstant();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
