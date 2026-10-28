package com.legacyguard.infra.mq;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 消息消费记录，对应 {@code message_consume_record}。
 *
 * <p>{@code message_id} 上的唯一索引就是消费幂等的边界：
 * 同一条消息第二次处理时插入必然失败，据此知道"这活已经干过了"。
 */
@Getter
@Setter
@TableName("message_consume_record")
public class MessageConsumeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageId;

    private String queueName;

    /** SUCCESS / FAILED */
    private String status;

    private String errorMsg;

    private LocalDateTime consumedAt;
}
