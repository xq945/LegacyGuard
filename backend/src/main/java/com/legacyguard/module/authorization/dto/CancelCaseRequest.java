package com.legacyguard.module.authorization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 取消案件。理由可选，会记入案件的 {@code reason} 字段便于追溯。
 */
@Schema(description = "取消案件")
public record CancelCaseRequest(

        @Size(max = 500, message = "取消理由不能超过 500 字")
        @Schema(description = "取消理由，可选")
        String reason
) {
}
