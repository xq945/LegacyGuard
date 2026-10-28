package com.legacyguard.module.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.audit.domain.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志数据访问。
 *
 * <p><b>只提供写入与查询，不提供更新和删除。</b>这不是靠约定，
 * 而是这个接口里根本没有对应的方法 —— 想改审计记录就必须绕过本接口另写 SQL，
 * 那会在 Code Review 里一眼被看出来。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
