package com.legacyguard.module.authorization.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 授权案件，对应 {@code authorization_case}。
 *
 * <p><b>本实体不提供公开的 {@code setStatus()}</b> —— 状态只能通过
 * {@code CaseStateMachine} 迁移。这不是靠自觉，而是所有 update 语句
 * 都写成条件更新并检查影响行数，绕过状态机的写法会直接失败。
 */
@Getter
@Setter
@ToString
@TableName("authorization_case")
public class AuthorizationCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外暴露的业务编号，避免自增值泄露业务量 */
    private String caseNo;

    private Long ownerId;

    /** 申请人，指向 {@code trusted_contact.id}（不是用户 ID） */
    private Long contactId;

    /** HEARTBEAT_MISS / MANUAL */
    private String triggerType;

    /** 见 {@link CaseStatus} */
    private String status;

    /** 申请人填写的理由 */
    private String reason;

    /** 最近一次审批意见 */
    private String reviewComment;

    /** 等待期截止时间 */
    private LocalDateTime waitPeriodEndAt;

    private LocalDateTime authorizedAt;

    private LocalDateTime revokedAt;

    /** 进入终态的时间 */
    private LocalDateTime closedAt;

    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
