package com.legacyguard.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果。
 *
 * <p>刻意做成与 MyBatis-Plus {@code IPage} 解耦的纯数据载体 —— common 层不引入持久层依赖，
 * 由 Service 层负责把 {@code IPage} 转成它。
 */
@Schema(description = "分页结果")
public record PageResult<T>(

        @Schema(description = "当前页数据")
        List<T> records,

        @Schema(description = "总记录数", example = "128")
        long total,

        @Schema(description = "当前页码，从 1 开始", example = "1")
        long page,

        @Schema(description = "每页条数", example = "20")
        long size
) {

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0L, page, size);
    }

    /** 保持分页元数据不变，把记录映射成另一种类型。 */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(records.stream().<R>map(mapper).toList(), total, page, size);
    }
}
