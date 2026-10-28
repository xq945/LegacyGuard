package com.legacyguard.module.heartbeat.controller;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.common.web.ClientIpResolver;
import com.legacyguard.module.heartbeat.dto.HeartbeatConfigRequest;
import com.legacyguard.module.heartbeat.dto.HeartbeatRecordResponse;
import com.legacyguard.module.heartbeat.dto.HeartbeatStatusResponse;
import com.legacyguard.module.heartbeat.service.HeartbeatService;
import com.legacyguard.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定期状态确认接口。
 *
 * <p>注意 {@code POST /confirm} 的连锁效应：它不只是重置一个计时器，
 * 还会作废该用户所有未生效的授权案件。这是"人回来了"应有的语义。
 */
@RestController
@RequestMapping("/api/heartbeat")
@Validated
@Tag(name = "状态确认", description = "定期确认自己仍然在线，是授权流程的启动条件")
public class HeartbeatController {

    private final HeartbeatService heartbeatService;
    private final ClientIpResolver clientIpResolver;

    public HeartbeatController(HeartbeatService heartbeatService, ClientIpResolver clientIpResolver) {
        this.heartbeatService = heartbeatService;
        this.clientIpResolver = clientIpResolver;
    }

    @GetMapping("/status")
    @Operation(summary = "查看状态确认情况", description = "首次访问会自动创建默认配置。")
    public ApiResponse<HeartbeatStatusResponse> status() {
        return ApiResponse.success(heartbeatService.getStatus(CurrentUser.get()));
    }

    @PutMapping("/config")
    @Operation(summary = "修改确认周期与提醒策略")
    public ApiResponse<HeartbeatStatusResponse> updateConfig(
            @Valid @RequestBody HeartbeatConfigRequest request) {
        return ApiResponse.success(heartbeatService.updateConfig(CurrentUser.get(), request));
    }

    @PostMapping("/confirm")
    @Operation(summary = "完成一次状态确认", description = """
            重置计时器并开始新的周期。

            **次要但重要的副作用**：该用户所有未生效的授权案件（草稿、待审、审核中、
            等待期、异议中）会被自动取消 —— 用户既然回来了，那些建立在"失联"前提上的
            申请就不再成立。已经生效的授权不受影响，需要所有者主动撤销。
            """)
    public ApiResponse<HeartbeatStatusResponse> confirm(HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        return ApiResponse.success(heartbeatService.confirm(CurrentUser.get(), clientIp));
    }

    @GetMapping("/records")
    @Operation(summary = "我的确认历史")
    public ApiResponse<PageResult<HeartbeatRecordResponse>> records(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码从 1 开始") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少 1 条")
            @Max(value = 100, message = "每页最多 100 条") long size) {
        return ApiResponse.success(heartbeatService.listRecords(CurrentUser.get(), page, size));
    }
}
