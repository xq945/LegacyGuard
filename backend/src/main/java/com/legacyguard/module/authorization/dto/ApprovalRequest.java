package com.legacyguard.module.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员审批请求（批准或驳回）。
 */
@Schema(description = "审批请求")
public record ApprovalRequest(

        @NotBlank(message = "审批意见不能为空")
        @Size(max = 500, message = "审批意见不能超过 500 字")
        @Schema(description = "审批意见，会写入审批链并通知双方",
                example = "已核实联系人身份，资产范围与申请理由一致")
        String comment
) {
}
