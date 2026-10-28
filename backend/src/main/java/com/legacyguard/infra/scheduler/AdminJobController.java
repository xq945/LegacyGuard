package com.legacyguard.infra.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.infra.outbox.OutboxPublisher;
import com.legacyguard.infra.outbox.domain.OutboxEvent;
import com.legacyguard.infra.outbox.mapper.OutboxEventMapper;
import com.legacyguard.module.audit.domain.JobExecutionLog;
import com.legacyguard.module.audit.mapper.JobExecutionLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 管理端：任务与消息的可观测性。
 *
 * <p>需求要求"任务状态、执行次数和最后执行时间可以查询"，
 * 以及"通知失败任务可以查询"。这两个需求在这里落地。
 */
@RestController
@RequestMapping("/api/admin/jobs")
@Validated
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "管理端-任务与消息", description = "定时任务执行记录、发件箱积压与失败事件")
public class AdminJobController {

    private final JobExecutionLogMapper jobLogMapper;
    private final OutboxEventMapper outboxMapper;
    private final OutboxPublisher outboxPublisher;

    public AdminJobController(JobExecutionLogMapper jobLogMapper,
                              OutboxEventMapper outboxMapper,
                              OutboxPublisher outboxPublisher) {
        this.jobLogMapper = jobLogMapper;
        this.outboxMapper = outboxMapper;
        this.outboxPublisher = outboxPublisher;
    }

    @GetMapping("/executions")
    @Operation(summary = "定时任务执行记录")
    public ApiResponse<PageResult<JobExecutionLog>> executions(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码从 1 开始") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少 1 条")
            @Max(value = 100, message = "每页最多 100 条") long size,
            @RequestParam(required = false) String jobName) {

        Page<JobExecutionLog> paging = new Page<>(page, size);
        LambdaQueryWrapper<JobExecutionLog> query = new LambdaQueryWrapper<JobExecutionLog>()
                .eq(jobName != null && !jobName.isBlank(), JobExecutionLog::getJobName, jobName)
                .orderByDesc(JobExecutionLog::getId);

        IPage<JobExecutionLog> result = jobLogMapper.selectPage(paging, query);
        return ApiResponse.success(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @GetMapping("/outbox/failed")
    @Operation(summary = "发送失败的事件", description = "连续重试超过上限、已转入人工处理的事件。")
    public ApiResponse<PageResult<OutboxEvent>> failedEvents(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码从 1 开始") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少 1 条")
            @Max(value = 100, message = "每页最多 100 条") long size) {

        Page<OutboxEvent> paging = new Page<>(page, size);
        LambdaQueryWrapper<OutboxEvent> query = new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getStatus, "FAILED")
                .orderByDesc(OutboxEvent::getId);

        IPage<OutboxEvent> result = outboxMapper.selectPage(paging, query);
        return ApiResponse.success(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @GetMapping("/outbox/summary")
    @Operation(summary = "发件箱概况", description = "待发与失败的数量，用于判断消息链路是否健康。")
    public ApiResponse<Map<String, Long>> outboxSummary() {
        return ApiResponse.success(Map.of(
                "pending", outboxMapper.countPending(),
                "failed", outboxMapper.countFailed()));
    }

    /**
     * 重投一条失败事件。
     *
     * <p>只是把它退回 NEW 并清零重试次数，实际投递仍由发布器按正常流程完成 ——
     * 不会因为"人工点了重投"就跳过任何可靠性环节。
     */
    @PostMapping("/outbox/{id}/retry")
    @Operation(summary = "重投失败事件", description = "退回待发状态并清零重试次数。")
    public ApiResponse<Void> retryEvent(@PathVariable Long id) {
        OutboxEvent event = outboxMapper.selectById(id);
        if (event == null) {
            return ApiResponse.success();
        }
        event.setStatus("NEW");
        event.setRetryCount(0);
        event.setNextRetryAt(LocalDateTime.now());
        event.setLastError(null);
        event.setUpdatedAt(LocalDateTime.now());
        outboxMapper.updateById(event);
        return ApiResponse.success();
    }

    @PostMapping("/outbox/publish-now")
    @Operation(summary = "立即触发一次发布扫描", description = "运维排查用，等价于调度器跑一轮。")
    public ApiResponse<Map<String, Integer>> publishNow() {
        return ApiResponse.success(Map.of("published", outboxPublisher.publishPending()));
    }
}
