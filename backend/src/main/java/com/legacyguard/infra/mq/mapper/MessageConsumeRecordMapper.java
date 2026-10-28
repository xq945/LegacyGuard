package com.legacyguard.infra.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.infra.mq.MessageConsumeRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 消费记录数据访问。
 */
@Mapper
public interface MessageConsumeRecordMapper extends BaseMapper<MessageConsumeRecord> {

    /**
     * 尝试登记一次消费。
     *
     * <p>{@code INSERT IGNORE} + 唯一索引 = 原子的"首次处理"判断。
     * 返回 1 表示这是第一次处理该消息；返回 0 表示已经处理过，应当跳过。
     *
     * <p><b>必须和业务处理在同一事务里</b>：如果先登记后处理，处理失败回滚时
     * 登记也跟着回滚，下次重投还能正常处理；若分开提交，就会出现
     * "登记成功但业务没做成，且永远不再重试"。
     */
    @Insert("""
            INSERT IGNORE INTO message_consume_record
                (message_id, queue_name, status, consumed_at)
            VALUES (#{messageId}, #{queueName}, #{status}, #{now})
            """)
    int tryRecord(@Param("messageId") String messageId,
                  @Param("queueName") String queueName,
                  @Param("status") String status,
                  @Param("now") LocalDateTime now);
}
