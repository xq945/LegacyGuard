package com.legacyguard.infra.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacyguard.infra.outbox.domain.OutboxEvent;
import com.legacyguard.infra.outbox.mapper.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 事务发件箱写入。
 *
 * <p>业务代码调用这里记录"发生了什么事"，真正的投递由 {@link OutboxPublisher} 完成。
 *
 * <p>写入与业务数据必须处于同一事务：这就是发件箱模式能保证"业务成功则事件必在"
 * 的原因。{@code REQUIRED} 传播级别在调用方已有事务时加入该事务；
 * 没有事务时（例如定时任务里的批量处理）会自己开一个 —— 此时事件与业务不再原子，
 * 但业务变更本身是条件更新，已经各自提交，这个取舍是可接受的。
 */
@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private static final String STATUS_NEW = "NEW";

    private final OutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 追加一条待发布事件。
     *
     * @param eventType     事件类型（同时作为 MQ 路由键）
     * @param aggregateType 聚合类型
     * @param aggregateId   聚合 ID
     * @param payload       载荷对象，会被序列化为 JSON
     */
    public void append(String eventType, String aggregateType, Long aggregateId, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // 载荷序列化失败属于编码错误，不该静默吞掉 —— 直接在开发/测试阶段暴露
            throw new IllegalStateException("事件载荷序列化失败 eventType=" + eventType, e);
        }

        LocalDateTime now = LocalDateTime.now();
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(json);
        event.setStatus(STATUS_NEW);
        event.setRetryCount(0);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        outboxMapper.insert(event);
        log.debug("事件已写入发件箱 eventId={} type={} aggregate={}:{}",
                event.getEventId(), eventType, aggregateType, aggregateId);
    }

    /**
     * 发送通知类事件的便捷方法。
     */
    public void notifyUser(String eventType, Long receiverId, String bizType, Long bizId,
                           String title, String content, String dedupKey) {
        append(eventType, aggregateTypeOf(eventType), bizId,
                NotificationPayload.of(receiverId, bizType, bizId, title, content, dedupKey));
    }

    /**
     * 从事件类型推出聚合类型：{@code case.submitted} → {@code CASE}。
     */
    private String aggregateTypeOf(String eventType) {
        int dot = eventType.indexOf('.');
        String prefix = dot > 0 ? eventType.substring(0, dot) : eventType;
        return prefix.toUpperCase();
    }
}
