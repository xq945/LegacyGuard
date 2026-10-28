package com.legacyguard.module.asset.domain;

import java.util.Set;

/**
 * 资产类型。
 *
 * <p>限制为白名单：用户不能自定义类型。原因是类型会出现在列表展示与统计中，
 * 自由文本会导致"文档/文档 / 资料 / 笔记"这类同义混杂。
 */
public final class AssetType {

    /** 个人文档（说明性文字） */
    public static final String DOCUMENT = "DOCUMENT";

    /** 图片说明 */
    public static final String IMAGE = "IMAGE";

    /** 重要资料说明 */
    public static final String NOTE = "NOTE";

    /** 账号恢复说明 */
    public static final String ACCOUNT_RECOVERY = "ACCOUNT_RECOVERY";

    /** 其他 */
    public static final String OTHER = "OTHER";

    private static final Set<String> ALL = Set.of(DOCUMENT, IMAGE, NOTE, ACCOUNT_RECOVERY, OTHER);

    private AssetType() {
    }

    public static boolean isValid(String type) {
        return type != null && ALL.contains(type);
    }

    public static Set<String> all() {
        return ALL;
    }
}
