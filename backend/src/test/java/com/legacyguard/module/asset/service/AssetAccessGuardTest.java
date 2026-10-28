package com.legacyguard.module.asset.service;

import com.legacyguard.module.asset.domain.DigitalAsset;
import com.legacyguard.module.asset.mapper.AssetAccessMapper;
import com.legacyguard.module.asset.mapper.DigitalAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 访问守卫测试。
 *
 * <p>这是"未授权不得访问"这条原则的验收点 —— 这里的每个分支都对应
 * 一种可能被攻击者利用的情形。
 */
@ExtendWith(MockitoExtension.class)
class AssetAccessGuardTest {

    private static final Long OWNER_ID = 100L;
    private static final Long STRANGER_ID = 200L;
    private static final Long ASSET_ID = 1L;

    @Mock
    private DigitalAssetMapper assetMapper;
    @Mock
    private AssetAccessMapper accessMapper;

    private AssetAccessGuard guard;

    @BeforeEach
    void setUp() {
        guard = new AssetAccessGuard(assetMapper, accessMapper);
    }

    private DigitalAsset assetOwnedBy(Long ownerId, boolean deleted) {
        DigitalAsset asset = new DigitalAsset();
        asset.setId(ASSET_ID);
        asset.setOwnerId(ownerId);
        asset.setDeletedAt(deleted ? LocalDateTime.now() : null);
        return asset;
    }

    @Test
    @DisplayName("资产不存在时拒绝，且原因记为 NOT_FOUND")
    void missingAssetDenied() {
        when(assetMapper.selectById(ASSET_ID)).thenReturn(null);

        AccessResult result = guard.evaluate(ASSET_ID, STRANGER_ID);

        assertThat(result.allowed()).isFalse();
        assertThat(result.denyReason()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("已删除的资产对所有人不可见 —— 包括所有者")
    void deletedAssetDeniedEvenForOwner() {
        when(assetMapper.selectById(ASSET_ID)).thenReturn(assetOwnedBy(OWNER_ID, true));

        AccessResult result = guard.evaluate(ASSET_ID, OWNER_ID);

        assertThat(result.allowed()).isFalse();
        assertThat(result.denyReason()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("所有者本人直接放行")
    void ownerAllowed() {
        when(assetMapper.selectById(ASSET_ID)).thenReturn(assetOwnedBy(OWNER_ID, false));

        AccessResult result = guard.evaluate(ASSET_ID, OWNER_ID);

        assertThat(result.allowed()).isTrue();
        assertThat(result.accessType()).isEqualTo(AccessResult.OWNER_VIEW);
    }

    @Test
    @DisplayName("存在生效授权案件时，联系人可以访问并记录案件 ID")
    void contactWithActiveGrantAllowed() {
        when(assetMapper.selectById(ASSET_ID)).thenReturn(assetOwnedBy(OWNER_ID, false));
        when(accessMapper.findActiveGrantCaseId(ASSET_ID, STRANGER_ID)).thenReturn(777L);

        AccessResult result = guard.evaluate(ASSET_ID, STRANGER_ID);

        assertThat(result.allowed()).isTrue();
        assertThat(result.accessType()).isEqualTo(AccessResult.CASE_VIEW);
        assertThat(result.caseId()).isEqualTo(777L);
    }

    @Test
    @DisplayName("没有授权关系的陌生人被拒绝")
    void strangerDenied() {
        when(assetMapper.selectById(ASSET_ID)).thenReturn(assetOwnedBy(OWNER_ID, false));
        when(accessMapper.findActiveGrantCaseId(ASSET_ID, STRANGER_ID)).thenReturn(null);

        AccessResult result = guard.evaluate(ASSET_ID, STRANGER_ID);

        assertThat(result.allowed()).isFalse();
        assertThat(result.denyReason()).isEqualTo("NO_ACTIVE_GRANT");
    }
}
