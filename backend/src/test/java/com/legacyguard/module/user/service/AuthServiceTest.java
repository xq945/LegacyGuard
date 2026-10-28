package com.legacyguard.module.user.service;

import com.legacyguard.common.api.ErrorCode;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.common.ratelimit.RateLimitService;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.module.user.config.AuthProperties;
import com.legacyguard.module.user.domain.LoginRecord;
import com.legacyguard.module.user.domain.SysUser;
import com.legacyguard.module.user.domain.UserRole;
import com.legacyguard.module.user.domain.UserStatus;
import com.legacyguard.module.user.dto.LoginRequest;
import com.legacyguard.module.user.dto.LoginResponse;
import com.legacyguard.module.user.dto.RegisterRequest;
import com.legacyguard.module.user.mapper.LoginRecordMapper;
import com.legacyguard.module.user.mapper.SysUserMapper;
import com.legacyguard.security.AuthPrincipal;
import com.legacyguard.security.JwtProperties;
import com.legacyguard.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试。
 *
 * <p>密码编码器用真实的 BCrypt（strength=4，仅测试用，速度约为主流配置的 1/64），
 * 而不用 mock —— 如果把它 mock 掉，"密码是否正确"这条逻辑就完全没被测到。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String RAW_PASSWORD = "Str0ngPass";
    private static final String CLIENT_IP = "203.0.113.7";

    private static final String JWT_SECRET = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private LoginRecordMapper loginRecordMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private StringRedisTemplate redis;

    private PasswordEncoder passwordEncoder;
    private JwtProvider jwtProvider;
    private JwtProperties jwtProperties;
    private AuthProperties authProperties;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        jwtProperties = new JwtProperties(JWT_SECRET, Duration.ofMinutes(30), "legacyguard-test");
        jwtProvider = new JwtProvider(jwtProperties);
        authProperties = new AuthProperties(5, Duration.ofMinutes(15), 10, Duration.ofMinutes(1));
        authService = new AuthService(userMapper, loginRecordMapper, passwordEncoder, jwtProvider,
                jwtProperties, auditService, rateLimitService, redis, authProperties);

        // 默认放行限流；个别用例会覆盖
        lenient().when(rateLimitService.tryAcquire(anyString(), anyInt(), any())).thenReturn(true);
    }

    private SysUser existingUser(String rawPassword) {
        SysUser user = new SysUser();
        user.setId(42L);
        user.setUsername("zhangsan");
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setDisplayName("张三");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setFailedLoginCount(0);
        return user;
    }

    // ==================================================================
    // 注册
    // ==================================================================

    @Nested
    @DisplayName("注册")
    class Register {

        @Test
        @DisplayName("注册成功后密码以哈希存储，绝不明文")
        void registerStoresHashedPassword() {
            SysUser mapped = new SysUser();
            mapped.setId(1L);
            when(userMapper.insert(any(SysUser.class))).thenAnswer(invocation -> {
                // 模拟数据库自增主键回填
                invocation.<SysUser>getArgument(0).setId(1L);
                return 1;
            });

            authService.register(new RegisterRequest("lisi", RAW_PASSWORD, "李四", "lisi@example.com"));

            ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
            verify(userMapper).insert(captor.capture());
            SysUser saved = captor.getValue();

            assertThat(saved.getPasswordHash()).isNotEqualTo(RAW_PASSWORD);
            assertThat(saved.getPasswordHash()).startsWith("$2");
            assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getPasswordHash())).isTrue();
            assertThat(saved.getRole()).isEqualTo(UserRole.USER);
            assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("用户名已存在时拒绝注册")
        void rejectDuplicateUsername() {
            when(userMapper.selectByUsername("zhangsan")).thenReturn(existingUser(RAW_PASSWORD));

            assertThatThrownBy(() -> authService.register(
                    new RegisterRequest("zhangsan", RAW_PASSWORD, "张三", null)))
                    .isInstanceOf(BizException.class);

            verify(userMapper, never()).insert(any(SysUser.class));
        }

        @Test
        @DisplayName("密码超过 BCrypt 的 72 字节上限时拒绝，而不是静默截断")
        void rejectPasswordLongerThanBcryptLimit() {
            // 25 个中文字符，UTF-8 下 75 字节：字符数远小于 64，但字节数已超限
            String longPassword = "密码密码密码密码密码密码密码密码密码密码密码密码密1";

            assertThatThrownBy(() -> authService.register(
                    new RegisterRequest("lisi", longPassword, "李四", null)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("72");

            verify(userMapper, never()).insert(any(SysUser.class));
        }
    }

    // ==================================================================
    // 登录
    // ==================================================================

    @Nested
    @DisplayName("登录")
    class Login {

        @Test
        @DisplayName("凭证正确时签发令牌并重置失败计数")
        void loginSuccess() {
            when(userMapper.selectByUsername("zhangsan")).thenReturn(existingUser(RAW_PASSWORD));

            LoginResponse response = authService.login(
                    new LoginRequest("zhangsan", RAW_PASSWORD), CLIENT_IP);

            assertThat(response.accessToken()).isNotBlank();
            assertThat(response.expiresInSeconds()).isEqualTo(1800);

            // 令牌确实可以用同一把密钥解析回来
            AuthPrincipal principal = jwtProvider.parse(response.accessToken());
            assertThat(principal.userId()).isEqualTo(42L);
            assertThat(principal.role()).isEqualTo(UserRole.USER);

            verify(userMapper).markLoginSuccess(eq(42L), any(LocalDateTime.class));
            verify(userMapper, never()).increaseFailedLogin(any());
        }

        @Test
        @DisplayName("用户不存在时返回统一的错误提示，且记录失败流水")
        void unknownUserIsIndistinguishable() {
            when(userMapper.selectByUsername("nobody")).thenReturn(null);

            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("nobody", RAW_PASSWORD), CLIENT_IP))
                    .isInstanceOf(BizException.class)
                    .hasMessage("用户名或密码错误");

            ArgumentCaptor<LoginRecord> captor = ArgumentCaptor.forClass(LoginRecord.class);
            verify(loginRecordMapper).insert(captor.capture());
            assertThat(captor.getValue().getSuccess()).isEqualTo(0);
            assertThat(captor.getValue().getFailReason()).isEqualTo("NOT_FOUND");
        }

        @Test
        @DisplayName("密码错误时累加失败计数，且不影响同名的正确密码校验")
        void wrongPasswordIncrementsCounter() {
            SysUser user = existingUser("Another123");
            when(userMapper.selectByUsername("zhangsan")).thenReturn(user);

            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("zhangsan", RAW_PASSWORD), CLIENT_IP))
                    .isInstanceOf(BizException.class)
                    .hasMessage("用户名或密码错误");

            verify(userMapper).increaseFailedLogin(42L);
            verify(userMapper, never()).markLoginSuccess(any(), any());
        }

        @Test
        @DisplayName("失败次数达到阈值时锁定账号")
        void lockAfterMaxFailures() {
            SysUser user = existingUser("Another123");
            user.setFailedLoginCount(4);   // 本次失败后正好第 5 次
            when(userMapper.selectByUsername("zhangsan")).thenReturn(user);

            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("zhangsan", RAW_PASSWORD), CLIENT_IP))
                    .isInstanceOf(BizException.class);

            verify(userMapper).lockUntil(eq(42L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("锁定未到期时拒绝登录，且不会提前解锁")
        void lockedAccountRejectedBeforeExpiry() {
            SysUser user = existingUser(RAW_PASSWORD);
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(10));
            when(userMapper.selectByUsername("zhangsan")).thenReturn(user);

            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("zhangsan", RAW_PASSWORD), CLIENT_IP))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("锁定");

            // 关键断言：密码其实是对的，但依然不能登录；也不能顺手把锁解了
            verify(userMapper, never()).unlockIfExpired(any());
            verify(userMapper, never()).markLoginSuccess(any(), any());
        }

        @Test
        @DisplayName("锁定已到期时自动解锁并允许登录")
        void expiredLockIsReleased() {
            SysUser user = existingUser(RAW_PASSWORD);
            user.setStatus(UserStatus.LOCKED);
            user.setLockedUntil(LocalDateTime.now().minusMinutes(1));
            when(userMapper.selectByUsername("zhangsan")).thenReturn(user);

            LoginResponse response = authService.login(
                    new LoginRequest("zhangsan", RAW_PASSWORD), CLIENT_IP);

            assertThat(response.accessToken()).isNotBlank();
            verify(userMapper).unlockIfExpired(42L);
        }

        @Test
        @DisplayName("账号被禁用时拒绝登录")
        void disabledAccountRejected() {
            SysUser user = existingUser(RAW_PASSWORD);
            user.setStatus(UserStatus.DISABLED);
            when(userMapper.selectByUsername("zhangsan")).thenReturn(user);

            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("zhangsan", RAW_PASSWORD), CLIENT_IP))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.ACCESS_DENIED);
        }

        @Test
        @DisplayName("同一 IP 尝试过于频繁时拒绝，且不触碰数据库")
        void rateLimitedBeforeTouchingDatabase() {
            when(rateLimitService.tryAcquire(anyString(), anyInt(), any())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(
                    new LoginRequest("zhangsan", RAW_PASSWORD), CLIENT_IP))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RATE_LIMITED);

            verify(userMapper, never()).selectByUsername(anyString());
        }
    }

    // ==================================================================
    // 登出
    // ==================================================================

    @Nested
    @DisplayName("登出")
    class Logout {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("把令牌 jti 写入黑名单，TTL 不超过令牌剩余有效期")
        void blacklistsTokenId() {
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(valueOps);

            String token = jwtProvider.issue(42L, "zhangsan", UserRole.USER);
            AuthPrincipal principal = jwtProvider.parse(token);

            authService.logout(token, principal);

            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOps).set(eq("lg:jwt:blacklist:" + principal.tokenId()), eq("1"), ttlCaptor.capture());
            assertThat(ttlCaptor.getValue()).isPositive();
            assertThat(ttlCaptor.getValue()).isLessThanOrEqualTo(Duration.ofMinutes(30));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("Redis 故障时登出不报错（令牌最多 30 分钟自然过期）")
        void redisFailureDoesNotBreakLogout() {
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(valueOps);
            org.mockito.Mockito.doThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"))
                    .when(valueOps).set(anyString(), anyString(), any(Duration.class));

            String token = jwtProvider.issue(42L, "zhangsan", UserRole.USER);
            AuthPrincipal principal = jwtProvider.parse(token);

            authService.logout(token, principal);

            // 不抛异常即为通过
            verify(auditService).success(eq(42L), any(), any(), any(), any(), any());
        }
    }
}
