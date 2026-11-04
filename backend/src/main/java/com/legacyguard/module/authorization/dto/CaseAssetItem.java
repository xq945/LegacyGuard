package com.legacyguard.module.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 案件覆盖的资产元数据。
 *
 * <p>案件详情必须让申请人也能看到资产名称 —— 申请人不是资产所有者，
 * 调资产列表接口只能看到自己的资产，无法靠客户端拼出这份清单。
 *
 * <p>只含元数据，始终不包含内容、密文或数据密钥。
 */
@Schema(description = "案件覆盖的资产元数据")
public record CaseAssetItem(

        @Schema(description = "资产 ID")
        Long id,

        @Schema(description = "资产名称")
        String name,

        @Schema(description = "资产类型")
        String assetType,

        @Schema(description = "资产是否已被所有者删除")
        boolean deleted
) {
}
