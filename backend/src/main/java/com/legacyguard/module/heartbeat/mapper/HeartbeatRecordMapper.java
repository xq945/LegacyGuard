package com.legacyguard.module.heartbeat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.heartbeat.domain.HeartbeatRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 状态确认流水数据访问。
 *
 * <p>表上的 {@code uk_user_cycle (user_id, cycle_no)} 唯一约束是重复确认的最终防线：
 * 即便乐观锁因为某种原因失效，也不会出现同一周期两条确认记录。
 */
@Mapper
public interface HeartbeatRecordMapper extends BaseMapper<HeartbeatRecord> {
}
