package com.legacyguard.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求。
 */
@Schema(description = "登录请求")
public record LoginRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名过长")
        @Schema(description = "登录名", example = "zhangsan")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 64, message = "密码过长")
        @Schema(description = "登录密码", example = "Str0ngPass")
        String password
) {
}
