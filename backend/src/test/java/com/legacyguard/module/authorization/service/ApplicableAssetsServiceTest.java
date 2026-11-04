package com.legacyguard.module.authorization.service;

import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.module.asset.domain.DigitalAsset;
import com.legacyguard.module.asset.mapper.DigitalAssetMapper;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.module.authorization.config.AuthorizationProperties;
import com.legacyguard.module.authorization.domain.AuthorizationCase;
import com.legacyguard.module.authorization.domain.CaseStatus;
import com.legacyguard.module.authorization.dto.ApplicableAssetsResponse;
import com.legacyguard.module.authorization.dto.CaseDetailResponse;
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
import com.legacyguard.infra.outbox.OutboxService;
import com.legacyguard.module.user.domain.UserRole;
import com.legacyguard.security.AuthPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 申请前查询与案件资产元数据的测试。
 *
 * <p>这里验证的是"界面能不能拿到正确数据"，不是权限判定本身：
 * 创建接口仍会重新执行五道校验。
 */
@ExtendWith(MockitoExtension.class)
class ApplicableAssetsServiceTest {

    private static final Long OWNER_ID = 100L;
    private static final Long APPLICANT_ID = 200L;
    private static final Long CONTACT_ID = 5L;
    private static final Long CASE_ID = 10L;
    private static final Long ASSET_ID = 50L;

    @Mock private AuthorizationCaseMapper caseMapper;
    @Mock private AuthorizationAssetMapper caseAssetMapper;
    @Mock private AuthorizationApprovalMapper approvalMapper;
    @Mock private DisputeRecordMapper disputeMapper;
    @Mock private TrustedContactMapper contactMapper;
    @Mock private HeartbeatConfigMapper heartbeatMapper;
    @Mock private DigitalAssetMapper assetMapper;
    @Mock private AuditService auditService;
    @Mock private OutboxService outboxService;

    private AuthorizationCaseService service;
    private AuthPrincipal applicant;

    @BeforeEach
    void setUp() {
        service = new AuthorizationCaseService(caseMapper, caseAssetMapper, approvalMapper,
                disputeMapper, contactMapper, heartbeatMapper, assetMapper, auditService,
                new CaseNoGenerator(), new AuthorizationProperties(7, 100), outboxService);

        applicant = new AuthPrincipal(APPLICANT_ID, "applicant", UserRole.USER, "jti-c");
    }

    @Test
    @DisplayName("查询别人的关系返回 404，不泄露关系存在性")
    void rejectsForeignContact() {
        TrustedContact others = contact(ContactStatus.ACTIVE);
        others.setContactUserId(999L);
        when(contactMapper.selectById(CONTACT_ID)).thenReturn(others);

        assertThatThrownBy(() -> service.applicableAssets(applicant, CONTACT_ID))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("关系尚未生效时不可申请，且不查询资产")
    void inactiveContactCannotApply() {
        when(contactMapper.selectById(CONTACT_ID)).thenReturn(contact(ContactStatus.PENDING));

        ApplicableAssetsResponse result = service.applicableAssets(applicant, CONTACT_ID);

        assertThat(result.canApply()).isFalse();
        assertThat(result.blockReason()).contains("尚未生效");
        assertThat(result.assets()).isEmpty();
        verify(assetMapper, never()).selectByOwner(any());
    }

    @Test
    @DisplayName("所有者未完成失联核验时不可申请")
    void ownerNotReviewing() {
        when(contactMapper.selectById(CONTACT_ID)).thenReturn(contact(ContactStatus.ACTIVE));
        when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.REMINDING));

        ApplicableAssetsResponse result = service.applicableAssets(applicant, CONTACT_ID);

