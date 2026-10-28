package com.legacyguard.module.authorization.dto;

import com.legacyguard.module.authorization.domain.AuthorizationCase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 案件摘要（列表用）。
 *
 * <p>不含资产详情与审批记录 —— 列表页不需要它们，带上只会让响应变大、查询变慢。
 */
@Schema(description = "授权案件摘要")
public record CaseResponse(

        @Schema(description = "案件 ID")
        Long id,

        @Schema(description = "业务编号")
        String caseNo,

        @Schema(description = "资产所有者 ID")
        Long ownerId,

        @Schema(description = "申请人关系 ID")
        Long contactId,

        @Schema(description = "案件状态")
        String status,

        @Schema(description = "申请理由")
        String reason,

        @Schema(description = "最近的审批意见")
        String reviewComment,

        @Schema(description = "等待期截止时间")
        LocalDateTime waitPeriodEndAt,

        @Schema(description = "授权生效时间")
        LocalDateTime authorizedAt,

        @Schema(description = "撤销时间")
        LocalDateTime revokedAt,

        @Schema(description = "创建时间")
        LocalDateTime createdAt,

        @Schema(description = "当前用户是否为资产所有者")
        boolean iAmOwner,

        @Schema(description = "当前用户是否为申请人")
        boolean iAmApplicant
) {

    public static CaseResponse from(AuthorizationCase c, Long currentUserId, Long applicantUserId) {
        return new CaseResponse(
                c.getId(),
                c.getCaseNo(),
                c.getOwnerId(),
                c.getContactId(),
                c.getStatus(),
                c.getReason(),
                c.getReviewComment(),
                c.getWaitPeriodEndAt(),
                c.getAuthorizedAt(),
                c.getRevokedAt(),
                c.getCreatedAt(),
                c.getOwnerId().equals(currentUserId),
                applicantUserId != null && applicantUserId.equals(currentUserId)
        );
    }
}
