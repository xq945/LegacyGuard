package com.legacyguard.infra.scheduler;

import com.legacyguard.module.audit.domain.JobExecutionLog;
import com.legacyguard.module.audit.mapper.JobExecutionLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务执行包装测试。
 */
@ExtendWith(MockitoExtension.class)
class JobExecutorTest {

    @Mock
    private JobExecutionLogMapper logMapper;

    private JobExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new JobExecutor(logMapper);
    }

    @Test
    @DisplayName("执行成功时记录处理条数与耗时")
    void recordsSuccess() {
        int processed = executor.run("heartbeatDueScan", () -> 7);

        assertThat(processed).isEqualTo(7);

        ArgumentCaptor<JobExecutionLog> captor = ArgumentCaptor.forClass(JobExecutionLog.class);
        verify(logMapper).insert(captor.capture());
        JobExecutionLog entry = captor.getValue();

        assertThat(entry.getJobName()).isEqualTo("heartbeatDueScan");
        assertThat(entry.getStatus()).isEqualTo("SUCCESS");
        assertThat(entry.getProcessedCount()).isEqualTo(7);
        assertThat(entry.getStartedAt()).isNotNull();
        assertThat(entry.getFinishedAt()).isNotNull();
        assertThat(entry.getDurationMs()).isNotNull();
    }

    @Test
    @DisplayName("任务抛异常时记录 FAILED，且不把异常抛给调度线程")
    void recordsFailureWithoutRethrowing() {
        int processed = executor.run("waitPeriodExpireScan", () -> {
            throw new IllegalStateException("数据库不可用");
        });

        assertThat(processed).isEqualTo(-1);

        ArgumentCaptor<JobExecutionLog> captor = ArgumentCaptor.forClass(JobExecutionLog.class);
        verify(logMapper).insert(captor.capture());
        JobExecutionLog entry = captor.getValue();

        assertThat(entry.getStatus()).isEqualTo("FAILED");
        assertThat(entry.getErrorMsg()).contains("数据库不可用");
    }

    @Test
    @DisplayName("连任务日志都写不进去时也不能让调度线程崩溃")
    void logWriteFailureIsSwallowed() {
        when(logMapper.insert(any(JobExecutionLog.class))).thenThrow(new RuntimeException("DB down"));

        int processed = executor.run("outboxPublishScan", () -> 3);

        // 任务本身跑完了，只是日志没记下
        assertThat(processed).isEqualTo(3);
    }
}
