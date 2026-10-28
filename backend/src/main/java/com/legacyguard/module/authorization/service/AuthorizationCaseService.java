package com.legacyguard.module.authorization.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.common.exception.StateConflictException;
import com.legacyguard.module.asset.domain.DigitalAsset;
import com.legacyguard.module.asset.mapper.DigitalAssetMapper;
import com.legacyguard.module.audit.domain.AuditAction;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.module.authorization.config.AuthorizationProperties;
import com.legacyguard.module.authorization.domain.AuthorizationApproval;
import com.legacyguard.module.authorization.domain.AuthorizationAsset;
import com.legacyguard.module.authorization.domain.AuthorizationCase;
import com.legacyguard.module.authorization.domain.CaseStatus;
import com.legacyguard.module.authorization.domain.DisputeRecord;
import com.legacyguard.module.authorization.dto.ApprovalItem;
import com.legacyguard.module.authorization.dto.ApprovalRequest;
import com.legacyguard.module.authorization.dto.CaseDetailResponse;
import com.legacyguard.module.authorization.dto.CaseResponse;
import com.legacyguard.module.authorization.dto.CreateCaseRequest;
import com.legacyguard.module.authorization.dto.DisputeRequest;
import com.legacyguard.module.authorization.dto.ResolveDisputeRequest;
import com.legacyguard.module.authorization.mapper.AuthorizationApprovalMapper;
import com.legacyguard.module.authorization.mapper.AuthorizationAssetMapper;
import com.legacyguard.module.authorization.mapper.AuthorizationCaseMapper;
import com.legacyguard.module.authorization.mapper.DisputeRecordMapper;
import com.legacyguard.module.authorization.support.CaseNoGenerator;
import com.legacyguard.module.contact.domain.ContactStatus;
import com.legacyguard.module.contact.domain.TrustedContact;
import com.legacyguard.module.contact.mapper.TrustedContactMapper;
import com.legacyguard.module.heartbeat.domain.HeartbeatConfig;
import com.legacyguard.module.heartbeat.domain.HeartbeatStatus;
import com.legacyguard.module.heartbeat.mapper.HeartbeatConfigMapper;
import com.legacyguard.module.heartbeat.service.HeartbeatRecoveryHook;
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
import java.util.Objects;

/**
 * 授权案件状态机。
 *
 * <p><b>这是整个系统权限转移的唯一通道。</b>资产从"只有所有者能看"变成
 * "某个联系人也能看"，必须完整走完：
 * <pre>
 *   DRAFT → PENDING_REVIEW → REVIEWING → WAITING_PERIOD → AUTHORIZED → (REVOKED)
 * </pre>
 *
 * <p>三条贯穿始终的约束：
 * <ol>
 *   <li><b>每次状态变更都是条件更新</b>（带 status + version），并检查影响行数。
 *       影响 0 行 = 并发冲突，抛 409 而不是继续往下走</li>
 *   <li><b>审批与生效是两个阶段</b>。批准只到 WAITING_PERIOD，
 *       真正生效由定时任务在全条件复核后推进</li>
 *   <li><b>管理员只能推进流程，不能读取资产内容</b>。
 *       本类不注入任何解密组件</li>
 * </ol>
 */
