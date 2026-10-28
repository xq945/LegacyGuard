package com.legacyguard.module.contact.controller;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.module.contact.dto.ContactResponse;
import com.legacyguard.module.contact.dto.InviteContactRequest;
import com.legacyguard.module.contact.service.ContactService;
import com.legacyguard.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 可信联系人接口。
 *
 * <p>邀请的"接受/拒绝"是<b>被邀请人</b>的操作，而"撤回/撤销"是<b>所有者</b>的操作。
 * 同一个 URL 前缀下有两种主体身份，因此每个方法都要靠服务端判定的当前用户来决定能不能做，
 * 前端传什么参数都改变不了这一点。
 */
@RestController
@RequestMapping("/api/contacts")
@Tag(name = "可信联系人", description = "邀请、接受、拒绝与撤销联系人关系")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping("/invitations")
    @Operation(summary = "邀请可信联系人",
            description = "按用户名查找已注册用户并发出邀请，对方确认后关系才生效。")
    public ResponseEntity<ApiResponse<ContactResponse>> invite(
            @Valid @RequestBody InviteContactRequest request) {

        ContactResponse created = contactService.invite(CurrentUser.get(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PostMapping("/invitations/{id}/accept")
    @Operation(summary = "接受邀请", description = "仅被邀请人本人可操作。")
    public ApiResponse<Void> accept(@PathVariable Long id) {
        contactService.accept(CurrentUser.get(), id);
        return ApiResponse.success();
    }

    @PostMapping("/invitations/{id}/reject")
    @Operation(summary = "拒绝邀请", description = "仅被邀请人本人可操作。")
    public ApiResponse<Void> reject(@PathVariable Long id) {
        contactService.reject(CurrentUser.get(), id);
        return ApiResponse.success();
    }

    @GetMapping
    @Operation(summary = "联系人关系列表",
            description = "返回与当前用户相关的全部关系：我邀请的人，以及邀请我的人。")
    public ApiResponse<List<ContactResponse>> list() {
        return ApiResponse.success(contactService.list(CurrentUser.get()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "撤销联系人关系",
            description = """
                    仅所有者可操作。可撤回待确认的邀请，也可撤销已生效的关系。

                    注意：撤销关系只阻止未来的新授权申请，**已经生效的授权不受影响**，
                    需要所有者单独调用授权撤销接口。
                    """)
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        contactService.revoke(CurrentUser.get(), id);
        return ApiResponse.success();
    }
}
