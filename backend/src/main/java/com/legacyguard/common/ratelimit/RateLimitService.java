package com.legacyguard.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 Redis 固定窗口的限流。
 *
 * <p>用于登录接口的防爆破、以及后续高成本接口的保护。
 *
 * <p><b>为什么是固定窗口而不是滑动窗口</b>：v1 的目标是"挡住脚本暴力尝试"，
 * 固定窗口在窗口边界可能出现最多 2 倍突发，但对这个场景完全够用，
 * 且实现只需要 {@code INCR + EXPIRE} 两条命令，没有 Lua 脚本的维护成本。
 * 若要更精确，第四阶段可换成令牌桶。
 *
 * <p><b>Redis 故障时放行</b>：限流是防护措施，不是业务前提。
 * 若 Redis 不可用就拒绝所有请求，等于用一个辅助组件把整个系统拖垮。
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final String KEY_PREFIX = "lg:rate:";

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 计数并判断是否超过阈值。
     *
     * @param key    业务维度，如 {@code login:ip:1.2.3.4}
     * @param limit  窗口内允许的最大次数
     * @param window 窗口长度
     * @return true 表示放行
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        String redisKey = KEY_PREFIX + key;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) {
                return true;
            }
            // 只有首次自增时设置过期，避免每个请求都刷新 TTL 导致窗口永不到期
            if (count == 1L) {
                redis.expire(redisKey, window);
            }
            return count <= limit;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，限流降级为放行 key={}", key);
            return true;
        }
    }

    /** 清零计数（例如登录成功后）。 */
    public void reset(String key) {
        try {
            redis.delete(KEY_PREFIX + key);
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 不可用，限流计数清零失败 key={}", key);
        }
    }
}
