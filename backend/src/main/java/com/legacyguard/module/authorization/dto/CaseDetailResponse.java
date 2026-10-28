package com.legacyguard.module.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 案件详情：在摘要基础上加上被覆盖的资产与完整审批链。
 *
 * <p>审批链是"这个授权为什么生效"的完整证据：
 * 谁在什么时候受理、谁批准的、有没有异议、异议怎么处理的。
 */
@Schema(description = "授权案件详情")
public record CaseDetailResponse(

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

        @Schema(description = "案件覆盖的资产 ID")
        List<Long> assetIds,

        @Schema(description = "等待期截止时间")
        LocalDateTime waitPeriodEndAt,

        @Schema(description = "授权生效时间")
        LocalDateTime authorizedAt,

        @Schema(description = "撤销时间")
        LocalDateTime revokedAt,

        @Schema(description = "结束时间")
        LocalDateTime closedAt,

        @Schema(description = "创建时间")
        LocalDateTime createdAt,

        @Schema(description = "更新时间")
        LocalDateTime updatedAt,

        @Schema(description = "审批与状态变更记录，按时间正序")
        List<ApprovalItem> approvals,

        @Schema(description = "当前用户是否为资产所有者")
        boolean iAmOwner,

        @Schema(description = "当前用户是否为申请人")
        boolean iAmApplicant
) {
}
