package com.legacyguard.infra.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacyguard.config.RabbitConfig;
import com.legacyguard.infra.outbox.EventTypes;
import com.legacyguard.infra.mq.mapper.MessageConsumeRecordMapper;
import com.legacyguard.infra.outbox.NotificationPayload;
import com.legacyguard.module.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 事件处理：把业务事件落成站内信。
 *
 * <p>幂等记录与站内信写入<b>在同一事务</b>内完成：
 * 处理失败时两者一起回滚，下次重投还能正常处理；
 * 处理成功时记录留下，重投会被识别为重复并跳过。
 */
@Service
public class NotificationEventHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    private static final String STATUS_SUCCESS = "SUCCESS";

    private final MessageConsumeRecordMapper consumeMapper;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationEventHandler(MessageConsumeRecordMapper consumeMapper,
                                    NotificationService notificationService,
                                    ObjectMapper objectMapper) {
        this.consumeMapper = consumeMapper;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * 处理一条事件。重复消息会被静默跳过（不是错误）。
     */
    @Transactional
    public void handle(LegacyEvent event) {
        int firstTime = consumeMapper.tryRecord(event.eventId(), RabbitConfig.NOTIFY_QUEUE,
                STATUS_SUCCESS, LocalDateTime.now());

        if (firstTime == 0) {
            // 这条消息之前处理过：可能是 Broker 重投，也可能是发布器的租约到期重发
            log.info("重复消息，已跳过 eventId={} type={}", event.eventId(), event.eventType());
            return;
        }

        dispatch(event);
    }

    private void dispatch(LegacyEvent event) {
        // 心跳提醒事件不带接收者信息，需要回查；其余事件载荷自解释
        if (EventTypes.HEARTBEAT_VERIFYING.equals(event.eventType())) {
            // 仅在载荷里带了接收者时处理，避免消费者去猜业务状态
            log.debug("收到核验事件，无需站内信 eventId={}", event.eventId());
            return;
        }

        NotificationPayload payload;
        try {
            payload = objectMapper.readValue(event.payload(), NotificationPayload.class);
        } catch (Exception e) {
            // 载荷解析失败属于不可恢复错误，重投多少次都一样 → 直接抛出，进死信
            throw new IllegalStateException("事件载荷解析失败 eventId=" + event.eventId(), e);
        }

        if (payload.getReceiverId() == null) {
            log.warn("事件缺少接收者，已忽略 eventId={} type={}", event.eventId(), event.eventType());
            return;
        }

        notificationService.send(
                payload.getReceiverId(),
                payload.getBizType(),
                payload.getBizId(),
                payload.getTitle(),
                payload.getContent(),
                payload.getDedupKey());

        log.info("站内信已投递 receiver={} type={} eventId={}",
                payload.getReceiverId(), payload.getBizType(), event.eventId());
    }
}
