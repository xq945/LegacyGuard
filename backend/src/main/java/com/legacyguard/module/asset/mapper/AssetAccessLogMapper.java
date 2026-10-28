package com.legacyguard.module.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.asset.domain.AssetAccessLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资产访问流水数据访问。只增不删。
 */
@Mapper
public interface AssetAccessLogMapper extends BaseMapper<AssetAccessLog> {
}
