package com.legacyguard.infra.outbox;

import lombok.Getter;
import lombok.Setter;

/**
 * 通知类事件的载荷。
 *
 * <p>消费者拿到它就能直接写站内信，不需要再回查业务表 ——
 * 事件自解释，避免消费者因为读不到最新状态而处理失败。
 *
 * <p><b>载荷里不能出现任何敏感内容</b>：这个 JSON 会落在 {@code outbox_event.payload}
 * 里，也可能出现在死信队列和日志中。标题与正文都是面向用户的措辞，不含资产明文。
 */
@Getter
@Setter
public class NotificationPayload {

    /** 接收者用户 ID */
    private Long receiverId;

    /** 站内信类型，见 NotificationType */
    private String bizType;

    /** 关联业务对象 ID */
    private Long bizId;

    private String title;

    private String content;

    /** 幂等键，同一件事只投一条 */
    private String dedupKey;

    public static NotificationPayload of(Long receiverId, String bizType, Long bizId,
                                         String title, String content, String dedupKey) {
        NotificationPayload payload = new NotificationPayload();
        payload.setReceiverId(receiverId);
        payload.setBizType(bizType);
        payload.setBizId(bizId);
        payload.setTitle(title);
        payload.setContent(content);
        payload.setDedupKey(dedupKey);
        return payload;
    }
}
