package com.legacyguard.module.asset.dto;

import com.legacyguard.module.asset.domain.DigitalAsset;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 资产元数据响应。
 *
 * <p>白名单式字段枚举：{@code cipherText}、{@code wrappedDek} 等字段不在其中，
 * 因此不存在"忘了过滤导致密文泄露"的可能。密文只能在
 * {@code GET /api/assets/{id}/content} 路径下、通过访问守卫后才可能被解密返回。
 */
@Schema(description = "资产元数据")
public record AssetResponse(

        @Schema(description = "资产 ID", example = "1")
        Long id,

        @Schema(description = "资产名称")
        String name,

        @Schema(description = "资产类型")
        String assetType,

        @Schema(description = "描述（不含敏感内容）")
        String description,

        @Schema(description = "文件元数据：文件名")
        String fileName,

        @Schema(description = "文件元数据：字节数")
        Long fileSize,

        @Schema(description = "文件元数据：MIME 类型")
        String mimeType,

        @Schema(description = "0 正常 / 1 归档")
        Integer status,

        @Schema(description = "创建时间")
        LocalDateTime createdAt,

        @Schema(description = "更新时间")
        LocalDateTime updatedAt
) {

    public static AssetResponse from(DigitalAsset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getName(),
                asset.getAssetType(),
                asset.getDescription(),
                asset.getFileName(),
                asset.getFileSize(),
                asset.getMimeType(),
                asset.getStatus(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }
}
