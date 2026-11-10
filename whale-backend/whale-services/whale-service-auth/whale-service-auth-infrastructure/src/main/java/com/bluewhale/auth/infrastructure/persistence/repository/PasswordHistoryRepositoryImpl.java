package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.user.repository.PasswordHistoryRepository;
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
        List<SysPasswordHistory> rows = passwordHistoryMapper.selectList(
                new LambdaQueryWrapper<SysPasswordHistory>()
                        .select(SysPasswordHistory::getId)
                        .eq(SysPasswordHistory::getTenantId, tenantId)
                        .eq(SysPasswordHistory::getUserId, userId)
                        .orderByDesc(SysPasswordHistory::getCreateTime)
                        .orderByDesc(SysPasswordHistory::getId));
        int keepCount = Math.max(keep, 0);
        if (rows.size() <= keepCount) {
            return;
        }
        List<Long> staleIds = rows.subList(keepCount, rows.size()).stream()
                .map(SysPasswordHistory::getId)
                .toList();
        passwordHistoryMapper.deleteByIds(staleIds);
    }
}
