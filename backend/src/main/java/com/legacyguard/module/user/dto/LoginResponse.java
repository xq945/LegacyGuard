package com.legacyguard.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录响应。
 *
 * <p>不返回 refresh token —— v1 用 30 分钟短令牌，过期即重新登录。
 * 少一条续期链路，就少一处可能被利用的入口。
 */
@Schema(description = "登录响应")
public record LoginResponse(

        @Schema(description = "访问令牌，放在 Authorization: Bearer 头中")
        String accessToken,

        @Schema(description = "有效期（秒）", example = "1800")
        long expiresInSeconds,

        @Schema(description = "当前用户资料")
        UserProfileResponse user
) {
}