@Service
public class AuthorizationCaseService implements HeartbeatRecoveryHook {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationCaseService.class);

    private static final String TRIGGER_HEARTBEAT_MISS = "HEARTBEAT_MISS";

    private static final String ACTION_ACCEPT = "ACCEPT";
    private static final String ACTION_APPROVE = "APPROVE";
    private static final String ACTION_REJECT = "REJECT";
    private static final String ACTION_RESOLVE = "RESOLVE_DISPUTE";

    private static final String DISPUTE_OPEN = "OPEN";
    private static final String DISPUTE_RESOLVED = "RESOLVED";

    private static final int SCAN_BATCH = 100;

    private final AuthorizationCaseMapper caseMapper;
    private final AuthorizationAssetMapper caseAssetMapper;
    private final AuthorizationApprovalMapper approvalMapper;
    private final DisputeRecordMapper disputeMapper;
    private final TrustedContactMapper contactMapper;
    private final HeartbeatConfigMapper heartbeatMapper;
    private final DigitalAssetMapper assetMapper;
    private final AuditService auditService;
    private final CaseNoGenerator caseNoGenerator;
    private final AuthorizationProperties properties;

    public AuthorizationCaseService(AuthorizationCaseMapper caseMapper,
                                    AuthorizationAssetMapper caseAssetMapper,
                                    AuthorizationApprovalMapper approvalMapper,
                                    DisputeRecordMapper disputeMapper,
                                    TrustedContactMapper contactMapper,
                                    HeartbeatConfigMapper heartbeatMapper,
                                    DigitalAssetMapper assetMapper,
                                    AuditService auditService,
                                    CaseNoGenerator caseNoGenerator,
                                    AuthorizationProperties properties) {
        this.caseMapper = caseMapper;
        this.caseAssetMapper = caseAssetMapper;
        this.approvalMapper = approvalMapper;
        this.disputeMapper = disputeMapper;
        this.contactMapper = contactMapper;
        this.heartbeatMapper = heartbeatMapper;
        this.assetMapper = assetMapper;
        this.auditService = auditService;
        this.caseNoGenerator = caseNoGenerator;
        this.properties = properties;
    }

    // ==================================================================
    // 创建
    // ==================================================================

    /**
     * 创建授权案件草稿。
     *
     * <p>五道前置校验，缺一不可：
     * <ol>
     *   <li>申请人确实持有这份联系人资格（ACTIVE）</li>
     *   <li>资产所有者已经过人工核验，处于 REVIEWING</li>
     *   <li>所选资产确实属于该所有者</li>
     *   <li>所选资产没有被其他进行中的案件占用（行锁 + 查询）</li>
     *   <li>同一对 owner/contact 没有其他未终结案件（唯一索引兜底）</li>
     * </ol>
     */
    @Transactional
    public CaseDetailResponse create(AuthPrincipal actor, CreateCaseRequest request) {
        TrustedContact contact = contactMapper.selectById(request.contactId());
        if (contact == null || !contact.getContactUserId().equals(actor.userId())) {
            // 不是自己的关系就当作不存在，不泄露别人关系的存在性
            throw BizException.notFound("联系人关系");
        }
        if (!ContactStatus.ACTIVE.equals(contact.getStatus())) {
            throw BizException.ruleViolation("该联系人关系尚未确认或已失效，无法发起授权申请");
        }

        Long ownerId = contact.getOwnerId();
        HeartbeatConfig heartbeat = heartbeatMapper.selectById(ownerId);
        if (heartbeat == null || !HeartbeatStatus.allowsAuthorizationApplication(heartbeat.getStatus())) {
            String current = heartbeat == null ? "未设置" : heartbeat.getStatus();
            throw BizException.ruleViolation(
                    "资产所有者当前状态为 " + current + "，尚未完成失联核验，暂不能发起授权申请");
        }

        List<Long> assetIds = request.assetIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()             // 固定加锁顺序，避免并发建案时的死锁
                .toList();

        if (assetIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "至少要选择一项资产");
        }
        if (assetIds.size() > properties.maxAssetsPerCase()) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "单个案件最多覆盖 " + properties.maxAssetsPerCase() + " 项资产");
        }

        // 对资产行加锁并把并发建案串行化 —— 跨表条件唯一无法用索引表达，
        // 这是 docs/03 第 3.3.3 节说明的补偿方案
        List<DigitalAsset> assets = assetMapper.selectForUpdate(assetIds);
        if (assets.size() != assetIds.size()) {
            throw BizException.notFound("部分资产");
        }
        for (DigitalAsset asset : assets) {
            if (!ownerId.equals(asset.getOwnerId())) {
                throw BizException.accessDenied("所选资产不属于该用户");
            }
        }

        if (caseAssetMapper.countActiveCasesCovering(assetIds) > 0) {
            throw BizException.ruleViolation("所选资产已被其他进行中的授权案件占用");
        }

        LocalDateTime now = LocalDateTime.now();
        AuthorizationCase entity = new AuthorizationCase();
        entity.setCaseNo(caseNoGenerator.next());
        entity.setOwnerId(ownerId);
        entity.setContactId(contact.getId());
        entity.setTriggerType(TRIGGER_HEARTBEAT_MISS);
        entity.setStatus(CaseStatus.DRAFT);
        entity.setReason(request.reason());
        entity.setVersion(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        try {
            caseMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            // uk_active_case：同一 owner+contact 已有未终结案件
            throw new BizException(ErrorCode.DUPLICATE_REQUEST, "你与该用户之间已有进行中的授权案件");
        }

        for (Long assetId : assetIds) {
            AuthorizationAsset link = new AuthorizationAsset();
            link.setCaseId(entity.getId());
            link.setAssetId(assetId);
            link.setCreatedAt(now);
            caseAssetMapper.insert(link);
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("caseNo", entity.getCaseNo());
        detail.put("ownerId", ownerId);
        detail.put("assetCount", assetIds.size());
        auditService.success(actor.userId(), actor.role(), AuditAction.CASE_CREATE,
                "CASE", entity.getId(), detail);

        log.info("创建授权案件 caseNo={} ownerId={} applicantId={} assets={}",
                entity.getCaseNo(), ownerId, actor.userId(), assetIds.size());

        return toDetail(entity, actor);
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /**
     * 我相关的案件：我作为所有者被申请的，以及我作为申请人发起的。
     */
    public PageResult<CaseResponse> listMine(AuthPrincipal actor, long page, long size) {
        Page<AuthorizationCase> paging = new Page<>(page, size);

        LambdaQueryWrapper<AuthorizationCase> query = new LambdaQueryWrapper<>();
        // 找到"以我为申请人"的关系 ID 集合，再与 ownerId 做 OR 查询
        List<Long> myContactIds = contactMapper.selectRelated(actor.userId()).stream()
                .filter(v -> v.getContactUserId().equals(actor.userId()))
                .map(v -> v.getId())
                .toList();

        if (myContactIds.isEmpty()) {
            query.eq(AuthorizationCase::getOwnerId, actor.userId());
        } else {
            query.and(w -> w.eq(AuthorizationCase::getOwnerId, actor.userId())
                    .or()
                    .in(AuthorizationCase::getContactId, myContactIds));
        }
        query.orderByDesc(AuthorizationCase::getId);

        IPage<AuthorizationCase> result = caseMapper.selectPage(paging, query);

        List<CaseResponse> records = result.getRecords().stream()
                .map(c -> CaseResponse.from(c, actor.userId(), applicantUserIdOf(c)))
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    /**
     * 案件详情。所有者、申请人、管理员可见。
     */
    public CaseDetailResponse detail(AuthPrincipal actor, Long caseId) {
        AuthorizationCase entity = requireCase(caseId);
        assertVisible(entity, actor);
        return toDetail(entity, actor);
    }

    /**
     * 管理端：待受理队列。
     */
    public PageResult<CaseResponse> listPending(AuthPrincipal admin, long page, long size) {
        Page<AuthorizationCase> paging = new Page<>(page, size);
        LambdaQueryWrapper<AuthorizationCase> query = new LambdaQueryWrapper<AuthorizationCase>()
                .in(AuthorizationCase::getStatus, List.of(CaseStatus.PENDING_REVIEW, CaseStatus.REVIEWING))
                .orderByAsc(AuthorizationCase::getCreatedAt);

        IPage<AuthorizationCase> result = caseMapper.selectPage(paging, query);
        List<CaseResponse> records = result.getRecords().stream()
                .map(c -> CaseResponse.from(c, admin.userId(), applicantUserIdOf(c)))
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    // ==================================================================
    // 申请人操作
    // ==================================================================

    /**
     * 提交审核：DRAFT → PENDING_REVIEW。
     */
    @Transactional
    public void submit(AuthPrincipal actor, Long caseId) {
        AuthorizationCase entity = requireCase(caseId);
        assertApplicant(entity, actor);
        assertStatus(entity, CaseStatus.DRAFT, "提交审核");

        int rows = caseMapper.submit(caseId, entity.getVersion(), LocalDateTime.now());
        requireRows(rows, "提交审核");

        auditService.success(actor.userId(), actor.role(), AuditAction.CASE_SUBMIT,
                "CASE", caseId, Map.of("caseNo", entity.getCaseNo()));
        log.info("案件已提交审核 caseNo={}", entity.getCaseNo());
    }

    /**
     * 取消案件。所有者或申请人都可以，但只能取消尚未生效的。
     */
    @Transactional
    public void cancel(AuthPrincipal actor, Long caseId, String reason) {
        AuthorizationCase entity = requireCase(caseId);
        assertOwnerOrApplicant(entity, actor);

        if (!CaseStatus.isCancellable(entity.getStatus())) {
            if (CaseStatus.AUTHORIZED.equals(entity.getStatus())) {
                throw new StateConflictException("案件已生效，请使用撤销接口而不是取消");
            }
            throw new StateConflictException("案件当前状态为 " + entity.getStatus() + "，不能取消");
        }

        int rows = caseMapper.cancel(caseId, entity.getStatus(), entity.getVersion(), reason, LocalDateTime.now());
        requireRows(rows, "取消案件");

        auditService.success(actor.userId(), actor.role(), AuditAction.CASE_CANCEL,
                "CASE", caseId, Map.of("caseNo", entity.getCaseNo(), "fromStatus", entity.getStatus()));
        log.info("案件被取消 caseNo={} by={}", entity.getCaseNo(), actor.userId());
    }

    // ==================================================================
    // 异议
    // ==================================================================

    /**
     * 提交异议：WAITING_PERIOD → DISPUTED。
     *
     * <p>异议人与案件状态在<b>同一事务</b>内处理：如果案件状态已被并发的生效任务改掉，
     * 这里会抛冲突，异议记录也不会留下 —— 不会出现"异议留在库里但案件已经生效"。
     */
    @Transactional
    public void raiseDispute(AuthPrincipal actor, Long caseId, DisputeRequest request) {
        AuthorizationCase entity = requireCase(caseId);
        assertOwnerOrApplicant(entity, actor);

        if (!CaseStatus.WAITING_PERIOD.equals(entity.getStatus())) {
            throw new StateConflictException(
                    "只有等待期内的案件可以提出异议，当前状态：" + entity.getStatus());
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = caseMapper.markDisputed(caseId, entity.getVersion(), now);
        requireRows(rows, "提交异议");

        DisputeRecord dispute = new DisputeRecord();
        dispute.setCaseId(caseId);
        dispute.setSubmitterId(actor.userId());
        dispute.setSubmitterRole(entity.getOwnerId().equals(actor.userId()) ? "OWNER" : "CONTACT");
        dispute.setReason(request.reason());
        dispute.setStatus(DISPUTE_OPEN);
        dispute.setCreatedAt(now);
        dispute.setUpdatedAt(now);
        disputeMapper.insert(dispute);

        auditService.success(actor.userId(), actor.role(), AuditAction.CASE_DISPUTE,
                "CASE", caseId, Map.of("caseNo", entity.getCaseNo()));
        log.warn("案件被提出异议，流程暂停 caseNo={} submitter={}", entity.getCaseNo(), actor.userId());
    }

    // ==================================================================
    // 撤销
    // ==================================================================

    /**
     * 撤销已生效的授权：AUTHORIZED → REVOKED。
     *
     * <p><b>只有所有者能撤销。</b>管理员没有这个接口 —— 撤销已生效授权
     * 是对他人已获得权利的剥夺，不应由平台单方面决定（docs/07 Q4）。
     */
    @Transactional
    public void revoke(AuthPrincipal actor, Long caseId) {
        AuthorizationCase entity = requireCase(caseId);
        assertOwner(entity, actor);

        if (!CaseStatus.AUTHORIZED.equals(entity.getStatus())) {
            throw new StateConflictException(
                    "只有已生效的授权可以撤销，当前状态：" + entity.getStatus());
        }

        int rows = caseMapper.revoke(caseId, entity.getVersion(), LocalDateTime.now());
        requireRows(rows, "撤销授权");

        auditService.success(actor.userId(), actor.role(), AuditAction.CASE_REVOKE,
                "CASE", caseId, Map.of("caseNo", entity.getCaseNo()));
        log.info("授权被撤销 caseNo={} by owner={}", entity.getCaseNo(), actor.userId());
    }

    // ==================================================================
    // 管理端审批
    // ==================================================================

    /**
     * 受理：PENDING_REVIEW → REVIEWING。
     */
    @Transactional
    public void accept(AuthPrincipal admin, Long caseId) {
        AuthorizationCase entity = requireCase(caseId);
        assertStatus(entity, CaseStatus.PENDING_REVIEW, "受理");

        LocalDateTime now = LocalDateTime.now();
        int rows = caseMapper.transition(caseId, CaseStatus.PENDING_REVIEW, CaseStatus.REVIEWING,
                entity.getVersion(), now);
        requireRows(rows, "受理");

        recordApproval(caseId, admin, ACTION_ACCEPT, CaseStatus.PENDING_REVIEW, CaseStatus.REVIEWING, null, now);

        auditService.success(admin.userId(), admin.role(), AuditAction.CASE_ACCEPT,
                "CASE", caseId, Map.of("caseNo", entity.getCaseNo()));
    }

    /**
     * 批准：REVIEWING → WAITING_PERIOD。
     *
     * <p><b>批准不等于生效。</b>这里只是开启等待期，真正生效要等等待期到点、
     * 定时任务重新复核全部条件之后（见 {@link #authorizeDueCases()}）。
     */
    @Transactional
    public void approve(AuthPrincipal admin, Long caseId, ApprovalRequest request) {
        AuthorizationCase entity = requireCase(caseId);
        assertStatus(entity, CaseStatus.REVIEWING, "批准");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endAt = now.plusDays(properties.waitPeriodDays());

        int rows = caseMapper.approveToWaiting(caseId, entity.getVersion(), endAt,
                request.comment(), now);
        requireRows(rows, "批准");

        recordApproval(caseId, admin, ACTION_APPROVE, CaseStatus.REVIEWING,
                CaseStatus.WAITING_PERIOD, request.comment(), now);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("caseNo", entity.getCaseNo());
        detail.put("waitPeriodEndAt", endAt.toString());
        auditService.success(admin.userId(), admin.role(), AuditAction.CASE_APPROVE,
                "CASE", caseId, detail);

        log.info("案件批准通过，进入等待期 caseNo={} endAt={}", entity.getCaseNo(), endAt);
    }

    /**
     * 驳回：PENDING_REVIEW / REVIEWING → REJECTED。
     */
    @Transactional
    public void reject(AuthPrincipal admin, Long caseId, ApprovalRequest request) {
        AuthorizationCase entity = requireCase(caseId);

        String from = entity.getStatus();
        if (!CaseStatus.PENDING_REVIEW.equals(from) && !CaseStatus.REVIEWING.equals(from)) {
            throw new StateConflictException("案件当前状态为 " + from + "，不能驳回");
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = caseMapper.reject(caseId, from, entity.getVersion(), request.comment(), now);
        requireRows(rows, "驳回");

        recordApproval(caseId, admin, ACTION_REJECT, from, CaseStatus.REJECTED, request.comment(), now);

        auditService.success(admin.userId(), admin.role(), AuditAction.CASE_REJECT,
                "CASE", caseId, Map.of("caseNo", entity.getCaseNo(), "fromStatus", from));
    }

    /**
     * 处理异议。
     *
     * <p>{@code UPHELD}：异议不成立 → 回到等待期，<b>等待期重新计时</b>。
     * 重新计时是为了防止有人用异议把生效拖到最后一刻再撤销，反复拉锯。
     *
     * <p>{@code OVERTURNED}：异议成立 → 案件直接驳回。
     */
    @Transactional
    public void resolveDispute(AuthPrincipal admin, Long caseId, ResolveDisputeRequest request) {
        AuthorizationCase entity = requireCase(caseId);

        if (!CaseStatus.DISPUTED.equals(entity.getStatus())) {
            throw new StateConflictException(
                    "案件当前状态为 " + entity.getStatus() + "，没有待处理的异议");
        }
        if (disputeMapper.countOpen(caseId) == 0) {
            throw new StateConflictException("案件处于异议状态但没有未处理的异议记录，数据异常");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean upheld = "UPHELD".equals(request.result());

        if (upheld) {
            // 异议不成立：回到等待期，并且等待期重新计时 ——
            // 否则异议可以卡在生效前一刻提交，处理完立刻到点生效，等于没给反应时间
            LocalDateTime endAt = now.plusDays(properties.waitPeriodDays());
            int rows = caseMapper.resolveDisputeToWaiting(caseId, entity.getVersion(),
                    endAt, request.comment(), now);
            requireRows(rows, "维持原审批");
        } else {
            int rows = caseMapper.reject(caseId, CaseStatus.DISPUTED, entity.getVersion(),
                    request.comment(), now);
            requireRows(rows, "支持异议并驳回");
        }

        disputeMapper.resolveAllOpen(caseId, request.comment(), admin.userId(), now);

        recordApproval(caseId, admin, ACTION_RESOLVE, CaseStatus.DISPUTED,
                upheld ? CaseStatus.WAITING_PERIOD : CaseStatus.REJECTED, request.comment(), now);

        auditService.success(admin.userId(), admin.role(), AuditAction.CASE_RESOLVE_DISPUTE,
                "CASE", caseId, Map.of("caseNo", entity.getCaseNo(), "result", request.result()));

        log.info("异议处理完成 caseNo={} result={}", entity.getCaseNo(), request.result());
    }

    // ==================================================================
    // 等待期到点生效（定时任务调用）
    // ==================================================================

    /**
     * 扫描等待期已到的案件并尝试生效。
     *
     * <p><b>授权生效前的全条件复核就发生在这里。</b>扫描时读到的状态只是"线索"，
     * 真正决定能不能生效的是本方法内重新查询并逐条核对的结果 ——
     * 因为从扫描到现在，用户可能已经恢复确认、关系可能被撤销、资产可能被删、
     * 也可能有新异议。
     *
     * @return 实际生效的案件数
     */
    public int authorizeDueCases() {
        LocalDateTime now = LocalDateTime.now();
        long lastId = 0L;
        int authorized = 0;

        while (true) {
            List<AuthorizationCase> batch = caseMapper.selectWaitPeriodExpired(now, lastId, SCAN_BATCH);
            if (batch.isEmpty()) {
                break;
            }
            for (AuthorizationCase scanned : batch) {
                lastId = scanned.getId();
                if (authorizeIfAllConditionsMet(scanned.getId(), now)) {
                    authorized++;
                }
            }
        }
        return authorized;
    }

    /**
     * 单个案件的全条件复核 + 生效。
     *
     * <p>任何一项不满足都会让案件离开 WAITING_PERIOD，而不是"等一下再说" ——
     * 前提不成立了，流程就该结束。
     */
    @Transactional
    public boolean authorizeIfAllConditionsMet(Long caseId, LocalDateTime now) {
        // 重新读取，不使用扫描时的快照
        AuthorizationCase entity = caseMapper.selectById(caseId);
        if (entity == null || !CaseStatus.WAITING_PERIOD.equals(entity.getStatus())) {
            return false;   // 已被并发改变（例如刚被提了异议、或被取消）
        }
        if (entity.getWaitPeriodEndAt() == null || entity.getWaitPeriodEndAt().isAfter(now)) {
            return false;
        }

        // 复核 1：所有者仍然处于失联核验状态？人回来了就不该授权
        HeartbeatConfig heartbeat = heartbeatMapper.selectById(entity.getOwnerId());
        if (heartbeat == null || !HeartbeatStatus.allowsAuthorizationApplication(heartbeat.getStatus())) {
            cancelBySystem(entity, "所有者已恢复状态确认，案件自动取消", now);
            return false;
        }

        // 复核 2：联系人关系仍然有效？
        TrustedContact contact = contactMapper.selectById(entity.getContactId());
        if (contact == null || !ContactStatus.ACTIVE.equals(contact.getStatus())) {
            cancelBySystem(entity, "联系人关系已失效，案件自动取消", now);
            return false;
        }

        // 复核 3：资产是否仍然属于该所有者且未删除？
        List<Long> assetIds = caseAssetMapper.selectAssetIds(caseId);
        if (assetIds.isEmpty()) {
            cancelBySystem(entity, "案件已无关联资产，自动取消", now);
            return false;
        }
        for (Long assetId : assetIds) {
            DigitalAsset asset = assetMapper.selectById(assetId);
            if (asset == null || asset.isDeleted() || !entity.getOwnerId().equals(asset.getOwnerId())) {
                cancelBySystem(entity, "授权范围内的资产已变更或删除，案件自动取消", now);
                return false;
            }
        }

        // 复核 4：是否存在未处理的异议？
        if (disputeMapper.countOpen(caseId) > 0) {
            caseMapper.markDisputed(caseId, entity.getVersion(), now);
            log.warn("案件存在未处理异议，暂停生效 caseNo={}", entity.getCaseNo());
            return false;
        }

        // 复核 5：这些资产是否已被其他生效案件覆盖？
        for (Long assetId : assetIds) {
            if (caseMapper.countOtherAuthorizedCases(assetId, caseId) > 0) {
                cancelBySystem(entity, "授权范围内的资产已被其他生效授权覆盖，案件自动取消", now);
                return false;
            }
        }

        // 全部通过，条件更新生效。若期间状态被改动，影响 0 行，静默放弃
        int rows = caseMapper.authorize(caseId, entity.getVersion(), now);
        if (rows == 0) {
            log.info("案件在生效前被并发修改，放弃本次生效 caseNo={}", entity.getCaseNo());
            return false;
        }

        auditService.success(null, "SYSTEM", AuditAction.CASE_AUTHORIZE,
                "CASE", caseId, Map.of("caseNo", entity.getCaseNo()));

        log.info("授权生效 caseNo={} ownerId={} contactId={}",
                entity.getCaseNo(), entity.getOwnerId(), entity.getContactId());
        return true;
    }

    private void cancelBySystem(AuthorizationCase entity, String reason, LocalDateTime now) {
        int rows = caseMapper.cancel(entity.getId(), CaseStatus.WAITING_PERIOD,
                entity.getVersion(), reason, now);
        if (rows > 0) {
            log.warn("案件复核未通过，自动取消 caseNo={} reason={}", entity.getCaseNo(), reason);
        }
    }

    // ==================================================================
    // 心跳恢复回调
    // ==================================================================

    /**
     * 用户完成状态确认时，作废其所有未生效案件。
     *
     * <p>由心跳服务在同一事务内调用。已生效（AUTHORIZED）的案件不在取消范围内 ——
     * 它是用户在清醒时通过完整流程表达的意愿，只能由用户自己撤销。
     */
    @Override
    public void onRecovered(Long userId) {
        int cancelled = caseMapper.cancelAllPendingByOwner(userId, LocalDateTime.now());
        if (cancelled > 0) {
            log.info("用户恢复确认，自动取消其未生效案件 {} 个 userId={}", cancelled, userId);
            auditService.success(null, "SYSTEM", AuditAction.CASE_CANCEL,
                    "USER", userId, Map.of("cancelledCount", cancelled, "reason", "OWNER_RECOVERED"));
        }
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    private AuthorizationCase requireCase(Long caseId) {
        AuthorizationCase entity = caseMapper.selectById(caseId);
        if (entity == null) {
            throw BizException.notFound("授权案件");
        }
        return entity;
    }

    private void assertStatus(AuthorizationCase entity, String expected, String action) {
        if (!expected.equals(entity.getStatus())) {
            if (CaseStatus.isTerminal(entity.getStatus())) {
                throw new StateConflictException(
                        "案件已结束（" + entity.getStatus() + "），不能" + action);
            }
            throw new StateConflictException(
                    "案件当前状态为 " + entity.getStatus() + "，不能" + action);
        }
    }

    private void assertOwner(AuthorizationCase entity, AuthPrincipal actor) {
        if (!entity.getOwnerId().equals(actor.userId())) {
            throw BizException.accessDenied("只有资产所有者可以执行此操作");
        }
    }

    private void assertApplicant(AuthorizationCase entity, AuthPrincipal actor) {
        Long applicantId = applicantUserIdOf(entity);
        if (!actor.userId().equals(applicantId)) {
            throw BizException.accessDenied("只有申请人可以执行此操作");
        }
    }

    private void assertOwnerOrApplicant(AuthorizationCase entity, AuthPrincipal actor) {
        boolean isOwner = entity.getOwnerId().equals(actor.userId());
        boolean isApplicant = actor.userId().equals(applicantUserIdOf(entity));
        if (!isOwner && !isApplicant) {
            // 既不是所有者也不是申请人：当作不存在，不泄露案件存在性
            throw BizException.notFound("授权案件");
        }
    }

    private void assertVisible(AuthorizationCase entity, AuthPrincipal actor) {
        boolean visible = entity.getOwnerId().equals(actor.userId())
                || actor.userId().equals(applicantUserIdOf(entity))
                || actor.isAdmin();
        if (!visible) {
            throw BizException.notFound("授权案件");
        }
    }

    /**
     * 由 contactId（trusted_contact 主键）解析出申请人用户 ID。
     */
    private Long applicantUserIdOf(AuthorizationCase entity) {
        TrustedContact contact = contactMapper.selectById(entity.getContactId());
        return contact == null ? null : contact.getContactUserId();
    }

    /**
     * 检查条件更新的影响行数。为 0 就意味着并发冲突 ——
     * 这是乐观锁唯一可靠的使用方式：不检查返回值的乐观锁等于没有锁。
     */
    private void requireRows(int rows, String action) {
        if (rows == 0) {
            throw new StateConflictException(action + "失败：案件状态已被其他操作改变，请刷新后重试");
        }
    }

    private void recordApproval(Long caseId, AuthPrincipal admin, String action,
                                String from, String to, String comment, LocalDateTime now) {
        AuthorizationApproval approval = new AuthorizationApproval();
        approval.setCaseId(caseId);
        approval.setApproverId(admin.userId());
        approval.setAction(action);
        approval.setFromStatus(from);
        approval.setToStatus(to);
        approval.setComment(comment);
        approval.setCreatedAt(now);
        approvalMapper.insert(approval);
    }

    private CaseDetailResponse toDetail(AuthorizationCase entity, AuthPrincipal actor) {
        List<Long> assetIds = caseAssetMapper.selectAssetIds(entity.getId());
        List<ApprovalItem> approvals = approvalMapper.selectByCase(entity.getId()).stream()
                .map(ApprovalItem::from)
                .toList();

        Long applicantId = applicantUserIdOf(entity);

        return new CaseDetailResponse(
                entity.getId(),
                entity.getCaseNo(),
                entity.getOwnerId(),
                entity.getContactId(),
                entity.getStatus(),
                entity.getReason(),
                entity.getReviewComment(),
                assetIds,
                entity.getWaitPeriodEndAt(),
                entity.getAuthorizedAt(),
                entity.getRevokedAt(),
                entity.getClosedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                approvals,
                entity.getOwnerId().equals(actor.userId()),
                applicantId != null && applicantId.equals(actor.userId())
        );
    }

    /** 供其他模块（如通知）查询用的只读入口 */
    public AuthorizationCase findById(Long caseId) {
        return caseMapper.selectById(caseId);
    }
}
