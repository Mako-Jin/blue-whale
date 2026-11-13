package com.bluewhale.auth.infrastructure.social;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.application.port.SocialAccountProvisioner;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserTenantMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserTenantPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 三方账号开通器实现（权限中心扩展 M2）：租户自助注册开启时自动建号。
 *
 * <p>账号特征：随机临时口令（BCrypt 加密，用户不可知，登录全走三方）、
 * {@code register_source=SOCIAL}、不强制首登改密（{@code must_change_password=0}）、
 * 建立 {@code sys_user_tenant} 成员关系。用户名冲突时追加 8 位随机后缀重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialAccountProvisionerImpl implements SocialAccountProvisioner {

    /** 用户名最大长度（与 sys_user 列宽一致） */
    private static final int USERNAME_MAX_LENGTH = 64;
    /** 冲突重试次数上限 */
    private static final int MAX_RETRY = 5;

    private final SysUserMapper userMapper;
    private final SysUserTenantMapper userTenantMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 在指定租户内开通三方来源账号
     *
     * @param tenantId 租户 ID
     * @param username 初始用户名（冲突时自动追加随机后缀）
     * @param nickname 昵称（可空）
     * @param avatar 头像 URL（可空）
     * @param email 邮箱（可空）
     * @return 新建用户 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long provision(Long tenantId, String username, String nickname, String avatar, String email) {
        String randomPassword = passwordEncoder.encode(UUID.randomUUID() + "-" + UUID.randomUUID());
        LocalDateTime now = LocalDateTime.now();
        String finalUsername = resolveUsername(tenantId, username);
        SysUserPo user = new SysUserPo();
        user.setTenantId(tenantId);
        user.setUsername(finalUsername);
        user.setPassword(randomPassword);
        user.setNickname(nickname);
        user.setEmail(email);
        user.setAvatar(avatar);
        user.setStatus(1);
        user.setMustChangePassword(0);
        user.setPasswordUpdatedTime(now);
        user.setRegisterSource("SOCIAL");
        user.setRegisteredAt(now);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下用户名唯一约束兜底：换随机用户名重试
            user.setUsername(finalUsername + "_" + UUID.randomUUID().toString()
                    .substring(0, 8));
            userMapper.insert(user);
        }
        SysUserTenantPo membership = new SysUserTenantPo();
        membership.setUserId(user.getId());
        membership.setTenantId(tenantId);
        membership.setStatus(1);
        membership.setCreateTime(now);
        membership.setUpdateTime(now);
        try {
            userTenantMapper.insert(membership);
        } catch (DuplicateKeyException e) {
            log.info("用户已存在租户成员关系，跳过补建: userId={}, tenantId={}", user.getId(), tenantId);
        }
        log.info("三方自动建号完成: userId={}, tenantId={}, username={}",
                user.getId(), tenantId, user.getUsername());
        return user.getId();
    }

    /**
     * 解析最终用户名：冲突时追加 8 位随机后缀（最多重试 MAX_RETRY 次）
     *
     * @param tenantId 租户 ID
     * @param username 初始用户名
     * @return 可用用户名
     */
    private String resolveUsername(Long tenantId, String username) {
        String candidate = username.length() > USERNAME_MAX_LENGTH
                ? username.substring(0, USERNAME_MAX_LENGTH) : username;
        for (int i = 0; i < MAX_RETRY; i++) {
            Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUserPo>()
                    .eq(SysUserPo::getTenantId, tenantId)
                    .eq(SysUserPo::getUsername, candidate));
            if (count == null || count == 0) {
                return candidate;
            }
            candidate = username.substring(0, Math.min(username.length(), USERNAME_MAX_LENGTH - 9))
                    + "_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return candidate;
    }
}
