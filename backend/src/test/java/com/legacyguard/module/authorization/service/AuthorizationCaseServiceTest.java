package com.legacyguard.module.authorization.service;

import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.common.exception.StateConflictException;
import com.legacyguard.module.asset.domain.DigitalAsset;
import com.legacyguard.module.asset.mapper.DigitalAssetMapper;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.module.authorization.config.AuthorizationProperties;
import com.legacyguard.module.authorization.domain.AuthorizationCase;
import com.legacyguard.module.authorization.domain.CaseStatus;
import com.legacyguard.module.authorization.dto.ApprovalRequest;
import com.legacyguard.module.authorization.dto.CreateCaseRequest;
import com.legacyguard.module.authorization.dto.DisputeRequest;
import com.legacyguard.module.authorization.mapper.AuthorizationApprovalMapper;
import com.legacyguard.module.authorization.mapper.AuthorizationAssetMapper;
import com.legacyguard.module.authorization.mapper.AuthorizationCaseMapper;
import com.legacyguard.module.authorization.mapper.DisputeRecordMapper;
import com.legacyguard.module.authorization.support.CaseNoGenerator;
import com.legacyguard.module.contact.domain.ContactStatus;
import com.legacyguard.module.contact.domain.TrustedContact;
import com.legacyguard.module.contact.mapper.TrustedContactMapper;
import com.legacyguard.module.heartbeat.domain.HeartbeatConfig;
import com.legacyguard.module.heartbeat.domain.HeartbeatStatus;
import com.legacyguard.module.heartbeat.mapper.HeartbeatConfigMapper;
import com.legacyguard.module.notification.service.NotificationService;
import com.legacyguard.module.user.domain.UserRole;
import com.legacyguard.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 授权案件状态机测试。
 *
 * <p>重点覆盖三类最容易出错的地方：
 * <ol>
 *   <li>五种前置条件缺一不可</li>
 *   <li>条件更新影响 0 行必须当作冲突，而不是当作成功</li>
 *   <li>生效前的复核 —— 任何一条前提消失，案件就不能生效</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationCaseServiceTest {

    private static final Long OWNER_ID = 100L;
    private static final Long APPLICANT_ID = 200L;
    private static final Long ADMIN_ID = 1L;
    private static final Long CASE_ID = 10L;
    private static final Long CONTACT_ID = 5L;
    private static final Long ASSET_ID = 50L;

    @Mock private AuthorizationCaseMapper caseMapper;
    @Mock private AuthorizationAssetMapper caseAssetMapper;
    @Mock private AuthorizationApprovalMapper approvalMapper;
    @Mock private DisputeRecordMapper disputeMapper;
    @Mock private TrustedContactMapper contactMapper;
    @Mock private HeartbeatConfigMapper heartbeatMapper;
    @Mock private DigitalAssetMapper assetMapper;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    private AuthorizationCaseService service;
    private AuthPrincipal owner;
    private AuthPrincipal applicant;
    private AuthPrincipal admin;

    @BeforeEach
    void setUp() {
        service = new AuthorizationCaseService(caseMapper, caseAssetMapper, approvalMapper,
                disputeMapper, contactMapper, heartbeatMapper, assetMapper, auditService,
                new CaseNoGenerator(), new AuthorizationProperties(7, 100), notificationService);

        owner = new AuthPrincipal(OWNER_ID, "owner", UserRole.USER, "jti-o");
        applicant = new AuthPrincipal(APPLICANT_ID, "applicant", UserRole.USER, "jti-c");
        admin = new AuthPrincipal(ADMIN_ID, "admin", UserRole.ADMIN, "jti-a");

        lenient().when(caseAssetMapper.selectAssetIds(any())).thenReturn(List.of(ASSET_ID));
        lenient().when(approvalMapper.selectByCase(any())).thenReturn(List.of());
        lenient().when(contactMapper.selectById(CONTACT_ID)).thenReturn(contact(ContactStatus.ACTIVE));
    }

    // ==================== 测试夹具 ====================

    private TrustedContact contact(String status) {
        TrustedContact c = new TrustedContact();
        c.setId(CONTACT_ID);
        c.setOwnerId(OWNER_ID);
        c.setContactUserId(APPLICANT_ID);
        c.setStatus(status);
        return c;
    }

    private HeartbeatConfig heartbeat(String status) {
        HeartbeatConfig h = new HeartbeatConfig();
        h.setUserId(OWNER_ID);
        h.setStatus(status);
        h.setCycleNo(3);
        return h;
    }

    private DigitalAsset asset(Long ownerId) {
        DigitalAsset a = new DigitalAsset();
        a.setId(ASSET_ID);
        a.setOwnerId(ownerId);
        return a;
    }

    private AuthorizationCase caseIn(String status) {
        AuthorizationCase c = new AuthorizationCase();
        c.setId(CASE_ID);
        c.setCaseNo("LG-20260101-ABC123");
        c.setOwnerId(OWNER_ID);
        c.setContactId(CONTACT_ID);
        c.setStatus(status);
        c.setVersion(4);
        c.setWaitPeriodEndAt(LocalDateTime.now().minusMinutes(1));
        return c;
    }

    private CreateCaseRequest createRequest() {
        return new CreateCaseRequest(CONTACT_ID, List.of(ASSET_ID), "申请理由");
    }

    // ==================================================================

    @Nested
    @DisplayName("创建案件的前置条件")
    class CreatePreconditions {

        @Test
        @DisplayName("联系人关系未生效时拒绝")
        void rejectsInactiveContact() {
            when(contactMapper.selectById(CONTACT_ID)).thenReturn(contact(ContactStatus.PENDING));

            assertThatThrownBy(() -> service.create(applicant, createRequest()))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BIZ_RULE_VIOLATION);

            verify(caseMapper, never()).insert(any(AuthorizationCase.class));
        }

        @Test
        @DisplayName("不是自己的联系人关系时返回 404，不泄露他人关系")
        void rejectsForeignContact() {
            TrustedContact others = contact(ContactStatus.ACTIVE);
            others.setContactUserId(999L);
            when(contactMapper.selectById(CONTACT_ID)).thenReturn(others);

            assertThatThrownBy(() -> service.create(applicant, createRequest()))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("所有者未通过失联核验时拒绝 —— 超时不等于可申请")
        void rejectsWhenOwnerNotReviewing() {
            when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.REMINDING));

            assertThatThrownBy(() -> service.create(applicant, createRequest()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("尚未完成失联核验");

            verify(caseMapper, never()).insert(any(AuthorizationCase.class));
        }

        @Test
        @DisplayName("资产不属于该所有者时拒绝")
        void rejectsForeignAsset() {
            when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.REVIEWING));
            when(assetMapper.selectForUpdate(any())).thenReturn(List.of(asset(888L)));

            assertThatThrownBy(() -> service.create(applicant, createRequest()))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("资产已被其他进行中的案件占用时拒绝")
        void rejectsClaimedAsset() {
            when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.REVIEWING));
            when(assetMapper.selectForUpdate(any())).thenReturn(List.of(asset(OWNER_ID)));
            when(caseAssetMapper.countActiveCasesCovering(any())).thenReturn(1);

            assertThatThrownBy(() -> service.create(applicant, createRequest()))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("已被其他进行中的授权案件占用");
        }
    }

    @Nested
    @DisplayName("状态迁移的并发保护")
    class StateTransitions {

        @Test
        @DisplayName("条件更新影响 0 行时抛冲突，绝不当作成功")
        void zeroRowsMeansConflict() {
            when(caseMapper.selectById(CASE_ID)).thenReturn(caseIn(CaseStatus.DRAFT));
            when(caseMapper.submit(eq(CASE_ID), eq(4), any())).thenReturn(0);

            assertThatThrownBy(() -> service.submit(applicant, CASE_ID))
                    .isInstanceOf(StateConflictException.class);
        }

        @Test
        @DisplayName("终态案件不能再被受理")
        void terminalCaseCannotBeAccepted() {
            when(caseMapper.selectById(CASE_ID)).thenReturn(caseIn(CaseStatus.REVOKED));

            assertThatThrownBy(() -> service.accept(admin, CASE_ID))
                    .isInstanceOf(StateConflictException.class)
                    .hasMessageContaining("已结束");
        }

        @Test
        @DisplayName("批准只推进到等待期，不是直接授权")
        void approveMovesToWaitingNotAuthorized() {
            when(caseMapper.selectById(CASE_ID)).thenReturn(caseIn(CaseStatus.REVIEWING));
            when(caseMapper.approveToWaiting(eq(CASE_ID), eq(4), any(), any(), any())).thenReturn(1);

            service.approve(admin, CASE_ID, new ApprovalRequest("材料齐全"));

            verify(caseMapper).approveToWaiting(eq(CASE_ID), eq(4), any(), any(), any());
            verify(caseMapper, never()).authorize(any(), any(), any());
        }

        @Test
        @DisplayName("只有所有者能撤销已生效授权")
        void onlyOwnerCanRevoke() {
            when(caseMapper.selectById(CASE_ID)).thenReturn(caseIn(CaseStatus.AUTHORIZED));

            assertThatThrownBy(() -> service.revoke(applicant, CASE_ID))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);

            verify(caseMapper, never()).revoke(any(), any(), any());
        }

        @Test
        @DisplayName("等待期外的案件不能提异议")
        void disputeOnlyDuringWaitingPeriod() {
            when(caseMapper.selectById(CASE_ID)).thenReturn(caseIn(CaseStatus.PENDING_REVIEW));

            assertThatThrownBy(() -> service.raiseDispute(owner, CASE_ID, new DisputeRequest("异议")))
                    .isInstanceOf(StateConflictException.class)
                    .hasMessageContaining("等待期");
        }
    }

    @Nested
    @DisplayName("生效前的全条件复核")
    class AuthorizationGuard {

        @Test
        @DisplayName("所有者已恢复确认，案件不能生效并自动取消")
        void cancelsWhenOwnerRecovered() {
            when(caseMapper.selectById(CASE_ID)).thenReturn(caseIn(CaseStatus.WAITING_PERIOD));
            when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.ACTIVE));
            when(caseMapper.cancel(eq(CASE_ID), eq(CaseStatus.WAITING_PERIOD), eq(4), any(), any()))
                    .thenReturn(1);

            boolean authorized = service.authorizeIfAllConditionsMet(CASE_ID, LocalDateTime.now());

            assertThat(authorized).isFalse();
            verify(caseMapper, never()).authorize(any(), any(), any());
            verify(caseMapper).cancel(eq(CASE_ID), eq(CaseStatus.WAITING_PERIOD), eq(4), any(), any());
        }

        @Test
        @DisplayName("联系人关系已失效，案件不能生效")
        void cancelsWhenContactRevoked() {
            when(caseMapper.selectById(CASE_ID)).thenReturn(caseIn(CaseStatus.WAITING_PERIOD));
            when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.REVIEWING));
            when(contactMapper.selectById(CONTACT_ID)).thenReturn(contact(ContactStatus.REVOKED));
            when(caseMapper.cancel(any(), any(), any(), any(), any())).thenReturn(1);

            assertThat(service.authorizeIfAllConditionsMet(CASE_ID, LocalDateTime.now())).isFalse();

            verify(caseMapper, never()).authorize(any(), any(), any());
        }

        @Test
        @DisplayName("存在未处理异议时不能生效")
        void doesNotAuthorizeWithOpenDispute() {
            when(caseMapper.selectById(CASE_ID)).thenReturn(caseIn(CaseStatus.WAITING_PERIOD));
            when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.REVIEWING));
            when(assetMapper.selectById(ASSET_ID)).thenReturn(asset(OWNER_ID));
            when(disputeMapper.countOpen(CASE_ID)).thenReturn(1);

            assertThat(service.authorizeIfAllConditionsMet(CASE_ID, LocalDateTime.now())).isFalse();

            verify(caseMapper, never()).authorize(any(), any(), any());
        }

        @Test
        @DisplayName("全部条件满足时才生效")
        void authorizesWhenAllConditionsHold() {
            when(caseMapper.selectById(CASE_ID)).thenReturn(caseIn(CaseStatus.WAITING_PERIOD));
            when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.REVIEWING));
            when(assetMapper.selectById(ASSET_ID)).thenReturn(asset(OWNER_ID));
            when(disputeMapper.countOpen(CASE_ID)).thenReturn(0);
            when(caseMapper.countOtherAuthorizedCases(ASSET_ID, CASE_ID)).thenReturn(0);
            when(caseMapper.authorize(eq(CASE_ID), eq(4), any())).thenReturn(1);

            boolean authorized = service.authorizeIfAllConditionsMet(CASE_ID, LocalDateTime.now());

            assertThat(authorized).isTrue();
            verify(caseMapper).authorize(eq(CASE_ID), eq(4), any());
        }

        @Test
        @DisplayName("生效时被并发修改（影响 0 行）则放弃，不重试")
        void givesUpWhenConcurrentlyChanged() {
            when(caseMapper.selectById(CASE_ID)).thenReturn(caseIn(CaseStatus.WAITING_PERIOD));
            when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.REVIEWING));
            when(assetMapper.selectById(ASSET_ID)).thenReturn(asset(OWNER_ID));
            when(disputeMapper.countOpen(CASE_ID)).thenReturn(0);
            when(caseMapper.countOtherAuthorizedCases(ASSET_ID, CASE_ID)).thenReturn(0);
            // 状态在读取后被并发改掉
            when(caseMapper.authorize(eq(CASE_ID), eq(4), any())).thenReturn(0);

            assertThat(service.authorizeIfAllConditionsMet(CASE_ID, LocalDateTime.now())).isFalse();
        }

        @Test
        @DisplayName("等待期未到时不做任何处理")
        void skipsBeforeDeadline() {
            AuthorizationCase notDue = caseIn(CaseStatus.WAITING_PERIOD);
            notDue.setWaitPeriodEndAt(LocalDateTime.now().plusDays(3));
            when(caseMapper.selectById(CASE_ID)).thenReturn(notDue);

            assertThat(service.authorizeIfAllConditionsMet(CASE_ID, LocalDateTime.now())).isFalse();

            verify(heartbeatMapper, never()).selectById(anyLong());
        }
    }

    @Test
    @DisplayName("用户恢复确认时作废其所有未生效案件")
    void recoversCancelsPendingCases() {
        when(caseMapper.cancelAllPendingByOwner(eq(OWNER_ID), any())).thenReturn(2);

        service.onRecovered(OWNER_ID);

        verify(caseMapper).cancelAllPendingByOwner(eq(OWNER_ID), any());
    }
}
