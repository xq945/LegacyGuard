package com.legacyguard.module.user.dto;

import com.legacyguard.module.user.domain.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 用户资料响应。
 *
 * <p>字段是白名单式枚举 —— 只有这个 record 里写出来的字段才可能出现在响应中。
 * {@code passwordHash} 不在其中，因此不存在"忘了过滤"导致泄露的可能。
 */
@Schema(description = "用户资料")
public record UserProfileResponse(

        @Schema(description = "用户 ID", example = "1")
        Long id,

        @Schema(description = "登录名", example = "zhangsan")
        String username,

        @Schema(description = "显示名", example = "张三")
        String displayName,

        @Schema(description = "邮箱")
        String email,

        @Schema(description = "手机号")
        String phone,

        @Schema(description = "角色：USER / ADMIN", example = "USER")
        String role,

        @Schema(description = "最近登录时间")
        LocalDateTime lastLoginAt,

        @Schema(description = "注册时间")
        LocalDateTime createdAt
) {

    public static UserProfileResponse from(SysUser user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
