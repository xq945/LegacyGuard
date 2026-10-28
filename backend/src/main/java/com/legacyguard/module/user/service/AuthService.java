package com.legacyguard.module.user.service;

import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.common.ratelimit.RateLimitService;
import com.legacyguard.module.audit.domain.AuditAction;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.module.user.config.AuthProperties;
import com.legacyguard.module.user.domain.LoginRecord;
import com.legacyguard.module.user.domain.SysUser;
import com.legacyguard.module.user.domain.UserRole;
import com.legacyguard.module.user.domain.UserStatus;
import com.legacyguard.module.user.dto.LoginRequest;
import com.legacyguard.module.user.dto.LoginResponse;
import com.legacyguard.module.user.dto.RegisterRequest;
import com.legacyguard.module.user.dto.UserProfileResponse;
import com.legacyguard.module.user.mapper.LoginRecordMapper;
import com.legacyguard.module.user.mapper.SysUserMapper;
import com.legacyguard.security.AuthPrincipal;
import com.legacyguard.security.JwtProperties;
import com.legacyguard.security.JwtProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证服务：注册、登录、登出。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** BCrypt 的输入上限，超出部分会被静默截断 */
    private static final int BCRYPT_MAX_BYTES = 72;

    private static final String BLACKLIST_PREFIX = "lg:jwt:blacklist:";

    /** 用户不存在时用于消耗等量时间，降低"通过响应时间枚举用户名"的可能 */
    private final String dummyPasswordHash;

    private final SysUserMapper userMapper;
    private final LoginRecordMapper loginRecordMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final AuditService auditService;
    private final RateLimitService rateLimitService;
    private final StringRedisTemplate redis;
    private final AuthProperties authProperties;

    public AuthService(SysUserMapper userMapper,
                       LoginRecordMapper loginRecordMapper,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       JwtProperties jwtProperties,
                       AuditService auditService,
                       RateLimitService rateLimitService,
                       StringRedisTemplate redis,
                       AuthProperties authProperties) {
        this.userMapper = userMapper;
        this.loginRecordMapper = loginRecordMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.auditService = auditService;
        this.rateLimitService = rateLimitService;
        this.redis = redis;
        this.authProperties = authProperties;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalizer-dummy");
    }

    // ==================================================================
    // 注册
    // ==================================================================

    /**
     * 注册新用户。
     *
     * <p>先查重再插入属于"友好提示"层面的检查，真正的唯一性由数据库上的
     * {@code uk_username_active} 保证 —— 两个并发注册请求可能同时通过查重，
     * 此时后插入的那条会因唯一约束失败，被转换为 409。
     */
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        validatePasswordLength(request.password());

        if (userMapper.selectByUsername(request.username()) != null) {
            throw new BizException(ErrorCode.DUPLICATE_REQUEST, "该用户名已被占用");
        }

        LocalDateTime now = LocalDateTime.now();
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setEmail(blankToNull(request.email()));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginCount(0);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册同名账号：唯一索引是最终防线
            throw new BizException(ErrorCode.DUPLICATE_REQUEST, "该用户名已被占用");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("username", user.getUsername());
        detail.put("role", user.getRole());
        auditService.success(user.getId(), user.getRole(), AuditAction.REGISTER,
                "USER", user.getId(), detail);

        log.info("新用户注册成功 userId={} username={}", user.getId(), user.getUsername());
        return UserProfileResponse.from(user);
    }

    // ==================================================================
    // 登录
    // ==================================================================

    /**
     * 登录。
     *
     * <p><b>刻意不加 {@code @Transactional}</b>：登录失败时要写登录流水、累加失败计数、
     * 写审计日志，然后抛出 401。如果整体包在一个事务里，抛出异常会让这些记录
     * 全部回滚 —— 于是"失败次数永远涨不上去"，锁定策略彻底失效。
     * 这些写操作各自独立提交才符合语义：它们记录的是"发生过的事"，
     * 不应该因为登录本身失败而被抹掉。
     */
    public LoginResponse login(LoginRequest request, String clientIp) {
        checkLoginRateLimit(clientIp, request.username());

        SysUser user = userMapper.selectByUsername(request.username());

        if (user == null) {
            // 消耗与真实校验相当的时间，避免通过响应快慢判断用户名是否存在
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            recordLoginFailure(null, request.username(), "NOT_FOUND", clientIp);
            throw new BizException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }

        if (UserStatus.DISABLED == user.getStatus()) {
            recordLoginFailure(user.getId(), user.getUsername(), "DISABLED", clientIp);
            throw new BizException(ErrorCode.ACCESS_DENIED, "账号已被禁用，请联系管理员");
        }

        if (UserStatus.LOCKED == user.getStatus()) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                recordLoginFailure(user.getId(), user.getUsername(), "LOCKED", clientIp);
                throw new BizException(ErrorCode.ACCESS_DENIED,
                        "账号因多次登录失败已被锁定，请于 " + user.getLockedUntil() + " 后重试");
            }
            // 锁定已到期，自动解锁后继续本次登录
            userMapper.unlockIfExpired(user.getId());
            user.setStatus(UserStatus.ACTIVE);
            user.setFailedLoginCount(0);
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleWrongPassword(user, clientIp);
            throw new BizException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }

        return completeLogin(user, clientIp);
    }

    private LoginResponse completeLogin(SysUser user, String clientIp) {
        LocalDateTime now = LocalDateTime.now();
        userMapper.markLoginSuccess(user.getId(), now);

        String token = jwtProvider.issue(user.getId(), user.getUsername(), user.getRole());

        LoginRecord record = new LoginRecord();
        record.setUserId(user.getId());
        record.setUsername(user.getUsername());
        record.setSuccess(1);
        record.setClientIp(clientIp);
        record.setCreatedAt(now);
        loginRecordMapper.insert(record);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("username", user.getUsername());
        auditService.success(user.getId(), user.getRole(), AuditAction.LOGIN_SUCCESS,
                "USER", user.getId(), detail);

        user.setLastLoginAt(now);
        log.info("用户登录成功 userId={} username={}", user.getId(), user.getUsername());

        return new LoginResponse(
                token,
                jwtProperties.accessTokenTtl().toSeconds(),
                UserProfileResponse.from(user));
    }

    /**
     * 处理密码错误：累加计数，达到阈值则锁定。
     *
     * <p>注意这里读到的 {@code failedLoginCount} 是本次自增<b>之前</b>的值，
     * 实际自增由 SQL 原子完成。并发场景下多个请求可能都读到同一个旧值，
     * 但自增本身是原子的，最终计数是准确的，锁定会稍晚一两轮 —-
     * 对一个防爆破机制来说完全可以接受，换来的是不必给用户行加锁。
     */
    private void handleWrongPassword(SysUser user, String clientIp) {
        userMapper.increaseFailedLogin(user.getId());

        int currentFailures = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("username", user.getUsername());
        detail.put("failures", currentFailures);

        if (currentFailures >= authProperties.maxLoginFailures()) {
            LocalDateTime lockedUntil = LocalDateTime.now().plus(authProperties.lockDuration());
            userMapper.lockUntil(user.getId(), lockedUntil);
            detail.put("lockedUntil", lockedUntil.toString());
            log.warn("账号因连续登录失败被锁定 userId={} until={}", user.getId(), lockedUntil);
        }

        recordLoginFailure(user.getId(), user.getUsername(), "BAD_CREDENTIAL", clientIp, detail);
    }

    private void recordLoginFailure(Long userId, String username, String reason, String clientIp) {
        recordLoginFailure(userId, username, reason, clientIp, null);
    }

    private void recordLoginFailure(Long userId, String username, String reason,
                                    String clientIp, Map<String, Object> extra) {
        LoginRecord record = new LoginRecord();
        record.setUserId(userId);
        record.setUsername(username);
        record.setSuccess(0);
        record.setFailReason(reason);
        record.setClientIp(clientIp);
        record.setCreatedAt(LocalDateTime.now());
        loginRecordMapper.insert(record);

        Map<String, Object> detail = extra != null ? new LinkedHashMap<>(extra) : new LinkedHashMap<>();
        detail.put("username", username);
        detail.put("reason", reason);
        auditService.failure(userId, null, AuditAction.LOGIN_FAILURE, "USER", userId, detail);
    }

    /**
     * 登录接口限流，<b>同时按 IP 和账号两个维度</b>。
     *
     * <p>两个维度缺一不可：
     * <ul>
     *   <li><b>IP 维度</b>挡住"一台机器换着用户名撞库"</li>
     *   <li><b>账号维度</b>挡住"换 IP 反复撞同一个账号" —— 如果只按 IP 限流，
     *       攻击者用代理池轮换 IP 就能绕过；反过来只按账号限流，
     *       则挡不住拿用户名字典横扫的撞库</li>
     * </ul>
     *
     * <p>用户名统一转小写做 key：数据库的排序规则是大小写不敏感的，
     * {@code ZhangSan} 与 {@code zhangsan} 是同一个账号，限流键也必须一致。
     *
     * <p>在这两层之上还有第三道防线 —— 连续失败达到阈值后锁定账号。
     * 前两层是"限速"，第三层是"熔断"，即使限流被绕过，账号仍会被锁住。
     */
    private void checkLoginRateLimit(String clientIp, String username) {
        String ipKey = "login:ip:" + (clientIp == null ? "unknown" : clientIp);
        String userKey = "login:user:" + (username == null ? "unknown" : username.toLowerCase());

        int limit = authProperties.loginRateLimit();
        Duration window = authProperties.loginRateWindow();

        if (!rateLimitService.tryAcquire(ipKey, limit, window)
                || !rateLimitService.tryAcquire(userKey, limit, window)) {
            log.warn("登录请求超限 ip={} username={}", clientIp, username);
            throw new BizException(ErrorCode.RATE_LIMITED, "登录尝试过于频繁，请稍后再试");
        }
    }

    // ==================================================================
    // 登出
    // ==================================================================

    /**
     * 登出：把当前令牌的 jti 写入黑名单，TTL 设为令牌剩余有效期。
     *
     * <p>JWT 无法主动作废，黑名单是让其提前失效的唯一手段。
     * 如果 Redis 不可用，这里<b>不报错</b>：令牌最多 30 分钟自然过期，
     * 前端清除本地存储即可；报错反而会让用户以为登出失败而反复操作。
     */
    public void logout(String rawToken, AuthPrincipal principal) {
        Duration ttl = jwtProvider.remainingTtl(rawToken);
        if (!ttl.isZero() && !ttl.isNegative()) {
            try {
                redis.opsForValue().set(BLACKLIST_PREFIX + principal.tokenId(), "1", ttl);
            } catch (RuntimeException e) {
                log.warn("Redis 不可用，登出未能写入黑名单 jti={}", principal.tokenId(), e);
            }
        }

        auditService.success(principal.userId(), principal.role(), AuditAction.LOGOUT,
                "USER", principal.userId(), Map.of("username", principal.username()));
        log.info("用户登出 userId={}", principal.userId());
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /**
     * BCrypt 只处理前 72 个字节，超出部分被静默丢弃 —— 意味着两个密码只要
     * 前 72 字节相同就会被判定为同一个。必须显式拒绝，而不是让用户以为设了长密码。
     */
    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "密码过长（BCrypt 最多支持 " + BCRYPT_MAX_BYTES + " 字节），请缩短至 72 字节以内");
        }
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
