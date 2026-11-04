package com.legacyguard.module.authorization.controller;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.module.authorization.dto.ApplicableAssetsResponse;
import com.legacyguard.module.authorization.dto.CancelCaseRequest;
import com.legacyguard.module.authorization.dto.CaseDetailResponse;
import com.legacyguard.module.authorization.dto.CaseResponse;
import com.legacyguard.module.authorization.dto.CreateCaseRequest;
import com.legacyguard.module.authorization.dto.DisputeRequest;
import com.legacyguard.module.authorization.service.AuthorizationCaseService;
import com.legacyguard.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 授权案件接口（用户端）。
 *
 * <p>这里没有一个"直接改状态"的接口。每个动作对应一个语义明确的方法
 * （提交、取消、异议、撤销），状态由服务端根据当前状态推导，
 * 客户端无法指定目标状态。
 */
@RestController
@RequestMapping("/api/authorizations")
@Validated
@Tag(name = "授权案件", description = "联系人发起申请，所有者处理异议与撤销")
public class AuthorizationController {

    private final AuthorizationCaseService caseService;

    public AuthorizationController(AuthorizationCaseService caseService) {
        this.caseService = caseService;
    }

    @PostMapping
    @Operation(summary = "创建授权申请（草稿）", description = """
            只有同时满足以下条件才能创建：

            1. 你的联系人关系已生效（ACTIVE）
            2. 资产所有者已通过失联核验（REVIEWING）
            3. 所选资产确实属于该所有者
            4. 所选资产未被其他进行中的案件占用
            """)
    public ResponseEntity<ApiResponse<CaseDetailResponse>> create(
            @Valid @RequestBody CreateCaseRequest request) {

        CaseDetailResponse created = caseService.create(CurrentUser.get(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/applicable-assets")
    @Operation(summary = "可申请资产与资格", description = """
            发起申请前查询：这条联系人关系当前能否申请、该所有者名下有哪些资产可选。

            只返回资产元数据，不返回内容或密文；不可申请时资产列表为空并给出原因。
            真正的五道校验在创建接口里重新执行，这里的结论仅用于界面展示。
            """)
    public ApiResponse<ApplicableAssetsResponse> applicableAssets(
            @RequestParam @NotNull(message = "联系人关系 ID 不能为空") Long contactId) {

        return ApiResponse.success(caseService.applicableAssets(CurrentUser.get(), contactId));
    }

    @GetMapping
    @Operation(summary = "我相关的案件", description = "作为所有者被申请的，以及作为申请人发起的。")
    public ApiResponse<PageResult<CaseResponse>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码从 1 开始") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少 1 条")
            @Max(value = 100, message = "每页最多 100 条") long size) {
        return ApiResponse.success(caseService.listMine(CurrentUser.get(), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "案件详情", description = "含被覆盖的资产与完整审批链。")
    public ApiResponse<CaseDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(caseService.detail(CurrentUser.get(), id));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "提交审核", description = "DRAFT → PENDING_REVIEW。")
    public ApiResponse<Void> submit(@PathVariable Long id) {
        caseService.submit(CurrentUser.get(), id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消案件", description = """
            所有者或申请人都可以取消，但只能取消尚未生效的案件。

            已生效的案件请使用撤销接口 —— 两者的语义不同：
            取消是"流程没走完"，撤销是"已经给出去的再收回来"。
            """)
    public ApiResponse<Void> cancel(@PathVariable Long id,
                                    @RequestBody(required = false) CancelCaseRequest request) {
        caseService.cancel(CurrentUser.get(), id, request == null ? null : request.reason());
        return ApiResponse.success();
    }

    @PostMapping("/{id}/disputes")
    @Operation(summary = "提交异议", description = """
            仅等待期内的案件可以提异议。提交成功后案件转入 DISPUTED，流程暂停。

            这是审批与生效之间的缓冲：审核通过不等于最终生效。
            """)
    public ApiResponse<Void> raiseDispute(@PathVariable Long id,
                                          @Valid @RequestBody DisputeRequest request) {
        caseService.raiseDispute(CurrentUser.get(), id, request);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/revoke")
    @Operation(summary = "撤销已生效的授权", description = """
            **仅资产所有者可调用。**

            撤销立即生效：下一次访问被授权资产时就会返回 403，
            因为访问判定没有任何缓存。
            """)
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        caseService.revoke(CurrentUser.get(), id);
        return ApiResponse.success();
    }
}
