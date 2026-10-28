package com.legacyguard.module.asset.service;

import com.legacyguard.module.asset.domain.DigitalAsset;
import com.legacyguard.module.asset.mapper.AssetAccessMapper;
import com.legacyguard.module.asset.mapper.DigitalAssetMapper;
import org.springframework.stereotype.Component;

/**
 * 资产访问守卫。
 *
 * <p><b>这是"未经授权的用户不得访问敏感资产"这条原则的唯一落点。</b>
 * 所有读取资产内容的路径都必须先经过这里，不存在第二条判断权限的代码。
 *
 * <p><b>这里没有任何缓存，这是刻意的。</b>每次判定都查数据库 ——
 * 如果缓存"这个人能看这份资产"，那么撤销授权后，用户在缓存过期前仍然能继续访问，
 * 而需求明确要求"撤销后必须立即阻止后续访问"。一次查询的代价远小于一次越权。
 *
 * <p>判定顺序（从前到后，一旦命中即返回）：
 * <ol>
 *   <li>资产不存在或已删除 → 拒绝（返回 404 语义，不泄露"存在但无权"）</li>
 *   <li>是所有者本人 → 放行（OWNER_VIEW）</li>
 *   <li>存在以自己为联系人的生效授权案件 → 放行（CASE_VIEW）</li>
 *   <li>其余一律拒绝</li>
 * </ol>
 */
@Component
public class AssetAccessGuard {

    private final DigitalAssetMapper assetMapper;
    private final AssetAccessMapper accessMapper;

    public AssetAccessGuard(DigitalAssetMapper assetMapper, AssetAccessMapper accessMapper) {
        this.assetMapper = assetMapper;
        this.accessMapper = accessMapper;
    }

    /**
     * 判定某用户能否读取该资产。
     *
     * <p>本方法<b>不抛异常</b>，只返回结论。调用方据此决定：
     * 允许则继续，拒绝则记录流水并抛出 403/404。
     * 这样"判定"与"响应"分开，便于在测试里直接断言判定结果。
     */
    public AccessResult evaluate(Long assetId, Long userId) {
        DigitalAsset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.isDeleted()) {
            // 已删除的资产对外就是"不存在"，不区分"存在但被删"
            return AccessResult.denied("NOT_FOUND");
        }

        if (asset.getOwnerId().equals(userId)) {
            return AccessResult.owner();
        }

        Long caseId = accessMapper.findActiveGrantCaseId(assetId, userId);
        if (caseId != null) {
            return AccessResult.viaCase(caseId);
        }

        return AccessResult.denied("NO_ACTIVE_GRANT");
    }
}
