package com.legacyguard.module.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.module.notification.domain.Notification;
import com.legacyguard.module.notification.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 站内信服务。
 *
 * <p><b>第三阶段实现是同步写库</b>：业务事务里直接调用 {@link #send}，
 * 通知记录与业务数据在同一事务内提交。
 *
 * <p><b>第四阶段会改成事务发件箱 + RabbitMQ</b>：
 * 业务事务只写 {@code outbox_event}，由独立发布器投递到 MQ，
 * 消费者再落 {@code notification}。届时本类的 {@code send} 语义不变，
 * 调用方不需要修改。
 *
 * <p>为什么现在就可以这么设计：{@code dedup_key} 唯一索引已经保证了幂等，
 * 无论同步还是异步、重投多少次，同一件事只会产生一条站内信。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationMapper notificationMapper;

    public NotificationService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    // ==================================================================
    // 发送（供其他模块调用）
    // ==================================================================

    /**
     * 发送一条站内信。
     *
     * @param dedupKey 幂等键。相同键只写一条；传 null 表示不做去重。
     *                 建议格式：{@code 类型:业务ID:序号}，例如
     *                 {@code heartbeat:3:2:remind}（用户 3、第 2 周期、第 1 轮提醒）
     * @return true 表示确实写入了新记录；false 表示因幂等键已存在而跳过
     */
    public boolean send(Long receiverId, String bizType, Long bizId,
                        String title, String content, String dedupKey) {
        if (receiverId == null) {
            log.warn("通知接收者为空，已忽略 type={}", bizType);
            return false;
        }

        Notification notification = new Notification();
        notification.setReceiverId(receiverId);
        notification.setBizType(bizType);
        notification.setBizId(bizId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setDedupKey(dedupKey);
        notification.setCreatedAt(LocalDateTime.now());

        try {
            notificationMapper.insert(notification);
            log.debug("站内信已发送 receiver={} type={} bizId={}", receiverId, bizType, bizId);
            return true;
        } catch (DuplicateKeyException e) {
            // 幂等键冲突：同一件事已经通知过了，静默跳过
            log.debug("站内信重复，已忽略 dedupKey={}", dedupKey);
            return false;
        }
    }

    // ==================================================================
    // 查询
    // ==================================================================

    public PageResult<NotificationView> list(Long userId, long page, long size, boolean unreadOnly) {
        Page<Notification> paging = new Page<>(page, size);

        LambdaQueryWrapper<Notification> query = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getReceiverId, userId)
                .isNull(unreadOnly, Notification::getReadAt)
                .orderByDesc(Notification::getId);

        IPage<Notification> result = notificationMapper.selectPage(paging, query);

        var records = result.getRecords().stream().map(NotificationView::from).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public NotificationView detail(Long userId, Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        // 不是自己的通知就当作不存在 —— 不泄露别人收到了什么
        if (n == null || !n.getReceiverId().equals(userId)) {
            throw BizException.notFound("通知");
        }
        return NotificationView.from(n);
    }

    public long countUnread(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        if (n == null || !n.getReceiverId().equals(userId)) {
            throw BizException.notFound("通知");
        }
        notificationMapper.markRead(notificationId, userId, LocalDateTime.now());
    }

    public int markAllRead(Long userId) {
        return notificationMapper.markAllRead(userId, LocalDateTime.now());
    }
}
