package com.legacyguard.infra.scheduler;

import com.legacyguard.infra.outbox.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 发件箱发布扫描。
 *
 * <p>扫描频率比其它任务高得多（默认 2 秒），因此<b>不写 {@code job_execution_log}</b> ——
 * 每 2 秒一条记录，一天就是四万多条，很快就会把日志表变成负担，
 * 而其中绝大多数是"扫了个寂寞"。
 *
 * <p>这里改为只在确实发布了事件时打应用日志。需要观察 Outbox 积压情况，
 * 可以查 {@code outbox_event} 中 {@code status='NEW'} 的行数，
 * 或看管理端提供的失败事件列表。
 */
@Component
@ConditionalOnProperty(name = "legacyguard.job.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxPublisher outboxPublisher;

    public OutboxScheduler(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @Scheduled(fixedDelayString = "${legacyguard.job.outbox-scan-ms:2000}",
            initialDelayString = "${legacyguard.job.initial-delay-ms:10000}")
    public void publish() {
        try {
            int published = outboxPublisher.publishPending();
            if (published > 0) {
                log.debug("本轮发布 {} 条事件", published);
            }
        } catch (RuntimeException e) {
            // 发布器自身异常（例如数据库不可用）不能让调度线程中断
            log.error("发件箱扫描异常", e);
        }
    }
}
