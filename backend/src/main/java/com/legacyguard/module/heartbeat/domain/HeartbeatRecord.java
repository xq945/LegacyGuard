package com.legacyguard.module.heartbeat.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 状态确认流水，对应 {@code heartbeat_record}。
 *
 * <p>表上有唯一约束 {@code (user_id, cycle_no)} —— 同一个周期只能确认一次。
 * 这是"重复点击确认"的最终防线：即使前端连点、网络重发，
 * 也不会产生两条同一周期的确认记录。
 */
@Getter
@Setter
@TableName("heartbeat_record")
public class HeartbeatRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 被完成的周期号（确认<b>之前</b>的周期） */
    private Integer cycleNo;

    /** MANUAL / AUTO */
    private String confirmType;

    /** 确认前的截止时间 */
    private LocalDateTime prevDeadlineAt;

    /** 确认后计算出的新截止时间 */
    private LocalDateTime nextDeadlineAt;

    private String sourceIp;

    private LocalDateTime createdAt;
}
