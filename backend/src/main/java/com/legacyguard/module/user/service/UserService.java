package com.legacyguard.module.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.legacyguard.common.exception.BizException;
import com.legacyguard.module.audit.domain.AuditAction;
import com.legacyguard.module.audit.service.AuditService;
import com.legacyguard.module.user.domain.SysUser;
import com.legacyguard.module.user.dto.UpdateProfileRequest;
import com.legacyguard.module.user.dto.UserProfileResponse;
import com.legacyguard.module.user.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 个人资料读写。
 */
@Service
public class UserService {

    private final SysUserMapper userMapper;
    private final AuditService auditService;

    public UserService(SysUserMapper userMapper, AuditService auditService) {
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    public UserProfileResponse getProfile(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.isDeleted()) {
            throw BizException.notFound("用户");
        }
        return UserProfileResponse.from(user);
    }

    /**
     * 更新资料。字段为 null 表示不修改。
     *
     * <p>用条件更新而非 {@code updateById}：后者会把实体上所有非 null 字段一起写回，
     * 包括调用方没有打算改的列。只 set 明确传入的字段，语义更清楚，也不会
     * 因为并发更新把别人刚改的值覆盖回去。
     */
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.isDeleted()) {
            throw BizException.notFound("用户");
        }

        Map<String, Object> changed = new LinkedHashMap<>();
        LambdaUpdateWrapper<SysUser> wrapper = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId);

        if (request.displayName() != null) {
            wrapper.set(SysUser::getDisplayName, request.displayName());
            changed.put("displayName", request.displayName());
        }
        if (request.email() != null) {
            wrapper.set(SysUser::getEmail, request.email().isBlank() ? null : request.email().trim());
            changed.put("email", request.email());
        }
        if (request.phone() != null) {
            wrapper.set(SysUser::getPhone, request.phone().isBlank() ? null : request.phone().trim());
            changed.put("phone", request.phone());
        }

        if (changed.isEmpty()) {
            return UserProfileResponse.from(user);
        }

        wrapper.set(SysUser::getUpdatedAt, LocalDateTime.now());
        userMapper.update(null, wrapper);

        // 只记录"改了哪些字段"，不记录改前改后的值 —— 邮箱手机号属于个人信息，
        // 审计的价值在于追责，不在于留存一份用户资料的副本
        auditService.success(userId, user.getRole(), AuditAction.PROFILE_UPDATE,
                "USER", userId, Map.of("fields", changed.keySet()));

        SysUser updated = userMapper.selectById(userId);
        return UserProfileResponse.from(updated);
    }
}