        assertThat(result.canApply()).isFalse();
        assertThat(result.blockReason()).contains("失联核验");
        assertThat(result.assets()).isEmpty();
    }

    @Test
    @DisplayName("已有未终结案件时不能重复申请")
    void existingOpenCaseBlocks() {
        when(contactMapper.selectById(CONTACT_ID)).thenReturn(contact(ContactStatus.ACTIVE));
        when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.REVIEWING));
        when(caseMapper.countOpenByOwnerAndContact(OWNER_ID, CONTACT_ID)).thenReturn(1);

        ApplicableAssetsResponse result = service.applicableAssets(applicant, CONTACT_ID);

        assertThat(result.canApply()).isFalse();
        assertThat(result.blockReason()).contains("未结束");
        assertThat(result.assets()).isEmpty();
    }

    @Test
    @DisplayName("可申请时返回资产元数据，并标记被其他案件占用的资产")
    void returnsAssetsWithOccupiedFlag() {
        when(contactMapper.selectById(CONTACT_ID)).thenReturn(contact(ContactStatus.ACTIVE));
        when(heartbeatMapper.selectById(OWNER_ID)).thenReturn(heartbeat(HeartbeatStatus.REVIEWING));
        when(caseMapper.countOpenByOwnerAndContact(OWNER_ID, CONTACT_ID)).thenReturn(0);

        DigitalAsset free = asset(ASSET_ID, "备份说明");
        DigitalAsset taken = asset(ASSET_ID + 1, "账号提示");
        when(assetMapper.selectByOwner(OWNER_ID)).thenReturn(List.of(free, taken));
        when(caseAssetMapper.selectOccupiedAssetIds(OWNER_ID)).thenReturn(List.of(taken.getId()));

        ApplicableAssetsResponse result = service.applicableAssets(applicant, CONTACT_ID);

        assertThat(result.canApply()).isTrue();
        assertThat(result.blockReason()).isNull();
        assertThat(result.assets()).hasSize(2);

        ApplicableAssetsResponse.ApplicableAssetItem freeItem = result.assets().stream()
                .filter(item -> item.id().equals(ASSET_ID)).findFirst().orElseThrow();
        ApplicableAssetsResponse.ApplicableAssetItem takenItem = result.assets().stream()
                .filter(item -> item.id().equals(taken.getId())).findFirst().orElseThrow();

        assertThat(freeItem.occupied()).isFalse();
        assertThat(freeItem.name()).isEqualTo("备份说明");
        assertThat(takenItem.occupied()).isTrue();
    }

    @Test
    @DisplayName("案件详情对申请人返回资产名称，客户端不需要再查资产列表")
    void detailIncludesAssetMetadata() {
        AuthorizationCase entity = caseIn(CaseStatus.DRAFT);
        when(caseMapper.selectById(CASE_ID)).thenReturn(entity);
        when(contactMapper.selectById(CONTACT_ID)).thenReturn(contact(ContactStatus.ACTIVE));
        when(caseAssetMapper.selectAssetIds(CASE_ID)).thenReturn(List.of(ASSET_ID));
        when(assetMapper.selectBatchIds(any())).thenReturn(List.of(asset(ASSET_ID, "备份说明")));
        when(approvalMapper.selectByCase(CASE_ID)).thenReturn(List.of());

        CaseDetailResponse detail = service.detail(applicant, CASE_ID);

        assertThat(detail.iAmApplicant()).isTrue();
        assertThat(detail.assets()).singleElement().satisfies(asset -> {
            assertThat(asset.id()).isEqualTo(ASSET_ID);
            assertThat(asset.name()).isEqualTo("备份说明");
            assertThat(asset.deleted()).isFalse();
        });
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
        h.setCycleNo(1);
        return h;
    }

    private DigitalAsset asset(Long id, String name) {
        DigitalAsset a = new DigitalAsset();
        a.setId(id);
        a.setOwnerId(OWNER_ID);
        a.setName(name);
        a.setAssetType("NOTE");
        return a;
    }

    private AuthorizationCase caseIn(String status) {
        AuthorizationCase c = new AuthorizationCase();
        c.setId(CASE_ID);
        c.setCaseNo("LG-20260101-ABC123");
        c.setOwnerId(OWNER_ID);
        c.setContactId(CONTACT_ID);
        c.setStatus(status);
        c.setVersion(0);
        return c;
    }
}
