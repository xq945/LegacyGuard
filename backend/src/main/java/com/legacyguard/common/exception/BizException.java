package com.legacyguard.common.exception;

import com.legacyguard.common.api.ErrorCode;

/**
 * 业务异常：表示"请求合法，但业务规则不允许"。
 *
 * <p>与系统异常的区别：业务异常是**预期内**的，会被全局异常处理器转换为 4xx 响应，
 * 日志级别为 WARN；系统异常是**预期外**的，转为 5xx，日志级别为 ERROR 并记录堆栈。
 *
 * <p>常见用法：
 * <pre>{@code
 * throw new BizException(ErrorCode.BIZ_RULE_VIOLATION, "该联系人尚未确认邀请，无法提交授权申请");
 * }</pre>
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    // ===== 常用快捷构造 =====

    /** 资源不存在 */
    public static BizException notFound(String what) {
        return new BizException(ErrorCode.RESOURCE_NOT_FOUND, what + "不存在");
    }

    /** 越权访问 */
    public static BizException accessDenied(String message) {
        return new BizException(ErrorCode.ACCESS_DENIED, message);
    }

    /** 业务规则不满足 */
    public static BizException ruleViolation(String message) {
        return new BizException(ErrorCode.BIZ_RULE_VIOLATION, message);
    }
}
