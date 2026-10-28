package com.legacyguard.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.user.domain.LoginRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录流水数据访问。只增不查（v1 没有查询接口）。
 */
@Mapper
public interface LoginRecordMapper extends BaseMapper<LoginRecord> {
}
