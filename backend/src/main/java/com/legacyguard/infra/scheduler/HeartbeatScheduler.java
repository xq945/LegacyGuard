package com.legacyguard.infra.scheduler;

import com.legacyguard.module.heartbeat.service.HeartbeatService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 状态确认相关定时任务。
 *
 * <p>两个任务都不带"当前时间对不对"的判断，而是把「现在」交给 SQL 里的
 * {@code WHERE next_deadline_at <= NOW()} 去判断。这样即使系统重启、
 * 任务漏跑几轮，下次扫描也会把该处理的记录一并处理掉 ——
 * 任务本身无状态，不需要补偿逻辑。
 */
@Component
@ConditionalOnProperty(name = "legacyguard.job.enabled", havingValue = "true", matchIfMissing = true)
public class HeartbeatScheduler {

    private final HeartbeatService heartbeatService;
    private final JobExecutor jobExecutor;

    public HeartbeatScheduler(HeartbeatService heartbeatService, JobExecutor jobExecutor) {
        this.heartbeatService = heartbeatService;
        this.jobExecutor = jobExecutor;
    }

    /**
     * 到期扫描：把逾期未确认的用户推进到 REMINDING。
     */
    @Scheduled(fixedDelayString = "${legacyguard.job.heartbeat-scan-ms:60000}",
            initialDelayString = "${legacyguard.job.initial-delay-ms:15000}")
    public void scanDue() {
        jobExecutor.run("heartbeatDueScan", heartbeatService::scanDue);
    }

    /**
     * 提醒扫描：对提醒中且间隔已到的用户发下一轮提醒，轮次耗尽转入人工核验。
     */
    @Scheduled(fixedDelayString = "${legacyguard.job.heartbeat-remind-ms:60000}",
            initialDelayString = "${legacyguard.job.initial-delay-ms:20000}")
    public void scanRemind() {
        jobExecutor.run("heartbeatRemindScan", heartbeatService::scanRemind);
    }
}
