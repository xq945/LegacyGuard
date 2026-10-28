package com.legacyguard.module.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 用户实体，对应 {@code sys_user}。
 *
 * <p>{@code toString} 排除 {@code passwordHash} —— 这个字段一旦出现在日志、
 * 异常信息或调试输出里，就等于把凭据泄露到了日志系统。Lombok 的 {@code @Data}
 * 默认会打印全部字段，因此这里刻意改用 {@code @Getter/@Setter} 并手动排除。
 *
 * <p>注意表上有三个生成列（{@code username_active} 等）用于软删除后的唯一性，
 * 它们由数据库维护，<b>不映射到实体</b>，否则写入时会报错。
 */
@Getter
@Setter
@ToString(exclude = "passwordHash")
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希，永远不返回给客户端 */
    private String passwordHash;

    private String displayName;

    private String email;

    private String phone;

    /** USER / ADMIN，见 {@link UserRole} */
    private String role;

    /** 0 正常 / 1 锁定 / 2 禁用，见 {@link UserStatus} */
    private Integer status;

    /** 连续登录失败次数，登录成功后清零 */
    private Integer failedLoginCount;

    /** 锁定的截止时间；非空且晚于当前时间时不允许登录 */
    private LocalDateTime lockedUntil;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
