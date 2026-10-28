package com.legacyguard.module.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 更新资产请求。字段为 null 表示不修改。
 *
 * <p><b>只有所有者能调用。</b>被授权的联系人即便是通过生效案件访问，
 * 也只能读，不能改 —— 授权的是"查看权"，不是"处置权"。
 */
@Schema(description = "更新资产")
public record UpdateAssetRequest(

        @Size(max = 100, message = "资产名称不能超过 100 字")
        @Schema(description = "新名称，null 表示不修改")
        String name,

        @Schema(description = "新类型，null 表示不修改")
        String assetType,

        @Size(max = 500, message = "描述不能超过 500 字")
        @Schema(description = "新描述，null 表示不修改")
        String description,

        // 同 CreateAssetRequest：长度按字节在 Service 层校验，避免 400/422 两种错误码并存
        @Schema(description = "新内容，null 表示不修改。修改后会重新加密并更新摘要")
        String content,

        @Size(max = 255)
        String fileName,

        Long fileSize,

        @Size(max = 100)
        String mimeType
) {
}
