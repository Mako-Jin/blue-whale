package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.application.user.password.port.PasswordExpiryQuery;
import com.bluewhale.auth.infrastructure.persistence.entity.SysUser;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.bluewhale.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link PasswordExpiryQuery} 的 MyBatis-Plus 实现（REQ-SEC-023）。
 *
 * <p>提醒任务跨租户运行，查询与回写前临时清空租户上下文以绕过行级过滤，执行后恢复原值；
 * 已提醒判定采用「提醒时间早于最近改密时间」——口令一旦变更，最近改密时间变新，
 * 新周期自动恢复提醒能力，无需额外清理提醒时间。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PasswordExpiryQueryImpl implements PasswordExpiryQuery {

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;
    /** 已提醒判定：提醒时间早于最近改密时间（或从未提醒） */
    private static final String NOT_NOTICED_THIS_CYCLE =
            "(password_notice_time IS NULL OR password_notice_time < password_updated_time)";

    private final SysUserMapper userMapper;

    @Override
    public List<PasswordExpiryCandidate> findCandidates(LocalDateTime remindFrom) {
        if (remindFrom == null) {
            return List.of();
        }
        Long previous = TenantContext.get();
        TenantContext.clear();
        try {
            List<SysUser> rows = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getStatus, STATUS_ENABLED)
                    .isNotNull(SysUser::getPasswordUpdatedTime)
                    .le(SysUser::getPasswordUpdatedTime, remindFrom)
                    .apply(NOT_NOTICED_THIS_CYCLE));
            return rows.stream().map(row -> new PasswordExpiryCandidate(row.getTenantId(), row.getId(),
                    row.getUsername(), row.getPasswordUpdatedTime())).toList();
        } finally {
            TenantContext.set(previous);
        }
    }

    @Override
    public void markNoticed(Long userId, LocalDateTime noticedAt) {
        if (userId == null || noticedAt == null) {
            return;
        }
        SysUser update = new SysUser();
        update.setId(userId);
        update.setPasswordNoticeTime(noticedAt);
        Long previous = TenantContext.get();
        TenantContext.clear();
        try {
            userMapper.updateById(update);
        } finally {
            TenantContext.set(previous);
        }
    }
}
