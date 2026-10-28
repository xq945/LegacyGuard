package com.legacyguard.module.authorization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.authorization.domain.DisputeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 异议数据访问。
 */
@Mapper
public interface DisputeRecordMapper extends BaseMapper<DisputeRecord> {

    /** 案件是否存在未处理的异议 */
    @Select("SELECT COUNT(*) FROM dispute_record WHERE case_id = #{caseId} AND status = 'OPEN'")
    int countOpen(@Param("caseId") Long caseId);

    /**
     * 处理掉案件下所有未决异议。
     *
     * <p>条件更新而非按主键更新：即便并发有两个管理员同时点"处理异议"，
     * 也只有一个能把状态从 OPEN 改掉，另一个影响 0 行。
     */
    @Update("""
            UPDATE dispute_record
               SET status = 'RESOLVED', resolution = #{resolution},
                   resolved_by = #{resolvedBy}, resolved_at = #{now}, updated_at = #{now}
             WHERE case_id = #{caseId} AND status = 'OPEN'
            """)
    int resolveAllOpen(@Param("caseId") Long caseId,
                       @Param("resolution") String resolution,
                       @Param("resolvedBy") Long resolvedBy,
                       @Param("now") LocalDateTime now);
}
