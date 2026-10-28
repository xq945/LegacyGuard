package com.legacyguard.module.heartbeat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.common.exception.StateConflictException;
import com.legacyguard.common.web.ClientIpResolver;
import com.legacyguard.module.audit.domain.AuditAction;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.module.heartbeat.config.HeartbeatProperties;
import com.legacyguard.module.heartbeat.domain.HeartbeatConfig;
import com.legacyguard.module.heartbeat.domain.HeartbeatRecord;
import com.legacyguard.module.heartbeat.domain.HeartbeatStatus;
import com.legacyguard.module.heartbeat.dto.HeartbeatConfigRequest;
import com.legacyguard.module.heartbeat.dto.HeartbeatRecordResponse;
import com.legacyguard.module.heartbeat.dto.HeartbeatStatusResponse;
import com.legacyguard.module.heartbeat.mapper.HeartbeatConfigMapper;
import com.legacyguard.module.heartbeat.mapper.HeartbeatRecordMapper;
import com.legacyguard.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定期状态确认服务。
 *
 * <p>系统里所有"用户还在不在"的判断都以这里为准。它的推进逻辑有两个铁律：
 * <ol>
 *   <li><b>每次推进都带 {@code cycleNo} 条件</b> —— 用户一旦确认，周期号 +1，
 *       所有基于旧周期号的定时任务自然失效</li>
 *   <li><b>推进终点是 REVIEWING，不是授权</b> —— 从 ACTIVE 到 REVIEWING
 *       必须经过提醒、提醒耗尽、人工核验三道关，最后一关是人工的</li>
 * </ol>
 */
