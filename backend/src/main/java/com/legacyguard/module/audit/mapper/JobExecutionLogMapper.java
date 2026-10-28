package com.legacyguard.module.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.audit.domain.JobExecutionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务执行日志。只增不删（过期清理由运维脚本按时间删除）。
 */
@Mapper
public interface JobExecutionLogMapper extends BaseMapper<JobExecutionLog> {
}
