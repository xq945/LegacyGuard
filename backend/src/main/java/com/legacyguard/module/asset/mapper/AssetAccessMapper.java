package com.legacyguard.module.asset.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 资产访问判定所需的查询。
 *
 * <p>单独一个 Mapper 而不是塞进资产 Mapper：这里的查询跨越
 * {@code authorization_case} / {@code authorization_asset} / {@code trusted_contact}
 * 三张表，语义上是"授权关系"，不是"资产"。
 */
@Mapper
public interface AssetAccessMapper {

    /**
     * 查询某人因为哪个<b>生效中</b>的授权案件而获得了该资产访问权。
     *
     * <p>三个条件缺一不可：
     * <ul>
     *   <li>{@code c.status = 'AUTHORIZED'} —— 只有生效的授权才放行</li>
     *   <li>{@code c.revoked_at IS NULL} —— 撤销后即使状态字段还没更新也不能放行（双保险）</li>
     *   <li>联系人身份匹配当前用户 —— 别人的案件不算数</li>
     * </ul>
     *
     * @return 案件 ID；没有任何生效授权时返回 {@code null}
     */
    @Select("""
            SELECT c.id
              FROM authorization_case c
              JOIN authorization_asset aa ON aa.case_id = c.id
              JOIN trusted_contact tc ON tc.id = c.contact_id
             WHERE aa.asset_id = #{assetId}
               AND tc.contact_user_id = #{userId}
               AND c.status = 'AUTHORIZED'
               AND c.revoked_at IS NULL
             LIMIT 1
            """)
    Long findActiveGrantCaseId(@Param("assetId") Long assetId, @Param("userId") Long userId);

    /**
     * 统计引用该资产的<b>未终结</b>案件数量。
     *
     * <p>用于删除前的检查：资产一旦进入授权流程，就不能被所有者随手删掉，
     * 否则案件会指向一个不存在的对象，审批结果也就失去了意义。
     */
    @Select("""
            SELECT COUNT(*)
              FROM authorization_asset aa
              JOIN authorization_case c ON c.id = aa.case_id
             WHERE aa.asset_id = #{assetId}
               AND c.status NOT IN ('REJECTED', 'CANCELLED', 'REVOKED')
            """)
    int countOpenCases(@Param("assetId") Long assetId);
}