@Service
public class HeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);

    private static final String CONFIRM_TYPE_MANUAL = "MANUAL";
    private static final int SCAN_BATCH = 200;

    private final HeartbeatConfigMapper configMapper;
    private final HeartbeatRecordMapper recordMapper;
    private final AuditService auditService;
    private final ClientIpResolver clientIpResolver;
    private final HeartbeatProperties properties;
    private final ObjectProvider<HeartbeatRecoveryHook> recoveryHookProvider;

    public HeartbeatService(HeartbeatConfigMapper configMapper,
                            HeartbeatRecordMapper recordMapper,
                            AuditService auditService,
                            ClientIpResolver clientIpResolver,
                            HeartbeatProperties properties,
                            ObjectProvider<HeartbeatRecoveryHook> recoveryHookProvider) {
        this.configMapper = configMapper;
        this.recordMapper = recordMapper;
        this.auditService = auditService;
        this.clientIpResolver = clientIpResolver;
        this.properties = properties;
        this.recoveryHookProvider = recoveryHookProvider;
    }

    // ==================================================================
    // 查询与配置
    // ==================================================================

    /**
     * 查看自己的状态确认情况。首次访问时自动创建默认配置。
     */
    public HeartbeatStatusResponse getStatus(AuthPrincipal actor) {
        HeartbeatConfig config = requireOrCreate(actor.userId());
        return HeartbeatStatusResponse.from(config, LocalDateTime.now());
    }

    @Transactional
    public HeartbeatStatusResponse updateConfig(AuthPrincipal actor, HeartbeatConfigRequest request) {
        validateCycleDays(request.cycleDays());
        validateReminderRounds(request.reminderRounds());

        HeartbeatConfig config = requireOrCreate(actor.userId());

        HeartbeatConfig update = new HeartbeatConfig();
        update.setUserId(actor.userId());
        update.setCycleDays(request.cycleDays());
        update.setReminderRounds(request.reminderRounds());
        update.setReminderIntervalHours(request.reminderIntervalHours());
        update.setVersion(config.getVersion());
        update.setUpdatedAt(LocalDateTime.now());

        int rows = configMapper.updateById(update);
        if (rows == 0) {
            throw new StateConflictException("配置已被其他操作修改，请刷新后重试");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("cycleDays", request.cycleDays());
        detail.put("reminderRounds", request.reminderRounds());
        auditService.success(actor.userId(), actor.role(), AuditAction.HEARTBEAT_CONFIG_UPDATE,
                "HEARTBEAT", actor.userId(), detail);

        return HeartbeatStatusResponse.from(configMapper.selectById(actor.userId()), LocalDateTime.now());
    }

    // ==================================================================
    // 确认
    // ==================================================================

    /**
     * 完成一次状态确认。
     *
     * <p>这一步的连锁反应比看上去多：
     * <ol>
     *   <li>周期号 +1，截止时间往后推</li>
     *   <li>提醒计数清零，状态回到 ACTIVE</li>
     *   <li><b>通知授权模块作废所有未生效的案件</b> —— 人回来了，
     *       那些建立在"失联"前提上的申请就不再成立</li>
     * </ol>
     *
     * <p>已经生效（AUTHORIZED）的授权不受影响，只能由所有者主动撤销（docs/07 Q2）。
     */
    @Transactional
    public HeartbeatStatusResponse confirm(AuthPrincipal actor, String clientIp) {
        HeartbeatConfig config = requireOrCreate(actor.userId());
        LocalDateTime now = LocalDateTime.now();

        int rows = configMapper.confirm(actor.userId(), config.getVersion(), now);
        if (rows == 0) {
            // 同一秒内重复点击、或另一个请求刚改过配置
            throw new StateConflictException("状态刚刚已被更新，请刷新后再试");
        }

        HeartbeatRecord record = new HeartbeatRecord();
        record.setUserId(actor.userId());
        // 记录的是被完成的那一个周期号（确认前的值）
        record.setCycleNo(config.getCycleNo());
        record.setConfirmType(CONFIRM_TYPE_MANUAL);
        record.setPrevDeadlineAt(config.getNextDeadlineAt());
        record.setNextDeadlineAt(now.plusDays(config.getCycleDays()));
        record.setSourceIp(clientIp);
        record.setCreatedAt(now);
        recordMapper.insert(record);

        // 连锁处理未生效的授权案件。放在同一事务里：取消失败则整笔确认失败，
        // 用户重试即可，不会出现"人已确认但案件还在跑"的中间态
        recoveryHookProvider.ifAvailable(hook -> hook.onRecovered(actor.userId()));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("cycleNo", config.getCycleNo());
        detail.put("prevStatus", config.getStatus());
        auditService.success(actor.userId(), actor.role(), AuditAction.HEARTBEAT_CONFIRM,
                "HEARTBEAT", actor.userId(), detail);

        log.info("用户完成状态确认 userId={} prevCycle={} prevStatus={}",
                actor.userId(), config.getCycleNo(), config.getStatus());

        return HeartbeatStatusResponse.from(configMapper.selectById(actor.userId()), now);
    }

    public PageResult<HeartbeatRecordResponse> listRecords(AuthPrincipal actor, long page, long size) {
        Page<HeartbeatRecord> paging = new Page<>(page, size);
        LambdaQueryWrapper<HeartbeatRecord> query = new LambdaQueryWrapper<HeartbeatRecord>()
                .eq(HeartbeatRecord::getUserId, actor.userId())
                .orderByDesc(HeartbeatRecord::getId);

        IPage<HeartbeatRecord> result = recordMapper.selectPage(paging, query);
        List<HeartbeatRecordResponse> records = result.getRecords().stream()
                .map(HeartbeatRecordResponse::from).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    // ==================================================================
    // 管理端
    // ==================================================================

    /**
     * 待核验队列：状态为 VERIFYING 的用户。
     */
    public List<HeartbeatStatusResponse> listVerifyQueue(long page, long size) {
        Page<HeartbeatConfig> paging = new Page<>(page, size);
        LambdaQueryWrapper<HeartbeatConfig> query = new LambdaQueryWrapper<HeartbeatConfig>()
                .eq(HeartbeatConfig::getStatus, HeartbeatStatus.VERIFYING)
                .orderByAsc(HeartbeatConfig::getUpdatedAt);

        IPage<HeartbeatConfig> result = configMapper.selectPage(paging, query);
        LocalDateTime now = LocalDateTime.now();
        return result.getRecords().stream()
                .map(c -> HeartbeatStatusResponse.from(c, now))
                .toList();
    }

    /**
     * 人工核验失联事实成立：VERIFYING → REVIEWING。
     *
     * <p><b>这是从"联系不上"跨越到"可以申请授权"的唯一入口，且只能由人工触发。</b>
     * 代码里没有定时任务能走到这里 —— 再长的超时也换不来这一步。
     */
    @Transactional
    public void verify(AuthPrincipal admin, Long userId) {
        HeartbeatConfig config = configMapper.selectById(userId);
        if (config == null) {
            throw BizException.notFound("状态确认配置");
        }
        if (!HeartbeatStatus.VERIFYING.equals(config.getStatus())) {
            throw new StateConflictException(
                    "该用户当前状态为 " + config.getStatus() + "，只有 VERIFYING 状态可以核验");
        }

        int rows = configMapper.markReviewing(userId, config.getCycleNo(), LocalDateTime.now());
        if (rows == 0) {
            throw new StateConflictException("状态已被其他操作改变，请刷新后重试");
        }

        auditService.success(admin.userId(), admin.role(), AuditAction.HEARTBEAT_VERIFY,
                "HEARTBEAT", userId, Map.of("cycleNo", config.getCycleNo()));
        log.warn("管理员核验失联成立 adminId={} userId={}", admin.userId(), userId);
    }

    // ==================================================================
    // 定时任务入口（第四阶段接入调度）
    // ==================================================================

    /**
     * 到期扫描：把逾期未确认的用户推进到 REMINDING。
     *
     * <p>用游标分页而不是 OFFSET，避免大表上的深分页。
     * 单条失败不影响整批 —— 条件更新失败（比如用户刚好确认了）是正常情况，
     * 不是错误。
     *
     * @return 实际推进的条数
     */
    public int scanDue() {
        LocalDateTime now = LocalDateTime.now();
        long lastId = 0L;
        int advanced = 0;

        while (true) {
            List<HeartbeatConfig> batch = configMapper.selectDue(now, lastId, SCAN_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (HeartbeatConfig config : batch) {
                lastId = config.getUserId();
                int rows = configMapper.startReminding(config.getUserId(), config.getCycleNo(), now);
                if (rows > 0) {
                    advanced++;
                    log.info("状态确认逾期，进入提醒阶段 userId={} cycleNo={}",
                            config.getUserId(), config.getCycleNo());
                    // 第四阶段：在此处通过事务发件箱发出第 1 轮提醒通知
                } else {
                    // 影响 0 行说明用户在扫描后被并发确认了，或周期已变 —— 正常跳过
                    log.debug("跳过已失效的到期记录 userId={} cycleNo={}",
                            config.getUserId(), config.getCycleNo());
                }
            }
        }
        return advanced;
    }

    /**
     * 提醒扫描：对提醒中且间隔已到的用户发出下一轮提醒；轮次耗尽则进入核验。
     */
    public int scanRemind() {
        LocalDateTime now = LocalDateTime.now();
        long lastId = 0L;
        int processed = 0;

        while (true) {
            List<HeartbeatConfig> batch = configMapper.selectNeedRemind(now, lastId, SCAN_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (HeartbeatConfig config : batch) {
                lastId = config.getUserId();

                if (config.getReminderCount() >= config.getReminderRounds()) {
                    // 理论上 selectNeedRemind 已过滤，这里再判一次防止配置变更
                    continue;
                }

                int nextCount = config.getReminderCount() + 1;
                if (nextCount >= config.getReminderRounds()) {
                    // 这是最后一轮提醒：发完就转入人工核验
                    int rows = configMapper.increaseReminder(config.getUserId(), config.getCycleNo(), now);
                    if (rows > 0) {
                        configMapper.markVerifying(config.getUserId(), config.getCycleNo(), now);
                        processed++;
                        log.info("提醒轮次耗尽，转入人工核验 userId={} cycleNo={}",
                                config.getUserId(), config.getCycleNo());
                    }
                } else {
                    int rows = configMapper.increaseReminder(config.getUserId(), config.getCycleNo(), now);
                    if (rows > 0) {
                        processed++;
                        log.info("发出第 {} 轮提醒 userId={}", nextCount, config.getUserId());
                    }
                }
            }
        }
        return processed;
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 取配置，不存在则创建默认配置。
     *
     * <p>并发首次访问时，两个请求可能都查不到记录并尝试插入，
     * 后者撞唯一主键失败 —— 此时重新读取即可，不当作错误。
     */
    private HeartbeatConfig requireOrCreate(Long userId) {
        HeartbeatConfig existing = configMapper.selectById(userId);
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        HeartbeatConfig config = new HeartbeatConfig();
        config.setUserId(userId);
        config.setCycleDays(properties.defaultCycleDays());
        config.setReminderRounds(properties.defaultReminderRounds());
        config.setReminderIntervalHours(properties.defaultReminderIntervalHours());
        config.setStatus(HeartbeatStatus.ACTIVE);
        config.setCycleNo(1);
        config.setLastConfirmAt(now);
        config.setNextDeadlineAt(now.plusDays(properties.defaultCycleDays()));
        config.setReminderCount(0);
        config.setVersion(0);
        config.setCreatedAt(now);
        config.setUpdatedAt(now);

        try {
            configMapper.insert(config);
            log.info("为用户创建默认状态确认配置 userId={} cycleDays={}", userId, properties.defaultCycleDays());
            return config;
        } catch (DuplicateKeyException e) {
            // 并发创建，别人先插入了
            return configMapper.selectById(userId);
        }
    }

    private void validateCycleDays(Integer cycleDays) {
        if (cycleDays < properties.minCycleDays() || cycleDays > properties.maxCycleDays()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "确认周期需在 " + properties.minCycleDays() + "~" + properties.maxCycleDays() + " 天之间");
        }
    }

    private void validateReminderRounds(Integer rounds) {
        if (rounds > properties.maxReminderRounds()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "提醒轮数不能超过 " + properties.maxReminderRounds());
        }
    }
}
