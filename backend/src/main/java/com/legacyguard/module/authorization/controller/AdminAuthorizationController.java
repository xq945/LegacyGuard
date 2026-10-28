package com.legacyguard.module.authorization.controller;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.module.authorization.dto.ApprovalRequest;
import com.legacyguard.module.authorization.dto.CaseDetailResponse;
import com.legacyguard.module.authorization.dto.CaseResponse;
import com.legacyguard.module.authorization.dto.ResolveDisputeRequest;
import com.legacyguard.module.authorization.service.AuthorizationCaseService;
import com.legacyguard.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 授权案件接口（管理端）。
 *
 * <p><b>管理员能做什么，不能做什么，在这里一目了然：</b>
 * <ul>
 *   <li>能：受理、批准、驳回、处理异议 —— 都是推进流程</li>
 *   <li>不能：撤销已生效授权（那是所有者的权利）</li>
 *   <li>不能：读取任何资产内容（本控制器不返回资产明文）</li>
 * </ul>
 *
 * <p>批准只把案件推进到等待期。真正生效要等等待期结束、系统重新复核全部条件 ——
 * 所以这里不存在"点了批准资产就到手"的捷径。
 */
@RestController
@RequestMapping("/api/admin/authorizations")
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理端-案件审核", description = "受理、批准、驳回与异议处理")
public class AdminAuthorizationController {

    private final AuthorizationCaseService caseService;

    public AdminAuthorizationController(AuthorizationCaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/pending")
    @Operation(summary = "待处理案件", description = "待受理（PENDING_REVIEW）与审核中（REVIEWING）的案件。")
    public ApiResponse<PageResult<CaseResponse>> pending(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码从 1 开始") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少 1 条")
            @Max(value = 100, message = "每页最多 100 条") long size) {
        return ApiResponse.success(caseService.listPending(CurrentUser.get(), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "案件详情（审核用）",
            description = "返回案件元数据与审批链，**不包含任何资产内容**。")
    public ApiResponse<CaseDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(caseService.detail(CurrentUser.get(), id));
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "受理案件", description = "PENDING_REVIEW → REVIEWING。")
    public ApiResponse<Void> accept(@PathVariable Long id) {
        caseService.accept(CurrentUser.get(), id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "批准", description = """
            REVIEWING → WAITING_PERIOD。

            **批准不等于生效。** 案件会进入等待期，期间所有者可以提出异议；
            等待期结束时会重新校验全部条件（所有者是否仍失联、关系是否有效、
            资产是否仍存在、有无新异议），全部通过才真正授权。
            """)
    public ApiResponse<Void> approve(@PathVariable Long id,
                                     @Valid @RequestBody ApprovalRequest request) {
        caseService.approve(CurrentUser.get(), id, request);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "驳回", description = "PENDING_REVIEW / REVIEWING → REJECTED，必须填写理由。")
    public ApiResponse<Void> reject(@PathVariable Long id,
                                    @Valid @RequestBody ApprovalRequest request) {
        caseService.reject(CurrentUser.get(), id, request);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/resolve-dispute")
    @Operation(summary = "处理异议", description = """
            **UPHELD**：异议不成立 → 回到等待期，并**重新计时**。

            **OVERTURNED**：异议成立 → 案件直接驳回。
            """)
    public ApiResponse<Void> resolveDispute(@PathVariable Long id,
                                            @Valid @RequestBody ResolveDisputeRequest request) {
        caseService.resolveDispute(CurrentUser.get(), id, request);
        return ApiResponse.success();
    }

    /**
     * 手动触发等待期扫描。
     *
     * <p>等价于定时任务跑一轮，供运维在异常排查时立即触发，也便于演示环境
     * 不必等待调度周期（定时任务将在第四阶段接入调度器）。
     *
     * <p><b>它不是"立即生效"的捷径</b>：仍然只处理已经过了等待期、
     * 且全条件复核通过的案件。
     */
    @PostMapping("/scan-expired")
    @Operation(summary = "手动触发等待期扫描",
            description = "等价于定时任务跑一轮：只处理已过等待期且复核通过的案件。")
    public ApiResponse<java.util.Map<String, Integer>> scanExpired() {
        int authorized = caseService.authorizeDueCases();
        return ApiResponse.success(java.util.Map.of("authorized", authorized));
    }
}
