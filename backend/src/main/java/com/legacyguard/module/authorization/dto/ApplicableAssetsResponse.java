package com.legacyguard.module.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发起授权申请前的资格与可选资产。
 *
 * <p>这个接口只返回元数据，不碰密文，也不返回资产内容。
 * 联系人需要看到名称才能决定勾选哪些资产；真正的访问权仍然只在
 * {@code AUTHORIZED} 之后、且每次读取都重新判定。
 *
 * <p>不可申请时不返回资产列表 —— 没有资格就不需要看到清单，
 * 少一次查询，也少一分无谓的暴露。
 */
@Schema(description = "授权申请资格与可选资产")
public record ApplicableAssetsResponse(

        @Schema(description = "联系人关系 ID")
        Long contactId,

        @Schema(description = "资产所有者 ID")
        Long ownerId,

        @Schema(description = "当前是否可以发起申请")
        boolean canApply,

        @Schema(description = "不可申请的原因；可申请时为 null")
        String blockReason,

        @Schema(description = "该所有者名下的资产元数据；不可申请时为空列表")
        List<ApplicableAssetItem> assets
) {

    @Schema(description = "可申请资产元数据（不含内容与密文）")
    public record ApplicableAssetItem(

            @Schema(description = "资产 ID")
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

            @Schema(description = "更新时间")
            LocalDateTime updatedAt,

            @Schema(description = "是否已被其他进行中的案件占用")
            boolean occupied
    ) {
    }
}
