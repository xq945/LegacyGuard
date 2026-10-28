package com.legacyguard.module.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.legacyguard.common.web.ClientIpResolver;
import com.legacyguard.common.web.RequestContext;
import com.legacyguard.module.audit.domain.AuditLog;
import com.legacyguard.module.audit.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 安全审计。
 *
 * <p><b>写入时机</b>：关键业务操作与审计记录必须在<b>同一事务</b>内完成 ——
 * 业务成功而审计失败，等于做了一次没有痕迹的操作。
 * 因此本类的方法不带 {@code @Transactional}，由调用方的业务事务包裹。
 *
 * <p><b>失败处理</b>：{@code detail} 序列化失败不抛异常（审计的价值在于记录，
 * 不应该因为一个摘要字段格式问题把整笔业务回滚），但数据库写入失败会向上抛出。
 *
 * <p><b>禁止写入</b>：密码及其哈希、JWT、资产明文或密文、加密密钥、验证码。
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILURE = "FAILURE";

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    public AuditService(AuditLogMapper auditLogMapper, ObjectMapper objectMapper,
                        ClientIpResolver clientIpResolver) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
    }

    public void success(Long actorId, String actorRole, String action,
                        String objectType, Long objectId, Map<String, Object> detail) {
        record(actorId, actorRole, action, objectType, objectId, RESULT_SUCCESS, detail);
    }

    public void failure(Long actorId, String actorRole, String action,
                        String objectType, Long objectId, Map<String, Object> detail) {
        record(actorId, actorRole, action, objectType, objectId, RESULT_FAILURE, detail);
    }

    private void record(Long actorId, String actorRole, String action,
                        String objectType, Long objectId, String result, Map<String, Object> detail) {

        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setActorRole(actorRole);
        entry.setAction(action);
        entry.setObjectType(objectType);
        entry.setObjectId(objectId);
        entry.setResult(result);
        entry.setRequestId(RequestContext.currentRequestId());
        entry.setDetail(toJson(detail));
        entry.setClientIp(currentClientIp());
        entry.setUserAgent(currentUserAgent());
        entry.setCreatedAt(java.time.LocalDateTime.now());

        auditLogMapper.insert(entry);

        log.debug("审计记录 action={} object={}:{} result={}", action, objectType, objectId, result);
    }

    /**
     * 摘要序列化。失败时降级为 null 而不是抛异常 ——
     * 审计的价值在于"发生过这件事"，摘要只是附加信息。
     */
    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            log.warn("审计摘要序列化失败，已省略 detail", e);
            return null;
        }
    }

    /**
     * 取客户端 IP。是否采信 {@code X-Forwarded-For} 由配置决定，
     * 详见 {@link ClientIpResolver}。
     */
    private String currentClientIp() {
        return clientIpResolver.resolve(currentRequest());
    }

    private String currentUserAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : truncate(request.getHeader("User-Agent"), 255);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
