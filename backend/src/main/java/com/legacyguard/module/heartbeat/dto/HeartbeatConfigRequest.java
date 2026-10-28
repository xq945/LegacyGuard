package com.legacyguard.module.heartbeat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 修改状态确认配置。
 *
 * <p>取值范围在注解里做粗筛，精确边界（来自配置）在 Service 里再校验。
 * 周期越短越容易误触发失联流程，因此下限设得比较保守。
 */
@Schema(description = "修改状态确认配置")
public record HeartbeatConfigRequest(

        @NotNull(message = "确认周期不能为空")
        @Min(value = 1, message = "确认周期至少 1 天")
        @Max(value = 3650, message = "确认周期过长")
        @Schema(description = "确认周期（天），建议 30~365", example = "90")
        Integer cycleDays,

        @NotNull(message = "提醒轮数不能为空")
        @Min(value = 1, message = "至少提醒 1 轮")
        @Max(value = 10, message = "提醒轮数最多 10 轮")
        @Schema(description = "最大提醒轮数", example = "3")
        Integer reminderRounds,

        @NotNull(message = "提醒间隔不能为空")
        @Min(value = 1, message = "提醒间隔至少 1 小时")
        @Max(value = 720, message = "提醒间隔最多 30 天")
        @Schema(description = "提醒间隔（小时）", example = "72")
        Integer reminderIntervalHours
) {
}
