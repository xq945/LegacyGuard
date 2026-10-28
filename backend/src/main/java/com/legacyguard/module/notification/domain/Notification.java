package com.legacyguard.module.notification.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 站内信，对应 {@code notification}。
 *
 * <p>{@code dedupKey} 上的唯一索引是通知幂等的核心：同一件事（例如
 * "3 号用户第 2 周期的第 1 轮提醒"）无论被触发多少次，库里只会有一条记录。
 * 这比在应用层判断"发过了没有"可靠得多 —— 并发下也可以保证。
 */
@Getter
@Setter
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long receiverId;

    /** 见 {@link NotificationType} */
    private String bizType;

    /** 关联业务对象（案件 ID、关系 ID 等） */
    private Long bizId;

    private String title;

    private String content;

    /** 幂等键，同一键只会存在一条记录；不需要去重的场景可留空 */
    private String dedupKey;

    /** 已读时间，null 表示未读 */
    private LocalDateTime readAt;

    private LocalDateTime createdAt;
}
