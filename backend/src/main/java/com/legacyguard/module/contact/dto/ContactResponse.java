package com.legacyguard.module.contact.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 联系人关系响应。
 *
 * <p>同一条记录对双方的含义不同：对所有者是"我托付的人"，对被邀请人是"谁托付了我"。
 * {@code iAmOwner} 与 {@code canRespond} 让前端不必自己推导该显示哪些按钮 ——
 * 但真正的权限校验始终在后端。
 *
 * @param iAmOwner    当前用户是否为邀请方
 * @param canRespond  当前用户是否该处理这条邀请（被邀请人 + 状态为 PENDING）
 */
@Schema(description = "联系人关系")
public record ContactResponse(

        @Schema(description = "关系 ID")
        Long id,

        @Schema(description = "所有者用户 ID")
        Long ownerId,

        @Schema(description = "所有者显示名")
        String ownerDisplayName,

        @Schema(description = "联系人用户 ID")
        Long contactUserId,

        @Schema(description = "联系人显示名")
        String contactDisplayName,

        @Schema(description = "PENDING / ACTIVE / REJECTED / REVOKED")
        String status,

        @Schema(description = "所有者备注")
        String note,

        @Schema(description = "邀请时间")
        LocalDateTime invitedAt,

        @Schema(description = "回应时间")
        LocalDateTime respondedAt,

        @Schema(description = "当前用户是否为邀请方")
        boolean iAmOwner,

        @Schema(description = "当前用户是否应处理这条邀请")
        boolean canRespond
) {

    public static ContactResponse from(ContactView view, Long currentUserId) {
        boolean iAmOwner = view.getOwnerId().equals(currentUserId);
        boolean canRespond = !iAmOwner
                && view.getContactUserId().equals(currentUserId)
                && "PENDING".equals(view.getStatus());

        return new ContactResponse(
                view.getId(),
                view.getOwnerId(),
                view.getOwnerDisplayName(),
                view.getContactUserId(),
                view.getContactDisplayName(),
                view.getStatus(),
                view.getNote(),
                view.getInvitedAt(),
                view.getRespondedAt(),
                iAmOwner,
                canRespond
        );
    }
}
