package com.legacyguard.infra.outbox.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 事务发件箱记录，对应 {@code outbox_event}。
 *
 * <p><b>它是"要发消息"这件事本身的数据化。</b>业务事务里同时写业务数据和这条记录，
 * 二者要么都成功要么都回滚；真正的投递由独立的发布器完成。
 *
 * <p>这样就避开了那个两难：
 * <ul>
 *   <li>事务内直接发 MQ → 事务回滚了消息却已发出</li>
 *   <li>先提交再发 MQ → 提交后进程崩溃，消息永久丢失</li>
 * </ul>
 */
@Getter
@Setter
@TableName("outbox_event")
public class OutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 事件唯一标识（UUID），消费者的幂等键 */
    private String eventId;

    /** 聚合类型：CASE / CONTACT / HEARTBEAT */
    private String aggregateType;

    private Long aggregateId;

    /** 事件类型，见 {@code EventTypes} */
    private String eventType;

    /** 事件载荷（JSON） */
    private String payload;

    /** NEW / PUBLISHED / FAILED */
    private String status;

    private Integer retryCount;

    /** 下次可被扫描的时间。发布器用它实现"租约"，避免多实例重复投递 */
    private LocalDateTime nextRetryAt;

    private LocalDateTime publishedAt;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
