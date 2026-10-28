package com.legacyguard.module.contact.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.legacyguard.module.contact.domain.TrustedContact;
import com.legacyguard.module.contact.dto.ContactView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 可信联系人数据访问。
 *
 * <p>所有状态变更都是<b>条件更新</b>并返回影响行数，
 * 由调用方检查是否为 0 来判断"状态已被他人改变"。
 */
@Mapper
public interface TrustedContactMapper extends BaseMapper<TrustedContact> {

    /**
     * 查同一对用户之间当前有效的关系（PENDING 或 ACTIVE）。
     *
     * <p>用于给出友好提示。真正的唯一性由 {@code uk_active_pair} 保证 ——
     * 两个并发邀请请求可能都查不到已存在的关系，但第二条插入必然失败。
     */
    @Select("""
            SELECT * FROM trusted_contact
             WHERE owner_id = #{ownerId}
               AND contact_user_id = #{contactUserId}
               AND status IN ('PENDING', 'ACTIVE')
             LIMIT 1
            """)
    TrustedContact selectActivePair(@Param("ownerId") Long ownerId,
                                    @Param("contactUserId") Long contactUserId);

    /**
     * 列出与某人相关的全部联系人关系（自己邀请的 + 邀请自己的），带上双方显示名。
     */
    @Select("""
            SELECT tc.id,
                   tc.owner_id,
                   tc.contact_user_id,
                   tc.status,
                   tc.note,
                   tc.invited_at,
                   tc.responded_at,
                   tc.revoked_at,
                   o.username      AS owner_username,
                   o.display_name  AS owner_display_name,
                   c.username      AS contact_username,
                   c.display_name  AS contact_display_name
              FROM trusted_contact tc
              JOIN sys_user o ON o.id = tc.owner_id
              JOIN sys_user c ON c.id = tc.contact_user_id
             WHERE tc.owner_id = #{userId} OR tc.contact_user_id = #{userId}
             ORDER BY tc.updated_at DESC, tc.id DESC
            """)
    List<ContactView> selectRelated(@Param("userId") Long userId);

    /**
     * 接受邀请。只有被邀请人本人、且关系仍处于 PENDING 时才能成功。
     */
    @Update("""
            UPDATE trusted_contact
               SET status = 'ACTIVE',
                   responded_at = #{now},
                   version = version + 1,
                   updated_at = #{now}
             WHERE id = #{id}
               AND contact_user_id = #{contactUserId}
               AND status = 'PENDING'
            """)
    int accept(@Param("id") Long id,
               @Param("contactUserId") Long contactUserId,
               @Param("now") LocalDateTime now);

    /**
     * 拒绝邀请。同样只允许被邀请人本人操作。
     */
    @Update("""
            UPDATE trusted_contact
               SET status = 'REJECTED',
                   responded_at = #{now},
                   version = version + 1,
                   updated_at = #{now}
             WHERE id = #{id}
               AND contact_user_id = #{contactUserId}
               AND status = 'PENDING'
            """)
    int reject(@Param("id") Long id,
               @Param("contactUserId") Long contactUserId,
               @Param("now") LocalDateTime now);

    /**
     * 撤销关系。所有者可撤回 PENDING 邀请，也可撤销已生效的 ACTIVE 关系。
     */
    @Update("""
            UPDATE trusted_contact
               SET status = 'REVOKED',
                   revoked_at = #{now},
                   version = version + 1,
                   updated_at = #{now}
             WHERE id = #{id}
               AND owner_id = #{ownerId}
               AND status IN ('PENDING', 'ACTIVE')
            """)
    int revoke(@Param("id") Long id,
               @Param("ownerId") Long ownerId,
               @Param("now") LocalDateTime now);

    /**
     * 判断某人是否是另一个人的生效联系人。
     *
     * <p>授权案件提交时用它做前置校验 —— 未确认邀请的人不能替别人申请授权。
     */
    @Select("""
            SELECT COUNT(*) FROM trusted_contact
             WHERE id = #{contactId}
               AND owner_id = #{ownerId}
               AND status = 'ACTIVE'
            """)
    int countActiveRelation(@Param("contactId") Long contactId, @Param("ownerId") Long ownerId);
}
