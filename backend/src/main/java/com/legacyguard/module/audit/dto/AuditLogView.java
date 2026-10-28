package com.legacyguard.module.audit.dto;

import com.legacyguard.module.audit.domain.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 审计记录展示对象。
 *
 * <p>返回给普通用户自己看时，{@code clientIp} 与 {@code userAgent} 仍是自己的信息，
 * 可以展示；管理端查询他人记录时也保留，因为审计的价值就在于可追溯。
 * 但 {@code detail} 里绝不会出现密码、令牌、资产明文 —— 那是写入时的约束。
 */
@Schema(description = "审计记录")
public record AuditLogView(

        @Schema(description = "记录 ID")
        Long id,

        @Schema(description = "操作人 ID")
        Long actorId,

        @Schema(description = "操作人角色")
        String actorRole,

        @Schema(description = "动作")
        String action,

        @Schema(description = "业务对象类型")
        String objectType,

        @Schema(description = "业务对象 ID")
        Long objectId,

        @Schema(description = "结果：SUCCESS / FAILURE")
        String result,

        @Schema(description = "请求关联 ID")
        String requestId,

        @Schema(description = "变更摘要")
        String detail,

        @Schema(description = "客户端 IP")
        String clientIp,

        @Schema(description = "操作时间")
        LocalDateTime createdAt
) {

    public static AuditLogView from(AuditLog log) {
        return new AuditLogView(
                log.getId(),
                log.getActorId(),
                log.getActorRole(),
                log.getAction(),
                log.getObjectType(),
                log.getObjectId(),
                log.getResult(),
                log.getRequestId(),
                log.getDetail(),
                log.getClientIp(),
                log.getCreatedAt()
        );
    }
}
