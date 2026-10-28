package com.legacyguard.module.heartbeat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 状态确认的默认值与边界。
 *
 * @param defaultCycleDays          新用户默认确认周期（天）
 * @param minCycleDays              允许的最小周期
 * @param maxCycleDays              允许的最大周期
 * @param defaultReminderRounds     默认提醒轮数
 * @param maxReminderRounds         允许的最大提醒轮数
 * @param defaultReminderIntervalHours 默认提醒间隔（小时）
 */
@ConfigurationProperties(prefix = "legacyguard.heartbeat")
public record HeartbeatProperties(
        int defaultCycleDays,
        int minCycleDays,
        int maxCycleDays,
        int defaultReminderRounds,
        int maxReminderRounds,
        int defaultReminderIntervalHours
) {

    public HeartbeatProperties {
        if (defaultCycleDays <= 0) {
            defaultCycleDays = 90;
        }
        if (minCycleDays <= 0) {
            minCycleDays = 7;
        }
        if (maxCycleDays <= 0) {
            maxCycleDays = 365;
        }
        if (defaultReminderRounds <= 0) {
            defaultReminderRounds = 3;
        }
        if (maxReminderRounds <= 0) {
            maxReminderRounds = 10;
        }
        if (defaultReminderIntervalHours <= 0) {
            defaultReminderIntervalHours = 72;
        }
    }
}
