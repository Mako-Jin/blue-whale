package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.application.port.AuthSecurityStatQuery;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysLoginHistoryMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysRefreshTokenMapper;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysUserMapper;
import com.bluewhale.auth.infrastructure.persistence.po.LoginDayCountPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysRefreshTokenPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AuthSecurityStatQuery} 的 MyBatis-Plus 实现。
 *
 * <p>三张表各有自己的"有效"口径：{@code sys_user} 看 {@code status + deleted}、
 * {@code sys_refresh_token} 看 {@code revoked + expires_at}、
 * {@code sys_login_history} 只看时间窗。口径写在这里而不是散在 provider，
 * 是因为以后会话管理页、账号报表页都要用同一口径——
 * 一个口径只能有一处实现，否则"总控说 38 个在线，会话页列出 41 条"永远说不清。</p>
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class AuthSecurityStatQueryImpl implements AuthSecurityStatQuery {

    /** 账号启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 未逻辑删除 */
    private static final int NOT_DELETED = 0;

    /** 令牌未撤销 */
    private static final int NOT_REVOKED = 0;

    private final SysUserMapper userMapper;

    private final SysRefreshTokenMapper refreshTokenMapper;

    private final SysLoginHistoryMapper loginHistoryMapper;

    @Override
    public long countEnabledUsers(Long tenantId) {
        if (tenantId == null) {
            return 0L;
        }
        Long total = userMapper.selectCount(new LambdaQueryWrapper<SysUserPo>()
                .eq(SysUserPo::getTenantId, tenantId)
                .eq(SysUserPo::getStatus, STATUS_ENABLED)
                .eq(SysUserPo::getDeleted, NOT_DELETED));
        return total == null ? 0L : total;
    }

    @Override
    public long countActiveSessions(Long tenantId, LocalDateTime now) {
        if (tenantId == null || now == null) {
            return 0L;
        }
        Long total = refreshTokenMapper.selectCount(new LambdaQueryWrapper<SysRefreshTokenPo>()
                .eq(SysRefreshTokenPo::getTenantId, tenantId)
                .eq(SysRefreshTokenPo::getRevoked, NOT_REVOKED)
                .gt(SysRefreshTokenPo::getExpiresAt, now));
        return total == null ? 0L : total;
    }

    @Override
    public List<LoginDayCount> countLoginByDay(Long tenantId, LocalDate fromDay, LocalDate toDay) {
        if (tenantId == null || fromDay == null || toDay == null || !fromDay.isBefore(toDay)) {
            return List.of();
        }
        List<LoginDayCountPo> rows = loginHistoryMapper.countByDay(tenantId,
                fromDay.atStartOfDay(), toDay.atStartOfDay());
        List<LoginDayCount> result = new ArrayList<>(rows.size());
        for (LoginDayCountPo row : rows) {
            if (row.getDay() == null) {
                continue;
            }
            result.add(new LoginDayCount(row.getDay(), row.getLoginResult(),
                    row.getTotal() == null ? 0L : row.getTotal()));
        }
        return List.copyOf(result);
    }
}
