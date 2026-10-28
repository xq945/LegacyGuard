package com.legacyguard.module.contact.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 联系人关系的查询视图（带双方显示名）。
 *
 * <p>这是给 Mapper 用的可变对象，转成 {@link ContactResponse} 后才对外返回。
 */
@Getter
@Setter
public class ContactView {

    private Long id;
    private Long ownerId;
    private Long contactUserId;
    private String status;
    private String note;
    private LocalDateTime invitedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime revokedAt;

    private String ownerUsername;
    private String ownerDisplayName;
    private String contactUsername;
    private String contactDisplayName;
}
