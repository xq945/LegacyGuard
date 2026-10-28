package com.legacyguard.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。
 */
@Schema(description = "注册请求")
public record RegisterRequest(

        @NotBlank(message = "用户名不能为空")
        @Pattern(regexp = "^[a-zA-Z0-9_]{4,20}$", message = "用户名需为 4-20 位字母、数字或下划线")
        @Schema(description = "登录名", example = "zhangsan")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 位之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码必须同时包含字母和数字")
        @Schema(description = "登录密码", example = "Str0ngPass")
        String password,

        @NotBlank(message = "显示名不能为空")
        @Size(max = 50, message = "显示名不能超过 50 字")
        @Schema(description = "显示名", example = "张三")
        String displayName,

        @Email(message = "邮箱格式不正确")
        @Size(max = 120, message = "邮箱不能超过 120 字")
        @Schema(description = "邮箱，可选", example = "zhangsan@example.com")
        String email
) {
}
