package com.legacyguard.module.contact.service;

import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.common.exception.StateConflictException;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.module.contact.domain.ContactStatus;
import com.legacyguard.module.contact.domain.TrustedContact;
import com.legacyguard.module.contact.dto.ContactResponse;
import com.legacyguard.module.contact.dto.ContactView;
import com.legacyguard.module.contact.dto.InviteContactRequest;
import com.legacyguard.module.contact.mapper.TrustedContactMapper;
import com.legacyguard.module.notification.service.NotificationService;
import com.legacyguard.module.user.domain.SysUser;
import com.legacyguard.module.user.domain.UserRole;
import com.legacyguard.module.user.mapper.SysUserMapper;
import com.legacyguard.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 联系人关系测试。
 */
@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    private static final Long OWNER_ID = 100L;
    private static final Long CONTACT_ID = 200L;
    private static final Long RELATION_ID = 10L;

    @Mock
    private TrustedContactMapper contactMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationService notificationService;

    private ContactService service;
    private AuthPrincipal owner;
    private AuthPrincipal contact;

    @BeforeEach
    void setUp() {
        service = new ContactService(contactMapper, userMapper, auditService, notificationService);
        owner = new AuthPrincipal(OWNER_ID, "owner", UserRole.USER, "jti-1");
        contact = new AuthPrincipal(CONTACT_ID, "contact", UserRole.USER, "jti-2");
    }

    private SysUser user(Long id, String username) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername(username);
        u.setDisplayName(username);
        return u;
    }

    private TrustedContact relation(String status, Long contactUserId) {
        TrustedContact r = new TrustedContact();
        r.setId(RELATION_ID);
        r.setOwnerId(OWNER_ID);
        r.setContactUserId(contactUserId);
        r.setStatus(status);
        return r;
    }

    private ContactView view(String status) {
        ContactView v = new ContactView();
        v.setId(RELATION_ID);
        v.setOwnerId(OWNER_ID);
        v.setContactUserId(CONTACT_ID);
        v.setStatus(status);
        v.setOwnerDisplayName("owner");
        v.setContactDisplayName("contact");
        v.setInvitedAt(LocalDateTime.now());
        return v;
    }

    // ==================================================================
    // 邀请
    // ==================================================================

    @Test
    @DisplayName("邀请已注册用户成功，初始状态为 PENDING")
    void inviteCreatesPendingRelation() {
        when(userMapper.selectByUsername("contact")).thenReturn(user(CONTACT_ID, "contact"));
        when(contactMapper.selectActivePair(OWNER_ID, CONTACT_ID)).thenReturn(null);
        when(contactMapper.insert(any(TrustedContact.class))).thenAnswer(inv -> {
            inv.<TrustedContact>getArgument(0).setId(RELATION_ID);
            return 1;
        });
        when(contactMapper.selectRelated(OWNER_ID)).thenReturn(List.of(view(ContactStatus.PENDING)));

        ContactResponse response = service.invite(owner, new InviteContactRequest("contact", "弟弟"));

        ArgumentCaptor<TrustedContact> captor = ArgumentCaptor.forClass(TrustedContact.class);
        verify(contactMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ContactStatus.PENDING);
        assertThat(captor.getValue().getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(captor.getValue().getContactUserId()).isEqualTo(CONTACT_ID);

        assertThat(response.status()).isEqualTo(ContactStatus.PENDING);
        assertThat(response.iAmOwner()).isTrue();
        assertThat(response.canRespond()).isFalse();
    }

    @Test
    @DisplayName("不能邀请自己")
    void cannotInviteSelf() {
        when(userMapper.selectByUsername("owner")).thenReturn(user(OWNER_ID, "owner"));

        assertThatThrownBy(() -> service.invite(owner, new InviteContactRequest("owner", null)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能邀请自己");

        verify(contactMapper, never()).insert(any(TrustedContact.class));
    }

    @Test
    @DisplayName("邀请不存在的用户时提示对方先注册")
    void inviteUnknownUser() {
        when(userMapper.selectByUsername("nobody")).thenReturn(null);

        assertThatThrownBy(() -> service.invite(owner, new InviteContactRequest("nobody", null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("已存在待确认的邀请时拒绝重复邀请")
    void rejectDuplicateInvitation() {
        when(userMapper.selectByUsername("contact")).thenReturn(user(CONTACT_ID, "contact"));
        when(contactMapper.selectActivePair(OWNER_ID, CONTACT_ID))
                .thenReturn(relation(ContactStatus.PENDING, CONTACT_ID));

        assertThatThrownBy(() -> service.invite(owner, new InviteContactRequest("contact", null)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_REQUEST);

        verify(contactMapper, never()).insert(any(TrustedContact.class));
    }

    // ==================================================================
    // 接受 / 拒绝
    // ==================================================================

    @Test
    @DisplayName("被邀请人接受后关系生效")
    void acceptActivatesRelation() {
        when(contactMapper.selectById(RELATION_ID)).thenReturn(relation(ContactStatus.PENDING, CONTACT_ID));
        when(contactMapper.accept(eq(RELATION_ID), eq(CONTACT_ID), any())).thenReturn(1);

        service.accept(contact, RELATION_ID);

        verify(contactMapper).accept(eq(RELATION_ID), eq(CONTACT_ID), any());
    }

    @Test
    @DisplayName("非被邀请人不能接受别人的邀请，且不泄露其存在")
    void strangerCannotAccept() {
        when(contactMapper.selectById(RELATION_ID)).thenReturn(relation(ContactStatus.PENDING, CONTACT_ID));

        // owner 自己不是这条邀请的被邀请人
        assertThatThrownBy(() -> service.accept(owner, RELATION_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(contactMapper, never()).accept(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("已处理过的邀请不能被再次接受")
    void cannotAcceptProcessedInvitation() {
        when(contactMapper.selectById(RELATION_ID)).thenReturn(relation(ContactStatus.ACTIVE, CONTACT_ID));

        assertThatThrownBy(() -> service.accept(contact, RELATION_ID))
                .isInstanceOf(StateConflictException.class);

        verify(contactMapper, never()).accept(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("并发接受时只有一个能成功，另一个收到冲突")
    void concurrentAcceptYieldsConflict() {
        when(contactMapper.selectById(RELATION_ID)).thenReturn(relation(ContactStatus.PENDING, CONTACT_ID));
        // 模拟：读到 PENDING 之后，条件更新时状态已被另一个请求改掉
        when(contactMapper.accept(eq(RELATION_ID), eq(CONTACT_ID), any())).thenReturn(0);

        assertThatThrownBy(() -> service.accept(contact, RELATION_ID))
                .isInstanceOf(StateConflictException.class);
    }

    @Test
    @DisplayName("拒绝邀请后关系终止")
    void rejectEndsInvitation() {
        when(contactMapper.selectById(RELATION_ID)).thenReturn(relation(ContactStatus.PENDING, CONTACT_ID));
        when(contactMapper.reject(eq(RELATION_ID), eq(CONTACT_ID), any())).thenReturn(1);

        service.reject(contact, RELATION_ID);

        verify(contactMapper).reject(eq(RELATION_ID), eq(CONTACT_ID), any());
    }

    // ==================================================================
    // 撤销
    // ==================================================================

    @Test
    @DisplayName("所有者可以撤销生效中的关系")
    void ownerCanRevokeActiveRelation() {
        when(contactMapper.selectById(RELATION_ID)).thenReturn(relation(ContactStatus.ACTIVE, CONTACT_ID));
        when(contactMapper.revoke(eq(RELATION_ID), eq(OWNER_ID), any())).thenReturn(1);

        service.revoke(owner, RELATION_ID);

        verify(contactMapper).revoke(eq(RELATION_ID), eq(OWNER_ID), any());
    }

    @Test
    @DisplayName("非所有者不能撤销关系")
    void nonOwnerCannotRevoke() {
        when(contactMapper.selectById(RELATION_ID)).thenReturn(relation(ContactStatus.ACTIVE, CONTACT_ID));

        assertThatThrownBy(() -> service.revoke(contact, RELATION_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(contactMapper, never()).revoke(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("已结束的关系不能再撤销")
    void cannotRevokeFinishedRelation() {
        when(contactMapper.selectById(RELATION_ID)).thenReturn(relation(ContactStatus.REVOKED, CONTACT_ID));

        assertThatThrownBy(() -> service.revoke(owner, RELATION_ID))
                .isInstanceOf(StateConflictException.class);
    }

    // ==================================================================
    // 列表
    // ==================================================================

    @Test
    @DisplayName("列表区分'我邀请的'与'邀请我的'")
    void listMarksRoleForCurrentUser() {
        ContactView asOwner = view(ContactStatus.ACTIVE);
        ContactView asContact = new ContactView();
        asContact.setId(20L);
        asContact.setOwnerId(CONTACT_ID);
        asContact.setContactUserId(OWNER_ID);
        asContact.setStatus(ContactStatus.PENDING);

        when(contactMapper.selectRelated(OWNER_ID)).thenReturn(List.of(asOwner, asContact));

        List<ContactResponse> result = service.list(owner);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).iAmOwner()).isTrue();
        assertThat(result.get(1).iAmOwner()).isFalse();
        assertThat(result.get(1).canRespond()).isTrue();   // 有待处理的邀请
    }
}
