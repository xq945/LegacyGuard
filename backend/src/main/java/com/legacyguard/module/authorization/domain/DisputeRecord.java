package com.legacyguard.module.authorization.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 异议记录，对应 {@code dispute_record}。
 *
 * <p>异议是等待期存在的意义：审批通过不等于最终生效，
 * 中间留出一段时间让所有者"发现并叫停"。
 */
@Getter
@Setter
@TableName("dispute_record")
public class DisputeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long caseId;

    private Long submitterId;

    /** OWNER / CONTACT / ADMIN */
    private String submitterRole;

    private String reason;

    /** OPEN / RESOLVED */
    private String status;

    /** 管理员处理意见 */
    private String resolution;

    private Long resolvedBy;

    private LocalDateTime resolvedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
