package com.legacyguard.module.contact.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 可信联系人关系，对应 {@code trusted_contact}。
 *
 * <p>这是 {@code user → user} 的一条有向关系边：{@code ownerId} 是资产所有者，
 * {@code contactUserId} 是被托付的人。同一个人可以既是自己的资产所有者，
 * 又出现在别人的联系人列表里。
 *
 * <p>表中有一个生成列 {@code active_key}（PENDING/ACTIVE 时非空，其余为 NULL），
 * 配合唯一索引实现"同一对用户只能有一条有效关系，但失效后可以重新邀请"。
 * 生成列由数据库维护，<b>不映射到本实体</b>。
 */
@Getter
@Setter
@ToString
@TableName("trusted_contact")
public class TrustedContact {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资产所有者 */
    private Long ownerId;

    /** 被邀请的联系人（平台用户） */
    private Long contactUserId;

    /** 见 {@link ContactStatus} */
    private String status;

    /** 所有者写的备注，例如"这是我弟弟" */
    private String note;

    @Version
    private Integer version;

    private LocalDateTime invitedAt;

    private LocalDateTime respondedAt;

    private LocalDateTime revokedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
