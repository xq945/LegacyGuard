package com.legacyguard.common.api;

import com.legacyguard.common.web.RequestContext;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 统一响应结构。
 *
 * <pre>{@code
 * { "code": 0, "message": "ok", "data": {...}, "requestId": "0c1b7f3e-..." }
 * }</pre>
 *
 * <p>注意：{@code code}/{@code message} 是**业务**语义，真实的 HTTP 状态码在响应的 status line 上，
 * 两者通过 {@link ErrorCode} 绑定。客户端既要看 status，也要看 code。
 */
@Schema(description = "统一响应结构")
public record ApiResponse<T>(

        @Schema(description = "业务码，0 表示成功", example = "0")
        int code,

        @Schema(description = "提示信息", example = "ok")
        String message,

        @Schema(description = "业务数据，失败时为 null")
        T data,

        @Schema(description = "请求关联 ID，排查问题时提供给运维", example = "0c1b7f3e-8a2d-4f19-9c77-2e5f1a3b4d68")
        String requestId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getDefaultMessage(),
                data, RequestContext.currentRequestId());
    }

    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null, RequestContext.currentRequestId());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.getDefaultMessage());
    }
}
