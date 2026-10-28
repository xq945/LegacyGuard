package com.legacyguard.module.authorization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.authorization.domain.AuthorizationApproval;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审批记录数据访问。只增不删。
 */
@Mapper
public interface AuthorizationApprovalMapper extends BaseMapper<AuthorizationApproval> {

    @Select("""
            SELECT * FROM authorization_approval
             WHERE case_id = #{caseId}
             ORDER BY id
            """)
    List<AuthorizationApproval> selectByCase(@Param("caseId") Long caseId);
}
