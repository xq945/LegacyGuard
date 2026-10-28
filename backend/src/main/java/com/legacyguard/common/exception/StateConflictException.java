package com.legacyguard.common.exception;

import com.legacyguard.common.api.ErrorCode;

/**
 * 并发冲突：乐观锁版本不匹配，或条件更新影响行数为 0。
 *
 * <p>这不是错误，而是**并发控制正常工作的信号** —— 说明在你读取之后，
 * 已经有另一个操作改变了这条记录。调用方应当提示用户刷新后重试，而不是重试写操作本身。
 *
 * <p>什么时候抛出：所有状态机转换在条件更新后检查影响行数，为 0 即抛此异常。
 * <pre>{@code
 * int rows = caseMapper.transition(caseId, fromStatus, toStatus, version);
 * if (rows == 0) {
 *     throw new StateConflictException("案件状态已被改变");
 * }
 * }</pre>
 */
public class StateConflictException extends BizException {

    public StateConflictException(String message) {
        super(ErrorCode.STATE_CONFLICT, message);
    }

    public StateConflictException() {
        super(ErrorCode.STATE_CONFLICT);
    }
}
