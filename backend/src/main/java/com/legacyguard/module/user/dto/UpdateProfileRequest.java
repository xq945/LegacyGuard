package com.legacyguard.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新个人资料请求。
 *
 * <p>只允许改这三个字段。用户名、角色、状态都不在此列 ——
 * 想改它们必须走单独的管理流程，不存在"顺带改一下"的路径。
 *
 * <p>字段为 {@code null} 表示不修改（PATCH 语义）。
 */
@Schema(description = "更新个人资料")
public record UpdateProfileRequest(

        @Size(max = 50, message = "显示名不能超过 50 字")
        @Schema(description = "显示名，null 表示不修改", example = "张三")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 120, message = "邮箱不能超过 120 字")
        @Schema(description = "邮箱，null 表示不修改")
        String email,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        @Schema(description = "手机号，null 表示不修改")
        String phone
) {
}
