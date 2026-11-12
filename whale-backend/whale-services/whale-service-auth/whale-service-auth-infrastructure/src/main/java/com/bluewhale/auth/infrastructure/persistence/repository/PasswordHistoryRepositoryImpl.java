package com.bluewhale.auth.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.auth.domain.repository.PasswordHistoryRepository;
import com.bluewhale.auth.infrastructure.persistence.entity.SysPasswordHistoryPo;
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
        List<SysPasswordHistoryPo> rows = passwordHistoryMapper.selectList(
                new LambdaQueryWrapper<SysPasswordHistoryPo>()
                        .eq(SysPasswordHistoryPo::getTenantId, tenantId)
                        .eq(SysPasswordHistoryPo::getUserId, userId)
                        .orderByDesc(SysPasswordHistoryPo::getCreateTime)
                        .orderByDesc(SysPasswordHistoryPo::getId)
                        .last("limit " + limit));
        return rows.stream().map(SysPasswordHistoryPo::getPassword).toList();
    }

    @Override
    public void append(Long tenantId, Long userId, String encodedPassword) {
        SysPasswordHistoryPo history = new SysPasswordHistoryPo();
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
        List<SysPasswordHistoryPo> boundary = passwordHistoryMapper.selectList(
                new LambdaQueryWrapper<SysPasswordHistoryPo>()
                        .select(SysPasswordHistoryPo::getCreateTime, SysPasswordHistoryPo::getId)
                        .eq(SysPasswordHistoryPo::getTenantId, tenantId)
                        .eq(SysPasswordHistoryPo::getUserId, userId)
                        .orderByDesc(SysPasswordHistoryPo::getCreateTime)
                        .orderByDesc(SysPasswordHistoryPo::getId)
                        .last("limit " + keepCount + ", 1"));
        if (boundary.isEmpty()) {
            return;
        }
        SysPasswordHistoryPo edge = boundary.get(0);
        passwordHistoryMapper.delete(new LambdaQueryWrapper<SysPasswordHistoryPo>()
                .eq(SysPasswordHistoryPo::getTenantId, tenantId)
                .eq(SysPasswordHistoryPo::getUserId, userId)
                .and(w -> w.lt(SysPasswordHistoryPo::getCreateTime, edge.getCreateTime())
                        .or(wt -> wt.eq(SysPasswordHistoryPo::getCreateTime, edge.getCreateTime())
                                .lt(SysPasswordHistoryPo::getId, edge.getId()))));
    }
}
