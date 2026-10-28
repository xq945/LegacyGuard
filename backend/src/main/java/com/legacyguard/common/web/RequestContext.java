package com.legacyguard.common.web;

import org.slf4j.MDC;

/**
 * 请求上下文：访问当前请求的关联 ID。
 *
 * <p>「请求关联 ID」是本系统排查问题的主线索 —— 一条授权案件从提交到生效会跨越
 * 多个接口调用和后台任务，唯一能把它们串起来的就是这个 ID（以及消息的 eventId）。
 */
public final class RequestContext {

    /** MDC 中的键名，logback 的 pattern 里用 %X{requestId} 引用。 */
    public static final String MDC_KEY = "requestId";

    /** HTTP 头名，客户端可传入以串联前后端日志。 */
    public static final String HEADER_NAME = "X-Request-Id";

    private RequestContext() {
    }

    /**
     * @return 当前请求的关联 ID；不在请求线程中（如定时任务）时返回 {@code null}
     */
    public static String currentRequestId() {
        return MDC.get(MDC_KEY);
    }

    /**
     * 供后台任务使用：在没有 HTTP 请求的线程里临时设置一个关联 ID。
     */
    public static void runWith(String requestId, Runnable action) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, requestId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
