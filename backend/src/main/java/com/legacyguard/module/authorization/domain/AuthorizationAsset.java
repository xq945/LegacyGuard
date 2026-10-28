package com.legacyguard.module.authorization.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 案件与资产的关联，对应 {@code authorization_asset}。
 *
 * <p>一个案件可以覆盖多项资产，但只覆盖联系人明确勾选的那些 ——
 * "授权"从来不是"把所有东西都给他"。
 */
@Getter
@Setter
@TableName("authorization_asset")
public class AuthorizationAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long caseId;

    private Long assetId;

    private LocalDateTime createdAt;
}
