package com.legacyguard.module.heartbeat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 状态确认配置，对应 {@code heartbeat_confirm_config}。每个用户一条，主键即用户 ID。
 *
 * <p>{@code cycleNo} 是整套幂等体系的核心：用户每次确认都会让它 +1，
 * 定时任务带着扫描时读到的值做条件更新，用户一旦确认，旧任务自然影响 0 行。
 */
@Getter
@Setter
@ToString
@TableName("heartbeat_confirm_config")
public class HeartbeatConfig {

    @TableId(type = IdType.INPUT)
    private Long userId;

    /** 确认周期（天） */
    private Integer cycleDays;

    /** 最大提醒轮数 */
    private Integer reminderRounds;

    /** 提醒间隔（小时） */
    private Integer reminderIntervalHours;

    /** 见 {@link HeartbeatStatus} */
    private String status;

    /** 当前周期号，定时任务的幂等键 */
    private Integer cycleNo;

    private LocalDateTime lastConfirmAt;

    private LocalDateTime nextDeadlineAt;

    /** 本周期已发出的提醒轮数 */
    private Integer reminderCount;

    private LocalDateTime lastRemindAt;

    /** 冻结前状态，用于解冻还原 */
    private String suspendedFromStatus;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
