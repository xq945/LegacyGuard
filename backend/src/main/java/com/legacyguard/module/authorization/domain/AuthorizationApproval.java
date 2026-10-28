package com.legacyguard.module.authorization.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审批记录，对应 {@code authorization_approval}。
 *
 * <p>只增不改。每次状态流转都留一条"谁、从什么状态、到什么状态、说了什么"，
 * 这是事后追溯审批过程的完整链条。
 */
@Getter
@Setter
@TableName("authorization_approval")
public class AuthorizationApproval {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long caseId;

    private Long approverId;

    /** ACCEPT / APPROVE / REJECT / RESOLVE_DISPUTE */
    private String action;

    private String fromStatus;

    private String toStatus;

    private String comment;

    private LocalDateTime createdAt;
}
