package com.legacyguard.module.asset.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 资产访问流水，对应 {@code asset_access_log}。
 *
 * <p>与 {@code audit_log} 的区别：审计记录的是"业务决策"
 * （谁提交了案件、谁批准了），访问流水记录的是"每一次读敏感内容的尝试"，
 * <b>包括被拒绝的</b>。后者量更大，且结构规整，单独建表便于做越权分析。
 *
 * <p>拒绝记录很重要：如果某人反复尝试访问不属于他的资产，
 * 这条流水就是唯一的证据。
 */
@Getter
@Setter
@TableName("asset_access_log")
public class AssetAccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetId;

    /** 通过授权案件访问时记录的案号 */
    private Long caseId;

    private Long accessorId;

    /** OWNER_VIEW / CASE_VIEW */
    private String accessType;

    /** SUCCESS / DENIED */
    private String result;

    /** NOT_FOUND / NO_ACTIVE_GRANT */
    private String denyReason;

    private String clientIp;

    private LocalDateTime createdAt;
}
