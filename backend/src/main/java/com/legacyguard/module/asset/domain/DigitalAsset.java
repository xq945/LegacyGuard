package com.legacyguard.module.asset.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 资产元数据，对应 {@code digital_asset}。
 *
 * <p><b>这个实体里没有密文。</b>密文在 {@link DigitalAssetSecret}，按主键 1:1 关联。
 * 拆表不是为了好看：列表查询是最热的接口，只要密文和元数据同表，
 * 任何一次 {@code SELECT *} 或序列化都可能把整段密文带出去。
 * 分表之后，列表查询物理上碰不到密文列。
 */
@Getter
@Setter
@ToString
@TableName("digital_asset")
public class DigitalAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ownerId;

    private String name;

    /** 见 {@link AssetType} */
    private String assetType;

    /** 普通描述，<b>不得包含敏感内容</b>，它会被明文存储并可被列表接口返回 */
    private String description;

    /** 文件元数据：文件名 */
    private String fileName;

    /** 文件元数据：字节数 */
    private Long fileSize;

    /** 文件元数据：MIME */
    private String mimeType;

    /** 0 正常 / 1 归档 / 2 已删除 */
    private Integer status;

    /** 乐观锁版本，防止并发编辑互相覆盖 */
    @Version
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 软删除标记。删除时与 status=2 同时置位 */
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
