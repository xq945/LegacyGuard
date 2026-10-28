package com.legacyguard.module.heartbeat.dto;

import com.legacyguard.module.heartbeat.domain.HeartbeatConfig;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 状态确认的当前情况。
 */
@Schema(description = "状态确认情况")
public record HeartbeatStatusResponse(

        @Schema(description = "用户 ID。管理端核验队列必须靠它来定位用户")
        Long userId,

        @Schema(description = "ACTIVE / REMINDING / VERIFYING / REVIEWING / SUSPENDED")
        String status,

        @Schema(description = "当前周期号")
        Integer cycleNo,

        @Schema(description = "确认周期（天）")
        Integer cycleDays,

        @Schema(description = "最近一次确认时间")
        LocalDateTime lastConfirmAt,

        @Schema(description = "下次确认截止时间")
        LocalDateTime nextDeadlineAt,

        @Schema(description = "本周期已发出的提醒轮数")
        Integer reminderCount,

        @Schema(description = "最大提醒轮数")
        Integer reminderRounds,

        @Schema(description = "距截止还有多少天；已逾期为负")
        Long daysRemaining,

        @Schema(description = "是否已逾期")
        boolean overdue
) {

    public static HeartbeatStatusResponse from(HeartbeatConfig config, LocalDateTime now) {
        long days = config.getNextDeadlineAt() == null
                ? 0
                : java.time.Duration.between(now, config.getNextDeadlineAt()).toDays();

        return new HeartbeatStatusResponse(
                config.getUserId(),
                config.getStatus(),
                config.getCycleNo(),
                config.getCycleDays(),
                config.getLastConfirmAt(),
                config.getNextDeadlineAt(),
                config.getReminderCount(),
                config.getReminderRounds(),
                days,
                config.getNextDeadlineAt() != null && config.getNextDeadlineAt().isBefore(now)
        );
    }
}
