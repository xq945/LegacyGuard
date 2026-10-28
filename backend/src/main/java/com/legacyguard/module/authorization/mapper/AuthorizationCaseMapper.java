package com.legacyguard.module.authorization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.authorization.domain.AuthorizationCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 授权案件数据访问。
 *
 * <p><b>本接口里的每一条状态更新都同时带 {@code status} 与 {@code version} 条件，
 * 并且调用方必须检查影响行数。</b>影响 0 行意味着"状态已经被别人改了"，
 * 必须当作冲突处理，而不是当作成功继续往下走。
 *
 * <p>这样写的好处：两个管理员同时审批时，只有一个的 UPDATE 能命中，
 * 另一个必然是 0 行。不需要悲观锁，也不会出现"先批准又被驳回"。
 */
@Mapper
public interface AuthorizationCaseMapper extends BaseMapper<AuthorizationCase> {

    /**
     * 加行锁读取案件，用于需要"先检查再决定"的复合操作。
     */
    @Select("SELECT * FROM authorization_case WHERE id = #{id} FOR UPDATE")
    AuthorizationCase selectForUpdate(@Param("id") Long id);

    /**
     * 通用状态迁移。
     */
    @Update("""
            UPDATE authorization_case
               SET status = #{toStatus}, version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND status = #{fromStatus} AND version = #{version}
            """)
    int transition(@Param("id") Long id,
                   @Param("fromStatus") String fromStatus,
                   @Param("toStatus") String toStatus,
                   @Param("version") Integer version,
                   @Param("now") LocalDateTime now);

    /**
     * 提交审核：DRAFT → PENDING_REVIEW。
     */
    @Update("""
            UPDATE authorization_case
               SET status = 'PENDING_REVIEW', version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND status = 'DRAFT' AND version = #{version}
            """)
    int submit(@Param("id") Long id, @Param("version") Integer version, @Param("now") LocalDateTime now);

    /**
     * 取消：进入终态 CANCELLED。
     */
    @Update("""
            UPDATE authorization_case
               SET status = 'CANCELLED', closed_at = #{now}, reason = COALESCE(#{reason}, reason),
                   version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND status = #{fromStatus} AND version = #{version}
            """)
    int cancel(@Param("id") Long id,
               @Param("fromStatus") String fromStatus,
               @Param("version") Integer version,
               @Param("reason") String reason,
               @Param("now") LocalDateTime now);

    /**
     * 转入等待期：REVIEWING → WAITING_PERIOD，并写入等待期截止时间。
     */
    @Update("""
            UPDATE authorization_case
               SET status = 'WAITING_PERIOD', wait_period_end_at = #{endAt},
                   review_comment = #{comment}, version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND status = 'REVIEWING' AND version = #{version}
            """)
    int approveToWaiting(@Param("id") Long id,
                         @Param("version") Integer version,
                         @Param("endAt") LocalDateTime endAt,
                         @Param("comment") String comment,
                         @Param("now") LocalDateTime now);

    /**
     * 等待期生效：WAITING_PERIOD → AUTHORIZED。
     *
     * <p>这是整个状态机里最关键的一条 UPDATE。它同时带状态与版本条件，
     * 因此如果等待期内有人提交了异议（状态变 DISPUTED），这条更新影响 0 行，
     * 授权任务自动失败回滚。
     */
    @Update("""
            UPDATE authorization_case
               SET status = 'AUTHORIZED', authorized_at = #{now},
                   version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND status = 'WAITING_PERIOD' AND version = #{version}
            """)
    int authorize(@Param("id") Long id, @Param("version") Integer version, @Param("now") LocalDateTime now);

    /**
     * 驳回：进入终态 REJECTED。
     */
    @Update("""
            UPDATE authorization_case
               SET status = 'REJECTED', review_comment = #{comment}, closed_at = #{now},
                   version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND status = #{fromStatus} AND version = #{version}
            """)
    int reject(@Param("id") Long id,
               @Param("fromStatus") String fromStatus,
               @Param("version") Integer version,
               @Param("comment") String comment,
               @Param("now") LocalDateTime now);

