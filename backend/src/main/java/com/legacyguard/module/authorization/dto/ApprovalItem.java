package com.legacyguard.module.authorization.dto;

import com.legacyguard.module.authorization.domain.AuthorizationApproval;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 一条审批/状态变更记录。
 */
@Schema(description = "审批记录")
public record ApprovalItem(

        @Schema(description = "操作人 ID")
        Long approverId,

        @Schema(description = "动作：ACCEPT / APPROVE / REJECT / RESOLVE_DISPUTE")
        String action,

        @Schema(description = "变更前状态")
        String fromStatus,

        @Schema(description = "变更后状态")
        String toStatus,

        @Schema(description = "意见")
        String comment,

        @Schema(description = "时间")
        LocalDateTime createdAt
) {

    public static ApprovalItem from(AuthorizationApproval a) {
        return new ApprovalItem(a.getApproverId(), a.getAction(), a.getFromStatus(),
                a.getToStatus(), a.getComment(), a.getCreatedAt());
    }
}
