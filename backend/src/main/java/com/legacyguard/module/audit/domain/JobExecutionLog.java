package com.legacyguard.module.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 定时任务执行日志，对应 {@code job_execution_log}。
 *
 * <p>没有这张表，定时任务就是黑盒：跑没跑、跑了多少、有没有失败，全都无从得知。
 * 有了它，管理端可以直接回答"心跳扫描上一次执行是什么时候、处理了几条、失败几条"。
 *
 * <p>与 {@code audit_log} 的区别：审计记业务操作，这里记<b>系统自身</b>的运行状况，
 * 属于运维可观测性而非安全留痕。
 */
@Getter
@Setter
@TableName("job_execution_log")
public class JobExecutionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名，如 heartbeatDueScan */
    private String jobName;

    /** 实例标识，多实例部署时用于区分 */
    private String instanceId;

    /** SUCCESS / PARTIAL / FAILED */
    private String status;

    /** 扫描到的记录数 */
    private Integer scannedCount;

    /** 成功处理数 */
    private Integer processedCount;

    /** 处理失败数 */
    private Integer failedCount;

    private String errorMsg;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Integer durationMs;
}
