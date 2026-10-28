package com.legacyguard.common.exception;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>核心原则：
 * <ol>
 *   <li><b>业务异常 → 4xx，系统异常 → 5xx</b>，绝不统一返回 200</li>
 *   <li><b>绝不把堆栈返回给客户端</b>，只返回 requestId，让排查走服务端日志</li>
 *   <li>业务异常记 WARN（不需要堆栈），系统异常记 ERROR（带完整堆栈）</li>
 * </ol>
 *
 * <p>注意：这里处理的是 Controller / Service 层抛出的异常。
 * Filter 链（JWT 校验、认证）中的异常由 Spring Security 的
 * {@code AuthenticationEntryPoint} / {@code AccessDeniedHandler} 处理，两者缺一不可。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================================================================
    // 业务异常
    // ==================================================================

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        log.warn("业务异常 code={} path={} message={}", code.getCode(), request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.failure(code, ex.getMessage()));
    }

    // ==================================================================
    // 参数校验
    // ==================================================================

    /** @RequestBody 上的 @Valid 校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", detail);
        return parameterInvalid(detail);
    }

    /** 表单/查询参数绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBind(BindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describeFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", detail);
        return parameterInvalid(detail);
    }

    /** @PathVariable / @RequestParam 上的约束注解失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数约束失败: {}", detail);
        return parameterInvalid(detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return parameterInvalid("缺少必填参数: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return parameterInvalid("参数类型不正确: " + ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        // 不把 ex.getMessage() 返回给前端 —— 它可能包含类名与字段结构
        log.warn("请求体解析失败", ex);
        return parameterInvalid("请求体格式不正确");
    }

    // ==================================================================
    // 权限
    // ==================================================================

    /** 未携带凭证 / 凭证无效 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        log.warn("认证失败: {}", ex.getMessage());
        return build(ErrorCode.UNAUTHENTICATED);
    }

    /** 方法级 @PreAuthorize 拒绝 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("权限不足: {}", ex.getMessage());
        return build(ErrorCode.ACCESS_DENIED);
    }

    // ==================================================================
    // 数据库约束
    // ==================================================================

    /**
     * 唯一约束冲突。
     * 这通常意味着"重复提交"，而不是系统故障 —— 唯一索引正是幂等设计的最后一道防线。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("唯一约束冲突（疑似重复请求）: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.DUPLICATE_REQUEST);
    }

    /** 外键、CHECK 约束等完整性约束失败 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("数据完整性约束失败: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.BIZ_RULE_VIOLATION);
    }

    // ==================================================================
    // HTTP 协议层
    // ==================================================================

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return parameterInvalid("不支持的 Content-Type: " + ex.getContentType());
    }

    // ==================================================================
    // 兜底
    // ==================================================================

    /**
     * 未预期的系统异常。
     *
     * <p>对外只返回"系统繁忙"与 requestId，堆栈只写服务端日志 ——
     * 堆栈里可能含有类名、SQL、甚至参数值，属于不应外泄的信息。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 path={} method={}", request.getRequestURI(), request.getMethod(), ex);
        return build(ErrorCode.INTERNAL_ERROR);
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    private ResponseEntity<ApiResponse<Void>> parameterInvalid(String detail) {
        return ResponseEntity.status(ErrorCode.PARAM_INVALID.getHttpStatus())
                .body(ApiResponse.failure(ErrorCode.PARAM_INVALID, detail));
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code) {
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.failure(code));
    }

    private String describeFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
