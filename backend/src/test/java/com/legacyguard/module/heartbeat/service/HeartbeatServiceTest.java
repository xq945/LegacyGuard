package com.legacyguard.module.heartbeat.service;

import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.common.exception.StateConflictException;
import com.legacyguard.common.web.ClientIpResolver;
import com.legacyguard.infra.outbox.OutboxService;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.module.heartbeat.config.HeartbeatProperties;
import com.legacyguard.module.heartbeat.domain.HeartbeatConfig;
import com.legacyguard.module.heartbeat.domain.HeartbeatStatus;
import com.legacyguard.module.heartbeat.dto.HeartbeatConfigRequest;
import com.legacyguard.module.heartbeat.mapper.HeartbeatConfigMapper;
import com.legacyguard.module.heartbeat.mapper.HeartbeatRecordMapper;
import com.legacyguard.security.AuthPrincipal;
import com.legacyguard.module.user.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 状态确认服务测试。
 *
 * <p>重点覆盖两件事：
 * <ol>
 *   <li>并发下旧任务失效（cycleNo 条件）</li>
 *   <li>确认的连锁反应（作废未生效案件）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    private static final Long USER_ID = 100L;
    private static final Long ADMIN_ID = 1L;

    @Mock
    private HeartbeatConfigMapper configMapper;
    @Mock
    private HeartbeatRecordMapper recordMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private ClientIpResolver clientIpResolver;
    @Mock
    private ObjectProvider<HeartbeatRecoveryHook> hookProvider;
    @Mock
    private HeartbeatRecoveryHook recoveryHook;
    @Mock
    private OutboxService outboxService;

    private HeartbeatService service;
    private AuthPrincipal user;
    private AuthPrincipal admin;

    @BeforeEach
    void setUp() {
        HeartbeatProperties props = new HeartbeatProperties(90, 7, 365, 3, 10, 72);
        service = new HeartbeatService(configMapper, recordMapper, auditService,
                clientIpResolver, props, hookProvider, outboxService);
        user = new AuthPrincipal(USER_ID, "user", UserRole.USER, "jti");
        admin = new AuthPrincipal(ADMIN_ID, "admin", UserRole.ADMIN, "jti-a");

        // ifAvailable(Consumer) 是 void 方法，必须用 doAnswer 而不是 when
        lenient().doAnswer(inv -> {
            Consumer<HeartbeatRecoveryHook> consumer = inv.getArgument(0);
            consumer.accept(recoveryHook);
            return null;
        }).when(hookProvider).ifAvailable(ArgumentMatchers.<Consumer<HeartbeatRecoveryHook>>any());
    }

    private HeartbeatConfig config(String status, int cycleNo, int version) {
        HeartbeatConfig c = new HeartbeatConfig();
        c.setUserId(USER_ID);
        c.setStatus(status);
        c.setCycleNo(cycleNo);
        c.setVersion(version);
        c.setCycleDays(90);
        c.setReminderRounds(3);
        c.setReminderIntervalHours(72);
        c.setReminderCount(0);
        c.setLastConfirmAt(LocalDateTime.now().minusDays(90));
        c.setNextDeadlineAt(LocalDateTime.now().minusDays(1));
        return c;
    }

    // ==================================================================
    // 配置
    // ==================================================================

    @Test
    @DisplayName("首次访问自动创建默认配置，初始状态 ACTIVE")
    void createsDefaultConfigOnFirstAccess() {
        when(configMapper.selectById(USER_ID)).thenReturn(null, config(HeartbeatStatus.ACTIVE, 1, 0));
        when(configMapper.insert(any(HeartbeatConfig.class))).thenReturn(1);

        service.getStatus(user);

        ArgumentCaptor<HeartbeatConfig> captor = ArgumentCaptor.forClass(HeartbeatConfig.class);
        verify(configMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(HeartbeatStatus.ACTIVE);
        assertThat(captor.getValue().getCycleNo()).isEqualTo(1);
        assertThat(captor.getValue().getCycleDays()).isEqualTo(90);
    }

    @Test
    @DisplayName("确认周期超出允许范围时拒绝，且在校验阶段就返回、不查库")
    void rejectsOutOfRangeCycleDays() {
        assertThatThrownBy(() -> service.updateConfig(user,
                new HeartbeatConfigRequest(1, 3, 72)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        // 参数不合法时连配置都不该读 —— 校验发生在查库之前
        verify(configMapper, never()).selectById(any());
        verify(configMapper, never()).updateById(any(HeartbeatConfig.class));
    }

    // ==================================================================
    // 确认
    // ==================================================================

    @Test
    @DisplayName("确认后周期号 +1，并写入确认流水")
    void confirmAdvancesCycle() {
        HeartbeatConfig before = config(HeartbeatStatus.REMINDING, 5, 7);
        when(configMapper.selectById(USER_ID)).thenReturn(before, config(HeartbeatStatus.ACTIVE, 6, 8));
        when(configMapper.confirm(eq(USER_ID), eq(7), any())).thenReturn(1);

        service.confirm(user, "1.2.3.4");

        verify(configMapper).confirm(eq(USER_ID), eq(7), any());

        ArgumentCaptor<com.legacyguard.module.heartbeat.domain.HeartbeatRecord> captor =
                ArgumentCaptor.forClass(com.legacyguard.module.heartbeat.domain.HeartbeatRecord.class);
        verify(recordMapper).insert(captor.capture());
        // 记录的是被完成的那个周期号（确认前的值）
        assertThat(captor.getValue().getCycleNo()).isEqualTo(5);
        assertThat(captor.getValue().getConfirmType()).isEqualTo("MANUAL");
    }

    @Test
    @DisplayName("确认会触发连锁处理，作废未生效的授权案件")
    void confirmTriggersRecoveryHook() {
        when(configMapper.selectById(USER_ID)).thenReturn(
                config(HeartbeatStatus.REVIEWING, 3, 2), config(HeartbeatStatus.ACTIVE, 4, 3));
        when(configMapper.confirm(any(), any(), any())).thenReturn(1);

        service.confirm(user, "1.2.3.4");

        verify(recoveryHook).onRecovered(USER_ID);
    }

    @Test
    @DisplayName("并发确认时后到者收到冲突，且不写流水")
    void concurrentConfirmYieldsConflict() {
        when(configMapper.selectById(USER_ID)).thenReturn(config(HeartbeatStatus.ACTIVE, 5, 7));
        // 读到 version=7 之后，条件更新影响 0 行
        when(configMapper.confirm(eq(USER_ID), eq(7), any())).thenReturn(0);

        assertThatThrownBy(() -> service.confirm(user, "1.2.3.4"))
                .isInstanceOf(StateConflictException.class);

        verify(recordMapper, never()).insert(any(com.legacyguard.module.heartbeat.domain.HeartbeatRecord.class));
    }

    // ==================================================================
    // 管理端核验
    // ==================================================================

    @Test
    @DisplayName("核验通过后进入 REVIEWING，联系人才能申请授权")
    void verifyMovesToReviewing() {
        when(configMapper.selectById(USER_ID)).thenReturn(config(HeartbeatStatus.VERIFYING, 2, 4));
        when(configMapper.markReviewing(eq(USER_ID), eq(2), any())).thenReturn(1);

        service.verify(admin, USER_ID);

        verify(configMapper).markReviewing(eq(USER_ID), eq(2), any());
    }

    @Test
    @DisplayName("非 VERIFYING 状态不能被核验 —— 超时不会自己走到授权")
    void cannotVerifyUnlessPending() {
        when(configMapper.selectById(USER_ID)).thenReturn(config(HeartbeatStatus.REMINDING, 2, 4));

        assertThatThrownBy(() -> service.verify(admin, USER_ID))
                .isInstanceOf(StateConflictException.class)
                .hasMessageContaining("VERIFYING");

        verify(configMapper, never()).markReviewing(anyLong(), anyInt(), any());
    }

    // ==================================================================
    // 定时扫描
    // ==================================================================

    @Test
    @DisplayName("到期扫描把逾期用户推进到 REMINDING")
    void scanDueAdvancesToReminding() {
        HeartbeatConfig due = config(HeartbeatStatus.ACTIVE, 2, 5);
        when(configMapper.selectDue(any(), eq(0L), anyInt())).thenReturn(List.of(due));
        when(configMapper.startReminding(eq(USER_ID), eq(2), any())).thenReturn(1);

        int advanced = service.scanDue();

        assertThat(advanced).isEqualTo(1);
        verify(configMapper).startReminding(eq(USER_ID), eq(2), any());
    }

    @Test
    @DisplayName("扫描时条件更新失败（用户刚好确认）不影响其他记录")
    void scanSkipsAlreadyConfirmed() {
        HeartbeatConfig stale = config(HeartbeatStatus.ACTIVE, 2, 5);
        when(configMapper.selectDue(any(), eq(0L), anyInt())).thenReturn(List.of(stale));
        // 用户已确认，周期号变了，条件更新影响 0 行
        when(configMapper.startReminding(eq(USER_ID), eq(2), any())).thenReturn(0);

        int advanced = service.scanDue();

        assertThat(advanced).isZero();
    }

    @Test
    @DisplayName("最后一轮提醒发出后转入 VERIFYING")
    void lastReminderMovesToVerifying() {
        HeartbeatConfig cfg = config(HeartbeatStatus.REMINDING, 3, 9);
        cfg.setReminderCount(2);       // 已发 2 轮，本次是第 3 轮（= 上限）
        cfg.setReminderRounds(3);

        when(configMapper.selectNeedRemind(any(), eq(0L), anyInt())).thenReturn(List.of(cfg));
        when(configMapper.increaseReminder(eq(USER_ID), eq(3), any())).thenReturn(1);

        int processed = service.scanRemind();

        assertThat(processed).isEqualTo(1);
        verify(configMapper).increaseReminder(eq(USER_ID), eq(3), any());
        verify(configMapper).markVerifying(eq(USER_ID), eq(3), any());
    }
}
