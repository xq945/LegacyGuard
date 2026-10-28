package com.legacyguard.module.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 登录流水，对应 {@code login_record}。
 *
 * <p>与 {@code audit_log} 的分工：审计记录"发生了什么业务动作"，
 * 登录流水记录"每一次认证尝试"（含失败）。失败尝试量大、结构规整，
 * 单独一张表便于做风控分析（例如统计某 IP 的暴力破解频率）。
 *
 * <p>{@code userId} 允许为 null —— 用户名不存在时也要记下来。
 */
@Getter
@Setter
@TableName("login_record")
public class LoginRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    /** 1 成功 0 失败 */
    private Integer success;

    /** BAD_CREDENTIAL / LOCKED / DISABLED / NOT_FOUND */
    private String failReason;

    private String clientIp;

    private LocalDateTime createdAt;
}
