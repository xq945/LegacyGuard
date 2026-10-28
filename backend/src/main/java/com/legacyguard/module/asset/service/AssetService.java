package com.legacyguard.module.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.common.crypto.AssetCipher;
import com.legacyguard.common.crypto.EncryptedPayload;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.common.web.ClientIpResolver;
import com.legacyguard.module.asset.config.AssetProperties;
import com.legacyguard.module.asset.domain.AssetAccessLog;
import com.legacyguard.module.asset.domain.AssetType;
import com.legacyguard.module.asset.domain.DigitalAsset;
import com.legacyguard.module.asset.domain.DigitalAssetSecret;
import com.legacyguard.module.asset.dto.AssetContentResponse;
import com.legacyguard.module.asset.dto.AssetResponse;
import com.legacyguard.module.asset.dto.CreateAssetRequest;
import com.legacyguard.module.asset.dto.UpdateAssetRequest;
import com.legacyguard.module.asset.mapper.AssetAccessLogMapper;
import com.legacyguard.module.asset.mapper.AssetAccessMapper;
import com.legacyguard.module.asset.mapper.DigitalAssetMapper;
import com.legacyguard.module.asset.mapper.DigitalAssetSecretMapper;
import com.legacyguard.module.audit.domain.AuditAction;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * 数字资产服务。
 *
 * <p>所有涉及"这份资产属于谁"的判断，身份都来自传入的 {@link AuthPrincipal}，
 * 它由过滤器从 JWT 解析后放进认证上下文 —— 不接受任何前端提交的用户 ID。
 */
