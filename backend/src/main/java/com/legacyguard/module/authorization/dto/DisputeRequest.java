package com.legacyguard.module.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 提交异议。
 *
 * <p>异议只在<b>等待期内</b>有效。这个窗口是审批与生效之间的缓冲，
 * 也是所有者能叫停流程的最后机会。
 */
@Schema(description = "提交异议")
public record DisputeRequest(

        @NotBlank(message = "异议理由不能为空")
        @Size(max = 1000, message = "异议理由不能超过 1000 字")
        @Schema(description = "异议理由", example = "我本人健在，此申请存在冒名情形")
        String reason
) {
}
