package com.legacyguard.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.user.domain.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户表数据访问。
 *
 * <p>自定义 SQL 集中在这里，Service 层不拼 SQL。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按用户名查未注销的用户。
     *
     * <p>刻意不用 MyBatis-Plus 的 {@code @TableLogic}：本项目只有两张表做软删除，
     * 全局配置会让所有查询隐式加上逻辑删除条件，容易在排查时看不懂生成的 SQL。
     */
    @Select("""
            SELECT * FROM sys_user
             WHERE username = #{username} AND deleted_at IS NULL
            """)
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 登录成功后的收尾更新：重置失败计数、记录登录时间。
     */
    @Update("""
            UPDATE sys_user
               SET failed_login_count = 0,
                   locked_until = NULL,
                   status = 0,
                   last_login_at = #{loginAt},
                   updated_at = NOW()
             WHERE id = #{userId} AND deleted_at IS NULL
            """)
    int markLoginSuccess(@Param("userId") Long userId, @Param("loginAt") LocalDateTime loginAt);

    /**
     * 登录失败计数 +1。
     *
     * <p>用 SQL 原子自增而不是"先查后写"，避免并发登录请求把计数算少。
     */
    @Update("""
            UPDATE sys_user
               SET failed_login_count = failed_login_count + 1,
                   updated_at = NOW()
             WHERE id = #{userId} AND deleted_at IS NULL
            """)
    int increaseFailedLogin(@Param("userId") Long userId);

    /**
     * 锁定账号直到指定时间。
     *
     * <p>条件里带上 {@code status = 0}：如果账号已被管理员禁用（2），
     * 不应被登录失败逻辑覆盖成"锁定"。
     */
    @Update("""
            UPDATE sys_user
               SET status = 1, locked_until = #{lockedUntil}, updated_at = NOW()
             WHERE id = #{userId} AND status = 0 AND deleted_at IS NULL
            """)
    int lockUntil(@Param("userId") Long userId, @Param("lockedUntil") LocalDateTime lockedUntil);

    /**
     * 解除因登录失败产生的锁定。锁定到期后允许自助恢复。
     *
     * <p>条件里带了 {@code locked_until <= NOW()}：只有确实到期的锁定才会被解除。
     * 如果漏掉这个条件，任何一次登录尝试都能把"还有 10 分钟才解封"的账号提前解锁，
     * 登录失败限制就形同虚设。
     */
    @Update("""
            UPDATE sys_user
               SET status = 0, locked_until = NULL, failed_login_count = 0, updated_at = NOW()
             WHERE id = #{userId}
               AND status = 1
               AND locked_until IS NOT NULL
               AND locked_until <= NOW()
               AND deleted_at IS NULL
            """)
    int unlockIfExpired(@Param("userId") Long userId);
}
