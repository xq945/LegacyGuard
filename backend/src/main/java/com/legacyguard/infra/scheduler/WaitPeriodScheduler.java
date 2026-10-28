package com.legacyguard.infra.scheduler;

import com.legacyguard.module.authorization.service.AuthorizationCaseService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 授权等待期到点扫描。
 *
 * <p><b>这是"审批通过"变成"授权生效"的唯一自动路径</b>，
 * 而它并不直接生效 —— 每处理一个案件都要重新复核全部条件
 * （所有者是否仍失联、关系是否仍有效、资产是否仍在、有无新异议）。
 * 任何一条不满足，案件就离开等待期而不是继续生效。
 */
@Component
@ConditionalOnProperty(name = "legacyguard.job.enabled", havingValue = "true", matchIfMissing = true)
public class WaitPeriodScheduler {

    private final AuthorizationCaseService caseService;
    private final JobExecutor jobExecutor;

    public WaitPeriodScheduler(AuthorizationCaseService caseService, JobExecutor jobExecutor) {
        this.caseService = caseService;
        this.jobExecutor = jobExecutor;
    }

    @Scheduled(fixedDelayString = "${legacyguard.job.wait-period-scan-ms:60000}",
            initialDelayString = "${legacyguard.job.initial-delay-ms:25000}")
    public void scanExpired() {
        jobExecutor.run("waitPeriodExpireScan", caseService::authorizeDueCases);
    }
}
