package com.legacyguard.infra.outbox;

import com.legacyguard.config.RabbitConfig;
import com.legacyguard.infra.mq.LegacyEvent;
import com.legacyguard.infra.outbox.domain.OutboxEvent;
import com.legacyguard.infra.outbox.mapper.OutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 发件箱发布器：把 {@code outbox_event} 里的待发事件投递到 RabbitMQ。
 *
 * <p>执行流程（每条事件）：
 * <ol>
 *   <li>条件更新"抢占"（把 {@code next_retry_at} 推到未来 60 秒作为租约）</li>
 *   <li>抢到才发送，否则说明别的实例正在处理它</li>
 *   <li><b>同步等待 Publisher Confirm</b>：只有 Broker 回 ack 才算成功</li>
 *   <li>成功 → {@code PUBLISHED}；失败 → 退回 {@code NEW} 并指数退避</li>
 * </ol>
 *
 * <p><b>为什么必须等 confirm</b>：{@code convertAndSend} 返回不代表 Broker 收到。
 * 不等确认就把状态改成 PUBLISHED，等于把"可能丢消息"伪装成"发送成功"。
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    /** 租约时长：抢占后 60 秒内其他实例不会重复处理 */
    private static final int LEASE_SECONDS = 60;

    /** 超过这个次数仍然发不出去，转人工处理 */
    private static final int MAX_RETRY = 5;

    /** 等待 Broker 确认的超时 */
    private static final int CONFIRM_TIMEOUT_SECONDS = 5;

    private static final int BATCH = 50;

    /** 指数退避：30s → 2min → 10min → 1h */
    private static final long[] BACKOFF_SECONDS = {30, 120, 600, 3600};

    private final OutboxEventMapper outboxMapper;
    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventMapper outboxMapper, RabbitTemplate rabbitTemplate) {
        this.outboxMapper = outboxMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 扫描并发布一批待发事件。
     *
     * @return 成功发布的事件数
     */
    public int publishPending() {
        LocalDateTime now = LocalDateTime.now();
        long lastId = 0L;
        int published = 0;

        while (true) {
            List<OutboxEvent> batch = outboxMapper.selectPending(now, lastId, BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (OutboxEvent event : batch) {
                lastId = event.getId();
                if (publishOne(event, now)) {
                    published++;
                }
            }
        }
        return published;
    }

    private boolean publishOne(OutboxEvent event, LocalDateTime now) {
        // 抢占：抢不到说明别的实例已领走，或刚被别人处理过
        int claimed = outboxMapper.claim(event.getId(), now, now.plusSeconds(LEASE_SECONDS));
        if (claimed == 0) {
            return false;
        }

        try {
            CorrelationData correlation = new CorrelationData(event.getEventId());
            LegacyEvent message = new LegacyEvent(
                    event.getEventId(),
                    event.getEventType(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getPayload(),
                    event.getCreatedAt());

            rabbitTemplate.convertAndSend(RabbitConfig.EVENT_EXCHANGE, event.getEventType(),
                    message, correlation);

            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (confirm == null || !confirm.isAck()) {
                String reason = confirm == null ? "未收到确认" : confirm.getReason();
                throw new IllegalStateException("Broker 未确认接收: " + reason);
            }

            outboxMapper.markPublished(event.getId(), LocalDateTime.now());
            log.info("事件已投递 eventId={} type={}", event.getEventId(), event.getEventType());
            return true;

        } catch (Exception e) {
            handleFailure(event, e);
            return false;
        }
    }

    /**
     * 发布失败：退避重试，超过上限转 FAILED 等待人工处理。
     *
     * <p>注意这里<b>不抛异常</b>：一条事件发不出去不应该让整批扫描中断，
     * 后面的事件还等着发。
     */
    private void handleFailure(OutboxEvent event, Exception e) {
        LocalDateTime now = LocalDateTime.now();
        int nextRetry = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;

        if (nextRetry >= MAX_RETRY) {
            outboxMapper.markFailed(event.getId(), "FAILED", now, truncate(e.toString()), now);
            log.error("事件发布连续失败，转入人工处理 eventId={} type={} retry={}",
                    event.getEventId(), event.getEventType(), nextRetry, e);
        } else {
            long delay = BACKOFF_SECONDS[Math.min(nextRetry - 1, BACKOFF_SECONDS.length - 1)];
            outboxMapper.markFailed(event.getId(), "NEW", now.plusSeconds(delay), truncate(e.toString()), now);
            log.warn("事件发布失败，{} 秒后重试 eventId={} retry={} cause={}",
                    delay, event.getEventId(), nextRetry, e.toString());
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
