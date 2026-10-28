package com.legacyguard.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置。
 *
 * <p>提供两种模板，用途严格区分：
 * <ul>
 *   <li>{@code StringRedisTemplate}（自动配置）—— 计数器、限流、黑名单、验证码，
 *       全部是字符串或数字，<b>业务代码应该优先用它</b></li>
 *   <li>{@code RedisTemplate<String, Object>} —— 需要存结构化对象时的备用，值用 JSON 序列化</li>
 * </ul>
 *
 * <p><b>红线</b>：Redis 里不得出现任何授权判定所需的数据
 * （案件状态、资产可访问性、资产内容）。要求见 docs/05 第 5.5 节。
 * 判断标准很简单：这个缓存失效会不会导致越权？会，就不能缓存。
 */
@Configuration
public class RedisConfig {

    /**
     * 值序列化用 JSON 而非 JDK 序列化。
     *
     * <p>JDK 序列化的两个问题：一是二进制不可读，排查时只能干瞪眼；
     * 二是存在反序列化漏洞风险（gadget chain），而且类改动后旧缓存全部无法读取。
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
