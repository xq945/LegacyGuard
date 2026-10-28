package com.legacyguard.module.heartbeat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.heartbeat.domain.HeartbeatConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 状态确认配置数据访问。
 *
 * <p>所有推进状态的 SQL 都带 {@code cycle_no} 条件 —— 这是与用户确认操作
 * 并发时的正确性保证，详见 docs/02 第 2.1.1 节。
 */
@Mapper
public interface HeartbeatConfigMapper extends BaseMapper<HeartbeatConfig> {

    /**
     * 确认：重置周期。用户每次确认都让 {@code cycle_no + 1}。
     *
     * <p>条件里同时带 {@code version}，防止同一秒内重复提交。
     */
    @Update("""
            UPDATE heartbeat_confirm_config
               SET cycle_no = cycle_no + 1,
                   status = 'ACTIVE',
                   last_confirm_at = #{now},
                   next_deadline_at = DATE_ADD(#{now}, INTERVAL cycle_days DAY),
                   reminder_count = 0,
                   last_remind_at = NULL,
                   suspended_from_status = NULL,
                   version = version + 1,
                   updated_at = #{now}
             WHERE user_id = #{userId} AND version = #{version}
            """)
    int confirm(@Param("userId") Long userId,
                @Param("version") Integer version,
                @Param("now") LocalDateTime now);

    /**
     * 到期扫描：ACTIVE → REMINDING，发出第 1 轮提醒。
     *
     * <p>{@code cycle_no} 条件保证：如果用户在这期间完成了确认，周期号已经变了，
     * 这条更新影响 0 行，旧任务自动失效 —— 不需要任何分布式锁。
     */
    @Update("""
            UPDATE heartbeat_confirm_config
               SET status = 'REMINDING',
                   reminder_count = 1,
                   last_remind_at = #{now},
                   version = version + 1,
                   updated_at = #{now}
             WHERE user_id = #{userId}
               AND status = 'ACTIVE'
               AND cycle_no = #{cycleNo}
               AND next_deadline_at <= #{now}
            """)
    int startReminding(@Param("userId") Long userId,
                       @Param("cycleNo") Integer cycleNo,
                       @Param("now") LocalDateTime now);

    /**
     * 提醒轮次 +1。同样带周期号条件。
     */
    @Update("""
            UPDATE heartbeat_confirm_config
               SET reminder_count = reminder_count + 1,
                   last_remind_at = #{now},
                   version = version + 1,
                   updated_at = #{now}
             WHERE user_id = #{userId}
               AND status = 'REMINDING'
               AND cycle_no = #{cycleNo}
            """)
    int increaseReminder(@Param("userId") Long userId,
                         @Param("cycleNo") Integer cycleNo,
                         @Param("now") LocalDateTime now);

    /**
     * 提醒耗尽：REMINDING → VERIFYING，进入人工核验队列。
     */
    @Update("""
            UPDATE heartbeat_confirm_config
               SET status = 'VERIFYING',
                   version = version + 1,
                   updated_at = #{now}
             WHERE user_id = #{userId}
               AND status = 'REMINDING'
               AND cycle_no = #{cycleNo}
            """)
    int markVerifying(@Param("userId") Long userId,
                      @Param("cycleNo") Integer cycleNo,
                      @Param("now") LocalDateTime now);

    /**
     * 管理员核验通过：VERIFYING → REVIEWING，允许发起授权申请。
     */
    @Update("""
            UPDATE heartbeat_confirm_config
               SET status = 'REVIEWING',
                   version = version + 1,
                   updated_at = #{now}
             WHERE user_id = #{userId}
               AND status = 'VERIFYING'
               AND cycle_no = #{cycleNo}
            """)
    int markReviewing(@Param("userId") Long userId,
                      @Param("cycleNo") Integer cycleNo,
                      @Param("now") LocalDateTime now);

    /**
     * 冻结：记录冻结前状态，便于解冻还原。
     */
    @Update("""
            UPDATE heartbeat_confirm_config
               SET suspended_from_status = status,
                   status = 'SUSPENDED',
                   version = version + 1,
                   updated_at = #{now}
             WHERE user_id = #{userId}
               AND status <> 'SUSPENDED'
            """)
    int suspend(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 解冻：还原到冻结前的状态。
     */
    @Update("""
            UPDATE heartbeat_confirm_config
               SET status = COALESCE(suspended_from_status, 'ACTIVE'),
                   suspended_from_status = NULL,
                   version = version + 1,
                   updated_at = #{now}
             WHERE user_id = #{userId}
               AND status = 'SUSPENDED'
            """)
    int unsuspend(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * 到期扫描的候选集。
     *
     * <p>走 {@code idx_status_deadline (status, next_deadline_at)} 复合索引。
     * 用游标分页（{@code user_id > lastId}）而不是 OFFSET —— 后者在大表上会越来越慢。
     */
    @Select("""
            SELECT * FROM heartbeat_confirm_config
             WHERE status = 'ACTIVE'
               AND next_deadline_at <= #{now}
               AND user_id > #{lastId}
             ORDER BY user_id
             LIMIT #{limit}
            """)
    List<HeartbeatConfig> selectDue(@Param("now") LocalDateTime now,
                                    @Param("lastId") Long lastId,
                                    @Param("limit") int limit);

    /**
     * 提醒扫描的候选集：仍在提醒中、轮次未耗尽、且距上次提醒超过间隔。
     */
    @Select("""
            SELECT * FROM heartbeat_confirm_config
             WHERE status = 'REMINDING'
               AND reminder_count < reminder_rounds
               AND (last_remind_at IS NULL
                    OR last_remind_at <= DATE_SUB(#{now}, INTERVAL reminder_interval_hours HOUR))
               AND user_id > #{lastId}
             ORDER BY user_id
             LIMIT #{limit}
            """)
    List<HeartbeatConfig> selectNeedRemind(@Param("now") LocalDateTime now,
                                           @Param("lastId") Long lastId,
                                           @Param("limit") int limit);
}
