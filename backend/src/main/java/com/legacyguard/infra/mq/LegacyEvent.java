package com.legacyguard.infra.mq;

import java.time.LocalDateTime;

/**
 * 在 MQ 上传递的事件消息。
 *
 * <p>{@code eventId} 是消费者幂等的依据，从发件箱一路带过来 ——
 * 它是同一条业务事件在所有环节中的唯一标识。
 *
 * @param eventId       事件唯一标识（UUID）
 * @param eventType     事件类型，同时是路由键
 * @param aggregateType 聚合类型
 * @param aggregateId   聚合 ID
 * @param payload       载荷 JSON 字符串，由消费者按类型解析
 * @param occurredAt    事件发生时间
 */
public record LegacyEvent(
        String eventId,
        String eventType,
        String aggregateType,
        Long aggregateId,
        String payload,
        LocalDateTime occurredAt
) {
}
