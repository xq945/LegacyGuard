package com.legacyguard.module.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 审计日志，对应 {@code audit_log}。
 *
 * <p>三条不可变约定：
 * <ul>
 *   <li><b>只增不改</b>：没有 update 路径，也没有 {@code updatedAt}</li>
 *   <li><b>无外键</b>：用户注销后审计记录仍须可读，不能被外键约束或级联删除影响</li>
 *   <li><b>不含敏感内容</b>：{@code detail} 里禁止出现密码、令牌、资产明文、加密密钥。
 *       它记录的是"谁在什么时候对哪条数据做了什么"，不是数据本身</li>
 * </ul>
 */
@Getter
@Setter
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人；系统任务为 null */
    private Long actorId;

    private String actorRole;

    /** 见 {@link AuditAction} */
    private String action;

    /** 业务对象类型：USER / ASSET / CONTACT / CASE / HEARTBEAT */
    private String objectType;

    private Long objectId;

    /** SUCCESS / FAILURE */
    private String result;

    /** 请求关联 ID，可据此拉出同一次请求的全部日志 */
    private String requestId;

    /** JSON 字符串形式的变更摘要 */
    private String detail;

    private String clientIp;

    private String userAgent;

    private LocalDateTime createdAt;
}