    /**
     * 转入异议：WAITING_PERIOD → DISPUTED。
     */
    @Update("""
            UPDATE authorization_case
               SET status = 'DISPUTED', version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND status = 'WAITING_PERIOD' AND version = #{version}
            """)
    int markDisputed(@Param("id") Long id, @Param("version") Integer version, @Param("now") LocalDateTime now);

    /**
     * 异议不成立：DISPUTED → WAITING_PERIOD，并<b>重新计算</b>等待期截止时间。
     *
     * <p>重新计时而不是延续原来的截止时间：否则异议可以卡在生效前一刻提交，
     * 处理完立刻到点生效，等于没给所有者反应时间。
     */
    @Update("""
            UPDATE authorization_case
               SET status = 'WAITING_PERIOD', wait_period_end_at = #{endAt},
                   review_comment = #{comment}, version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND status = 'DISPUTED' AND version = #{version}
            """)
    int resolveDisputeToWaiting(@Param("id") Long id,
                                @Param("version") Integer version,
                                @Param("endAt") LocalDateTime endAt,
                                @Param("comment") String comment,
                                @Param("now") LocalDateTime now);

    /**
     * 撤销：AUTHORIZED → REVOKED。
     */
    @Update("""
            UPDATE authorization_case
               SET status = 'REVOKED', revoked_at = #{now}, closed_at = #{now},
                   version = version + 1, updated_at = #{now}
             WHERE id = #{id} AND status = 'AUTHORIZED' AND version = #{version}
            """)
    int revoke(@Param("id") Long id, @Param("version") Integer version, @Param("now") LocalDateTime now);

    /**
     * 用户恢复确认时，批量取消其所有未生效案件。
     *
     * <p>这是唯一一条<b>不需要逐条检查版本</b>的更新：目标很明确 ——
     * 把所有未终结且未生效的案件一次性作废。逐条冲突检查在这里没有意义，
     * 因为它要达成的正是"无论当前停在哪个阶段，都让它结束"。
     *
     * @return 被取消的案件数
     */
    @Update("""
            UPDATE authorization_case
               SET status = 'CANCELLED', closed_at = #{now}, version = version + 1, updated_at = #{now}
             WHERE owner_id = #{ownerId}
               AND status IN ('DRAFT', 'PENDING_REVIEW', 'REVIEWING', 'WAITING_PERIOD', 'DISPUTED')
            """)
    int cancelAllPendingByOwner(@Param("ownerId") Long ownerId, @Param("now") LocalDateTime now);

    /**
     * 等待期已到却被遗漏的案件（定时任务扫描用）。
     *
     * <p>走 {@code idx_wait_deadline (status, wait_period_end_at)} 索引。
     */
    @Select("""
            SELECT * FROM authorization_case
             WHERE status = 'WAITING_PERIOD'
               AND wait_period_end_at <= #{now}
               AND id > #{lastId}
             ORDER BY id
             LIMIT #{limit}
            """)
    List<AuthorizationCase> selectWaitPeriodExpired(@Param("now") LocalDateTime now,
                                                    @Param("lastId") Long lastId,
                                                    @Param("limit") int limit);

    /**
     * 查询某个生效案件是否覆盖指定资产 —— 供资产访问守卫使用。
     */
    @Select("""
            SELECT COUNT(*)
              FROM authorization_case c
              JOIN authorization_asset aa ON aa.case_id = c.id
             WHERE aa.asset_id = #{assetId}
               AND c.status = 'AUTHORIZED'
               AND c.id <> #{excludeCaseId}
            """)
    int countOtherAuthorizedCases(@Param("assetId") Long assetId,
                                  @Param("excludeCaseId") Long excludeCaseId);
}
