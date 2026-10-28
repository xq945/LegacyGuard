package com.legacyguard.module.contact.service;

import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.common.exception.StateConflictException;
import com.legacyguard.module.audit.domain.AuditAction;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.module.contact.domain.ContactStatus;
import com.legacyguard.module.contact.domain.TrustedContact;
import com.legacyguard.module.contact.dto.ContactResponse;
import com.legacyguard.module.contact.dto.ContactView;
import com.legacyguard.module.contact.dto.InviteContactRequest;
import com.legacyguard.module.contact.mapper.TrustedContactMapper;
import com.legacyguard.module.notification.domain.NotificationType;
import com.legacyguard.module.notification.service.NotificationService;
import com.legacyguard.module.user.domain.SysUser;
import com.legacyguard.module.user.mapper.SysUserMapper;
import com.legacyguard.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可信联系人服务。
 *
 * <p>这个模块本身不处理资产，它只维护"谁可以替谁申请授权"的资格。
 * 真正的授权判定在 authorization 模块，那边会读这里的状态。
 */
@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final TrustedContactMapper contactMapper;
    private final SysUserMapper userMapper;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public ContactService(TrustedContactMapper contactMapper,
                          SysUserMapper userMapper,
                          AuditService auditService,
                          NotificationService notificationService) {
        this.contactMapper = contactMapper;
        this.userMapper = userMapper;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    // ==================================================================
    // 邀请
    // ==================================================================

    /**
     * 邀请某个已注册用户成为自己的可信联系人。
     *
     * <p>查重（{@code selectActivePair}）只是为了给出友好提示。
     * 真正的防线是数据库上的 {@code uk_active_pair} 唯一索引 ——
     * 两个并发邀请可能同时通过查重，后者会被唯一约束拦下。
     */
    @Transactional
    public ContactResponse invite(AuthPrincipal actor, InviteContactRequest request) {
        SysUser target = userMapper.selectByUsername(request.username());
        if (target == null || target.isDeleted()) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,
                    "用户 " + request.username() + " 不存在，请先请对方注册账号");
        }
        if (target.getId().equals(actor.userId())) {
            throw new BizException(ErrorCode.BIZ_RULE_VIOLATION, "不能邀请自己");
        }

        TrustedContact existing = contactMapper.selectActivePair(actor.userId(), target.getId());
        if (existing != null) {
            String msg = ContactStatus.ACTIVE.equals(existing.getStatus())
                    ? "该用户已经是你的可信联系人"
                    : "已经向该用户发出邀请，等待对方确认";
            throw new BizException(ErrorCode.DUPLICATE_REQUEST, msg);
        }

        LocalDateTime now = LocalDateTime.now();
        TrustedContact relation = new TrustedContact();
        relation.setOwnerId(actor.userId());
        relation.setContactUserId(target.getId());
        relation.setStatus(ContactStatus.PENDING);
        relation.setNote(request.note());
        relation.setVersion(0);
        relation.setInvitedAt(now);
        relation.setCreatedAt(now);
        relation.setUpdatedAt(now);

        try {
            contactMapper.insert(relation);
        } catch (DuplicateKeyException e) {
            // 并发邀请撞上唯一索引
            throw new BizException(ErrorCode.DUPLICATE_REQUEST, "已经向该用户发出邀请，等待对方确认");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("contactUserId", target.getId());
        detail.put("contactUsername", target.getUsername());
        auditService.success(actor.userId(), actor.role(), AuditAction.CONTACT_INVITE,
                "CONTACT", relation.getId(), detail);

        log.info("发出联系人邀请 ownerId={} contactUserId={}", actor.userId(), target.getId());

        // 通知失败不能影响邀请本身：邀请已经写进库了，通知只是提醒手段
        safeNotify(target.getId(), NotificationType.CONTACT_INVITED, relation.getId(),
                "收到可信联系人邀请",
                "有用户邀请你成为其可信联系人。确认后，在你失联的情况下对方可能申请访问其托管的资料。",
                "contact:invite:" + relation.getId());

        // 邀请成功后立即回读，保证返回的字段与其他接口一致
        return listRelated(actor.userId()).stream()
                .filter(v -> v.getId().equals(relation.getId()))
                .findFirst()
                .map(view -> ContactResponse.from(view, actor.userId()))
                .orElseThrow(() -> new IllegalStateException("邀请已写入但无法回读"));
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /**
     * 列出与当前用户相关的全部关系：我邀请的人，以及邀请我的人。
     */
    public List<ContactResponse> list(AuthPrincipal actor) {
        return listRelated(actor.userId()).stream()
                .map(view -> ContactResponse.from(view, actor.userId()))
                .toList();
    }

    private List<ContactView> listRelated(Long userId) {
        return contactMapper.selectRelated(userId);
    }

    // ==================================================================
    // 回应邀请
    // ==================================================================

    /**
     * 接受邀请。只有被邀请人本人能操作，且关系必须仍是 PENDING。
     */
    @Transactional
    public void accept(AuthPrincipal actor, Long contactId) {
        TrustedContact relation = requireInvitationForMe(actor, contactId);

        int rows = contactMapper.accept(contactId, actor.userId(), LocalDateTime.now());
        if (rows == 0) {
            throw new StateConflictException("邀请状态已变化，请刷新后重试");
        }

        auditService.success(actor.userId(), actor.role(), AuditAction.CONTACT_ACCEPT,
                "CONTACT", contactId, Map.of("ownerId", relation.getOwnerId()));
        log.info("联系人关系生效 contactId={} contactUserId={}", contactId, actor.userId());

        safeNotify(relation.getOwnerId(), NotificationType.CONTACT_ACTIVATED, contactId,
                "可信联系人已确认",
                "对方已接受你的邀请，关系正式生效。请记得按时完成状态确认。",
                "contact:accept:" + contactId);
    }

    /**
     * 拒绝邀请。
     */
    @Transactional
    public void reject(AuthPrincipal actor, Long contactId) {
        TrustedContact relation = requireInvitationForMe(actor, contactId);

        int rows = contactMapper.reject(contactId, actor.userId(), LocalDateTime.now());
        if (rows == 0) {
            throw new StateConflictException("邀请状态已变化，请刷新后重试");
        }

        auditService.success(actor.userId(), actor.role(), AuditAction.CONTACT_REJECT,
                "CONTACT", contactId, Map.of("ownerId", relation.getOwnerId()));
        log.info("邀请被拒绝 contactId={} contactUserId={}", contactId, actor.userId());
    }

    /**
     * 校验这是一条"发给我的、尚待处理的"邀请。
     *
     * <p>区分 404 与 409 很重要：<br>
     * 不存在或不是发给我的 → 404（不该让人知道别人的邀请存在）<br>
     * 存在但已经处理过 → 409（这是并发或重复操作）
     */
    private TrustedContact requireInvitationForMe(AuthPrincipal actor, Long contactId) {
        TrustedContact relation = contactMapper.selectById(contactId);
        if (relation == null || !relation.getContactUserId().equals(actor.userId())) {
            throw BizException.notFound("邀请");
        }
        if (!ContactStatus.PENDING.equals(relation.getStatus())) {
            throw new StateConflictException("该邀请已经被处理过，当前状态：" + relation.getStatus());
        }
        return relation;
    }

    // ==================================================================
    // 撤销
    // ==================================================================

    /**
     * 撤销关系。所有者可撤回待确认的邀请，也可撤销已生效的联系人关系。
     *
     * <p><b>已生效的授权不受影响</b>（docs/07 Q2）：撤销关系只阻止未来的新申请，
     * 已经生效的授权只能由所有者单独撤销。理由：授权是独立的意愿表达，
     * 不应被关系变更静默收回。
     */
    @Transactional
    public void revoke(AuthPrincipal actor, Long contactId) {
        TrustedContact relation = contactMapper.selectById(contactId);
        if (relation == null || !relation.getOwnerId().equals(actor.userId())) {
            // 不是我的关系就不存在 —— 不泄露别人关系的存在性
            throw BizException.notFound("联系人关系");
        }
        if (!ContactStatus.isActivePair(relation.getStatus())) {
            throw new StateConflictException("该关系已是结束状态：" + relation.getStatus());
        }

        int rows = contactMapper.revoke(contactId, actor.userId(), LocalDateTime.now());
        if (rows == 0) {
            throw new StateConflictException("关系状态已变化，请刷新后重试");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fromStatus", relation.getStatus());
        detail.put("contactUserId", relation.getContactUserId());
        auditService.success(actor.userId(), actor.role(), AuditAction.CONTACT_REVOKE,
                "CONTACT", contactId, detail);

        log.info("联系人关系已撤销 contactId={} ownerId={}", contactId, actor.userId());
    }

    // ==================================================================
    // 内部
    // ==================================================================

    /**
     * 发送通知，失败只记日志。
     *
     * <p>通知是辅助手段，不是业务前提。邀请已经写入数据库了，
     * 因为一条站内信写失败就把整个邀请回滚，反而让用户困惑。
     * 第四阶段改用事务发件箱后，这里会交给 MQ 投递，语义不变。
     */
    private void safeNotify(Long receiverId, String type, Long bizId,
                            String title, String content, String dedupKey) {
        try {
            notificationService.send(receiverId, type, bizId, title, content, dedupKey);
        } catch (RuntimeException e) {
            log.error("站内信发送失败 receiver={} type={}", receiverId, type, e);
        }
    }
}
