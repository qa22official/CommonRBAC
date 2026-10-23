package com.example.rbac.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明业务接口不产生审计记录。value 必填且不得为空串，说明原因。
 * 构建门禁：既未调用 AuditRecorder 也未标注本注解 = 构建失败。
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoAudit {

    String value();
}