@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    private static final int STATUS_NORMAL = 0;

    private final DigitalAssetMapper assetMapper;
    private final DigitalAssetSecretMapper secretMapper;
    private final AssetAccessMapper accessMapper;
    private final AssetAccessLogMapper accessLogMapper;
    private final AssetCipher cipher;
    private final AssetAccessGuard accessGuard;
    private final AuditService auditService;
    private final ClientIpResolver clientIpResolver;
    private final AssetProperties properties;

    public AssetService(DigitalAssetMapper assetMapper,
                        DigitalAssetSecretMapper secretMapper,
                        AssetAccessMapper accessMapper,
                        AssetAccessLogMapper accessLogMapper,
                        AssetCipher cipher,
                        AssetAccessGuard accessGuard,
                        AuditService auditService,
                        ClientIpResolver clientIpResolver,
                        AssetProperties properties) {
        this.assetMapper = assetMapper;
        this.secretMapper = secretMapper;
        this.accessMapper = accessMapper;
        this.accessLogMapper = accessLogMapper;
        this.cipher = cipher;
        this.accessGuard = accessGuard;
        this.auditService = auditService;
        this.clientIpResolver = clientIpResolver;
        this.properties = properties;
    }

    // ==================================================================
    // 创建
    // ==================================================================

    /**
     * 创建资产。
     *
     * <p><b>为什么先插元数据再加密</b>：加密时要用 assetId 作为 AAD，
     * 把密文与这条记录绑定死。而 assetId 是数据库自增的，必须先入库才能拿到。
     * 两步在同一事务里，任一步失败都不会留下半条数据。
     */
    @Transactional
    public AssetResponse create(AuthPrincipal actor, CreateAssetRequest request) {
        validateAssetType(request.assetType());
        validateContentSize(request.content());

        long existing = assetMapper.countActiveByOwner(actor.userId());
        if (existing >= properties.maxPerUser()) {
            throw new BizException(ErrorCode.BIZ_RULE_VIOLATION,
                    "资产数量已达上限（" + properties.maxPerUser() + " 条）");
        }

        LocalDateTime now = LocalDateTime.now();

        DigitalAsset asset = new DigitalAsset();
        asset.setOwnerId(actor.userId());
        asset.setName(request.name());
        asset.setAssetType(request.assetType());
        asset.setDescription(request.description());
        asset.setFileName(request.fileName());
        asset.setFileSize(request.fileSize());
        asset.setMimeType(request.mimeType());
        asset.setStatus(STATUS_NORMAL);
        asset.setVersion(0);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        assetMapper.insert(asset);

        // 自增主键已回填，可以拿它做 AAD 了
        EncryptedPayload payload = cipher.encrypt(asset.getId(), request.content());
        secretMapper.insert(toSecretEntity(asset.getId(), payload, now));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("name", request.name());
        detail.put("assetType", request.assetType());
        detail.put("plainSize", payload.plainSize());
        auditService.success(actor.userId(), actor.role(), AuditAction.ASSET_CREATE,
                "ASSET", asset.getId(), detail);

        log.info("创建资产 userId={} assetId={} plainSize={}", actor.userId(), asset.getId(), payload.plainSize());
        return AssetResponse.from(asset);
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /**
     * 列出自己的资产（只返回元数据，不碰密文表）。
     */
    public PageResult<AssetResponse> list(AuthPrincipal actor, long page, long size) {
        Page<DigitalAsset> paging = new Page<>(page, size);
        LambdaQueryWrapper<DigitalAsset> query = new LambdaQueryWrapper<DigitalAsset>()
                .eq(DigitalAsset::getOwnerId, actor.userId())
                .isNull(DigitalAsset::getDeletedAt)
                .orderByDesc(DigitalAsset::getUpdatedAt)
                .orderByDesc(DigitalAsset::getId);

        IPage<DigitalAsset> result = assetMapper.selectPage(paging, query);

        List<AssetResponse> records = result.getRecords().stream().map(AssetResponse::from).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    /**
     * 读取资产元数据。所有者或已授权联系人可见。
     */
    public AssetResponse getMeta(AuthPrincipal actor, Long assetId) {
        DigitalAsset asset = requireReadableAsset(actor, assetId);
        return AssetResponse.from(asset);
    }

    /**
     * 读取资产明文内容。
     *
     * <p>这是整个系统里唯一返回明文的入口，守卫就在这里，且<b>每次都会执行</b>。
     */
    public AssetContentResponse getContent(AuthPrincipal actor, Long assetId, String clientIp) {
        AccessResult access = accessGuard.evaluate(assetId, actor.userId());

        if (!access.allowed()) {
            // 记录拒绝流水与审计。这两步是"留证据"，不是"判定" ——
            // 已经有了拒绝结论，就不该让留痕失败把结论改写成 500 或 422。
            // 曾经因为这里写库失败（access_type 违反非空约束），
            // 导致越权请求返回 422 而不是 403，接口语义完全走样。
            recordDenialQuietly(assetId, actor, access, clientIp);

            // 不存在的资产返回 404，存在但无权访问返回 403 ——
            // 这个区分是有意的：如果统一返回 404，用户会以为自己的资产消失了
            if ("NOT_FOUND".equals(access.denyReason())) {
                throw BizException.notFound("资产");
            }
            throw BizException.accessDenied("无权访问该资产");
        }

        DigitalAsset asset = assetMapper.selectById(assetId);
        DigitalAssetSecret secret = secretMapper.selectById(assetId);
        if (secret == null) {
            // 元数据在但密文缺失，属于数据不一致，是系统故障而不是用户问题
            log.error("资产密文缺失 assetId={}", assetId);
            throw new IllegalStateException("资产密文缺失");
        }

        String plaintext = cipher.decrypt(assetId, toPayload(secret));

        recordAccess(assetId, access.caseId(), actor.userId(), access.accessType(),
                "SUCCESS", null, clientIp);

        auditService.success(actor.userId(), actor.role(), AuditAction.ASSET_VIEW,
                "ASSET", assetId, Map.of("accessType", access.accessType()));

        return AssetContentResponse.of(asset, plaintext, access.accessType());
    }

    // ==================================================================
    // 修改
    // ==================================================================

    /**
     * 修改资产。<b>仅所有者可调用。</b>
     *
     * <p>被授权的联系人即使能读，也不能改 —— 授权转移的是查看权，不是处置权。
     * 因此这里不用 {@link AssetAccessGuard}（它允许联系人通过），
     * 而是直接校验所有者身份。
     */
    @Transactional
    public AssetResponse update(AuthPrincipal actor, Long assetId, UpdateAssetRequest request) {
        DigitalAsset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.isDeleted()) {
            throw BizException.notFound("资产");
        }
        if (!asset.getOwnerId().equals(actor.userId())) {
            throw BizException.accessDenied("只有资产所有者可以修改");
        }

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<DigitalAsset> wrapper = new LambdaUpdateWrapper<DigitalAsset>()
                .eq(DigitalAsset::getId, assetId)
                .isNull(DigitalAsset::getDeletedAt);

        Map<String, Object> changed = new LinkedHashMap<>();

        if (request.name() != null) {
            wrapper.set(DigitalAsset::getName, request.name());
            changed.put("name", request.name());
        }
        if (request.assetType() != null) {
            validateAssetType(request.assetType());
            wrapper.set(DigitalAsset::getAssetType, request.assetType());
            changed.put("assetType", request.assetType());
        }
        if (request.description() != null) {
            wrapper.set(DigitalAsset::getDescription, request.description());
            changed.put("description", true);
        }
        if (request.fileName() != null) {
            wrapper.set(DigitalAsset::getFileName, request.fileName());
        }
        if (request.fileSize() != null) {
            wrapper.set(DigitalAsset::getFileSize, request.fileSize());
        }
        if (request.mimeType() != null) {
            wrapper.set(DigitalAsset::getMimeType, request.mimeType());
        }

        // 内容变更需要重新加密；AAD 仍用同一个 assetId，密文与记录依然绑定
        if (request.content() != null) {
            validateContentSize(request.content());
            EncryptedPayload payload = cipher.encrypt(assetId, request.content());
            secretMapper.updateById(toSecretEntity(assetId, payload, now));
            changed.put("content", true);
        }

        if (changed.isEmpty()) {
            return AssetResponse.from(asset);
        }

        wrapper.set(DigitalAsset::getUpdatedAt, now)
                .setSql("version = version + 1");

        int rows = assetMapper.update(null, wrapper);
        if (rows == 0) {
            throw new com.legacyguard.common.exception.StateConflictException("资产已被其他操作修改，请刷新后重试");
        }

        auditService.success(actor.userId(), actor.role(), AuditAction.ASSET_UPDATE,
                "ASSET", assetId, Map.of("fields", changed.keySet()));

        return AssetResponse.from(assetMapper.selectById(assetId));
    }

    // ==================================================================
    // 删除
    // ==================================================================

    /**
     * 软删除资产。
     *
     * <p>删除前必须检查是否还有未终结的授权案件在引用它 ——
     * 资产一旦进入授权流程，删掉它会让案件指向空对象，审批结论就失去了意义。
     */
    @Transactional
    public void delete(AuthPrincipal actor, Long assetId) {
        DigitalAsset asset = assetMapper.selectById(assetId);
        if (asset == null || asset.isDeleted()) {
            throw BizException.notFound("资产");
        }
        if (!asset.getOwnerId().equals(actor.userId())) {
            throw BizException.accessDenied("只有资产所有者可以删除");
        }

        int openCases = accessMapper.countOpenCases(assetId);
        if (openCases > 0) {
            throw new BizException(ErrorCode.BIZ_RULE_VIOLATION,
                    "该资产正在被 " + openCases + " 个进行中的授权案件引用，无法删除");
        }

        int rows = assetMapper.softDelete(assetId, actor.userId(), LocalDateTime.now());
        if (rows == 0) {
            // 并发删除：另一个请求已经删掉了
            throw new BizException(ErrorCode.STATE_CONFLICT, "资产已被删除");
        }

        auditService.success(actor.userId(), actor.role(), AuditAction.ASSET_DELETE,
                "ASSET", assetId, Map.of("name", asset.getName()));
        log.info("删除资产 userId={} assetId={}", actor.userId(), assetId);
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * 校验当前用户可以读取该资产，并返回资产实体。
     * 供只返回元数据的接口使用 —— 元数据同样是需要权限的。
     */
    private DigitalAsset requireReadableAsset(AuthPrincipal actor, Long assetId) {
        AccessResult access = accessGuard.evaluate(assetId, actor.userId());
        if (!access.allowed()) {
            if ("NOT_FOUND".equals(access.denyReason())) {
                throw BizException.notFound("资产");
            }
            throw BizException.accessDenied("无权访问该资产");
        }
        return assetMapper.selectById(assetId);
    }

    private void validateAssetType(String assetType) {
        if (!AssetType.isValid(assetType)) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "资产类型必须是 " + AssetType.all() + " 之一");
        }
    }

    /**
     * 按<b>字节数</b>而不是字符数校验 —— 一个汉字在 UTF-8 下占 3 字节，
     * 若按字符数限制，同样的"字数"可能产生三倍的数据量。
     */
    private void validateContentSize(String content) {
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > properties.maxContentBytes()) {
            throw new BizException(ErrorCode.BIZ_RULE_VIOLATION,
                    "资产内容过大（" + bytes + " 字节），上限为 " + properties.maxContentBytes() + " 字节");
        }
    }

    private DigitalAssetSecret toSecretEntity(Long assetId, EncryptedPayload payload, LocalDateTime now) {
        DigitalAssetSecret secret = new DigitalAssetSecret();
        secret.setAssetId(assetId);
        secret.setWrappedDek(payload.wrappedDek());
        secret.setKekVersion(payload.kekVersion());
        secret.setCipherText(payload.cipherText());
        secret.setIv(payload.iv());
        secret.setAuthTag(payload.authTag());
        secret.setContentHash(payload.contentHash());
        secret.setPlainSize(payload.plainSize());
        secret.setCreatedAt(now);
        secret.setUpdatedAt(now);
        return secret;
    }

    private EncryptedPayload toPayload(DigitalAssetSecret secret) {
        return new EncryptedPayload(
                secret.getCipherText(),
                secret.getIv(),
                secret.getAuthTag(),
                secret.getWrappedDek(),
                secret.getKekVersion(),
                secret.getContentHash(),
                secret.getPlainSize());
    }

    /**
     * 记录拒绝流水与审计，失败只记日志、不改变已经得出的拒绝结论。
     *
     * <p>注意两者的不对称：<b>成功</b>路径的流水写入失败必须让请求失败
     * （没有留痕的敏感数据访问不该放行），而<b>拒绝</b>路径的留痕失败
     * 只应记 ERROR，返回值仍然必须是 403/404。
     */
    private void recordDenialQuietly(Long assetId, AuthPrincipal actor, AccessResult access, String clientIp) {
        try {
            recordAccess(assetId, null, actor.userId(), AccessResult.DENIED_TYPE,
                    "DENIED", access.denyReason(), clientIp);
        } catch (RuntimeException e) {
            log.error("拒绝访问流水写入失败 assetId={} accessorId={}", assetId, actor.userId(), e);
        }

        try {
            auditService.failure(actor.userId(), actor.role(), AuditAction.ASSET_VIEW,
                    "ASSET", assetId, Map.of("reason", access.denyReason()));
        } catch (RuntimeException e) {
            log.error("拒绝访问审计写入失败 assetId={} accessorId={}", assetId, actor.userId(), e);
        }
    }

    /**
     * 记录访问流水。成功与拒绝都要记 —— 被拒绝的尝试才是越权分析的关键证据。
     */
    private void recordAccess(Long assetId, Long caseId, Long accessorId, String accessType,
                              String result, String denyReason, String clientIp) {
        AssetAccessLog entry = new AssetAccessLog();
        entry.setAssetId(assetId);
        entry.setCaseId(caseId);
        entry.setAccessorId(accessorId);
        // access_type 列非空，拒绝时没有有效访问身份，用固定值占位
        entry.setAccessType(accessType == null ? AccessResult.DENIED_TYPE : accessType);
        entry.setResult(result);
        entry.setDenyReason(denyReason);
        entry.setClientIp(clientIp);
        entry.setCreatedAt(LocalDateTime.now());
        accessLogMapper.insert(entry);
    }
}
