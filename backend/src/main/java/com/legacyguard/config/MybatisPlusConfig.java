package com.legacyguard.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置。
 *
 * <p><b>乐观锁插件</b>：这是本项目并发控制的核心手段之一。实体上标注
 * {@code @Version} 后，MyBatis-Plus 会自动生成
 * {@code UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?}。
 * 但<b>它不会帮你判断影响行数</b> —— 作者必须自己检查返回值，
 * 为 0 时抛 {@code StateConflictException}，否则并发冲突会被静默吞掉。
 *
 * <p><b>分页插件</b>：注意 MyBatis-Plus 3.5.9 起，分页依赖的 jsqlparser 被拆成
 * 独立模块，pom 中必须额外引入 {@code mybatis-plus-jsqlparser}，否则运行期
 * 抛 {@code ClassNotFoundException}。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 乐观锁需在分页之前注册
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页上限，防止前端传 size=999999 拖垮数据库
        pagination.setMaxLimit(100L);
        // 页码越界时返回空结果而不是回到第一页，避免数据被误读
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
