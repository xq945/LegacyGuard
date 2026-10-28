package com.legacyguard.infra.scheduler;

import com.legacyguard.module.audit.domain.JobExecutionLog;
import com.legacyguard.module.audit.mapper.JobExecutionLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.function.IntSupplier;

/**
 * 定时任务执行包装。
 *
 * <p>每次执行都写一条 {@code job_execution_log}，让任务不再是黑盒 ——
 * 管理端可以回答"上一次扫描是什么时候、处理了几条、失败了几条"。
 *
 * <p><b>异常必须在这里被吃掉。</b>定时任务抛出未捕获异常，虽然不会让调度器停止，
 * 但调度线程的日志里只会留下堆栈，没有任何结构化的执行记录。
 * 包一层之后，失败也是一条可查询的记录。
 */
@Component
public class JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_LENGTH = 500;

    private final JobExecutionLogMapper logMapper;
    private final String instanceId;

    public JobExecutor(JobExecutionLogMapper logMapper) {
        this.logMapper = logMapper;
        this.instanceId = resolveInstanceId();
    }

    /**
     * 执行一个任务并记录结果。
     *
     * @param jobName 任务名
     * @param task    任务逻辑，返回处理条数
     * @return 处理条数；失败时返回 -1
     */
    public int run(String jobName, IntSupplier task) {
        LocalDateTime startedAt = LocalDateTime.now();
        long startNanos = System.nanoTime();

        JobExecutionLog entry = new JobExecutionLog();
        entry.setJobName(jobName);
        entry.setInstanceId(instanceId);
        entry.setStartedAt(startedAt);
        entry.setScannedCount(0);
        entry.setProcessedCount(0);
        entry.setFailedCount(0);

        int processed = -1;
        try {
            processed = task.getAsInt();
            entry.setStatus(STATUS_SUCCESS);
            entry.setProcessedCount(processed);
            entry.setScannedCount(processed);
        } catch (RuntimeException e) {
            entry.setStatus(STATUS_FAILED);
            entry.setFailedCount(1);
            entry.setErrorMsg(truncate(e.toString()));
            log.error("定时任务执行失败 job={}", jobName, e);
        } finally {
            entry.setFinishedAt(LocalDateTime.now());
            entry.setDurationMs((int) ((System.nanoTime() - startNanos) / 1_000_000));
            try {
                logMapper.insert(entry);
            } catch (RuntimeException e) {
                // 连日志都写不进去（比如数据库挂了），只能退化为本地日志
                log.error("任务执行日志写入失败 job={}", jobName, e);
            }
        }

        return processed;
    }

    private String resolveInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH ? value : value.substring(0, MAX_ERROR_LENGTH);
    }
}
