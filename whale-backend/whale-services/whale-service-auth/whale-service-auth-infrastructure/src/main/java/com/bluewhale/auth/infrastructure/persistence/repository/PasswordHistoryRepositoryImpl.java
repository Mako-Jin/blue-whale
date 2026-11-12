package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.repository.PasswordHistoryRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysPasswordHistory;
import com.bluewhale.auth.infrastructure.persistence.mapper.SysPasswordHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link PasswordHistoryRepository} 的 MyBatis-Plus 实现（REQ-SEC-023）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class PasswordHistoryRepositoryImpl implements PasswordHistoryRepository {

    /** 口令历史 Mapper */
    private final SysPasswordHistoryMapper passwordHistoryMapper;

    @Override
    public List<String> listRecent(Long tenantId, Long userId, int limit) {
        if (tenantId == null || userId == null || limit <= 0) {
            return List.of();
        }
        List<SysPasswordHistory> rows = passwordHistoryMapper.selectList(
                new LambdaQueryWrapper<SysPasswordHistory>()
                        .eq(SysPasswordHistory::getTenantId, tenantId)
                        .eq(SysPasswordHistory::getUserId, userId)
                        .orderByDesc(SysPasswordHistory::getCreateTime)
                        .orderByDesc(SysPasswordHistory::getId)
                        .last("limit " + limit));
        return rows.stream().map(SysPasswordHistory::getPassword).toList();
    }

    @Override
    public void append(Long tenantId, Long userId, String encodedPassword) {
        SysPasswordHistory history = new SysPasswordHistory();
        history.setTenantId(tenantId);
        history.setUserId(userId);
        history.setPassword(encodedPassword);
        history.setCreateTime(LocalDateTime.now());
        passwordHistoryMapper.insert(history);
    }

    @Override
    public void trim(Long tenantId, Long userId, int keep) {
        if (tenantId == null || userId == null) {
            return;
        }
        int keepCount = Math.max(keep, 0);
        // 只取保留边界那一行（OFFSET keep LIMIT 1），避免全量加载历史行到内存再裁剪；
        // 严格小于边界排序键 (create_time, id) 的行即为待清理行，与列表排序口径一致
        List<SysPasswordHistory> boundary = passwordHistoryMapper.selectList(
                new LambdaQueryWrapper<SysPasswordHistory>()
                        .select(SysPasswordHistory::getCreateTime, SysPasswordHistory::getId)
                        .eq(SysPasswordHistory::getTenantId, tenantId)
                        .eq(SysPasswordHistory::getUserId, userId)
                        .orderByDesc(SysPasswordHistory::getCreateTime)
                        .orderByDesc(SysPasswordHistory::getId)
                        .last("limit " + keepCount + ", 1"));
        if (boundary.isEmpty()) {
            return;
        }
        SysPasswordHistory edge = boundary.get(0);
        passwordHistoryMapper.delete(new LambdaQueryWrapper<SysPasswordHistory>()
                .eq(SysPasswordHistory::getTenantId, tenantId)
                .eq(SysPasswordHistory::getUserId, userId)
                .and(w -> w.lt(SysPasswordHistory::getCreateTime, edge.getCreateTime())
                        .or(wt -> wt.eq(SysPasswordHistory::getCreateTime, edge.getCreateTime())
                                .lt(SysPasswordHistory::getId, edge.getId()))));
    }
}
