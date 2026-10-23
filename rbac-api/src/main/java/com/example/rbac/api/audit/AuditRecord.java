package com.example.rbac.api.audit;

import java.time.Instant;
import java.util.Objects;

/**
 * 完整的审计记录。AuditRecorder 实现把它落 sys_audit 表。
 * 密码/密码哈希/令牌/密钥不得出现在任何 detail 字段。
 */
public class AuditRecord {

    /** 结果枚举。 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL    = "FAIL";

    private AuditCategory category;
    private String action;
    private String operatorName;
    private Long operatorId;
    private String transactionId;
    private String targetType;
    private String targetId;
    private String detailBefore;
    private String detailAfter;
    private String result;
    private String failReason;
    private String clientIp;
    private String userAgent;
    private String traceId;
    private Instant eventTime;

    public AuditRecord() {}

    public AuditRecord(AuditCategory category, String action, String operatorName, Long operatorId,
                       String transactionId, String targetType, String targetId,
                       String detailBefore, String detailAfter, String result, String failReason,
                       String clientIp, String userAgent, String traceId, Instant eventTime) {
        this.category = category;
        this.action = action;
        this.operatorName = operatorName;
        this.operatorId = operatorId;
        this.transactionId = transactionId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detailBefore = detailBefore;
        this.detailAfter = detailAfter;
        this.result = result;
        this.failReason = failReason;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.traceId = traceId;
        this.eventTime = eventTime;
    }

    public AuditCategory category() { return category; }
    public AuditCategory getCategory() { return category; }
    public void setCategory(AuditCategory category) { this.category = category; }

    public String action() { return action; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String operatorName() { return operatorName; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }

    public Long operatorId() { return operatorId; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }

    public String transactionId() { return transactionId; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String targetType() { return targetType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String targetId() { return targetId; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String detailBefore() { return detailBefore; }
    public String getDetailBefore() { return detailBefore; }
    public void setDetailBefore(String detailBefore) { this.detailBefore = detailBefore; }

    public String detailAfter() { return detailAfter; }
    public String getDetailAfter() { return detailAfter; }
    public void setDetailAfter(String detailAfter) { this.detailAfter = detailAfter; }

    public String result() { return result; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String failReason() { return failReason; }
    public String getFailReason() { return failReason; }
    public void setFailReason(String failReason) { this.failReason = failReason; }

    public String clientIp() { return clientIp; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public String userAgent() { return userAgent; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public String traceId() { return traceId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public Instant eventTime() { return eventTime; }
    public Instant getEventTime() { return eventTime; }
    public void setEventTime(Instant eventTime) { this.eventTime = eventTime; }
}
