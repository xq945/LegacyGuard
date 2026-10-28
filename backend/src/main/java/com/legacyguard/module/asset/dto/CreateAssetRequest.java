package com.legacyguard.module.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建资产请求。
 *
 * <p>{@code description} 是<b>明文存储</b>的字段，会出现在列表与详情响应中 ——
 * 因此它只适合放"这是什么"，不能放"里面的秘密是什么"。
 * 真正的敏感内容必须走 {@code content}，那条路会加密。
 */
@Schema(description = "创建资产")
public record CreateAssetRequest(

        @NotBlank(message = "资产名称不能为空")
        @Size(max = 100, message = "资产名称不能超过 100 字")
        @Schema(description = "资产名称", example = "书房硬盘存放说明")
        String name,

        @NotBlank(message = "资产类型不能为空")
        @Schema(description = "DOCUMENT / IMAGE / NOTE / ACCOUNT_RECOVERY / OTHER", example = "NOTE")
        String assetType,

        @Size(max = 500, message = "描述不能超过 500 字")
        @Schema(description = "不含敏感信息的描述", example = "关于备份硬盘的位置说明")
        String description,

        // 长度不在这里校验：@Size 数的是字符数，而存储上限按 UTF-8 字节数算
        // （一个汉字 3 字节）。两处校验会导致同一个"太长"返回不同的错误码，
        // 统一交给 Service 按字节判定，结论稳定为 422。
        @NotBlank(message = "资产内容不能为空")
        @Schema(description = "敏感内容，将以加密形式存储", example = "硬盘在书房第二个抽屉，口令提示是……")
        String content,

        @Size(max = 255, message = "文件名过长")
        @Schema(description = "文件元数据：文件名，可选")
        String fileName,

        @Schema(description = "文件元数据：字节数，可选")
        Long fileSize,

        @Size(max = 100, message = "MIME 类型过长")
        @Schema(description = "文件元数据：MIME 类型，可选")
        String mimeType
) {
}
