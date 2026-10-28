package com.legacyguard.module.user.controller;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.common.web.ClientIpResolver;
import com.legacyguard.module.user.dto.LoginRequest;
import com.legacyguard.module.user.dto.LoginResponse;
import com.legacyguard.module.user.dto.RegisterRequest;
import com.legacyguard.module.user.dto.UserProfileResponse;
import com.legacyguard.module.user.service.AuthService;
import com.legacyguard.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "注册、登录、登出")
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    @Operation(summary = "注册新用户",
            description = "用户名 4-20 位字母数字下划线；密码 8-64 位且需同时包含字母与数字。")
    public ResponseEntity<ApiResponse<UserProfileResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        UserProfileResponse profile = authService.register(request);
        // RESTful 语义：成功创建资源返回 201
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(profile));
    }

    @PostMapping("/login")
    @Operation(summary = "登录",
            description = "成功返回访问令牌；连续失败 5 次将锁定账号 15 分钟。")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        return ApiResponse.success(authService.login(request, clientIp));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出",
            description = "将当前令牌加入黑名单，立即失效。需要认证。")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        authService.logout(extractToken(authorization), CurrentUser.get());
        return ApiResponse.success();
    }

    /**
     * 从 {@code Authorization: Bearer <token>} 中取出原始令牌。
     *
     * <p>登出需要它来计算剩余有效期 —— 黑名单的 TTL 只需覆盖到令牌自然过期为止。
     * 能走到这里说明过滤器已通过认证，头一定存在且格式正确。
     */
    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
