package com.legacyguard.module.heartbeat.controller;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.module.heartbeat.dto.HeartbeatStatusResponse;
import com.legacyguard.module.heartbeat.service.HeartbeatService;
import com.legacyguard.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端：失联核验。
 *
 * <p><b>这是整个系统里唯一能把用户从"提醒完毕"推进到"可申请授权"的入口。</b>
 * 没有定时任务能走到这里 —— 再长的超时也换不来这一步，
 * 必须由人来看一眼并做决定。
 */
@RestController
@RequestMapping("/api/admin/heartbeat")
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理端-失联核验", description = "核验失联事实，通过后才允许联系人申请授权")
public class AdminHeartbeatController {

    private final HeartbeatService heartbeatService;

    public AdminHeartbeatController(HeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @GetMapping("/verify-queue")
    @Operation(summary = "待核验队列", description = "列出提醒轮次已耗尽、等待人工核验的用户。")
    public ApiResponse<List<HeartbeatStatusResponse>> verifyQueue(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码从 1 开始") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少 1 条")
            @Max(value = 100, message = "每页最多 100 条") long size) {
        return ApiResponse.success(heartbeatService.listVerifyQueue(page, size));
    }

    @PostMapping("/{userId}/verify")
    @Operation(summary = "核验失联成立", description = """
            把用户状态从 VERIFYING 推进到 REVIEWING，之后其可信联系人才能发起授权申请。

            这一步必须由人工完成：单纯超时永远不能自动授权。
            """)
    public ApiResponse<Void> verify(@PathVariable Long userId) {
        heartbeatService.verify(CurrentUser.get(), userId);
        return ApiResponse.success();
    }
}
