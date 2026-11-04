package com.legacyguard.module.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.asset.domain.DigitalAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资产元数据访问。
 */
@Mapper
public interface DigitalAssetMapper extends BaseMapper<DigitalAsset> {

    /**
     * 加行锁读取未删除的资产。
     *
     * <p>用于创建授权案件时"检查这些资产是否已被占用的一个有效案件覆盖"。
     * 跨表条件唯一无法用索引表达，只能靠行锁把并发请求串行化，
     * 详见 docs/03 第 3.3.3 节。
     */
    @Select("""
            <script>
            SELECT * FROM digital_asset
             WHERE id IN
             <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
               AND deleted_at IS NULL
             FOR UPDATE
            </script>
            """)
    List<DigitalAsset> selectForUpdate(@Param("ids") List<Long> ids);

    /**
     * 列出某用户名下未删除的资产。
     *
     * <p>仅供"可申请资产"查询使用：联系人本身不是所有者，
     * 不能通过资产列表接口拿到这份清单。返回的是元数据实体，
     * 调用方必须转成 DTO 后才能对外，不得把实体直接序列化。
     */
    @Select("""
            SELECT * FROM digital_asset
             WHERE owner_id = #{ownerId} AND deleted_at IS NULL
             ORDER BY updated_at DESC, id DESC
            """)
    List<DigitalAsset> selectByOwner(@Param("ownerId") Long ownerId);

    /**
     * 统计某用户当前有效资产数量，用于配额检查。
     */
    @Select("""
            SELECT COUNT(*) FROM digital_asset
             WHERE owner_id = #{ownerId} AND deleted_at IS NULL
            """)
    long countActiveByOwner(@Param("ownerId") Long ownerId);

    /**
     * 软删除。条件里带 {@code deleted_at IS NULL}，重复删除影响 0 行而不是报错。
     */
    @Update("""
            UPDATE digital_asset
               SET status = 2, deleted_at = #{deletedAt}, version = version + 1, updated_at = #{deletedAt}
             WHERE id = #{id} AND owner_id = #{ownerId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("id") Long id,
                   @Param("ownerId") Long ownerId,
                   @Param("deletedAt") LocalDateTime deletedAt);
}
