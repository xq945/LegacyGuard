package com.legacyguard.module.notification.service;

import com.legacyguard.module.notification.domain.Notification;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 站内信展示对象。
 */
@Schema(description = "站内信")
public record NotificationView(

        @Schema(description = "通知 ID")
        Long id,

        @Schema(description = "类型")
        String bizType,

        @Schema(description = "关联业务对象 ID")
        Long bizId,

        @Schema(description = "标题")
        String title,

        @Schema(description = "正文")
        String content,

        @Schema(description = "是否已读")
        boolean read,

        @Schema(description = "已读时间，未读为 null")
        LocalDateTime readAt,

        @Schema(description = "发送时间")
        LocalDateTime createdAt
) {

    public static NotificationView from(Notification n) {
        return new NotificationView(
                n.getId(),
                n.getBizType(),
                n.getBizId(),
                n.getTitle(),
                n.getContent(),
                n.getReadAt() != null,
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}
