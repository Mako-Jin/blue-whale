package com.bluewhale.oa.common.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.oa.common.domain.repository.SequenceRepository;
import com.bluewhale.oa.common.infrastructure.persistence.mapper.OaSequenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * 业务序列号仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class SequenceRepositoryImpl implements SequenceRepository {

    private final OaSequenceMapper oaSequenceMapper;

    @Override
    public long nextValue(String bizType, String period) {
        Long tenantId = TenantContext.requireId();
        oaSequenceMapper.upsertIncrement(IdWorker.getId(), tenantId, bizType, period);
        Long current = oaSequenceMapper.selectCurrentValue(tenantId, bizType, period);
        return current == null ? 1L : current;
    }
}
