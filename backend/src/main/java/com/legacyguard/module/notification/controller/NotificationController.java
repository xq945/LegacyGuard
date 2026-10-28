package com.legacyguard.module.notification.controller;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.module.notification.service.NotificationService;
import com.legacyguard.module.notification.service.NotificationView;
import com.legacyguard.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 通知中心接口。
 *
 * <p>所有查询都以 {@link CurrentUser} 的身份为准，接收者 ID 由服务端注入 ——
 * 传别人的通知 ID 进来只会得到 404。
 */
@RestController
@RequestMapping("/api/notifications")
@Validated
@Tag(name = "通知中心", description = "站内信查询与已读标记")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "我的通知列表")
    public ApiResponse<PageResult<NotificationView>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码从 1 开始") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少 1 条")
            @Max(value = 100, message = "每页最多 100 条") long size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ApiResponse.success(notificationService.list(CurrentUser.id(), page, size, unreadOnly));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读数量")
    public ApiResponse<Map<String, Long>> unreadCount() {
        return ApiResponse.success(Map.of("count", notificationService.countUnread(CurrentUser.id())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "通知详情")
    public ApiResponse<NotificationView> detail(@PathVariable Long id) {
        return ApiResponse.success(notificationService.detail(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记单条已读", description = "重复标记不会报错。")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(CurrentUser.id(), id);
        return ApiResponse.success();
    }

    @PostMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public ApiResponse<Map<String, Integer>> markAllRead() {
        return ApiResponse.success(Map.of("updated", notificationService.markAllRead(CurrentUser.id())));
    }
}
