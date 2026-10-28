package com.legacyguard.module.authorization.support;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 案件业务编号生成：{@code LG-yyyyMMdd-XXXXXX}。
 *
 * <p>为什么要一个对外编号而不是直接用自增主键：自增值会泄露业务量
 * （"这个案件 id 是 1024，说明平台总共才一千多个案件"），
 * 也不适合出现在日志和客服沟通中。
 *
 * <p>编号只用于<b>展示与检索</b>，一切关联查询仍走主键 ——
 * 随机串不适合做外键，也不该承担唯一性职责（数据库上 {@code uk_case_no}
 * 只负责"不重复"，真正的唯一性是主键）。
 */
@Component
public class CaseNoGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int SUFFIX_LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder sb = new StringBuilder("LG-");
        sb.append(LocalDate.now().format(DATE)).append('-');
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
