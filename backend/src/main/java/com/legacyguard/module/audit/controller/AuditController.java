package com.legacyguard.module.audit.controller;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.module.audit.dto.AuditLogView;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 安全审计查询。
 *
 * <p><b>这个接口最关键的一行是 {@code actorScope}：</b>
 * 身份由服务端决定，而不是客户端传参。
 *
 * <pre>
 *   普通用户 → 强制只查自己的记录（actorId = 当前用户）
 *   管理员   → 可以按任意条件查全部
 * </pre>
 *
 * <p>如果让客户端传 {@code actorId}，任何人都能翻别人的操作历史 ——
 * 这恰恰是审计系统最不该出现的问题。
 */
@RestController
@RequestMapping("/api/audit/logs")
@Validated
@Tag(name = "安全审计", description = "查询操作审计记录")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "查询审计记录", description = """
            普通用户只能看到自己的操作记录；管理员可以按条件查询全部。

            可按动作类型、对象类型、对象 ID 与时间范围筛选。
            """)
    public ApiResponse<PageResult<AuditLogView>> query(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码从 1 开始") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少 1 条")
            @Max(value = 100, message = "每页最多 100 条") long size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) Long objectId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        // 身份范围由服务端决定：普通用户永远只能看自己的；
        // 管理员传 null 表示不限制操作人。
        // 普通用户即便传了别人的 objectId，也只会看到"自己在该对象上的操作"。
        Long actorScope = CurrentUser.isAdmin() ? null : CurrentUser.id();

        return ApiResponse.success(
                auditService.query(actorScope, action, objectType, objectId, from, to, page, size));
    }
}
