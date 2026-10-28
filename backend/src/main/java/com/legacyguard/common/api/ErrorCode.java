package com.legacyguard.common.api;

import org.springframework.http.HttpStatus;

/**
 * 统一业务错误码。
 *
 * <p>设计要点：<b>业务码与 HTTP 状态码一一绑定</b>，绝不允许把所有错误都包成 HTTP 200。
 * 前端据此可以区分"没登录"(401) / "没权限"(403) / "当前状态不允许"(422) / "并发冲突"(409)，
 * 这四种情况在前端需要完全不同的处理方式。
 *
 * <p>码段划分：
 * <ul>
 *   <li>40xxx —— 客户端问题（参数、权限、状态）</li>
 *   <li>50xxx —— 服务端问题</li>
 * </ul>
 */
public enum ErrorCode {

    SUCCESS(0, HttpStatus.OK, "ok"),

    // ===== 4xx 客户端 =====
    PARAM_INVALID(40001, HttpStatus.BAD_REQUEST, "参数校验失败"),
    UNAUTHENTICATED(40101, HttpStatus.UNAUTHORIZED, "未登录或登录已失效"),
    ACCESS_DENIED(40301, HttpStatus.FORBIDDEN, "无权访问该资源"),
    RESOURCE_NOT_FOUND(40401, HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(40501, HttpStatus.METHOD_NOT_ALLOWED, "请求方法不支持"),
    STATE_CONFLICT(40901, HttpStatus.CONFLICT, "状态已被其他操作改变，请刷新后重试"),
    DUPLICATE_REQUEST(40902, HttpStatus.CONFLICT, "重复请求"),
    BIZ_RULE_VIOLATION(42201, HttpStatus.UNPROCESSABLE_ENTITY, "业务规则不满足"),
    RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"),

    // ===== 5xx 服务端 =====
    INTERNAL_ERROR(50001, HttpStatus.INTERNAL_SERVER_ERROR, "系统繁忙，请稍后重试"),
    MESSAGE_PUBLISH_FAILED(50002, HttpStatus.INTERNAL_SERVER_ERROR, "消息发布失败");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
