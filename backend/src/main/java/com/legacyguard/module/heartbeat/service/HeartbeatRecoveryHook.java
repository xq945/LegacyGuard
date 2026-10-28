package com.legacyguard.module.heartbeat.service;

/**
 * 用户恢复状态确认后的连锁处理扩展点。
 *
 * <p>为什么做成接口而不是直接调用授权模块：<br>
 * 授权模块<b>依赖</b>心跳状态（必须处于 REVIEWING 才能发起申请），
 * 如果心跳再反向依赖授权模块，就形成了循环依赖。
 * 用这个接口把方向反过来 —— 心跳只声明"我恢复了一个人"，
 * 谁关心谁去实现。
 *
 * <p>目前由 authorization 模块实现，负责取消该用户所有未生效的授权案件
 * （见 docs/07 Q2：人回来了，建立在"失联"前提上的案件都该作废）。
 *
 * <p>没有实现时（例如授权模块尚未接入），心跳确认照常完成 ——
 * 这是一个可选扩展点，不是必需依赖。
 */
public interface HeartbeatRecoveryHook {

    /**
     * 用户在状态确认接口上完成了确认。
     *
     * @param userId 完成确认的用户
     */
    void onRecovered(Long userId);
}
