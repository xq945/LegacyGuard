package com.legacyguard.module.user.controller;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.module.user.dto.UpdateProfileRequest;
import com.legacyguard.module.user.dto.UserProfileResponse;
import com.legacyguard.module.user.service.UserService;
import com.legacyguard.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人资料接口。
 *
 * <p><b>注意方法签名里没有 userId 参数。</b>当前用户一律由
 * {@link CurrentUser#id()} 从认证上下文取得 —— 这是"不信任前端提交的身份"
 * 这条规则最直接的体现。任何人把 URL 或请求体里的 ID 改成别人的，
 * 都不会改变服务端认定的操作者。
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "个人资料", description = "查看与修改自己的资料")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前登录用户资料")
    public ApiResponse<UserProfileResponse> me() {
        return ApiResponse.success(userService.getProfile(CurrentUser.id()));
    }

    @PutMapping("/me")
    @Operation(summary = "更新当前用户资料",
            description = "只允许修改显示名、邮箱、手机号；传入 null 表示该字段不变。")
    public ApiResponse<UserProfileResponse> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(CurrentUser.id(), request));
    }
}
