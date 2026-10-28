package com.legacyguard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LegacyGuard 应用入口。
 *
 * <p>定时任务是本系统的核心驱动之一（心跳到期、等待期生效、Outbox 发布），
 * 因此在此处开启 {@link EnableScheduling}。
 *
 * <p>排除 {@link UserDetailsServiceAutoConfiguration}：本系统用 JWT 认证，
 * 不依赖 UserDetailsService。若保留，Spring Boot 会生成一个内存用户并打印随机密码，
 * 既无用又容易让部署者误以为那是可用的登录入口。
 */
@EnableScheduling
@MapperScan("com.legacyguard.**.mapper")
@ConfigurationPropertiesScan
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class LegacyGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacyGuardApplication.class, args);
    }
}
