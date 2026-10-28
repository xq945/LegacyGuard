package com.legacyguard.module.heartbeat.dto;

import com.legacyguard.module.heartbeat.domain.HeartbeatRecord;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 一次状态确认的流水。
 */
@Schema(description = "状态确认流水")
public record HeartbeatRecordResponse(

        @Schema(description = "记录 ID")
        Long id,

        @Schema(description = "被完成的周期号")
        Integer cycleNo,

        @Schema(description = "确认时间")
        LocalDateTime confirmedAt,

        @Schema(description = "确认前的截止时间")
        LocalDateTime prevDeadlineAt,

        @Schema(description = "确认后的新截止时间")
        LocalDateTime nextDeadlineAt
) {

    public static HeartbeatRecordResponse from(HeartbeatRecord record) {
        return new HeartbeatRecordResponse(
                record.getId(),
                record.getCycleNo(),
                record.getCreatedAt(),
                record.getPrevDeadlineAt(),
                record.getNextDeadlineAt()
        );
    }
}
