package com.legacyguard.module.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员处理异议。
 *
 * @param result {@code UPHELD} 表示异议不成立、回到等待期（重新计时）；
 *               {@code OVERTURNED} 表示异议成立、案件直接驳回
 */
@Schema(description = "处理异议")
public record ResolveDisputeRequest(

        @NotBlank(message = "处理结论不能为空")
        @Pattern(regexp = "UPHELD|OVERTURNED",
                message = "处理结论必须是 UPHELD（异议不成立）或 OVERTURNED（异议成立）")
        @Schema(description = "UPHELD：异议不成立，重回等待期；OVERTURNED：异议成立，案件驳回",
                example = "UPHELD")
        String result,

        @NotBlank(message = "处理说明不能为空")
        @Size(max = 500, message = "处理说明不能超过 500 字")
        @Schema(description = "处理说明")
        String comment
) {
}
