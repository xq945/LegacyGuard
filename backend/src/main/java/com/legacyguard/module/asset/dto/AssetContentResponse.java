package com.legacyguard.module.asset.dto;

import com.legacyguard.module.asset.domain.DigitalAsset;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 资产内容响应 —— 系统里唯一会返回明文的 DTO。
 *
 * <p>{@code accessType} 告诉前端这次访问是所有者本人还是通过授权案件：
 * <ul>
 *   <li>{@code OWNER_VIEW} —— 所有者查看自己的资产</li>
 *   <li>{@code CASE_VIEW} —— 联系人通过生效中的授权案件查看</li>
 * </ul>
 * 前端据此调整界面（例如联系人视角下不显示"编辑"按钮 —— 虽然后端本来也会拒绝）。
 */
@Schema(description = "资产内容（解密后）")
public record AssetContentResponse(

        @Schema(description = "资产 ID")
        Long assetId,

        @Schema(description = "资产名称")
        String name,

        @Schema(description = "资产类型")
        String assetType,

        @Schema(description = "解密后的内容")
        String content,

        @Schema(description = "本次访问的身份：OWNER_VIEW / CASE_VIEW")
        String accessType,

        @Schema(description = "内容最后更新时间")
        LocalDateTime updatedAt
) {

    public static AssetContentResponse of(DigitalAsset asset, String content, String accessType) {
        return new AssetContentResponse(
                asset.getId(),
                asset.getName(),
                asset.getAssetType(),
                content,
                accessType,
                asset.getUpdatedAt()
        );
    }
}
