package com.legacyguard.module.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.notification.domain.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 站内信数据访问。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Select("SELECT COUNT(*) FROM notification WHERE receiver_id = #{userId} AND read_at IS NULL")
    long countUnread(@Param("userId") Long userId);

    /**
     * 标记单条已读。条件里带 {@code receiver_id}：
     * 只有收件人本人能标记，且重复标记影响 0 行也不会报错。
     */
    @Update("""
            UPDATE notification SET read_at = #{now}
             WHERE id = #{id} AND receiver_id = #{userId} AND read_at IS NULL
            """)
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE notification SET read_at = #{now}
             WHERE receiver_id = #{userId} AND read_at IS NULL
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
