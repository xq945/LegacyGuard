package com.legacyguard.module.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建授权案件（草稿）。
 *
 * <p>注意 {@code contactId} 是<b>联系人关系 ID</b>，不是用户 ID。
 * 申请人身份仍然来自令牌，这里传的只是"我用哪一份联系人资格来申请"。
 */
@Schema(description = "创建授权案件")
public record CreateCaseRequest(

        @NotNull(message = "联系人关系 ID 不能为空")
        @Schema(description = "自己的 trusted_contact 关系 ID", example = "1")
        Long contactId,

        @NotEmpty(message = "至少要选择一项资产")
        @Size(max = 100, message = "单个案件最多覆盖 100 项资产")
        @Schema(description = "要申请的资产 ID 列表")
        List<Long> assetIds,

        @Size(max = 500, message = "申请理由不能超过 500 字")
        @Schema(description = "申请理由", example = "父亲已失联超过三个月，需要按他此前的嘱托处理")
        String reason
) {
}
