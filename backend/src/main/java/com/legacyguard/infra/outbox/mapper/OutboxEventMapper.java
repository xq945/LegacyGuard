package com.legacyguard.infra.outbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.infra.outbox.domain.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发件箱数据访问。
 */
@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    /**
     * 找出待发布的事件。
     *
     * <p>条件里没有加 {@code FOR UPDATE} —— 抢占交给下面的 {@link #claim} 条件更新完成。
     * 这样查询本身不会长时间持锁，多个实例可以并发扫描。
     */
    @Select("""
            SELECT * FROM outbox_event
             WHERE status = 'NEW'
               AND (next_retry_at IS NULL OR next_retry_at <= #{now})
               AND id > #{lastId}
             ORDER BY id
             LIMIT #{limit}
            """)
    List<OutboxEvent> selectPending(@Param("now") LocalDateTime now,
                                    @Param("lastId") Long lastId,
                                    @Param("limit") int limit);

    /**
     * 抢占一条事件（租约）。
     *
     * <p>把 {@code next_retry_at} 推到未来，等于"这条我先领走了"。
     * 影响行数为 1 才表示抢到；多实例并发时只有一个能成功。
     *
     * <p><b>为什么用租约而不是 PUBLISHING 状态</b>：三态机需要一个"僵死回收"机制
     * （实例崩溃后卡在 PUBLISHING 的记录永远发不出去）。租约到期后自动重新可领，
     * 天然自愈，代价只是极端情况下可能重复发布一次 —— 而重复由消费者幂等吸收。
     */
    @Update("""
            UPDATE outbox_event
               SET next_retry_at = #{leaseUntil}, updated_at = #{now}
             WHERE id = #{id}
               AND status = 'NEW'
               AND (next_retry_at IS NULL OR next_retry_at <= #{now})
            """)
    int claim(@Param("id") Long id,
              @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    @Update("""
            UPDATE outbox_event
               SET status = 'PUBLISHED', published_at = #{now}, updated_at = #{now}, last_error = NULL
             WHERE id = #{id} AND status = 'NEW'
            """)
    int markPublished(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 发布失败：退回 NEW 并做指数退避，超过最大次数则标记 FAILED 进入人工处理。
     */
    @Update("""
            UPDATE outbox_event
               SET status = #{status},
                   retry_count = retry_count + 1,
                   next_retry_at = #{nextRetryAt},
                   last_error = #{error},
                   updated_at = #{now}
             WHERE id = #{id}
            """)
    int markFailed(@Param("id") Long id,
                   @Param("status") String status,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("error") String error,
                   @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = 'NEW'")
    long countPending();

    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = 'FAILED'")
    long countFailed();
}
