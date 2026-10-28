package com.legacyguard.module.contact.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 邀请可信联系人。
 *
 * <p>按<b>用户名</b>精确查找平台已注册用户。对方没注册就无法邀请 ——
 * 这是已确认的设计决策（docs/07 Q1）：v1 不做"邀请未注册邮箱后自动关联"，
 * 那条路需要邀请令牌、邮箱验证和过期机制，成本与收益不匹配。
 */
@Schema(description = "邀请可信联系人")
public record InviteContactRequest(

        @NotBlank(message = "用户名不能为空")
        @Size(max = 50, message = "用户名过长")
        @Schema(description = "被邀请人的登录名，对方必须已注册", example = "lisi")
        String username,

        @Size(max = 255, message = "备注不能超过 255 字")
        @Schema(description = "备注，例如\"这是我弟弟\"", example = "弟弟")
        String note
) {
}
