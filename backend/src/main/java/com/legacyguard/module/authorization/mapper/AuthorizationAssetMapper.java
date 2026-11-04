package com.legacyguard.module.authorization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.authorization.domain.AuthorizationAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 案件与资产关联的数据访问。
 */
@Mapper
public interface AuthorizationAssetMapper extends BaseMapper<AuthorizationAsset> {

    @Select("SELECT asset_id FROM authorization_asset WHERE case_id = #{caseId} ORDER BY asset_id")
    List<Long> selectAssetIds(@Param("caseId") Long caseId);

    /**
     * 统计覆盖指定资产的<b>未终结</b>案件数量。
     *
     * <p>创建案件前用它对资产加锁检查：一个资产同时只能被一个有效案件占用。
     * 这是 docs/03 第 3.3.3 节说明的"跨表条件唯一无法用索引表达"的补偿方案 ——
     * 配合对资产行的 {@code FOR UPDATE}，把并发请求串行化。
     */
    @Select("""
            <script>
            SELECT COUNT(*)
              FROM authorization_asset aa
              JOIN authorization_case c ON c.id = aa.case_id
             WHERE aa.asset_id IN
             <foreach item="id" collection="assetIds" open="(" separator="," close=")">#{id}</foreach>
               AND c.status NOT IN ('REJECTED', 'CANCELLED', 'REVOKED')
            </script>
            """)
    int countActiveCasesCovering(@Param("assetIds") List<Long> assetIds);

    /**
     * 列出某所有者名下已被未终结案件占用的资产 ID。
     *
     * <p>同一个资产同时只能被一个有效案件覆盖；这些资产在申请对话框里
     * 应当显示为不可选，而不是等用户提交后再报 422。
     */
    @Select("""
            SELECT DISTINCT aa.asset_id
              FROM authorization_asset aa
              JOIN authorization_case c ON c.id = aa.case_id
              JOIN digital_asset d ON d.id = aa.asset_id
             WHERE d.owner_id = #{ownerId}
               AND c.status NOT IN ('REJECTED', 'CANCELLED', 'REVOKED')
            """)
    List<Long> selectOccupiedAssetIds(@Param("ownerId") Long ownerId);

    /**
     * 查询某个案件覆盖的资产及其名称，供详情页展示。
     */
    @Select("""
            SELECT aa.asset_id
              FROM authorization_asset aa
             WHERE aa.case_id = #{caseId}
             ORDER BY aa.asset_id
            """)
    List<Long> listAssetIds(@Param("caseId") Long caseId);
}
