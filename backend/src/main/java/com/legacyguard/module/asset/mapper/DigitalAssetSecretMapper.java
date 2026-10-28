package com.legacyguard.module.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.asset.domain.DigitalAssetSecret;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资产密文访问。
 *
 * <p>只在这里查询密文 —— 其他任何 Mapper 都不应触碰 {@code digital_asset_secret}，
 * 这样"哪些代码路径可能读到密文"这个问题，永远只需要看一个文件。
 */
@Mapper
public interface DigitalAssetSecretMapper extends BaseMapper<DigitalAssetSecret> {
}
